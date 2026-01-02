package de.haumacher.webtranslate.arb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

/**
 * Translates ARB (Application Resource Bundle) files using DeepL API.
 *
 * <p>
 * This tool reads a source ARB file, extracts the source language from the filename
 * (e.g., {@code app_en.arb} → "en"), translates all resource values to target languages,
 * and writes new ARB files for each target language.
 * </p>
 *
 * <p>
 * File naming convention:
 * <ul>
 *   <li>Source file: {@code basename_lang.arb} (e.g., {@code app_en.arb})</li>
 *   <li>Target files: {@code basename_targetLang.arb} (e.g., {@code app_de.arb}, {@code app_fr.arb})</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 * ArbTranslator translator = new ArbTranslator("your-deepl-api-key");
 * List&lt;String&gt; targetLangs = List.of("de", "fr", "es");
 * translator.translate(new File("app_en.arb"), targetLangs);
 * </pre>
 * </p>
 */
public class ArbTranslator {

	private static final Pattern LANG_PATTERN = Pattern.compile("(.+?)_(\\w{2}(?:_\\w+)?)\\.arb$");

	private final String apiKey;
	private final Translator translator;
	private final ArbParser parser;
	private final ArbWriter writer;

	private int totalBilledChars = 0;

	/**
	 * Creates a new ARB translator with the given DeepL API key.
	 *
	 * @param apiKey DeepL API authentication key
	 */
	public ArbTranslator(String apiKey) {
		this.apiKey = apiKey;
		this.translator = new Translator(apiKey);
		this.parser = new ArbParser();
		this.writer = new ArbWriter();
	}

	/**
	 * Translates a source ARB file to multiple target languages.
	 *
	 * <p>
	 * The source language is automatically extracted from the filename.
	 * For each target language, a new ARB file is created in the same directory
	 * as the source file.
	 * </p>
	 *
	 * @param sourceFile  The source ARB file (e.g., {@code app_en.arb})
	 * @param targetLangs List of target language codes (e.g., ["de", "fr", "es"])
	 * @throws IOException      If file reading/writing fails
	 * @throws DeepLException   If DeepL API call fails
	 * @throws InterruptedException If translation is interrupted
	 */
	public void translate(File sourceFile, List<String> targetLangs)
			throws IOException, DeepLException, InterruptedException {

		// Extract source language from filename
		String sourceLang = extractLanguage(sourceFile);
		if (sourceLang == null) {
			throw new IllegalArgumentException(
				"Cannot determine source language from filename: " + sourceFile.getName() +
				". Expected format: basename_lang.arb (e.g., app_en.arb)"
			);
		}

		System.out.println("Source file: " + sourceFile.getAbsolutePath());
		System.out.println("Source language: " + sourceLang);
		System.out.println("Target languages: " + targetLangs);
		System.out.println();

		// Parse source ARB file
		ArbBundle sourceBundle = parser.parse(sourceFile);
		System.out.println("Parsed source ARB: " + sourceBundle.getResourceCount() + " resources");

		// Translate to each target language
		for (String targetLang : targetLangs) {
			System.out.println();
			System.out.println("Translating to: " + targetLang);
			translateToLanguage(sourceFile, sourceBundle, sourceLang, targetLang);
		}

		System.out.println();
		System.out.println("========================================");
		System.out.println("Translation complete!");
		System.out.println("Total billed characters: " + totalBilledChars);
		System.out.println("========================================");
	}

