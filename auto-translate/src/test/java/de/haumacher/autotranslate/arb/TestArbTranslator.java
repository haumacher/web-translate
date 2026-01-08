package de.haumacher.autotranslate.arb;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.StubTranslator;
import de.haumacher.autotranslate.arb.ArbTranslator;
import de.haumacher.autotranslate.arb.io.ArbParser;
import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbResource;

/**
 * Test cases for {@link ArbTranslator}.
 */
public class TestArbTranslator {

	@Test
	public void testExtractLanguageSimple() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("app_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("app_de.arb")));
		assertEquals("fr", ArbTranslator.extractLanguage(new File("messages_fr.arb")));
		assertEquals("es", ArbTranslator.extractLanguage(new File("strings_es.arb")));
	}

	@Test
	public void testExtractLanguageWithRegion() {
		assertEquals("en_US", ArbTranslator.extractLanguage(new File("app_en_US.arb")));
		assertEquals("de_DE", ArbTranslator.extractLanguage(new File("app_de_DE.arb")));
		assertEquals("zh_CN", ArbTranslator.extractLanguage(new File("messages_zh_CN.arb")));
	}

	@Test
	public void testExtractLanguageWithPath() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("/path/to/app_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("./locales/messages_de.arb")));
	}

	@Test
	public void testExtractLanguageInvalid() {
		assertNull(ArbTranslator.extractLanguage(new File("app.arb")));
		assertNull(ArbTranslator.extractLanguage(new File("app_en.txt")));
		assertNull(ArbTranslator.extractLanguage(new File("app.json")));
	}

	@Test
	public void testExtractLanguageComplexBasename() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("my_app_strings_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("flutter_app_de.arb")));
	}

	@Test
	public void testChecksumAdded(@TempDir File tempDir) throws Exception {
		// Create a simple source ARB file without checksums
		File sourceFile = new File(tempDir, "app_en.arb");
		String sourceContent = """
			{
			  "@@locale": "en",
			  "greeting": "Hello {username}!",
			  "welcome": "Welcome to our app"
			}
			""";
		Files.writeString(sourceFile.toPath(), sourceContent);

		// Parse the source file
		ArbParser parser = new ArbParser();
		ArbBundle initialSourceBundle = parser.parse(sourceFile);

		// Verify no checksums initially
		ArbResource initialGreeting = initialSourceBundle.getResource("greeting");
		assertNotNull(initialGreeting);
		assertNull(initialGreeting.getAttribute("x-translated"),
			"Should not have x-translated attribute initially");

		ArbResource initialWelcome = initialSourceBundle.getResource("welcome");
		assertNotNull(initialWelcome);
		assertNull(initialWelcome.getAttribute("x-translated"),
			"Should not have x-translated attribute initially");

		// Use ArbTranslator - should add checksums for new resources
		ArbTranslator arbTranslator = new ArbTranslator(new StubTranslator());
		arbTranslator.translate(sourceFile, List.of("de"));

		// Re-parse and verify checksums were added
		ArbBundle finalSourceBundle = parser.parse(sourceFile);

		ArbResource finalGreeting = finalSourceBundle.getResource("greeting");
		assertNotNull(finalGreeting.getAttribute("x-translated"),
			"greeting should have x-translated checksum");
		assertEquals(ArbTranslator.computeChecksum("Hello {username}!"),
			finalGreeting.getAttribute("x-translated"),
			"greeting checksum should match current text");

		ArbResource finalWelcome = finalSourceBundle.getResource("welcome");
		assertNotNull(finalWelcome.getAttribute("x-translated"),
			"welcome should have x-translated checksum");
		assertEquals(ArbTranslator.computeChecksum("Welcome to our app"),
			finalWelcome.getAttribute("x-translated"),
			"welcome checksum should match current text");

		// Verify target file was created with translations
		File targetFile = new File(tempDir, "app_de.arb");
		assertTrue(targetFile.exists(), "Target file should be created");

		ArbBundle targetBundle = parser.parse(targetFile);
		assertTrue(targetBundle.getResource("greeting").getValue().contains("[de]"),
			"Target should contain translated greeting");
		assertTrue(targetBundle.getResource("welcome").getValue().contains("[de]"),
			"Target should contain translated welcome");
	}

