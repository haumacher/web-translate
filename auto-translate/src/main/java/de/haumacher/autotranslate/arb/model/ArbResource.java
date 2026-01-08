package de.haumacher.autotranslate.arb.model;

/**
 * A single resource entry in an ARB bundle.
 *
 * <p>
 * Represents a translatable string with its identifier, value, and optional metadata.
 * In ARB files, resources appear as simple key-value pairs, with metadata in separate
 * {@code @}-prefixed entries.
 * </p>
 *
 * <p>
 * Example ARB entries:
 * </p>
 * 
 * <pre>
 * "MSG_HELLO": "Hello {username}!",
 * "@MSG_HELLO": {
 *   "description": "greeting message",
 *   "placeholders": {
 *     "username": {
 *       "example": "John"
 *     }
 *   }
 * }
 * </pre>
 */
public class ArbResource {

	private String _id;
	private String _value;
	private ArbResourceAttributes _attributes;

	/**
	 * Creates a new resource with an ID and value.
	 *
	 * @param id    Resource identifier (the key in ARB file)
	 * @param value Translatable content (the string value)
	 */
	public ArbResource(String id, String value) {
		_id = id;
		_value = value;
		_attributes = null;
	}

	/**
	 * Creates a new resource with ID, value, and metadata.
	 *
	 * @param id         Resource identifier
	 * @param value      Translatable content
	 * @param attributes Metadata for this resource
	 */
	public ArbResource(String id, String value, ArbResourceAttributes attributes) {
		_id = id;
		_value = value;
		_attributes = attributes;
	}

	/**
	 * The resource identifier used as the key in the ARB file.
	 *
	 * <p>
	 * This follows the target language's naming conventions and uniquely identifies
	 * the resource within the bundle.
	 * </p>
	 */
	public String getId() {
		return _id;
	}

	public void setId(String id) {
		_id = id;
	}

	/**
	 * The translatable string content.
	 *
	 * <p>
	 * May contain placeholders in curly braces (e.g., {@code {username}}) or
	 * masked content (e.g., {@code {@content}}).
	 * </p>
	 */
	public String getValue() {
		return _value;
	}

	public void setValue(String value) {
		_value = value;
	}

	/**
	 * Optional metadata attributes for this resource.
	 *
	 * <p>
	 * Package-private: Internal use only by parser/writer. Users should use the
	 * delegation methods like {@link #getAttribute(String)}, {@link #getType()}, etc.
	 * </p>
	 *
	 * @return The attributes, or {@code null} if no metadata is present
	 */
	public ArbResourceAttributes getAttributes() {
		return _attributes;
	}

	/**
	 * Sets the attributes object.
	 *
	 * <p>
	 * Internal use only by parser. Users should use the
	 * delegation methods like {@link #setAttribute(String, String)}, {@link #setType(String)}, etc.
	 * </p>
	 *
	 * @param attributes The attributes object
	 */
	public void setAttributes(ArbResourceAttributes attributes) {
		_attributes = attributes;
	}

	/**
	 * Checks if this resource has any metadata.
	 */
	public boolean hasAttributes() {
		return _attributes != null && !_attributes.isEmpty();
	}

	// Convenience delegation methods for attribute access

	/**
	 * Gets an attribute value directly from this resource.
	 *
	 * <p>
	 * This is a convenience method that delegates to the internal attributes object.
	 * Works for both standard properties (type, context, description) and custom attributes.
	 * </p>
	 *
	 * @param name Attribute name (use {@link ArbConstants} for standard properties)
	 * @return The attribute value, or {@code null} if not set
	 * @see ArbResourceAttributes#getAttribute(String)
	 */
	public String getAttribute(String name) {
		return _attributes != null ? _attributes.getAttribute(name) : null;
	}

	/**
	 * Sets an attribute value directly on this resource.
	 *
	 * <p>
	 * This is a convenience method that delegates to the internal attributes object.
	 * If no attributes object exists yet, one will be created automatically.
	 * </p>
	 *
	 * @param name  Attribute name (use {@link ArbConstants} for standard properties)
	 * @param value Attribute value
	 * @see ArbResourceAttributes#setAttribute(String, String)
	 */
	public void setAttribute(String name, String value) {
		ensureAttributes();
		_attributes.setAttribute(name, value);
	}

	/**
	 * Gets the type attribute of this resource.
	 *
	 * @return The type ("text", "image", "css", etc.), or {@code null} if not set
	 */
	public String getType() {
		return _attributes != null ? _attributes.getType() : null;
	}

	/**
	 * Sets the type attribute of this resource.
	 *
	 * @param type The resource type
	 */
	public void setType(String type) {
		ensureAttributes();
		_attributes.setType(type);
	}

	/**
	 * Gets the description attribute of this resource.
	 *
	 * @return The description for translators, or {@code null} if not set
	 */
	public String getDescription() {
		return _attributes != null ? _attributes.getDescription() : null;
	}

	/**
	 * Sets the description attribute of this resource.
	 *
	 * @param description Human-readable description for translators
	 */
	public void setDescription(String description) {
		ensureAttributes();
		_attributes.setDescription(description);
	}

	/**
	 * Gets the context attribute of this resource.
	 *
	 * @return The hierarchical context, or {@code null} if not set
	 */
	public String getContext() {
		return _attributes != null ? _attributes.getContext() : null;
	}

	/**
	 * Sets the context attribute of this resource.
	 *
	 * @param context Hierarchical context using colon-separated notation
	 */
	public void setContext(String context) {
		ensureAttributes();
		_attributes.setContext(context);
	}

	/**
	 * Adds a placeholder to this resource.
	 *
	 * @param placeholder The placeholder to add
	 */
	public void addPlaceholder(ArbPlaceholder placeholder) {
		ensureAttributes();
		_attributes.addPlaceholder(placeholder);
	}

	/**
	 * Gets a placeholder by name.
	 *
	 * @param name The placeholder name
	 * @return The placeholder, or {@code null} if not found
	 */
	public ArbPlaceholder getPlaceholder(String name) {
		return _attributes != null ? _attributes.getPlaceholders().get(name) : null;
	}

	/**
	 * Ensures that an attributes object exists, creating one if necessary.
	 */
	private void ensureAttributes() {
		if (_attributes == null) {
			_attributes = new ArbResourceAttributes();
		}
	}

	@Override
	public String toString() {
		return "ArbResource{" +
			"id='" + _id + '\'' +
			", value='" + _value + '\'' +
			", hasAttributes=" + hasAttributes() +
			'}';
	}
}
