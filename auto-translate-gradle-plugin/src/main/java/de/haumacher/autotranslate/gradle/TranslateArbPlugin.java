package de.haumacher.autotranslate.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin for ARB (Application Resource Bundle) auto-translation using DeepL API.
 *
 * <p>
 * This plugin provides a task to automatically translate Flutter/Dart localization files
 * while preserving ICU MessageFormat syntax and tracking changes via checksums.
 *
 *
 * <p>
 * Usage example in build.gradle:
 * <pre>
 * plugins {
 *     id 'de.haumacher.auto-translate-arb' version '1.0.0'
 * }
 *
 * translateArb {
 *     apiKey = System.getenv('DEEPL_API_KEY') // or use serverId
 *     serverId = 'deepl' // optional: retrieve API key from ~/.gradle/gradle.properties
 *     sourceFile = file('lib/l10n/app_en.arb')
 *     targetLangs = ['de', 'fr', 'es']
 * }
 * </pre>
 *
 */
public class TranslateArbPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Create the extension for configuration
        TranslateArbExtension extension = project.getExtensions().create(
            "translateArb",
            TranslateArbExtension.class,
            project
        );

        // Register the translation task
        project.getTasks().register("translateArb", TranslateArbTask.class, task -> {
            task.setGroup("translation");
            task.setDescription("Translate ARB files using DeepL API");

            // Connect extension properties to task inputs
            task.getApiKey().set(extension.getApiKey());
            task.getServerId().set(extension.getServerId());
            task.getSourceFile().set(extension.getSourceFile());
            task.getTargetLangs().set(extension.getTargetLangs());
            task.getLanguageMappings().set(extension.getLanguageMappings());
            task.getGlossaryDir().set(extension.getGlossaryDir());
        });
    }
}
