package de.haumacher.autotranslate.arb;

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

	private String type;
	private String context;
	private String description;
	private String sourceText;
	private Map<String, ArbPlaceholder> placeholders;
	private String screenshot;
	private String video;
	private Map<String, String> customAttributes;

	/**
	 * Creates new resource attributes with no metadata.
	 */
	public ArbResourceAttributes() {
		this.placeholders = new HashMap<>();
		this.customAttributes = new HashMap<>();
	}

	/**
	 * The resource type: "text", "image", or "css".
	 */
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Hierarchical context using colon-separated notation (e.g., "HomePage:MainPanel").
	 */
	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}

	/**
	 * Human-readable description for translators explaining when/how this resource is used.
	 */
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Original source text for tracking changes across versions.
	 */
	public String getSourceText() {
		return sourceText;
	}

	public void setSourceText(String sourceText) {
		this.sourceText = sourceText;
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
		return placeholders;
	}

	public void setPlaceholders(Map<String, ArbPlaceholder> placeholders) {
		this.placeholders = placeholders != null ? placeholders : new HashMap<>();
	}

	/**
	 * Adds a placeholder to this resource's metadata.
	 */
	public void addPlaceholder(ArbPlaceholder placeholder) {
		this.placeholders.put(placeholder.getName(), placeholder);
	}

	/**
	 * URL reference to a screenshot showing this resource in context.
	 */
	public String getScreenshot() {
		return screenshot;
	}

	public void setScreenshot(String screenshot) {
		this.screenshot = screenshot;
	}

	/**
	 * URL reference to a video showing this resource in context.
	 */
	public String getVideo() {
		return video;
	}

	public void setVideo(String video) {
		this.video = video;
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
		return customAttributes;
	}

	public void setCustomAttributes(Map<String, String> customAttributes) {
		this.customAttributes = customAttributes != null ? customAttributes : new HashMap<>();
	}

	/**
	 * Adds a custom attribute.
	 *
	 * @param name  Attribute name (should be prefixed with {@code x-})
	 * @param value Attribute value
	 */
	public void addCustomAttribute(String name, String value) {
		this.customAttributes.put(name, value);
	}

	/**
	 * Checks if any metadata is present.
	 */
	public boolean isEmpty() {
		return type == null &&
			context == null &&
			description == null &&
			sourceText == null &&
			screenshot == null &&
			video == null &&
			placeholders.isEmpty() &&
			customAttributes.isEmpty();
	}

	@Override
	public String toString() {
		return "ArbResourceAttributes{" +
			"type='" + type + '\'' +
			", context='" + context + '\'' +
			", description='" + description + '\'' +
			", placeholders=" + placeholders.size() +
			", customAttributes=" + customAttributes.size() +
			'}';
	}
}
