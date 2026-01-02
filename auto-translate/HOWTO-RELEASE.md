# How to Release to Maven Central

This guide describes the steps required to deploy the `auto-translate` Maven plugin to Maven Central.

## Prerequisites

### 1. Sonatype OSSRH Account

1. Create a Sonatype JIRA account at https://issues.sonatype.org/
2. Create a JIRA ticket to claim the `de.haumacher` group ID
   - Project: Community Support - Open Source Project Repository Hosting (OSSRH)
   - Issue Type: New Project
   - Group Id: `de.haumacher`
   - Project URL: `https://github.com/haumacher/auto-translate`
   - SCM URL: `https://github.com/haumacher/auto-translate.git`
3. Wait for approval (usually takes 1-2 business days)
4. Note your Sonatype username and password for later configuration

### 2. GPG Key Setup

1. **Generate a GPG key** (if you don't have one):
   ```bash
   gpg --gen-key
   ```
   - Use your real name and email address
   - Choose a strong passphrase

2. **List your GPG keys** to find the key ID:
   ```bash
   gpg --list-keys
   ```
   Example output:
   ```
   pub   rsa3072 2024-01-02 [SC] [expires: 2026-01-02]
         401FE46033F5589B53443B0CBD126C2FB1395E37
   uid           [ultimate] Bernhard Haumacher <...>
   ```
   The key ID is the long hex string (e.g., `401FE46033F5589B53443B0CBD126C2FB1395E37`)

3. **Publish your public key** to a key server:
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

   You can also publish to other popular keyservers:
   ```bash
   gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
   gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID
   ```

4. **Export your key** (optional, for backup):
   ```bash
   gpg --export-secret-keys YOUR_KEY_ID > private-key.asc
   gpg --export YOUR_KEY_ID > public-key.asc
   ```

### 3. Maven Settings Configuration

Create or edit `~/.m2/settings.xml` with your Sonatype credentials:

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

**Security Note:** For better security, you can encrypt passwords:
```bash
mvn --encrypt-master-password
mvn --encrypt-password
```
See: https://maven.apache.org/guides/mini/guide-encryption.html

## Release Process

The project uses the Maven Release Plugin to automate version management, tagging, and deployment.

### Maven Release Plugin Configuration

The `pom.xml` is configured with the following release settings:

- **Tag format:** `v@{project.version}` (e.g., `v1.0.0`)
- **Release profile:** Automatically activates the `release` profile for GPG signing
- **Auto-version submodules:** Enabled (though not applicable for single-module project)
- **Goals:** Executes `deploy` goal to push artifacts to Maven Central

### Option A: Automated Release (Recommended)

The `maven-release-plugin` handles everything automatically:

1. **Ensure all changes are committed:**
   ```bash
   git status
   # Should show "working tree clean"
   ```

2. **Perform the release:**
   ```bash
   mvn release:prepare release:perform
   ```

   This single command will:
   - Remove `-SNAPSHOT` from the version
   - Build and test the project
   - Commit the release version
   - Create a Git tag (e.g., `v1.0.0`)
   - Bump version to next SNAPSHOT (e.g., `1.0.1-SNAPSHOT`)
   - Commit the new SNAPSHOT version
   - Checkout the release tag
   - Build, sign (with GPG), and deploy to Maven Central
   - Automatically release to Maven Central

3. **Enter information when prompted:**
   - Release version (default: removes `-SNAPSHOT`)
   - SCM tag name (default: `v1.0.0`)
   - Next development version (default: increments patch version)
   - GPG passphrase

4. **Push the changes:**
   ```bash
   git push origin master
   git push origin --tags
   ```

5. **Wait for synchronization** to Maven Central (usually 10-30 minutes)
   - Check status at: https://s01.oss.sonatype.org/
   - Artifacts appear on Maven Central: https://search.maven.org/

6. **Create GitHub Release** (optional):
   - Go to https://github.com/haumacher/auto-translate/releases
   - Click "Create a new release"
   - Select the tag (e.g., `v1.0.0`)
   - Add release notes describing changes

### Option B: Manual Release (Advanced)

If you prefer manual control over the release process:

1. **Prepare the release:**
   ```bash
   mvn release:prepare
   ```
   This updates versions and creates the tag without deploying.

2. **Perform the release:**
   ```bash
   mvn release:perform
   ```
   This checks out the tag and deploys to Maven Central.

3. **If something goes wrong, rollback:**
   ```bash
   mvn release:rollback
   ```
   This reverts version changes (but doesn't remove tags).

### Option C: Completely Manual Release

If you need complete manual control:

1. **Update version to release version:**
   ```bash
   mvn versions:set -DnewVersion=1.0.0
   ```

2. **Commit and tag:**
   ```bash
   git add pom.xml
   git commit -m "Release version 1.0.0"
   git tag -a v1.0.0 -m "Release version 1.0.0"
   ```

3. **Deploy:**
   ```bash
   mvn clean deploy -Prelease
   ```

4. **Update to next SNAPSHOT:**
   ```bash
   mvn versions:set -DnewVersion=1.0.1-SNAPSHOT
   git add pom.xml
   git commit -m "Prepare for next development iteration"
   ```

5. **Push:**
   ```bash
   git push origin master
   git push origin v1.0.0
   ```

## Deploying SNAPSHOT Versions

For development snapshots (versions ending with `-SNAPSHOT`):

```bash
mvn clean deploy
```

Note: No `-Prelease` needed for snapshots - GPG signing is optional.

Snapshots are deployed to:
- https://s01.oss.sonatype.org/content/repositories/snapshots/

## Troubleshooting

### GPG Issues

**"gpg: signing failed: Inappropriate ioctl for device"**
```bash
export GPG_TTY=$(tty)
```

**"gpg: cannot open '/dev/tty': No such device or address"**

Add to `~/.gnupg/gpg.conf`:
```
use-agent
pinentry-mode loopback
```

Or specify in Maven command:
```bash
mvn clean deploy -Prelease -Dgpg.passphrase=YOUR_PASSPHRASE
```

### Deployment Issues

**"401 Unauthorized"**
- Check your `~/.m2/settings.xml` credentials
- Verify your Sonatype account is active

**"403 Forbidden"**
- Ensure your JIRA ticket for group ID claim was approved
- Verify you're authorized to deploy to `de.haumacher` group

**"Staging repository is already closed"**
- The `autoReleaseAfterClose` is set to `true`
- If you need manual control, set it to `false` in `pom.xml`

### Manual Release (if autoReleaseAfterClose=false)

1. Login to https://s01.oss.sonatype.org/
2. Click "Staging Repositories" on the left
3. Find your staging repository (usually at the bottom)
4. Click "Close" and wait for validation
5. If validation passes, click "Release"
6. Artifacts will sync to Maven Central in 10-30 minutes

## Verification

After release, verify the deployment:

1. **Check Maven Central:**
   ```
   https://search.maven.org/artifact/de.haumacher/auto-translate/1.0.0/maven-plugin
   ```

2. **Test the plugin in a sample project:**
   ```xml
   <plugin>
     <groupId>de.haumacher</groupId>
     <artifactId>auto-translate</artifactId>
     <version>1.0.0</version>
   </plugin>
   ```

3. **Wait for Maven Central indexing** (can take up to 2 hours for search to update)

## Additional Resources

- Maven Central Guide: https://central.sonatype.org/publish/publish-guide/
- GPG Guide: https://central.sonatype.org/publish/requirements/gpg/
- Maven Settings Encryption: https://maven.apache.org/guides/mini/guide-encryption.html
- Sonatype OSSRH Guide: https://central.sonatype.org/publish/publish-maven/
