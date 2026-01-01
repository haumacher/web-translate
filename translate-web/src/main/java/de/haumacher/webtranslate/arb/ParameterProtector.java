package de.haumacher.webtranslate.arb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	// Pattern to match ARB parameters: {paramName}
	// Excludes {@content} which is a special ARB syntax for masked content
	private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{(?!@)([^}]+)\\}");

	// Pattern to match XML tags: <xN>...</xN>
	private static final Pattern TAG_PATTERN = Pattern.compile("<x(\\d+)>.*?</x\\1>");

	/**
	 * Holds information about a translation with protected parameters.
	 */
	public static class ProtectedText {
		private final String protectedText;
		private final List<String> parameters;

		public ProtectedText(String protectedText, List<String> parameters) {
			this.protectedText = protectedText;
			this.parameters = parameters;
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
		public List<String> getParameters() {
			return parameters;
		}
	}

	/**
	 * Protects parameters in the text by replacing them with numbered XML tags.
	 *
	 * <p>
	 * Example:
	 * <pre>
	 * Input:  "Hello {username}, you have {count} messages"
	 * Output: "Hello &lt;x1&gt;username&lt;/x1&gt;, you have &lt;x2&gt;count&lt;/x2&gt; messages"
	 * </pre>
	 * </p>
	 *
	 * @param text The original text with ARB parameters
	 * @return ProtectedText containing the protected text and parameter list
	 */
	public static ProtectedText protect(String text) {
		List<String> parameters = new ArrayList<>();
		Matcher matcher = PARAMETER_PATTERN.matcher(text);
		StringBuffer result = new StringBuffer();

		int paramIndex = 1;
		while (matcher.find()) {
			String paramName = matcher.group(1);
			parameters.add(paramName);

			// Replace {paramName} with <xN>paramName</xN>
			String replacement = "<x" + paramIndex + ">" + paramName + "</x" + paramIndex + ">";
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));

			paramIndex++;
		}
		matcher.appendTail(result);

		return new ProtectedText(result.toString(), parameters);
	}

	/**
	 * Restores original parameters in the translated text.
	 *
	 * <p>
	 * Replaces XML tags with the original parameter syntax. The content inside
	 * the XML tags is ignored (may have been translated), and the original
	 * parameter names are restored.
	 * </p>
	 *
	 * <p>
	 * Example:
	 * <pre>
	 * Translated: "Hallo &lt;x1&gt;benutzername&lt;/x1&gt;, Sie haben &lt;x2&gt;anzahl&lt;/x2&gt; Nachrichten"
	 * Parameters: ["username", "count"]
	 * Output:     "Hallo {username}, Sie haben {count} Nachrichten"
	 * </pre>
	 * </p>
	 *
	 * @param translatedText The translated text with XML tags
	 * @param parameters     The original parameter names in order
	 * @return The text with original ARB parameter syntax restored
	 */
	public static String restore(String translatedText, List<String> parameters) {
		Matcher matcher = TAG_PATTERN.matcher(translatedText);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String indexStr = matcher.group(1);
			int index = Integer.parseInt(indexStr);

			// Get original parameter name (index is 1-based)
			if (index > 0 && index <= parameters.size()) {
				String originalParam = parameters.get(index - 1);
				String replacement = "{" + originalParam + "}";
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
	 * Convenience method to protect, translate (via callback), and restore.
	 *
	 * @param text              The original text with parameters
	 * @param translationFunction A function that translates the protected text
	 * @return The translated text with original parameters restored
	 */
	public static String protectAndTranslate(String text,
			java.util.function.Function<String, String> translationFunction) {
		ProtectedText protected_ = protect(text);
		String translated = translationFunction.apply(protected_.getProtectedText());
		return restore(translated, protected_.getParameters());
	}
}
