package de.haumacher.webtranslate.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Test case for {@link HtmlAnalyzer}.
 */
public class TestHtmlAnalyzer {

	@Test
	public void testAnalyze() throws SAXException, IOException, ParserConfigurationException {
		String html = "<html><body>Some <a>funny <b><c>new</c><d>ly</d></b> generated <e>awesome</e></a> text</body></html>";
		
		Document document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(new InputSource(new StringReader(html)));
		
		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();
		
		assertEquals("""
			t0001=Some <x1>funny <x2><x3>new</x3><x4>ly</x4></x2> generated <x5>awesome</x5></x1> text
			
			""", properties(analyzer));
		
		assertEquals("""
			<html><body data-tx="t0001">Some <a>funny <b><c>new</c><d>ly</d></b> generated <e>awesome</e></a> text</body></html>""", 
			html(document));
		
		analyzer.getTextById().put("t0001", "<x1>Lustiger <x2><x3>neu</x3></x2> generierter Text, der <x5>wunderbar</x5></x1> ist");
		
		analyzer.inject();
		
		assertEquals("""
			<html><body data-tx="t0001"><a>Lustiger <b><c>neu</c><d></d></b> generierter Text, der <e>wunderbar</e></a> ist</body></html>""", 
			html(document));
	}

	private String html(Document document) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PropertiesExtractor.serializeDocument(buffer, document);
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}

	private String properties(HtmlAnalyzer analyzer) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		new PropertiesWriter(buffer).write(analyzer.getTextById());
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}
}
