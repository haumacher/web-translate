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
		try (FileWriter writer = new FileWriter(propertiesFile)) {
			writer.write("greeting=Hello, World!\n");
			writer.write("farewell=Goodbye!\n");
			writer.write("welcome=Welcome {username}!\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
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

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_de.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
		converter.convert(propertiesFile, arbFile, "de", StandardCharsets.UTF_8);

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
		try (FileWriter writer = new FileWriter(propertiesFile)) {
			writer.write("user.profile.name=Name\n");
			writer.write("user.profile.email=Email Address\n");
			writer.write("messages.count={count, plural, =0{no messages} =1{1 message} other{{count} messages}}\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
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
		try (FileWriter writer = new FileWriter(propertiesFile)) {
			// Write nothing
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "empty.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
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
		try (FileWriter writer = new FileWriter(propertiesFile)) {
			writer.write("multiline=Line 1\\nLine 2\\nLine 3\n");
			writer.write("tab=Column1\\tColumn2\\tColumn3\n");
			writer.write("colon=Key\\: Value\n");
			writer.write("equals=A \\= B\n");
		}

		// Convert to ARB
		File arbFile = new File(_tempDir, "app_en.arb");
		PropertiesToArbConverter converter = new PropertiesToArbConverter();
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
}
