package de.haumacher.autotranslate.arb.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Metadata for a placeholder variable in an ARB resource value.
 *
 * <p>
 * Placeholders appear in resource values using curly brace syntax, e.g., {@code {COST}}.
 * This class holds the metadata that helps translators understand how to use the placeholder.
 * </p>
 *
 * <p>
 * Example ARB entry:
 * </p>
 *
 * <pre>
 * "FOO_123": "Your pending cost is {COST}",
 * "@FOO_123": {
 *   "placeholders": {
 *     "COST": {
 *       "type": "double",
 *       "example": "$123.45",
 *       "description": "cost presented with currency symbol",
 *       "x-custom": "custom value"
 *     }
 *   }
 * }
 * </pre>
 */
public class ArbPlaceholder {

	private String _name;
	private String _type;
	private String _description;
	private String _example;
	private Map<String, String> _customAttributes;

	/**
	 * Creates a new placeholder with all metadata.
	 *
	 * @param name        The placeholder identifier (e.g., "COST", "num")
	 * @param type        The type of the placeholder (e.g., "int", "String", "DateTime")
	 * @param description Usage description for translators
	 * @param example     Example value to illustrate the placeholder
	 */
	public ArbPlaceholder(String name, String type, String description, String example) {
		_name = name;
		_type = type;
		_description = description;
		_example = example;
	}

	/**
	 * Creates a new placeholder with description and example.
	 *
	 * @param name        The placeholder identifier (e.g., "COST", "num")
	 * @param description Usage description for translators
	 * @param example     Example value to illustrate the placeholder
	 */
	public ArbPlaceholder(String name, String description, String example) {
		this(name, null, description, example);
	}

	/**
	 * Creates a new placeholder with minimal metadata.
	 *
	 * @param name The placeholder identifier
	 */
	public ArbPlaceholder(String name) {
		this(name, null, null);
	}

	/**
	 * The placeholder identifier as it appears in the resource value.
	 */
	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}

	/**
	 * The type of the placeholder value (e.g., "int", "String", "DateTime").
	 */
	public String getType() {
		return _type;
	}

	public void setType(String type) {
		_type = type;
	}

	/**
	 * Usage description for translators explaining what this placeholder represents.
	 */
	public String getDescription() {
		return _description;
	}

	public void setDescription(String description) {
		_description = description;
	}

	/**
	 * Example value illustrating how the placeholder will be replaced at runtime.
	 */
	public String getExample() {
		return _example;
	}

	public void setExample(String example) {
		_example = example;
	}

	/**
	 * Sets an attribute value.
	 *
	 * <p>
	 * If the attribute name is a standard ARB placeholder property (type, description, example),
	 * it will be set on the corresponding dedicated field. Otherwise, it will be stored as a
	 * custom attribute. This provides a unified API for setting both standard and custom properties.
	 * </p>
	 *
	 * @param name  The attribute name (use {@link ArbConstants} for standard properties)
	 * @param value The attribute value
	 */
	public void setAttribute(String name, String value) {
		// Redirect standard properties to their dedicated fields
		if (ArbConstants.isStandardPlaceholderAttribute(name)) {
			switch (name) {
				case ArbConstants.PLACEHOLDER_TYPE:
					setType(value);
					break;
				case ArbConstants.PLACEHOLDER_DESCRIPTION:
					setDescription(value);
					break;
				case ArbConstants.PLACEHOLDER_EXAMPLE:
					setExample(value);
					break;
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
	 * Gets an attribute value.
	 *
	 * <p>
	 * This method works for both standard and custom attributes, providing a unified API.
	 * For standard properties (type, description, example), it returns the value from the
	 * dedicated field. For custom properties, it returns the value from the custom attributes map.
	 * </p>
	 *
	 * @param name The attribute name (use {@link ArbConstants} for standard properties)
	 * @return The attribute value, or null if not set
	 */
	public String getAttribute(String name) {
		// Check standard properties first
		if (ArbConstants.isStandardPlaceholderAttribute(name)) {
			switch (name) {
				case ArbConstants.PLACEHOLDER_TYPE:
					return getType();
				case ArbConstants.PLACEHOLDER_DESCRIPTION:
					return getDescription();
				case ArbConstants.PLACEHOLDER_EXAMPLE:
					return getExample();
			}
		}
		// Fall back to custom attributes
		return _customAttributes != null ? _customAttributes.get(name) : null;
	}

	/**
	 * Returns all custom attribute entries.
	 *
	 * @return Set of custom attribute entries
	 */
	public Set<Map.Entry<String, String>> customAttributeEntries() {
		if (_customAttributes == null) {
			return Set.of();
		}
		return _customAttributes.entrySet();
	}

	/**
	 * Checks if this placeholder has any custom attributes.
	 *
	 * @return true if custom attributes exist
	 */
	public boolean hasCustomAttributes() {
		return _customAttributes != null && !_customAttributes.isEmpty();
	}

	@Override
	public String toString() {
		return "ArbPlaceholder{" +
			"name='" + _name + '\'' +
			", type='" + _type + '\'' +
			", description='" + _description + '\'' +
			", example='" + _example + '\'' +
			'}';
	}
}
