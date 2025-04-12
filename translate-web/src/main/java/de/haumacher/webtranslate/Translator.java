package de.haumacher.webtranslate;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.deepl.api.DeepLException;

import de.haumacher.webtranslate.extract.PropertiesExtractor;
import de.haumacher.webtranslate.synthesize.TranslationSynthesizer;
import de.haumacher.webtranslate.translate.NameStrategy;
import de.haumacher.webtranslate.translate.PropertiesTranslator;

public class Translator {

	private final String apikey;
	private final String srcLang;
	private final List<String> destLangs;
	private final File propertiesDir;
	private final File templateDir;

	public Translator(String apikey, String srcLang, List<String> destLangs, File propertiesDir, File templateDir) {
		this.apikey = apikey;
		this.srcLang = srcLang;
		this.destLangs = destLangs;
		this.propertiesDir = propertiesDir;
		this.templateDir = templateDir;
	}

	private void run() throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		new PropertiesExtractor(new File(propertiesDir, srcLang), new File(templateDir, srcLang)).process();
		new PropertiesTranslator(apikey, srcLang, destLangs, propertiesDir, null, NameStrategy.LANG_TAG_DIR).translate();
		new TranslationSynthesizer(templateDir, propertiesDir, srcLang, destLangs).synthesize();
	}
	
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		String apikey = args[0];
		String srcLang = args[1];
		List<String> destLangs = Arrays.stream(args[2].split(",")).map(String::strip).toList();
		File propertiesDir = new File(args[3]);
		File templateDir = new File(args[4]);
		
		new Translator(apikey, srcLang, destLangs, propertiesDir, templateDir).run();
	}
	
}