	@Test
	public void testChecksumMismatchDetected(@TempDir File tempDir) throws Exception {
		// Create source file with outdated checksum
		File sourceFile = new File(tempDir, "app_en.arb");
		String sourceContent = """
			{
			  "@@locale": "en",
			  "greeting": "Hello {username}!",
			  "@greeting": {
			    "x-translated": "outdated-checksum-value"
			  }
			}
			""";
		Files.writeString(sourceFile.toPath(), sourceContent);

		// Create existing target file with old translation
		File targetFile = new File(tempDir, "app_de.arb");
		String targetContent = """
			{
			  "@@locale": "de",
			  "greeting": "Hallo {username}! (old)"
			}
			""";
		Files.writeString(targetFile.toPath(), targetContent);

		// Parse the source file to verify initial state
		ArbParser parser = new ArbParser();
		ArbBundle initialSourceBundle = parser.parse(sourceFile);

		ArbResource initialGreeting = initialSourceBundle.getResource("greeting");
		String storedChecksum = initialGreeting.getAttribute("x-translated");
		String currentChecksum = ArbTranslator.computeChecksum(initialGreeting.getValue());

		// Verify checksum mismatch
		assertNotEquals(currentChecksum, storedChecksum,
			"Current checksum should differ from outdated stored checksum");

		// Use ArbTranslator - should detect mismatch and re-translate
		ArbTranslator arbTranslator = new ArbTranslator(new StubTranslator());
		arbTranslator.translate(sourceFile, List.of("de"));

		// Verify source file was updated with new checksum
		ArbBundle finalSourceBundle = parser.parse(sourceFile);
		ArbResource finalSourceGreeting = finalSourceBundle.getResource("greeting");
		String finalChecksum = finalSourceGreeting.getAttribute("x-translated");

		assertEquals(currentChecksum, finalChecksum,
			"Source checksum should be updated to match current text");
		assertNotEquals(storedChecksum, finalChecksum,
			"New checksum should differ from outdated checksum");

		// Verify target file was updated with new translation
		ArbBundle finalTargetBundle = parser.parse(targetFile);
		ArbResource finalTargetGreeting = finalTargetBundle.getResource("greeting");

		assertTrue(finalTargetGreeting.getValue().contains("[de]"),
			"Target should contain stub translation marker, proving it was re-translated");
		assertFalse(finalTargetGreeting.getValue().contains("(old)"),
			"Target should not contain old translation");
	}

	@Test
	public void testChecksumMatchSkipsTranslation(@TempDir File tempDir) throws Exception {
		// Create source file with correct checksum
		String greetingText = "Hello {username}!";
		String greetingChecksum = ArbTranslator.computeChecksum(greetingText);

		File sourceFile = new File(tempDir, "app_en.arb");
		String sourceContent = """
			{
			  "@@locale": "en",
			  "greeting": "%s",
			  "@greeting": {
			    "x-translated": "%s"
			  }
			}
			""".formatted(greetingText, greetingChecksum);
		Files.writeString(sourceFile.toPath(), sourceContent);

		// Create existing target file with translation
		File targetFile = new File(tempDir, "app_de.arb");
		String targetContent = """
			{
			  "@@locale": "de",
			  "greeting": "Hallo {username}!"
			}
			""";
		Files.writeString(targetFile.toPath(), targetContent);

		// Parse initial state
		ArbParser parser = new ArbParser();
		ArbBundle initialSourceBundle = parser.parse(sourceFile);

		// Verify checksum matches initially
		ArbResource initialGreeting = initialSourceBundle.getResource("greeting");
		String storedChecksum = initialGreeting.getAttribute("x-translated");
		String currentChecksum = ArbTranslator.computeChecksum(initialGreeting.getValue());

		assertEquals(currentChecksum, storedChecksum,
			"Checksums should match, indicating no translation needed");

		// Use ArbTranslator - should skip translation since checksum matches
		ArbTranslator arbTranslator = new ArbTranslator(new StubTranslator());
		arbTranslator.translate(sourceFile, List.of("de"));

		// Verify target file was NOT updated (translation was skipped)
		ArbBundle finalTargetBundle = parser.parse(targetFile);
		ArbResource finalGreeting = finalTargetBundle.getResource("greeting");

		assertEquals("Hallo {username}!", finalGreeting.getValue(),
			"Target translation should remain unchanged when checksum matches");
		assertFalse(finalGreeting.getValue().contains("[de]"),
			"Target should NOT contain stub translation marker, proving translation was skipped");

		// Verify source file checksum remains unchanged
		ArbBundle finalSourceBundle = parser.parse(sourceFile);
		ArbResource finalSourceGreeting = finalSourceBundle.getResource("greeting");
		String finalChecksum = finalSourceGreeting.getAttribute("x-translated");

		assertEquals(greetingChecksum, finalChecksum,
			"Source checksum should remain unchanged");
	}

