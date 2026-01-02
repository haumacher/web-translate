package de.haumacher.webtranslate.arb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.haumacher.webtranslate.arb.IcuMessageParser.MessagePart;
import de.haumacher.webtranslate.arb.ParameterProtector.ProtectedText;

/**
 * Test cases for {@link IcuMessageParser} and complex ICU MessageFormat handling.
 */
public class TestIcuMessageParser {

	@Test
	public void testSimplePlaceholder() {
		String message = "Hello {username}!";
		List<MessagePart> parts = IcuMessageParser.parse(message);

		assertEquals(3, parts.size());
		assertTrue(parts.get(0) instanceof IcuMessageParser.TextPart);
		assertTrue(parts.get(1) instanceof IcuMessageParser.SimplePlaceholder);
		assertTrue(parts.get(2) instanceof IcuMessageParser.TextPart);

		assertEquals("Hello ", ((IcuMessageParser.TextPart) parts.get(0)).getText());
		assertEquals("username", ((IcuMessageParser.SimplePlaceholder) parts.get(1)).getName());
		assertEquals("!", ((IcuMessageParser.TextPart) parts.get(2)).getText());
	}

	@Test
	public void testPluralFormat() {
		String message = "{count, plural, =1{1 message} other{{count} messages}}";
		List<MessagePart> parts = IcuMessageParser.parse(message);

		assertEquals(1, parts.size());
		assertTrue(parts.get(0) instanceof IcuMessageParser.ComplexFormat);

		IcuMessageParser.ComplexFormat format = (IcuMessageParser.ComplexFormat) parts.get(0);
		assertEquals("count", format.getArgumentName());
		assertEquals("plural", format.getFormatType());
		assertEquals(2, format.getCases().size());

		// First case: =1
		assertEquals("=1", format.getCases().get(0).getSelector());
		assertEquals(1, format.getCases().get(0).getParts().size());

		// Second case: other
		assertEquals("other", format.getCases().get(1).getSelector());
		List<MessagePart> otherParts = format.getCases().get(1).getParts();
		assertEquals(2, otherParts.size());
		
		assertTrue(otherParts.get(0) instanceof IcuMessageParser.SimplePlaceholder);
		assertTrue(otherParts.get(1) instanceof IcuMessageParser.TextPart);
	}

	@Test
	public void testPluralWithHashSymbol() {
		String message = "{count, plural, one{# item} other{# items}}";
		List<MessagePart> parts = IcuMessageParser.parse(message);

		IcuMessageParser.ComplexFormat format = (IcuMessageParser.ComplexFormat) parts.get(0);
		assertEquals(2, format.getCases().size());

		// Check that # is parsed as a placeholder
		List<MessagePart> oneParts = format.getCases().get(0).getParts();
		assertTrue(oneParts.stream().anyMatch(p ->
			p instanceof IcuMessageParser.SimplePlaceholder &&
			((IcuMessageParser.SimplePlaceholder) p).getName().equals("#")
		));
	}

	@Test
	public void testSelectFormat() {
		String message = "{gender, select, male{his} female{her} other{their}}";
		List<MessagePart> parts = IcuMessageParser.parse(message);

		assertEquals(1, parts.size());
		IcuMessageParser.ComplexFormat format = (IcuMessageParser.ComplexFormat) parts.get(0);

		assertEquals("gender", format.getArgumentName());
		assertEquals("select", format.getFormatType());
		assertEquals(3, format.getCases().size());

		assertEquals("male", format.getCases().get(0).getSelector());
		assertEquals("female", format.getCases().get(1).getSelector());
		assertEquals("other", format.getCases().get(2).getSelector());
	}

