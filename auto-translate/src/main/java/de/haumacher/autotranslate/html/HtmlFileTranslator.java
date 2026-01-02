package de.haumacher.autotranslate.html;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

		// Determine which IDs are new (need unconditional translation)
		Set<String> existingIds = sourceAnalyzer.getExistingIds();
		Map<String, String> newTexts = new HashMap<>();
		for (Map.Entry<String, String> entry : sourceTexts.entrySet()) {
			if (!existingIds.contains(entry.getKey())) {
				newTexts.put(entry.getKey(), entry.getValue());
			}
		}

		// Overwrite source file with normalized HTML (including new data-tx attributes)
		try (FileOutputStream out = new FileOutputStream(sourceFile)) {
			PropertiesExtractor.serializeDocument(out, sourceDoc);
		}

		// Translate to each target language
		for (String destLang : _destLangs) {
			translateToLanguage(sourceFile, targetDir, relativePath, destLang, sourceTexts, newTexts);
		}
	}

	private void translateToLanguage(File sourceFile, File targetDir, String relativePath,
			String destLang, Map<String, String> sourceTexts, Map<String, String> newTexts)
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

			// Always translate new texts
			if (newTexts.containsKey(textId)) {
				textsToTranslate.add(sourceText);
				textIdToSourceText.put(textId, sourceText);
			}
			// For existing texts, only translate if not in target
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

			// Use existing translation if available
			if (existingTargetTexts.containsKey(textId)) {
				targetTexts.put(textId, existingTargetTexts.get(textId));
			}
			// Otherwise use new translation
			else if (translatedTexts.containsKey(sourceText)) {
				targetTexts.put(textId, translatedTexts.get(sourceText));
			}
		}

		// Create target HTML from source structure with translated texts
		Document targetDoc = PropertiesExtractor.parseHtml(sourceFile);
		HtmlAnalyzer targetAnalyzer = new HtmlAnalyzer(targetDoc);
		targetAnalyzer.analyze();
		targetAnalyzer.setTextById(targetTexts);
		targetAnalyzer.inject();

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
}
