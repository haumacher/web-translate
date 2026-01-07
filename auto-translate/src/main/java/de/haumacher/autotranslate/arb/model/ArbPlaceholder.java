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
 * </p>
 * 
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
 */
public class ArbPlaceholder {

	private String _name;
	private String _description;
	private String _example;

	/**
	 * Creates a new placeholder with all metadata.
	 *
	 * @param name        The placeholder identifier (e.g., "COST", "num")
	 * @param description Usage description for translators
	 * @param example     Example value to illustrate the placeholder
	 */
	public ArbPlaceholder(String name, String description, String example) {
		_name = name;
		_description = description;
		_example = example;
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

	@Override
	public String toString() {
		return "ArbPlaceholder{" +
			"name='" + _name + '\'' +
			", description='" + _description + '\'' +
			", example='" + _example + '\'' +
			'}';
	}
}
