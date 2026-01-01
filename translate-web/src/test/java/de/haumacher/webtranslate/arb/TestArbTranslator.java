package de.haumacher.webtranslate.arb;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link ArbTranslator}.
 */
public class TestArbTranslator {

	@Test
	public void testExtractLanguageSimple() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("app_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("app_de.arb")));
		assertEquals("fr", ArbTranslator.extractLanguage(new File("messages_fr.arb")));
		assertEquals("es", ArbTranslator.extractLanguage(new File("strings_es.arb")));
	}

	@Test
	public void testExtractLanguageWithRegion() {
		assertEquals("en_US", ArbTranslator.extractLanguage(new File("app_en_US.arb")));
		assertEquals("de_DE", ArbTranslator.extractLanguage(new File("app_de_DE.arb")));
		assertEquals("zh_CN", ArbTranslator.extractLanguage(new File("messages_zh_CN.arb")));
	}

	@Test
	public void testExtractLanguageWithPath() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("/path/to/app_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("./locales/messages_de.arb")));
	}

	@Test
	public void testExtractLanguageInvalid() {
		assertNull(ArbTranslator.extractLanguage(new File("app.arb")));
		assertNull(ArbTranslator.extractLanguage(new File("app_en.txt")));
		assertNull(ArbTranslator.extractLanguage(new File("app.json")));
	}

	@Test
	public void testExtractLanguageComplexBasename() {
		assertEquals("en", ArbTranslator.extractLanguage(new File("my_app_strings_en.arb")));
		assertEquals("de", ArbTranslator.extractLanguage(new File("flutter_app_de.arb")));
	}
}
