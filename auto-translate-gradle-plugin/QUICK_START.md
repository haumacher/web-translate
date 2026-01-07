# Quick Start Guide - ARB Auto-Translation Gradle Plugin

This guide will help you get started with the ARB Auto-Translation Gradle plugin in just a few minutes.

## Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher
- A DeepL API key (get one at https://www.deepl.com/pro-api)

## Step 1: Build and Install the Plugin Locally

Since the plugin is not yet published to a public repository, you need to build and install it locally:

```bash
cd /home/bhu/git/web-translate
./gradlew publishToMavenLocal
```

This will build both the `auto-translate` library and the `auto-translate-gradle-plugin` and install them to your local Maven repository (~/.m2/repository).

## Step 2: Configure Your Flutter/Dart Project

### Add the Plugin to Your Build

In your Flutter project's `build.gradle` (or create one if it doesn't exist):

```gradle
plugins {
    id 'de.haumacher.auto-translate-arb' version '1.0.0-SNAPSHOT'
}

repositories {
    mavenLocal()
    mavenCentral()
}

translateArb {
    // Option 1: Use environment variable (recommended for CI/CD)
    // Set DEEPL_API_KEY environment variable before running

    // Option 2: Use gradle.properties (recommended for local development)
    serverId = 'deepl'  // Will read from deepl.apiKey in gradle.properties

    // Option 3: Direct configuration (not recommended - avoid committing API keys)
    // apiKey = 'YOUR_DEEPL_API_KEY'

    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es']
}
```

### Set Up Your API Key

**Recommended: Use gradle.properties**

Create or edit `~/.gradle/gradle.properties` (for global configuration):

```properties
deepl.apiKey=YOUR_DEEPL_API_KEY
```

Or create `gradle.properties` in your project root (for project-specific configuration):

```properties
deepl.apiKey=YOUR_DEEPL_API_KEY
```

**Alternative: Use Environment Variable**

```bash
export DEEPL_API_KEY=YOUR_DEEPL_API_KEY
```

## Step 3: Create Your Source ARB File

Create a source ARB file at `lib/l10n/app_en.arb`:

```json
{
  "@@locale": "en",
  "welcomeMessage": "Welcome to my app!",
  "greeting": "Hello, {username}!",
  "itemCount": "{count, plural, =0{No items} =1{One item} other{{count} items}}",
  "profileTitle": "{gender, select, male{His Profile} female{Her Profile} other{Their Profile}}"
}
```

## Step 4: Run the Translation

Execute the Gradle task:

```bash
./gradlew translateArb
```

You should see output like:

```
========================================
Starting ARB translation
========================================
Source file: /path/to/lib/l10n/app_en.arb
Source language: en
Target languages: [de, fr, es]

Parsed source ARB: 4 resources

Translating to: de
Reusing 0 existing translations
Translating 4 resources...
Collected 8 text fragments to translate
Billed characters: 156
Written to: /path/to/lib/l10n/app_de.arb

Translating to: fr
...

========================================
ARB translation completed successfully!
Total billed characters: 468
========================================
```

## Step 5: Verify the Output

Check that the translated files have been created:

```bash
ls -l lib/l10n/
```

You should see:
- `app_en.arb` (source)
- `app_de.arb` (German)
- `app_fr.arb` (French)
- `app_es.arb` (Spanish)

## Step 6: Incremental Updates

When you modify your source ARB file, only the changed resources will be re-translated:

1. Edit `lib/l10n/app_en.arb`:
   ```json
   {
     "@@locale": "en",
     "welcomeMessage": "Welcome to our amazing app!",  // Changed
     "greeting": "Hello, {username}!",  // Unchanged
     ...
   }
   ```

2. Run translation again:
   ```bash
   ./gradlew translateArb
   ```

3. Only the modified `welcomeMessage` will be re-translated, saving API costs!

## Common Use Cases

### Multi-Project Build (Composite Build)

If you're developing the plugin and want to test it in another project:

In your Flutter project's `settings.gradle`:

```gradle
includeBuild('/home/bhu/git/web-translate')
```

Then you can use the plugin without publishing to Maven Local.

### CI/CD Integration

In your CI/CD pipeline (e.g., GitHub Actions):

```yaml
- name: Translate ARB files
  env:
    DEEPL_API_KEY: ${{ secrets.DEEPL_API_KEY }}
  run: ./gradlew translateArb
```

### Custom Language Pairs

Translate from different source languages:

```gradle
translateArb {
    sourceFile = file('lib/l10n/app_de.arb')  // German source
    targetLangs = ['en', 'fr', 'it']           // Translate to English, French, Italian
}
```

## Troubleshooting

### Plugin not found

**Error**: `Plugin [id: 'de.haumacher.auto-translate-arb'] was not found`

**Solution**: Make sure you've run `./gradlew publishToMavenLocal` and added `mavenLocal()` to your repositories.

### API key not found

**Error**: `DeepL API key not found`

**Solution**: Verify that your API key is set in one of the three supported locations:
1. `gradle.properties` (as `deepl.apiKey=...`)
2. Environment variable `DEEPL_API_KEY`
3. Direct configuration in `build.gradle`

### Source file not found

**Error**: `Source file not found: ...`

**Solution**: Verify the path to your source ARB file. Use `file()` function for relative paths:
```gradle
sourceFile = file('lib/l10n/app_en.arb')
```

### Invalid filename format

**Error**: `Cannot determine source language from filename`

**Solution**: Ensure your ARB file follows the naming convention: `basename_lang.arb`
- ✓ `app_en.arb`
- ✓ `messages_de_DE.arb`
- ✗ `app.arb`
- ✗ `app_english.arb`

## Next Steps

- Read the [full README](README.md) for advanced configuration options
- Explore ICU MessageFormat syntax: https://unicode-org.github.io/icu/userguide/format_parse/messages/
- Check DeepL API usage and limits: https://www.deepl.com/pro-api

## Support

For issues and questions:
- GitHub Issues: https://github.com/haumacher/auto-translate/issues
- Email: haui@haumacher.de
