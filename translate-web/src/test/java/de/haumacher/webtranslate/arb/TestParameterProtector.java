package de.haumacher.webtranslate.arb;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import de.haumacher.webtranslate.arb.ParameterProtector.ProtectedText;

/**
 * Test cases for {@link ParameterProtector}.
 */
public class TestParameterProtector {

	@Test
	public void testProtectSimpleParameter() {
		String text = "Hello {username}!";
		ProtectedText protected_ = ParameterProtector.protect(text);

		assertEquals("Hello <x1>username</x1>!", protected_.getProtectedText());
		assertEquals(1, protected_.getParameters().size());
		assertEquals("username", protected_.getParameters().get(0));
	}

	@Test
	public void testProtectMultipleParameters() {
		String text = "Hello {username}, you have {count} messages";
		ProtectedText protected_ = ParameterProtector.protect(text);

		assertEquals("Hello <x1>username</x1>, you have <x2>count</x2> messages",
			protected_.getProtectedText());
		assertEquals(2, protected_.getParameters().size());
		assertEquals("username", protected_.getParameters().get(0));
		assertEquals("count", protected_.getParameters().get(1));
	}

	@Test
	public void testProtectNoParameters() {
		String text = "Hello World!";
		ProtectedText protected_ = ParameterProtector.protect(text);

		assertEquals("Hello World!", protected_.getProtectedText());
		assertEquals(0, protected_.getParameters().size());
	}

	@Test
	public void testProtectComplexParameters() {
		String text = "Your balance is {amount_usd} and you have {pending-count} pending items";
		ProtectedText protected_ = ParameterProtector.protect(text);

		assertEquals(
			"Your balance is <x1>amount_usd</x1> and you have <x2>pending-count</x2> pending items",
			protected_.getProtectedText());
		assertEquals(2, protected_.getParameters().size());
		assertEquals("amount_usd", protected_.getParameters().get(0));
		assertEquals("pending-count", protected_.getParameters().get(1));
	}

	@Test
	public void testProtectMaskedContent() {
		// {@content} is special ARB syntax for masked content, should NOT be treated as parameter
		String text = "Some text {@content} with markup";
		ProtectedText protected_ = ParameterProtector.protect(text);

		assertEquals("Some text {@content} with markup", protected_.getProtectedText());
		assertEquals(0, protected_.getParameters().size());
	}

	@Test
	public void testRestoreSimple() {
		String translatedText = "Hallo <x1>benutzername</x1>!";
		String restored = ParameterProtector.restore(translatedText,
			java.util.List.of("username"));

		assertEquals("Hallo {username}!", restored);
	}

	@Test
	public void testRestoreMultiple() {
		String translatedText = "Hallo <x1>benutzername</x1>, Sie haben <x2>anzahl</x2> Nachrichten";
		String restored = ParameterProtector.restore(translatedText,
			java.util.List.of("username", "count"));

		assertEquals("Hallo {username}, Sie haben {count} Nachrichten", restored);
	}

	@Test
	public void testRestoreIgnoresTranslatedParameterNames() {
		// The translator may change parameter names inside tags, but we ignore this
		String translatedText = "Hallo <x1>nom-utilisateur</x1>, vous avez <x2>nombre</x2> messages";
		String restored = ParameterProtector.restore(translatedText,
			java.util.List.of("username", "count"));

		// Original parameter names are restored, ignoring translated names
		assertEquals("Hallo {username}, vous avez {count} messages", restored);
	}

	@Test
	public void testRestoreNoTags() {
		String translatedText = "Hello World!";
		String restored = ParameterProtector.restore(translatedText, java.util.List.of());

		assertEquals("Hello World!", restored);
	}

	@Test
	public void testRestoreReorderedTags() {
		// Translator may reorder parameters in some languages
		String translatedText = "Sie haben <x2>anzahl</x2> Nachrichten, <x1>benutzername</x1>";
		String restored = ParameterProtector.restore(translatedText,
			java.util.List.of("username", "count"));

		assertEquals("Sie haben {count} Nachrichten, {username}", restored);
	}

	@Test
	public void testFullRoundTrip() {
		String original = "Hello {username}, your balance is {amount}";

		// Protect
		ProtectedText protected_ = ParameterProtector.protect(original);
		assertEquals("Hello <x1>username</x1>, your balance is <x2>amount</x2>",
			protected_.getProtectedText());

		// Simulate translation (parameter names might change)
		String translated = "Hallo <x1>benutzername</x1>, Ihr Guthaben ist <x2>betrag</x2>";

		// Restore
		String restored = ParameterProtector.restore(translated, protected_.getParameters());
		assertEquals("Hallo {username}, Ihr Guthaben ist {amount}", restored);
	}

	@Test
	public void testRoundTripWithReordering() {
		String original = "{count} new messages for {username}";

		ProtectedText protected_ = ParameterProtector.protect(original);

		// German might reorder these
		String translated = "Für <x2>benutzername</x2> gibt es <x1>anzahl</x1> neue Nachrichten";

		String restored = ParameterProtector.restore(translated, protected_.getParameters());
		assertEquals("Für {username} gibt es {count} neue Nachrichten", restored);
	}

	@Test
	public void testProtectAndTranslate() {
		String original = "Hello {username}!";

		// Simulate a simple translation function
		String result = ParameterProtector.protectAndTranslate(original, protected_ -> {
			// Verify we receive protected text
			assertEquals("Hello <x1>username</x1>!", protected_);
			// Simulate translation
			return "Hallo <x1>benutzername</x1>!";
		});

		assertEquals("Hallo {username}!", result);
	}

	@Test
	public void testComplexRealWorld() {
		String original = "You bought {num} units of {product} for {price}. " +
			"Your total is {total} and you have {discount}% discount.";

		ProtectedText protected_ = ParameterProtector.protect(original);

		assertEquals(5, protected_.getParameters().size());
		assertEquals("num", protected_.getParameters().get(0));
		assertEquals("product", protected_.getParameters().get(1));
		assertEquals("price", protected_.getParameters().get(2));
		assertEquals("total", protected_.getParameters().get(3));
		assertEquals("discount", protected_.getParameters().get(4));

		// Simulate German translation
		String translated = "Sie haben <x1>anzahl</x1> Einheiten von <x2>produkt</x2> " +
			"für <x3>preis</x3> gekauft. Ihre Gesamtsumme beträgt <x4>gesamt</x4> " +
			"und Sie haben <x5>rabatt</x5>% Rabatt.";

		String restored = ParameterProtector.restore(translated, protected_.getParameters());

		assertEquals("Sie haben {num} Einheiten von {product} " +
			"für {price} gekauft. Ihre Gesamtsumme beträgt {total} " +
			"und Sie haben {discount}% Rabatt.", restored);
	}
}
