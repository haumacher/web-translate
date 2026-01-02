# auto-translate - Automatic translation of HTML templates and ARB files using DeepL

The `auto-translate` Maven plugin provides automated translation for:
- **HTML templates** (Thymeleaf or any HTML-based templates)
- **ARB files** (Application Resource Bundle for Flutter/Dart localization)

It uses the [DeepL API](https://www.deepl.com/pro-api) to automatically translate content while preserving markup structure and supporting incremental updates.

## Quick Start

Add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>de.haumacher</groupId>
  <artifactId>auto-translate</artifactId>
  <version>1.0.0</version>
  <configuration>
    <apiKey>${env.DEEPL_API_KEY}</apiKey>
    <sourceLang>en</sourceLang>
    <targetLangs>de,fr,es</targetLangs>
  </configuration>
</plugin>
```

Run translation:
```bash
mvn auto-translate:translate
```

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

### Example Process

Original template `/WEB-INF/templates/en/home.html`:
```html
<p>To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```

**Step 1:** The plugin assigns translation IDs with CRC checksums:
```html
<p data-tx="t0001:a1b2c3d4">To install PhoneBlock, you need a <a th:href="@{/link/fritzbox}">FRITZ!Box Internet router from AVM</a> and a PhoneBlock account.</p>
```

**Step 2:** Text is extracted (in-memory, no intermediate files) with markup converted to placeholders:
```
t0001=To install PhoneBlock, you need a <x1>FRITZ!Box Internet router from AVM</x1> and a PhoneBlock account.
```

The `<a>` tag becomes `<x1>` - preserving sentence structure while protecting technical markup.

**Step 3:** Text is translated via DeepL API to German:
```
t0001=Um PhoneBlock zu installieren, benötigst Du einen <x1>FRITZ!Box Internet-Router von AVM</x1> und einen PhoneBlock-Account.
```

**Step 4:** German template is generated at `/WEB-INF/templates/de/home.html`:
```html
<p data-tx="t0001:a1b2c3d4">Um PhoneBlock zu installieren, benötigst Du einen <a th:href="@{/link/fritzbox}">FRITZ!Box Internet-Router von AVM</a> und einen PhoneBlock-Account.</p>
```

The `<x1>` placeholder is replaced with the original `<a>` tag, preserving all technical attributes.

### Incremental Translation

The plugin uses CRC checksums to detect changes:

- **Unchanged text**: Reuses existing translations (saves API costs)
- **New content**: Automatically translates
- **Modified content**: Detects via CRC mismatch and re-translates
- **No CRC**: Treats as unchanged (backward compatibility)

### Configuration

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
  <artifactId>auto-translate</artifactId>
  <version>1.0.0</version>
  <configuration>
    <apiKey>${env.DEEPL_API_KEY}</apiKey>
    <sourceLang>en</sourceLang>
    <targetLangs>de,fr,es</targetLangs>
    <templateDirectory>${project.basedir}/templates</templateDirectory>
  </configuration>
</plugin>
```

## ARB Translation

The plugin also supports ARB (Application Resource Bundle) files used in Flutter/Dart applications.

### Features

- **Incremental translation**: Only translates new or modified resources
- **Parameter protection**: Preserves `{username}`, `{count}`, etc.
- **ICU MessageFormat support**: Handles plurals, select, gender, nested formats
- **Compact output**: Target files contain only translations (no metadata)

### Example

Source file `app_en.arb`:
```json
{
  "@@locale": "en",
  "greeting": "Hello {username}!",
  "messages": "{count, plural, =0{no messages} =1{1 message} other{{count} messages}}"
}
```

Run translation:
```bash
java -jar auto-translate.jar YOUR_API_KEY app_en.arb de,fr
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

## Building from Source

```bash
cd auto-translate
mvn clean install
```

## Documentation

- [HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) - Guide for releasing to Maven Central
- [CLAUDE.md](CLAUDE.md) - Development guide for Claude Code

## License

Apache License 2.0 
