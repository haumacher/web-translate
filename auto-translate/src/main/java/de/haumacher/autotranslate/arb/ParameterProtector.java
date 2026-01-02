package de.haumacher.autotranslate.arb;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.haumacher.autotranslate.arb.IcuMessageParser.MessagePart;
import de.haumacher.autotranslate.arb.IcuMessageParser.ParameterPart;

/**
 * Protects ARB parameters from being translated by replacing them with XML-style placeholders.
 *
 * <p>
 * ARB resources can contain parameters in curly braces (e.g., {@code {username}}, {@code {count}}).
 * These parameter names should not be translated, as they are code identifiers. This class:
 * <ol>
 *   <li>Extracts parameters from text</li>
 *   <li>Replaces them with numbered XML tags (e.g., {@code <x1>username</x1>})</li>
 *   <li>After translation, restores the original parameter syntax</li>
 * </ol>
 * </p>
 *
 * <p>
 * Example workflow:
 * <pre>
 * Original:    "Hello {username}, you have {count} messages"
 * Protected:   "Hello &lt;x1&gt;username&lt;/x1&gt;, you have &lt;x2&gt;count&lt;/x2&gt; messages"
 * Translated:  "Hallo &lt;x1&gt;benutzername&lt;/x1&gt;, Sie haben &lt;x2&gt;anzahl&lt;/x2&gt; Nachrichten"
 * Restored:    "Hallo {username}, Sie haben {count} Nachrichten"
 * </pre>
 * </p>
 *
 * <p>
 * Note: The translator may change the text inside the XML tags (e.g., "username" → "benutzername"),
 * but this is ignored during restoration. We only preserve the original parameter names.
 * </p>
 */
public class ParameterProtector {

	// Pattern to match XML tags: <xN>...</xN>
	private static final Pattern TAG_PATTERN = Pattern.compile("<x(\\d+)>.*?</x\\1>");

	/**
	 * Holds information about a translation with protected parameters.
	 */
	public static class ProtectedText {
		private final String protectedText;
		private final List<MessagePart> parts; // Store original structure

		/**
		 * Creates a ProtectedText from pre-parsed message parts.
		 *
		 * @param protectedText The text with parameters replaced by XML tags
		 * @param originalParts The original parsed ICU message parts
		 */
		public ProtectedText(String protectedText, List<MessagePart> originalParts) {
			assert originalParts != null;
			this.protectedText = protectedText;
			this.parts = originalParts;
		}

		/**
		 * Creates a ProtectedText by parsing and protecting the given text.
		 *
		 * <p>
		 * This constructor handles both simple parameters and complex ICU MessageFormat syntax:
		 * <ul>
		 *   <li>Simple: {@code "Hello {username}"} → {@code "Hello <x1>username</x1>"}</li>
		 *   <li>Plural: {@code "{count, plural, =1{1 message} other{{count} messages}}"}</li>
		 * </ul>
		 * </p>
		 *
		 * <p>
		 * For complex ICU formats (plural, select), only translatable text is exposed
		 * while identifiers (parameter names, format types, selector keywords) are protected.
		 * </p>
		 *
		 * @param text The original text with ARB parameters
		 */
		public ProtectedText(String text) {
			List<MessagePart> parsedParts = IcuMessageParser.parse(text);
			this.protectedText = IcuMessageParser.toProtectedText(parsedParts);
			this.parts = parsedParts;
		}

		/**
		 * The text with parameters replaced by XML tags.
		 */
		public String getProtectedText() {
			return protectedText;
		}

		/**
		 * The original parameter names in order of appearance.
		 */
		public List<String> getParameterNames() {
			List<String> parameterNames = new ArrayList<>();
			for (MessagePart part : parts) {
				if (part instanceof ParameterPart parameter) {
					parameterNames.add(parameter.getName());
				}
			}
			return parameterNames;
		}

		/**
		 * The parsed ICU message parts.
		 */
		public List<MessagePart> getParts() {
			return parts;
		}

		/**
		 * Restores original structure from translated text.
		 *
		 * @return The text with original structure restored
		 */
		public String restore() {
			// Replace <xN>...</xN> placeholders in the translated protected text
			// with the reconstructed original parameters
			List<ParameterPart> parameters = new ArrayList<>();
			for (MessagePart part : parts) {
				if (part instanceof ParameterPart) {
					parameters.add((ParameterPart) part);
				}
			}

			Matcher matcher = TAG_PATTERN.matcher(protectedText);
			StringBuffer result = new StringBuffer();

			while (matcher.find()) {
				String indexStr = matcher.group(1);
				int index = Integer.parseInt(indexStr);

				// Get original parameter (index is 1-based)
				if (index > 0 && index <= parameters.size()) {
					ParameterPart originalParam = parameters.get(index - 1);
					String replacement = originalParam.reconstruct();
					matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
				} else {
					// Keep as-is if index is out of bounds (shouldn't happen)
					matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
				}
			}
			matcher.appendTail(result);

			return result.toString();
		}

		/**
		 * Applies translation to this protected text, including inner fragments.
		 *
		 * @param translationFunction The function to apply to all text fragments
		 * @return A new ProtectedText with translation applied
		 */
		public ProtectedText translate(java.util.function.Function<String, String> translationFunction) {
			// Translate the protected text itself
			String translatedProtectedText = translationFunction.apply(protectedText);

			List<MessagePart> translatedParts = new ArrayList<>();
			for (MessagePart part : parts) {
				translatedParts.add(part.translate(translationFunction));
			}

			return create(translatedProtectedText, translatedParts);
		}

		protected ProtectedText create(String translatedProtectedText, List<MessagePart> translatedParts) {
			return new ProtectedText(translatedProtectedText, translatedParts);
		}
	}

	/**
	 * Protects parameters in the text by replacing them with numbered XML tags.
	 *
	 * <p>
	 * This method handles both simple parameters and complex ICU MessageFormat syntax:
	 * <ul>
	 *   <li>Simple: {@code "Hello {username}"} → {@code "Hello <x1>username</x1>"}</li>
	 *   <li>Plural: {@code "{count, plural, =1{1 message} other{{count} messages}}"}</li>
	 * </ul>
	 * </p>
	 *
	 * <p>
	 * For complex ICU formats (plural, select), only translatable text is exposed
	 * while identifiers (parameter names, format types, selector keywords) are protected.
	 * </p>
	 *
	 * @param text The original text with ARB parameters
	 * @return ProtectedText containing the protected text and parameter list
	 */
	public static ProtectedText protect(String text) {
		return new ProtectedText(text);
	}

	/**
	 * Convenience method to protect, translate (via callback), and restore.
	 *
	 * @param text              The original text with parameters
	 * @param translationFunction A function that translates the protected text
	 * @return The translated text with original parameters restored
	 */
	public static String translate(String text, Function<String, String> translationFunction) {
		return new ProtectedText(text).translate(translationFunction).restore();
	}
}
