# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

web-translate is a Java-based HTML translation tool that integrates with DeepL API to translate HTML templates while preserving markup structure. The project is located in the `translate-web/` subdirectory.

## Build and Development Commands

### Building the Project
```bash
cd translate-web
mvn clean compile
```

### Running Tests
```bash
cd translate-web
mvn test
```

Run a single test class:
```bash
cd translate-web
mvn test -Dtest=TestHtmlAnalyzer
```

### Packaging
```bash
cd translate-web
mvn package
```

## Running the Translator

### Maven Goal (Recommended)

The recommended way to run the translator is using the Maven goal:

```bash
cd translate-web
mvn web-translate:translate -DapiKey=YOUR_DEEPL_API_KEY
```

**Configuration Options:**

All parameters can be configured via command-line properties:

```bash
mvn web-translate:translate \
  -DapiKey=YOUR_DEEPL_API_KEY \
  -DsourceLang=en \
  -DtargetLangs=de,fr,es \
  -DtemplateDirectory=./templates \
  -DpropertiesDirectory=./properties \
  -DpropertiesCharset=UTF-8
```

Or in your `pom.xml`:

```xml
<plugin>
  <groupId>de.haumacher</groupId>
  <artifactId>web-translate</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <configuration>
    <apiKey>${env.DEEPL_API_KEY}</apiKey>
    <sourceLang>en</sourceLang>
    <targetLangs>de,fr</targetLangs>
    <templateDirectory>${project.basedir}/templates</templateDirectory>
    <propertiesDirectory>${project.basedir}/properties</propertiesDirectory>
    <propertiesCharset>UTF-8</propertiesCharset>
  </configuration>
</plugin>
```

**Default Values:**
- `sourceLang`: `en`
- `targetLangs`: `de`
- `templateDirectory`: `${project.basedir}/templates`
- `propertiesDirectory`: `${project.basedir}/properties`
- `propertiesCharset`: `UTF-8`

**Directory Structure Expected:**
```
templates/
  en/              # Source language templates
    index.html
    about.html
properties/
  en/              # Generated source properties
  de/              # Generated target properties
  fr/              # Generated target properties
```

The Maven goal automatically runs all three phases (extract → translate → synthesize) in sequence.

### Java Main Class (Alternative)

You can also run the translator directly via the main class:

```bash
java -cp target/web-translate-1.0.0-SNAPSHOT.jar de.haumacher.webtranslate.Translator \
  <deepl-api-key> \
  <source-language> \
  <dest-languages-comma-separated> \
  <properties-dir> \
  <template-dir> \
  [properties-charset]
```

Example:
```bash
java -cp target/classes:~/.m2/repository/com/deepl/api/deepl-java/1.9.0/* \
  de.haumacher.webtranslate.Translator \
  "your-api-key" "en" "de,fr" ./properties ./templates UTF-8
```

### Running Individual Components

**Extract properties from HTML:**
```bash
java de.haumacher.webtranslate.extract.PropertiesExtractor <template-dir> <properties-dir> [charset]
```

**Translate properties files:**
```bash
java de.haumacher.webtranslate.translate.PropertiesTranslator \
  <api-key> <src-lang> <dest-langs> <properties-dir> [src-file] [name-strategy] [charset]
```

**Synthesize translated HTML:**
```bash
java de.haumacher.webtranslate.synthesize.TranslationSynthesizer \
  <template-dir> <properties-dir> <src-lang> <dest-langs> [charset]
```

## Architecture

### Three-Phase Translation Pipeline

The translation process has three sequential phases:

1. **Extraction** (`extract` package): Analyzes HTML templates, extracts translatable text to `.properties` files
2. **Translation** (`translate` package): Uses DeepL API to translate properties files
3. **Synthesis** (`synthesize` package): Injects translated text back into HTML structure

### Key Components

**HtmlAnalyzer** (`extract/HtmlAnalyzer.java`):
- Core text extraction and injection logic
- Assigns `data-tx` IDs to elements containing translatable content
- Extracts text from both element content and text attributes (`alt`, `title`, `placeholder`, `aria-label`, etc.)
- Handles nested markup by converting sub-elements to placeholder tags (`<x1>`, `<x2>`, etc.)

