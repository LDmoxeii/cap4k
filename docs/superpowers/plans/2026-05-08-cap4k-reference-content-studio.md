# Cap4k Reference Content Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first runnable version of `cap4k-reference-content-studio` as a separate reference repository that proves the current Default Happy Path end to end without depending on `only-engine`.

**Architecture:** Build a four-module Spring Boot sample under a separate repository, bootstrap its base shape from `cap4k`, keep behavior driven by design input and persistence driven by schema input, and preserve generator ownership through committed snapshot evidence instead of committed build directories. The main flow stays strict: review approval emits a domain event, application seam starts media processing, the callback path enters through `ddd-integration-event-http`, media-processing success emits a domain event, and publishing is gated by a pure domain-service eligibility check that finally writes only `Content`.

**Tech Stack:** Gradle multi-module build, Kotlin, Spring Boot, Spring Data JPA, H2, `cap4k` bootstrap/generator pipeline, `springdoc-openapi`, `.http` request files, HTTP end-to-end smoke tests.

---

## File Map

- Create repository root: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio`
  - Canonical runnable reference project repository that will later bind to `git@github.com:LDmoxeii/cap4k-reference-content-studio.git`.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/settings.gradle.kts`
  - Declares the four-module layout and project naming.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/build.gradle.kts`
  - Shared build setup, repository policy, and root task wiring.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/README.md`
  - Shortest runnable path, `.http` usage order, OpenAPI snapshot notes, generator/snapshot contract, and smoke-test entry points.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/build.gradle.kts`
  - Domain module build, generation inputs, and snapshot source-root registration.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-application/build.gradle.kts`
  - Application module build, design-driven families, handwritten orchestration dependencies, and snapshot source-root registration.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-adapter/build.gradle.kts`
  - Adapter module build, HTTP/query/persistence/integration entry points, and snapshot source-root registration.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/build.gradle.kts`
  - Spring Boot start module with H2/JPA runtime wiring and boot-run entrypoint.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/gradle/libs.versions.toml`
  - Version catalog for Boot, Kotlin, `springdoc-openapi`, and test dependencies.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/application.yml`
  - Default local runtime settings, H2 datasource, and `.http`/OpenAPI-friendly defaults.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql`
  - Generator-facing business schema evidence for content and media-processing tables only.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/init/content-studio-runtime-init-h2.sql`
  - Runtime init SQL for H2 including the business schema application slice and the required minimal frame slice (`__event`, `__archived_event`, `__event_http_subscriber`, `__locker`) converted from the `only-danmuku-zero` MySQL source.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/resources/cap4k/content-studio.design.yaml`
  - Design input defining commands, queries, CLI contracts, domain events, and handler families that already exist in stable design-driven generation.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/**`
  - Handwritten domain behavior, aggregate roots, handwritten value objects, and the handwritten publication-eligibility domain service.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-application/src/main/kotlin/com/only4/cap4k/reference/contentstudio/application/**`
  - Handwritten seams that consume generated command/query/handler families where available and handwrite transition surfaces where the generator families do not yet exist.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-adapter/src/main/kotlin/com/only4/cap4k/reference/contentstudio/adapter/**`
  - Query HTTP endpoints, review/content endpoints, repository-backed query handlers, fake media CLI adapter, and handwritten integration-event callback wiring that remains temporary until `#34`.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/kotlin/com/only4/cap4k/reference/contentstudio/start/ContentStudioApplication.kt`
  - Spring Boot application entrypoint.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/http/content.http`
  - Manual draft creation and submit-for-review requests.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/http/review.http`
  - Manual approval request carrying `reviewerId`.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/http/media-processing.http`
  - Real callback consume-path requests plus polling reference notes.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/http/query.http`
  - Content-detail and processing-status queries.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/openapi/content-studio-openapi.json`
  - Committed static OpenAPI snapshot exported from runtime.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src-generated/main/kotlin/**`
  - Snapshot evidence copied from real generated outputs for domain artifacts.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-application/src-generated/main/kotlin/**`
  - Snapshot evidence copied from real generated outputs for application artifacts.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-adapter/src-generated/main/kotlin/**`
  - Snapshot evidence copied from real generated outputs for adapter artifacts.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/test/kotlin/com/only4/cap4k/reference/contentstudio/start/ContentStudioHappyPathHttpSmokeTest.kt`
  - End-to-end HTTP smoke test from empty business DB through callback consume path to final published content state.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/test/kotlin/com/only4/cap4k/reference/contentstudio/domain/**`
  - Domain behavior tests for `Content`, `MediaProcessingTask`, and publication eligibility.
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-application/src/test/kotlin/com/only4/cap4k/reference/contentstudio/application/**`
  - Application seam tests centered on `ApproveContentCmd -> StartMediaProcessingCmd` and `MediaProcessingSucceeded -> PublishContent`.

## Execution Policy

- This plan targets the separate repository `cap4k-reference-content-studio`, not code implementation inside `cap4k`.
- The first implementation happens locally under `only-workspace`, but delivery is incomplete until the project is bound and pushed to `git@github.com:LDmoxeii/cap4k-reference-content-studio.git`.
- Version one must stay independent from `only-engine`, keep `enumTranslation` disabled, and treat `#34`, `#35`, and `#36` as explicit handwritten-transition constraints rather than silent framework patch work.
- All Windows command examples should stay short enough for local execution; use focused commands and avoid giant one-liners.

## Shared Conventions For Every Task

- Use `mavenLocal()` as the default `cap4k` dependency source.
- Use a four-module project only: `domain`, `application`, `adapter`, `start`.
- Keep `.http` files as the primary manual interaction surface; OpenAPI remains a contract surface, not the main operator UI.
- Keep generated snapshot directories (`src-generated/main/kotlin`) out of compilation source sets. They are copied evidence only.
- Keep handwritten transition surfaces explicit anywhere version one is blocked by `#34`, `#35`, or `#36`.
- Keep callback consumption on the real `ddd-integration-event-http` consume path.
- Do not introduce `only-engine`, `enumTranslation`, auth, reject flow, withdraw flow, or standalone read-model infrastructure.

### Task 1: Create the repository skeleton and root build

**Files:**
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/.gitignore`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/settings.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/gradle/libs.versions.toml`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/README.md`

- [ ] **Step 1: Create the empty repository directory and confirm nothing already occupies it**

Run:

```powershell
$root = 'C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio'
if (Test-Path $root) { Get-ChildItem -Force $root } else { New-Item -ItemType Directory -Path $root | Out-Null; 'CREATED' }
```

Expected:

- either a newly created empty directory
- or an explicit listing that proves the directory was already present and needs review before continuing

- [ ] **Step 2: Create `.gitignore` with build/output exclusions but keep snapshot roots versionable**

Write:

```gitignore
.gradle/
build/
out/
*.iml
.idea/
!**/src-generated/
!**/src-generated/**
```

Expected:

- no committed build directories
- committed `src-generated` evidence stays allowed

- [ ] **Step 3: Create `settings.gradle.kts` with the fixed four-module layout**

Write:

```kotlin
rootProject.name = "cap4k-reference-content-studio"

include("cap4k-reference-content-studio-domain")
include("cap4k-reference-content-studio-application")
include("cap4k-reference-content-studio-adapter")
include("cap4k-reference-content-studio-start")
```

Expected:

- project naming matches the approved reference-project identity
- no extra modules are introduced

- [ ] **Step 4: Create the root `build.gradle.kts` with shared repository policy and a placeholder snapshot-sync aggregate task**

Write:

```kotlin
plugins {
    base
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

tasks.register("syncGeneratedSnapshots") {
    group = "verification"
    description = "Sync generated artifact snapshots into src-generated roots."
}
```

Expected:

- `mavenLocal()` is the default cap4k source
- a root-level sync task exists and can later depend on module tasks

- [ ] **Step 5: Create `gradle/libs.versions.toml` with the first-pass runtime and tooling versions**

Write:

```toml
[versions]
kotlin = "2.1.21"
spring-boot = "3.5.6"
spring-dependency-management = "1.1.7"
springdoc = "2.8.13"
junit = "5.13.1"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
h2 = { module = "com.h2database:h2" }
springdoc-webmvc-ui = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

Expected:

- versions are centralized
- `springdoc-openapi` uses the current starter artifact family

- [ ] **Step 6: Create an initial `README.md` that states the shortest runnable path and the repo boundary**

Write:

```markdown
# cap4k-reference-content-studio

`cap4k-reference-content-studio` is the official runnable reference project for `cap4k`.

## Prerequisites

1. Publish local `cap4k` artifacts to `mavenLocal()`.
2. Use JDK 21.

## Shortest Path

1. Start the application from `cap4k-reference-content-studio-start`.
2. Use the `.http` files under `http/` to walk the main happy path.

## Contract Surfaces

- `.http` files are the primary manual interaction surface.
- Runtime OpenAPI is exposed by the running application.
- `openapi/content-studio-openapi.json` is the committed static contract snapshot.
- `src-generated/main/kotlin` roots are committed snapshot evidence, not compile-time source roots.
```

Expected:

- the repo states its identity and shortest path immediately
- README does not falsely present Swagger UI as the main operating surface

- [ ] **Step 7: Run Gradle help at the new root to verify the basic skeleton resolves**

Run:

```powershell
Set-Location C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio
gradle help
```

Expected:

- `BUILD SUCCESSFUL`
- no missing-settings or version-catalog parse error

- [ ] **Step 8: Initialize the local git repository and make the first commit**

Run:

```powershell
git init
git add .gitignore settings.gradle.kts build.gradle.kts gradle/libs.versions.toml README.md
git commit -m "chore: initialize reference project skeleton"
```

Expected:

- the new repository has a clean first commit
- the project skeleton is ready for module-level work

### Task 2: Create the four-module build and the local `cap4k` dependency model

**Files:**
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-application/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-adapter/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/build.gradle.kts`

- [ ] **Step 1: Re-read the current bootstrap-generated sample shape in `cap4k` before creating module builds**

Run:

```powershell
Get-Content C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\cap4k-plugin-pipeline-gradle\src\test\resources\functional\bootstrap-generated-project-smoke-sample\build.gradle.kts
Get-Content C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\cap4k-plugin-pipeline-gradle\src\test\resources\functional\aggregate-compile-sample\build.gradle.kts
Get-Content C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\cap4k-plugin-pipeline-gradle\src\test\resources\functional\design-integrated-compile-sample\build.gradle.kts
```

Expected:

- bootstrap module naming conventions are fresh in memory
- aggregate and design-generator configuration shapes are available for reuse

- [ ] **Step 2: Create the `domain` module build with Kotlin/JPA basics and `cap4k` plugin wiring**

Write:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(21)
}
```

Then extend it in later tasks when the exact `cap4k` plugin coordinates are confirmed from the published local artifacts.

Expected:

- the domain module exists with the correct toolchain
- no accidental Boot plugin is applied here

- [ ] **Step 3: Create the `application` module build with dependency on `domain`**

Write:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":cap4k-reference-content-studio-domain"))
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(21)
}
```

Expected:

- application depends only on domain at this stage
- no adapter/runtime dependency leaks in

- [ ] **Step 4: Create the `adapter` module build with dependencies on `application` and `domain`**

Write:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":cap4k-reference-content-studio-domain"))
    implementation(project(":cap4k-reference-content-studio-application"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.h2)
}

kotlin {
    jvmToolchain(21)
}
```

Expected:

- adapter is the first place where web + JPA dependencies appear
- the domain/application boundary is still explicit

- [ ] **Step 5: Create the `start` module build with Boot entrypoint and dependency on the other three modules**

Write:

```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":cap4k-reference-content-studio-domain"))
    implementation(project(":cap4k-reference-content-studio-application"))
    implementation(project(":cap4k-reference-content-studio-adapter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.springdoc.webmvc.ui)
    runtimeOnly(libs.h2)
    testImplementation(libs.spring.boot.starter.test)
}

kotlin {
    jvmToolchain(21)
}
```

Expected:

- start becomes the runnable module
- `springdoc-openapi` is only introduced where the app actually runs

- [ ] **Step 6: Run Gradle help and `projects` again to verify module resolution**

Run:

```powershell
gradle projects
```

Expected:

- all four modules are listed
- no missing module build file errors remain

- [ ] **Step 7: Commit the module-build baseline**

Run:

```powershell
git add cap4k-reference-content-studio-domain/build.gradle.kts cap4k-reference-content-studio-application/build.gradle.kts cap4k-reference-content-studio-adapter/build.gradle.kts cap4k-reference-content-studio-start/build.gradle.kts
git commit -m "build: add reference project modules"
```

Expected:

- the repository now has a stable multi-module build baseline

### Task 3: Add bootstrap origin, design input, schema input, and runtime SQL skeletons

**Files:**
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/resources/cap4k/content-studio.design.yaml`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/init/content-studio-runtime-init-h2.sql`

- [ ] **Step 1: Re-read the public generator DSL contract in `cap4k` before writing design/schema inputs**

Run:

```powershell
Get-Content C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\public\reference\generator-dsl.zh-CN.md
```

Expected:

- the currently documented DSL keys are visible
- no stale wrapper or enumTranslation assumptions leak in

- [ ] **Step 2: Create the first-pass design input that only uses already-stable families**

Write:

```yaml
project:
  group: com.only4.cap4k.reference.contentstudio
  artifact: cap4k-reference-content-studio

types:
  imports: []

generators:
  designCommand:
    enabled: true
  designQuery:
    enabled: true
  designQueryHandler:
    enabled: true
  designClient:
    enabled: true
  designClientHandler:
    enabled: true
  designDomainEvent:
    enabled: true
  designDomainEventHandler:
    enabled: true
  aggregate:
    enabled: true
    artifacts:
      factory: true
      specification: true
      unique: false

layout:
  aggregate:
    packageName: com.only4.cap4k.reference.contentstudio.domain.aggregates
```

Then extend the file in later tasks with the actual `Content`, `MediaProcessingTask`, command, query, CLI, and domain-event declarations.

Expected:

- design input deliberately avoids integration-event, domain-service, and value-object families
- aggregate generation and design generation are both explicitly enabled

- [ ] **Step 3: Create the business schema SQL with only business tables**

Write:

```sql
create table content (
    id uuid primary key,
    title varchar(200) not null,
    body clob not null,
    media_source_key varchar(200) not null,
    review_status varchar(40) not null,
    content_status varchar(40) not null,
    db_created_at timestamp not null,
    db_updated_at timestamp not null
);

create table media_processing_task (
    id uuid primary key,
    content_id uuid not null,
    external_task_id varchar(120),
    processing_status varchar(40) not null,
    db_created_at timestamp not null,
    db_updated_at timestamp not null
);
```

Expected:

- the schema captures only business truth
- no frame/runtime infrastructure tables leak into generator input SQL

- [ ] **Step 4: Create the runtime init SQL and add placeholder comments for the H2-converted frame slice**

Write:

```sql
-- business schema bootstrap
runscript from 'classpath:db/schema/content-studio-schema.sql';

-- frame slice required by cap4k-ddd-starter + ddd-integration-event-http-jpa
-- add H2-compatible definitions for:
-- __event
-- __archived_event
-- __event_http_subscriber
-- __locker
```

Expected:

- runtime init SQL is explicitly separate from generator input SQL
- the file already documents the minimal frame slice requirement

- [ ] **Step 5: Commit the input skeletons before filling their full content**

Run:

```powershell
git add cap4k-reference-content-studio-domain/src/main/resources/cap4k/content-studio.design.yaml cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql cap4k-reference-content-studio-start/src/main/resources/db/init/content-studio-runtime-init-h2.sql
git commit -m "build: add reference project input skeletons"
```

Expected:

- the repository now has explicit placeholders for both design and schema truth sources

### Task 4: Fill the design, schema, and runtime-init content around the approved happy path

**Files:**
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/resources/cap4k/content-studio.design.yaml`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-start/src/main/resources/db/init/content-studio-runtime-init-h2.sql`

- [ ] **Step 1: Add the actual behavior contracts to the design file**

Extend the design file with declarations equivalent to:

```yaml
commands:
  - name: CreateContentDraftCmd
  - name: SubmitContentForReviewCmd
  - name: ApproveContentCmd
  - name: StartMediaProcessingCmd
  - name: MarkMediaProcessingSucceededCmd
  - name: PublishContentCmd

queries:
  - name: GetContentDetailQry
  - name: GetCurrentProcessingStatusQry

clients:
  - name: MediaProcessingCli

domainEvents:
  - name: ContentApprovedDomainEvent
  - name: MediaProcessingSucceededDomainEvent
```

Expected:

- all main happy-path contracts are represented
- there is no design-driven integration-event declaration yet

- [ ] **Step 2: Add aggregate-relevant persistence columns that support the main path and nothing more**

Ensure the schema SQL contains at minimum:

```sql
alter table content add column reviewer_id varchar(80);
alter table content add column reviewed_at timestamp;
alter table content add column published_at timestamp;

alter table media_processing_task add unique (content_id);
```

Expected:

- `reviewerId`, approval time, and publish time are stored as business facts
- the one-content-one-task simplification is encoded by schema constraint

- [ ] **Step 3: Extract the minimal frame slice from `only-danmuku-zero` and rewrite it into H2-compatible SQL**

Use as source:

```powershell
Get-Content C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero\only-danmuku-start\src\main\resources\db.migration\V2025.0217.000000__frame_init.sql
```

Then write H2-compatible definitions for:

```sql
create table __event (...);
create table __archived_event (...);
create table __event_http_subscriber (...);
create table __locker (...);
```

Expected:

- only the required slice is copied
- MySQL-specific syntax is removed or adapted for H2
- runtime tables are clearly separated from business tables

- [ ] **Step 4: Verify there is no accidental `enumTranslation` or `only-engine` reference in any input file**

Run:

```powershell
rg -n "only-engine|enumTranslation|translation" C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\cap4k-reference-content-studio-domain\src\main\resources C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\cap4k-reference-content-studio-start\src\main\resources
```

Expected:

- no matches

- [ ] **Step 5: Commit the fully populated input files**

Run:

```powershell
git add cap4k-reference-content-studio-domain/src/main/resources/cap4k/content-studio.design.yaml cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql cap4k-reference-content-studio-start/src/main/resources/db/init/content-studio-runtime-init-h2.sql
git commit -m "build: define reference project inputs"
```

Expected:

- the project now has a stable behavior/persistence input baseline

### Task 5: Implement the domain model, handwritten value objects, and publish-eligibility service

**Files:**
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/content/Content.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/content/ContentBehavior.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/content/ReviewStatus.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/content/ContentStatus.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/media/MediaProcessingTask.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/aggregates/media/MediaProcessingStatus.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/main/kotlin/com/only4/cap4k/reference/contentstudio/domain/services/PublicationEligibilityDomainService.kt`
- Test: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/test/kotlin/com/only4/cap4k/reference/contentstudio/domain/ContentDomainTest.kt`
- Test: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/test/kotlin/com/only4/cap4k/reference/contentstudio/domain/MediaProcessingTaskDomainTest.kt`
- Test: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-content-studio/cap4k-reference-content-studio-domain/src/test/kotlin/com/only4/cap4k/reference/contentstudio/domain/PublicationEligibilityDomainServiceTest.kt`

- [ ] **Step 1: Write the failing domain tests first**

At minimum, write tests equivalent to:

```kotlin
@Test
fun `approve marks review approved and emits content approved event`() { /* ... */ }

