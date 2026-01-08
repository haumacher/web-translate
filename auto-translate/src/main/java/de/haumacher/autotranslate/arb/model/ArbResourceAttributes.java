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
 * </p>
 * 
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
	 * Gets an attribute value.
	 *
	 * <p>
	 * This method works for both standard and custom attributes, providing a unified API.
	 * For standard properties (type, context, description, etc.), it returns the value from the
	 * dedicated field. For custom properties, it returns the value from the custom attributes map.
	 * </p>
	 *
	 * @param name Attribute name (use {@link ArbConstants} for standard properties)
	 * @return The attribute value, or {@code null} if not set
	 */
	public String getAttribute(String name) {
		// Check standard properties first
		if (ArbConstants.isStandardResourceAttribute(name)) {
			switch (name) {
				case ArbConstants.TYPE:
					return getType();
				case ArbConstants.ATTR_CONTEXT:
					return getContext();
				case ArbConstants.DESCRIPTION:
					return getDescription();
				case ArbConstants.SOURCE_TEXT:
					return getSourceText();
				case ArbConstants.SCREENSHOT:
					return getScreenshot();
				case ArbConstants.VIDEO:
					return getVideo();
				// Note: PLACEHOLDERS is not returned as a string
			}
		}
		// Fall back to custom attributes
		return _customAttributes == null ? null : _customAttributes.get(name);
	}


	/**
	 * Checks if a custom attribute is present.
	 *
	 * @param name Attribute name
	 * @return {@code true} if the attribute is set
	 */
	public boolean hasCustomAttribute(String name) {
		return _customAttributes != null && _customAttributes.containsKey(name);
	}

	/**
	 * Sets an attribute value.
	 *
	 * <p>
	 * If the attribute name is a standard ARB resource property (type, context, description, etc.),
	 * it will be set on the corresponding dedicated field. Otherwise, it will be stored as a
	 * custom attribute. This provides a unified API for setting both standard and custom properties.
	 * </p>
	 *
	 * @param name  Attribute name (use {@link ArbConstants} for standard properties)
	 * @param value Attribute value
	 */
	public void setAttribute(String name, String value) {
		// Redirect standard properties to their dedicated fields
		if (ArbConstants.isStandardResourceAttribute(name)) {
			switch (name) {
				case ArbConstants.TYPE:
					setType(value);
					break;
				case ArbConstants.ATTR_CONTEXT:
					setContext(value);
					break;
				case ArbConstants.DESCRIPTION:
					setDescription(value);
					break;
				case ArbConstants.SOURCE_TEXT:
					setSourceText(value);
					break;
				case ArbConstants.SCREENSHOT:
					setScreenshot(value);
					break;
				case ArbConstants.VIDEO:
					setVideo(value);
					break;
				// Note: "placeholders" is not set via this method as it's a complex object
			}
		} else {
			// Store as custom attribute
			if (_customAttributes == null) {
				_customAttributes = new HashMap<>();
			}
			_customAttributes.put(name, value);
		}
	}


	/**
	 * Removes a custom attribute.
	 *
	 * @param name Attribute name
	 * @return The previous value, or {@code null} if not set
	 */
	public String removeCustomAttribute(String name) {
		return _customAttributes == null ? null : _customAttributes.remove(name);
	}

	/**
	 * Internal method for iteration over custom attributes.
	 * Used by {@link de.haumacher.autotranslate.arb.io.ArbWriter} for serialization.
	 *
	 * @return Iterator over custom attribute entries, or empty iterator if no custom attributes
	 */
	public Iterable<Map.Entry<String, String>> customAttributeEntries() {
		if (_customAttributes == null || _customAttributes.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		return _customAttributes.entrySet();
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
			(_customAttributes == null || _customAttributes.isEmpty());
	}

	@Override
	public String toString() {
		return "ArbResourceAttributes{" +
			"type='" + _type + '\'' +
			", context='" + _context + '\'' +
			", description='" + _description + '\'' +
			", placeholders=" + _placeholders.size() +
			", customAttributes=" + (_customAttributes == null ? 0 : _customAttributes.size()) +
			'}';
	}
}
