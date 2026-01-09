package de.haumacher.autotranslate.html;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;

import de.haumacher.autotranslate.log.ConsoleLogger;
import de.haumacher.autotranslate.log.Logger;

public class Translator {

	private final com.deepl.api.Translator _translator;
	private final String _srcLang;
	private final List<String> _destLangs;
	private final File _templateDir;
	private final Logger _logger;

	/**
	 * Creates a new HTML translator with a translator instance.
	 *
	 * @param translator Translator instance for DeepL API communication
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param templateDir Base template directory containing language subdirectories
	 * @param logger Logger for output messages
	 */
	public Translator(com.deepl.api.Translator translator, String srcLang, List<String> destLangs, File templateDir, Logger logger) {
		_translator = translator;
		_srcLang = srcLang;
		_destLangs = destLangs;
		_templateDir = templateDir;
		_logger = logger;
	}

	/**
	 * Creates a new HTML translator with a translator instance using console logger.
	 *
	 * @param translator Translator instance for DeepL API communication
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param templateDir Base template directory containing language subdirectories
	 */
	public Translator(com.deepl.api.Translator translator, String srcLang, List<String> destLangs, File templateDir) {
		this(translator, srcLang, destLangs, templateDir, ConsoleLogger.INSTANCE);
	}

	/**
	 * Creates a new HTML translator with an API key.
	 *
	 * @param apikey DeepL API key
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param templateDir Base template directory containing language subdirectories
	 */
	public Translator(String apikey, String srcLang, List<String> destLangs, File templateDir) {
		this(new DeepLClient(apikey), srcLang, destLangs, templateDir, ConsoleLogger.INSTANCE);
	}

	/**
	 * Creates a new HTML translator with an API key and custom logger.
	 *
	 * @param apikey DeepL API key
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param templateDir Base template directory containing language subdirectories
	 * @param logger Logger for output messages
	 */
	public Translator(String apikey, String srcLang, List<String> destLangs, File templateDir, Logger logger) {
		this(new DeepLClient(apikey), srcLang, destLangs, templateDir, logger);
	}

	/**
	 * Runs the complete HTML translation pipeline using in-memory translation.
	 *
	 * <p>
	 * Processes all HTML files in the source language directory and translates them
	 * to all target languages. Translation is done in-memory without creating
	 * intermediate properties files.
	 * </p>
	 *
	 * @throws ParserConfigurationException If HTML parser configuration fails
	 * @throws SAXException If HTML parsing fails
	 * @throws IOException If file I/O fails
	 * @throws DeepLException If DeepL API call fails
	 * @throws InterruptedException If translation is interrupted
	 */
	public void run() throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		File srcDir = new File(_templateDir, _srcLang);
		if (!srcDir.exists() || !srcDir.isDirectory()) {
			throw new IOException("Source language directory does not exist: " + srcDir.getAbsolutePath());
		}

		HtmlFileTranslator fileTranslator = new HtmlFileTranslator(_translator, _srcLang, _destLangs, _logger);
		processDirectory(srcDir, fileTranslator, "");

		_logger.info("");
		_logger.info("Translation complete!");
		_logger.info("Total billed characters: " + fileTranslator.getTotalBilledChars());
	}

	private void processDirectory(File dir, HtmlFileTranslator fileTranslator, String relativePath)
			throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		for (File file : dir.listFiles()) {
			String fileRelativePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();

			if (file.isDirectory()) {
				processDirectory(file, fileTranslator, fileRelativePath);
			} else if (file.getName().endsWith(".html")) {
				fileTranslator.translateFile(file, _templateDir, fileRelativePath);
			}
		}
	}
	
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		String apikey = args[0];
		String srcLang = args[1];
		List<String> destLangs = Arrays.stream(args[2].split(",")).map(String::strip).toList();
		File templateDir = new File(args[3]);

		new Translator(apikey, srcLang, destLangs, templateDir).run();
	}
	
}
