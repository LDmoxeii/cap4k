# cap4k Maven Central Tag-Driven Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `cap4k`'s Central publishing branch into a release-only, tag-driven Maven Central channel that derives artifact versions from `v*` tags and cannot publish remotely by accident in non-tag mode.

**Architecture:** The release channel keeps one small version resolver in `buildSrc`, then lets the precompiled Kotlin/JVM convention plugin consume that resolver for `group`, `version`, remote publish gating, and signing behavior. GitHub Actions becomes a tag-only release workflow that derives `RELEASE_VERSION` from `github.ref_name`, passes it into Gradle, and keeps the Central compatibility upload step after a successful publish.

**Tech Stack:** Gradle Kotlin DSL, `buildSrc` convention plugins, Kotlin test, GitHub Actions, JDK 17, Maven Publish, Gradle Signing, Sonatype Central compatibility API, PowerShell, GitHub CLI.

---

## File Map

- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\build.gradle.kts` - add a small test harness for `buildSrc`.
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\main\kotlin\buildsrc\convention\CentralReleaseVersion.kt` - centralize group id, baseline version, and release-version validation.
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\test\kotlin\buildsrc\convention\CentralReleaseVersionTest.kt` - protect the resolver with unit tests.
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts` - switch from fixed snapshot versioning to tag-driven release version injection and remote publish gating.
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\.github\workflows\maven-central-release.yml` - remove manual trigger, derive `RELEASE_VERSION`, and publish only on `v*` tags.
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\docs\superpowers\analysis\2026-05-14-cap4k-maven-central-release-verification.md` - capture the first real Central verification procedure and required secrets.

---

### Task 1: Add A Tested Release Version Resolver

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\main\kotlin\buildsrc\convention\CentralReleaseVersion.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\test\kotlin\buildsrc\convention\CentralReleaseVersionTest.kt`

- [ ] **Step 1: Enable `buildSrc` unit tests**

Replace `buildSrc/build.gradle.kts` with:

```kotlin
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing resolver tests**

Create `buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt`:

```kotlin
package buildsrc.convention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CentralReleaseVersionTest {

    @Test
    fun `resolve uses baseline version when release version is missing`() {
        assertEquals("0.5.0-dev", CentralReleaseVersion.resolve(null))
        assertEquals("0.5.0-dev", CentralReleaseVersion.resolve("   "))
    }

    @Test
    fun `resolve accepts plain release versions`() {
        assertEquals("0.5.0", CentralReleaseVersion.resolve("0.5.0"))
        assertEquals("1.2.3", CentralReleaseVersion.resolve(" 1.2.3 "))
    }

    @Test
    fun `resolve rejects snapshot release versions`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CentralReleaseVersion.resolve("0.5.0-SNAPSHOT")
        }
        assertEquals(
            "Snapshot versions are not allowed for Maven Central release: 0.5.0-SNAPSHOT",
            error.message
        )
    }

    @Test
    fun `resolve rejects malformed release versions`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CentralReleaseVersion.resolve("v0.5.0")
        }
        assertEquals(
            "Release version must come from a v<major>.<minor>.<patch> tag. Got: v0.5.0",
            error.message
        )
    }
}
```

- [ ] **Step 3: Run the new test target and confirm it fails**

Run:

```powershell
cd buildSrc
..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"
```

Expected: FAIL because `CentralReleaseVersion` does not exist yet.

- [ ] **Step 4: Implement the resolver**

Create `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`:

```kotlin
package buildsrc.convention

internal object CentralReleaseVersion {
    const val groupId = "io.github.ldmoxeii"
    const val baselineVersion = "0.5.0-dev"
    const val releaseVersionProperty = "release.version"
    const val releaseVersionEnvironment = "RELEASE_VERSION"

    private val releaseVersionPattern = Regex("""\d+\.\d+\.\d+""")

    fun resolve(releaseVersionInput: String?): String {
        val normalized = releaseVersionInput?.trim().orEmpty()
        return if (normalized.isEmpty()) {
            baselineVersion
        } else {
            validateReleaseVersion(normalized)
        }
    }

    fun isReleaseBuild(releaseVersionInput: String?): Boolean =
        releaseVersionInput?.isNotBlank() == true

    private fun validateReleaseVersion(releaseVersion: String): String {
        val normalized = releaseVersion.trim()
        require(releaseVersionPattern.matches(normalized)) {
            "Release version must come from a v<major>.<minor>.<patch> tag. Got: $releaseVersion"
        }
        require(!normalized.endsWith("-SNAPSHOT")) {
            "Snapshot versions are not allowed for Maven Central release: $releaseVersion"
        }
        return normalized
    }
}
```

