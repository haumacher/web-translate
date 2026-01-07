package de.haumacher.autotranslate.arb.io;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import de.haumacher.autotranslate.arb.ParameterProtector;
import de.haumacher.autotranslate.arb.ParameterProtector.ProtectedText;

/**
 * Parser for ICU MessageFormat syntax used in ARB files.
 *
 * <p>
 * ICU MessageFormat supports complex parameter syntax including:
 * </p>
 * 
 * <ul>
 *   <li>Simple placeholders: {@code {name}}</li>
 *   <li>Typed arguments: {@code {count, number}}</li>
 *   <li>Plural forms: {@code {count, plural, =1{one item} other{# items}}}</li>
 *   <li>Select forms: {@code {gender, select, male{his} female{her} other{their}}}</li>
 *   <li>Nested formats: Complex combinations of the above</li>
 * </ul>
 *
 * <p>
 * This parser identifies translatable text vs. non-translatable identifiers (keywords like
 * "plural", "select", "one", "other", etc.) and protects them appropriately for translation.
 * </p>
 *
 * <p>
 * Example:
 * </p>

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
 *
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
		private final String _name;

		public ParameterPart(String name) {
			_name = name;
		}

		public String getName() {
			return _name;
		}
	}

	/**
	 * Plain text that should be translated.
	 */
	public static class TextPart extends MessagePart {
		private final String _text;

		public TextPart(String text) {
			_text = text;
		}

		public String getText() {
			return _text;
		}

		@Override
		public ConversionResult toProtectedText(int nextParamIndex) {
			return new ConversionResult(_text, nextParamIndex);
		}

		@Override
		public MessagePart translate(java.util.function.Function<String, String> translationFunction) {
			// Text parts are never translated, instead the whole protected text with
			// placeholders is translated and the parameters are restored later on.
			return this;
		}

		@Override
		public String reconstruct() {
			throw new UnsupportedOperationException("A text part must not be reconstructed. Instead the translation must be taken from the translated protected text.");
		}

		@Override
		public String toString() {
			return "Text{" + _text + "}";
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
		private final String _formatType;
		private final List<SelectorCase> _cases;

		public ComplexParameter(String argumentName, String formatType, List<SelectorCase> cases) {
			super(argumentName);
			_formatType = formatType;
			_cases = cases;
		}

		public String getFormatType() {
			return _formatType;
		}

		public List<SelectorCase> getCases() {
			return _cases;
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
				translatedCases.add(originalCase.translate(translationFunction));
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
			for (SelectorCase selectorCase : getCases()) {
				result.append(" ");
				result.append(selectorCase.getSelector());
				result.append("{");
				result.append(selectorCase.restore());
				result.append("}");
			}
			result.append("}");
			return result.toString();
		}

		@Override
		public String toString() {
			return "ComplexFormat{" + getName() + ", " + _formatType + ", cases=" + _cases.size() + "}";
		}
	}

	/**
	 * A single case in a select/plural format.
	 */
	public static class SelectorCase extends ProtectedText {
		private final String _selector;  // e.g., "=1", "one", "other", "male"

		public SelectorCase(String selector, List<MessagePart> parts) {
			this(selector, IcuMessageParser.toProtectedText(parts), parts);
		}

		public SelectorCase(String selector, String protectedText, List<MessagePart> parts) {
			super(protectedText, parts);
			_selector = selector;
		}

		public String getSelector() {
			return _selector;
		}
		
		@Override
		public SelectorCase translate(Function<String, String> translationFunction) {
			return (SelectorCase) super.translate(translationFunction);
		}
		
		@Override
		protected SelectorCase create(String translatedProtectedText, List<MessagePart> translatedParts) {
			return new SelectorCase(_selector, translatedProtectedText, translatedParts);
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
		private final String _input;
		private int _pos;

		public Parser(String input) {
			_input = input;
			_pos = 0;
		}

		public List<MessagePart> parseMessage() {
			List<MessagePart> parts = new ArrayList<>();
			StringBuilder text = new StringBuilder();

			while (_pos < _input.length()) {
				char ch = _input.charAt(_pos);

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
					_pos++;
					if (_pos < _input.length() && _input.charAt(_pos) == '\'') {
						// Two apostrophes = literal apostrophe
						text.append('\'');
						_pos++;
					} else {
						// Quoted section - find closing apostrophe
						while (_pos < _input.length() && _input.charAt(_pos) != '\'') {
							text.append(_input.charAt(_pos));
							_pos++;
						}
						if (_pos < _input.length()) {
							_pos++; // skip closing '
						}
					}
				} else {
					text.append(ch);
					_pos++;
				}
			}

			// Save remaining text
			if (text.length() > 0) {
				parts.add(new TextPart(text.toString()));
			}

			return parts;
		}

		private MessagePart parsePlaceholder() {
			_pos++; // skip opening {

			// Read argument name
			String argumentName = readUntil(',', '}');

			if (_pos >= _input.length() || _input.charAt(_pos) == '}') {
				// Simple placeholder: {name}
				// Skip {@...} patterns (ARB masked content syntax) - treat as literal text
				if (argumentName.startsWith("@")) {
					_pos++; // skip closing }
					// Return as literal text, not a placeholder
					return new TextPart("{" + argumentName + "}");
				}
				_pos++; // skip closing }
				return new SimpleParameter(argumentName.trim());
			}

			// Complex format: {name, type, ...}
			_pos++; // skip comma

			String formatType = readUntil(',', '}').trim();

			if (_pos >= _input.length() || _input.charAt(_pos) == '}') {
				// Format with type but no style: {count, number}
				// Treat as simple placeholder for now
				_pos++; // skip closing }
				return new SimpleParameter(argumentName.trim());
			}

			_pos++; // skip comma

			// Check if this is a select/plural format
			if (formatType.equals("plural") || formatType.equals("select") || formatType.equals("selectordinal")) {
				List<SelectorCase> cases = parseSelectorCases();
				return new ComplexParameter(argumentName.trim(), formatType, cases);
			} else {
				// Other format types (number, date, time with styles)
				// Skip to end of placeholder for now
				readUntil('}');
				if (_pos < _input.length()) {
					_pos++; // skip closing }
				}
				return new SimpleParameter(argumentName.trim());
			}
		}

		private List<SelectorCase> parseSelectorCases() {
			List<SelectorCase> cases = new ArrayList<>();

			// Skip whitespace
			skipWhitespace();

			while (_pos < _input.length() && _input.charAt(_pos) != '}') {
				// Read selector keyword (=1, one, other, etc.)
				String selector = readUntil('{').trim();

				if (_pos >= _input.length()) {
					break;
				}

				_pos++; // skip opening {

				// Parse case content (may contain nested placeholders)
				List<MessagePart> caseParts = parseCaseContent();

				cases.add(new SelectorCase(selector, caseParts));

				skipWhitespace();
			}

			if (_pos < _input.length() && _input.charAt(_pos) == '}') {
				_pos++; // skip closing } of entire format
			}

			return cases;
		}

		private List<MessagePart> parseCaseContent() {
			List<MessagePart> parts = new ArrayList<>();
			StringBuilder text = new StringBuilder();
			int depth = 1; // We're already inside one {

			while (_pos < _input.length() && depth > 0) {
				char ch = _input.charAt(_pos);

				if (ch == '{') {
					// Save accumulated text
					if (text.length() > 0) {
						parts.add(new TextPart(text.toString()));
						text.setLength(0);
					}

					// Check if this is a nested placeholder or literal {
					int savedPos = _pos;
					MessagePart part = parsePlaceholder();

					if (part != null) {
						parts.add(part);
					} else {
						// Failed to parse, treat as literal
						_pos = savedPos;
						text.append(ch);
						_pos++;
					}
				} else if (ch == '}') {
					depth--;
					if (depth == 0) {
						// End of this case - skip the closing }
						_pos++;
						break;
					} else {
						text.append(ch);
						_pos++;
					}
				} else if (ch == '#') {
					// # is a special symbol in plural forms representing the number
					// Treat it as a placeholder
					if (text.length() > 0) {
						parts.add(new TextPart(text.toString()));
						text.setLength(0);
					}
					parts.add(new SimpleParameter("#"));
					_pos++;
				} else {
					text.append(ch);
					_pos++;
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

			while (_pos < _input.length()) {
				char ch = _input.charAt(_pos);

				for (char terminator : terminators) {
					if (ch == terminator) {
						return result.toString();
					}
				}

				result.append(ch);
				_pos++;
			}

			return result.toString();
		}

		private void skipWhitespace() {
			while (_pos < _input.length() && Character.isWhitespace(_input.charAt(_pos))) {
				_pos++;
			}
		}
	}
}
