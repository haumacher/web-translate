package de.haumacher.webtranslate.extract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public class PropertiesExtractor {

	private File outputDir;
	private File input;

	public PropertiesExtractor(File outputDir, File input) {
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
		
		Document document = parseHtml(file);

		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();
		
		Map<String, String> textById = analyzer.getTextById();
		writeProperties(file, textById);
		
	    // Overwrite with normalized contents.
		try (FileOutputStream out = new FileOutputStream(file)) {
		    serializeDocument(out, document);
	    }
	}

	public static Document parseHtml(File file) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		
		Document document = builder.parse(file);
		return document;
	}

	public static void serializeDocument(OutputStream out, Document document) {
		try {
			out.write("<!DOCTYPE html>\n".getBytes(StandardCharsets.UTF_8));
			XMLOutputFactory factory = XMLOutputFactory.newDefaultFactory();
			XMLStreamWriter xml = factory.createXMLStreamWriter(out, "utf-8");
			write(xml, document.getDocumentElement());
		} catch (XMLStreamException | IOException ex) {
			throw new IOError(ex);
		}
	}

	private static void write(XMLStreamWriter xml, Element element) throws XMLStreamException {
		xml.writeStartElement(element.getTagName());
		NamedNodeMap attributes = element.getAttributes();
		for (int n = 0, cnt = attributes.getLength(); n < cnt; n++) {
			Node attribute = attributes.item(n);
			xml.writeAttribute(attribute.getNodeName(), attribute.getNodeValue());
		}
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				xml.writeCharacters(text.getTextContent());
			} else if (child instanceof Element sub) {
				write(xml, sub);
			}
		}
		xml.writeEndElement();
	}

	private void writeProperties(File file, Map<String, String> textById) throws IOException {
	    String baseName = baseName(file);
		
		Path path = input.toPath().relativize(file.getParentFile().toPath()).resolve(baseName + ".properties");
		File output = outputDir.toPath().resolve(path).toFile();
		output.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(output)) {
			new PropertiesWriter(out).write(textById);
		}
	}

	public static String baseName(File file) {
		String fileName = file.getName();
	    int index = fileName.lastIndexOf(".");
		String baseName = index >= 0 ? fileName.substring(0, index) : fileName;
		return baseName;
	}

	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
		File input = new File(args[0]);
		File output = new File(args[1]);
		
		new PropertiesExtractor(output, input).process();
	}

}
