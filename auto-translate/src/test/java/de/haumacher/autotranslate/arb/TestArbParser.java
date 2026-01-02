package de.haumacher.autotranslate.arb;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import de.haumacher.autotranslate.arb.io.ArbParser;
import de.haumacher.autotranslate.arb.io.ArbWriter;
import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbPlaceholder;
import de.haumacher.autotranslate.arb.model.ArbResource;
import de.haumacher.autotranslate.arb.model.ArbResourceAttributes;

/**
 * Test cases for {@link ArbParser} and {@link ArbWriter}.
 */
public class TestArbParser {

	@Test
	public void testParseMinimalArb() {
		String json = """
			{
			  "MSG_HELLO": "Hello World"
			}
			""";

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(json);

		assertNotNull(bundle);
		assertEquals(1, bundle.getResourceCount());
		assertTrue(bundle.hasResource("MSG_HELLO"));

		ArbResource resource = bundle.getResource("MSG_HELLO");
		assertEquals("MSG_HELLO", resource.getId());
		assertEquals("Hello World", resource.getValue());
		assertFalse(resource.hasAttributes());
	}

	@Test
	public void testParseWithGlobalAttributes() {
		String json = """
			{
			  "@@locale": "en_US",
			  "@@context": "HomePage",
			  "MSG_HELLO": "Hello"
			}
			""";

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(json);

		assertEquals("en_US", bundle.getLocale());
		assertEquals("HomePage", bundle.getContext());
		assertEquals(1, bundle.getResourceCount());
	}

	@Test
	public void testParseWithResourceAttributes() {
		String json = """
			{
			  "@@locale": "en_US",
			  "MSG_HELLO": "Hello!",
			  "@MSG_HELLO": {
			    "type": "text",
			    "context": "HomePage",
			    "description": "greeting message"
			  }
			}
			""";

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(json);

		ArbResource resource = bundle.getResource("MSG_HELLO");
		assertNotNull(resource);
		assertTrue(resource.hasAttributes());

		ArbResourceAttributes attrs = resource.getAttributes();
		assertEquals("text", attrs.getType());
		assertEquals("HomePage", attrs.getContext());
		assertEquals("greeting message", attrs.getDescription());
	}

	@Test
	public void testParseWithPlaceholders() {
		String json = """
			{
			  "FOO_123": "Your pending cost is {COST}",
			  "@FOO_123": {
			    "type": "text",
			    "description": "balance statement",
			    "placeholders": {
			      "COST": {
			        "example": "$123.45",
			        "description": "cost presented with currency symbol"
			      }
			    }
			  }
			}
			""";

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(json);

		ArbResource resource = bundle.getResource("FOO_123");
		assertNotNull(resource);
		assertEquals("Your pending cost is {COST}", resource.getValue());

		ArbResourceAttributes attrs = resource.getAttributes();
		assertNotNull(attrs);
		assertEquals(1, attrs.getPlaceholders().size());

		ArbPlaceholder placeholder = attrs.getPlaceholders().get("COST");
		assertNotNull(placeholder);
		assertEquals("COST", placeholder.getName());
		assertEquals("$123.45", placeholder.getExample());
		assertEquals("cost presented with currency symbol", placeholder.getDescription());
	}

	@Test
	public void testWriteVerbose() {
		ArbBundle bundle = new ArbBundle();
		bundle.setLocale("en_US");
		bundle.setContext("HomePage");

		ArbResource resource = new ArbResource("MSG_HELLO", "Hello {username}!");
		ArbResourceAttributes attrs = new ArbResourceAttributes();
		attrs.setType("text");
		attrs.setDescription("greeting message");
		attrs.addPlaceholder(new ArbPlaceholder("username", "name of the user", "John"));
		resource.setAttributes(attrs);

		bundle.addResource(resource);

		ArbWriter writer = new ArbWriter();
		String json = writer.toJson(bundle, true);

		// Verify the JSON contains all expected elements
		assertTrue(json.contains("\"@@locale\": \"en_US\""));
		assertTrue(json.contains("\"@@context\": \"HomePage\""));
		assertTrue(json.contains("\"MSG_HELLO\": \"Hello {username}!\""));
		assertTrue(json.contains("\"@MSG_HELLO\""));
		assertTrue(json.contains("\"type\": \"text\""));
		assertTrue(json.contains("\"description\": \"greeting message\""));
		assertTrue(json.contains("\"placeholders\""));
		assertTrue(json.contains("\"username\""));
	}

