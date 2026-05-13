# GitHub Release Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize public GitHub release hygiene for `cap4k`, `only-engine`, and `cap4k-reference-content-studio`, while keeping remote Maven publishing out of main branches.

**Architecture:** Main branches receive only contributor-friendly assets: MIT license, README badges, CI, and GitHub Release creation. Maven Central and Aliyun remote publishing are isolated into `cap4k` publishing channel branches, so normal clones do not need Central or GPG credentials. `cap4k-reference-content-studio` CI builds against a freshly published local `cap4k` snapshot to match its documented workflow.

**Tech Stack:** GitHub Actions, Gradle Kotlin DSL, JDK 17, Maven Publish, Gradle signing, Sonatype Central Portal, MIT License, DeepWiki badge.

---

## File Map

### `cap4k`

- Create: `LICENSE` - standard MIT license.
- Modify: `README.md` - add CI, Maven Central, GitHub Release, license, and DeepWiki badges.
- Modify: `README.zh-CN.md` - add the same badge block for the Chinese README.
- Create: `.github/workflows/ci.yml` - main branch CI, no publish credentials.
- Create: `.github/workflows/release.yml` - tag-triggered GitHub Release creation only, no Maven publish on main.
- Modify on `publish/maven-central`: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` - Maven Central coordinates, POM metadata, signing, Central repository.
- Create on `publish/maven-central`: `.github/workflows/maven-central-release.yml` - tag-triggered Central publish and GitHub Release.
- Preserve on `publish/aliyun-private`: Aliyun private publish repository configuration from the current `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.

### `only-engine`

- Create: `LICENSE` - standard MIT license.
- Modify: `README.md` - add CI, GitHub Release, license, and DeepWiki badges.
- Create: `.github/workflows/ci.yml` - main branch CI.
- Create: `.github/workflows/release.yml` - tag-triggered GitHub Release creation only.
- Modify: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` - remove remote Aliyun repository publishing from main if the repository should not expose remote publish configuration. Keep `publishToMavenLocal` behavior.

### `cap4k-reference-content-studio`

- Create: `LICENSE` - standard MIT license.
- Modify: `README.md` - add CI, GitHub Release, license, and DeepWiki badges.
- Modify: `README.zh-CN.md` - add the same badge block.
- Create: `.github/workflows/ci.yml` - checkout `cap4k`, publish it to `mavenLocal()`, then run this repo's checks.
- Create: `.github/workflows/release.yml` - repeat CI prerequisites, then create GitHub Release only.

---

### Task 1: Add Main-Branch License And README Badges

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\LICENSE`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\README.zh-CN.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\LICENSE`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\README.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\LICENSE`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\README.zh-CN.md`

- [ ] **Step 1: Add MIT license to each repository**

Write this exact content to `cap4k/LICENSE`, `only-engine/LICENSE`, and `cap4k-reference-content-studio/LICENSE`:

```text
MIT License

Copyright (c) 2026 LDmoxeii

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: Add badge block to `cap4k/README.md` and `cap4k/README.zh-CN.md`**

Insert immediately after the `# cap4k` heading:

```md
[![CI](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml/badge.svg)](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ldmoxeii/ddd-core)](https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core)
[![GitHub Release](https://img.shields.io/github/v/release/LDmoxeii/cap4k)](https://github.com/LDmoxeii/cap4k/releases)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/LDmoxeii/cap4k/blob/master/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/LDmoxeii/cap4k)
```

- [ ] **Step 3: Add badge block to `only-engine/README.md`**

Insert immediately after the `# only-engine` heading:

```md
[![CI](https://github.com/LDmoxeii/only-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/LDmoxeii/only-engine/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/LDmoxeii/only-engine)](https://github.com/LDmoxeii/only-engine/releases)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/LDmoxeii/only-engine/blob/master/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/LDmoxeii/only-engine)
```

- [ ] **Step 4: Add badge block to both reference project READMEs**

Insert immediately after the `# cap4k-reference-content-studio` heading:

```md
[![CI](https://github.com/LDmoxeii/cap4k-reference-content-studio/actions/workflows/ci.yml/badge.svg)](https://github.com/LDmoxeii/cap4k-reference-content-studio/actions/workflows/ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/LDmoxeii/cap4k-reference-content-studio)](https://github.com/LDmoxeii/cap4k-reference-content-studio/releases)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/LDmoxeii/cap4k-reference-content-studio/blob/master/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/LDmoxeii/cap4k-reference-content-studio)
```

- [ ] **Step 5: Verify the README headings and license files**

Run:

```powershell
rg -n "Ask DeepWiki|Maven Central|GitHub license|Copyright \\(c\\) 2026 LDmoxeii" C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio
```

