package de.haumacher.autotranslate.maven;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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

/**
 * Maven goal to translate HTML templates using DeepL API.
 *
 * <p>
 * This Mojo translates HTML templates directly using an in-memory approach:
 * <ul>
 *   <li>Parses source HTML files and assigns data-tx IDs with CRC checksums</li>
 *   <li>Extracts translatable text while preserving markup structure</li>
 *   <li>Translates only new or changed content (incremental translation)</li>
 *   <li>Generates translated HTML files in target language directories</li>
 * </ul>
 * No intermediate properties files are created.
 * </p>
 *
 * <p>
 * Usage example with server credentials:
 * <pre>
 * mvn auto-translate:translate -Dtranslate.serverId=deepl
 * </pre>
 * </p>
 */
@Mojo(name = "translate", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class TranslateMojo extends AbstractMojo {

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
	@Parameter(property = "translate.serverId", defaultValue = "deepl")
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
	@Parameter(property = "translate.apiKey")
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
	 * Source language code (e.g., "en", "de", "fr").
	 */
	@Parameter(property = "translate.sourceLang", defaultValue = "en")
	private String sourceLang;

	/**
	 * Comma-separated list of target language codes (e.g., "de,fr,es").
	 */
	@Parameter(property = "translate.targetLangs", defaultValue = "de")
	private String targetLangs;

	/**
	 * Directory containing the HTML templates.
	 *
	 * <p>
	 * Templates should be organized in subdirectories by language code.
	 * For example: <code>templates/en/index.html</code>
	 * Translated templates will be generated in parallel directories like
	 * <code>templates/de/index.html</code>, <code>templates/fr/index.html</code>, etc.
	 * </p>
	 */
	@Parameter(property = "translate.templateDirectory", defaultValue = "${project.basedir}/templates")
	private File templateDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			getLog().info("========================================");
			getLog().info("Starting HTML translation");
			getLog().info("========================================");
			getLog().info("Source language: " + sourceLang);
			getLog().info("Target languages: " + targetLangs);
			getLog().info("Template directory: " + templateDirectory.getAbsolutePath());
			getLog().info("");

			// Resolve API key from server credentials if not directly provided
			String resolvedApiKey = resolveApiKey();

			List<String> destLangs = parseTargetLanguages();

			// Create and run translator
			de.haumacher.autotranslate.html.Translator translator =
				new de.haumacher.autotranslate.html.Translator(
					resolvedApiKey,
					sourceLang,
					destLangs,
					templateDirectory
				);
			translator.run();

			getLog().info("");
			getLog().info("========================================");
			getLog().info("Translation completed successfully!");
			getLog().info("========================================");

		} catch (Exception ex) {
			throw new MojoExecutionException("Translation failed: " + ex.getMessage(), ex);
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
				"or provide the API key directly using -Dtranslate.apiKey=YOUR_KEY"
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
