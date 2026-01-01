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

		ProtectedText protected_ = ParameterProtector.protect(message);

		// The protected text should have parameter names protected
		// but the translatable text (1 Meldung, Meldungen) exposed
		// Structure keywords (plural, =1, other) are kept as-is
		String protectedText = protected_.getProtectedText();

		// Should contain protected parameter name
		assertTrue(protectedText.contains("<x1>count</x1>"));

		// Should contain format type and selectors as-is (not protected)
		assertTrue(protectedText.contains("plural"));
		assertTrue(protectedText.contains("=1"));
		assertTrue(protectedText.contains("other"));

		// Should contain translatable text
		assertTrue(protectedText.contains("1 Meldung"));
		assertTrue(protectedText.contains("Meldungen"));

		// The nested {count} reference should also be protected
		assertTrue(protectedText.contains("<x2>count</x2>"));
	}

	@Test
	public void testRestorePluralFormat() {
		String original = "{count, plural, =1{1 message} other{{count} messages}}";

		// Protect
		ProtectedText protected_ = ParameterProtector.protect(original);

		// Simulate translation (German)
		String simulatedTranslation = protected_.getProtectedText()
			.replace("1 message", "1 Nachricht")
			.replace("messages", "Nachrichten");

		// Restore
		String restored = ParameterProtector.restore(simulatedTranslation, protected_.getParameters());

		// Should have original structure with translated text
		assertTrue(restored.contains("{count, plural,"));
		assertTrue(restored.contains("=1{1 Nachricht}"));
		assertTrue(restored.contains("other{"));
		assertTrue(restored.contains("Nachrichten}"));
	}

	@Test
	public void testComplexRealWorldPlural() {
		String original = "{reportsCount, plural, =0{Keine Meldungen} =1{1 Meldung} other{{reportsCount} Meldungen}}";

		ProtectedText protected_ = ParameterProtector.protect(original);

		// Parameters should include the format structure and selectors
		assertNotNull(protected_.getParameters());

		// The translatable parts should be accessible
		String protectedText = protected_.getProtectedText();
		assertTrue(protectedText.contains("Keine Meldungen"));
		assertTrue(protectedText.contains("1 Meldung"));
		assertTrue(protectedText.contains("Meldungen"));
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
		ProtectedText protected_ = ParameterProtector.protect(original);

		// Simulate English translation
		String translated = protected_.getProtectedText()
			.replace("1 Meldung", "1 report")
			.replace("Meldungen", "reports");

		// Restore
		String restored = ParameterProtector.restore(translated, protected_.getParameters());

		// Verify structure is preserved
		assertTrue(restored.contains("{reportsCount, plural,"));
		assertTrue(restored.contains("=1{1 report}"));
		assertTrue(restored.contains("other{"));
		assertTrue(restored.contains("reports}"));
	}
}