- [ ] **Step 5: Re-run the resolver tests and confirm they pass**

Run:

```powershell
cd buildSrc
..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"
```

Expected: PASS with 4 tests, 0 failures.

- [ ] **Step 6: Commit the resolver foundation**

Run:

```powershell
git add buildSrc/build.gradle.kts buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt
git commit -m "test: cover central release version resolution"
```

---

### Task 2: Wire Tag-Driven Versioning Into The Convention Plugin

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts`

- [ ] **Step 1: Replace the fixed snapshot versioning with resolver-backed release gating**

Replace `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` with:

```kotlin
package buildsrc.convention

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.signing.Sign
import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    signing
}

val releaseVersionInput = providers.gradleProperty(CentralReleaseVersion.releaseVersionProperty).orNull
    ?: System.getenv(CentralReleaseVersion.releaseVersionEnvironment)
val centralUsername = providers.gradleProperty("central.username").orNull ?: System.getenv("CENTRAL_USERNAME")
val centralPassword = providers.gradleProperty("central.password").orNull ?: System.getenv("CENTRAL_PASSWORD")
val signingKey = providers.gradleProperty("signingKey").orNull ?: System.getenv("SIGNING_KEY")
val signingPassword = providers.gradleProperty("signingPassword").orNull ?: System.getenv("SIGNING_PASSWORD")
val isCentralRelease = CentralReleaseVersion.isReleaseBuild(releaseVersionInput)

group = CentralReleaseVersion.groupId
version = CentralReleaseVersion.resolve(releaseVersionInput)

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set("cap4k module ${project.name}")
            url.set("https://github.com/LDmoxeii/cap4k")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/LDmoxeii/cap4k/blob/master/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("LDmoxeii")
                    name.set("LDmoxeii")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/LDmoxeii/cap4k.git")
                developerConnection.set("scm:git:https://github.com/LDmoxeii/cap4k.git")
                url.set("https://github.com/LDmoxeii/cap4k")
            }
        }
    }
    repositories {
        maven {
            name = "CentralPortal"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = centralUsername
                password = centralPassword
            }
        }
    }
}

afterEvaluate {
    if (!pluginManager.hasPlugin("java-gradle-plugin")) {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
                }
            }
        }
    }

    signing {
        setRequired {
            isCentralRelease && gradle.taskGraph.allTasks.any { task ->
                task is PublishToMavenRepository && task.repository.name == "CentralPortal"
            }
        }
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications)
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("PluginMarkerMavenPublicationToCentralPortalRepository")) {
        enabled = false
        return@configureEach
    }

    if (repository.name == "CentralPortal") {
        onlyIf("CentralPortal publication requires RELEASE_VERSION from a v* tag") {
            isCentralRelease
        }
    }
}

