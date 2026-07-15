package de.haumacher.autotranslate.glossary;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.deepl.api.DeepLException;
import com.deepl.api.GlossaryEntries;
import com.deepl.api.GlossaryInfo;
import com.deepl.api.GlossaryLanguagePair;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.log.Logger;

/**
 * Creates and manages the lifecycle of DeepL glossaries for a translation run.
 *
 * <p>
 * Glossaries let you pin the translation of certain terms (e.g. always render
 * the German word {@code Sperre} as English {@code block}) so that DeepL does
 * not translate the same source word inconsistently across a document or
 * between runs. Because glossaries are consulted on <em>every</em> translation,
 * a pinned term stays consistent even after the source text (and hence its CRC)
 * changes and the affected unit is re-translated.
 * </p>
 *
 * <h2>Ephemeral lifecycle</h2>
 *
 * <p>
 * DeepL glossaries are account-persistent server-side objects that survive until
 * explicitly deleted. This manager treats them as <em>ephemeral</em>: it creates
 * the glossaries needed for the current run and deletes them again on
 * {@link #close()}. To avoid leaking glossaries from a crashed previous run, it
 * also sweeps and deletes any pre-existing glossaries whose name carries this
 * tool's {@link #NAME_PREFIX} before creating new ones. Glossaries not created by
 * this tool are never touched.
 * </p>
 *
 * <h2>Glossary files</h2>
 *
 * <p>
 * Entries are read from a directory containing one tab-separated file per
 * source/target language pair, named {@code <source>-<target>.tsv} using base
 * language codes (e.g. {@code de-en.tsv}). Each line is
 * {@code sourceTerm<TAB>targetTerm}. A glossary is only created for a target
 * language when a matching file exists <em>and</em> DeepL supports a glossary for
 * that language pair; otherwise the target is translated without a glossary.
 * </p>
 */
public class GlossaryManager implements AutoCloseable {

	/** Prefix used for the names of all glossaries created by this tool. */
	public static final String NAME_PREFIX = "auto-translate:";

	private final Translator _translator;

	private final Logger _logger;

	private final Map<String, String> _glossaryIdByTargetLang = new HashMap<>();

	private final List<String> _createdGlossaryIds = new ArrayList<>();

	private GlossaryManager(Translator translator, Logger logger) {
		_translator = translator;
		_logger = logger;
	}

	/**
	 * Normalizes a language code to the base language expected by the DeepL
	 * glossary API, e.g. {@code en-US} to {@code en}, {@code zh-Hans} to
	 * {@code zh}. The result is lower-case.
	 */
	public static String normalizeLang(String lang) {
		if (lang == null) {
			return null;
		}
		String result = lang;
		int sep = result.indexOf('-');
		if (sep < 0) {
			sep = result.indexOf('_');
		}
		if (sep >= 0) {
			result = result.substring(0, sep);
		}
		return result.toLowerCase();
	}

	/**
	 * Creates the glossaries needed to translate from {@code sourceLang} into the
	 * given {@code targetLangs}, reading entries from {@code glossaryDir}.
	 *
	 * <p>
	 * If {@code glossaryDir} is {@code null} or not an existing directory, an
	 * empty manager is returned (no glossaries, no server calls beyond the orphan
	 * sweep are made). The returned manager must be {@link #close() closed} to
	 * delete the created glossaries.
	 * </p>
	 *
	 * @param translator  The real DeepL translator used to manage glossaries.
	 * @param sourceLang  Source language code (e.g. {@code de}).
	 * @param targetLangs Target language codes (e.g. {@code en-US}, {@code fr}).
	 * @param glossaryDir Directory containing {@code <source>-<target>.tsv} files,
	 *                    or {@code null}.
	 * @param logger      Logger for progress/diagnostic output.
	 */
	public static GlossaryManager create(Translator translator, String sourceLang, List<String> targetLangs,
			File glossaryDir, Logger logger) throws DeepLException, InterruptedException {
		GlossaryManager manager = new GlossaryManager(translator, logger);
		if (glossaryDir == null || !glossaryDir.isDirectory()) {
			return manager;
		}

		manager.sweepOrphans();
		manager.createGlossaries(sourceLang, targetLangs, glossaryDir);
		return manager;
	}

