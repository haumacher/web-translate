package de.haumacher.autotranslate;

import java.util.ArrayList;
import java.util.List;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
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
	 * One entry per translated text in the order the texts were passed to the
	 * translator, see {@link TranslationRequest}.
	 */
	private final List<TranslationRequest> _requests = new ArrayList<>();

	/**
	 * A single text that was passed to the translator, together with the DeepL
	 * context it was translated with.
	 */
	public record TranslationRequest(String text, String context) {
		// Pure value type.
	}

	/**
	 * Creates a new stub translator.
	 */
	public StubTranslator() {
		super("fake-api-key");
	}

	@Override
	public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang)
			throws DeepLException, InterruptedException {
		return translateText(texts, sourceLang, targetLang, null);
	}

	@Override
	public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang,
			TextTranslationOptions options) throws DeepLException, InterruptedException {
		List<TextResult> results = new ArrayList<>();
		String context = options == null ? null : options.getContext();
		for (String text : texts) {
			_requests.add(new TranslationRequest(text, context));

			// Simple stub: append language suffix to show it was "translated"
			String translated = translateSingle(text, targetLang);
			results.add(new TextResult(translated, sourceLang, text.length(), targetLang));
		}
		return results;
	}

	/**
	 * The DeepL contexts the given text was translated with, in the order the
	 * translations were requested.
	 *
	 * <p>
	 * A text is translated more than once, if it is used by multiple resources with
	 * different contexts. Texts translated without context contribute a
	 * {@code null} entry.
	 * </p>
	 *
	 * @param text The source text passed to the translator.
	 * @return The contexts used for the given text, empty if the text was never
	 *         translated.
	 */
	public List<String> getContexts(String text) {
		List<String> result = new ArrayList<>();
		for (TranslationRequest request : _requests) {
			if (request.text().equals(text)) {
				result.add(request.context());
			}
		}
		return result;
	}

	/**
	 * Whether the given text was passed to the translator at all.
	 */
	public boolean wasTranslated(String text) {
		return !getContexts(text).isEmpty();
	}

	/**
	 * All translations that were requested, in call order.
	 */
	public List<TranslationRequest> getRequests() {
		return _requests;
	}

	protected String translateSingle(String text, String targetLang) {
		return text + " [" + targetLang + "]";
	}
}