	@Test
	public void testNestedPlaceholdersInPlural() {
		String message = "{count, plural, =1{1 message from {sender}} other{{count} messages from {sender}}}";
		List<MessagePart> parts = IcuMessageParser.parse(message);

		IcuMessageParser.ComplexFormat format = (IcuMessageParser.ComplexFormat) parts.get(0);

		// Check "other" case has nested {sender} placeholder
		List<MessagePart> otherParts = format.getCases().get(1).getParts();
		assertTrue(otherParts.stream().anyMatch(p ->
			p instanceof IcuMessageParser.SimplePlaceholder &&
			((IcuMessageParser.SimplePlaceholder) p).getName().equals("sender")
		));
	}

	@Test
	public void testProtectPluralFormat() {
		String message = "{count, plural, =1{1 Meldung} other{{count} Meldungen}}";

		ProtectedText protection = ParameterProtector.protect(message);

		// Note: Complex ICU format protection is simplified - only parameter name is protected
		// Full ICU format support with proper translatable text extraction is not yet implemented
		String protectedText = protection.getProtectedText();

		// Should contain protected parameter name (just the name, nothing else)
		assertTrue(protectedText.contains("<x1>count</x1>"));

		// Verify it's just the parameter name in the tag
		assertEquals(1, protection.getParameters().size());
		assertEquals("count", protection.getParameters().get(0));
	}

	@Test
	public void testRestorePluralFormat() {
		String original = "{count, plural, =1{1 message} other{{count} messages}}";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Restore should return the original
		String restored = protection.restore();

		// Protect + restore must yield the original input
		assertEquals(original, restored);
	}

	@Test
	public void testComplexRealWorldPlural() {
		String original = "{reportsCount, plural, =0{Keine Meldungen} =1{1 Meldung} other{{reportsCount} Meldungen}}";

		ProtectedText protection = ParameterProtector.protect(original);

		// Should just protect the parameter name
		assertNotNull(protection.getParameters());
		assertEquals(1, protection.getParameters().size());
		assertEquals("reportsCount", protection.getParameters().get(0));

		// The protected text is just the parameter tag
		String protectedText = protection.getProtectedText();
		assertTrue(protectedText.contains("<x1>reportsCount</x1>"));
	}

	@Test
	public void testMixedTextAndPlural() {
		String message = "You have {count, plural, =0{no messages} one{one message} other{# messages}} in your inbox.";

		List<MessagePart> parts = IcuMessageParser.parse(message);

		// Should have: text + plural format + text
		assertEquals(3, parts.size());
		assertTrue(parts.get(0) instanceof IcuMessageParser.TextPart);
		assertTrue(parts.get(1) instanceof IcuMessageParser.ComplexFormat);
		assertTrue(parts.get(2) instanceof IcuMessageParser.TextPart);

		assertEquals("You have ", ((IcuMessageParser.TextPart) parts.get(0)).getText());
		assertEquals(" in your inbox.", ((IcuMessageParser.TextPart) parts.get(2)).getText());
	}

	@Test
	public void testSelectWithNestedPlural() {
		String message = "{gender, select, male{He has {count, plural, one{# item} other{# items}}} other{They have items}}";

		List<MessagePart> parts = IcuMessageParser.parse(message);

		assertEquals(1, parts.size());
		IcuMessageParser.ComplexFormat outerFormat = (IcuMessageParser.ComplexFormat) parts.get(0);
		assertEquals("select", outerFormat.getFormatType());

		// Check male case contains nested plural
		List<MessagePart> maleParts = outerFormat.getCases().get(0).getParts();
		assertTrue(maleParts.stream().anyMatch(p -> p instanceof IcuMessageParser.ComplexFormat));
	}

	@Test
	public void testProtectAndRestoreRealExample() {
		String original = "{reportsCount, plural, =1{1 Meldung} other{{reportsCount} Meldungen}}";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// The protected text is just <x1>reportsCount</x1>
		String protectedText = protection.getProtectedText();
		assertTrue(protectedText.contains("<x1>reportsCount</x1>"));

		// Restore should give back the original
		String restored = protection.restore();
		assertEquals(original, restored);
	}
}
