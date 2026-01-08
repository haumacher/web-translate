package de.haumacher.autotranslate.arb.model;

/**
 * Constants for standard ARB (Application Resource Bundle) property names.
 *
 * <p>
 * These constants define the well-known property names used in ARB files according to the
 * ARB specification. Using these constants ensures consistency and avoids typos when
 * working with ARB metadata.
 * </p>
 *
 * @see <a href="https://github.com/google/app-resource-bundle/wiki/ApplicationResourceBundleSpecification">ARB Specification</a>
 */
public final class ArbConstants {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private ArbConstants() {
		// Utility class
	}

	// Global attributes (prefixed with @@)

	/**
	 * Global attribute: Locale identifier (e.g., "en_US", "de_DE").
	 */
	public static final String LOCALE = "@@locale";

	/**
	 * Global attribute: Application context or screen name.
	 */
	public static final String CONTEXT = "@@context";

	/**
	 * Global attribute: Last modification timestamp.
	 */
	public static final String LAST_MODIFIED = "@@last_modified";

	// Resource attributes (appear in @resource_id objects)

	/**
	 * Resource attribute: Type of the resource ("text", "image", "css", etc.).
	 */
	public static final String TYPE = "type";

	/**
	 * Resource attribute: Hierarchical context using colon-separated notation.
	 */
	public static final String ATTR_CONTEXT = "context";

	/**
	 * Resource attribute: Human-readable description for translators.
	 */
	public static final String DESCRIPTION = "description";

	/**
	 * Resource attribute: Original source text before translation.
	 */
	public static final String SOURCE_TEXT = "source_text";

	/**
	 * Resource attribute: Screenshot URL or path showing the resource in use.
	 */
	public static final String SCREENSHOT = "screenshot";

	/**
	 * Resource attribute: Video URL or path demonstrating the resource usage.
	 */
	public static final String VIDEO = "video";

	/**
	 * Resource attribute: Placeholder definitions for variables in the resource value.
	 */
	public static final String PLACEHOLDERS = "placeholders";

	// Placeholder attributes (appear within placeholder objects)

	/**
	 * Placeholder attribute: Type of the placeholder value (e.g., "int", "String", "DateTime").
	 */
	public static final String PLACEHOLDER_TYPE = "type";

	/**
	 * Placeholder attribute: Human-readable description of the placeholder.
	 */
	public static final String PLACEHOLDER_DESCRIPTION = "description";

	/**
	 * Placeholder attribute: Example value illustrating the placeholder.
	 */
	public static final String PLACEHOLDER_EXAMPLE = "example";

	/**
	 * Checks if a property name is a standard ARB resource attribute.
	 *
	 * @param propertyName The property name to check
	 * @return true if it's a standard resource attribute
	 */
	public static boolean isStandardResourceAttribute(String propertyName) {
		return TYPE.equals(propertyName)
			|| ATTR_CONTEXT.equals(propertyName)
			|| DESCRIPTION.equals(propertyName)
			|| SOURCE_TEXT.equals(propertyName)
			|| SCREENSHOT.equals(propertyName)
			|| VIDEO.equals(propertyName)
			|| PLACEHOLDERS.equals(propertyName);
	}

	/**
	 * Checks if a property name is a standard ARB placeholder attribute.
	 *
	 * @param propertyName The property name to check
	 * @return true if it's a standard placeholder attribute
	 */
	public static boolean isStandardPlaceholderAttribute(String propertyName) {
		return PLACEHOLDER_TYPE.equals(propertyName)
			|| PLACEHOLDER_DESCRIPTION.equals(propertyName)
			|| PLACEHOLDER_EXAMPLE.equals(propertyName);
	}
}
