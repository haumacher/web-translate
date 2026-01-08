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
- Update Maven POM
- Run tests
- Create git tag
- Bump to next SNAPSHOT version

### 2. Push to GitHub

```bash
git push origin master
git push origin v1.0.3
```

### 3. Publish Maven Plugin

```bash
cd auto-translate-maven-plugin
mvn deploy -Prelease
cd ..
```

### 4. Publish Gradle Plugin

```bash
git checkout v1.0.3
cd auto-translate-gradle-plugin
../gradlew publishPlugins
cd ..
git checkout master
```

### 5. Create GitHub Release

Go to https://github.com/haumacher/auto-translate/releases/new:
- Select tag `v1.0.3`
- Add release notes
- Publish

### 6. Verify

- Maven Central: https://search.maven.org/
- Gradle Plugin Portal: https://plugins.gradle.org/

## Troubleshooting

**GPG signing fails:**
```bash
export GPG_TTY=$(tty)
```

**Maven 401 Unauthorized:**
Check `~/.m2/settings.xml` credentials

**Gradle publishPlugins fails:**
Check `~/.gradle/gradle.properties` credentials
