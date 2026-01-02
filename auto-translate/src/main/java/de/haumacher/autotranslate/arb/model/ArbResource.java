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
 * </p>
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
	 * @return The attributes, or {@code null} if no metadata is present
	 */
	public ArbResourceAttributes getAttributes() {
		return _attributes;
	}

	public void setAttributes(ArbResourceAttributes attributes) {
		_attributes = attributes;
	}

	/**
	 * Checks if this resource has any metadata.
	 */
	public boolean hasAttributes() {
		return _attributes != null && !_attributes.isEmpty();
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
