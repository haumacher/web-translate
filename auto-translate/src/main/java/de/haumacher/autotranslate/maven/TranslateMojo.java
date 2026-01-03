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
 * Usage example:
 * <pre>
 * mvn auto-translate:translate -Dtranslate.apiKey=YOUR_DEEPL_API_KEY
 * </pre>
 * </p>
 */
@Mojo(name = "translate", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class TranslateMojo extends AbstractMojo {

	/**
	 * DeepL API key for authentication.
	 *
	 * <p>
	 * This parameter is required. You can pass it via command line:
	 * <code>-Dtranslate.apiKey=YOUR_KEY</code> or configure it in the plugin configuration.
	 * For security, consider using environment variables:
	 * <code>${env.DEEPL_API_KEY}</code>
	 * </p>
	 */
	@Parameter(name = "apiKey", property = "translate.apiKey", required = true)
	private String _apiKey;

	/**
	 * Source language code (e.g., "en", "de", "fr").
	 */
	@Parameter(name = "sourceLang", property = "translate.sourceLang", defaultValue = "en")
	private String _sourceLang;

	/**
	 * Comma-separated list of target language codes (e.g., "de,fr,es").
	 */
	@Parameter(name = "targetLangs", property = "translate.targetLangs", defaultValue = "de")
	private String _targetLangs;

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
	@Parameter(name = "templateDirectory", property = "translate.templateDirectory", defaultValue = "${project.basedir}/templates")
	private File _templateDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			getLog().info("========================================");
			getLog().info("Starting HTML translation");
			getLog().info("========================================");
			getLog().info("Source language: " + _sourceLang);
			getLog().info("Target languages: " + _targetLangs);
			getLog().info("Template directory: " + _templateDirectory.getAbsolutePath());
			getLog().info("");

			List<String> destLangs = parseTargetLanguages();

			// Create and run translator
			de.haumacher.autotranslate.html.Translator translator =
				new de.haumacher.autotranslate.html.Translator(
					_apiKey,
					_sourceLang,
					destLangs,
					_templateDirectory
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

	private List<String> parseTargetLanguages() {
		return Arrays.stream(_targetLangs.split(","))
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.toList();
	}
}