	private void sweepOrphans() throws DeepLException, InterruptedException {
		for (GlossaryInfo info : _translator.listGlossaries()) {
			if (info.getName() != null && info.getName().startsWith(NAME_PREFIX)) {
				_logger.info("Deleting orphaned glossary from a previous run: " + info.getName());
				_translator.deleteGlossary(info.getGlossaryId());
			}
		}
	}

	private void createGlossaries(String sourceLang, List<String> targetLangs, File glossaryDir)
			throws DeepLException, InterruptedException {
		String sourceBase = normalizeLang(sourceLang);
		Set<String> supportedTargets = supportedTargetsFor(sourceBase);

		for (String targetLang : targetLangs) {
			String targetBase = normalizeLang(targetLang);

			// The source language itself is among the "target" directories in some
			// setups; never build a glossary for the identity pair.
			if (targetBase.equals(sourceBase)) {
				continue;
			}

			File glossaryFile = new File(glossaryDir, sourceBase + "-" + targetBase + ".tsv");
			if (!glossaryFile.isFile()) {
				continue;
			}

			if (!supportedTargets.contains(targetBase)) {
				_logger.warn("Skipping glossary for " + sourceBase + "->" + targetBase
					+ ": DeepL does not support glossaries for this language pair.");
				continue;
			}

			GlossaryEntries entries = readEntries(glossaryFile);
			if (entries == null || entries.isEmpty()) {
				continue;
			}

			String name = NAME_PREFIX + sourceBase + "-" + targetBase;
			GlossaryInfo info = _translator.createGlossary(name, sourceBase, targetBase, entries);
			_createdGlossaryIds.add(info.getGlossaryId());
			awaitReady(info);
			_glossaryIdByTargetLang.put(targetBase, info.getGlossaryId());
			_logger.info("Created glossary " + name + " with " + entries.size() + " entries for "
				+ sourceBase + "->" + targetBase);
		}
	}

	private Set<String> supportedTargetsFor(String sourceBase) throws DeepLException, InterruptedException {
		Set<String> result = new HashSet<>();
		for (GlossaryLanguagePair pair : _translator.getGlossaryLanguages()) {
			if (normalizeLang(pair.getSourceLanguage()).equals(sourceBase)) {
				result.add(normalizeLang(pair.getTargetLanguage()));
			}
		}
		return result;
	}

	private GlossaryEntries readEntries(File glossaryFile) {
		try {
			String tsv = Files.readString(glossaryFile.toPath(), StandardCharsets.UTF_8);
			return GlossaryEntries.fromTsv(tsv);
		} catch (IOException | IllegalArgumentException ex) {
			_logger.warn("Ignoring invalid glossary file " + glossaryFile.getAbsolutePath() + ": " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Waits until the glossary reports as ready. Text glossaries are normally
	 * ready immediately; this guards against the rare case of a short delay.
	 */
	private void awaitReady(GlossaryInfo info) throws DeepLException, InterruptedException {
		GlossaryInfo current = info;
		for (int attempt = 0; attempt < 10 && !current.isReady(); attempt++) {
			Thread.sleep(500);
			current = _translator.getGlossary(info.getGlossaryId());
		}
	}

	/**
	 * Whether any glossary was registered. When {@code false} there is no point in
	 * wrapping the translator in a {@link GlossaryTranslator}.
	 */
	public boolean hasGlossaries() {
		return !_glossaryIdByTargetLang.isEmpty();
	}

	/**
	 * Glossary IDs keyed by normalized target language, for use by
	 * {@link GlossaryTranslator}.
	 */
	public Map<String, String> getGlossaryIdByTargetLang() {
		return Collections.unmodifiableMap(_glossaryIdByTargetLang);
	}

	@Override
	public void close() {
		for (String glossaryId : _createdGlossaryIds) {
			try {
				_translator.deleteGlossary(glossaryId);
			} catch (DeepLException | InterruptedException ex) {
				_logger.warn("Could not delete glossary " + glossaryId + ": " + ex.getMessage());
				if (ex instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			}
		}
		_createdGlossaryIds.clear();
		_glossaryIdByTargetLang.clear();
	}

}
