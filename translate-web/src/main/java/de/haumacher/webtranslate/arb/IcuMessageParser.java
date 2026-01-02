package de.haumacher.webtranslate.arb;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for ICU MessageFormat syntax used in ARB files.
 *
 * <p>
 * ICU MessageFormat supports complex parameter syntax including:
 * <ul>
 *   <li>Simple placeholders: {@code {name}}</li>
 *   <li>Typed arguments: {@code {count, number}}</li>
 *   <li>Plural forms: {@code {count, plural, =1{one item} other{# items}}}</li>
 *   <li>Select forms: {@code {gender, select, male{his} female{her} other{their}}}</li>
 *   <li>Nested formats: Complex combinations of the above</li>
 * </ul>
 * </p>
 *
 * <p>
 * This parser identifies translatable text vs. non-translatable identifiers (keywords like
 * "plural", "select", "one", "other", etc.) and protects them appropriately for translation.
 * </p>
 *
 * <p>
 * Example:
 * <pre>
 * Input:  "{count, plural, =1{1 message} other{{count} messages}}"
 *
 * Parts to protect from translation:
 * - "count" (parameter name)
 * - "plural" (format type)
 * - "=1", "other" (selector keywords)
 *
 * Parts to translate:
 * - "1 message"
 * - "messages"
 * </pre>
 * </p>
 */
public class IcuMessageParser {

	/**
	 * Represents a parsed segment of ICU message text.
	 */
	public static abstract class MessagePart {
		/**
		 * Converts this part back to text for translation.
		 *
		 * @param nextParamIndex The next available parameter index for protection
		 * @return The converted text and the next available index
		 */
		public abstract ConversionResult toProtectedText(int nextParamIndex);

		/**
		 * Translates this message part recursively.
		 *
		 * @param translationFunction The function to apply to all text fragments
		 * @return A new translated MessagePart
		 */
		public abstract MessagePart translate(java.util.function.Function<String, String> translationFunction);

		/**
		 * Reconstructs this message part to its original ICU message format.
		 *
		 * @return The reconstructed ICU message text
		 */
		public abstract String reconstruct();
	}

	/**
	 * Base class for message parts that have a parameter name.
	 */
	public static abstract class ParameterPart extends MessagePart {
		private final String name;

		public ParameterPart(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * Plain text that should be translated.
	 */
	public static class TextPart extends MessagePart {
		private final String text;

		public TextPart(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public ConversionResult toProtectedText(int nextParamIndex) {
			return new ConversionResult(text, nextParamIndex);
		}

		@Override
		public MessagePart translate(java.util.function.Function<String, String> translationFunction) {
			return new TextPart(translationFunction.apply(getText()));
		}

		@Override
		public String reconstruct() {
			throw new UnsupportedOperationException("A text part must not be reconstructed. Instead the translation must be taken from the translated protected text.");
		}

		@Override
		public String toString() {
			return "Text{" + text + "}";
		}
	}

	/**
	 * A simple placeholder: {@code {name}} or {@code {0}}.
	 */
	public static class SimpleParameter extends ParameterPart {

		public SimpleParameter(String name) {
			super(name);
		}

		@Override
		public ConversionResult toProtectedText(int nextParamIndex) {
			String protection = "<x" + nextParamIndex + ">" + getName() + "</x" + nextParamIndex + ">";
			return new ConversionResult(protection, nextParamIndex + 1);
		}

		@Override
		public MessagePart translate(java.util.function.Function<String, String> translationFunction) {
			// Placeholders are not translated
			return this;
		}

		@Override
		public String reconstruct() {
			return "{" + getName() + "}";
		}

		@Override
		public String toString() {
			return "Placeholder{" + getName() + "}";
		}
	}

	/**
	 * A complex format with type and style: {@code {count, plural, ...}}.
	 */
	public static class ComplexParameter extends ParameterPart {
		private final String formatType;
		private final List<SelectorCase> cases;

		public ComplexParameter(String argumentName, String formatType, List<SelectorCase> cases) {
			super(argumentName);
			this.formatType = formatType;
			this.cases = cases;
		}

		public String getFormatType() {
			return formatType;
		}

		public List<SelectorCase> getCases() {
			return cases;
		}

		@Override
		public ConversionResult toProtectedText(int nextParamIndex) {
			// Complex format is treated as a single top-level parameter
			// Only the parameter name is protected, everything else (including nested content) is hidden
			String protection = "<x" + nextParamIndex + ">" + getName() + "</x" + nextParamIndex + ">";
			return new ConversionResult(protection, nextParamIndex + 1);
		}

		@Override
		public MessagePart translate(java.util.function.Function<String, String> translationFunction) {
			// Translate all cases recursively
			List<SelectorCase> translatedCases = new ArrayList<>();
			for (SelectorCase originalCase : getCases()) {
				List<MessagePart> translatedCaseParts = new ArrayList<>();
				for (MessagePart part : originalCase.getParts()) {
					translatedCaseParts.add(part.translate(translationFunction));
				}
				translatedCases.add(new SelectorCase(originalCase.getSelector(), translatedCaseParts));
			}
			return new ComplexParameter(getName(), getFormatType(), translatedCases);
		}

		@Override
		public String reconstruct() {
			StringBuilder result = new StringBuilder();
			result.append("{");
			result.append(getName());
			result.append(", ");
			result.append(getFormatType());
			result.append(",");
			for (SelectorCase case_ : getCases()) {
				result.append(" ");
				result.append(case_.getSelector());
				result.append("{");
				for (MessagePart casePart : case_.getParts()) {
					result.append(casePart.reconstruct());
				}
				result.append("}");
			}
			result.append("}");
			return result.toString();
		}

		@Override
		public String toString() {
			return "ComplexFormat{" + getName() + ", " + formatType + ", cases=" + cases.size() + "}";
		}
	}

	/**
	 * A single case in a select/plural format.
	 */
	public static class SelectorCase {
		private final String selector;  // e.g., "=1", "one", "other", "male"
		private final List<MessagePart> parts;

		public SelectorCase(String selector, List<MessagePart> parts) {
			this.selector = selector;
			this.parts = parts;
		}

		public String getSelector() {
			return selector;
		}

		public List<MessagePart> getParts() {
			return parts;
		}
	}

	/**
	 * Result of converting message parts to protected text.
	 */
	public static class ConversionResult {
		public final String text;
		public final int nextIndex;

		public ConversionResult(String text, int nextIndex) {
			this.text = text;
			this.nextIndex = nextIndex;
		}
	}

	/**
	 * Parses an ICU message string into structured parts.
	 *
	 * @param message The ICU message string
	 * @return List of message parts
	 */
	public static List<MessagePart> parse(String message) {
		Parser parser = new Parser(message);
		return parser.parseMessage();
	}

	/**
	 * Converts parsed message parts to protected text for translation.
	 *
	 * @param parts The parsed message parts
	 * @return Protected text with identifiers replaced by XML tags
	 */
	public static String toProtectedText(List<MessagePart> parts) {
		ConversionResult result = convertParts(parts, 1);
		return result.text;
	}

	private static ConversionResult convertParts(List<MessagePart> parts, int startIndex) {
		StringBuilder result = new StringBuilder();
		int currentIndex = startIndex;

		for (MessagePart part : parts) {
			ConversionResult partResult = part.toProtectedText(currentIndex);
			result.append(partResult.text);
			currentIndex = partResult.nextIndex;
		}

		return new ConversionResult(result.toString(), currentIndex);
	}

	/**
	 * Internal parser implementation.
	 */
	private static class Parser {
		private final String input;
		private int pos;

		public Parser(String input) {
			this.input = input;
			this.pos = 0;
		}

		public List<MessagePart> parseMessage() {
			List<MessagePart> parts = new ArrayList<>();
			StringBuilder text = new StringBuilder();

			while (pos < input.length()) {
				char ch = input.charAt(pos);

				if (ch == '{') {
					// Save accumulated text
					if (text.length() > 0) {
						parts.add(new TextPart(text.toString()));
						text.setLength(0);
					}

					// Parse placeholder or complex format
					MessagePart part = parsePlaceholder();
					if (part != null) {
						parts.add(part);
					}
				} else if (ch == '\'') {
					// Handle quoted text (escaping)
					pos++;
					if (pos < input.length() && input.charAt(pos) == '\'') {
						// Two apostrophes = literal apostrophe
						text.append('\'');
						pos++;
					} else {
						// Quoted section - find closing apostrophe
						while (pos < input.length() && input.charAt(pos) != '\'') {
							text.append(input.charAt(pos));
							pos++;
						}
						if (pos < input.length()) {
							pos++; // skip closing '
						}
					}
				} else {
					text.append(ch);
					pos++;
				}
			}

			// Save remaining text
			if (text.length() > 0) {
				parts.add(new TextPart(text.toString()));
			}

			return parts;
		}

		private MessagePart parsePlaceholder() {
			pos++; // skip opening {

			// Read argument name
			String argumentName = readUntil(',', '}');

			if (pos >= input.length() || input.charAt(pos) == '}') {
				// Simple placeholder: {name}
				// Skip {@...} patterns (ARB masked content syntax) - treat as literal text
				if (argumentName.startsWith("@")) {
					pos++; // skip closing }
					// Return as literal text, not a placeholder
					return new TextPart("{" + argumentName + "}");
				}
				pos++; // skip closing }
				return new SimpleParameter(argumentName.trim());
			}

			// Complex format: {name, type, ...}
			pos++; // skip comma

			String formatType = readUntil(',', '}').trim();

			if (pos >= input.length() || input.charAt(pos) == '}') {
				// Format with type but no style: {count, number}
				// Treat as simple placeholder for now
				pos++; // skip closing }
				return new SimpleParameter(argumentName.trim());
			}

			pos++; // skip comma

			// Check if this is a select/plural format
			if (formatType.equals("plural") || formatType.equals("select") || formatType.equals("selectordinal")) {
				List<SelectorCase> cases = parseSelectorCases();
				return new ComplexParameter(argumentName.trim(), formatType, cases);
			} else {
				// Other format types (number, date, time with styles)
				// Skip to end of placeholder for now
				readUntil('}');
				if (pos < input.length()) {
					pos++; // skip closing }
				}
				return new SimpleParameter(argumentName.trim());
			}
		}

		private List<SelectorCase> parseSelectorCases() {
			List<SelectorCase> cases = new ArrayList<>();

			// Skip whitespace
			skipWhitespace();

			while (pos < input.length() && input.charAt(pos) != '}') {
				// Read selector keyword (=1, one, other, etc.)
				String selector = readUntil('{').trim();

				if (pos >= input.length()) {
					break;
				}

				pos++; // skip opening {

				// Parse case content (may contain nested placeholders)
				List<MessagePart> caseParts = parseCaseContent();

				cases.add(new SelectorCase(selector, caseParts));

				skipWhitespace();
			}

			if (pos < input.length() && input.charAt(pos) == '}') {
				pos++; // skip closing } of entire format
			}

			return cases;
		}

		private List<MessagePart> parseCaseContent() {
			List<MessagePart> parts = new ArrayList<>();
			StringBuilder text = new StringBuilder();
			int depth = 1; // We're already inside one {

			while (pos < input.length() && depth > 0) {
				char ch = input.charAt(pos);

				if (ch == '{') {
					// Save accumulated text
					if (text.length() > 0) {
						parts.add(new TextPart(text.toString()));
						text.setLength(0);
					}

					// Check if this is a nested placeholder or literal {
					int savedPos = pos;
					MessagePart part = parsePlaceholder();

					if (part != null) {
						parts.add(part);
					} else {
						// Failed to parse, treat as literal
						pos = savedPos;
						text.append(ch);
						pos++;
					}
				} else if (ch == '}') {
					depth--;
					if (depth == 0) {
						// End of this case - skip the closing }
						pos++;
						break;
					} else {
						text.append(ch);
						pos++;
					}
				} else if (ch == '#') {
					// # is a special symbol in plural forms representing the number
					// Treat it as a placeholder
					if (text.length() > 0) {
						parts.add(new TextPart(text.toString()));
						text.setLength(0);
					}
					parts.add(new SimpleParameter("#"));
					pos++;
				} else {
					text.append(ch);
					pos++;
				}
			}

			// Save remaining text
			if (text.length() > 0) {
				parts.add(new TextPart(text.toString()));
			}

			return parts;
		}

		private String readUntil(char... terminators) {
			StringBuilder result = new StringBuilder();

			while (pos < input.length()) {
				char ch = input.charAt(pos);

				for (char terminator : terminators) {
					if (ch == terminator) {
						return result.toString();
					}
				}

				result.append(ch);
				pos++;
			}

			return result.toString();
		}

		private void skipWhitespace() {
			while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
				pos++;
			}
		}
	}
}
