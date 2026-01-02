package de.haumacher.autotranslate.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.xml.sax.SAXException;

import com.deepl.api.DeepLException;

import de.haumacher.autotranslate.html.extract.PropertiesExtractor;
import de.haumacher.autotranslate.html.synthesize.TranslationSynthesizer;
import de.haumacher.autotranslate.html.translate.NameStrategy;
import de.haumacher.autotranslate.html.translate.PropertiesTranslator;

/**
 * Maven goal to translate HTML templates using DeepL API.
 *
 * <p>
 * This Mojo performs a complete translation workflow in three phases:
 * <ol>
 *   <li>Extract translatable text from HTML templates to properties files</li>
 *   <li>Translate properties files using DeepL API</li>
 *   <li>Synthesize translated HTML templates from translated properties</li>
 * </ol>
 * </p>
 *
 * <p>
 * Usage example:
 * <pre>
 * mvn auto-translate:translate -DapiKey=YOUR_DEEPL_API_KEY
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
	 * <code>-DapiKey=YOUR_KEY</code> or configure it in the plugin configuration.
	 * For security, consider using environment variables:
	 * <code>${env.DEEPL_API_KEY}</code>
	 * </p>
	 */
	@Parameter(property = "apiKey", required = true)
	private String _apiKey;

	/**
	 * Source language code (e.g., "en", "de", "fr").
	 */
	@Parameter(property = "sourceLang", defaultValue = "en")
	private String _sourceLang;

	/**
	 * Comma-separated list of target language codes (e.g., "de,fr,es").
	 */
	@Parameter(property = "targetLangs", defaultValue = "de")
	private String _targetLangs;

	/**
	 * Directory containing the source HTML templates.
	 *
	 * <p>
	 * Templates should be organized in subdirectories by language code.
	 * For example: <code>templates/en/index.html</code>
	 * </p>
	 */
	@Parameter(property = "templateDirectory", defaultValue = "${project.basedir}/templates")
	private File _templateDirectory;

	/**
	 * Directory where properties files are stored and generated.
	 *
	 * <p>
	 * Properties will be organized in subdirectories by language code.
	 * For example: <code>properties/en/index.properties</code>
	 * </p>
	 */
	@Parameter(property = "propertiesDirectory", defaultValue = "${project.basedir}/properties")
	private File _propertiesDirectory;

	/**
	 * Character encoding for properties files.
	 */
	@Parameter(property = "propertiesCharset", defaultValue = "UTF-8")
	private String _propertiesCharset;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			getLog().info("========================================");
			getLog().info("Starting auto-translate translation process");
			getLog().info("========================================");
			getLog().info("Source language: " + _sourceLang);
			getLog().info("Target languages: " + _targetLangs);
			getLog().info("Template directory: " + _templateDirectory.getAbsolutePath());
			getLog().info("Properties directory: " + _propertiesDirectory.getAbsolutePath());
			getLog().info("Charset: " + _propertiesCharset);
			getLog().info("");

			Charset charset = Charset.forName(_propertiesCharset);
			List<String> destLangs = parseTargetLanguages();

			// Phase 1: Extract
			getLog().info("Phase 1/3: Extracting properties from HTML templates...");
			extractProperties(charset);
			getLog().info("");

			// Phase 2: Translate
			getLog().info("Phase 2/3: Translating properties using DeepL API...");
			translateProperties(charset, destLangs);
			getLog().info("");

			// Phase 3: Synthesize
			getLog().info("Phase 3/3: Synthesizing translated HTML templates...");
			synthesizeTemplates(charset, destLangs);
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

	private void extractProperties(Charset charset)
			throws ParserConfigurationException, SAXException, IOException {
		File srcPropertiesDir = new File(_propertiesDirectory, _sourceLang);
		File srcTemplateDir = new File(_templateDirectory, _sourceLang);

		if (!srcTemplateDir.exists()) {
			throw new IOException("Source template directory does not exist: " + srcTemplateDir.getAbsolutePath());
		}

		PropertiesExtractor extractor = new PropertiesExtractor(srcPropertiesDir, srcTemplateDir, charset);
		extractor.process();
	}

	private void translateProperties(Charset charset, List<String> destLangs)
			throws IOException, DeepLException, InterruptedException {
		File srcPropertiesDir = new File(_propertiesDirectory, _sourceLang);

		if (!srcPropertiesDir.exists()) {
			throw new IOException("Source properties directory does not exist: " + srcPropertiesDir.getAbsolutePath());
		}

		PropertiesTranslator translator = new PropertiesTranslator(
			_apiKey,
			_sourceLang,
			destLangs,
			_propertiesDirectory,
			srcPropertiesDir,
			NameStrategy.LANG_TAG_DIR,
			charset
		);
		translator.translate();
	}

	private void synthesizeTemplates(Charset charset, List<String> destLangs)
			throws IOException, ParserConfigurationException, SAXException {
		TranslationSynthesizer synthesizer = new TranslationSynthesizer(
			_templateDirectory,
			_propertiesDirectory,
			_sourceLang,
			destLangs,
			charset
		);
		synthesizer.synthesize();
	}
}