Expected: Each README contains the correct badge labels, and each repository contains one matching copyright line in `LICENSE`.

- [ ] **Step 6: Commit each repository separately**

Run in `cap4k`:

```powershell
git add LICENSE README.md README.zh-CN.md
git commit -m "docs: add release badges and license"
```

Run in `only-engine`:

```powershell
git add LICENSE README.md
git commit -m "docs: add release badges and license"
```

Run in `cap4k-reference-content-studio`:

```powershell
git add LICENSE README.md README.zh-CN.md
git commit -m "docs: add release badges and license"
```

---

### Task 2: Add Main-Branch CI Workflows

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.github\workflows\ci.yml`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\.github\workflows\ci.yml`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\.github\workflows\ci.yml`

- [ ] **Step 1: Create `cap4k/.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
  push:
    branches:
      - master
  workflow_dispatch:

permissions:
  contents: read

jobs:
  check:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Check
        run: ./gradlew check
```

- [ ] **Step 2: Create `only-engine/.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
  push:
    branches:
      - master
  workflow_dispatch:

permissions:
  contents: read

jobs:
  check:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Check
        run: ./gradlew check
```

- [ ] **Step 3: Create `cap4k-reference-content-studio/.github/workflows/ci.yml`**

This workflow checks out `cap4k` into a sibling directory, publishes its snapshots to `mavenLocal()`, then checks the reference project.

```yaml
name: CI

on:
  pull_request:
  push:
    branches:
      - master
  workflow_dispatch:

permissions:
  contents: read

jobs:
  check:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout reference project
        uses: actions/checkout@v4
        with:
          path: cap4k-reference-content-studio

      - name: Checkout cap4k
        uses: actions/checkout@v4
        with:
          repository: LDmoxeii/cap4k
          path: cap4k

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Publish cap4k snapshots to Maven local
        working-directory: cap4k
        run: ./gradlew publishToMavenLocal

      - name: Check reference project
        working-directory: cap4k-reference-content-studio
        run: ./gradlew check
```

- [ ] **Step 4: Verify workflow YAML exists**

Run:

```powershell
Test-Path C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.github\workflows\ci.yml
Test-Path C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\.github\workflows\ci.yml
Test-Path C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\.github\workflows\ci.yml
```

Expected: all three commands print `True`.

- [ ] **Step 5: Commit each repository separately**

Run in each repository:

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: add gradle checks"
```

---

### Task 3: Add Main-Branch GitHub Release Workflows Without Package Publishing

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.github\workflows\release.yml`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\.github\workflows\release.yml`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\.github\workflows\release.yml`

- [ ] **Step 1: Create `cap4k/.github/workflows/release.yml`**

```yaml
name: GitHub Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Check
        run: ./gradlew check

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/')
        with:
          generate_release_notes: true
```

- [ ] **Step 2: Create `only-engine/.github/workflows/release.yml`**

```yaml
name: GitHub Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Check
        run: ./gradlew check

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/')
        with:
          generate_release_notes: true
```

- [ ] **Step 3: Create `cap4k-reference-content-studio/.github/workflows/release.yml`**

```yaml
name: GitHub Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout reference project
        uses: actions/checkout@v4
        with:
          path: cap4k-reference-content-studio

      - name: Checkout cap4k
        uses: actions/checkout@v4
        with:
          repository: LDmoxeii/cap4k
          path: cap4k

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Publish cap4k snapshots to Maven local
        working-directory: cap4k
        run: ./gradlew publishToMavenLocal

      - name: Check reference project
        working-directory: cap4k-reference-content-studio
        run: ./gradlew check

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/')
        with:
          generate_release_notes: true
```

- [ ] **Step 4: Verify release workflows do not publish packages**

Run:

```powershell
rg -n "publish|CENTRAL|SIGNING|AliYun|aliyun" C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.github\workflows\release.yml C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\.github\workflows\release.yml C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\.github\workflows\release.yml
```

Expected: the only matches are the reference project's `Publish cap4k snapshots to Maven local` step and its `publishToMavenLocal` command.

- [ ] **Step 5: Commit each repository separately**

Run in each repository:

```powershell
git add .github/workflows/release.yml
git commit -m "ci: add github release workflow"
```

---

### Task 4: Remove Remote Publish Configuration From Main Branches

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts`

- [ ] **Step 1: In `cap4k`, remove the remote `publishing.repositories` block from main**

Remove this repository block from the convention plugin on main:

```kotlin
publishing {
    repositories {
        maven {
            name = "AliYunMaven"
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
                password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
            }
        }
    }
}
```

Keep the `maven-publish` plugin and the publication creation so `publishToMavenLocal` remains available.

