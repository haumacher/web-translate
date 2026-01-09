# Auto-Translate Project Architecture

## Overview

The auto-translate project is now structured as a **multi-module build** with clean separation of concerns:

```
web-translate/
├── auto-translate/                    # Core library
├── auto-translate-gradle-plugin/      # Gradle plugin
└── auto-translate-maven-plugin/       # Maven plugin
```

## Module Structure

### 1. auto-translate (Core Library)

**Purpose**: Core translation functionality for HTML and ARB files

**Dependencies**:
- `deepl-java:1.9.0` - DeepL API client
- `gson:2.10.1` - JSON parsing for ARB files

**Key Packages**:
- `de.haumacher.autotranslate.arb` - ARB translation
  - `io` - ARB file parsing/writing
  - `model` - ARB data model
- `de.haumacher.autotranslate.html` - HTML translation
  - `extract` - Extract translatable text
  - `translate` - DeepL API integration
  - `synthesize` - Inject translations back

**Build Output**:
- `auto-translate-1.1.0.jar` (75KB) - Pure library, no plugin code
- Published to Maven Local

**Build Tools**: Both Maven and Gradle
- Maven: `cd auto-translate && mvn install`
- Gradle: `./gradlew :auto-translate:build`

---

### 2. auto-translate-gradle-plugin

**Purpose**: Gradle plugin for ARB auto-translation

**Dependencies**:
- `auto-translate:1.1.0` - Core library (project dependency)
- `deepl-java:1.9.0` - DeepL API
- `gson:2.10.1` - JSON parsing

**Key Classes**:
- `TranslateArbPlugin` - Plugin entry point
- `TranslateArbTask` - Gradle task implementation
- `TranslateArbExtension` - Configuration DSL

**Build Output**:
- `auto-translate-gradle-plugin-1.1.0.jar` (6.3KB)
- Plugin ID: `de.haumacher.auto-translate-arb`
- Published to Maven Local

**Build Tool**: Gradle only
- `./gradlew :auto-translate-gradle-plugin:build`

**Usage**:
```gradle
plugins {
    id 'de.haumacher.auto-translate-arb' version '1.1.0'
}

translateArb {
    serverId = 'deepl'
    sourceFile = file('lib/l10n/app_en.arb')
    targetLangs = ['de', 'fr', 'es']
}
```

---

### 3. auto-translate-maven-plugin

**Purpose**: Maven plugin for HTML and ARB auto-translation

**Dependencies**:
- `auto-translate:1.1.0` - Core library
- `maven-plugin-api:3.9.6` - Maven plugin API (provided)
- `maven-plugin-annotations:3.11.0` - Annotations (provided)
- `maven-settings:3.9.6` - Settings access (provided)
- `deepl-java:1.9.0` - DeepL API

**Key Classes**:
- `TranslateMojo` - HTML translation goal
- `TranslateArbMojo` - ARB translation goal

**Build Output**:
- `auto-translate-maven-plugin-1.1.0.jar` (6.8KB)
- Goal prefix: `auto-translate`
- **Note**: This JAR built with Gradle contains only the Mojo classes
- For full Maven plugin, build with Maven: `cd auto-translate-maven-plugin && mvn install`

**Build Tools**: Both Maven and Gradle
- Maven (recommended): `cd auto-translate-maven-plugin && mvn install`
- Gradle: `./gradlew :auto-translate-maven-plugin:build` (creates JAR but not full plugin)

**Usage**:
```bash
mvn auto-translate:translate-arb \
  -Dtranslate.arb.sourceFile=app_en.arb \
  -Dtranslate.arb.targetLangs=de,fr,es
```

---

## Build System Integration

### Gradle Multi-Module Build

The project uses Gradle's multi-module build system:

**settings.gradle**:
```gradle
include 'auto-translate'
include 'auto-translate-gradle-plugin'
include 'auto-translate-maven-plugin'
```

**Build Commands**:
```bash
# Build everything
./gradlew build

# Build specific module
./gradlew :auto-translate:build

# Publish all to Maven Local
./gradlew publishToMavenLocal

# Clean all
./gradlew clean
```

### Maven Integration

The Maven plugin module has its own `pom.xml` and can be built independently:

```bash
cd auto-translate-maven-plugin
mvn clean install
```

This is the **recommended** way to build the Maven plugin for production use.

---

## Dependency Flow

```
auto-translate (core)
    ├─> auto-translate-gradle-plugin
    │   └─> Uses: ArbTranslator, HtmlFileTranslator
    └─> auto-translate-maven-plugin
        └─> Uses: ArbTranslator, HtmlFileTranslator
```

Both plugins depend on the core library but are independent of each other.

---

## Key Design Decisions

### 1. **Separation of Plugin Code**

**Before**: Maven Mojos were in the core `auto-translate` module
- Problem: Required Maven dependencies even for Gradle users
- Problem: Gradle build had to exclude Maven sources

**After**: Maven Mojos moved to `auto-translate-maven-plugin`
- ✅ Clean dependency separation
- ✅ Core library has no build-tool-specific code
- ✅ Each plugin includes only what it needs
- ✅ Parallel architecture for Maven and Gradle plugins

### 2. **compileOnly Dependencies**

Maven dependencies in the Maven plugin module use `provided` scope:
- Not included in transitive dependencies
- Available at compile time only
- Maven runtime provides them

### 3. **Multi-Build Support**

Each module can be built with either Maven or Gradle:
- **Core**: Both work equally well
- **Gradle Plugin**: Gradle only (uses `java-gradle-plugin`)
- **Maven Plugin**: Maven recommended (creates proper Maven plugin)

---

## Published Artifacts

All modules published to Maven Local (`~/.m2/repository/`):

```
de/haumacher/
├── auto-translate/1.1.0/
│   ├── auto-translate-1.1.0.jar
│   ├── auto-translate-1.1.0-sources.jar
│   └── auto-translate-1.1.0-javadoc.jar
├── auto-translate-gradle-plugin/1.1.0/
│   ├── auto-translate-gradle-plugin-1.1.0.jar
│   ├── auto-translate-gradle-plugin-1.1.0-sources.jar
│   └── auto-translate-gradle-plugin-1.1.0-javadoc.jar
└── (Maven plugin would be here after mvn install)
```

---

## File Sizes

| Module | JAR Size | Description |
|--------|----------|-------------|
| auto-translate | 75 KB | Core library only |
| auto-translate-gradle-plugin | 6.3 KB | Gradle plugin classes |
| auto-translate-maven-plugin | 6.8 KB | Maven Mojo classes |

**Total**: ~88 KB (excluding dependencies)

---

## Testing

Each module has its own test suite:

```bash
# Test all modules
./gradlew test

# Test specific module
./gradlew :auto-translate:test
./gradlew :auto-translate-gradle-plugin:test

# Maven plugin tests
cd auto-translate-maven-plugin && mvn test
```

---

## Summary

The refactored architecture provides:

✅ **Clean separation**: Core library, Gradle plugin, Maven plugin
✅ **No cross-contamination**: Maven code doesn't affect Gradle users
✅ **Consistent pattern**: Both plugins follow the same dependency model
✅ **Build flexibility**: Use Maven or Gradle as appropriate
✅ **Smaller artifacts**: Each JAR contains only what it needs
✅ **Better maintainability**: Changes to one plugin don't affect the other

This is a **professional multi-module architecture** following best practices for build tool plugin development.
