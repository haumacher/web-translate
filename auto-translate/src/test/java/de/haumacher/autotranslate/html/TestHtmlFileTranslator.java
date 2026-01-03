package de.haumacher.autotranslate.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HtmlFileTranslator}.
 */
public class TestHtmlFileTranslator {

	@Test
	public void testEnsureAllTagsWithMissingNestedTags() {
		String original = "foo <x1><x2></x2></x1> bar";
		String translated = "foo bar";

		String result = HtmlFileTranslator.ensureAllTags(original, translated);

		assertEquals("foo bar<x1><x2></x2></x1>", result);
	}

	@Test
	public void testEnsureAllTagsWithNoMissingTags() {
		String original = "foo <x1>bar</x1> baz";
		String translated = "foo <x1>bar</x1> baz";

		String result = HtmlFileTranslator.ensureAllTags(original, translated);

		assertEquals("foo <x1>bar</x1> baz", result);
	}

	@Test
	public void testEnsureAllTagsWithPartiallyMissingTags() {
		String original = "text <x1>with <x2>nested</x2> tags</x1> and <x3>more</x3>";
		String translated = "text <x1>avec tags</x1> et";

		String result = HtmlFileTranslator.ensureAllTags(original, translated);

		// Tags x2 and x3 are missing, should be appended
		assertEquals("text <x1>avec tags</x1> et<x2></x2><x3></x3>", result);
	}

	@Test
	public void testEnsureAllTagsWithCompletelyMissingTags() {
		String original = "<x1>hello</x1> <x2>world</x2>";
		String translated = "bonjour monde";

		String result = HtmlFileTranslator.ensureAllTags(original, translated);

		assertEquals("bonjour monde<x1></x1><x2></x2>", result);
	}

	@Test
	public void testEnsureAllTagsWithEmptyOriginalTags() {
		String original = "foo <x1></x1> bar";
		String translated = "foo bar";

		String result = HtmlFileTranslator.ensureAllTags(original, translated);

		assertEquals("foo bar<x1></x1>", result);
	}
}
