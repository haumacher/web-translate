package de.haumacher.webtranslate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
	private Charset propertiesCharset;

	public Translator(String apikey, String srcLang, List<String> destLangs, File propertiesDir, File templateDir, Charset propertiesCharset) {
		this.apikey = apikey;
		this.srcLang = srcLang;
		this.destLangs = destLangs;
		this.propertiesDir = propertiesDir;
		this.templateDir = templateDir;
		this.propertiesCharset = propertiesCharset;
	}

	private void run() throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {
		new PropertiesExtractor(new File(propertiesDir, srcLang), new File(templateDir, srcLang), StandardCharsets.ISO_8859_1).process();
		new PropertiesTranslator(apikey, srcLang, destLangs, propertiesDir, null, NameStrategy.LANG_TAG_DIR, propertiesCharset).translate();
		new TranslationSynthesizer(templateDir, propertiesDir, srcLang, destLangs).synthesize();
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
