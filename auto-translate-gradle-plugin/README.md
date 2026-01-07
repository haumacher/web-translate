# ARB Auto-Translation Gradle Plugin

A Gradle plugin for automatic translation of ARB (Application Resource Bundle) files using DeepL API. This plugin is designed for Flutter/Dart projects and preserves ICU MessageFormat syntax while providing incremental translation support.

## Features

- **Automatic ARB Translation**: Translates Flutter/Dart localization files using DeepL API
- **ICU MessageFormat Preservation**: Correctly handles complex message formats including:
  - Simple placeholders: `{username}`
  - Plural forms: `{count, plural, one{# item} other{# items}}`
  - Select forms: `{gender, select, male{his} female{her} other{their}}`
  - Nested formats
- **Incremental Translation**: Uses CRC32 checksums to avoid retranslating unchanged content
- **Cost Optimization**: Only translates new or modified resources
- **Secure API Key Management**: Supports multiple methods for providing DeepL API keys

## Installation

### Using the plugin from local build

1. Build and publish the plugin locally:

```bash
cd auto-translate-gradle-plugin
../gradlew publishToMavenLocal
```

2. Apply the plugin in your Flutter project's `build.gradle`:

```gradle
plugins {
    id 'de.haumacher.auto-translate-arb' version '1.0.0-SNAPSHOT'
}
```

### Including plugin in a multi-module build

Add to your `settings.gradle`:

```gradle
includeBuild('../path/to/web-translate')
```

## Configuration

### Basic Configuration

Configure the plugin in your `build.gradle`:

```gradle
translateArb {
    apiKey = System.getenv('DEEPL_API_KEY')
    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es']
}
```

### Secure Configuration (Recommended)

Store your API key in `~/.gradle/gradle.properties`:

```properties
deepl.apiKey=YOUR_DEEPL_API_KEY
```

Then in your `build.gradle`:

```gradle
translateArb {
    serverId = 'deepl'  // This is the default, can be omitted
    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es']
}
```

### Configuration Options

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `apiKey` | String | No | - | DeepL API key (overrides serverId if provided) |
| `serverId` | String | No | `"deepl"` | Server ID for retrieving API key from gradle.properties |
| `sourceFile` | File | Yes | - | Source ARB file to translate (must follow `basename_lang.arb` pattern) |
| `targetLangs` | List<String> | Yes | - | List of target language codes (e.g., `['de', 'fr', 'es']`) |

## Usage

### Running the Translation Task

Translate your ARB files:

```bash
./gradlew translateArb
```

### File Naming Convention

ARB files must follow the pattern: `basename_lang.arb`

Examples:
- `app_en.arb` (English source)
- `app_de.arb` (German target)
- `messages_en_US.arb` (English US with region)

The plugin automatically:
1. Extracts the source language from the filename
2. Creates target files with corresponding language codes
3. Preserves the directory structure

### Directory Structure Example

```
lib/
└── l10n/
    ├── app_en.arb       # Source file
    ├── app_de.arb       # Generated German translation
    ├── app_fr.arb       # Generated French translation
    └── app_es.arb       # Generated Spanish translation
```

## How It Works

### Translation Process

1. **Parse Source File**: Reads the source ARB file and extracts all resources
2. **Load Existing Translations**: Checks for existing target files to enable incremental translation
3. **Compute Checksums**: Calculates CRC32 checksums for source resources
4. **Identify Changes**: Determines which resources need translation:
   - New resources (don't exist in target)
   - Modified resources (checksum mismatch)
5. **Protect Parameters**: Preserves ICU MessageFormat parameters during translation
6. **Translate**: Sends texts to DeepL API in batches
7. **Restore Parameters**: Reconstructs original parameter syntax in translated text
8. **Write Target Files**: Creates/updates target ARB files with translations
9. **Update Checksums**: Stores checksums in source file for future incremental translations

### Parameter Protection

The plugin intelligently protects ICU MessageFormat syntax:

**Original:**
```json
"{count, plural, =1{1 message} other{{count} messages}}"
```

**Protected for DeepL:**
```
<x1>count, plural,</x1> <x2>=1</x2>{1 message} <x3>other</x3>{<x4>count</x4> messages}
```

**After Translation:**
```
<x1>count, plural,</x1> <x2>=1</x2>{1 Nachricht} <x3>other</x3>{<x4>count</x4> Nachrichten}
```

**Restored:**
```json
"{count, plural, =1{1 Nachricht} other{{count} Nachrichten}}"
```

## API Key Management

The plugin supports three methods for providing the DeepL API key (in order of precedence):

1. **Direct Configuration** (in build.gradle):
   ```gradle
   translateArb {
       apiKey = 'YOUR_KEY'
   }
   ```

2. **Gradle Properties** (in `gradle.properties` or `~/.gradle/gradle.properties`):
   ```properties
   deepl.apiKey=YOUR_KEY
   ```

3. **Environment Variable**:
   ```bash
   export DEEPL_API_KEY=YOUR_KEY
   ./gradlew translateArb
   ```

## Incremental Translation

The plugin uses CRC32 checksums stored in a custom `x-translated` attribute to track which resources have been translated. This provides several benefits:

- **Cost Savings**: Avoids re-translating unchanged content
- **Faster Builds**: Only translates modified resources
- **Consistency**: Preserves existing translations for unchanged text

### Checksum Storage Example

Source file with checksums:

```json
{
  "@@locale": "en",
  "greeting": "Hello {username}!",
  "@greeting": {
    "x-translated": "a1b2c3d4"
  }
}
```

When the source text changes, the checksum mismatch triggers re-translation to all target languages.

## Supported ICU MessageFormat Features

The plugin fully supports:

- **Simple placeholders**: `{name}`, `{count}`, `{value}`
- **Plural forms**: `{count, plural, =0{no items} one{# item} other{# items}}`
- **Select forms**: `{gender, select, male{he} female{she} other{they}}`
- **SelectOrdinal**: `{position, selectordinal, one{#st} two{#nd} few{#rd} other{#th}}`
- **Nested formats**: `{gender, select, male{He has {count, plural, one{# item} other{# items}}} other{...}}`

## Example Usage in Flutter

1. Configure the plugin in your `build.gradle`:

```gradle
plugins {
    id 'de.haumacher.auto-translate-arb' version '1.0.0-SNAPSHOT'
}

translateArb {
    serverId = 'deepl'
    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es', 'it', 'pt']
}
```

2. Create your source ARB file (`lib/l10n/app_en.arb`):

```json
{
  "@@locale": "en",
  "welcomeMessage": "Welcome, {username}!",
  "itemCount": "{count, plural, =0{No items} =1{One item} other{{count} items}}",
  "profileTitle": "{gender, select, male{His Profile} female{Her Profile} other{Their Profile}}"
}
```

3. Run the translation task:

```bash
./gradlew translateArb
```

4. The plugin will generate translated ARB files:
   - `lib/l10n/app_de.arb`
   - `lib/l10n/app_fr.arb`
   - `lib/l10n/app_es.arb`
   - etc.

## Building from Source

```bash
cd auto-translate-gradle-plugin
../gradlew build
```

## Publishing to Maven Local

```bash
cd auto-translate-gradle-plugin
../gradlew publishToMavenLocal
```

## License

Apache License, Version 2.0

## Author

Bernhard Haumacher (haui@haumacher.de)

## Related Projects

- **auto-translate** (Maven): Maven plugin for HTML and ARB translation
- **DeepL API**: https://www.deepl.com/docs-api

## Support

For issues and feature requests, please visit:
https://github.com/haumacher/auto-translate