tasks.withType<Sign>().configureEach {
    onlyIf { isCentralRelease }
    doFirst {
        check(!signingKey.isNullOrBlank()) {
            "Central release signing requires signingKey Gradle property or SIGNING_KEY environment variable."
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    enabled = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
    jvmArgs(
        "-Xmx2g",
        "-Xms512m",
        "-XX:MaxMetaspaceSize=512m",
    )
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
```

- [ ] **Step 2: Verify non-release local validation still works**

Run:

```powershell
.\gradlew.bat check publishToMavenLocal
```

Expected: PASS without `RELEASE_VERSION`, without Central credentials, and without GPG signing credentials.

- [ ] **Step 3: Verify remote Central publish tasks do not run in non-tag mode**

Run:

```powershell
.\gradlew.bat :ddd-core:publishMavenPublicationToCentralPortalRepository --console=plain
```

Expected: the task is `SKIPPED` because `RELEASE_VERSION` was not provided, and there is no remote upload attempt.

- [ ] **Step 4: Verify the release version can be injected locally without remote publication**

Run:

```powershell
Remove-Item "$env:USERPROFILE\.m2\repository\io\github\ldmoxeii\ddd-core\0.5.0" -Recurse -Force -ErrorAction SilentlyContinue
.\gradlew.bat :ddd-core:publishMavenPublicationToMavenLocal -Prelease.version=0.5.0
Test-Path "$env:USERPROFILE\.m2\repository\io\github\ldmoxeii\ddd-core\0.5.0\ddd-core-0.5.0.pom"
```

Expected: the final command prints `True`, proving the release version is taken from `-Prelease.version=0.5.0`.

- [ ] **Step 5: Commit the convention plugin integration**

Run:

```powershell
git add buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
git commit -m "build: derive central release version from tags"
```

---

### Task 3: Tighten The GitHub Actions Release Workflow

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\.github\workflows\maven-central-release.yml`

- [ ] **Step 1: Remove manual dispatch and derive `RELEASE_VERSION` from the tag**

Replace `.github/workflows/maven-central-release.yml` with:

```yaml
name: Maven Central Release

on:
  push:
    tags:
      - "v*"

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Derive release version
        shell: bash
        run: |
          TAG_NAME="${GITHUB_REF_NAME}"
          if [[ ! "${TAG_NAME}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            echo "Expected tag format v<major>.<minor>.<patch>, got ${TAG_NAME}" >&2
            exit 1
          fi
          echo "RELEASE_VERSION=${TAG_NAME#v}" >> "$GITHUB_ENV"

      - name: Check
        run: ./gradlew check

      - name: Publish
        run: ./gradlew publish -Prelease.version="${RELEASE_VERSION}"
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}

      - name: Upload staging repository to Central Portal
        run: |
          curl --fail --request POST \
            --user "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
            https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.ldmoxeii
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
```

- [ ] **Step 2: Verify the workflow is tag-only**

Run:

```powershell
rg -n "workflow_dispatch|RELEASE_VERSION|GITHUB_REF_NAME|push:" .github/workflows/maven-central-release.yml
```

Expected:

- no `workflow_dispatch` match
- one `GITHUB_REF_NAME` match
- one `RELEASE_VERSION` assignment match
- the workflow still triggers on `push.tags = v*`

- [ ] **Step 3: Commit the workflow tightening**

Run:

```powershell
git add .github/workflows/maven-central-release.yml
git commit -m "ci: restrict central release to tags"
```

---

### Task 4: Document The First Real Release Verification Path And Re-Verify The Branch

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central\docs\superpowers\analysis\2026-05-14-cap4k-maven-central-release-verification.md`

- [ ] **Step 1: Write the release verification note**

Create `docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md`:

````md
# cap4k Maven Central Release Verification

Date: 2026-05-14

## Required GitHub Repository Secrets

Configure these secrets on `LDmoxeii/cap4k` before the first real Central release:

- `CENTRAL_USERNAME`
- `CENTRAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

## Local Structural Verification

Run in `verify/maven-central`:

```powershell
cd buildSrc
..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"
cd ..
.\gradlew.bat check publishToMavenLocal
.\gradlew.bat :ddd-core:publishMavenPublicationToMavenLocal -Prelease.version=0.5.0
```

Expected:

- resolver tests pass
- full build passes
- local Maven repository contains `io/github/ldmoxeii/ddd-core/0.5.0`

## First Remote Central Verification

1. Merge `verify/maven-central` into `publish/maven-central`.
2. Push `publish/maven-central`:

```powershell
git push origin publish/maven-central
```

3. Create and push the first release tag from that branch:

```powershell
git tag v0.5.0
git push origin v0.5.0
```

4. Watch the workflow:

```powershell
gh run list --repo LDmoxeii/cap4k --workflow "Maven Central Release" --limit 5
gh run view <run-id> --repo LDmoxeii/cap4k --log-failed
```

5. Confirm the resulting artifact page exists:

```text
https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core
```

6. Confirm the GitHub Release exists:

```text
https://github.com/LDmoxeii/cap4k/releases/tag/v0.5.0
```

## Failure Triage

- If the run fails before `Publish`, inspect tag parsing.
- If `Publish` fails, inspect `CENTRAL_*` credentials and signing configuration.
- If the upload call fails after publish, inspect the workflow logs before retrying with another tag.
- Do not re-enable plugin marker publication as part of initial release triage.
````

- [ ] **Step 2: Run branch-wide verification**

Run:

```powershell
cd buildSrc
..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"
cd ..
.\gradlew.bat check publishToMavenLocal
git diff --check
```

Expected: all commands succeed with no diff hygiene errors.

- [ ] **Step 3: Commit the verification note**

Run:

```powershell
git add docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md
git commit -m "docs: add central release verification guide"
```

- [ ] **Step 4: Review the branch commit stack**

Run:

```powershell
git log --oneline --decorate -5
```

Expected: the top of the branch shows these new commits in order:

- `docs: add central release verification guide`
- `ci: restrict central release to tags`
- `build: derive central release version from tags`
- `test: cover central release version resolution`
