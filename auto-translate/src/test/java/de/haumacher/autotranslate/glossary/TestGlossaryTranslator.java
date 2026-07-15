package de.haumacher.autotranslate.glossary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
import com.deepl.api.Translator;

/**
 * Unit tests for {@link GlossaryTranslator}: verifies that a registered glossary
 * is applied for the matching target language and omitted otherwise, without
 * calling the real DeepL API.
 */
public class TestGlossaryTranslator {

	/**
	 * Recording delegate that captures the glossary id passed on the last call.
	 */
	private static class RecordingTranslator extends Translator {

		String lastGlossaryId;

		boolean lastCallHadOptions;

		RecordingTranslator() {
			super("fake-api-key");
		}

		@Override
		public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang) {
			lastCallHadOptions = false;
			lastGlossaryId = null;
			return stub(texts, sourceLang, targetLang);
		}

		@Override
		public List<TextResult> translateText(List<String> texts, String sourceLang, String targetLang,
				TextTranslationOptions options) {
			lastCallHadOptions = true;
			lastGlossaryId = options == null ? null : options.getGlossaryId();
			return stub(texts, sourceLang, targetLang);
		}

		private static List<TextResult> stub(List<String> texts, String sourceLang, String targetLang) {
			List<TextResult> results = new ArrayList<>();
			for (String text : texts) {
				results.add(new TextResult(text, sourceLang, text.length(), targetLang));
			}
			return results;
		}
	}

	@Test
	public void testGlossaryAppliedForMatchingTarget() throws DeepLException, InterruptedException {
		RecordingTranslator delegate = new RecordingTranslator();
		GlossaryTranslator translator = new GlossaryTranslator(delegate, Map.of("en", "gid-123"));

		translator.translateText(List.of("Sperre"), "de", "en-US");

		assertEquals(true, delegate.lastCallHadOptions);
		assertEquals("gid-123", delegate.lastGlossaryId);
	}

	@Test
	public void testNoGlossaryForUnmappedTarget() throws DeepLException, InterruptedException {
		RecordingTranslator delegate = new RecordingTranslator();
		GlossaryTranslator translator = new GlossaryTranslator(delegate, Map.of("en", "gid-123"));

		translator.translateText(List.of("Sperre"), "de", "fr");

		assertEquals(false, delegate.lastCallHadOptions);
		assertNull(delegate.lastGlossaryId);
	}

	@Test
	public void testNormalizeLang() {
		assertEquals("en", GlossaryManager.normalizeLang("en-US"));
		assertEquals("zh", GlossaryManager.normalizeLang("zh-Hans"));
		assertEquals("pt", GlossaryManager.normalizeLang("pt-PT"));
		assertEquals("nb", GlossaryManager.normalizeLang("nb"));
		assertEquals("de", GlossaryManager.normalizeLang("DE"));
	}

}