@Test
fun `mark processing succeeded emits media processing succeeded event`() { /* ... */ }

@Test
fun `publish eligibility requires approved review and succeeded media processing`() { /* ... */ }
```

Expected:

- the tests fail because the aggregates and service do not exist yet

- [ ] **Step 2: Implement `Content` with split state lines and no mirrored processing status**

Ensure `Content` carries:

```kotlin
var reviewStatus: ReviewStatus
var contentStatus: ContentStatus
val mediaSourceKey: String
```

And supports:

```kotlin
fun approve(reviewerId: String, approvedAt: Instant)
fun publish(publishedAt: Instant)
```

Expected:

- `Content` never stores media-processing success as its own mirrored field
- `approve()` emits `ContentApprovedDomainEvent`

- [ ] **Step 3: Implement `MediaProcessingTask` as the unique processing-truth aggregate**

Ensure it keeps:

```kotlin
val contentId: UUID
var externalTaskId: String?
var processingStatus: MediaProcessingStatus
```

And supports:

```kotlin
fun markSubmitted(externalTaskId: String)
fun markSucceeded()
```

Expected:

- media-processing truth stays in this aggregate only
- success emits `MediaProcessingSucceededDomainEvent`

- [ ] **Step 4: Implement the handwritten `PublicationEligibilityDomainService` as a pure decision service**

Use a shape equivalent to:

```kotlin
interface PublicationEligibilityDomainService {
    fun evaluate(content: Content, task: MediaProcessingTask): Boolean
}
```

Expected:

- the service does not call repositories
- it only judges current facts

- [ ] **Step 5: Run the domain test suite and make it pass**

Run:

```powershell
gradle :cap4k-reference-content-studio-domain:test
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit the domain baseline**

