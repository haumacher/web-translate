package de.haumacher.autotranslate.gradle;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.deepl.api.DeepLClient;
import com.deepl.api.Translator;

import de.haumacher.autotranslate.arb.ArbTranslator;

/**
 * Gradle task for translating ARB (Application Resource Bundle) files using DeepL API.
 *
 * <p>
 * This task translates Flutter/Dart localization files while preserving
 * ICU MessageFormat syntax and tracking changes via checksums.
 *
 *
 * <p>
 * The task supports incremental translation using CRC32 checksums stored in the
 * {@code x-translated} custom attribute. Resources are only re-translated when:
 * <ul>
 *   <li>They don't exist in the target file yet</li>
 *   <li>The checksum doesn't match (indicating the source text changed)</li>
 * </ul>
 *
 */
public abstract class TranslateArbTask extends DefaultTask {

    /**
     * DeepL API key for authentication.
     *
     * <p>
     * This parameter is optional and can be used to directly provide the API key.
     * If both serverId and apiKey are provided, apiKey takes precedence.
     * </p>
     */
    @Input
    @Optional
    public abstract Property<String> getApiKey();

    /**
     * Server ID for retrieving DeepL API key from gradle.properties.
     *
     * <p>
     * The API key should be stored in gradle.properties as:
     * <pre>
     * deepl.apiKey=YOUR_DEEPL_API_KEY
     * </pre>
     * Or in ~/.gradle/gradle.properties for global configuration.
     */
    @Input
    @Optional
    public abstract Property<String> getServerId();

    /**
     * Source ARB file to translate.
     *
     * <p>
     * The file must follow the naming convention: {@code basename_lang.arb}
     * (e.g., {@code app_en.arb}, {@code messages_de.arb}).
     * The source language is automatically extracted from the filename.
     * </p>
     */
    @InputFile
    public abstract Property<File> getSourceFile();

    /**
     * List of target language codes (e.g., ["de", "fr", "es"]).
     *
     * <p>
     * Target files will be created in the same directory as the source file
     * with the appropriate language suffix (e.g., {@code app_de.arb}, {@code app_fr.arb}).
     * </p>
     */
    @Input
    public abstract ListProperty<String> getTargetLangs();

    /**
     * Language mappings for DeepL API compatibility.
     *
     * <p>
     * Allows customizing how generic language codes map to DeepL-specific variants.
     * For example, "en" can be mapped to "en-US" or "en-GB".
     *
     * <p>
     * Default mappings are used if not specified:
     * <ul>
     *   <li>{@code en} → {@code en-US}</li>
     *   <li>{@code pt} → {@code pt-PT}</li>
     * </ul>
     */
    @Input
    @Optional
    public abstract MapProperty<String, String> getLanguageMappings();

    @TaskAction
    public void translateArb() {
        try {
            getLogger().lifecycle("========================================");
            getLogger().lifecycle("Starting ARB translation");
            getLogger().lifecycle("========================================");

            File sourceFile = getSourceFile().get();

            // Validate source file
            if (!sourceFile.exists()) {
                throw new GradleException(
                    "Source file not found: " + sourceFile.getAbsolutePath()
                );
            }

            // Extract source language from filename
            String sourceLang = ArbTranslator.extractLanguage(sourceFile);
            if (sourceLang == null) {
                throw new GradleException(
                    "Cannot determine source language from filename: " + sourceFile.getName() +
                    ". Expected format: basename_lang.arb (e.g., app_en.arb)"
                );
            }

            List<String> targetLangsList = getTargetLangs().get();

            getLogger().lifecycle("Source file: " + sourceFile.getAbsolutePath());
            getLogger().lifecycle("Source language: " + sourceLang);
            getLogger().lifecycle("Target languages: " + targetLangsList);
            getLogger().lifecycle("");

            // Resolve API key
            String resolvedApiKey = resolveApiKey();

            // Create DeepL translator
            Translator deeplTranslator = new DeepLClient(resolvedApiKey);

            // Create ARB translator with custom language mappings if provided
            ArbTranslator arbTranslator;
            if (getLanguageMappings().isPresent() && !getLanguageMappings().get().isEmpty()) {
                Map<String, String> mappings = getLanguageMappings().get();
                getLogger().lifecycle("Using custom language mappings: " + mappings);
                arbTranslator = new ArbTranslator(deeplTranslator, mappings);
            } else {
                arbTranslator = new ArbTranslator(deeplTranslator);
            }

            arbTranslator.translate(sourceFile, targetLangsList);

            getLogger().lifecycle("");
            getLogger().lifecycle("========================================");
            getLogger().lifecycle("ARB translation completed successfully!");
            getLogger().lifecycle("Total billed characters: " + arbTranslator.getTotalBilledChars());
            getLogger().lifecycle("========================================");

        } catch (Exception ex) {
            throw new GradleException("ARB translation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Resolves the DeepL API key from either direct configuration or server credentials.
     *
     * @return The resolved API key
     * @throws GradleException If API key cannot be resolved
     */
    private String resolveApiKey() throws GradleException {
        // If apiKey is directly provided, use it
        if (getApiKey().isPresent() && !getApiKey().get().trim().isEmpty()) {
            getLogger().debug("Using directly configured API key");
            return getApiKey().get();
        }

        // Otherwise, retrieve from gradle.properties using serverId
        String serverId = getServerId().getOrElse("deepl");
        getLogger().debug("Retrieving API key from property: " + serverId + ".apiKey");

        String propertyKey = serverId + ".apiKey";

        // Try project properties first, then global gradle properties
        if (getProject().hasProperty(propertyKey)) {
            String key = (String) getProject().property(propertyKey);
            if (key != null && !key.trim().isEmpty()) {
                return key;
            }
        }

        // Check environment variable as fallback
        String envKey = "DEEPL_API_KEY";
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            getLogger().debug("Using API key from environment variable: " + envKey);
            return envValue;
        }

        throw new GradleException(
            "DeepL API key not found. Please provide it via:\n" +
            "  1. translateArb { apiKey = 'YOUR_KEY' } in build.gradle\n" +
            "  2. " + propertyKey + "=YOUR_KEY in gradle.properties\n" +
            "  3. Environment variable " + envKey + "=YOUR_KEY"
        );
    }
}
