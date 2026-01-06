package de.haumacher.autotranslate.html;

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

import de.haumacher.autotranslate.html.extract.HtmlAnalyzer;
import de.haumacher.autotranslate.html.extract.PropertiesExtractor;
import de.haumacher.autotranslate.html.extract.PropertiesWriter;

/**
 * Test case for {@link HtmlAnalyzer}.
 */
public class TestHtmlAnalyzer {

	@Test
	public void testAnalyze() throws SAXException, IOException, ParserConfigurationException {
		String html = "<html><body>Some <a>funny <b><c>new</c><d>ly</d></b> generated <e>awesome</e></a> text</body></html>";
		
		Document document = parse(html);
		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();
		
		assertEquals("""
			t0001=Some <x1>funny <x2><x3>new</x3><x4>ly</x4></x2> generated <x5>awesome</x5></x1> text
			
			""", properties(analyzer));
		
		assertEquals("""
			<!DOCTYPE html>
			<html><body data-tx="t0001:68ea8b3c">Some <a>funny <b><c>new</c><d>ly</d></b> generated <e>awesome</e></a> text</body></html>""",
			html(document));
		
		// Set text with missing <x4> tag (must be inserted at the end).
		analyzer.getTextById().put("t0001", "<x1>Lustiger <x2><x3>neu</x3></x2> generierter Text, der <x5>wunderbar</x5></x1> ist");
		analyzer.inject();

		assertEquals("""
			<!DOCTYPE html>
			<html><body data-tx="t0001:68ea8b3c"><a>Lustiger <b><c>neu</c></b> generierter Text, der <e>wunderbar</e></a> ist<d></d></body></html>""",
			html(document));
	}

	static Document parse(String html) throws SAXException, IOException, ParserConfigurationException {
		return DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(new InputSource(new StringReader(html)));
	}

	@Test
	public void testTextAttributeParent() throws SAXException, IOException, ParserConfigurationException {
		String html = """
			<nav title="My title"><ul><li>My text</li></ul></nav>""";

		Document document = parse(html);
		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();

		assertEquals("""
			<!DOCTYPE html>
			<nav data-tx="t0001:98c2b6c7" title="My title"><ul><li data-tx="t0002:780af66c">My text</li></ul></nav>""",
			html(document));
		
		assertEquals("""
			t0001.title=My title
			t0002=My text
			
			""", properties(analyzer));

		analyzer.getTextById().put("t0001.title", "Mein Titel");
		analyzer.getTextById().put("t0002", "Mein Text");
		analyzer.inject();

		assertEquals("""
			<!DOCTYPE html>
			<nav data-tx="t0001:98c2b6c7" title="Mein Titel"><ul><li data-tx="t0002:780af66c">Mein Text</li></ul></nav>""",
			html(document));
	}
	
	static String html(Document document) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PropertiesExtractor.serializeDocument(buffer, document);
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}

	private String properties(HtmlAnalyzer analyzer) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		new PropertiesWriter(buffer, StandardCharsets.ISO_8859_1).write(analyzer.getTextById());
		return new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	@Test
	public void testComplexNestedStructure() throws SAXException, IOException, ParserConfigurationException {
		// Create an HTML structure that will produce the protected text pattern:
		// "Text A <x1> Text B <x2> Text C <x3> <x4></x4><x5></x5> Text D </x3></x2></x1>"
		String html = """
			<html><body>
				<li data-tx="t0001:e8cdfbe5">
					Das Passwort<a>, \
					das Du bei der <b>Registrierung</b> erhalten \
					hast, </a><c> <d></d><e></e>,</c> muss Du jetzt noch in das Feld \
					<f>Passwort</f> eintragen werden.
				</li>
			</body></html>""";

		Document document = parse(html);
		HtmlAnalyzer analyzer = new HtmlAnalyzer(document);
		analyzer.analyze();

		// Extract the text - should contain nested placeholders
		String extractedText = analyzer.getTextById().get("t0001");
		assertEquals("""
			Das Passwort<x1>, \
			das Du bei der <x2>Registrierung</x2> erhalten \
			hast, </x1><x3> <x4></x4><x5></x5>,</x3> muss Du jetzt noch in das Feld \
			<x6>Passwort</x6> eintragen werden.""",
				extractedText);

		// Now inject the same text back (simulating translation that preserved structure)
		analyzer.getTextById().put("t0001", "Text A1 <x1> Text B1 <x2> Text C1 <x3> <x4></x4><x5></x5> Text D1 </x3></x2></x1>");
		analyzer.inject();

		// Verify the HTML structure was correctly reconstructed
		String result = html(document);
		assertEquals("""
			<!DOCTYPE html>
			<html><body>
				<li data-tx="t0001:21a0ad48">Text A1 <a> Text B1 <b> Text C1 <c> <d></d><e></e> Text D1 </c></b></a><f></f></li>
			</body></html>""",
			result);
	}
}
