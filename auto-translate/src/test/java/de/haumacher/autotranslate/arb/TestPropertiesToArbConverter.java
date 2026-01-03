package de.haumacher.autotranslate.arb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haumacher.autotranslate.arb.io.ArbParser;
import de.haumacher.autotranslate.arb.model.ArbBundle;

/**
 * Tests for {@link PropertiesToArbConverter}.
 */
public class TestPropertiesToArbConverter {

	@TempDir
	File _tempDir;

	@Test
	public void testBasicConversion() throws IOException {
		// Create a test properties file
		File propertiesFile = new File(_tempDir, "messages_en.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("greeting=Hello, World!\n");
			writer.write("farewell=Goodbye!\n");
			writer.write("welcome=Welcome {username}!\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile, "en");

		// Verify the ARB file was created
		assertTrue(arbFile.exists(), "ARB file should be created");

		// Parse the ARB file and verify contents
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("en", bundle.getLocale(), "Locale should be 'en'");
		assertEquals(3, bundle.getResourceCount(), "Should have 3 resources");

		assertEquals("Hello, World!", bundle.getResource("greeting").getValue());
		assertEquals("Goodbye!", bundle.getResource("farewell").getValue());
		assertEquals("Welcome {username}!", bundle.getResource("welcome").getValue());
	}

	@Test
	public void testConversionWithSpecialCharacters() throws IOException {
		// Create a test properties file with special characters
		File propertiesFile = new File(_tempDir, "messages_de.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("greeting=Hallo, Welt!\n");
			writer.write("umlauts=Äpfel, Öl, Über\n");
			writer.write("special=Kostet €10,00\n");
		}

		// Convert to ARB using configured charset
		File arbFile = new File(_tempDir, "app_de.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile, "de");

		// Verify the ARB file
		assertTrue(arbFile.exists(), "ARB file should be created");

		// Parse and verify contents
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("de", bundle.getLocale(), "Locale should be 'de'");
		assertEquals("Hallo, Welt!", bundle.getResource("greeting").getValue());
		assertEquals("Äpfel, Öl, Über", bundle.getResource("umlauts").getValue());
		assertEquals("Kostet €10,00", bundle.getResource("special").getValue());
	}

	@Test
	public void testConversionWithComplexKeys() throws IOException {
		// Create a test properties file with complex keys
		File propertiesFile = new File(_tempDir, "messages_en.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("user.profile.name=Name\n");
			writer.write("user.profile.email=Email Address\n");
			writer.write("messages.count={count, plural, =0{no messages} =1{1 message} other{{count} messages}}\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile, "en");

		// Verify the ARB file
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("en", bundle.getLocale());
		assertEquals(3, bundle.getResourceCount());

		assertEquals("Name", bundle.getResource("user.profile.name").getValue());
		assertEquals("Email Address", bundle.getResource("user.profile.email").getValue());
		assertEquals("{count, plural, =0{no messages} =1{1 message} other{{count} messages}}",
			bundle.getResource("messages.count").getValue());
	}

	@Test
	public void testEmptyPropertiesFile() throws IOException {
		// Create an empty properties file
		File propertiesFile = new File(_tempDir, "empty.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			// Write nothing
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "empty.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile, "en");

		// Verify the ARB file
		assertTrue(arbFile.exists(), "ARB file should be created");

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("en", bundle.getLocale());
		assertEquals(0, bundle.getResourceCount(), "Should have no resources");
	}

	@Test
	public void testConversionWithEscapedCharacters() throws IOException {
		// Create a test properties file with escaped characters
		File propertiesFile = new File(_tempDir, "messages_en.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("multiline=Line 1\\nLine 2\\nLine 3\n");
			writer.write("tab=Column1\\tColumn2\\tColumn3\n");
			writer.write("colon=Key\\: Value\n");
			writer.write("equals=A \\= B\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile, "en");

		// Verify the ARB file
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("en", bundle.getLocale());

		// Properties.load() automatically unescapes these sequences
		assertEquals("Line 1\nLine 2\nLine 3", bundle.getResource("multiline").getValue());
		assertEquals("Column1\tColumn2\tColumn3", bundle.getResource("tab").getValue());
		assertEquals("Key: Value", bundle.getResource("colon").getValue());
		assertEquals("A = B", bundle.getResource("equals").getValue());
	}

	@Test
	public void testLocaleExtractionFromFilename() throws IOException {
		// Create a test properties file with locale in filename
		File propertiesFile = new File(_tempDir, "messages_fr.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("greeting=Bonjour!\n");
		}

		// Convert to ARB without specifying locale (should extract from filename)
		File arbFile = new File(_tempDir, "app_fr.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile); // No locale parameter

		// Verify the ARB file
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("fr", bundle.getLocale(), "Locale should be extracted from filename");
		assertEquals("Bonjour!", bundle.getResource("greeting").getValue());
	}

	@Test
	public void testLocaleExtractionWithRegion() throws IOException {
		// Create a test properties file with locale and region in filename
		File propertiesFile = new File(_tempDir, "strings_en_US.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("color=Color\n");
		}

		// Convert to ARB without specifying locale
		File arbFile = new File(_tempDir, "app_en_US.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile, arbFile);

		// Verify the ARB file
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("en_US", bundle.getLocale(), "Locale with region should be extracted");
		assertEquals("Color", bundle.getResource("color").getValue());
	}

	@Test
	public void testExtractLanguageStaticMethod() {
		// Test the static extractLanguage method
		assertEquals("en", PropertiesToArbConverter.extractLanguage(new File("messages_en.properties")));
		assertEquals("de", PropertiesToArbConverter.extractLanguage(new File("strings_de.properties")));
		assertEquals("fr_FR", PropertiesToArbConverter.extractLanguage(new File("app_fr_FR.properties")));
		assertEquals("en_US", PropertiesToArbConverter.extractLanguage(new File("labels_en_US.properties")));
		assertEquals(null, PropertiesToArbConverter.extractLanguage(new File("messages.properties")));
		assertEquals(null, PropertiesToArbConverter.extractLanguage(new File("nolocale.properties")));
	}

	@Test
	public void testAutomaticOutputFilename() throws IOException {
		// Create a test properties file
		File propertiesFile = new File(_tempDir, "app_es.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.ISO_8859_1)) {
			writer.write("greeting=¡Hola!\n");
			writer.write("farewell=¡Adiós!\n");
		}

		// Convert without specifying output file (should auto-generate)
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		// No charset meanse ISO.
		// converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile);

		// Verify the ARB file was created with automatic filename
		File expectedArbFile = new File(_tempDir, "app_es.arb");
		assertTrue(expectedArbFile.exists(), "ARB file should be created with automatic filename");

		// Parse and verify contents
		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(expectedArbFile);

		assertEquals("es", bundle.getLocale(), "Locale should be extracted from filename");
		assertEquals("¡Hola!", bundle.getResource("greeting").getValue());
		assertEquals("¡Adiós!", bundle.getResource("farewell").getValue());
	}

	@Test
	public void testCreateArbFileNameStaticMethod() {
		// Test the static createArbFileName method
		assertEquals("messages_en.arb",
			PropertiesToArbConverter.createArbFileName(new File("messages_en.properties")).getName());
		assertEquals("app_de.arb",
			PropertiesToArbConverter.createArbFileName(new File("app_de.properties")).getName());
		assertEquals("strings_fr_FR.arb",
			PropertiesToArbConverter.createArbFileName(new File("strings_fr_FR.properties")).getName());

		// Test with path
		File fileWithPath = new File("/path/to/messages_en.properties");
		File result = PropertiesToArbConverter.createArbFileName(fileWithPath);
		assertEquals("messages_en.arb", result.getName());
		assertEquals("/path/to", result.getParent());
	}

	@Test
	public void testAutomaticOutputFilenameWithCharsetConfiguration() throws IOException {
		// Create a test properties file with special characters
		File propertiesFile = new File(_tempDir, "messages_pt.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.UTF_8)) {
			writer.write("greeting=Olá!\n");
			writer.write("farewell=Tchau!\n");
		}

		// Convert with configured charset but automatic filename
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(StandardCharsets.UTF_8);
		converter.convert(propertiesFile);

		// Verify the ARB file
		File expectedArbFile = new File(_tempDir, "messages_pt.arb");
		assertTrue(expectedArbFile.exists(), "ARB file should be created");

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(expectedArbFile);

		assertEquals("pt", bundle.getLocale());
		assertEquals("Olá!", bundle.getResource("greeting").getValue());
		assertEquals("Tchau!", bundle.getResource("farewell").getValue());
	}

	@Test
	public void testDefaultCharsetBehavior() throws IOException {
		// Create a test properties file using standard properties format (ISO-8859-1 with Unicode escapes)
		File propertiesFile = new File(_tempDir, "messages_ja.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.ISO_8859_1)) {
			// Write using Unicode escapes (standard Java properties format)
			writer.write("greeting=\\u3053\\u3093\\u306b\\u3061\\u306f\n"); // "こんにちは" in Unicode escapes
			writer.write("simple=Hello\n");
		}

		// Convert without setting charset (should use default Properties.load())
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		File arbFile = new File(_tempDir, "messages_ja.arb");
		converter.convert(propertiesFile, arbFile, "ja");

		// Verify the ARB file
		assertTrue(arbFile.exists(), "ARB file should be created");

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("ja", bundle.getLocale());
		assertEquals("こんにちは", bundle.getResource("greeting").getValue(),
			"Unicode escapes should be decoded by default Properties.load()");
		assertEquals("Hello", bundle.getResource("simple").getValue());
	}

	@Test
	public void testCharsetNull() throws IOException {
		// Test that explicitly setting charset to null uses default behavior
		File propertiesFile = new File(_tempDir, "test_en.properties");
		try (FileWriter writer = new FileWriter(propertiesFile, StandardCharsets.ISO_8859_1)) {
			writer.write("key=\\u00E4\\u00F6\\u00FC\n"); // äöü in Unicode escapes
		}

		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.setCharset(null); // Explicitly set to null
		File arbFile = new File(_tempDir, "test_en.arb");
		converter.convert(propertiesFile, arbFile, "en");

		ArbParser parser = new ArbParser();
		ArbBundle bundle = parser.parse(arbFile);

		assertEquals("äöü", bundle.getResource("key").getValue());
	}
}
