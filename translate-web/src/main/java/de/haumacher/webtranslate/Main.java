package de.haumacher.webtranslate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

public class Main {

	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
		String fileName = args[0];
		
		Document document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(new File(fileName));

		new HtmlAnalyzer(document).analyze();
		
	    final DOMImplementationLS domImplementation = (DOMImplementationLS) document.getImplementation();
	    final LSSerializer lsSerializer = domImplementation.createLSSerializer();
	    LSOutput output = domImplementation.createLSOutput();
	    
	    String baseName = fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - 4) : fileName;
		try (FileOutputStream out = new FileOutputStream(new File(baseName + "-id.xml"))) {
	    	output.setByteStream(out);
	    	lsSerializer.write(document, output);
	    }
	}
	
}
