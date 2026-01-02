package de.haumacher.autotranslate;

import java.util.ArrayList;
import java.util.List;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;

/**
 * Stub translator for testing that returns predictable translations.
 *
 * <p>
 * The stub appends the target language code in brackets to show it was "translated".
 * For example, "Hello" translates to "Hello [de]" for German.
 * </p>
 *
 * <p>
 * This stub can be used in both HTML and ARB translation tests to avoid
 * real DeepL API calls during testing.
 * </p>
 */
public class StubTranslator extends Translator {

	/**
	 * Creates a new stub translator.
	 */
	public StubTranslator() {
		super("fake-api-key");
	}

	@Override
	public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang)
			throws DeepLException, InterruptedException {
		List<TextResult> results = new ArrayList<>();
		for (String text : texts) {
			// Simple stub: append language suffix to show it was "translated"
			String translated = translateSingle(text, targetLang);
			results.add(new TextResult(translated, sourceLang, text.length(), targetLang));
		}
		return results;
	}

	protected String translateSingle(String text, String targetLang) {
		return text + " [" + targetLang + "]";
	}
}
