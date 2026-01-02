package de.haumacher.autotranslate.maven;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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
	 * DeepL API key for authentication.
	 *
	 * <p>
	 * This parameter is required. You can pass it via command line:
	 * <code>-DapiKey=YOUR_KEY</code> or configure it in the plugin configuration.
	 * For security, consider using environment variables:
	 * <code>${env.DEEPL_API_KEY}</code>
	 * </p>
	 */
	@Parameter(property = "apiKey", required = true)
	private String apiKey;

	/**
	 * Source ARB file to translate.
	 *
	 * <p>
	 * The file must follow the naming convention: {@code basename_lang.arb}
	 * (e.g., {@code app_en.arb}, {@code messages_de.arb}).
	 * The source language is automatically extracted from the filename.
	 * </p>
	 */
	@Parameter(property = "sourceFile", required = true)
	private File sourceFile;

	/**
	 * Comma-separated list of target language codes (e.g., "de,fr,es").
	 *
	 * <p>
	 * Target files will be created in the same directory as the source file
	 * with the appropriate language suffix (e.g., {@code app_de.arb}, {@code app_fr.arb}).
	 * </p>
	 */
	@Parameter(property = "targetLangs", required = true)
	private String targetLangs;

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

			// Create DeepL translator
			Translator deeplTranslator = new DeepLClient(apiKey);

			// Create and run ARB translator
			ArbTranslator arbTranslator = new ArbTranslator(deeplTranslator);
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

	private List<String> parseTargetLanguages() {
		return Arrays.stream(targetLangs.split(","))
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.toList();
	}
}
