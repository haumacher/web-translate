package de.haumacher.autotranslate.arb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.arb.io.ArbParser;
import de.haumacher.autotranslate.arb.io.ArbWriter;
import de.haumacher.autotranslate.arb.model.ArbBundle;
import de.haumacher.autotranslate.arb.model.ArbResource;
import de.haumacher.autotranslate.arb.model.ArbResourceAttributes;
import de.haumacher.autotranslate.glossary.GlossaryManager;
import de.haumacher.autotranslate.glossary.GlossaryTranslator;
import de.haumacher.autotranslate.log.ConsoleLogger;

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
 * </p>
 * <ul>
 *   <li>Source file: {@code basename_lang.arb} (e.g., {@code app_en.arb})</li>
 *   <li>Target files: {@code basename_targetLang.arb} (e.g., {@code app_de.arb}, {@code app_fr.arb})</li>
 * </ul>
 *
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ArbTranslator translator = new ArbTranslator("your-deepl-api-key");
 * List&lt;String&gt; targetLangs = List.of("de", "fr", "es");
 * translator.translate(new File("app_en.arb"), targetLangs);
 * </pre>
 *
 */
public class ArbTranslator {

	private static final Pattern LANG_PATTERN = Pattern.compile("(.+?)_(\\w{2}(?:_\\w+)?)\\.arb$");

	private static final String X_TRANSLATED_ATTR = "x-translated";

	/**
	 * Default language mappings for DeepL API compatibility.
	 *
	 * <p>
	 * DeepL requires specific language variants for certain languages.
	 * These defaults map generic codes to sensible variants:
	 * </p>
	 * <ul>
	 *   <li>{@code en} → {@code en-US} (American English)</li>
	 *   <li>{@code pt} → {@code pt-PT} (European Portuguese)</li>
	 * </ul>
	 */
	private static final Map<String, String> DEFAULT_LANGUAGE_MAPPINGS = Map.of(
		"en", "en-US",
		"pt", "pt-PT"
	);

	private final Translator _translator;
	private final ArbParser _parser;
	private final ArbWriter _writer;
	private final Map<String, String> _languageMappings;
	private final File _glossaryDir;

	private int _totalBilledChars = 0;

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
	private Map<String, String> _translatedResourceChecksums;

	/**
	 * Creates a new ARB translator with a translator instance.
	 *
	 * <p>
	 * Uses default language mappings: {@code en} → {@code en-US}, {@code pt} → {@code pt-PT}
	 *
	 * @param translator Translator instance for DeepL API communication
	 */
	public ArbTranslator(Translator translator) {
		this(translator, new HashMap<>(DEFAULT_LANGUAGE_MAPPINGS));
	}

	/**
	 * Creates a new ARB translator with a translator instance and custom language mappings.
	 *
	 * <p>
	 * Custom language mappings allow you to override DeepL API target language codes.
	 * For example, to use British English or Brazilian Portuguese:
	 * <pre>
	 * Map&lt;String, String&gt; mappings = new HashMap&lt;&gt;();
	 * mappings.put("en", "en-GB");  // Override default en-US with British English
	 * mappings.put("pt", "pt-BR");  // Override default pt-PT with Brazilian Portuguese
	 * ArbTranslator translator = new ArbTranslator(deeplClient, mappings);
	 * </pre>
	 *
	 * <p>
	 * Default mappings (used when not overridden):
	 * <ul>
	 *   <li>{@code en} → {@code en-US}</li>
	 *   <li>{@code pt} → {@code pt-PT}</li>
	 * </ul>
	 *
	 * @param translator Translator instance for DeepL API communication
	 * @param languageMappings Map from user language codes to DeepL API language codes
	 */
	public ArbTranslator(Translator translator, Map<String, String> languageMappings) {
		this(translator, languageMappings, null);
	}

