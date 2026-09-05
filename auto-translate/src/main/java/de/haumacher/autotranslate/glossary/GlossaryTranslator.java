package de.haumacher.autotranslate.glossary;

import java.util.List;
import java.util.Map;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
import com.deepl.api.Translator;

/**
 * Decorating {@link Translator} that transparently applies a DeepL glossary to
 * every translation for which a glossary has been registered.
 *
 * <p>
 * The decorator wraps a real DeepL {@link Translator} and a map of glossary IDs
 * keyed by normalized target language (see
 * {@link GlossaryManager#normalizeLang(String)}). Whenever a translation to a
 * target language with a registered glossary is requested, the glossary is
 * passed to DeepL via {@link TextTranslationOptions#setGlossaryId(String)}. For
 * all other target languages the plain translation call is used, so behavior is
 * unchanged when no glossary applies.
 * </p>
 *
 * <p>
 * Extending {@link Translator} (rather than introducing a new interface) keeps
 * the existing call sites in {@code HtmlFileTranslator}/{@code ArbTranslator}
 * untouched: they keep calling the three-argument
 * {@link #translateText(List, String, String)} method. This mirrors the
 * approach already used by the test {@code StubTranslator}.
 * </p>
 */
public class GlossaryTranslator extends Translator {

	private final Translator _delegate;

	private final Map<String, String> _glossaryIdByTargetLang;

	/**
	 * Creates a new {@link GlossaryTranslator}.
	 *
	 * @param delegate                The real DeepL translator that performs the
	 *                                actual translation.
	 * @param glossaryIdByTargetLang  Glossary IDs keyed by normalized target
	 *                                language (see
	 *                                {@link GlossaryManager#normalizeLang(String)}).
	 */
	public GlossaryTranslator(Translator delegate, Map<String, String> glossaryIdByTargetLang) {
		// The super instance is never used - all calls are delegated. A dummy key
		// is sufficient, see StubTranslator for the same pattern.
		super("fake-api-key");
		_delegate = delegate;
		_glossaryIdByTargetLang = glossaryIdByTargetLang;
	}

	@Override
	public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang)
			throws DeepLException, InterruptedException {
		String glossaryId = glossaryId(targetLang);
		if (glossaryId != null) {
			TextTranslationOptions options = new TextTranslationOptions().setGlossaryId(glossaryId);
			return _delegate.translateText(texts, sourceLang, targetLang, options);
		}
		return _delegate.translateText(texts, sourceLang, targetLang);
	}

	/**
	 * Adds the glossary to the given options.
	 *
	 * <p>
	 * The caller's options object is left untouched: If a glossary applies, a
	 * copy carrying the glossary ID is passed to the delegate. Options that
	 * already name a glossary are used as-is.
	 * </p>
	 */
	@Override
	public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang,
			TextTranslationOptions options) throws DeepLException, InterruptedException {
		if (options == null) {
			return translateText(texts, sourceLang, targetLang);
		}

		String glossaryId = glossaryId(targetLang);
		if (glossaryId != null && options.getGlossaryId() == null) {
			return _delegate.translateText(texts, sourceLang, targetLang, copy(options).setGlossaryId(glossaryId));
		}
		return _delegate.translateText(texts, sourceLang, targetLang, options);
	}

	private String glossaryId(String targetLang) {
		return _glossaryIdByTargetLang.get(GlossaryManager.normalizeLang(targetLang));
	}

	/**
	 * Creates a modifiable copy of the given options.
	 */
	private static TextTranslationOptions copy(TextTranslationOptions options) {
		return new TextTranslationOptions()
			.setFormality(options.getFormality())
			.setGlossaryId(options.getGlossaryId())
			.setSentenceSplittingMode(options.getSentenceSplittingMode())
			.setPreserveFormatting(options.isPreserveFormatting())
			.setContext(options.getContext())
			.setModelType(options.getModelType())
			.setTagHandling(options.getTagHandling())
			.setOutlineDetection(options.isOutlineDetection())
			.setIgnoreTags(options.getIgnoreTags())
			.setNonSplittingTags(options.getNonSplittingTags())
			.setSplittingTags(options.getSplittingTags());
	}

}
