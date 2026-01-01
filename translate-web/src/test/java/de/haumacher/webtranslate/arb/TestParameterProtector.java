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
		ProtectedText protection = ParameterProtector.protect(text);

		assertEquals("Hello <x1>username</x1>!", protection.getProtectedText());
		assertEquals(1, protection.getParameters().size());
		assertEquals("username", protection.getParameters().get(0));
	}

	@Test
	public void testProtectMultipleParameters() {
		String text = "Hello {username}, you have {count} messages";
		ProtectedText protection = ParameterProtector.protect(text);

		assertEquals("Hello <x1>username</x1>, you have <x2>count</x2> messages",
			protection.getProtectedText());
		assertEquals(2, protection.getParameters().size());
		assertEquals("username", protection.getParameters().get(0));
		assertEquals("count", protection.getParameters().get(1));
	}

	@Test
	public void testProtectNoParameters() {
		String text = "Hello World!";
		ProtectedText protection = ParameterProtector.protect(text);

		assertEquals("Hello World!", protection.getProtectedText());
		assertEquals(0, protection.getParameters().size());
	}

	@Test
	public void testProtectComplexParameters() {
		String text = "Your balance is {amount_usd} and you have {pending-count} pending items";
		ProtectedText protection = ParameterProtector.protect(text);

		assertEquals(
			"Your balance is <x1>amount_usd</x1> and you have <x2>pending-count</x2> pending items",
			protection.getProtectedText());
		assertEquals(2, protection.getParameters().size());
		assertEquals("amount_usd", protection.getParameters().get(0));
		assertEquals("pending-count", protection.getParameters().get(1));
	}

	@Test
	public void testProtectMaskedContent() {
		// {@content} is special ARB syntax for masked content, should NOT be treated as parameter
		String text = "Some text {@content} with markup";
		ProtectedText protection = ParameterProtector.protect(text);

		assertEquals("Some text {@content} with markup", protection.getProtectedText());
		assertEquals(0, protection.getParameters().size());
	}

	@Test
	public void testRestoreSimple() {
		String original = "Hello {username}!";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Simulate translation
		String translatedText = "Hallo <x1>benutzername</x1>!";

		// Restore
		String restored = protection.restore(translatedText);

		assertEquals("Hallo {username}!", restored);
	}

	@Test
	public void testRestoreMultiple() {
		String original = "Hello {username}, you have {count} messages";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Simulate translation
		String translatedText = "Hallo <x1>benutzername</x1>, Sie haben <x2>anzahl</x2> Nachrichten";

		// Restore
		String restored = protection.restore(translatedText);

		assertEquals("Hallo {username}, Sie haben {count} Nachrichten", restored);
	}

	@Test
	public void testRestoreIgnoresTranslatedParameterNames() {
		String original = "Hello {username}, you have {count} messages";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Simulate translation where the translator changed parameter names inside tags
		// (this should be ignored)
		String translatedText = "Hallo <x1>nom-utilisateur</x1>, vous avez <x2>nombre</x2> messages";

		// Restore - original parameter names are restored, ignoring translated names
		String restored = protection.restore(translatedText);

		assertEquals("Hallo {username}, vous avez {count} messages", restored);
	}

	@Test
	public void testRestoreNoTags() {
		String original = "Hello World!";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Simulate translation (no parameters to protect)
		String translatedText = "Hallo Welt!";

		// Restore
		String restored = protection.restore(translatedText);

		assertEquals("Hallo Welt!", restored);
	}

	@Test
	public void testRestoreReorderedTags() {
		String original = "{username} has {count} messages";

		// Protect
		ProtectedText protection = ParameterProtector.protect(original);

		// Simulate translation where parameters are reordered
		String translatedText = "Sie haben <x2>anzahl</x2> Nachrichten, <x1>benutzername</x1>";

		// Restore
		String restored = protection.restore(translatedText);

		assertEquals("Sie haben {count} Nachrichten, {username}", restored);
	}

	@Test
	public void testFullRoundTrip() {
		String original = "Hello {username}, your balance is {amount}";

		// Protect
		ProtectedText protection =ParameterProtector.protect(original);
		assertEquals("Hello <x1>username</x1>, your balance is <x2>amount</x2>",
			protection.getProtectedText());

		// Simulate translation (parameter names might change)
		String translated = "Hallo <x1>benutzername</x1>, Ihr Guthaben ist <x2>betrag</x2>";

		// Restore
		String restored = ParameterProtector.restore(translated, protection.getParameters());
		assertEquals("Hallo {username}, Ihr Guthaben ist {amount}", restored);
	}

	@Test
	public void testRoundTripWithReordering() {
		String original = "{count} new messages for {username}";

		ProtectedText protection =ParameterProtector.protect(original);

		// German might reorder these
		String translated = "Für <x2>benutzername</x2> gibt es <x1>anzahl</x1> neue Nachrichten";

		String restored = ParameterProtector.restore(translated, protection.getParameters());
		assertEquals("Für {username} gibt es {count} neue Nachrichten", restored);
	}

	@Test
	public void testProtectAndTranslate() {
		String original = "Hello {username}!";

		// Simulate a simple translation function
		String result = ParameterProtector.translateWithParameterProtection(original, protected_ -> {
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

		ProtectedText protection =ParameterProtector.protect(original);

		assertEquals(5, protection.getParameters().size());
		assertEquals("num", protection.getParameters().get(0));
		assertEquals("product", protection.getParameters().get(1));
		assertEquals("price", protection.getParameters().get(2));
		assertEquals("total", protection.getParameters().get(3));
		assertEquals("discount", protection.getParameters().get(4));

		// Simulate German translation
		String translated = "Sie haben <x1>anzahl</x1> Einheiten von <x2>produkt</x2> " +
			"für <x3>preis</x3> gekauft. Ihre Gesamtsumme beträgt <x4>gesamt</x4> " +
			"und Sie haben <x5>rabatt</x5>% Rabatt.";

		String restored = ParameterProtector.restore(translated, protection.getParameters());

		assertEquals("Sie haben {num} Einheiten von {product} " +
			"für {price} gekauft. Ihre Gesamtsumme beträgt {total} " +
			"und Sie haben {discount}% Rabatt.", restored);
	}
}
