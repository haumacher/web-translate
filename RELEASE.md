# Release Guide

This guide describes the automated release process using the **Gradle Release Plugin** ([net.researchgate.release](https://github.com/researchgate/gradle-release)).

## Overview

The project uses the Gradle release plugin to automate:
- Version management (removing/adding SNAPSHOT)
- Git tagging
- Maven POM synchronization
- Commit creation

## Prerequisites

See [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) for detailed setup of:
- Sonatype OSSRH account
- GPG key configuration
- Maven settings.xml

## Quick Release

### One-Command Release

The simplest way to release:

```bash
./gradlew release
```

This will:
1. Check that your working directory is clean
2. Run all tests
3. Prompt you for the release version (suggests removing `-SNAPSHOT`)
4. Prompt you for the next development version (suggests incrementing patch)
5. Update all version files (including Maven POM)
6. Build all modules
7. Commit and tag the release
8. Update to next SNAPSHOT version
9. Commit the next version

**Example interaction:**
```
Current version: 1.0.3-SNAPSHOT
Release version [1.0.3]:
Next version [1.0.4-SNAPSHOT]:
```

### Non-Interactive Release

For CI/CD or scripted releases:

```bash
./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=1.0.3 -Prelease.newVersion=1.0.4-SNAPSHOT
```

## After Release

After the `release` task completes successfully, you need to:

### 1. Push to GitHub

```bash
git push origin master
git push origin v1.0.3
```

### 2. Publish Maven Plugin to Maven Central

```bash
cd auto-translate-maven-plugin
mvn deploy -Prelease
cd ..
```

### 3. Publish Gradle Plugin to Gradle Plugin Portal

```bash
git checkout v1.0.3
cd auto-translate-gradle-plugin
../gradlew publishPlugins
cd ..
git checkout master
```

### 4. Create GitHub Release

Go to https://github.com/haumacher/auto-translate/releases/new and:
- Select tag `v1.0.3`
- Add release notes
- Publish release

## Advanced Usage

### Skip Tests

**Not recommended**, but if needed:

```bash
./gradlew release -x test
```

### Custom Version Property File

The version is stored in `gradle.properties` at the root. To check the current version:

```bash
./gradlew properties | grep "^version:"
```

### Update Maven POM Manually

If you need to sync the Maven POM version manually:

```bash
./gradlew updateMavenVersion
```

This task is automatically run during the release process.

### Preview What Release Will Do

To see what the release plugin will do without making changes:

```bash
./gradlew tasks --group=release
```

Key tasks in the release workflow:
- `checkCommitNeeded` - Verifies working tree is clean
- `checkSnapshotDependencies` - Checks for SNAPSHOT dependencies
- `unSnapshotVersion` - Removes `-SNAPSHOT` from version
- `confirmReleaseVersion` - Prompts for release version
- `createReleaseTag` - Creates git tag
- `updateVersion` - Prompts for next version
- `commitNewVersion` - Commits next development version

## Version Management

### Version Storage

All version information is centralized in:
- **`gradle.properties`** - Single source of truth for version
- **Gradle modules** - Read version from `gradle.properties`
- **Maven POM** - Updated automatically by `updateMavenVersion` task

### Version Format

- **Development**: `X.Y.Z-SNAPSHOT` (e.g., `1.0.3-SNAPSHOT`)
- **Release**: `X.Y.Z` (e.g., `1.0.3`)

### Semantic Versioning

Follow [semantic versioning](https://semver.org/):
- **Patch** (X.Y.Z): Bug fixes, no API changes
- **Minor** (X.Y.0): New features, backward compatible
- **Major** (X.0.0): Breaking changes

## Configuration

The release plugin is configured in the root `build.gradle`:

```groovy
release {
    git {
        requireBranch.set('master')
        pushToRemote.set('origin')
        signTag.set(false)
    }

    versionPropertyFile = 'gradle.properties'

    preTagCommitMessage = 'Release version'
    tagCommitMessage = 'Release version'
    newVersionCommitMessage = 'Prepare for next development iteration'

    pushReleaseVersionBranch.set('master')
}
```

## SNAPSHOT Releases

For development snapshots (no release needed):

```bash
# Local testing
./gradlew publishToMavenLocal

# Deploy SNAPSHOT to Sonatype
cd auto-translate-maven-plugin
mvn clean deploy
cd ..
```

## Troubleshooting

### "Working directory is not clean"

Commit or stash your changes:
```bash
git status
git stash
./gradlew release
```

### "Task 'release' not found"

Make sure you're in the root directory and the release plugin is configured in `build.gradle`:
```bash
./gradlew tasks --group=release
```

### Maven POM version out of sync

Run the sync task manually:
```bash
./gradlew updateMavenVersion
git add auto-translate-maven-plugin/pom.xml
git commit -m "Sync Maven POM version"
```

### Failed to push tags

The plugin doesn't auto-push by default. Push manually:
```bash
git push origin master
git push origin v1.0.3
```

## Release Checklist

- [ ] All tests passing (`./gradlew test`)
- [ ] Working directory clean (`git status`)
- [ ] Documentation updated
- [ ] Run `./gradlew release`
- [ ] Push to GitHub (`git push origin master && git push origin vX.Y.Z`)
- [ ] Publish Maven plugin (`cd auto-translate-maven-plugin && mvn deploy -Prelease`)
- [ ] Publish Gradle plugin (`cd auto-translate-gradle-plugin && ../gradlew publishPlugins`)
- [ ] Create GitHub release
- [ ] Verify on Maven Central (https://search.maven.org/)
- [ ] Verify on Gradle Plugin Portal (https://plugins.gradle.org/)

## Additional Resources

- [net.researchgate.release plugin](https://github.com/researchgate/gradle-release)
- [Maven Central Guide](https://central.sonatype.org/publish/publish-guide/)
- [Gradle Plugin Publishing](https://plugins.gradle.org/docs/publish-plugin)
- [Semantic Versioning](https://semver.org/)
- [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) - Detailed Maven setup
