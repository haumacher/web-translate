package de.haumacher.webtranslate.arb;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.haumacher.webtranslate.arb.IcuMessageParser.ComplexParameter;
import de.haumacher.webtranslate.arb.IcuMessageParser.MessagePart;
import de.haumacher.webtranslate.arb.IcuMessageParser.SelectorCase;
import de.haumacher.webtranslate.arb.IcuMessageParser.SimpleParameter;
import de.haumacher.webtranslate.arb.IcuMessageParser.TextPart;

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
		private final List<MessagePart> originalParts; // Store original structure

		public ProtectedText(String protectedText, List<MessagePart> originalParts) {
			assert originalParts != null;
			this.protectedText = protectedText;
			this.originalParts = originalParts;
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
			return extractParameterNames(originalParts);
		}

		/**
		 * The original parsed ICU message parts (null for simple parameters).
		 */
		public List<MessagePart> getOriginalParts() {
			return originalParts;
		}

		/**
		 * Restores original structure from translated text.
		 *
		 * @return The text with original structure restored
		 */
		public String restore() {
			if (originalParts != null) {
				// Use original parts to reconstruct with proper structure
				return restoreWithStructure(protectedText, originalParts, getParameters());
			} else {
				// Simple parameter restoration
				return ParameterProtector.restore(protectedText, getParameters());
			}
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
			for (MessagePart part : originalParts) {
				translatedParts.add(part.translate(translationFunction));
			}

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
		// Try to parse as ICU MessageFormat
		try {
			List<MessagePart> parts = IcuMessageParser.parse(text);
			String protectedText = IcuMessageParser.toProtectedText(parts);

			// Store original parts for reconstruction during restore
			return new ProtectedText(protectedText, parts);
		} catch (Exception e) {
			// Fallback to simple regex-based protection
			return protectSimple(text);
		}
	}

	/**
	 * Simple regex-based protection (fallback for non-ICU messages).
	 */
	private static ProtectedText protectSimple(String text) {
		List<MessagePart> parts = new ArrayList<>();
		Matcher matcher = PARAMETER_PATTERN.matcher(text);
		StringBuffer result = new StringBuffer();

		int lastEnd = 0;
		int paramIndex = 1;
		while (matcher.find()) {
			// Add text before parameter
			if (matcher.start() > lastEnd) {
				parts.add(new TextPart(text.substring(lastEnd, matcher.start())));
			}

			String paramName = matcher.group(1);
			parts.add(new SimpleParameter(paramName));

			// Replace {paramName} with <xN>paramName</xN>
			String replacement = "<x" + paramIndex + ">" + paramName + "</x" + paramIndex + ">";
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));

			lastEnd = matcher.end();
			paramIndex++;
		}
		matcher.appendTail(result);

		// Add remaining text
		if (lastEnd < text.length()) {
			parts.add(new TextPart(text.substring(lastEnd)));
		}

		return new ProtectedText(result.toString(), parts);
	}

	/**
	 * Extracts parameter names from message parts.
	 */
	private static List<String> extractParameterNames(List<MessagePart> parts) {
		List<String> parameters = new ArrayList<>();
		if (parts != null) {
			extractParameterNamesFromParts(parts, parameters);
		}
		return parameters;
	}

	/**
	 * Recursively extracts parameter names from message parts.
	 */
	private static void extractParameterNamesFromParts(List<MessagePart> parts, List<String> parameters) {
		for (MessagePart part : parts) {
			if (part instanceof IcuMessageParser.ParameterPart) {
				parameters.add(((IcuMessageParser.ParameterPart) part).getName());
			}
		}
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
	 * Reconstructs the original ICU message structure with translated text.
	 */
	private static String restoreWithStructure(String translatedText, List<MessagePart> originalParts, List<String> parameters) {
		// Check if we have any ComplexFormat parts - if so, reconstruct from original structure
		boolean hasComplexFormat = originalParts.stream()
			.anyMatch(part -> part instanceof ComplexParameter);

		if (hasComplexFormat) {
			// For complex formats, reconstruct the full original structure
			// (Complex formats don't support translation of nested text yet)
			StringBuilder result = new StringBuilder();
			for (MessagePart part : originalParts) {
				result.append(reconstructPart(part));
			}
			return result.toString();
		} else {
			// For simple text with placeholders, use standard restore to apply translation
			return restore(translatedText, parameters);
		}
	}

	/**
	 * Reconstructs a single message part to its original form.
	 */
	private static String reconstructPart(MessagePart part) {
		if (part instanceof TextPart) {
			return ((TextPart) part).getText();
		} else if (part instanceof SimpleParameter) {
			return "{" + ((SimpleParameter) part).getName() + "}";
		} else if (part instanceof ComplexParameter) {
			ComplexParameter format = (ComplexParameter) part;
			StringBuilder result = new StringBuilder();
			result.append("{");
			result.append(format.getName());
			result.append(", ");
			result.append(format.getFormatType());
			result.append(",");
			for (SelectorCase case_ : format.getCases()) {
				result.append(" ");
				result.append(case_.getSelector());
				result.append("{");
				for (MessagePart casePart : case_.getParts()) {
					result.append(reconstructPart(casePart));
				}
				result.append("}");
			}
			result.append("}");
			return result.toString();
		}
		return "";
	}

	/**
	 * Convenience method to protect, translate (via callback), and restore.
	 *
	 * @param text              The original text with parameters
	 * @param translationFunction A function that translates the protected text
	 * @return The translated text with original parameters restored
	 */
	public static String translate(String text, Function<String, String> translationFunction) {
		return protect(text).translate(translationFunction).restore();
	}
}
