package de.haumacher.autotranslate.html;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.html.extract.HtmlAnalyzer;
import de.haumacher.autotranslate.html.extract.PropertiesExtractor;

/**
 * Translates a single HTML file to multiple target languages using an in-memory approach.
 *
 * <p>
 * This translator:
 * <ul>
 *   <li>Parses the source HTML file and extracts translatable text with data-tx IDs</li>
 *   <li>Tracks which data-tx IDs are new (freshly assigned) vs existing (already in source)</li>
 *   <li>For existing target files, loads them to reuse already-translated content</li>
 *   <li>Only translates new content or content missing from target files</li>
 *   <li>Writes translated HTML files directly without intermediate properties files</li>
 * </ul>
 * </p>
 */
public class HtmlFileTranslator {

	private final Translator _translator;
	private final String _srcLang;
	private final List<String> _destLangs;

	private int _totalBilledChars = 0;

	/**
	 * Creates a new HTML file translator.
	 *
	 * @param translator Translator instance for DeepL API communication
	 * @param srcLang Source language code
	 * @param destLangs List of destination language codes
	 */
	public HtmlFileTranslator(Translator translator, String srcLang, List<String> destLangs) {
		_translator = translator;
		_srcLang = srcLang;
		_destLangs = destLangs;
	}

	/**
	 * Translates a single HTML file to all target languages.
	 *
	 * @param sourceFile Source HTML file
	 * @param targetDir Base directory for target language subdirectories
	 * @param relativePath Relative path from language directory to HTML file
	 * @throws ParserConfigurationException If HTML parser configuration fails
	 * @throws SAXException If HTML parsing fails
	 * @throws IOException If file I/O fails
	 * @throws DeepLException If DeepL API call fails
	 * @throws InterruptedException If translation is interrupted
	 */
	public void translateFile(File sourceFile, File targetDir, String relativePath)
			throws ParserConfigurationException, SAXException, IOException, DeepLException, InterruptedException {

		System.out.println("Processing: " + sourceFile.getPath());

		// Parse and analyze source HTML
		Document sourceDoc = PropertiesExtractor.parseHtml(sourceFile);
		HtmlAnalyzer sourceAnalyzer = new HtmlAnalyzer(sourceDoc);

		// Analyze adds new data-tx IDs where needed and tracks existing ones
		sourceAnalyzer.analyze();

		// Extract all text (includes both new and existing IDs)
		Map<String, String> sourceTexts = sourceAnalyzer.getTextById();
		Map<String, String> currentCrcs = sourceAnalyzer.getCrcById();
		Map<String, String> oldCrcs = sourceAnalyzer.getOldCrcById();

		// Determine which texts need translation:
		// 1. New IDs (not in source before)
		// 2. Changed IDs (CRC mismatch between old and current)
		Set<String> existingIds = sourceAnalyzer.getExistingIds();
		Set<String> textsNeedingTranslation = new HashSet<>();

		for (Map.Entry<String, String> entry : sourceTexts.entrySet()) {
			String textId = entry.getKey();
			String baseId = extractBaseId(textId);

			// New text (ID didn't exist before)
			if (!existingIds.contains(baseId)) {
				textsNeedingTranslation.add(textId);
			}
			// Existing text but CRC changed
			else {
				// CRC is stored under the base ID (covers all texts for that element)
				String oldCrc = oldCrcs.get(baseId);
				String currentCrc = currentCrcs.get(baseId);

				// If no old CRC, assume unchanged (backward compatibility)
				// If CRCs differ, text has changed - re-translate ALL texts for this element
				if (oldCrc != null && currentCrc != null && !oldCrc.equals(currentCrc)) {
					textsNeedingTranslation.add(textId);
				}
			}
		}

		// Overwrite source file with normalized HTML (including new data-tx attributes)
		try (FileOutputStream out = new FileOutputStream(sourceFile)) {
			PropertiesExtractor.serializeDocument(out, sourceDoc);
		}

		// Translate to each target language
		for (String destLang : _destLangs) {
			translateToLanguage(sourceFile, targetDir, relativePath, destLang, sourceTexts, textsNeedingTranslation);
		}
	}

	/**
	 * Extracts the base ID from a text ID (removes attribute suffix if present).
	 * For example: "t0001" -> "t0001", "t0001.title" -> "t0001"
	 */
	private String extractBaseId(String textId) {
		int dotIndex = textId.indexOf('.');
		return dotIndex >= 0 ? textId.substring(0, dotIndex) : textId;
	}