- [ ] **Step 2: In `only-engine`, remove the remote `publishing.repositories` block from main**

Remove this repository block from the convention plugin on main:

```kotlin
repositories {
    maven {
        name = "AliYunMaven"
        url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/only-engine")
        credentials {
            username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
            password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
        }
    }
}
```

Keep the `publications` block and `sourcesJar` task so local publication still works.

- [ ] **Step 3: Verify no Aliyun remote publish configuration remains on main**

Run:

```powershell
rg -n "packages.aliyun.com|AliYunMaven|aliyun.maven" C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\buildSrc C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\buildSrc
```

Expected: no matches in `buildSrc`.

- [ ] **Step 4: Verify local publication tasks still exist**

Run in `cap4k`:

```powershell
.\gradlew.bat tasks --all | Select-String -Pattern "publishToMavenLocal"
```

Expected: output includes `publishToMavenLocal`.

Run in `only-engine`:

```powershell
.\gradlew.bat tasks --all | Select-String -Pattern "publishToMavenLocal"
```

Expected: output includes `publishToMavenLocal`.

- [ ] **Step 5: Commit each repository separately**

Run in `cap4k`:

```powershell
git add buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
git commit -m "build: keep remote publishing off main"
```

Run in `only-engine`:

```powershell
git add buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
git commit -m "build: keep remote publishing off main"
```

---

### Task 5: Verify Main-Branch Builds

**Files:**
- No file changes expected.

- [ ] **Step 1: Verify `cap4k`**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
.\gradlew.bat check publishToMavenLocal
```

Expected: build succeeds without Central, GPG, or Aliyun credentials.

- [ ] **Step 2: Verify `only-engine`**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine
.\gradlew.bat check
```

Expected: build succeeds without remote publishing credentials.

- [ ] **Step 3: Verify `cap4k-reference-content-studio`**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
.\gradlew.bat publishToMavenLocal
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio
.\gradlew.bat check
```

Expected: reference project resolves `0.5.0-SNAPSHOT` from `mavenLocal()` and passes checks.

- [ ] **Step 4: Commit no-op verification note if needed**

No commit is required if verification creates no intentional file changes. If Gradle generated tracked files changed unexpectedly, inspect them and do not commit unrelated generated churn.

---

### Task 6: Create `cap4k` Publishing Channel Branches

**Files:**
- Branch only: `publish/maven-central`
- Branch only: `publish/aliyun-private`

- [ ] **Step 1: Ensure main branch work is committed**

Run in `cap4k`:

```powershell
git status --short
```

Expected: no uncommitted changes from release standardization tasks. Existing unrelated user changes may still exist; do not revert them.

- [ ] **Step 2: Create `publish/aliyun-private` from the current pre-removal state if available**

If the Aliyun publishing configuration is still present in an earlier commit, create the branch from that commit. If Task 4 already removed it on main, create the branch from current main and restore only the Aliyun repository block in Task 7.

Run:

```powershell
git switch -c publish/aliyun-private
```

Expected: branch `publish/aliyun-private` exists locally.

- [ ] **Step 3: Return to main and create `publish/maven-central`**

Run:

```powershell
git switch master
git switch -c publish/maven-central
```

Expected: branch `publish/maven-central` exists locally.

---

### Task 7: Configure `publish/aliyun-private`

**Files:**
- Modify on `publish/aliyun-private`: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts`

- [ ] **Step 1: Switch to `publish/aliyun-private`**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
git switch publish/aliyun-private
```

Expected: current branch is `publish/aliyun-private`.

- [ ] **Step 2: Add the Aliyun repository block**

In `publishing { ... }`, include:

```kotlin
repositories {
    maven {
        name = "AliYunMaven"
        url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
        credentials {
            username = providers.gradleProperty("aliyun.maven.username").orNull
                ?: System.getenv("ALIYUN_MAVEN_USERNAME")
                ?: "defaultUsername"
            password = providers.gradleProperty("aliyun.maven.password").orNull
                ?: System.getenv("ALIYUN_MAVEN_PASSWORD")
                ?: "defaultPassword"
        }
    }
}
```

- [ ] **Step 3: Verify the branch has Aliyun configuration**

Run:

```powershell
rg -n "AliYunMaven|packages.aliyun.com|ALIYUN_MAVEN" buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
```

Expected: matches appear only on `publish/aliyun-private`.

- [ ] **Step 4: Commit the private publish branch change**

Run:

```powershell
git add buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
git commit -m "build: keep aliyun publishing on private branch"
```

Do not push this branch to the public GitHub repository unless explicitly requested.

---

### Task 8: Configure `publish/maven-central`

**Files:**
- Modify on `publish/maven-central`: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\buildSrc\src\main\kotlin\kotlin-jvm.gradle.kts`
- Create on `publish/maven-central`: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.github\workflows\maven-central-release.yml`

- [ ] **Step 1: Switch to `publish/maven-central`**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
git switch publish/maven-central
```