Run:

```powershell
git add cap4k-reference-content-studio-domain
git commit -m "feat: add reference project domain model"
```

Expected:

- domain aggregates and pure domain-service contract are stable

### Task 6: Implement the application seam and explicit handwritten transition surfaces

**Files:**
- Create or modify generated command/query/domain-event handler targets in `cap4k-reference-content-studio-application/src/main/kotlin/com/only4/cap4k/reference/contentstudio/application/**`
- Create: `.../application/ports/ContentRepository.kt`
- Create: `.../application/ports/MediaProcessingTaskRepository.kt`
- Create: `.../application/ports/MediaProcessingCli.kt`
- Test: `.../application/src/test/kotlin/com/only4/cap4k/reference/contentstudio/application/ApproveContentSeamTest.kt`
- Test: `.../application/src/test/kotlin/com/only4/cap4k/reference/contentstudio/application/PublishContentFlowTest.kt`

- [ ] **Step 1: Generate the stable design-driven families and inspect the concrete output paths**

Run:

```powershell
gradle cap4kPlan
gradle cap4kGenerate
```

Expected:

- generated command/query/client/domain-event families appear
- output paths are concrete and can be used for later snapshot sync

- [ ] **Step 2: Write failing application tests for the two main orchestration seams**

Write tests equivalent to:

```kotlin
@Test
fun `approve content emits domain event and starts media processing through domain-event seam`() { /* ... */ }

@Test
fun `media processing succeeded path publishes content only when eligibility passes`() { /* ... */ }
```

Expected:

- the tests fail because application ports/handlers are not yet wired

- [ ] **Step 3: Handwrite repository and CLI ports where version one needs stable seams**

Create interfaces shaped like:

```kotlin
interface ContentRepository { fun mustGet(id: UUID): Content; fun save(content: Content) }
interface MediaProcessingTaskRepository { fun mustGetByContentId(contentId: UUID): MediaProcessingTask; fun save(task: MediaProcessingTask) }
interface MediaProcessingCli { fun start(contentId: UUID, mediaSourceKey: String): String }
```

Expected:

- application logic depends on ports instead of adapter implementations

- [ ] **Step 4: Implement the approval seam exactly in the approved shape**

Ensure:

```kotlin
ApproveContentCmdHandler -> only writes Content
Content.approve() -> emits ContentApprovedDomainEvent
ContentApprovedDomainEvent handler -> issues StartMediaProcessingCmd
StartMediaProcessingCmdHandler -> reads Content, writes MediaProcessingTask, calls MediaProcessingCli
```

Expected:

- no direct second-command dispatch from inside `ApproveContentCmdHandler`

