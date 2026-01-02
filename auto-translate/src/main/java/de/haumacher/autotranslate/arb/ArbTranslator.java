package de.haumacher.autotranslate.arb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.arb.io.ArbParser;
import de.haumacher.autotranslate.arb.io.ArbWriter;
import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbResource;
import de.haumacher.autotranslate.arb.model.ArbResourceAttributes;

import com.deepl.api.DeepLClient;

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

	private static final String X_TRANSLATED_ATTR = "x-translated";

	private final Translator translator;
	private final ArbParser parser;
	private final ArbWriter writer;

	private int totalBilledChars = 0;

	/**
	 * Tracks checksums for resources that were translated during the current translation session.
	 *
	 * <p>
	 * Maps resource IDs to their CRC32 checksums. This map accumulates entries as resources
	 * are translated across all target languages, and is used to update the source ARB file
	 * with {@code x-translated} attributes after all translations are complete.
	 * </p>
	 *
	 * <p>
	 * By deferring checksum updates until after all target languages are processed, we ensure
	 * that modified resources are correctly translated to ALL target languages, not just the
	 * first one.
	 * </p>
	 */
	private Map<String, String> translatedResourceChecksums;

	/**
	 * Creates a new ARB translator with a translator instance.
	 *
	 * @param translator Translator instance for DeepL API communication
	 */
	public ArbTranslator(Translator translator) {
		this.translator = translator;
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

		// Initialize tracking for translated resources
		translatedResourceChecksums = new HashMap<>();

		// Translate to each target language
		for (String targetLang : targetLangs) {
			System.out.println();
			System.out.println("Translating to: " + targetLang);
			translateToLanguage(sourceFile, sourceBundle, sourceLang, targetLang);
		}

		// Update source file with checksums if any resources were translated
		if (!translatedResourceChecksums.isEmpty()) {
			System.out.println();
			System.out.println("Updating source file with translation checksums...");

			// Update checksums for all translated resources
			for (var entry : translatedResourceChecksums.entrySet()) {
				String resourceId = entry.getKey();
				String checksum = entry.getValue();

				ArbResource resource = sourceBundle.getResource(resourceId);
				if (resource != null) {
					ArbResourceAttributes attrs = resource.getAttributes();
					if (attrs == null) {
						attrs = new ArbResourceAttributes();
						resource.setAttributes(attrs);
					}
					attrs.addCustomAttribute(X_TRANSLATED_ATTR, checksum);
				}
			}

			writer.write(sourceBundle, sourceFile, true); // verbose mode to preserve metadata
			System.out.println("Source file updated: " + sourceFile.getAbsolutePath());
			System.out.println("Updated checksums for " + translatedResourceChecksums.size() + " resources");
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
		List<String> resourceIdsToTranslate = new ArrayList<>();
		List<ParameterProtector.ProtectedText> protectedTexts = new ArrayList<>();
		int reusedCount = 0;
		int updatedCount = 0;

		for (var entry : sourceBundle.getResources().entrySet()) {
			String resourceId = entry.getKey();
			ArbResource sourceResource = entry.getValue();
			String sourceValue = sourceResource.getValue();

			// Determine if this resource needs translation
			boolean needsTranslation = false;

			// Check for x-translated checksum
			String currentChecksum = computeChecksum(sourceValue);
			String storedChecksum = null;

			if (sourceResource.hasAttributes() && sourceResource.getAttributes().getCustomAttributes().containsKey(X_TRANSLATED_ATTR)) {
				storedChecksum = sourceResource.getAttributes().getCustomAttributes().get(X_TRANSLATED_ATTR);

				if (!currentChecksum.equals(storedChecksum)) {
					// Text has changed - force update in all target files
					needsTranslation = true;
					updatedCount++;
					System.out.println("  Resource '" + resourceId + "' has changed (checksum mismatch), will update all translations");

					// Track checksum for update
					translatedResourceChecksums.put(resourceId, currentChecksum);
				}
			} else {
				// No checksum stored - need to establish baseline
				// Track checksum even if resource exists in target files
				translatedResourceChecksums.put(resourceId, currentChecksum);

				if (existingTargetBundle == null || !existingTargetBundle.hasResource(resourceId)) {
					// Resource doesn't exist in target - translate it
					needsTranslation = true;
				}
			}

			if (needsTranslation) {
				// Need to translate this resource
				// Protect parameters before translation
				ParameterProtector.ProtectedText protection =
					ParameterProtector.protect(sourceValue);

				resourceIdsToTranslate.add(resourceId);
				protectedTexts.add(protection);
			} else if (existingTargetBundle != null && existingTargetBundle.hasResource(resourceId)) {
				// Reuse existing translation
				ArbResource existingResource = existingTargetBundle.getResource(resourceId);
				targetBundle.addResource(new ArbResource(resourceId, existingResource.getValue()));
				reusedCount++;
			}
		}

		System.out.println("Reusing " + reusedCount + " existing translations");
		if (updatedCount > 0) {
			System.out.println("Updating " + updatedCount + " modified resources");
		}
		System.out.println("Translating " + protectedTexts.size() + " resources...");

		// Translate only new texts in batch
		int billedChars = 0;
		if (!protectedTexts.isEmpty()) {
			// Phase 1: Collect all texts that need translation (including nested texts in complex parameters)
			Set<String> textsToTranslate = new LinkedHashSet<>();
			for (ParameterProtector.ProtectedText protection : protectedTexts) {
				// Use a dummy translator that just collects all text fragments
				protection.translate(text -> {
					textsToTranslate.add(text);
					return text; // Return original, we're just collecting
				});
			}

			System.out.println("Collected " + textsToTranslate.size() + " text fragments to translate");

			// Phase 2: Translate all collected texts using DeepL
			List<TextResult> results = translator.translateText(
				new ArrayList<>(textsToTranslate),
				sourceLang,
				targetLang
			);

			// Build translation map
			Map<String, String> translationMap = new HashMap<>();
			int index = 0;
			for (String original : textsToTranslate) {
				translationMap.put(original, results.get(index).getText());
				billedChars += results.get(index).getBilledCharacters();
				index++;
			}

			// Phase 3: Apply translations to all protected texts using the map
			for (int i = 0; i < protectedTexts.size(); i++) {
				String resourceId = resourceIdsToTranslate.get(i);
				ParameterProtector.ProtectedText originalProtection = protectedTexts.get(i);

				// Translate using the pre-built translation map
				ParameterProtector.ProtectedText translatedProtection =
					originalProtection.translate(text -> translationMap.getOrDefault(text, text));

				// Restore original parameters after translation
				String translatedText = translatedProtection.restore();

				// Create target resource with translated value only (no metadata)
				ArbResource targetResource = new ArbResource(resourceId, translatedText);

				targetBundle.addResource(targetResource);
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
			Translator deeplTranslator = new DeepLClient(apiKey);
			ArbTranslator translator = new ArbTranslator(deeplTranslator);
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

	/**
	 * Computes the CRC32 checksum of a text string.
	 *
	 * @param text The text to compute checksum for
	 * @return The CRC32 checksum as a hexadecimal string
	 */
	public static String computeChecksum(String text) {
		CRC32 crc = new CRC32();
		crc.update(text.getBytes());
		return Long.toHexString(crc.getValue());
	}
}
