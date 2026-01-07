# How to Release the Auto-Translate Multi-Module Project

This guide describes the release process for the multi-module `auto-translate` project, which includes:
- `auto-translate` - Core library
- `auto-translate-maven-plugin` - Maven plugin
- `auto-translate-gradle-plugin` - Gradle plugin

**Quick Start:** Use the automated release scripts! See [RELEASE_SCRIPTS.md](RELEASE_SCRIPTS.md) for detailed script documentation.

## Prerequisites

See [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) for detailed setup of:
- Sonatype OSSRH account
- GPG key configuration
- Maven settings.xml

## Release Strategy

The project uses **coordinated releases** where all modules are versioned together:
- All modules share the same version number
- Releases are coordinated to ensure compatibility
- Each module can be released independently if needed

## Version Numbering

- **Core library**: `1.0.x` (stable API)
- **Maven plugin**: `1.0.x` (matches core)
- **Gradle plugin**: `1.0.x` (matches core)

Example release versions:
- `auto-translate-1.0.1`
- `auto-translate-maven-plugin-1.0.1`
- `auto-translate-gradle-plugin-1.0.1`

## Release Process

### One-Click Release (Easiest)

For the simplest release (auto-increments patch version):

```bash
./quick-release.sh
```

This automatically determines:
- Release version: Removes `-SNAPSHOT` from current version
- Next version: Auto-increments patch number

Example: `1.0.1-SNAPSHOT` → Release `1.0.1` → Next `1.0.2-SNAPSHOT`

### Custom Release

Use the full release script for custom versions:

```bash
./release.sh 1.0.1
```

This script will automatically:
- Check that working tree is clean
- Run all tests
- Update version numbers in all modules (Gradle and Maven)
- Update README examples
- Commit the release version
- Create a git tag
- Build and publish to Maven Local
- Update to next SNAPSHOT version
- Commit the next development version

After the script completes, you just need to:
1. Push to GitHub: `git push origin master && git push origin v1.0.1`
2. Publish Maven plugin: `cd auto-translate-maven-plugin && mvn deploy -Prelease`
3. Publish Gradle plugin: `git checkout v1.0.1 && cd auto-translate-gradle-plugin && ../gradlew publishPlugins`
4. Create GitHub release

**Custom next version:**
```bash
./release.sh 1.0.1 1.1.0-SNAPSHOT
```

### Manual Release Process

If you prefer manual control:

### Step 1: Prepare for Release

1. **Ensure working tree is clean:**
   ```bash
   git status
   # Should show "working tree clean"
   ```

2. **Run full build and tests:**
   ```bash
   ./gradlew clean build test
   cd auto-translate-maven-plugin && mvn clean test && cd ..
   ```

3. **Verify all modules build successfully:**
   ```bash
   ./gradlew build
   ```

### Step 2: Update Version Numbers

Update versions in all modules:

**For Gradle modules (auto-translate, auto-translate-gradle-plugin):**
```bash
# Update version in build.gradle files
# Change from: version = '1.0.1-SNAPSHOT'
# To:         version = '1.0.1'
```

**For Maven plugin:**
```bash
cd auto-translate-maven-plugin
mvn versions:set -DnewVersion=1.0.1
cd ..
```

**Files to update manually:**
- `auto-translate/build.gradle` - line 7
- `auto-translate-gradle-plugin/build.gradle` - line 7
- `auto-translate-maven-plugin/pom.xml` - via mvn versions:set
- `README.md` - update version numbers in examples

### Step 3: Commit Release Version

```bash
git add -A
git commit -m "Release version 1.0.1"
git tag -a v1.0.1 -m "Release version 1.0.1"
```

### Step 4: Build and Publish

**Publish Core Library and Gradle Plugin:**
```bash
./gradlew publishToMavenLocal
./gradlew publish  # If configured for publishing to Maven Central
```

**Publish Maven Plugin:**
```bash
cd auto-translate-maven-plugin
mvn clean deploy -Prelease
cd ..
```