	private void translateToLanguage(File sourceFile, File targetDir, String relativePath,
			String destLang, Map<String, String> sourceTexts, Set<String> textsNeedingTranslation)
			throws IOException, ParserConfigurationException, SAXException, DeepLException, InterruptedException {

		// Determine target file location
		File targetFile = new File(new File(targetDir, destLang), relativePath);

		// Load existing target file if it exists
		Map<String, String> existingTargetTexts = new HashMap<>();
		if (targetFile.exists()) {
			try {
				Document targetDoc = PropertiesExtractor.parseHtml(targetFile);
				HtmlAnalyzer targetAnalyzer = new HtmlAnalyzer(targetDoc);
				targetAnalyzer.analyze();
				existingTargetTexts.putAll(targetAnalyzer.getTextById());
			} catch (Exception e) {
				System.err.println("WARN: Could not parse existing target file, will create new: " +
					e.getMessage());
			}
		}

		// Determine which texts need translation
		List<String> textsToTranslate = new ArrayList<>();
		Map<String, String> textIdToSourceText = new HashMap<>();

		for (Map.Entry<String, String> entry : sourceTexts.entrySet()) {
			String textId = entry.getKey();
			String sourceText = entry.getValue();

			// Translate if marked as needing translation (new or changed)
			if (textsNeedingTranslation.contains(textId)) {
				textsToTranslate.add(sourceText);
				textIdToSourceText.put(textId, sourceText);
			}
			// For unchanged texts, only translate if not in target
			else if (!existingTargetTexts.containsKey(textId)) {
				textsToTranslate.add(sourceText);
				textIdToSourceText.put(textId, sourceText);
			}
		}

		// Translate missing texts in batch
		Map<String, String> translatedTexts = new HashMap<>();
		if (!textsToTranslate.isEmpty()) {
			System.out.println("Translating " + textsToTranslate.size() + " texts to " + destLang);

			List<TextResult> results = _translator.translateText(textsToTranslate, _srcLang, destLang);

			int billedChars = 0;
			for (int i = 0; i < textsToTranslate.size(); i++) {
				String sourceText = textsToTranslate.get(i);
				String translatedText = results.get(i).getText();
				if (translatedText.isEmpty()) {
					// Safety: Sometimes the translation creates no result (empty string).
					// When using this empty string, the translation is repeated for each run.
					translatedText = sourceText;
				}
				// Ensure all tags from original are present in translation
				translatedText = ensureAllTags(sourceText, translatedText);
				translatedTexts.put(sourceText, translatedText);
				billedChars += results.get(i).getBilledCharacters();
			}

			_totalBilledChars += billedChars;
			System.out.println("Billed characters: " + billedChars);
		} else {
			System.out.println("No new texts to translate to " + destLang + ", reusing existing");
		}

		// Build complete target texts map (existing + newly translated)
		Map<String, String> targetTexts = new HashMap<>();
		for (Map.Entry<String, String> entry : sourceTexts.entrySet()) {
			String textId = entry.getKey();
			String sourceText = entry.getValue();

			// Use new translation if it was just translated
			if (translatedTexts.containsKey(sourceText)) {
				targetTexts.put(textId, translatedTexts.get(sourceText));
			}
			// Otherwise use existing translation if available
			else if (existingTargetTexts.containsKey(textId)) {
				targetTexts.put(textId, existingTargetTexts.get(textId));
			}
		}

		// Create target HTML from source structure with translated texts
		Document targetDoc = PropertiesExtractor.parseHtml(sourceFile);
		HtmlAnalyzer targetAnalyzer = new HtmlAnalyzer(targetDoc);
		targetAnalyzer.analyze();
		targetAnalyzer.setTextById(targetTexts);

		try {
			targetAnalyzer.inject();
		} catch (Exception ex) {
			throw new RuntimeException(
				"Failed to inject translations into target file: " + targetFile.getAbsolutePath() +
				"\nSource file: " + sourceFile.getAbsolutePath() +
				"\nTarget language: " + destLang,
				ex
			);
		}

		// Write target file
		targetFile.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(targetFile)) {
			PropertiesExtractor.serializeDocument(out, targetDoc);
		}
		System.out.println("Written to: " + targetFile.getAbsolutePath());
	}

	/**
	 * Gets the total number of characters billed by DeepL API during translation.
	 */
	public int getTotalBilledChars() {
		return _totalBilledChars;
	}

	/**
	 * Ensures all protection tags from the original text are present in the translated text.
	 * If the translation engine swallowed some tags, this method appends them at the end.
	 * The method preserves the complete nested structure of missing tags.
	 *
	 * @param originalText The original source text with protection tags
	 * @param translatedText The translated text (may be missing some tags)
	 * @return The repaired translated text with all tags present
	 */
	static String ensureAllTags(String originalText, String translatedText) {
		// Find all tag numbers in the translated text
		Set<Integer> tagsInTranslation = findAllTagNumbers(translatedText);

		// Find all tag numbers in the original text
		Set<Integer> tagsInOriginal = findAllTagNumbers(originalText);

		// Find missing tags
		Set<Integer> missingTags = new HashSet<>(tagsInOriginal);
		missingTags.removeAll(tagsInTranslation);

		if (missingTags.isEmpty()) {
			// All tags present, no repair needed
			return translatedText;
		}

		// Strip original text to get only the missing tag structure
		String missingStructure = stripTextAndPresentTags(originalText, tagsInTranslation);

		// Append missing structure to translation
		return translatedText + missingStructure;
	}

	/**
	 * Finds all tag numbers present in the text (both opening and closing tags).
	 */
	private static Set<Integer> findAllTagNumbers(String text) {
		Set<Integer> tagNumbers = new HashSet<>();
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<(?:/)?x(\\d+)>");
		java.util.regex.Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			int tagNum = Integer.parseInt(matcher.group(1));
			tagNumbers.add(tagNum);
		}

		return tagNumbers;
	}

	/**
	 * Removes all text content and all tags that are present in the translation.
	 * Returns only the missing tag structure.
	 *
	 * @param originalText The original text with all tags
	 * @param presentTags Set of tag numbers that are already in the translation
	 * @return The structure containing only missing tags
	 */
	private static String stripTextAndPresentTags(String originalText, Set<Integer> presentTags) {
		StringBuilder result = new StringBuilder();
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<(?:/)?x(\\d+)>");
		java.util.regex.Matcher matcher = pattern.matcher(originalText);

		while (matcher.find()) {
			// Skip text content between tags
			int tagNum = Integer.parseInt(matcher.group(1));

			if (!presentTags.contains(tagNum)) {
				// This tag is missing, include it
				result.append(matcher.group());
			}
		}

		return result.toString();
	}
}
