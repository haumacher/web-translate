package de.haumacher.autotranslate.arb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Writer for ARB (Application Resource Bundle) files.
 *
 * <p>
 * Converts an in-memory {@link ArbBundle} representation into JSON-formatted ARB files.
 * Supports two output modes:
 * <ul>
 *   <li><b>Verbose mode</b>: Includes all metadata (@-prefixed attributes) for development</li>
 *   <li><b>Compact mode</b>: Strips metadata for runtime usage</li>
 * </ul>
 * </p>
 *
 * <p>
 * The writer preserves the insertion order of resources and formats JSON with 2-space indentation.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 * ArbWriter writer = new ArbWriter();
 * writer.write(bundle, new File("messages_en.arb"), true); // verbose mode
 * </pre>
 * </p>
 */
public class ArbWriter {

	private final Gson gson;

	/**
	 * Creates a new ARB writer with pretty-printing enabled.
	 */
	public ArbWriter() {
		this.gson = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();
	}

	/**
	 * Writes an ARB bundle to a file in verbose mode (with all metadata).
	 *
	 * @param bundle The ARB bundle to write
	 * @param file   The output file
	 * @throws IOException If writing fails
	 */
	public void write(ArbBundle bundle, File file) throws IOException {
		write(bundle, file, true);
	}

	/**
	 * Writes an ARB bundle to a file.
	 *
	 * @param bundle  The ARB bundle to write
	 * @param file    The output file
	 * @param verbose If true, include metadata; if false, strip metadata for compact runtime format
	 * @throws IOException If writing fails
	 */
	public void write(ArbBundle bundle, File file, boolean verbose) throws IOException {
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			write(bundle, writer, verbose);
		}
	}

	/**
	 * Writes an ARB bundle to a writer.
	 *
	 * @param bundle  The ARB bundle to write
	 * @param writer  The output writer
	 * @param verbose If true, include metadata; if false, strip metadata
	 * @throws IOException If writing fails
	 */
	public void write(ArbBundle bundle, Writer writer, boolean verbose) throws IOException {
		JsonObject root = buildJsonObject(bundle, verbose);
		gson.toJson(root, writer);
	}

	/**
	 * Converts an ARB bundle to a JSON string.
	 *
	 * @param bundle  The ARB bundle to convert
	 * @param verbose If true, include metadata; if false, strip metadata
	 * @return JSON string representation
	 */
	public String toJson(ArbBundle bundle, boolean verbose) {
		JsonObject root = buildJsonObject(bundle, verbose);
		return gson.toJson(root);
	}

	private JsonObject buildJsonObject(ArbBundle bundle, boolean verbose) {
		JsonObject root = new JsonObject();

		// Add global attributes first (@@locale, @@context, etc.)
		for (Map.Entry<String, String> entry : bundle.getGlobalAttributes().entrySet()) {
			root.addProperty(entry.getKey(), entry.getValue());
		}

		// Add resources (and their attributes if verbose)
		for (Map.Entry<String, ArbResource> entry : bundle.getResources().entrySet()) {
			String resourceId = entry.getKey();
			ArbResource resource = entry.getValue();

			// Add resource entry
			root.addProperty(resourceId, resource.getValue());

			// Add resource attributes if verbose and attributes exist
			if (verbose && resource.hasAttributes()) {
				JsonObject attrObj = buildAttributesObject(resource.getAttributes());
				root.add("@" + resourceId, attrObj);
			}
		}

		return root;
	}

	private JsonObject buildAttributesObject(ArbResourceAttributes attributes) {
		JsonObject attrObj = new JsonObject();

		if (attributes.getType() != null) {
			attrObj.addProperty("type", attributes.getType());
		}

		if (attributes.getContext() != null) {
			attrObj.addProperty("context", attributes.getContext());
		}

		if (attributes.getDescription() != null) {
			attrObj.addProperty("description", attributes.getDescription());
		}

		if (attributes.getSourceText() != null) {
			attrObj.addProperty("source_text", attributes.getSourceText());
		}

		if (attributes.getScreenshot() != null) {
			attrObj.addProperty("screenshot", attributes.getScreenshot());
		}

		if (attributes.getVideo() != null) {
			attrObj.addProperty("video", attributes.getVideo());
		}

		// Add placeholders
		if (!attributes.getPlaceholders().isEmpty()) {
			JsonObject placeholdersObj = new JsonObject();
			for (Map.Entry<String, ArbPlaceholder> entry : attributes.getPlaceholders().entrySet()) {
				ArbPlaceholder placeholder = entry.getValue();
				JsonObject placeholderObj = new JsonObject();

				if (placeholder.getDescription() != null) {
					placeholderObj.addProperty("description", placeholder.getDescription());
				}
				if (placeholder.getExample() != null) {
					placeholderObj.addProperty("example", placeholder.getExample());
				}

				placeholdersObj.add(placeholder.getName(), placeholderObj);
			}
			attrObj.add("placeholders", placeholdersObj);
		}

		// Add custom attributes
		for (Map.Entry<String, String> entry : attributes.getCustomAttributes().entrySet()) {
			attrObj.addProperty(entry.getKey(), entry.getValue());
		}

		return attrObj;
	}
}
