package de.haumacher.autotranslate.arb.model;

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
 * <pre>
 * "FOO_123": "Your pending cost is {COST}",
 * "@FOO_123": {
 *   "placeholders": {
 *     "COST": {
 *       "example": "$123.45",
 *       "description": "cost presented with currency symbol"
 *     }
 *   }
 * }
 * </pre>
 * </p>
 */
public class ArbPlaceholder {

	private String name;
	private String description;
	private String example;

	/**
	 * Creates a new placeholder with all metadata.
	 *
	 * @param name        The placeholder identifier (e.g., "COST", "num")
	 * @param description Usage description for translators
	 * @param example     Example value to illustrate the placeholder
	 */
	public ArbPlaceholder(String name, String description, String example) {
		this.name = name;
		this.description = description;
		this.example = example;
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
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Usage description for translators explaining what this placeholder represents.
	 */
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Example value illustrating how the placeholder will be replaced at runtime.
	 */
	public String getExample() {
		return example;
	}

	public void setExample(String example) {
		this.example = example;
	}

	@Override
	public String toString() {
		return "ArbPlaceholder{" +
			"name='" + name + '\'' +
			", description='" + description + '\'' +
			", example='" + example + '\'' +
			'}';
	}
}
