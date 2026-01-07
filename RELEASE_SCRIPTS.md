# Release Scripts Guide

This project includes automated release scripts to simplify the multi-module release process.

## Overview

Two release scripts are available:

1. **`quick-release.sh`** - One-click release (recommended for most releases)
2. **`release.sh`** - Full control over version numbers

## Quick Release (One-Click)

### Usage

```bash
./quick-release.sh
```

### What It Does

Automatically:
- Reads current version from `build.gradle` (e.g., `1.0.1-SNAPSHOT`)
- Removes `-SNAPSHOT` for release version (e.g., `1.0.1`)
- Auto-increments patch number for next version (e.g., `1.0.2-SNAPSHOT`)
- Runs full release process

### Example Output

```
🚀 Quick Release

Current version:  1.0.1-SNAPSHOT
Release version:  1.0.1
Next version:     1.0.2-SNAPSHOT

This will:
  ✓ Run all tests
  ✓ Update all version numbers
  ✓ Create git tag v1.0.1
  ✓ Prepare for next development iteration

Continue? [y/N]
```

### When to Use

- ✅ Regular patch releases (1.0.1 → 1.0.2)
- ✅ Bug fixes
- ✅ Minor updates
- ✅ Most releases

### When NOT to Use

- ❌ Minor version bumps (1.0.x → 1.1.0)
- ❌ Major version bumps (1.x.x → 2.0.0)
- ❌ Custom version schemes

## Full Release Script

### Usage

```bash
./release.sh <release-version> [next-snapshot-version]
```

### Examples

**Specify both versions:**
```bash
./release.sh 1.1.0 1.1.1-SNAPSHOT
```

**Auto-increment next version:**
```bash
./release.sh 1.1.0
# Next version will be 1.1.1-SNAPSHOT
```

### What It Does

1. ✓ Checks working tree is clean
2. ✓ Runs all tests (`./gradlew clean test`)
3. ✓ Updates version in:
   - `auto-translate/build.gradle`
   - `auto-translate-gradle-plugin/build.gradle`
   - `auto-translate-maven-plugin/pom.xml`
   - `README.md` (example version numbers)
4. ✓ Commits release version
5. ✓ Creates git tag (`v1.0.1`)
6. ✓ Builds all modules
7. ✓ Publishes to Maven Local
8. ✓ Updates to next SNAPSHOT version
9. ✓ Commits next development version

### When to Use

- ✅ Minor version bumps (1.0.x → 1.1.0)
- ✅ Major version bumps (1.x.x → 2.0.0)
- ✅ Custom versioning schemes
- ✅ Pre-release versions (1.0.0-RC1)

## After Script Completes

Both scripts prepare the release locally. You still need to:

### 1. Push to GitHub

```bash
git push origin master
git push origin v1.0.1
```

### 2. Publish Maven Plugin

```bash
cd auto-translate-maven-plugin
mvn deploy -Prelease
cd ..
```

This publishes to Maven Central.

### 3. Publish Gradle Plugin (Optional)

```bash
git checkout v1.0.1
cd auto-translate-gradle-plugin
../gradlew publishPlugins
```

This publishes to Gradle Plugin Portal.

### 4. Create GitHub Release

Go to: https://github.com/haumacher/auto-translate/releases/new?tag=v1.0.1

## What Gets Updated

The scripts update versions in these files:

| File | Type | Method |
|------|------|--------|
| `auto-translate/build.gradle` | Gradle | sed regex |
| `auto-translate-gradle-plugin/build.gradle` | Gradle | sed regex |
| `auto-translate-maven-plugin/pom.xml` | Maven | `mvn versions:set` |
| `README.md` | Documentation | sed regex |

## Rollback

If something goes wrong after running the script but before pushing:

```bash
# Reset to before release commits
git reset --hard HEAD~2

# Delete the tag
git tag -d v1.0.1
```

If you've already pushed, see [RELEASE.md](RELEASE.md#rollback) for rollback procedures.

## Dry Run

To see what would happen without making changes:

```bash
# Show what quick-release would do
./quick-release.sh
# Answer 'n' when prompted

# Show current version
grep "^version = " auto-translate/build.gradle
```

## Script Compatibility

- **Linux**: Fully supported
- **macOS**: Fully supported (uses `sed -i ''` syntax)
- **Windows**: Use Git Bash or WSL

## Troubleshooting

### "Working directory is not clean"

Commit or stash your changes:
```bash
git status
git add .
git commit -m "Prepare for release"
```

### "Tests failed"

Fix failing tests before releasing:
```bash
./gradlew test
```

### "Permission denied"

Make scripts executable:
```bash
chmod +x release.sh quick-release.sh
```

### Version numbers don't match after script

This shouldn't happen, but if it does:
```bash
# Check all versions
grep "^version = " auto-translate/build.gradle
grep "^version = " auto-translate-gradle-plugin/build.gradle
grep "<version>" auto-translate-maven-plugin/pom.xml | head -1
```

## Manual Override

If you need to manually update versions:

**Gradle modules:**
```bash
# Edit these files
auto-translate/build.gradle
auto-translate-gradle-plugin/build.gradle
# Change: version = '1.0.1-SNAPSHOT'
```

**Maven plugin:**
```bash
cd auto-translate-maven-plugin
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT
```

## Best Practices

1. **Always run on clean working tree**
   - Commit all changes first
   - Don't release with uncommitted work

2. **Test before releasing**
   - Scripts run tests automatically
   - But test manually first for confidence

3. **Use quick-release for patches**
   - Fastest and least error-prone
   - Handles 90% of releases

4. **Use release.sh for version jumps**
   - Minor version bumps (1.0 → 1.1)
   - Major version bumps (1.x → 2.0)

5. **Review before pushing**
   - Check the commits: `git log --oneline -3`
   - Check the tag: `git show v1.0.1`

6. **Push atomically**
   - Push commits and tags together
   - Prevents version mismatch

## See Also

- [RELEASE.md](RELEASE.md) - Full release documentation
- [auto-translate/HOWTO-RELEASE.md](auto-translate/HOWTO-RELEASE.md) - Maven Central setup
- [ARCHITECTURE.md](ARCHITECTURE.md) - Project architecture