	/**
	 * Creates a new ARB translator with a translator instance, custom language
	 * mappings and an optional glossary directory.
	 *
	 * @param translator       Translator instance for DeepL API communication
	 * @param languageMappings Map from user language codes to DeepL API language
	 *                         codes, or {@code null} for the defaults
	 * @param glossaryDir      Directory with {@code <source>-<target>.tsv} glossary
	 *                         files, or {@code null} to translate without glossaries
	 */
	public ArbTranslator(Translator translator, Map<String, String> languageMappings, File glossaryDir) {
		_translator = translator;
		_parser = new ArbParser();
		_writer = new ArbWriter();
		_languageMappings = new HashMap<>(DEFAULT_LANGUAGE_MAPPINGS);
		if (languageMappings != null) {
			_languageMappings.putAll(languageMappings);
		}
		_glossaryDir = glossaryDir;
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
		ArbBundle sourceBundle = _parser.parse(sourceFile);
		System.out.println("Parsed source ARB: " + sourceBundle.getResourceCount() + " resources");

		// Initialize tracking for translated resources
		_translatedResourceChecksums = new HashMap<>();

		try (GlossaryManager glossaries =
				GlossaryManager.create(_translator, sourceLang, targetLangs, _glossaryDir, ConsoleLogger.INSTANCE)) {
			Translator effectiveTranslator = glossaries.hasGlossaries()
				? new GlossaryTranslator(_translator, glossaries.getGlossaryIdByTargetLang())
				: _translator;

			// Translate to each target language
			for (String targetLang : targetLangs) {
				System.out.println();
				System.out.println("Translating to: " + targetLang);
				translateToLanguage(effectiveTranslator, sourceFile, sourceBundle, sourceLang, targetLang);
			}
		}

		// Update source file with checksums if any resources were translated
		if (!_translatedResourceChecksums.isEmpty()) {
			System.out.println();
			System.out.println("Updating source file with translation checksums...");

			// Update checksums for all translated resources
			for (var entry : _translatedResourceChecksums.entrySet()) {
				String resourceId = entry.getKey();
				String checksum = entry.getValue();

				ArbResource resource = sourceBundle.getResource(resourceId);
				if (resource != null) {
					resource.setAttribute(X_TRANSLATED_ATTR, checksum);
				}
			}

			_writer.write(sourceBundle, sourceFile, true); // verbose mode to preserve metadata
			System.out.println("Source file updated: " + sourceFile.getAbsolutePath());
			System.out.println("Updated checksums for " + _translatedResourceChecksums.size() + " resources");
		}

		System.out.println();
		System.out.println("========================================");
		System.out.println("Translation complete!");
		System.out.println("Total billed characters: " + _totalBilledChars);
		System.out.println("========================================");
	}

