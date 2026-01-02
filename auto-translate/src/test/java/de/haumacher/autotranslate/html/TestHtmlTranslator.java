package de.haumacher.autotranslate.html;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haumacher.autotranslate.StubTranslator;

/**
 * End-to-end test cases for {@link de.haumacher.autotranslate.html.Translator}.
 */
public class TestHtmlTranslator {

	@Test
	public void testEndToEndSimpleHtml(@TempDir File tempDir) throws Exception {
		// Setup directory structure
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		templatesEnDir.mkdirs();

		// Create a simple source HTML file
		File sourceHtml = new File(templatesEnDir, "index.html");
		String sourceContent = """
			<html>
			<head>
				<title>Welcome Page</title>
			</head>
			<body>
				<h1>Hello World</h1>
				<p>This is a test page.</p>
			</body>
			</html>""";
		Files.writeString(sourceHtml.toPath(), sourceContent);

		// Run the HTML translator
		de.haumacher.autotranslate.html.Translator translator =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de", "fr"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator.run();

		// Verify German translation was created
		File deTemplatesDir = new File(templatesDir, "de");
		assertTrue(deTemplatesDir.exists(), "German templates directory should be created");

		File deHtml = new File(deTemplatesDir, "index.html");
		assertTrue(deHtml.exists(), "German HTML file should be created");

		String deContent = Files.readString(deHtml.toPath(), StandardCharsets.UTF_8);
		assertTrue(deContent.contains("[de]"), "German HTML should contain translated text with [de] marker");
		assertTrue(deContent.contains("Welcome Page [de]"), "Title should be translated to German");
		assertTrue(deContent.contains("Hello World [de]"), "Heading should be translated to German");
		assertTrue(deContent.contains("This is a test page. [de]"), "Paragraph should be translated to German");

		// Verify French translation was created
		File frTemplatesDir = new File(templatesDir, "fr");
		assertTrue(frTemplatesDir.exists(), "French templates directory should be created");

		File frHtml = new File(frTemplatesDir, "index.html");
		assertTrue(frHtml.exists(), "French HTML file should be created");

		String frContent = Files.readString(frHtml.toPath(), StandardCharsets.UTF_8);
		assertTrue(frContent.contains("[fr]"), "French HTML should contain translated text with [fr] marker");
		assertTrue(frContent.contains("Welcome Page [fr]"), "Title should be translated to French");
		assertTrue(frContent.contains("Hello World [fr]"), "Heading should be translated to French");
		assertTrue(frContent.contains("This is a test page. [fr]"), "Paragraph should be translated to French");
	}

	@Test
	public void testEndToEndWithNestedMarkup(@TempDir File tempDir) throws Exception {
		// Setup directory structure
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		templatesEnDir.mkdirs();

		// Create HTML file with nested markup
		File sourceHtml = new File(templatesEnDir, "page.html");
		String sourceContent = """
			<html>
			<body>
				<p>Some text <a href="#">with a <b>nested</b> link</a> here.</p>
				<div>
					<span>Another <em>important</em> message.</span>
				</div>
			</body>
			</html>""";
		Files.writeString(sourceHtml.toPath(), sourceContent);

		// Run the HTML translator
		de.haumacher.autotranslate.html.Translator translator =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator.run();

		// Verify German translation preserves markup structure
		File deHtml = new File(templatesDir, "de/page.html");
		assertTrue(deHtml.exists(), "German HTML should be created");

		String deContent = Files.readString(deHtml.toPath(), StandardCharsets.UTF_8);

		// Verify text was translated
		assertTrue(deContent.contains("[de]"), "Content should be translated");

		// Verify markup structure is preserved
		assertTrue(deContent.contains("<a href=\"#\">"), "Link tag should be preserved");
		assertTrue(deContent.contains("<b>"), "Bold tag should be preserved");
		assertTrue(deContent.contains("<em>"), "Emphasis tag should be preserved");
		assertTrue(deContent.contains("</a>"), "Closing link tag should be preserved");
		assertTrue(deContent.contains("</b>"), "Closing bold tag should be preserved");
		assertTrue(deContent.contains("</em>"), "Closing emphasis tag should be preserved");
	}

