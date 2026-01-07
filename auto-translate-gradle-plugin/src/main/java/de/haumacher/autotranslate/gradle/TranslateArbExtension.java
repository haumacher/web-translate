package de.haumacher.autotranslate.gradle;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Extension for configuring ARB translation in Gradle build scripts.
 *
 * <p>
 * This extension allows users to configure the ARB translation plugin
 * using a declarative DSL in their build.gradle file.
 *
 *
 * <p>
 * Example configuration:
 * <pre>
 * translateArb {
 *     apiKey = System.getenv('DEEPL_API_KEY')
 *     sourceFile = file('lib/l10n/app_en.arb')
 *     targetLangs = ['de', 'fr', 'es']
 * }
 * </pre>
 *
 *
 * <p>
 * Alternative using serverId (recommended for security):
 * <pre>
 * // In gradle.properties or ~/.gradle/gradle.properties:
 * // deepl.apiKey=YOUR_DEEPL_API_KEY
 *
 * translateArb {
 *     serverId = 'deepl' // defaults to 'deepl'
 *     sourceFile = file('lib/l10n/app_en.arb')
 *     targetLangs = ['de', 'fr', 'es']
 * }
 * </pre>
 *
 */
public class TranslateArbExtension {

    private final Property<String> apiKey;
    private final Property<String> serverId;
    private final Property<File> sourceFile;
    private final ListProperty<String> targetLangs;

    public TranslateArbExtension(Project project) {
        this.apiKey = project.getObjects().property(String.class);
        this.serverId = project.getObjects().property(String.class);
        this.serverId.convention("deepl"); // Default server ID

        this.sourceFile = project.getObjects().property(File.class);
        this.targetLangs = project.getObjects().listProperty(String.class);
    }

    /**
     * Gets the DeepL API key property.
     *
     * <p>
     * If set, this API key will be used directly for authentication.
     * If not set, the plugin will attempt to retrieve the API key from
     * gradle.properties using the serverId.
     * </p>
     */
    public Property<String> getApiKey() {
        return apiKey;
    }

    /**
     * Sets the DeepL API key directly.
     *
     * @param apiKey The DeepL API key
     */
    public void setApiKey(String apiKey) {
        this.apiKey.set(apiKey);
    }

    /**
     * Gets the server ID property for retrieving API key from gradle.properties.
     *
     * <p>
     * The API key will be read from the property: {@code <serverId>.apiKey}
     * For example, if serverId is "deepl", the plugin will look for
     * {@code deepl.apiKey} in gradle.properties.
     * </p>
     */
    public Property<String> getServerId() {
        return serverId;
    }

    /**
     * Sets the server ID for retrieving API key from gradle.properties.
     *
     * @param serverId The server ID (defaults to "deepl")
     */
    public void setServerId(String serverId) {
        this.serverId.set(serverId);
    }

    /**
     * Gets the source ARB file property.
     *
     * <p>
     * The source file must follow the naming convention: {@code basename_lang.arb}
     * (e.g., {@code app_en.arb}, {@code messages_de.arb}).
     * </p>
     */
    public Property<File> getSourceFile() {
        return sourceFile;
    }

    /**
     * Sets the source ARB file.
     *
     * @param sourceFile The source ARB file
     */
    public void setSourceFile(File sourceFile) {
        this.sourceFile.set(sourceFile);
    }

    /**
     * Gets the list of target language codes.
     *
     * <p>
     * Target files will be created in the same directory as the source file
     * with the appropriate language suffix.
     * </p>
     */
    public ListProperty<String> getTargetLangs() {
        return targetLangs;
    }

    /**
     * Sets the list of target language codes.
     *
     * @param targetLangs List of target language codes (e.g., ["de", "fr", "es"])
     */
    public void setTargetLangs(Iterable<String> targetLangs) {
        this.targetLangs.set(targetLangs);
    }
}