	private void translateToLanguage(Translator translator, File sourceFile, ArbBundle sourceBundle,
			String sourceLang, String targetLang)
			throws IOException, DeepLException, InterruptedException {

		// Normalize target language for DeepL API
		String deeplTargetLang = normalizeTargetLanguage(targetLang);

		// Check if target file already exists and load it
		File targetFile = createTargetFile(sourceFile, targetLang);
		ArbBundle existingTargetBundle = null;
		if (targetFile.exists()) {
			try {
				existingTargetBundle = _parser.parse(targetFile);
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
		List<String> translationContexts = new ArrayList<>();
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

			if (sourceResource.getAttribute(X_TRANSLATED_ATTR) != null) {
				storedChecksum = sourceResource.getAttribute(X_TRANSLATED_ATTR);

				if (!currentChecksum.equals(storedChecksum)) {
					// Text has changed - force update in all target files
					needsTranslation = true;
					updatedCount++;
					System.out.println("  Resource '" + resourceId + "' has changed (checksum mismatch), will update all translations");

					// Track checksum for update
					_translatedResourceChecksums.put(resourceId, currentChecksum);
				}
			} else {
				// No checksum stored - need to establish baseline
				// Track checksum even if resource exists in target files
				_translatedResourceChecksums.put(resourceId, currentChecksum);

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
				translationContexts.add(translationContext(sourceResource));
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
			// Phase 1: Collect all texts that need translation (including nested texts in complex
			// parameters), grouped by their translation context. The DeepL context parameter applies
			// to a whole request, therefore texts with different contexts cannot share a request.
			Map<String, Set<String>> textsByContext = new LinkedHashMap<>();
			int fragmentCount = 0;
			for (int i = 0; i < protectedTexts.size(); i++) {
				Set<String> textsToTranslate =
					textsByContext.computeIfAbsent(translationContexts.get(i), context -> new LinkedHashSet<>());

				// Use a dummy translator that just collects all text fragments
				protectedTexts.get(i).translate(text -> {
					textsToTranslate.add(text);
					return text; // Return original, we're just collecting
				});
			}
			for (Set<String> texts : textsByContext.values()) {
				fragmentCount += texts.size();
			}

			System.out.println("Collected " + fragmentCount + " text fragments to translate in "
				+ textsByContext.size() + " context group(s)");

			// Phase 2: Translate all collected texts using DeepL, one request per context group
			Map<String, Map<String, String>> translationsByContext = new HashMap<>();
			for (var group : textsByContext.entrySet()) {
				String context = group.getKey();
				List<String> texts = new ArrayList<>(group.getValue());

				List<TextResult> results;
				if (context == null) {
					results = translator.translateText(texts, sourceLang, deeplTargetLang);
				} else {
					results = translator.translateText(texts, sourceLang, deeplTargetLang,
						new TextTranslationOptions().setContext(context));
				}

				// Build translation map for this context
				Map<String, String> translationMap = new HashMap<>();
				for (int index = 0; index < texts.size(); index++) {
					translationMap.put(texts.get(index), results.get(index).getText());
					billedChars += results.get(index).getBilledCharacters();
				}
				translationsByContext.put(context, translationMap);
			}

			// Phase 3: Apply translations to all protected texts using the maps
			for (int i = 0; i < protectedTexts.size(); i++) {
				String resourceId = resourceIdsToTranslate.get(i);
				ParameterProtector.ProtectedText originalProtection = protectedTexts.get(i);
				Map<String, String> translationMap = translationsByContext.get(translationContexts.get(i));

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

		_totalBilledChars += billedChars;
		System.out.println("Billed characters: " + billedChars);

		// Write target ARB file in compact mode (without metadata)
		_writer.write(targetBundle, targetFile, false); // compact mode - no metadata
		System.out.println("Written to: " + targetFile.getAbsolutePath());
	}

	/**
	 * The context that is passed to DeepL when translating the given resource.
	 *
	 * <p>
	 * The DeepL <em>context</em> parameter takes additional text that describes the
	 * situation in which a text is used. It is not translated and does not count
	 * towards billing, but it helps to disambiguate short or ambiguous messages
	 * (e.g. whether "Open" is a verb on a button or an adjective describing a
	 * state). The {@code description} of an ARB resource holds exactly this kind of
	 * information and is therefore used as context.
	 * </p>
	 *
	 * <p>
	 * The ARB {@code context} attribute is deliberately <em>not</em> used: It holds
	 * a hierarchical identifier such as {@code HomePage:MainPanel}, which is not the
	 * natural-language text that DeepL expects.
	 * </p>
	 *
	 * @param resource The resource being translated.
	 * @return The context to send to DeepL, or {@code null} if the resource has no
	 *         description.
	 */
	private static String translationContext(ArbResource resource) {
		String description = resource.getDescription();
		if (description == null) {
			return null;
		}
		description = description.trim();
		return description.isEmpty() ? null : description;
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
	 * Normalizes target language codes for DeepL API compatibility.
	 *
	 * <p>
	 * DeepL requires specific language variants for certain languages:
	 * <ul>
	 *   <li>English: requires {@code en-US} or {@code en-GB} (not just {@code en})</li>
	 *   <li>Portuguese: requires {@code pt-PT} or {@code pt-BR} (not just {@code pt})</li>
	 * </ul>
	 *
	 * <p>
	 * This method uses configurable language mappings with the following defaults:
	 * <ul>
	 *   <li>{@code en} → {@code en-US}</li>
	 *   <li>{@code pt} → {@code pt-BR}</li>
	 * </ul>
	 *
	 * <p>
	 * Custom mappings can be provided via the constructor to override these defaults.
	 *
	 * @param targetLang The target language code (e.g., "en", "en-US", "de", "pt")
	 * @return Normalized language code compatible with DeepL API
	 */
	private String normalizeTargetLanguage(String targetLang) {
		String normalized = _languageMappings.get(targetLang.toLowerCase());
		if (normalized != null && !normalized.equals(targetLang)) {
			System.out.println("  Note: Mapping '" + targetLang + "' to '" + normalized + "' for DeepL API compatibility");
			return normalized;
		}

		// Return original if no mapping found
		return targetLang;
	}

	/**
	 * Gets the total number of characters billed by DeepL API during translation.
	 */
	public int getTotalBilledChars() {
		return _totalBilledChars;
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