- [ ] **Step 5: Implement the publish seam exactly in the approved shape**

Ensure:

```kotlin
IntegrationEvent handler -> MarkMediaProcessingSucceededCmd
MarkMediaProcessingSucceededCmdHandler -> only writes MediaProcessingTask
MediaProcessingSucceededDomainEvent handler -> PublishContentCmd
PublishContentCmdHandler -> reads Content + MediaProcessingTask, calls PublicationEligibilityDomainService, writes only Content
```

Expected:

- event listener layers do not read repositories
- repository reads happen only inside command handlers

- [ ] **Step 6: Run the application tests and keep them container-light**

Run:

```powershell
gradle :cap4k-reference-content-studio-application:test
```

Expected:

- `BUILD SUCCESSFUL`
- the tests prove the approved seams without requiring a heavy Spring container

- [ ] **Step 7: Commit the application layer**

Run:

```powershell
git add cap4k-reference-content-studio-application
git commit -m "feat: add reference project application seams"
```

Expected:

- application orchestration matches the strict default-path model

### Task 7: Implement the adapter layer, real HTTP consume path, and repository-backed queries

**Files:**
- Create: `.../adapter/application/queries/GetContentDetailQryHandler.kt`
- Create: `.../adapter/application/queries/GetCurrentProcessingStatusQryHandler.kt`
- Create: `.../adapter/http/ContentController.kt`
- Create: `.../adapter/http/ReviewController.kt`
- Create: `.../adapter/http/QueryController.kt`
- Create: `.../adapter/persistence/**`
- Create: `.../adapter/integration/FakeMediaProcessingCli.kt`
- Create handwritten integration-event registration/configuration classes needed for `#34` gap coverage
- Test: `.../adapter/src/test/kotlin/com/only4/cap4k/reference/contentstudio/adapter/QueryHandlerTest.kt`

