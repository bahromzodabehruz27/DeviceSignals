# Publishing an Android Library to Maven Central

A complete step-by-step guide for publishing an Android AAR library to Maven Central using the Sonatype Central Portal and the vanniktech maven-publish Gradle plugin.

---

## Table of Contents

1. [Create a Sonatype Account](#step-1-create-a-sonatype-account)
2. [Verify Your Namespace](#step-2-verify-your-namespace)
3. [Generate a GPG Key](#step-3-generate-a-gpg-key)
4. [Upload GPG Key to Keyserver](#step-4-upload-gpg-key-to-keyserver)
5. [Generate Sonatype API Token](#step-5-generate-sonatype-api-token)
6. [Add the Gradle Plugin](#step-6-add-the-gradle-plugin)
7. [Configure build.gradle.kts](#step-7-configure-buildgradlekts)
8. [Configure gradle.properties](#step-8-configure-gradleproperties)
9. [Test Locally First](#step-9-test-locally-first)
10. [Publish to Maven Central](#step-10-publish-to-maven-central)
11. [Verify Publication](#step-11-verify-publication)
12. [Releasing New Versions](#releasing-new-versions)
13. [CI/CD with GitHub Actions](#cicd-with-github-actions)
14. [Troubleshooting](#troubleshooting)

---

## Step 1: Create a Sonatype Account

1. Go to https://central.sonatype.com
2. Click **Sign In** (top right)
3. Sign in with your **GitHub** account (recommended — simplifies namespace verification)
4. Complete the registration

> **Important:** Remember which sign-in method you used. Different sign-in methods (GitHub, Google, email) create separate accounts even with the same email.

---

## Step 2: Verify Your Namespace

Maven Central requires you to prove ownership of the group ID (namespace) you publish under.

### Option A: GitHub-based namespace (easiest)

If you signed in with GitHub, use `io.github.<your-github-username>`:

1. Go to https://central.sonatype.com → **Publishing Settings** → **Namespace**
2. Click **Register New Namespace**
3. Enter `io.github.<your-github-username>` (e.g., `io.github.bahromzodabehruz27`)
4. Sonatype will ask you to create a **temporary verification repository** on GitHub
5. Create the repo with the exact name they specify
6. Click **Verify** — status should change to ✅ **Verified**
7. You can delete the temporary repo after verification

### Option B: Custom domain namespace

If you own a domain (e.g., `example.com`):

1. Register namespace `com.example`
2. Add a DNS TXT record as specified by Sonatype
3. Wait for verification (may take a few hours)

---

## Step 3: Generate a GPG Key

Maven Central requires all artifacts to be signed with a GPG/PGP key.

### 3.1 Install GPG (if not installed)

```bash
# macOS
brew install gnupg

# Ubuntu/Debian
sudo apt install gnupg

# Windows
# Download from https://gnupg.org/download/
```

### 3.2 Generate the key

```bash
gpg --full-generate-key
```

When prompted:
- **Key type:** `(1) RSA and RSA`
- **Key size:** `4096`
- **Expiration:** `0` (does not expire) — or set an expiry if you prefer
- **Real name:** Your name
- **Email:** Your email
- **Passphrase:** Choose a strong passphrase — **you'll need this later**

### 3.3 Find your key ID

```bash
gpg --list-keys --keyid-format short
```

Output:

```
pub   rsa4096/37897B36 2026-09-22 [SC]
      674E7AFF379C813D3AE53C2AE8BA0F0D37897B36
uid         [ultimate] Your Name <your@email.com>
sub   rsa4096/5D816C79 2026-09-22 [E]
```

Your **key ID** is the 8-character string after `rsa4096/` — in this example: `37897B36`

---

## Step 4: Upload GPG Key to Keyserver

Maven Central verifies signatures against public keyservers. Upload yours:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Example:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys 37897B36
```

Verify it was uploaded:

```bash
gpg --keyserver keyserver.ubuntu.com --search-keys your@email.com
```

---

## Step 5: Generate Sonatype API Token

You need an API token (not your password) for publishing:

1. Go to https://central.sonatype.com
2. Click your avatar (top right) → **View Account**
3. Click **Generate User Token**
4. Copy both the **username** and **password** — these are your Maven Central credentials

> **Save these securely.** You cannot retrieve the password again — only regenerate a new token.

---

## Step 6: Add the Gradle Plugin

Add the vanniktech maven-publish plugin to your version catalog.

**`gradle/libs.versions.toml`:**

```toml
[plugins]
central-publishing = { id = "com.vanniktech.maven.publish", version = "0.30.0" }
```

> **Why vanniktech?** The Sonatype Central Portal does not support direct Maven `PUT` uploads. This plugin handles the staging → release workflow automatically.

---

## Step 7: Configure build.gradle.kts

In your library module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.central.publishing)
}

android {
    namespace = "your.library.namespace"
    compileSdk { version = release(37) }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    // ... your existing android config
}

dependencies {
    // ... your dependencies
}

mavenPublishing {
    coordinates(
        groupId = property("SDK_GROUP_ID").toString(),
        artifactId = property("SDK_ARTIFACT_ID").toString(),
        version = property("SDK_VERSION").toString(),
    )

    pom {
        name.set("Your Library Name")
        description.set("Description of your library")
        url.set("https://github.com/your-username/your-repo")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("your-username")
                name.set("Your Name")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/your-username/your-repo.git")
            developerConnection.set("scm:git:ssh://github.com/your-username/your-repo.git")
            url.set("https://github.com/your-username/your-repo")
        }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
```

> **Note:** You no longer need `maven-publish` or `signing` plugins — vanniktech handles both.

---

## Step 8: Configure gradle.properties

Add the following to your project's `gradle.properties`:

```properties
# SDK Maven coordinates
SDK_GROUP_ID=io.github.your-username
SDK_ARTIFACT_ID=your-library-name
SDK_VERSION=0.1.0

# Maven Central credentials (from Step 5)
mavenCentralUsername=your-token-username
mavenCentralPassword=your-token-password

# PGP signing (key ID from Step 3, passphrase from key generation)
signing.keyId=YOUR_KEY_ID
signing.password=your-gpg-passphrase
```

### Security: What NOT to commit

Add to `.gitignore`:

```
# Never commit credentials
gradle.properties
```

Or better: keep non-secret properties in `gradle.properties` (committed) and put secrets in `~/.gradle/gradle.properties` (your home directory, never committed):

```properties
# ~/.gradle/gradle.properties (global, not committed)
mavenCentralUsername=your-token-username
mavenCentralPassword=your-token-password
signing.keyId=YOUR_KEY_ID
signing.password=your-gpg-passphrase
```

---

## Step 9: Test Locally First

Before publishing to Maven Central, verify everything builds correctly:

```bash
./gradlew :sdk:publishToMavenLocal
```

This publishes to `~/.m2/repository/` — no credentials or signing needed.

Check the output:

```bash
ls ~/.m2/repository/io/github/your-username/your-library-name/0.1.0/
```

You should see:
- `your-library-name-0.1.0.aar` — the library
- `your-library-name-0.1.0-sources.jar` — source code
- `your-library-name-0.1.0-javadoc.jar` — documentation
- `your-library-name-0.1.0.pom` — Maven metadata
- `your-library-name-0.1.0.module` — Gradle metadata

---

## Step 10: Publish to Maven Central

Run the following commands. Replace `YOUR_KEY_ID` and `your-gpg-passphrase` with your actual values:

```bash
# Export GPG key as environment variable
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="YOUR_KEY_ID"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="your-gpg-passphrase"
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --batch --yes \
  --pinentry-mode loopback \
  --passphrase 'your-gpg-passphrase' \
  --armor --export-secret-keys YOUR_KEY_ID)"

# Publish and release
./gradlew :sdk:publishAndReleaseToMavenCentral --no-configuration-cache
```

### What happens during publish:

```
Step 1: Build           → AAR, sources JAR, javadoc JAR
Step 2: Sign            → GPG signs all artifacts
Step 3: Create staging  → Opens a staging repo on Central Portal
Step 4: Upload          → Uploads all signed artifacts
Step 5: Release         → Closes and releases the staging repo
```

### Expected output:

```
> Task :sdk:createStagingRepository
> Task :sdk:signMavenPublication
> Task :sdk:publishMavenPublicationToMavenCentralRepository
> Task :sdk:releaseRepository
> Task :sdk:publishAndReleaseToMavenCentral

BUILD SUCCESSFUL
```

---

## Step 11: Verify Publication

After a successful publish, check these URLs:

| What | URL | Availability |
|------|-----|-------------|
| Central Portal | https://central.sonatype.com/artifact/io.github.your-username/your-library-name | Immediate |
| Maven Repo | https://repo1.maven.org/maven2/io/github/your-username/your-library-name/ | 10-30 minutes |
| Search | https://search.maven.org/search?q=g:io.github.your-username | Up to 4 hours |

### Test in a consumer project

Once synced, add to any Android project's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.your-username:your-library-name:0.1.0")
}
```

---

## Releasing New Versions

1. **Bump version** in `gradle.properties`:
   ```properties
   SDK_VERSION=0.4.1
   ```

2. **Publish** (same command as Step 10):
   ```bash
   ./gradlew :sdk:publishAndReleaseToMavenCentral --no-configuration-cache
   ```

3. **Tag the release** in git:
   ```bash
   git tag v0.4.1
   git push origin v0.4.1
   ```

> **Important:** Maven Central does not allow overwriting a released version. Always bump the version number.

---

## CI/CD with GitHub Actions

Automate publishing on git tag push.

### 1. Add GitHub Secrets

Go to your repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret Name | Value |
|-------------|-------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype token password |
| `SIGNING_KEY_ID` | GPG key ID (8 characters) |
| `SIGNING_KEY` | Full GPG private key (see below) |
| `SIGNING_KEY_PASSWORD` | GPG passphrase |

To get the full GPG key for the secret:

```bash
gpg --batch --yes --pinentry-mode loopback \
  --passphrase 'your-gpg-passphrase' \
  --armor --export-secret-keys YOUR_KEY_ID
```

Copy the **entire output** including `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----`.

### 2. Create Workflow

**`.github/workflows/publish.yml`:**

```yaml
name: Publish to Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Publish to Maven Central
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyId: ${{ secrets.SIGNING_KEY_ID }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: ./gradlew :sdk:publishAndReleaseToMavenCentral --no-configuration-cache
```

### 3. Trigger a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow will automatically publish to Maven Central when you push a version tag.

---

## Troubleshooting

### Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `Namespace 'x' is not allowed` | Namespace not verified on Central Portal | Go to https://central.sonatype.com → Publishing Settings → Namespace → Verify |
| `402 Payment Required` | Using old `s01.oss.sonatype.org` endpoint | Use `SonatypeHost.CENTRAL_PORTAL` in build config |
| `No configured signatory` | GPG key not available to Gradle | Set `ORG_GRADLE_PROJECT_signingInMemoryKey` env var |
| `404 Not Found` on publish | Wrong credentials or repo URL | Verify `mavenCentralUsername` and `mavenCentralPassword` |
| `Version already exists` | Can't overwrite a released artifact | Bump `SDK_VERSION` in `gradle.properties` |
| `Unable to retrieve secret key` | `signing.secretKeyRingFile` points to missing file | Use in-memory signing (env vars) instead of file-based |
| `Cannot convert '' to File` | Empty `signing.secretKeyRingFile` property | Remove the property or use in-memory signing |

### GPG Tips

```bash
# List all your keys
gpg --list-secret-keys --keyid-format short

# Test key export (should print the key)
gpg --batch --yes --pinentry-mode loopback \
  --passphrase 'your-passphrase' \
  --armor --export-secret-keys YOUR_KEY_ID

# Delete a key (if you need to start over)
gpg --delete-secret-and-public-key YOUR_KEY_FINGERPRINT
```

### Gradle Tips

```bash
# See all available publishing tasks
./gradlew tasks --group=publishing

# Publish without releasing (stays in staging)
./gradlew :sdk:publishToMavenCentral --no-configuration-cache

# Publish and auto-release
./gradlew :sdk:publishAndReleaseToMavenCentral --no-configuration-cache

# Clean build before publishing
./gradlew clean :sdk:publishAndReleaseToMavenCentral --no-configuration-cache
```

---

## Summary Checklist

- [ ] Created Sonatype account at https://central.sonatype.com
- [ ] Verified namespace (e.g., `io.github.your-username`)
- [ ] Generated GPG key (`gpg --full-generate-key`)
- [ ] Uploaded GPG key to keyserver
- [ ] Generated Sonatype API token
- [ ] Added vanniktech plugin to `libs.versions.toml`
- [ ] Configured `mavenPublishing {}` block in `build.gradle.kts`
- [ ] Added credentials to `gradle.properties` (or `~/.gradle/gradle.properties`)
- [ ] Tested with `publishToMavenLocal`
- [ ] Published with `publishAndReleaseToMavenCentral`
- [ ] Verified on https://central.sonatype.com
- [ ] (Optional) Set up GitHub Actions for automated releases
