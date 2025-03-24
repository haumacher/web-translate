package de.haumacher.webtranslate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Processor {

	private File outputDir;
	private File input;

	public Processor(File outputDir, File input) {
		this.outputDir = outputDir;
		this.input = input;
	}
	
	public void process() throws ParserConfigurationException, SAXException, IOException {
		process(input);
	}
	
	private void process(File file) throws ParserConfigurationException, SAXException, IOException {
		if (file.isDirectory()) {
			for (File sub : file.listFiles()) {
				process(sub);
			}
		} else if (file.getName().endsWith(".html")) {
			processHtml(file);
		}
	}

	private void processHtml(File file)
			throws ParserConfigurationException, SAXException, IOException, FileNotFoundException {
		System.out.println("Processing " + file.getPath());
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		EntityResolver resolver = new EntityResolver() {
			@Override
			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
				return null;
			}
		};
		builder.setEntityResolver(resolver);
		Document document = builder.parse(file);

		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();
		
		Map<String, String> textById = analyzer.getTextById();
		writeProperties(file, textById);
		
	    final DOMImplementationLS domImplementation = (DOMImplementationLS) document.getImplementation();
	    final LSSerializer lsSerializer = domImplementation.createLSSerializer();
	    LSOutput output = domImplementation.createLSOutput();
	    
	    // Overwrite with normalized contents.
		try (FileOutputStream out = new FileOutputStream(file)) {
	    	output.setByteStream(out);
	    	lsSerializer.write(document, output);
	    }
	}

	private void writeProperties(File file, Map<String, String> textById) throws IOException {
	    String fileName = file.getName();
	    int index = fileName.lastIndexOf(".");
		String baseName = index >= 0 ? fileName.substring(0, index) : fileName;
		
		Path path = input.toPath().relativize(file.getParentFile().toPath()).resolve(baseName + ".properties");
		File output = outputDir.toPath().resolve(path).toFile();
		output.getParentFile().mkdirs();
		try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.ISO_8859_1))) {
			for (String id : textById.keySet().stream().sorted().toList()) {
				w.println(id + "=" + textById.get(id));
			}
			w.println();
		}
	}
	
	

}
