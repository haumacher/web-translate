# auto-translate - Automatic translation of HTML templates and ARB files using DeepL

The `auto-translate` project provides automated translation tools for:
- **HTML templates** (Thymeleaf or any HTML-based templates)
- **ARB files** (Application Resource Bundle for Flutter/Dart localization)

Available as both **Maven plugin** and **Gradle plugin**, using the [DeepL API](https://www.deepl.com/pro-api) to automatically translate content while preserving markup structure and supporting incremental updates.

## Project Structure

This is a multi-module project:

- **auto-translate** - Core translation library
- **auto-translate-maven-plugin** - Maven plugin for HTML and ARB translation
- **auto-translate-gradle-plugin** - Gradle plugin for ARB translation

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architectural information.

## HTML Translation

### Problem with Standard Approaches

When using the [Thymeleaf template engine](https://www.thymeleaf.org) for rendering pages, the official recommendation is to convert text to resource keys. This becomes cumbersome for complex content.

Example - original template `/WEB-INF/templates/chart.html`:
```html
<h1>Your shopping cart</h1>
<p>The following items are ready for checkout:</p>
```

Standard internationalization requires: `/WEB-INF/templates/chart.html`:
```html
<h1 th:text="#{chart.title}"></h1>
<p th:text="#{chart.heading}"></p>
```

With properties `/WEB-INF/templates/chart_en.properties`:
```properties
chart.title=Your shopping cart
chart.heading=The following items are ready for checkout:
```

### Problems with the Standard Approach

This works for simple cases but becomes unmanageable for text with embedded links, formatting, or complex structure:

```html
<p>To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```

You'd have to split into multiple keys:
```properties
instruction.1=To install PhoneBlock, you need a
instruction.2=FRITZ!Box Internet router from AVM
instruction.3=and a PhoneBlock account.
```

This produces:
- Hard-to-maintain templates
- Fragmented sentences that translate poorly
- Loss of context for translators

### Automatic Translation with auto-translate

With `auto-translate`, write templates in your native language without worrying about internationalization. The plugin:

1. Assigns translation IDs (`data-tx`) to HTML elements with translatable content
2. Extracts text while preserving markup structure
3. Translates using DeepL API
4. Generates locale-specific templates automatically
5. Supports incremental updates (only translates changed content)

### How It Works

Original template `/WEB-INF/templates/en/home.html`:
```html
<p>To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```

**Step 1:** The plugin assigns translation IDs with CRC checksums:
```html
<p data-tx="t0001:a1b2c3d4">To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```

**Step 2:** Text is extracted in-memory with markup converted to placeholders:
```
To install PhoneBlock, you need a <x1>FRITZ!Box Internet router from AVM</x1> and a PhoneBlock account.
```

The `<a>` tag becomes `<x1>` - preserving sentence structure while protecting technical markup.

**Step 3:** Text is translated via DeepL API to German:
```
Um PhoneBlock zu installieren, benötigst Du einen <x1>FRITZ!Box Internet-Router von AVM</x1> und einen PhoneBlock-Account.
```

**Step 4:** German template is generated at `/WEB-INF/templates/de/home.html`:
```html
<p data-tx="t0001:a1b2c3d4">Um PhoneBlock zu installieren, benötigst Du einen <a th:href="@{/link/fritzbox}">FRITZ!Box Internet-Router von AVM</a> und einen PhoneBlock-Account.</p>
```

The `<x1>` placeholder is replaced with the original `<a>` tag, preserving all technical attributes.

**All processing happens in-memory - no intermediate properties files are created.**

### Incremental Translation

The plugin uses CRC checksums to detect changes:

- **Unchanged text**: Reuses existing translations (saves API costs)
- **New content**: Automatically translates
- **Modified content**: Detects via CRC mismatch and re-translates
- **No CRC**: Treats as unchanged (backward compatibility)

### HTML Translation Configuration (Maven)

Directory structure:
```
templates/
  en/              # Source language templates
    index.html
    about.html
  de/              # Generated German templates
  fr/              # Generated French templates
```

Maven plugin configuration:
```xml
<plugin>
  <groupId>de.haumacher</groupId>
  <artifactId>auto-translate-maven-plugin</artifactId>
  <version>1.1.0</version>
  <executions>
    <execution>
      <goals>
        <goal>translate</goal>
      </goals>
      <configuration>
        <!-- API key retrieved from server "deepl" in settings.xml -->
        <sourceLang>en</sourceLang>
        <targetLangs>de,fr,es</targetLangs>
        <templateDirectory>${project.basedir}/templates</templateDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Don't forget to configure the DeepL API key in `~/.m2/settings.xml`:
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

## ARB Translation

Both plugins support ARB (Application Resource Bundle) files used in Flutter/Dart applications.

### Features

- **Incremental translation**: Only translates new or modified resources
- **Parameter protection**: Preserves `{username}`, `{count}`, etc.
- **ICU MessageFormat support**: Handles plurals, select, gender, nested formats
- **Description as translation context**: The `description` of a resource is passed to DeepL as
  [context](https://developers.deepl.com/docs/learning-how-tos/examples-and-guides/how-to-use-context-parameter)
- **Compact output**: Target files contain only translations (no metadata)

### Translation Context

Short messages are often ambiguous: "Open" can be the label of a button (a verb) or the state of a
ticket (an adjective) - and the two translate differently. If a resource has a `description`, it is
sent to DeepL as *context*:

```json
{
  "@@locale": "en",
  "openAction": "Open",
  "@openAction": {
    "description": "Label of the button that opens the selected document."
  },
  "openState": "Open",
  "@openState": {
    "description": "State of a ticket that has not been closed yet."
  }
}
```

The context is not translated and is not billed by DeepL, it only disambiguates the translation.
Since the context applies to a whole request, texts are grouped by their description and one request
is sent per distinct description. Resources without a description are translated together in a
single request without context, exactly as before.

Note that changing only the `description` of a resource does not re-trigger a translation: The
`x-translated` checksum tracks the message text. To pick up a new description for an already
translated message, remove its `x-translated` attribute.

### Example Translation

Source file `app_en.arb`:
```json
{
  "@@locale": "en",
  "greeting": "Hello {username}!",
  "messages": "{count, plural, =0{no messages} =1{1 message} other{{count} messages}}"
}
```

Generated `app_de.arb`:
```json
{
  "@@locale": "de",
  "greeting": "Hallo {username}!",
  "messages": "{count, plural, =0{keine Nachrichten} =1{1 Nachricht} other{{count} Nachrichten}}"
}
```

Parameters and format identifiers are preserved, only the actual text is translated.

### Configuration

**Maven (pom.xml):**

```xml
<plugin>
  <groupId>de.haumacher</groupId>
  <artifactId>auto-translate-maven-plugin</artifactId>
  <version>1.1.0</version>
  <executions>
    <execution>
      <goals>
        <goal>translate-arb</goal>
      </goals>
      <configuration>
        <sourceFile>${project.basedir}/lib/l10n/app_en.arb</sourceFile>
        <targetLangs>de,fr,es</targetLangs>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Run with: `mvn auto-translate:translate-arb`

**Gradle (build.gradle):**

```gradle
plugins {
    id 'de.haumacher.auto-translate-arb' version '1.1.0'
}

translateArb {
    serverId = 'deepl'  // API key from gradle.properties
    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es']
}
```

Run with: `./gradlew translateArb`

See [auto-translate-gradle-plugin/QUICK_START.md](auto-translate-gradle-plugin/QUICK_START.md) for a detailed Gradle tutorial.

## Building from Source

### Build All Modules (Gradle)

```bash
./gradlew build
```

### Build Individual Modules

**Core library:**
```bash
./gradlew :auto-translate:build
```

**Gradle plugin:**
```bash
./gradlew :auto-translate-gradle-plugin:build
```

**Maven plugin:**
```bash
cd auto-translate-maven-plugin
mvn clean install
```

### Publish to Maven Local

```bash
./gradlew publishToMavenLocal
```

For the Maven plugin:
```bash
cd auto-translate-maven-plugin
mvn install
```

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Multi-module project architecture
- [RELEASE.md](RELEASE.md) - How to release using Gradle Release Plugin
- [CLAUDE.md](CLAUDE.md) - Development guide for Claude Code
- [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) - Detailed Maven Central release guide
- [auto-translate-gradle-plugin/README.md](auto-translate-gradle-plugin/README.md) - Gradle plugin documentation
- [auto-translate-gradle-plugin/QUICK_START.md](auto-translate-gradle-plugin/QUICK_START.md) - Gradle quick start guide

## Module Overview

| Module | Description | Build Tool | Artifact |
|--------|-------------|------------|----------|
| auto-translate | Core translation library | Maven/Gradle | `auto-translate-1.1.0.jar` |
| auto-translate-maven-plugin | Maven plugin for HTML & ARB | Maven | `auto-translate-maven-plugin-1.1.0.jar` |
| auto-translate-gradle-plugin | Gradle plugin for ARB | Gradle | `auto-translate-gradle-plugin-1.1.0.jar` |

## License

Apache License 2.0
