package de.haumacher.autotranslate.arb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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
 * Example usage:
 * <pre>
 * PropertiesToArbConverter converter = new PropertiesToArbConverter();
 * converter.convert(
 *     new File("messages_en.properties"),
 *     new File("app_en.arb"),
 *     "en",
 *     StandardCharsets.UTF_8
 * );
 * </pre>
 * </p>
 *
 * <p>
 * The converter creates compact ARB files without metadata, containing only the locale
 * and the key-value pairs from the properties file.
 * </p>
 */
public class PropertiesToArbConverter {

	private final ArbWriter _writer;

	/**
	 * Creates a new properties to ARB converter.
	 */
	public PropertiesToArbConverter() {
		_writer = new ArbWriter();
	}

	/**
	 * Converts a properties file to ARB format.
	 *
	 * @param propertiesFile The source properties file
	 * @param arbFile        The target ARB file
	 * @param locale         The locale identifier (e.g., "en", "de", "en_US")
	 * @param charset        The charset to use when reading the properties file
	 * @throws IOException If reading or writing fails
	 */
	public void convert(File propertiesFile, File arbFile, String locale, Charset charset) throws IOException {
		Properties properties = loadProperties(propertiesFile, charset);
		ArbBundle bundle = convertToArbBundle(properties, locale);
		_writer.write(bundle, arbFile, false); // Use compact format
	}

	/**
	 * Converts a properties file to ARB format using UTF-8 encoding.
	 *
	 * @param propertiesFile The source properties file
	 * @param arbFile        The target ARB file
	 * @param locale         The locale identifier (e.g., "en", "de", "en_US")
	 * @throws IOException If reading or writing fails
	 */
	public void convert(File propertiesFile, File arbFile, String locale) throws IOException {
		convert(propertiesFile, arbFile, locale, StandardCharsets.UTF_8);
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
	 * Command-line interface for converting properties files to ARB format.
	 *
	 * <p>
	 * Usage: {@code java PropertiesToArbConverter <properties-file> <arb-file> <locale> [charset]}
	 * </p>
	 *
	 * @param args Command-line arguments
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("Usage: PropertiesToArbConverter <properties-file> <arb-file> <locale> [charset]");
			System.err.println("Example: PropertiesToArbConverter messages_en.properties app_en.arb en UTF-8");
			System.exit(1);
		}

		File propertiesFile = new File(args[0]);
		File arbFile = new File(args[1]);
		String locale = args[2];
		Charset charset = args.length > 3 ? Charset.forName(args[3]) : StandardCharsets.UTF_8;

		if (!propertiesFile.exists()) {
			System.err.println("Error: Properties file not found: " + propertiesFile);
			System.exit(1);
		}

		try {
			PropertiesToArbConverter converter = new PropertiesToArbConverter();
			converter.convert(propertiesFile, arbFile, locale, charset);
			System.out.println("Successfully converted " + propertiesFile + " to " + arbFile);
			System.out.println("Locale: " + locale);
		} catch (IOException e) {
			System.err.println("Error during conversion: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