- [ ] **Step 1: Write failing repository-backed query tests first**

At minimum, write tests equivalent to:

```kotlin
@Test
fun `content detail query returns title body review status and content status`() { /* ... */ }

@Test
fun `processing status query returns current task status and external task id`() { /* ... */ }
```

Expected:

- query tests fail before handlers and repository adapters exist

- [ ] **Step 2: Implement repository-backed query handlers instead of a read-model subsystem**

Use the path:

```kotlin
controller -> query handler -> repository -> response
```

Expected:

- there is no standalone projection store
- controllers do not call repositories directly

- [ ] **Step 3: Implement handwritten HTTP endpoints for content, review, and query surfaces**

Expose endpoints for:

```text
POST /contents
POST /contents/{contentId}/submit-review
POST /contents/{contentId}/approve
GET /contents/{contentId}
GET /media-processing/{contentId}
```

Expected:

- publish is not exposed as a manual HTTP operation
- approval explicitly carries `reviewerId`

- [ ] **Step 4: Implement the handwritten fake media CLI adapter and the integration-event consume binding**

Ensure:

- fake CLI is in-process
- callback still enters through the real `ddd-integration-event-http` consume path
- no fake demo callback controller is introduced

Expected:

- the project demonstrates the real consume path
- `#34` gap is covered by explicit handwritten transition code only