	@Test
	public void testEndToEndWithTextAttributes(@TempDir File tempDir) throws Exception {
		// Setup directory structure
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		templatesEnDir.mkdirs();

		// Create HTML file with text attributes
		File sourceHtml = new File(templatesEnDir, "form.html");
		String sourceContent = """
			<html>
			<body>
				<img src="logo.png" alt="Company Logo" title="Our Logo"/>
				<input type="text" placeholder="Enter your name"/>
				<button aria-label="Submit Form">Submit</button>
			</body>
			</html>""";
		Files.writeString(sourceHtml.toPath(), sourceContent);

		// Run the HTML translator
		de.haumacher.autotranslate.html.Translator translator =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator.run();

		// Verify German translation contains translated attributes
		File deHtml = new File(templatesDir, "de/form.html");
		String deContent = Files.readString(deHtml.toPath(), StandardCharsets.UTF_8);

		assertTrue(deContent.contains("Company Logo [de]"), "alt attribute should be translated");
		assertTrue(deContent.contains("Our Logo [de]"), "title attribute should be translated");
		assertTrue(deContent.contains("Enter your name [de]"), "placeholder attribute should be translated");
		assertTrue(deContent.contains("Submit Form [de]"), "aria-label attribute should be translated");
		assertTrue(deContent.contains("Submit [de]"), "Button text should be translated");
	}

	@Test
	public void testEndToEndMultipleFiles(@TempDir File tempDir) throws Exception {
		// Setup directory structure
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		templatesEnDir.mkdirs();

		// Create multiple HTML files
		File indexHtml = new File(templatesEnDir, "index.html");
		Files.writeString(indexHtml.toPath(), "<html><body><h1>Home Page</h1></body></html>");

		File aboutHtml = new File(templatesEnDir, "about.html");
		Files.writeString(aboutHtml.toPath(), "<html><body><h1>About Us</h1></body></html>");

		// Run the HTML translator
		de.haumacher.autotranslate.html.Translator translator =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator.run();

		// Verify both files were translated
		File deIndexHtml = new File(templatesDir, "de/index.html");
		File deAboutHtml = new File(templatesDir, "de/about.html");

		assertTrue(deIndexHtml.exists(), "index.html should be translated");
		assertTrue(deAboutHtml.exists(), "about.html should be translated");

		String deIndexContent = Files.readString(deIndexHtml.toPath(), StandardCharsets.UTF_8);
		String deAboutContent = Files.readString(deAboutHtml.toPath(), StandardCharsets.UTF_8);

		assertTrue(deIndexContent.contains("Home Page [de]"), "Index should be translated");
		assertTrue(deAboutContent.contains("About Us [de]"), "About should be translated");
	}

	@Test
	public void testEndToEndNestedDirectories(@TempDir File tempDir) throws Exception {
		// Setup directory structure with nested directories
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		File nestedDir = new File(templatesEnDir, "docs");
		nestedDir.mkdirs();

		// Create HTML file in nested directory
		File nestedHtml = new File(nestedDir, "guide.html");
		Files.writeString(nestedHtml.toPath(), "<html><body><h1>User Guide</h1></body></html>");

		// Run the HTML translator
		de.haumacher.autotranslate.html.Translator translator =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator.run();

		// Verify nested directory structure is preserved in translation
		File deNestedHtml = new File(templatesDir, "de/docs/guide.html");
		assertTrue(deNestedHtml.exists(), "Nested directory structure should be preserved");

		String deContent = Files.readString(deNestedHtml.toPath(), StandardCharsets.UTF_8);
		assertTrue(deContent.contains("User Guide [de]"), "Nested file should be translated");
	}

	@Test
	public void testIncrementalTranslation(@TempDir File tempDir) throws Exception {
		// Setup directory structure
		File templatesDir = new File(tempDir, "templates");
		File propertiesDir = new File(tempDir, "properties");

		File templatesEnDir = new File(templatesDir, "en");
		templatesEnDir.mkdirs();

		// Create initial HTML file
		File sourceHtml = new File(templatesEnDir, "page.html");
		Files.writeString(sourceHtml.toPath(), "<html><body><h1>Title</h1></body></html>");

		// First translation run
		de.haumacher.autotranslate.html.Translator translator1 =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator1.run();

		// Verify first translation
		File deHtml = new File(templatesDir, "de/page.html");
		String deContent1 = Files.readString(deHtml.toPath(), StandardCharsets.UTF_8);
		assertTrue(deContent1.contains("Title [de]"), "First translation should work");

		// Add more content to source HTML
		Files.writeString(sourceHtml.toPath(),
			"<html><body><h1>Title</h1><p>New paragraph</p></body></html>");

		// Second translation run (incremental)
		de.haumacher.autotranslate.html.Translator translator2 =
			new de.haumacher.autotranslate.html.Translator(
				new StubTranslator(),
				"en",
				List.of("de"),
				propertiesDir,
				templatesDir,
				StandardCharsets.UTF_8
			);
		translator2.run();

		// Verify incremental translation contains both old and new content
		String deContent2 = Files.readString(deHtml.toPath(), StandardCharsets.UTF_8);
		assertTrue(deContent2.contains("Title [de]"), "Old content should remain");
		assertTrue(deContent2.contains("New paragraph [de]"), "New content should be translated");
	}
}
