package de.haumacher.webtranslate.synthesize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.haumacher.webtranslate.extract.HtmlAnalyzer;
import de.haumacher.webtranslate.extract.PropertiesExtractor;

public class TranslationSynthesizer {

	private File propertiesDir;
	private File templateDir;
	private List<String> destLangs;
	private String srcLang;
	private Charset propertiesCharset;
	
	public TranslationSynthesizer(File templateDir, File propertiesDir, String srcLang, List<String> destLangs, Charset propertiesCharset) {
		this.templateDir = templateDir;
		this.propertiesDir = propertiesDir;
		this.srcLang = srcLang;
		this.destLangs = destLangs;
		this.propertiesCharset = propertiesCharset;
	}

	public void synthesize() throws IOException, ParserConfigurationException, SAXException {
		for (String destLang : destLangs) {
			synthesize(new File(templateDir, srcLang), destLang);
		}
	}

	private void synthesize(File srcFile, String destLang) throws IOException, ParserConfigurationException, SAXException {
		if (srcFile.isDirectory()) {
			for (File sub : srcFile.listFiles()) {
				synthesize(sub, destLang);
			}
		} else if (srcFile.getName().endsWith(".html")) {
			synthesizeHtml(srcFile, destLang);
		}
	}

	private void synthesizeHtml(File srcFile, String destLang) throws IOException, ParserConfigurationException, SAXException {
		String propertiesName = PropertiesExtractor.baseName(srcFile) + ".properties";
		
		Path path = templateDir.toPath().resolve(srcLang).relativize(srcFile.toPath());
		File propertiesFile = propertiesDir.toPath().resolve(destLang).resolve(path).getParent().resolve(propertiesName).toFile();
		File outputFile = templateDir.toPath().resolve(destLang).resolve(path).toFile();

		System.err.println("Synthesizing: " + outputFile.getPath());

		Properties properties = new Properties();
		try (FileInputStream in = new FileInputStream(propertiesFile)) {
			properties.load(new InputStreamReader(in, propertiesCharset));
		}
		
		Document document = PropertiesExtractor.parseHtml(srcFile);
		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.setTextById(toMap(properties));
		analyzer.inject();
		
		outputFile.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(outputFile)) {
			PropertiesExtractor.serializeDocument(out, document);
		}
	}

	private Map<String, String> toMap(Properties properties) {
		HashMap<String, String> result = new HashMap<>();
		for (Entry<Object, Object> entry : properties.entrySet()) {
			result.put((String)entry.getKey(), (String)entry.getValue());
		}
		return result;
	}
	
	public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
		File templateDir = new File(args[0]);
		File propertiesDir = new File(args[1]);
		String srcLang = args[2];
		List<String> destLangs = Arrays.stream(args[3].split(",")).map(String::strip).toList();
		Charset propertiesCharset = args.length > 4 ? Charset.forName(args[4]) : StandardCharsets.ISO_8859_1;
		new TranslationSynthesizer(templateDir, propertiesDir, srcLang, destLangs, propertiesCharset).synthesize();
	}
}