- [ ] **Step 5: Run adapter tests and a focused application compile target**

Run:

```powershell
gradle :cap4k-reference-content-studio-adapter:test
gradle :cap4k-reference-content-studio-adapter:compileKotlin
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit the adapter layer**

Run:

```powershell
git add cap4k-reference-content-studio-adapter
git commit -m "feat: add reference project adapters"
```

Expected:

- the project now has real HTTP/manual-entry surfaces and repository-backed query flow

### Task 8: Implement the start module, runtime wiring, OpenAPI exposure, and local boot path

**Files:**
- Create: `.../start/src/main/kotlin/com/only4/cap4k/reference/contentstudio/start/ContentStudioApplication.kt`
- Create: `.../start/src/main/resources/application.yml`
- Modify: `.../start/build.gradle.kts`

- [ ] **Step 1: Create the Boot application entrypoint**

Write:

```kotlin
@SpringBootApplication
class ContentStudioApplication

fun main(args: Array<String>) {
    runApplication<ContentStudioApplication>(*args)
}
```

Expected:

- the project has a standard Boot entrypoint

- [ ] **Step 2: Add the H2/JPA/OpenAPI runtime configuration**

At minimum, configure:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:contentstudio;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: none
  sql:
    init:
      mode: always
      schema-locations: classpath:db/init/content-studio-runtime-init-h2.sql

springdoc:
  api-docs:
    path: /v3/api-docs
```

Expected:

- H2 starts from explicit SQL, not Hibernate auto-ddl
- runtime OpenAPI is available

- [ ] **Step 3: Run the application locally and confirm the baseline endpoints exist**

Run:

```powershell
gradle :cap4k-reference-content-studio-start:bootRun
```

Then verify:

```powershell
Invoke-WebRequest http://localhost:8080/v3/api-docs
```

Expected:

- app starts
- OpenAPI docs endpoint responds successfully

- [ ] **Step 4: Commit the start-module wiring**

Run:

```powershell
git add cap4k-reference-content-studio-start
git commit -m "feat: wire reference project runtime"
```

Expected:

- the project is locally bootable

### Task 9: Add generated snapshot sync and commit the snapshot evidence

**Files:**
- Modify: root `build.gradle.kts`
- Create or modify per-module snapshot-task wiring
- Populate: each module `src-generated/main/kotlin/**`

- [ ] **Step 1: Inspect the real generated output paths after `cap4kGenerate`**

Run:

```powershell
Get-ChildItem -Recurse -Path . -Filter *.kt | Where-Object { $_.FullName -like '*build\\generated\\cap4k*' } | Select-Object -ExpandProperty FullName
```

Expected:

- concrete source directories are known
- snapshot sync can be scripted against real outputs

- [ ] **Step 2: Implement a root `syncGeneratedSnapshots` task that copies all real generated outputs into module `src-generated` roots**

Ensure the task:

- deletes stale snapshot files before copy
- copies from real generated roots
- never makes `src-generated` a compile source root

Expected:

- snapshot sync is a project contract, not a framework contract

- [ ] **Step 3: Run the sync task and inspect the copied snapshot directories**

Run:

```powershell
gradle syncGeneratedSnapshots
git status --short
```

Expected:

- only `src-generated/main/kotlin/**` files appear as committed evidence
- no build directory is staged for commit

- [ ] **Step 4: Commit the snapshot-sync contract and the first snapshot evidence**

Run:

```powershell
git add build.gradle.kts cap4k-reference-content-studio-domain/src-generated cap4k-reference-content-studio-application/src-generated cap4k-reference-content-studio-adapter/src-generated
git commit -m "build: add generated snapshot evidence"
```

Expected:

- reviewers can inspect generated-vs-handwritten boundaries from committed snapshot roots

### Task 10: Add `.http` interaction files and the committed OpenAPI snapshot

**Files:**
- Create: `http/content.http`
- Create: `http/review.http`
- Create: `http/media-processing.http`
- Create: `http/query.http`
- Create: `openapi/content-studio-openapi.json`

- [ ] **Step 1: Create `content.http` and `review.http` with explicit variables and manual copy points**

At minimum, include:

```http
@baseUrl = http://localhost:8080
@contentId =

POST {{baseUrl}}/contents
Content-Type: application/json

{
  "title": "First content",
  "body": "Hello cap4k",
  "mediaSourceKey": "demo/video-001"
}
```

and

```http
POST {{baseUrl}}/contents/{{contentId}}/approve
Content-Type: application/json

{
  "reviewerId": "reviewer-001"
}
```

