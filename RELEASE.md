# Release Guide

## Prerequisites

### Sonatype OSSRH Account

1. Create account at https://issues.sonatype.org/
2. Create JIRA ticket to claim `de.haumacher` group ID
3. Note credentials for Maven settings.xml

### GPG Key Setup

```bash
# Generate key
gpg --gen-key

# Get key ID
gpg --list-keys

# Publish to keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### Maven Settings (~/.m2/settings.xml)

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
      </properties>
    </profile>
  </profiles>
</settings>
```

### Gradle Plugin Portal

Configure `~/.gradle/gradle.properties`:
```properties
gradle.publish.key=YOUR_API_KEY
gradle.publish.secret=YOUR_API_SECRET
```

Get credentials from https://plugins.gradle.org/

## Release Process

### 1. Prepare Release

```bash
# Ensure clean working directory
git status

# Run release (interactive)
./gradlew release
```

This will:
- Remove `-SNAPSHOT` from version
- Update Maven POMs
- Run tests
- Create git tag (e.g., `$VERSION`)
- Bump to next SNAPSHOT version

### 2. Push to GitHub

```bash
git push origin master
git push origin $VERSION
```

### 3. Publish Maven Artifacts to Maven Central

Deploy both core library and Maven plugin together:

```bash
mvn deploy -Prelease
```

This will deploy both modules in the correct order (core library first, then plugin).

### 4. Publish Gradle Plugin to Gradle Plugin Portal

```bash
git checkout $VERSION
./gradlew :auto-translate-gradle-plugin:publishPlugins
./gradlew publishToMavenLocal 
git checkout master
```

### 5. Create GitHub Release

Go to https://github.com/haumacher/auto-translate/releases/new:
- Select tag `$VERSION`
- Add release notes
- Publish

### 6. Verify

- Core library: https://search.maven.org/artifact/de.haumacher/auto-translate
- Maven plugin: https://search.maven.org/artifact/de.haumacher/auto-translate-maven-plugin
- Gradle plugin: https://plugins.gradle.org/plugin/de.haumacher.auto-translate-arb

## Troubleshooting

**GPG signing fails:**
```bash
export GPG_TTY=$(tty)
```

**Maven 401 Unauthorized:**
Check `~/.m2/settings.xml` credentials

**Gradle publishPlugins fails:**
Check `~/.gradle/gradle.properties` credentials
