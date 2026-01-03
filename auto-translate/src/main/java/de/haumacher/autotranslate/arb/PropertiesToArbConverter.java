package de.haumacher.autotranslate.arb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.haumacher.autotranslate.arb.io.ArbWriter;
import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbResource;

/**
 * Converter that reads Java properties files and writes them to ARB (Application Resource Bundle) format.
 *
 * <p>
 * This converter takes standard Java .properties files and converts them to ARB JSON format,
 * which is commonly used in Flutter/Dart applications for internationalization.
 * </p>
 *
 * <p>
 * Example usage with explicit configuration:
 * <pre>
 * PropertiesToArbConverter converter = new PropertiesToArbConverter();
 * converter.setCharset(StandardCharsets.UTF_8);
 * converter.convert(
 *     new File("messages_en.properties"),
 *     new File("app_en.arb")
 * );
 * </pre>
 * </p>
 *
 * <p>
 * The converter can automatically extract the locale from the filename if not explicitly specified.
 * For example, {@code messages_en.properties} will use locale "en".
 * </p>
 *
 * <p>
 * The converter creates compact ARB files without metadata, containing only the locale
 * and the key-value pairs from the properties file.
 * </p>
 */
public class PropertiesToArbConverter {

	private static final Pattern LANG_PATTERN = Pattern.compile("(.+?)_(\\w{2}(?:_\\w+)?)\\.[^.]+$");

	private final ArbWriter _writer;

	private Charset _charset = StandardCharsets.UTF_8;

	/**
	 * Creates a new properties to ARB converter with default settings.
	 *
	 * <p>
	 * Default charset: UTF-8
	 * </p>
	 */
	public PropertiesToArbConverter() {
		_writer = new ArbWriter();
	}

	/**
	 * Sets the charset to use when reading properties files.
	 *
	 * @param charset The charset (defaults to UTF-8)
	 * @return This converter for method chaining
	 */
	public PropertiesToArbConverter setCharset(Charset charset) {
		_charset = charset;
		return this;
	}

	/**
	 * Gets the currently configured charset.
	 *
	 * @return The charset used for reading properties files
	 */
	public Charset getCharset() {
		return _charset;
	}

	/**
	 * Converts a properties file to ARB format.
	 *
	 * <p>
	 * If locale is {@code null}, the locale will be automatically extracted from the properties
	 * file name (e.g., {@code messages_en.properties} → "en").
	 * </p>
	 *
	 * @param propertiesFile The source properties file
	 * @param arbFile        The target ARB file
	 * @param locale         The locale identifier (e.g., "en", "de", "en_US"), or {@code null} to extract from filename
	 * @throws IOException If reading or writing fails
	 * @throws IllegalArgumentException If locale is null and cannot be extracted from filename
	 */
	public void convert(File propertiesFile, File arbFile, String locale) throws IOException {
		// Extract locale from filename if not specified
		String effectiveLocale = locale;
		if (effectiveLocale == null) {
			effectiveLocale = extractLanguage(propertiesFile);
			if (effectiveLocale == null) {
				throw new IllegalArgumentException(
					"Could not extract locale from filename: " + propertiesFile.getName() +
					". Expected pattern: basename_lang.properties (e.g., messages_en.properties)");
			}
		}

		Properties properties = loadProperties(propertiesFile, _charset);
		ArbBundle bundle = convertToArbBundle(properties, effectiveLocale);
		_writer.write(bundle, arbFile, false); // Use compact format
	}

	/**
	 * Converts a properties file to ARB format with automatic locale detection from filename.
	 *
	 * @param propertiesFile The source properties file
	 * @param arbFile        The target ARB file
	 * @throws IOException If reading or writing fails
	 * @throws IllegalArgumentException If locale cannot be extracted from filename
	 */
	public void convert(File propertiesFile, File arbFile) throws IOException {
		convert(propertiesFile, arbFile, null);
	}

