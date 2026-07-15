package de.haumacher.autotranslate.maven;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import com.deepl.api.DeepLClient;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.arb.ArbTranslator;

/**
 * Maven goal to translate ARB (Application Resource Bundle) files using DeepL API.
 *
 * <p>
 * This Mojo translates Flutter/Dart localization files while preserving
 * ICU MessageFormat syntax and tracking changes via checksums.
 * </p>
 *
 * <p>
 * Usage example:
 * <pre>
 * mvn auto-translate:translate-arb -DapiKey=YOUR_DEEPL_API_KEY -DsourceFile=app_en.arb -DtargetLangs=de,fr
 * </pre>
 * </p>
 *
 * <p>
 * The mojo supports incremental translation using CRC32 checksums stored in the
 * {@code x-translated} custom attribute. Resources are only re-translated when:
 * <ul>
 *   <li>They don't exist in the target file yet</li>
 *   <li>The checksum doesn't match (indicating the source text changed)</li>
 * </ul>
 * </p>
 */
@Mojo(name = "translate-arb", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class TranslateArbMojo extends AbstractMojo {

	/**
	 * Server ID for retrieving DeepL API key from Maven settings.xml.
	 *
	 * <p>
	 * The API key should be stored in the password field of the server configuration.
	 * Example settings.xml:
	 * <pre>{@code
	 * <settings>
	 *   <servers>
	 *     <server>
	 *       <id>deepl</id>
	 *       <password>YOUR_DEEPL_API_KEY</password>
	 *     </server>
	 *   </servers>
	 * </settings>
	 * }</pre>
	 * </p>
	 */
	@Parameter(property = "translate.arb.serverId", defaultValue = "deepl")
	private String serverId;

	/**
	 * DeepL API key for authentication.
	 *
	 * <p>
	 * This parameter is optional and can be used to directly provide the API key
	 * instead of using server credentials. If both serverId and apiKey are provided,
	 * apiKey takes precedence.
	 * </p>
	 */
	@Parameter(property = "translate.arb.apiKey")
	private String apiKey;

	/**
	 * Maven settings, injected by Maven.
	 */
	@Parameter(defaultValue = "${settings}", readonly = true, required = true)
	private Settings settings;

	/**
	 * Settings decrypter component for decrypting passwords from settings.xml.
	 */
	@Component
	private SettingsDecrypter settingsDecrypter;

	/**
	 * Source ARB file to translate.
	 *
	 * <p>
	 * The file must follow the naming convention: {@code basename_lang.arb}
	 * (e.g., {@code app_en.arb}, {@code messages_de.arb}).
	 * The source language is automatically extracted from the filename.
	 * </p>
	 */
	@Parameter(property = "translate.arb.sourceFile", required = true)
	private File sourceFile;

	/**
	 * Comma-separated list of target language codes (e.g., "de,fr,es").
	 *
	 * <p>
	 * Target files will be created in the same directory as the source file
	 * with the appropriate language suffix (e.g., {@code app_de.arb}, {@code app_fr.arb}).
	 * </p>
	 */
	@Parameter(property = "translate.arb.targetLangs", required = true)
	private String targetLangs;

	/**
	 * Language mappings for DeepL API compatibility.
	 *
	 * <p>
	 * Allows customizing how generic language codes map to DeepL-specific variants.
	 * For example, "en" can be mapped to "en-US" or "en-GB".
	 *
	 * <p>
	 * Default mappings are used if not specified:
	 * <ul>
	 *   <li>{@code en} → {@code en-US}</li>
	 *   <li>{@code pt} → {@code pt-PT}</li>
	 * </ul>
	 *
	 * <p>
	 * Example configuration to override defaults:
	 * <pre>{@code
	 * <configuration>
	 *   <languageMappings>
	 *     <en>en-GB</en>
	 *     <pt>pt-BR</pt>
	 *   </languageMappings>
	 * </configuration>
	 * }</pre>
	 */
	@Parameter(property = "translate.arb.languageMappings")
	private Map<String, String> languageMappings;

	/**
	 * Optional directory containing DeepL glossary files.
	 *
	 * <p>
	 * When set, the directory is expected to contain one tab-separated file per
	 * source/target language pair, named {@code <source>-<target>.tsv} using base
	 * language codes (e.g. {@code en-de.tsv}). Each line is
	 * {@code sourceTerm<TAB>targetTerm} and pins how a term is translated, so the
	 * same source word is rendered consistently across resources and runs.
	 * Glossaries are created before translation and deleted afterwards. When unset,
	 * translation runs without glossaries (unchanged behavior).
	 * </p>
	 */
	@Parameter(property = "translate.arb.glossaryDirectory")
	private File glossaryDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			getLog().info("========================================");
			getLog().info("Starting ARB translation");
			getLog().info("========================================");

			// Validate source file
			if (!sourceFile.exists()) {
				throw new MojoExecutionException(
					"Source file not found: " + sourceFile.getAbsolutePath()
				);
			}

			// Extract source language from filename
			String sourceLang = ArbTranslator.extractLanguage(sourceFile);
			if (sourceLang == null) {
				throw new MojoExecutionException(
					"Cannot determine source language from filename: " + sourceFile.getName() +
					". Expected format: basename_lang.arb (e.g., app_en.arb)"
				);
			}

			List<String> targetLangsList = parseTargetLanguages();

			getLog().info("Source file: " + sourceFile.getAbsolutePath());
			getLog().info("Source language: " + sourceLang);
			getLog().info("Target languages: " + targetLangsList);
			getLog().info("");

			// Resolve API key from server credentials if not directly provided
			String resolvedApiKey = resolveApiKey();

			// Create DeepL translator
			Translator deeplTranslator = new DeepLClient(resolvedApiKey);

			// Create ARB translator with optional custom language mappings and glossary
			Map<String, String> mappings =
				(languageMappings != null && !languageMappings.isEmpty()) ? languageMappings : null;
			if (mappings != null) {
				getLog().info("Using custom language mappings: " + mappings);
			}
			if (glossaryDirectory != null) {
				getLog().info("Glossary directory: " + glossaryDirectory.getAbsolutePath());
			}
			ArbTranslator arbTranslator = new ArbTranslator(deeplTranslator, mappings, glossaryDirectory);

			arbTranslator.translate(sourceFile, targetLangsList);

			getLog().info("");
			getLog().info("========================================");
			getLog().info("ARB translation completed successfully!");
			getLog().info("Total billed characters: " + arbTranslator.getTotalBilledChars());
			getLog().info("========================================");

		} catch (Exception ex) {
			throw new MojoExecutionException("ARB translation failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Resolves the DeepL API key from either direct configuration or server credentials.
	 *
	 * @return The resolved API key
	 * @throws MojoExecutionException If API key cannot be resolved
	 */
	private String resolveApiKey() throws MojoExecutionException {
		// If apiKey is directly provided, use it
		if (apiKey != null && !apiKey.trim().isEmpty()) {
			getLog().debug("Using directly configured API key");
			return apiKey;
		}

		// Otherwise, retrieve from server credentials
		getLog().debug("Retrieving API key from server: " + serverId);

		Server server = settings.getServer(serverId);
		if (server == null) {
			throw new MojoExecutionException(
				"Server '" + serverId + "' not found in settings.xml. " +
				"Please configure the server with your DeepL API key in the password field, " +
				"or provide the API key directly using -Dtranslate.arb.apiKey=YOUR_KEY"
			);
		}

		// Decrypt the password (API key)
		SettingsDecryptionResult decryptionResult = settingsDecrypter.decrypt(
			new DefaultSettingsDecryptionRequest(server)
		);

		Server decryptedServer = decryptionResult.getServer();
		if (decryptedServer == null || decryptedServer.getPassphrase() == null || decryptedServer.getPassphrase().trim().isEmpty()) {
			throw new MojoExecutionException(
				"No passphrase found for server '" + serverId + "' in settings.xml. " +
				"Please configure the DeepL API key in the passphrase field."
			);
		}

		return decryptedServer.getPassphrase();
	}

	private List<String> parseTargetLanguages() {
		return Arrays.stream(targetLangs.split(","))
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.toList();
	}
}
