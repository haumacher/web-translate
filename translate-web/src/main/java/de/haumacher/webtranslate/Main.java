package de.haumacher.webtranslate;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Main {

	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
		File input = new File(args[0]);
		File output = new File(args[1]);
		
		new Processor(output, input).process();
	}

}
