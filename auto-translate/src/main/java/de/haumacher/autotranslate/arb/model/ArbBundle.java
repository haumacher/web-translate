package de.haumacher.autotranslate.arb.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level container for an ARB (Application Resource Bundle) file.
 *
 * <p>
 * An ARB bundle contains:
 * <ul>
 *   <li>Global attributes (@@locale, @@context, etc.)</li>
 *   <li>Resource entries (translatable strings)</li>
 *   <li>Resource attributes (metadata for each resource)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example ARB structure:
 * <pre>
 * {
 *   "@@locale": "en_US",
 *   "@@context": "HomePage",
 *   "MSG_HELLO": "Hello!",
 *   "@MSG_HELLO": {
 *     "type": "text",
 *     "description": "greeting message"
 *   }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * This class uses {@link LinkedHashMap} to preserve the insertion order of resources,
 * which is important for maintaining a consistent file structure.
 * </p>
 */
public class ArbBundle {

	private Map<String, String> _globalAttributes;
	private Map<String, ArbResource> _resources;

	/**
	 * Creates a new empty ARB bundle.
	 */
	public ArbBundle() {
		_globalAttributes = new LinkedHashMap<>();
		_resources = new LinkedHashMap<>();
	}

	/**
	 * Global attributes prefixed with {@code @@}.
	 *
	 * <p>
	 * Common global attributes:
	 * <ul>
	 *   <li>{@code @@locale} - Locale identifier (e.g., "en_US", "de_DE")</li>
	 *   <li>{@code @@context} - Bundle-level context description</li>
	 *   <li>{@code @@last_modified} - ISO8601 timestamp</li>
	 *   <li>{@code @@author} - Creator or translator information</li>
	 * </ul>
	 * </p>
	 */
	public Map<String, String> getGlobalAttributes() {
		return _globalAttributes;
	}

	public void setGlobalAttributes(Map<String, String> globalAttributes) {
		_globalAttributes = globalAttributes != null ? globalAttributes : new LinkedHashMap<>();
	}

	/**
	 * Sets a global attribute.
	 *
	 * @param name  Attribute name (with or without {@code @@} prefix)
	 * @param value Attribute value
	 */
	public void setGlobalAttribute(String name, String value) {
		// Ensure the attribute name has @@ prefix
		String key = name.startsWith("@@") ? name : "@@" + name;
		_globalAttributes.put(key, value);
	}

	/**
	 * Gets a global attribute value.
	 *
	 * @param name Attribute name (with or without {@code @@} prefix)
	 * @return The attribute value, or {@code null} if not set
	 */
	public String getGlobalAttribute(String name) {
		String key = name.startsWith("@@") ? name : "@@" + name;
		return _globalAttributes.get(key);
	}

	/**
	 * Gets the locale identifier from {@code @@locale} attribute.
	 *
	 * @return The locale string (e.g., "en_US"), or {@code null} if not set
	 */
	public String getLocale() {
		return getGlobalAttribute("@@locale");
	}

	/**
	 * Sets the locale identifier in {@code @@locale} attribute.
	 */
	public void setLocale(String locale) {
		setGlobalAttribute("@@locale", locale);
	}

	/**
	 * Gets the context from {@code @@context} attribute.
	 *
	 * @return The context string, or {@code null} if not set
	 */
	public String getContext() {
		return getGlobalAttribute("@@context");
	}

	/**
	 * Sets the context in {@code @@context} attribute.
	 */
	public void setContext(String context) {
		setGlobalAttribute("@@context", context);
	}

	/**
	 * Map of resource IDs to resource entries.
	 *
	 * <p>
	 * The map maintains insertion order to preserve the structure of the original ARB file.
	 * </p>
	 */
	public Map<String, ArbResource> getResources() {
		return _resources;
	}

	public void setResources(Map<String, ArbResource> resources) {
		_resources = resources != null ? resources : new LinkedHashMap<>();
	}

	/**
	 * Adds a resource to this bundle.
	 */
	public void addResource(ArbResource resource) {
		_resources.put(resource.getId(), resource);
	}

	/**
	 * Gets a resource by its ID.
	 *
	 * @param id Resource identifier
	 * @return The resource, or {@code null} if not found
	 */
	public ArbResource getResource(String id) {
		return _resources.get(id);
	}

	/**
	 * Checks if this bundle contains a resource with the given ID.
	 */
	public boolean hasResource(String id) {
		return _resources.containsKey(id);
	}

	/**
	 * Gets the number of resources in this bundle.
	 */
	public int getResourceCount() {
		return _resources.size();
	}

	@Override
	public String toString() {
		return "ArbBundle{" +
			"locale='" + getLocale() + '\'' +
			", resources=" + _resources.size() +
			", globalAttributes=" + _globalAttributes.size() +
			'}';
	}
}
