package de.haumacher.autotranslate.html;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.haumacher.autotranslate.html.extract.TextExtractor;

/**
 * Test case for {@link TextExtractor}.
 */
public class TestTextExtractor {

	@Test
	public void testExtractNested() throws SAXException, IOException, ParserConfigurationException {
		Assertions.assertEquals("Hello <x1><x2>world</x2></x1>!", new TextExtractor(parse("<div>Hello <b><i>world</i></b>!</div>").getDocumentElement()).extract());
	}
	
	private static Document parse(String html) throws SAXException, IOException, ParserConfigurationException {
		return TestHtmlAnalyzer.parse(html);
	}
}