**For Gradle Plugin Portal** (requires account at https://plugins.gradle.org/):
```bash
cd auto-translate-gradle-plugin
./gradlew publishPlugins
cd ..
```

### Step 5: Update to Next Development Version

**Gradle modules:**
```bash
# Update version in build.gradle files
# Change from: version = '1.0.1'
# To:         version = '1.0.2-SNAPSHOT'
```

**Maven plugin:**
```bash
cd auto-translate-maven-plugin
mvn versions:set -DnewVersion=1.0.2-SNAPSHOT
cd ..
```

**Commit:**
```bash
git add -A
git commit -m "Prepare for next development iteration (1.0.2-SNAPSHOT)"
```

### Step 6: Push to GitHub

```bash
git push origin master
git push origin v1.0.1
```

### Step 7: Create GitHub Release

1. Go to https://github.com/haumacher/auto-translate/releases
2. Click "Create a new release"
3. Select tag `v1.0.1`
4. Title: `v1.0.1`
5. Description:
   ```markdown
   ## Changes in this release

   - Feature 1
   - Feature 2
   - Bug fix 1

   ## Modules Released

   - `auto-translate-1.0.1` - Core library
   - `auto-translate-maven-plugin-1.0.1` - Maven plugin
   - `auto-translate-gradle-plugin-1.0.1` - Gradle plugin

   ## Installation

   **Maven:**
   ```xml
   <dependency>
     <groupId>de.haumacher</groupId>
     <artifactId>auto-translate-maven-plugin</artifactId>
     <version>1.0.1</version>
   </dependency>
   ```

   **Gradle:**
   ```gradle
   plugins {
     id 'de.haumacher.auto-translate-arb' version '1.0.1'
   }
   ```
   ```
6. Click "Publish release"

## Alternative: Maven Release Plugin (Maven Plugin Only)

For the Maven plugin module, you can use the automated Maven release plugin:

```bash
cd auto-translate-maven-plugin
mvn release:prepare release:perform
cd ..
```

**Note:** This only releases the Maven plugin module. You'll still need to manually release the Gradle modules.

## Publishing to Gradle Plugin Portal

To publish the Gradle plugin to https://plugins.gradle.org/:

1. **Create account** at https://plugins.gradle.org/
2. **Get API key** from your account settings
3. **Configure credentials** in `~/.gradle/gradle.properties`:
   ```properties
   gradle.publish.key=YOUR_API_KEY
   gradle.publish.secret=YOUR_API_SECRET
   ```

4. **Publish:**
   ```bash
   cd auto-translate-gradle-plugin
   ../gradlew publishPlugins
   ```

## SNAPSHOT Releases

For development snapshots:

**Gradle modules:**
```bash
./gradlew publishToMavenLocal
```

**Maven plugin:**
```bash
cd auto-translate-maven-plugin
mvn clean deploy
cd ..
```

Snapshots are deployed to:
- Gradle: Maven Local only (unless configured otherwise)
- Maven: https://s01.oss.sonatype.org/content/repositories/snapshots/

## Verification

After release, verify each module:

### 1. Core Library
```bash
# Check Maven Central
https://search.maven.org/artifact/de.haumacher/auto-translate/1.0.1/jar
```

### 2. Maven Plugin
```bash
# Check Maven Central
https://search.maven.org/artifact/de.haumacher/auto-translate-maven-plugin/1.0.1/maven-plugin

# Test in a project
mvn de.haumacher:auto-translate-maven-plugin:1.0.1:help
```

### 3. Gradle Plugin
```bash
# Check Gradle Plugin Portal
https://plugins.gradle.org/plugin/de.haumacher.auto-translate-arb

# Test in a project
gradle wrapper --gradle-version 8.5
echo "plugins { id 'de.haumacher.auto-translate-arb' version '1.0.1' }" > test.gradle
gradle -b test.gradle tasks
```

## Release Checklist

- [ ] All tests passing
- [ ] Documentation updated
- [ ] CHANGELOG updated
- [ ] Version numbers updated in all modules
- [ ] Version numbers updated in README examples
- [ ] Git tag created
- [ ] Core library published
- [ ] Maven plugin published to Maven Central
- [ ] Gradle plugin published to Gradle Plugin Portal
- [ ] GitHub release created
- [ ] Announcements sent (if applicable)

## Rollback

If you need to rollback a release:

1. **Delete the tag:**
   ```bash
   git tag -d v1.0.1
   git push origin :refs/tags/v1.0.1
   ```

2. **Revert version commits:**
   ```bash
   git revert HEAD~2..HEAD
   ```

3. **Contact Sonatype** if already published to Maven Central (releases cannot be deleted, but you can mark as deprecated)

## Troubleshooting

See [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) for detailed troubleshooting of:
- GPG signing issues
- Maven Central deployment issues
- Permission errors

## Module-Specific Notes

### Core Library (`auto-translate`)
- Can be built with either Maven or Gradle
- Maven: `cd auto-translate && mvn deploy -Prelease`
- Gradle: `./gradlew :auto-translate:publish`

### Maven Plugin (`auto-translate-maven-plugin`)
- **Must** be built with Maven for proper plugin packaging
- The `pom.xml` has packaging type `maven-plugin`
- Uses Maven plugin annotations

### Gradle Plugin (`auto-translate-gradle-plugin`)
- **Must** be built with Gradle
- Uses `java-gradle-plugin` for plugin packaging
- Auto-generates plugin descriptor

## Additional Resources

- [Maven Central Guide](https://central.sonatype.org/publish/publish-guide/)
- [Gradle Plugin Publishing](https://plugins.gradle.org/docs/publish-plugin)
- [Semantic Versioning](https://semver.org/)
- [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) - Detailed Maven release guide