**TextExtractor/TextInjector** (`extract/TextExtractor.java`, `extract/TextInjector.java`):
- TextExtractor: Converts HTML element text to simplified format with `<xN>` placeholders for sub-elements
- TextInjector: Reconstructs HTML structure from translated text with `<xN>` tags

**PropertiesExtractor** (`extract/PropertiesExtractor.java`):
- Recursively processes HTML files in template directory
- Normalizes HTML and writes `.properties` files with extracted text
- Uses `data-tx` attribute to track element IDs (format: `t0001`, `t0002`, etc.)

**PropertiesTranslator** (`translate/PropertiesTranslator.java`):
- Translates `.properties` files using DeepL API
- Supports incremental translation (only translates new keys)
- Tracks billed character count
- Supports different naming strategies (LANG_TAG_DIR, etc.)

**TranslationSynthesizer** (`synthesize/TranslationSynthesizer.java`):
- Creates translated HTML files by injecting translated properties
- Maintains template structure while replacing text content

### Markup Preservation Algorithm

The tool preserves nested HTML markup during translation:

- **Text-only elements**: Assigned IDs, text fully extracted
- **Elements with text siblings**: Converted to `<xN>` placeholders
- **Elements with nested text**: Nested tags also get placeholders
- **Non-text elements**: Not indexed, structure preserved but ignored in translation

Example:
```html
<!-- Original -->
<p>Some text <a>with markup</a>.</p>

<!-- Extracted to properties -->
t0001=Some text <x1>with markup</x1>.

<!-- After translation -->
t0001=Etwas Text <x1>mit Markup</x1>.

<!-- Synthesized -->
<p>Etwas Text <a>mit Markup</a>.</p>
```

### Important Implementation Details

- **Code tags excluded**: `<code>`, `<pre>`, `<script>`, `<xmp>`, `<style>` are never translated
- **Text attributes**: Extracted separately with suffix (e.g., `t0001.title` for title attribute)
- **ID assignment**: Avoids duplicates, assigns sequential IDs starting from t0001
- **Charset handling**: Defaults to ISO-8859-1 for properties files, configurable
- **Error handling**: Warns about missing start/end tags in translated markup but continues processing

## Project Structure

```
translate-web/
├── src/main/java/de/haumacher/webtranslate/
│   ├── Translator.java              # Main orchestrator (CLI)
│   ├── maven/
│   │   └── TranslateMojo.java       # Maven plugin goal
│   ├── extract/
│   │   ├── HtmlAnalyzer.java        # Core analysis/extraction logic
│   │   ├── TextExtractor.java       # Text → <xN> conversion
│   │   ├── TextInjector.java        # <xN> → HTML reconstruction
│   │   ├── PropertiesExtractor.java # HTML → .properties
│   │   ├── PropertiesWriter.java    # Properties file writer
│   │   └── Stack.java               # Utility stack implementation
│   ├── translate/
│   │   ├── PropertiesTranslator.java # DeepL API integration
│   │   └── NameStrategy.java         # File naming strategies
│   └── synthesize/
│       └── TranslationSynthesizer.java # .properties → HTML
└── src/test/java/de/haumacher/webtranslate/extract/
    └── TestHtmlAnalyzer.java         # Core tests

Dependencies:
- deepl-java 1.9.0 (DeepL API client)
- maven-plugin-api 3.9.6 (Maven plugin development)
- maven-plugin-annotations 3.11.0 (Maven plugin annotations)
- JUnit Jupiter 5.11.1 (testing)
- Java 17+
```

## Development Guidelines

### Working with HTML Analysis

When modifying text extraction or injection logic, understand the mapping between original elements and placeholder tags. The index in `children` list corresponds to the `<xN>` tag number minus 1.

### Testing

TestHtmlAnalyzer.java demonstrates the full extraction-injection cycle. When adding features, add test cases showing:
1. Original HTML
2. Extracted properties format
3. Translated properties
4. Final synthesized HTML

### API Key Management

Never commit DeepL API keys. They should always be passed as command-line arguments.
