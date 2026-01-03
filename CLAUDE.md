# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

auto-translate is a Java-based HTML translation tool that integrates with DeepL API to translate HTML templates while preserving markup structure. The project is located in the `auto-translate/` subdirectory.

## Build and Development Commands

### Building the Project
```bash
cd auto-translate
mvn clean compile
```

### Running Tests
```bash
cd auto-translate
mvn test
```

Run a single test class:
```bash
cd auto-translate
mvn test -Dtest=TestHtmlAnalyzer
```

### Packaging
```bash
cd auto-translate
mvn package
```

## Running the Translator

### Maven Goal (Recommended)

The recommended way to run the translator is using the Maven goal:

```bash
cd auto-translate
mvn auto-translate:translate
```

**Configuration Options:**

**Recommended: Using Maven Server Credentials (Secure)**

Add your DeepL API key to `~/.m2/settings.xml`:
```xml
<settings>
  <servers>
    <server>
      <id>deepl</id>
      <passphrase>YOUR_DEEPL_API_KEY</passphrase>
    </server>
  </servers>
</settings>
```

Then configure the plugin in your `pom.xml`:
```xml
<plugin>
  <groupId>de.haumacher</groupId>
  <artifactId>auto-translate</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>translate</goal>
      </goals>
      <configuration>
        <!-- serverId defaults to "deepl", can be omitted -->
        <sourceLang>en</sourceLang>
        <targetLangs>de,fr</targetLangs>
        <templateDirectory>${project.basedir}/templates</templateDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Alternative: Direct API Key (Less Secure)**

Via command-line properties:
```bash
mvn auto-translate:translate \
  -Dtranslate.apiKey=YOUR_DEEPL_API_KEY \
  -Dtranslate.sourceLang=en \
  -Dtranslate.targetLangs=de,fr,es \
  -Dtranslate.templateDirectory=./templates
```

Or in `pom.xml` configuration:
```xml
<configuration>
  <apiKey>${env.DEEPL_API_KEY}</apiKey>
  <sourceLang>en</sourceLang>
  <targetLangs>de,fr</targetLangs>
</configuration>
```

**Parameters:**

- `serverId` (default: `"deepl"`): Server ID in settings.xml containing the API key
- `apiKey` (optional): Direct API key (overrides serverId if provided)
- `sourceLang` (default: `"en"`): Source language code
- `targetLangs` (default: `"de"`): Comma-separated target language codes
- `templateDirectory` (default: `${project.basedir}/templates`): Template directory

**Directory Structure Expected:**
```
templates/
  en/              # Source language templates
    index.html
    about.html
  de/              # Generated German templates
  fr/              # Generated French templates
```

The Maven goal translates HTML files directly without creating intermediate properties files. It uses CRC checksums in `data-tx` attributes for incremental translation.

### Java Main Class (Alternative)

You can also run the translator directly via the main class:

```bash
java -cp target/auto-translate-1.0.0-SNAPSHOT.jar de.haumacher.autotranslate.Translator \
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
  de.haumacher.autotranslate.Translator \
  "your-api-key" "en" "de,fr" ./properties ./templates UTF-8
```

### Running Individual Components

**Extract properties from HTML:**
```bash
java de.haumacher.autotranslate.html.extract.PropertiesExtractor <template-dir> <properties-dir> [charset]
```

**Translate properties files:**
```bash
java de.haumacher.autotranslate.html.translate.PropertiesTranslator \
  <api-key> <src-lang> <dest-langs> <properties-dir> [src-file] [name-strategy] [charset]
```

**Synthesize translated HTML:**
```bash
java de.haumacher.autotranslate.html.synthesize.TranslationSynthesizer \
  <template-dir> <properties-dir> <src-lang> <dest-langs> [charset]