	@Test
	public void testMultipleTargetLanguagesWithModifiedResource(@TempDir File tempDir) throws Exception {
		// Create source file with a resource that has an outdated checksum
		// This simulates a resource that was previously translated but has been modified
		File sourceFile = new File(tempDir, "app_en.arb");
		String sourceContent = """
			{
			  "@@locale": "en",
			  "greeting": "Hello {username}!",
			  "@greeting": {
			    "x-translated": "outdated-checksum-value"
			  },
			  "welcome": "Welcome to our app",
			  "@welcome": {
			    "x-translated": "another-outdated-checksum"
			  }
			}
			""";
		Files.writeString(sourceFile.toPath(), sourceContent);

		// Create existing target files with old translations
		File targetFileDe = new File(tempDir, "app_de.arb");
		String targetContentDe = """
			{
			  "@@locale": "de",
			  "greeting": "Hallo {username}! (old)",
			  "welcome": "Willkommen (old)"
			}
			""";
		Files.writeString(targetFileDe.toPath(), targetContentDe);

		File targetFileFr = new File(tempDir, "app_fr.arb");
		String targetContentFr = """
			{
			  "@@locale": "fr",
			  "greeting": "Bonjour {username}! (old)",
			  "welcome": "Bienvenue (old)"
			}
			""";
		Files.writeString(targetFileFr.toPath(), targetContentFr);

		// Parse the source file to check initial state
		ArbParser parser = new ArbParser();
		ArbBundle initialSourceBundle = parser.parse(sourceFile);

		ArbResource greetingResource = initialSourceBundle.getResource("greeting");
		String greetingChecksum = greetingResource.getAttribute("x-translated");
		String currentGreetingChecksum = ArbTranslator.computeChecksum("Hello {username}!");

		// Verify checksum mismatch exists initially
		assertNotEquals(currentGreetingChecksum, greetingChecksum,
			"Initial checksum should not match current text");

		// Use ArbTranslator with stub translator
		ArbTranslator arbTranslator = new ArbTranslator(new StubTranslator());
		arbTranslator.translate(sourceFile, List.of("de", "fr"));

		// Verify the source file was updated with correct checksums
		ArbBundle finalSourceBundle = parser.parse(sourceFile);
		ArbResource finalGreeting = finalSourceBundle.getResource("greeting");
		String finalChecksum = finalGreeting.getAttribute("x-translated");

		assertEquals(currentGreetingChecksum, finalChecksum,
			"Source file should have updated checksum after translation");

		// Verify both target files received the new translations
		ArbBundle targetDe = parser.parse(targetFileDe);
		ArbResource greetingDe = targetDe.getResource("greeting");
		assertTrue(greetingDe.getValue().contains("[de]"),
			"German translation should be updated with new content");

		ArbBundle targetFr = parser.parse(targetFileFr);
		ArbResource greetingFr = targetFr.getResource("greeting");
		assertTrue(greetingFr.getValue().contains("[fr]"),
			"French translation should be updated with new content");
	}

	@Test
	public void testResourceWithoutChecksumGetsChecksumWhenAllTargetsExist(@TempDir File tempDir) throws Exception {
		// Create source file WITHOUT any checksums
		File sourceFile = new File(tempDir, "app_en.arb");
		String sourceContent = """
			{
			  "@@locale": "en",
			  "greeting": "Hello {username}!",
			  "welcome": "Welcome to our app"
			}
			""";
		Files.writeString(sourceFile.toPath(), sourceContent);

		// Create target files that ALREADY HAVE translations for all resources
		File targetFileDe = new File(tempDir, "app_de.arb");
		String targetContentDe = """
			{
			  "@@locale": "de",
			  "greeting": "Hallo {username}!",
			  "welcome": "Willkommen in unserer App"
			}
			""";
		Files.writeString(targetFileDe.toPath(), targetContentDe);

		File targetFileFr = new File(tempDir, "app_fr.arb");
		String targetContentFr = """
			{
			  "@@locale": "fr",
			  "greeting": "Bonjour {username}!",
			  "welcome": "Bienvenue dans notre application"
			}
			""";
		Files.writeString(targetFileFr.toPath(), targetContentFr);

		// Verify no checksums initially in source file
		ArbParser parser = new ArbParser();
		ArbBundle initialSourceBundle = parser.parse(sourceFile);
		ArbResource initialGreeting = initialSourceBundle.getResource("greeting");
		assertFalse(initialGreeting.hasAttributes() &&
			initialGreeting.getAttribute("x-translated") != null,
			"Should not have x-translated attribute initially");

		// Use ArbTranslator to "translate" (actually just process existing translations)
		// No actual translation will happen (all resources exist in targets)
		ArbTranslator translator = new ArbTranslator(new StubTranslator());
		translator.translate(sourceFile, List.of("de", "fr"));

		// Parse source file after translation to check if checksums were added
		ArbBundle updatedSourceBundle = parser.parse(sourceFile);

		// CORRECT BEHAVIOR: Even though resources exist in all target files,
		// checksums MUST be added to establish a baseline for future change detection
		String expectedGreetingChecksum = ArbTranslator.computeChecksum("Hello {username}!");
		String expectedWelcomeChecksum = ArbTranslator.computeChecksum("Welcome to our app");

		ArbResource updatedGreeting = updatedSourceBundle.getResource("greeting");
		assertTrue(updatedGreeting.hasAttributes(),
			"greeting must have attributes after translation");
		assertTrue(updatedGreeting.getAttribute("x-translated") != null,
			"greeting must have x-translated checksum to detect future changes");
		assertEquals(expectedGreetingChecksum,
			updatedGreeting.getAttribute("x-translated"),
			"greeting checksum should match current text");

		ArbResource updatedWelcome = updatedSourceBundle.getResource("welcome");
		assertTrue(updatedWelcome.hasAttributes(),
			"welcome must have attributes after translation");
		assertTrue(updatedWelcome.getAttribute("x-translated") != null,
			"welcome must have x-translated checksum to detect future changes");
		assertEquals(expectedWelcomeChecksum,
			updatedWelcome.getAttribute("x-translated"),
			"welcome checksum should match current text");
	}
}
