package de.haumacher.autotranslate.arb.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata attributes for an ARB resource entry.
 *
 * <p>
 * These attributes provide context and documentation for translators.
 * In ARB files, they appear as entries prefixed with {@code @}, e.g., {@code @MSG_HELLO}.
 * </p>
 *
 * <p>
 * Example ARB entry:
 * <pre>
 * "@MSG_HELLO": {
 *   "type": "text",
 *   "context": "HomePage:MainPanel",
 *   "description": "greeting message for users",
 *   "placeholders": {
 *     "username": {
 *       "description": "name of the user",
 *       "example": "John"
 *     }
 *   }
 * }
 * </pre>
 * </p>
 */
public class ArbResourceAttributes {

	private String _type;
	private String _context;
	private String _description;
	private String _sourceText;
	private Map<String, ArbPlaceholder> _placeholders;
	private String _screenshot;
	private String _video;
	private Map<String, String> _customAttributes;

	/**
	 * Creates new resource attributes with no metadata.
	 */
	public ArbResourceAttributes() {
		_placeholders = new HashMap<>();
		_customAttributes = new HashMap<>();
	}

	/**
	 * The resource type: "text", "image", or "css".
	 */
	public String getType() {
		return _type;
	}

	public void setType(String type) {
		_type = type;
	}

	/**
	 * Hierarchical context using colon-separated notation (e.g., "HomePage:MainPanel").
	 */
	public String getContext() {
		return _context;
	}

	public void setContext(String context) {
		_context = context;
	}

	/**
	 * Human-readable description for translators explaining when/how this resource is used.
	 */
	public String getDescription() {
		return _description;
	}

	public void setDescription(String description) {
		_description = description;
	}

	/**
	 * Original source text for tracking changes across versions.
	 */
	public String getSourceText() {
		return _sourceText;
	}

	public void setSourceText(String sourceText) {
		_sourceText = sourceText;
	}

	/**
	 * Map of placeholder names to their metadata.
	 *
	 * <p>
	 * When this map is populated, only placeholders listed here are considered
	 * replaceable variables. Unlisted placeholders are treated as literal text.
	 * </p>
	 */
	public Map<String, ArbPlaceholder> getPlaceholders() {
		return _placeholders;
	}

	public void setPlaceholders(Map<String, ArbPlaceholder> placeholders) {
		_placeholders = placeholders != null ? placeholders : new HashMap<>();
	}

	/**
	 * Adds a placeholder to this resource's metadata.
	 */
	public void addPlaceholder(ArbPlaceholder placeholder) {
		_placeholders.put(placeholder.getName(), placeholder);
	}

	/**
	 * URL reference to a screenshot showing this resource in context.
	 */
	public String getScreenshot() {
		return _screenshot;
	}

	public void setScreenshot(String screenshot) {
		_screenshot = screenshot;
	}

	/**
	 * URL reference to a video showing this resource in context.
	 */
	public String getVideo() {
		return _video;
	}

	public void setVideo(String video) {
		_video = video;
	}

	/**
	 * Custom attributes with {@code x-} prefix.
	 *
	 * <p>
	 * ARB specification requires custom attributes to be prefixed with {@code x-}
	 * (e.g., {@code x-version}, {@code x-priority}).
	 * </p>
	 */
	public Map<String, String> getCustomAttributes() {
		return _customAttributes;
	}

	public void setCustomAttributes(Map<String, String> customAttributes) {
		_customAttributes = customAttributes != null ? customAttributes : new HashMap<>();
	}

	/**
	 * Adds a custom attribute.
	 *
	 * @param name  Attribute name (should be prefixed with {@code x-})
	 * @param value Attribute value
	 */
	public void addCustomAttribute(String name, String value) {
		_customAttributes.put(name, value);
	}

	/**
	 * Checks if any metadata is present.
	 */
	public boolean isEmpty() {
		return _type == null &&
			_context == null &&
			_description == null &&
			_sourceText == null &&
			_screenshot == null &&
			_video == null &&
			_placeholders.isEmpty() &&
			_customAttributes.isEmpty();
	}

	@Override
	public String toString() {
		return "ArbResourceAttributes{" +
			"type='" + _type + '\'' +
			", context='" + _context + '\'' +
			", description='" + _description + '\'' +
			", placeholders=" + _placeholders.size() +
			", customAttributes=" + _customAttributes.size() +
			'}';
	}
}
