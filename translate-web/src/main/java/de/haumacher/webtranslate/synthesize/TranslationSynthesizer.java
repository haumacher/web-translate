package de.haumacher.webtranslate.synthesize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
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
	private File outputDir;
	
	public TranslationSynthesizer(File templateDir, File propertiesDir, File outputDir) {
		this.templateDir = templateDir;
		this.propertiesDir = propertiesDir;
		this.outputDir = outputDir;
	}

	public void synthesize() throws IOException, ParserConfigurationException, SAXException {
		synthesize(templateDir);
	}

	private void synthesize(File file) throws IOException, ParserConfigurationException, SAXException {
		if (file.isDirectory()) {
			for (File sub : file.listFiles()) {
				synthesize(sub);
			}
		} else if (file.getName().endsWith(".html")) {
			synthesizeHtml(file);
		}
	}

	private void synthesizeHtml(File htmlFile) throws IOException, ParserConfigurationException, SAXException {
		System.err.println("Synthesizing: " + htmlFile.getPath());
		String propertiesName = PropertiesExtractor.baseName(htmlFile) + ".properties";
		
		Path path = templateDir.toPath().relativize(htmlFile.toPath());
		File propertiesFile = propertiesDir.toPath().resolve(path).getParent().resolve(propertiesName).toFile();
		File outputFile = outputDir.toPath().resolve(path).toFile();
		
		Properties properties = new Properties();
		try (FileInputStream in = new FileInputStream(propertiesFile)) {
			properties.load(in);
		}
		
		Document document = PropertiesExtractor.parseHtml(htmlFile);
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
		new TranslationSynthesizer(new File(args[0]), new File(args[1]), new File(args[2])).synthesize();
	}
}