```

## Architecture

### Three-Phase Translation Pipeline

The translation process has three sequential phases:

1. **Extraction** (`html.extract` package): Analyzes HTML templates, extracts translatable text to `.properties` files
2. **Translation** (`html.translate` package): Uses DeepL API to translate properties files
3. **Synthesis** (`html.synthesize` package): Injects translated text back into HTML structure

### Key Components

**HtmlAnalyzer** (`html.extract/HtmlAnalyzer.java`):
- Core text extraction and injection logic
- Assigns `data-tx` IDs to elements containing translatable content
- Extracts text from both element content and text attributes (`alt`, `title`, `placeholder`, `aria-label`, etc.)
- Handles nested markup by converting sub-elements to placeholder tags (`<x1>`, `<x2>`, etc.)

**TextExtractor/TextInjector** (`html.extract/TextExtractor.java`, `html.extract/TextInjector.java`):
- TextExtractor: Converts HTML element text to simplified format with `<xN>` placeholders for sub-elements
- TextInjector: Reconstructs HTML structure from translated text with `<xN>` tags

**PropertiesExtractor** (`html.extract/PropertiesExtractor.java`):
- Recursively processes HTML files in template directory
- Normalizes HTML and writes `.properties` files with extracted text
- Uses `data-tx` attribute to track element IDs (format: `t0001`, `t0002`, etc.)

**PropertiesTranslator** (`html.translate/PropertiesTranslator.java`):
- Translates `.properties` files using DeepL API
- Supports incremental translation (only translates new keys)
- Tracks billed character count
- Supports different naming strategies (LANG_TAG_DIR, etc.)

**TranslationSynthesizer** (`html.synthesize/TranslationSynthesizer.java`):
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
auto-translate/
├── src/main/java/de/haumacher/autotranslate/
│   ├── Translator.java              # Main orchestrator (CLI)
│   ├── maven/
│   │   ├── TranslateMojo.java       # Maven plugin goal for HTML
│   │   └── TranslateArbMojo.java    # Maven plugin goal for ARB
│   ├── html/
│   │   ├── extract/
│   │   │   ├── HtmlAnalyzer.java        # Core analysis/extraction logic
│   │   │   ├── TextExtractor.java       # Text → <xN> conversion
│   │   │   ├── TextInjector.java        # <xN> → HTML reconstruction
│   │   │   ├── PropertiesExtractor.java # HTML → .properties
│   │   │   ├── PropertiesWriter.java    # Properties file writer
│   │   │   └── Stack.java               # Utility stack implementation
│   │   ├── translate/
│   │   │   ├── PropertiesTranslator.java # DeepL API integration
│   │   │   └── NameStrategy.java         # File naming strategies
│   │   └── synthesize/
│   │       └── TranslationSynthesizer.java # .properties → HTML
│   └── arb/
│       ├── ArbTranslator.java       # ARB translation tool
│       ├── ParameterProtector.java  # Parameter protection for translation
│       ├── io/
│       │   ├── ArbParser.java           # JSON → ArbBundle
│       │   ├── ArbWriter.java           # ArbBundle → JSON
│       │   └── IcuMessageParser.java    # ICU MessageFormat parser
│       └── model/
│           ├── ArbBundle.java           # ARB file container
│           ├── ArbResource.java         # ARB resource entry
│           ├── ArbResourceAttributes.java # ARB metadata
│           └── ArbPlaceholder.java      # ARB placeholder metadata
└── src/test/java/de/haumacher/autotranslate/
    ├── html/
    │   └── TestHtmlAnalyzer.java         # HTML analysis tests
    └── arb/
        ├── TestArbParser.java            # ARB parser tests
        ├── TestArbTranslator.java        # ARB translator tests
        ├── TestParameterProtector.java   # Parameter protection tests
        └── TestIcuMessageParser.java     # ICU format tests

Dependencies:
- deepl-java 1.9.0 (DeepL API client)
- gson 2.10.1 (JSON parsing for ARB)
- maven-plugin-api 3.9.6 (Maven plugin development)
- maven-plugin-annotations 3.11.0 (Maven plugin annotations)
- JUnit Jupiter 5.11.1 (testing)
- Java 17+
```

## ARB (Application Resource Bundle) Translation

### Overview

The ARB translation functionality provides automated translation of Flutter/Dart localization files using DeepL API. ARB files use JSON format with ICU MessageFormat syntax for complex features like plurals and gender selection.

### Running ARB Translation

**Command-line usage:**
```bash
java de.haumacher.autotranslate.arb.ArbTranslator \
  <deepl-api-key> \
  <source-arb-file> \
  <target-languages>
```

**Example:**
```bash
java de.haumacher.autotranslate.arb.ArbTranslator \
  YOUR_API_KEY \
  app_en.arb \
  de,fr,es
```

### File Naming Convention

ARB files must follow the pattern: `basename_lang.arb`

Examples:
- `app_en.arb` (English source)
- `app_de.arb` (German target)
- `messages_en_US.arb` (English US with region)

The tool automatically extracts the source language from the filename and creates target files with corresponding language codes.

### Key Features

**1. Incremental Translation**
- Loads existing target files to avoid retranslating
- Only translates new or modified resources
- Significantly reduces API costs for updates

**2. Parameter Protection**
- Protects ARB parameters from translation: `{username}` → preserved as `{username}`
- Handles ICU MessageFormat syntax (plural, select, selectordinal)
- Translates only the actual text, not format identifiers

**3. ICU MessageFormat Support**

Supports complex ICU syntax including:

**Simple placeholders:**
```json
"greeting": "Hello {username}!"
```

**Plural forms:**
```json
"messages": "{count, plural, =0{no messages} =1{1 message} other{{count} messages}}"
```

**Select forms:**
```json
"possessive": "{gender, select, male{his} female{her} other{their}}"
```

**Nested formats:**
```json
"complex": "{gender, select, male{He has {count, plural, one{# item} other{# items}}} other{They have items}}"
```

### Parameter Protection Algorithm

The translation process protects code identifiers while exposing translatable text:

**Original:**
```json
"{count, plural, =1{1 Meldung} other{{count} Meldungen}}"
```

**Protected for DeepL:**
```
<x1>count, plural,</x1> <x2>=1</x2>{1 Meldung} <x3>other</x3>{<x4>count</x4> Meldungen}
```

**After translation:**
```
<x1>count, plural,</x1> <x2>=1</x2>{1 report} <x3>other</x3>{<x4>count</x4> reports}
```

**Restored:**
```json
"{count, plural, =1{1 report} other{{count} reports}}"
```

**What's protected:**
- Parameter names (`count`, `gender`, etc.)
- Format types (`plural`, `select`, `selectordinal`)
- Selector keywords (`=1`, `one`, `other`, `male`, `female`)
- Special symbols (`#` in plural forms)

**What's translated:**
- Actual message text inside cases
- Text outside parameter definitions

### ARB File Structure

**Source file (app_en.arb):**
```json
{
  "@@locale": "en",
  "greeting": "Hello {username}!",
  "@greeting": {
    "description": "Welcome message",
    "placeholders": {
      "username": {"example": "John"}
    }
  }
}
```

**Target file (app_de.arb) - compact format:**
```json
{
  "@@locale": "de",
  "greeting": "Hallo {username}!"
}
```

Note: Target files use compact format (no metadata) since descriptions and placeholder definitions are redundant.

### ARB Architecture Components

**ArbParser/ArbWriter** (`arb.io` package):
- Parse ARB JSON to in-memory `ArbBundle` objects
- Write bundles back to JSON (verbose or compact mode)
- Preserve resource order using LinkedHashMap

**IcuMessageParser** (`arb.io` package):
- Parses ICU MessageFormat syntax
- Identifies translatable text vs. identifiers
- Handles nested formats (select within plural, etc.)
- Supports all ICU format types

**Model Classes** (`arb.model` package):
- `ArbBundle`: Container for all ARB resources and metadata
- `ArbResource`: Individual translatable resource entry
- `ArbResourceAttributes`: Resource metadata (description, placeholders, etc.)
- `ArbPlaceholder`: Placeholder metadata for parameters

**ParameterProtector** (`arb` package):
- Protects parameters using XML placeholder tags
- Uses ICU parser for complex formats
- Falls back to simple regex for basic parameters
- Restores original parameter names after translation

**ArbTranslator** (`arb` package):
- Orchestrates the translation workflow
- Loads existing translations for incremental updates
- Batch translates new resources via DeepL
- Writes compact target files

### Testing ARB Translation

Test files demonstrate:
- Simple parameter protection: `TestParameterProtector.java`
- ICU format parsing: `TestIcuMessageParser.java`
- ARB parsing/writing: `TestArbParser.java`
- Language extraction: `TestArbTranslator.java`

## Development Guidelines

### Code Conventions

**Instance Variable Naming:**
All instance variables (non-static fields) must start with an underscore prefix (`_`). This convention eliminates the need for `this.` prefixes and makes instance variable access clearer throughout the codebase.

Examples:
```java
private String _apiKey;
private List<String> _destLangs;
private final File _templateDirectory;
```

### Working with HTML Analysis

When modifying text extraction or injection logic, understand the mapping between original elements and placeholder tags. The index in `children` list corresponds to the `<xN>` tag number minus 1.

### Working with ARB Translation

When extending ICU MessageFormat support:
1. Update `IcuMessageParser` to handle new format types
2. Ensure new formats are properly protected in `toProtectedText()`
3. Add test cases showing the format works end-to-end
4. Test with real-world complex nested examples

### Testing

**HTML Translation:**
TestHtmlAnalyzer.java demonstrates the full extraction-injection cycle. When adding features, add test cases showing:
1. Original HTML
2. Extracted properties format
3. Translated properties
4. Final synthesized HTML

**ARB Translation:**
Test files demonstrate round-trip translation (protect → translate → restore). When adding features, test:
1. Parser correctly identifies all message parts
2. Protection preserves identifiers
3. Restoration works with reordered/modified translations
4. Real-world complex examples work

### API Key Management

Never commit DeepL API keys. They should always be passed as command-line arguments.