	/**
	 * Loads a properties file using the specified charset.
	 *
	 * @param file    The properties file to load
	 * @param charset The charset to use for reading
	 * @return The loaded properties
	 * @throws IOException If reading fails
	 */
	private Properties loadProperties(File file, Charset charset) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = new FileInputStream(file)) {
			if (charset.equals(StandardCharsets.ISO_8859_1)) {
				// Use the default Properties.load() which expects ISO-8859-1
				properties.load(in);
			} else {
				// Use the reader-based load for other charsets
				try (java.io.InputStreamReader reader = new java.io.InputStreamReader(in, charset)) {
					properties.load(reader);
				}
			}
		}
		return properties;
	}

	/**
	 * Converts a Properties object to an ArbBundle.
	 *
	 * @param properties The source properties
	 * @param locale     The locale identifier
	 * @return The ARB bundle
	 */
	private ArbBundle convertToArbBundle(Properties properties, String locale) {
		ArbBundle bundle = new ArbBundle();
		bundle.setLocale(locale);

		// Add all properties as resources
		for (String key : properties.stringPropertyNames()) {
			String value = properties.getProperty(key);
			ArbResource resource = new ArbResource(key, value);
			bundle.addResource(resource);
		}

		return bundle;
	}

	/**
	 * Extracts the language code from a properties filename.
	 *
	 * <p>
	 * Supports patterns like:
	 * <ul>
	 *   <li>{@code messages_en.properties} → "en"</li>
	 *   <li>{@code app_en_US.properties} → "en_US"</li>
	 *   <li>{@code strings_de_DE.properties} → "de_DE"</li>
	 * </ul>
	 * </p>
	 *
	 * @param file The properties file
	 * @return The language code, or {@code null} if not found
	 */
	public static String extractLanguage(File file) {
		String filename = file.getName();
		Matcher matcher = LANG_PATTERN.matcher(filename);
		if (matcher.matches()) {
			return matcher.group(2);
		}
		return null;
	}

	/**
	 * Command-line interface for converting properties files to ARB format.
	 *
	 * <p>
	 * Usage: {@code java PropertiesToArbConverter <properties-file> <arb-file> [locale] [charset]}
	 * </p>
	 *
	 * <p>
	 * If locale is omitted, it will be extracted from the filename.
	 * If charset is omitted, UTF-8 will be used.
	 * </p>
	 *
	 * @param args Command-line arguments
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			System.err.println("Usage: PropertiesToArbConverter <properties-file> <arb-file> [locale] [charset]");
			System.err.println();
			System.err.println("Examples:");
			System.err.println("  PropertiesToArbConverter messages_en.properties app_en.arb");
			System.err.println("  PropertiesToArbConverter messages_en.properties app_en.arb en");
			System.err.println("  PropertiesToArbConverter messages_en.properties app_en.arb en UTF-8");
			System.err.println("  PropertiesToArbConverter messages_en.properties app_en.arb en ISO-8859-1");
			System.err.println();
			System.err.println("If locale is omitted, it will be extracted from the properties filename.");
			System.exit(1);
		}

		File propertiesFile = new File(args[0]);
		File arbFile = new File(args[1]);
		String locale = args.length > 2 ? args[2] : null;
		Charset charset = args.length > 3 ? Charset.forName(args[3]) : StandardCharsets.UTF_8;

		if (!propertiesFile.exists()) {
			System.err.println("Error: Properties file not found: " + propertiesFile);
			System.exit(1);
		}

		try {
			PropertiesToArbConverter converter = new PropertiesToArbConverter();
			converter.setCharset(charset);
			converter.convert(propertiesFile, arbFile, locale);

			String effectiveLocale = locale != null ? locale : extractLanguage(propertiesFile);
			System.out.println("Successfully converted " + propertiesFile + " to " + arbFile);
			System.out.println("Locale: " + effectiveLocale);
			System.out.println("Charset: " + charset);
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Error during conversion: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