Expected: current branch is `publish/maven-central`.

- [ ] **Step 2: Update the Gradle convention plugin for Maven Central**

Apply these publishing principles in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    signing
}

group = "io.github.ldmoxeii"
version = "0.5.0-SNAPSHOT"

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "CentralPortal"
            url = uri(
                if (project.version.toString().endsWith("-SNAPSHOT")) {
                    "https://central.sonatype.com/repository/maven-snapshots/"
                } else {
                    "https://central.sonatype.com/api/v1/publisher/upload"
                }
            )
            credentials {
                username = providers.gradleProperty("central.username").orNull
                    ?: System.getenv("CENTRAL_USERNAME")
                password = providers.gradleProperty("central.password").orNull
                    ?: System.getenv("CENTRAL_PASSWORD")
            }
        }
    }
}
```

Keep the existing `afterEvaluate` guard that avoids manually creating a publication for `java-gradle-plugin` modules. Inside each manually created `MavenPublication`, add POM metadata:

```kotlin
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
```

Configure signing after publications exist:

```kotlin
afterEvaluate {
    signing {
        val signingKey = providers.gradleProperty("signingKey").orNull
            ?: System.getenv("SIGNING_KEY")
        val signingPassword = providers.gradleProperty("signingPassword").orNull
            ?: System.getenv("SIGNING_PASSWORD")
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }
    }
}
```

If Gradle reports that signing is configured too late, move the `signing` block after the publication creation inside the same existing `afterEvaluate` block.

- [ ] **Step 3: Create Maven Central release workflow on the publishing branch**

Create `.github/workflows/maven-central-release.yml`:

```yaml
name: Maven Central Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: write

jobs:
  publish:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Check
        run: ./gradlew check

      - name: Publish to Maven Central
        run: ./gradlew publish
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/')
        with:
          generate_release_notes: true
```

- [ ] **Step 4: Verify publishing branch has Central config and no Aliyun config**

Run:

```powershell
rg -n "io.github.ldmoxeii|CentralPortal|SIGNING_KEY|CENTRAL_USERNAME|packages.aliyun.com|AliYunMaven" buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts .github/workflows/maven-central-release.yml
```

Expected: Central and signing matches exist; Aliyun matches do not.

- [ ] **Step 5: Verify Maven publication metadata generation**

Run:

```powershell
.\gradlew.bat generatePomFileForMavenPublication
```

Expected: POM generation succeeds for modules that use the `maven` publication. If the task name differs by module, run:

```powershell
.\gradlew.bat tasks --all | Select-String -Pattern "generatePomFile"
```

Then run a concrete generated POM task shown by Gradle, such as:

```powershell
.\gradlew.bat :ddd-core:generatePomFileForMavenPublication
```

- [ ] **Step 6: Commit the Maven Central publishing branch change**

Run:

```powershell
git add buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts .github/workflows/maven-central-release.yml
git commit -m "build: add maven central publish channel"
```

---

### Task 9: Final Review And Handoff

**Files:**
- No required file changes.

- [ ] **Step 1: Verify branch isolation**

Run in `cap4k`:

```powershell
git switch master
rg -n "CentralPortal|SIGNING_KEY|CENTRAL_USERNAME|packages.aliyun.com|AliYunMaven" buildSrc .github
```

Expected: no Central, signing, or Aliyun remote publishing config on `master`.

Run:

```powershell
git switch publish/maven-central
rg -n "CentralPortal|SIGNING_KEY|CENTRAL_USERNAME" buildSrc .github
rg -n "packages.aliyun.com|AliYunMaven" buildSrc .github
```

Expected: first command finds Central/signing config; second command finds no Aliyun config.

Run:

```powershell
git switch publish/aliyun-private
rg -n "packages.aliyun.com|AliYunMaven" buildSrc
```

Expected: Aliyun config exists only on this branch.

- [ ] **Step 2: Return to master after review**

Run:

```powershell
git switch master
```

Expected: current branch is `master`.

- [ ] **Step 3: Document required GitHub repository secrets for future Central release**

Add this note to the final handoff message, not necessarily to repository docs:

```text
For cap4k Maven Central release on publish/maven-central, configure repository secrets:
CENTRAL_USERNAME
CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

- [ ] **Step 4: Do not push publishing branches automatically**

Ask before pushing:

```text
Do you want me to push publish/maven-central to GitHub now, or keep it local until the first release?
```

`publish/aliyun-private` should remain local unless the user explicitly requests a private remote backup.
