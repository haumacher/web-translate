package de.haumacher.autotranslate.arb.io;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbConstants;
import de.haumacher.autotranslate.arb.model.ArbPlaceholder;
import de.haumacher.autotranslate.arb.model.ArbResource;
import de.haumacher.autotranslate.arb.model.ArbResourceAttributes;

/**
 * Parser for ARB (Application Resource Bundle) files.
 *
 * <p>
 * Reads JSON-formatted ARB files and converts them into an in-memory {@link ArbBundle}
 * representation. The parser handles:
 * </p>
 * 
 * <ul>
 *   <li>Global attributes (@@locale, @@context, etc.)</li>
 *   <li>Resource entries (translatable strings)</li>
 *   <li>Resource attributes (@resource_id metadata)</li>
 *   <li>Placeholder definitions</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ArbParser parser = new ArbParser();
 * ArbBundle bundle = parser.parse(new File("messages_en.arb"));
 * </pre>
 *
 */
public class ArbParser {

	private final Gson _gson;

	/**
	 * Creates a new ARB parser.
	 */
	public ArbParser() {
		_gson = new GsonBuilder().create();
	}

	/**
	 * Parses an ARB file into an in-memory bundle.
	 *
	 * @param file The ARB file to parse
	 * @return The parsed ARB bundle
	 * @throws IOException If file reading fails or JSON is malformed
	 */
	public ArbBundle parse(File file) throws IOException {
		try (FileReader reader = new FileReader(file)) {
			return parse(reader);
		}
	}

	/**
	 * Parses ARB content from a reader.
	 *
	 * @param reader Reader providing ARB JSON content
	 * @return The parsed ARB bundle
	 * @throws IOException If reading fails or JSON is malformed
	 */
	public ArbBundle parse(Reader reader) throws IOException {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		return parseBundle(root);
	}

	/**
	 * Parses ARB content from a JSON string.
	 *
	 * @param json ARB JSON content
	 * @return The parsed ARB bundle
	 */
	public ArbBundle parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		return parseBundle(root);
	}

	private ArbBundle parseBundle(JsonObject root) {
		ArbBundle bundle = new ArbBundle();

		// Temporary storage for resource attributes until we match them with resources
		Map<String, ArbResourceAttributes> attributesByResourceId = new HashMap<>();

		// First pass: separate entries by type
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();

			if (key.startsWith("@@")) {
				// Global attribute
				parseGlobalAttribute(bundle, key, value);
			} else if (key.startsWith("@")) {
				// Resource attribute
				String resourceId = key.substring(1); // Remove @ prefix
				ArbResourceAttributes attributes = parseResourceAttributes(value.getAsJsonObject());
				attributesByResourceId.put(resourceId, attributes);
			} else {
				// Resource entry
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
					String resourceValue = value.getAsString();
					ArbResource resource = new ArbResource(key, resourceValue);
					bundle.addResource(resource);
				}
			}
		}

		// Second pass: attach attributes to resources
		for (Map.Entry<String, ArbResourceAttributes> entry : attributesByResourceId.entrySet()) {
			String resourceId = entry.getKey();
			ArbResourceAttributes attributes = entry.getValue();

			ArbResource resource = bundle.getResource(resourceId);
			if (resource != null) {
				resource.setAttributes(attributes);
			} else {
				System.err.println("WARN: Found attributes for non-existent resource: " + resourceId);
			}
		}

		return bundle;
	}

	private void parseGlobalAttribute(ArbBundle bundle, String key, JsonElement value) {
		if (value.isJsonPrimitive()) {
			bundle.setGlobalAttribute(key, value.getAsString());
		}
	}

	private ArbResourceAttributes parseResourceAttributes(JsonObject attrObj) {
		ArbResourceAttributes attributes = new ArbResourceAttributes();

		for (Map.Entry<String, JsonElement> entry : attrObj.entrySet()) {
			String attrName = entry.getKey();
			JsonElement attrValue = entry.getValue();

			switch (attrName) {
				case ArbConstants.TYPE:
					attributes.setType(attrValue.getAsString());
					break;
				case ArbConstants.ATTR_CONTEXT:
					attributes.setContext(attrValue.getAsString());
					break;
				case ArbConstants.DESCRIPTION:
					attributes.setDescription(attrValue.getAsString());
					break;
				case ArbConstants.SOURCE_TEXT:
					attributes.setSourceText(attrValue.getAsString());
					break;
				case ArbConstants.SCREENSHOT:
					attributes.setScreenshot(attrValue.getAsString());
					break;
				case ArbConstants.VIDEO:
					attributes.setVideo(attrValue.getAsString());
					break;
				case ArbConstants.PLACEHOLDERS:
					parsePlaceholders(attributes, attrValue.getAsJsonObject());
					break;
				default:
					// Custom attribute
					if (attrValue.isJsonPrimitive()) {
						attributes.setAttribute(attrName, attrValue.getAsString());
					}
					break;
			}
		}

		return attributes;
	}

	private void parsePlaceholders(ArbResourceAttributes attributes, JsonObject placeholdersObj) {
		for (Map.Entry<String, JsonElement> entry : placeholdersObj.entrySet()) {
			String placeholderName = entry.getKey();
			JsonObject placeholderObj = entry.getValue().getAsJsonObject();

			String type = placeholderObj.has(ArbConstants.PLACEHOLDER_TYPE) ?
				placeholderObj.get(ArbConstants.PLACEHOLDER_TYPE).getAsString() : null;
			String description = placeholderObj.has(ArbConstants.PLACEHOLDER_DESCRIPTION) ?
				placeholderObj.get(ArbConstants.PLACEHOLDER_DESCRIPTION).getAsString() : null;
			String example = placeholderObj.has(ArbConstants.PLACEHOLDER_EXAMPLE) ?
				placeholderObj.get(ArbConstants.PLACEHOLDER_EXAMPLE).getAsString() : null;

			ArbPlaceholder placeholder = new ArbPlaceholder(placeholderName, type, description, example);

			// Parse custom attributes (anything not a standard field)
			for (Map.Entry<String, JsonElement> attrEntry : placeholderObj.entrySet()) {
				String attrName = attrEntry.getKey();
				if (!ArbConstants.isStandardPlaceholderAttribute(attrName)) {
					// Custom attribute
					if (attrEntry.getValue().isJsonPrimitive()) {
						placeholder.setAttribute(attrName, attrEntry.getValue().getAsString());
					}
				}
			}

			attributes.addPlaceholder(placeholder);
		}
	}
}
