package de.haumacher.autotranslate.html;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;

import de.haumacher.autotranslate.html.extract.PropertiesExtractor;
import de.haumacher.autotranslate.html.synthesize.TranslationSynthesizer;
import de.haumacher.autotranslate.html.translate.NameStrategy;
import de.haumacher.autotranslate.html.translate.PropertiesTranslator;

public class Translator {

	private final com.deepl.api.Translator _translator;
	private final String _srcLang;
	private final List<String> _destLangs;
	private final File _propertiesDir;
	private final File _templateDir;
	private Charset _propertiesCharset;

	/**
	 * Creates a new HTML translator with a translator instance.
	 *
	 * @param translator Translator instance for DeepL API communication
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param propertiesDir Base properties directory
	 * @param templateDir Base template directory
	 * @param propertiesCharset Charset for properties files
	 */
	public Translator(com.deepl.api.Translator translator, String srcLang, List<String> destLangs, File propertiesDir, File templateDir, Charset propertiesCharset) {
		_translator = translator;
		_srcLang = srcLang;
		_destLangs = destLangs;
		_propertiesDir = propertiesDir;
		_templateDir = templateDir;
		_propertiesCharset = propertiesCharset;
	}

	/**
	 * Creates a new HTML translator with an API key.
	 *
	 * @param apikey DeepL API key
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 * @param propertiesDir Base properties directory
	 * @param templateDir Base template directory
	 * @param propertiesCharset Charset for properties files
	 */
	public Translator(String apikey, String srcLang, List<String> destLangs, File propertiesDir, File templateDir, Charset propertiesCharset) {
		this(new DeepLClient(apikey), srcLang, destLangs, propertiesDir, templateDir, propertiesCharset);
	}

	/**
	 * Runs the complete HTML translation pipeline: extract → translate → synthesize.
	 *
	 * @throws ParserConfigurationException If HTML parser configuration fails
	 * @throws SAXException If HTML parsing fails
	 * @throws IOException If file I/O fails
	 * @throws DeepLException If DeepL API call fails
	 * @throws InterruptedException If translation is interrupted
	 */
	public void run() throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		new PropertiesExtractor(new File(_propertiesDir, _srcLang), new File(_templateDir, _srcLang), _propertiesCharset).process();
		new PropertiesTranslator(_translator, _srcLang, _destLangs, _propertiesDir, null, NameStrategy.LANG_TAG_DIR, _propertiesCharset).translate();
		new TranslationSynthesizer(_templateDir, _propertiesDir, _srcLang, _destLangs, _propertiesCharset).synthesize();
	}
	
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		String apikey = args[0];
		String srcLang = args[1];
		List<String> destLangs = Arrays.stream(args[2].split(",")).map(String::strip).toList();
		File propertiesDir = new File(args[3]);
		File templateDir = new File(args[4]);
		Charset propertiesCharset = args.length > 5 ? Charset.forName(args[5]) : StandardCharsets.ISO_8859_1;
		
		new Translator(apikey, srcLang, destLangs, propertiesDir, templateDir, propertiesCharset).run();
	}
	
}