Expected:

- manual interaction is explicit and reviewable
- no automatic value-passing magic is introduced

- [ ] **Step 2: Create `media-processing.http` that hits the real integration-event consume path**

Include a request that targets:

```http
POST {{baseUrl}}/cap4k/integration-event/http/consume
```

with a minimal payload carrying:

- `externalTaskId`
- `status`

Expected:

- the file demonstrates the real callback path, not a demo shim endpoint

- [ ] **Step 3: Create `query.http` for the two allowed minimal queries**

Include:

```http
GET {{baseUrl}}/contents/{{contentId}}
GET {{baseUrl}}/media-processing/{{contentId}}
```

Expected:

- no list/query-expansion scope sneaks in

- [ ] **Step 4: Export the OpenAPI contract from runtime and commit it**

Run:

```powershell
Invoke-WebRequest http://localhost:8080/v3/api-docs -OutFile openapi/content-studio-openapi.json
```

Expected:

- a committed static snapshot exists
- OpenAPI remains a machine-readable contract surface for later follow-up work

- [ ] **Step 5: Commit the interaction materials**

Run:

```powershell
git add http openapi/content-studio-openapi.json
git commit -m "docs: add reference project interaction materials"
```

Expected:

- `.http` and OpenAPI evidence are both versioned

### Task 11: Add the HTTP smoke test and polling fallback proof

**Files:**
- Create: `cap4k-reference-content-studio-start/src/test/kotlin/com/only4/cap4k/reference/contentstudio/start/ContentStudioHappyPathHttpSmokeTest.kt`
- Create or modify: polling fallback job and its tests

- [ ] **Step 1: Write the failing end-to-end smoke test before the final runtime polish**

The test should execute the full chain:

```kotlin
create draft
submit review
approve review
POST callback consume event
assert content detail says published
assert processing status says succeeded
```

Expected:

- the test fails until the full runtime path is wired correctly

- [ ] **Step 2: Make the smoke test pass by fixing only the missing runtime gaps**

Run:

```powershell
gradle :cap4k-reference-content-studio-start:test --tests "*ContentStudioHappyPathHttpSmokeTest"
```

Expected:

- `BUILD SUCCESSFUL`
- the happy path is proven from empty business DB through real callback consume path

- [ ] **Step 3: Add polling fallback code and test proof without promoting it to the manual primary path**

At minimum, add:

- one polling job
- one focused test proving fallback behavior

Expected:

- polling exists in code and tests
- no new `.http` manual flow is introduced for polling

- [ ] **Step 4: Commit the smoke and fallback proof**

Run:

```powershell
git add cap4k-reference-content-studio-start/src/test cap4k-reference-content-studio-application/src/test
git commit -m "test: add reference project happy path proof"
```

Expected:

- the project now proves both the main callback path and the fallback contract

### Task 12: Finalize README, bind the remote, verify the whole project, and update `cap4k#27`

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Expand README from the minimal stub into the full first-version operator guide**

README must include:

- what the repo is
- prerequisite `publishToMavenLocal` note
- shortest startup path
- `.http` execution order
- where OpenAPI lives
- what `src-generated` means
- what is intentionally out of scope in version one

Expected:

- a reader can clone and run without reading the whole `cap4k` repo first

- [ ] **Step 2: Run the full reference-project verification set**

Run:

```powershell
gradle test
gradle cap4kPlan
gradle cap4kGenerate
gradle syncGeneratedSnapshots
git diff --check
git status --short
```

Expected:

- tests pass
- generator pipeline runs
- snapshot sync is stable
- no whitespace/diff corruption remains
- worktree is clean or only shows deliberate snapshot/openapi changes that will be committed immediately

- [ ] **Step 3: Add the remote and push the finished first version**

Run:

```powershell
git remote add origin git@github.com:LDmoxeii/cap4k-reference-content-studio.git
git branch -M master
git push -u origin master
```

Expected:

- the separate canonical reference repository exists as the delivered artifact

- [ ] **Step 4: Update `cap4k#27` lifecycle and leave the correct closure notes**

Run after the reference repo is live:

```text
- mark `plan written`
- mark `implementation merged` only when the separate repo is pushed
- note that `v1` intentionally handwrites transition surfaces constrained by #34/#35/#36
- note that enumTranslation remains forbidden before #33
```

Expected:

- `#27` reflects reality rather than treating “local prototype exists” as completion

- [ ] **Step 5: Commit the final README polish if needed**

Run:

```powershell
git add README.md
git commit -m "docs: finalize reference project guide"
```

Expected:

- the repository state is final and pushable