	@Test
	public void testWriteCompact() {
		ArbBundle bundle = new ArbBundle();
		bundle.setLocale("en_US");

		ArbResource resource = new ArbResource("MSG_HELLO", "Hello!");
		ArbResourceAttributes attrs = new ArbResourceAttributes();
		attrs.setDescription("greeting message");
		resource.setAttributes(attrs);
		bundle.addResource(resource);

		ArbWriter writer = new ArbWriter();
		String json = writer.toJson(bundle, false);

		// In compact mode, should have global attributes and resource, but no @MSG_HELLO
		assertTrue(json.contains("\"@@locale\": \"en_US\""));
		assertTrue(json.contains("\"MSG_HELLO\": \"Hello!\""));
		assertFalse(json.contains("\"@MSG_HELLO\""));
		assertFalse(json.contains("greeting message"));
	}

	@Test
	public void testRoundTrip() {
		// Create a bundle
		ArbBundle original = new ArbBundle();
		original.setLocale("de_DE");
		original.setContext("TestPage");

		ArbResource resource1 = new ArbResource("MSG_1", "Hallo Welt");
		original.addResource(resource1);

		ArbResource resource2 = new ArbResource("MSG_2", "Kosten: {amount}");
		ArbResourceAttributes attrs = new ArbResourceAttributes();
		attrs.setType("text");
		attrs.setDescription("cost display");
		attrs.addPlaceholder(new ArbPlaceholder("amount", "monetary amount", "42.00 EUR"));
		resource2.setAttributes(attrs);
		original.addResource(resource2);

		// Write to JSON
		ArbWriter writer = new ArbWriter();
		String json = writer.toJson(original, true);

		// Parse back
		ArbParser parser = new ArbParser();
		ArbBundle parsed = parser.parse(json);

		// Verify
		assertEquals(original.getLocale(), parsed.getLocale());
		assertEquals(original.getContext(), parsed.getContext());
		assertEquals(original.getResourceCount(), parsed.getResourceCount());

		ArbResource parsedResource2 = parsed.getResource("MSG_2");
		assertNotNull(parsedResource2);
		assertEquals("Kosten: {amount}", parsedResource2.getValue());
		assertTrue(parsedResource2.hasAttributes());

		ArbResourceAttributes parsedAttrs = parsedResource2.getAttributes();
		assertEquals("text", parsedAttrs.getType());
		assertEquals("cost display", parsedAttrs.getDescription());
		assertEquals(1, parsedAttrs.getPlaceholders().size());

		ArbPlaceholder parsedPlaceholder = parsedAttrs.getPlaceholders().get("amount");
		assertEquals("amount", parsedPlaceholder.getName());
		assertEquals("monetary amount", parsedPlaceholder.getDescription());
		assertEquals("42.00 EUR", parsedPlaceholder.getExample());
	}

	@Test
	public void testComplexExample() {
		String json = """
			{
			  "@@locale": "en_US",
			  "@@context": "HomePage",
			  "@@last_modified": "2024-01-15T10:30:00Z",
			  "title_bar": "My Cool Home",
			  "@title_bar": {
			    "type": "text",
			    "context": "HomePage",
			    "description": "Page title."
			  },
			  "MSG_OK": "Everything works fine.",
			  "FOO_123": "Your pending cost is {COST}",
			  "@FOO_123": {
			    "type": "text",
			    "context": "HomePage:MainPanel",
			    "description": "balance statement.",
			    "source_text": "Your pending cost is {COST}",
			    "placeholders": {
			      "COST": {
			        "example": "$123.45",
			        "description": "cost presented with currency symbol"
			      }
			    }
			  }
			}
			""";

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(json);

		assertEquals("en_US", bundle.getLocale());
		assertEquals("HomePage", bundle.getContext());
		assertEquals("2024-01-15T10:30:00Z", bundle.getGlobalAttribute("@@last_modified"));
		assertEquals(3, bundle.getResourceCount());

		ArbResource titleBar = bundle.getResource("title_bar");
		assertEquals("My Cool Home", titleBar.getValue());
		assertEquals("Page title.", titleBar.getAttributes().getDescription());

		ArbResource msgOk = bundle.getResource("MSG_OK");
		assertEquals("Everything works fine.", msgOk.getValue());
		assertFalse(msgOk.hasAttributes());

		ArbResource foo123 = bundle.getResource("FOO_123");
		assertEquals("Your pending cost is {COST}", foo123.getValue());
		assertEquals("HomePage:MainPanel", foo123.getAttributes().getContext());
		assertEquals("Your pending cost is {COST}", foo123.getAttributes().getSourceText());
	}
}
