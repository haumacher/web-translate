package de.haumacher.webtranslate.extract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

	// See https://developer.mozilla.org/en-US/docs/Glossary/Void_element
	private static final Set<String> VOID_ELEMENTS = new HashSet<>(Arrays.asList(
	    "area", 
	    "base", 
	    "br", 
	    "col", 
	    "embed", 
	    "hr", 
	    "img", 
	    "input", 
	    "link", 
	    "meta", 
	    "param",
	    "source", 
	    "track", 
	    "wbr")); 
	
	private File propertiesDir;
	private File templateDir;
	private Charset propertiesCharset;

	public PropertiesExtractor(File propertiesDir, File templateDir, Charset propertiesCharset) {
		this.propertiesDir = propertiesDir;
		this.templateDir = templateDir;
		this.propertiesCharset = propertiesCharset;
	}
	
	public void process() throws ParserConfigurationException, SAXException, IOException {
		process(templateDir);
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
		String tagName = element.getTagName();
		boolean isVoid = isVoidElement(tagName);
		if (isVoid) {
			xml.writeEmptyElement(tagName);
		} else {
			xml.writeStartElement(tagName);
		}
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
		if (!isVoid) {
			xml.writeEndElement();
		}
	}

	private static boolean isVoidElement(String tagName) {
		return VOID_ELEMENTS.contains(tagName);
	}

	private void writeProperties(File file, Map<String, String> textById) throws IOException {
	    String baseName = baseName(file);
		
		Path path = templateDir.toPath().relativize(file.getParentFile().toPath()).resolve(baseName + ".properties");
		File output = propertiesDir.toPath().resolve(path).toFile();
		output.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(output)) {
			new PropertiesWriter(out, propertiesCharset).write(textById);
		}
	}

	public static String baseName(File file) {
		String fileName = file.getName();
	    return baseName(fileName);
	}

	public static String baseName(String fileName) {
		int index = fileName.lastIndexOf(".");
		String baseName = index >= 0 ? fileName.substring(0, index) : fileName;
		return baseName;
	}

	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
		File input = new File(args[0]);
		File output = new File(args[1]);
		Charset propertiesCharset = args.length > 2 ? Charset.forName(args[2]) : StandardCharsets.ISO_8859_1;
		
		new PropertiesExtractor(output, input, propertiesCharset).process();
	}

}