	private void translateToLanguage(File sourceFile, ArbBundle sourceBundle,
			String sourceLang, String targetLang)
			throws IOException, DeepLException, InterruptedException {

		// Check if target file already exists and load it
		File targetFile = createTargetFile(sourceFile, targetLang);
		ArbBundle existingTargetBundle = null;
		if (targetFile.exists()) {
			try {
				existingTargetBundle = parser.parse(targetFile);
				System.out.println("Found existing target file with " +
					existingTargetBundle.getResourceCount() + " resources");
			} catch (Exception e) {
				System.err.println("WARN: Could not parse existing target file, will create new: " +
					e.getMessage());
			}
		}

		// Create target bundle
		ArbBundle targetBundle = new ArbBundle();

		// Copy and update global attributes
		for (var entry : sourceBundle.getGlobalAttributes().entrySet()) {
			String attrName = entry.getKey();
			String attrValue = entry.getValue();

			// Update @@locale to target language
			if (attrName.equals("@@locale")) {
				targetBundle.setGlobalAttribute(attrName, updateLocale(attrValue, targetLang));
			} else {
				targetBundle.setGlobalAttribute(attrName, attrValue);
			}
		}

		// If no locale was set, add one
		if (targetBundle.getLocale() == null) {
			targetBundle.setLocale(targetLang);
		}

		// Separate resources into: existing (reuse) and new (translate)
		List<String> textsToTranslate = new ArrayList<>();
		List<String> resourceIdsToTranslate = new ArrayList<>();
		List<ParameterProtector.ProtectedText> protectedTexts = new ArrayList<>();
		int reusedCount = 0;

		for (var entry : sourceBundle.getResources().entrySet()) {
			String resourceId = entry.getKey();
			ArbResource sourceResource = entry.getValue();

			// Check if this resource already exists in target
			if (existingTargetBundle != null && existingTargetBundle.hasResource(resourceId)) {
				// Reuse existing translation
				ArbResource existingResource = existingTargetBundle.getResource(resourceId);
				targetBundle.addResource(new ArbResource(resourceId, existingResource.getValue()));
				reusedCount++;
			} else {
				// Need to translate this resource
				// Protect parameters before translation
				ParameterProtector.ProtectedText protection =
					ParameterProtector.protect(sourceResource.getValue());

				textsToTranslate.add(protection.getProtectedText());
				resourceIdsToTranslate.add(resourceId);
				protectedTexts.add(protection);
			}
		}

		System.out.println("Reusing " + reusedCount + " existing translations");
		System.out.println("Translating " + textsToTranslate.size() + " new resources...");

		// Translate only new texts in batch
		int billedChars = 0;
		if (!textsToTranslate.isEmpty()) {
			List<TextResult> results = translator.translateText(
				textsToTranslate,
				sourceLang,
				targetLang
			);

			// Create target resources with translated values
			for (int i = 0; i < results.size(); i++) {
				TextResult result = results.get(i);
				String resourceId = resourceIdsToTranslate.get(i);
				ParameterProtector.ProtectedText protection = protectedTexts.get(i);

				// Restore original parameters after translation
				String translatedText = ParameterProtector.restore(
					result.getText(),
					protection.getParameterNames()
				);

				// Create target resource with translated value only (no metadata)
				ArbResource targetResource = new ArbResource(resourceId, translatedText);

				targetBundle.addResource(targetResource);
				billedChars += result.getBilledCharacters();
			}
		} else {
			System.out.println("No new resources to translate");
		}

		totalBilledChars += billedChars;
		System.out.println("Billed characters: " + billedChars);

		// Write target ARB file in compact mode (without metadata)
		writer.write(targetBundle, targetFile, false); // compact mode - no metadata
		System.out.println("Written to: " + targetFile.getAbsolutePath());
	}

	/**
	 * Extracts language code from ARB filename.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 *   <li>{@code app_en.arb} → "en"</li>
	 *   <li>{@code messages_de_DE.arb} → "de_DE"</li>
	 *   <li>{@code strings_fr.arb} → "fr"</li>
	 * </ul>
	 * </p>
	 *
	 * @param file The ARB file
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
	 * Creates the target file path by replacing the language code in the source filename.
	 *
	 * @param sourceFile The source ARB file
	 * @param targetLang The target language code
	 * @return The target file path
	 */
	private File createTargetFile(File sourceFile, String targetLang) {
		String filename = sourceFile.getName();
		Matcher matcher = LANG_PATTERN.matcher(filename);

		if (matcher.matches()) {
			String basename = matcher.group(1);
			String targetFilename = basename + "_" + targetLang + ".arb";
			return new File(sourceFile.getParentFile(), targetFilename);
		}

		// Fallback: append target language
		String basename = filename.replace(".arb", "");
		return new File(sourceFile.getParentFile(), basename + "_" + targetLang + ".arb");
	}

	/**
	 * Updates a locale string to use the target language code.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 *   <li>{@code updateLocale("en_US", "de")} → "de"</li>
	 *   <li>{@code updateLocale("en", "fr")} → "fr"</li>
	 * </ul>
	 * </p>
	 */
	private String updateLocale(String originalLocale, String targetLang) {
		// Simple approach: just return target language
		// Could be enhanced to preserve region (e.g., de_DE, fr_FR)
		return targetLang;
	}

	/**
	 * Gets the total number of characters billed by DeepL API during translation.
	 */
	public int getTotalBilledChars() {
		return totalBilledChars;
	}

	/**
	 * Command-line interface for ARB translation.
	 *
	 * @param args Command-line arguments: apiKey sourceFile targetLang1[,targetLang2,...]
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			printUsage();
			System.exit(1);
		}

		String apiKey = args[0];
		File sourceFile = new File(args[1]);
		List<String> targetLangs = List.of(args[2].split(","));

		if (!sourceFile.exists()) {
			System.err.println("Error: Source file not found: " + sourceFile.getAbsolutePath());
			System.err.println();
			printUsage();
			System.exit(1);
		}

		try {
			ArbTranslator translator = new ArbTranslator(apiKey);
			translator.translate(sourceFile, targetLangs);
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			System.err.println();
			printUsage();
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Error: Translation failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Prints usage information to stderr.
	 */
	private static void printUsage() {
		System.err.println("Usage: ArbTranslator <api-key> <source-file> <target-langs>");
		System.err.println();
		System.err.println("Arguments:");
		System.err.println("  <api-key>       DeepL API authentication key");
		System.err.println("  <source-file>   Path to source ARB file (e.g., app_en.arb)");
		System.err.println("  <target-langs>  Comma-separated list of target language codes");
		System.err.println();
		System.err.println("File naming convention:");
		System.err.println("  Source files must follow pattern: basename_lang.arb");
		System.err.println("  Examples: app_en.arb, messages_de.arb, strings_fr.arb");
		System.err.println();
		System.err.println("Examples:");
		System.err.println("  ArbTranslator YOUR_KEY app_en.arb de,fr,es");
		System.err.println("  ArbTranslator YOUR_KEY messages_en_US.arb de_DE,fr_FR");
	}
}
