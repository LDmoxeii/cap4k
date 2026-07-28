# Cap4k Querydsl Module Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `ddd-domain-repo-jpa-querydsl` from the active cap4k build and default repository generation surface, while keeping the default repository path JPA-only.

**Architecture:** Remove the module from Gradle entrypoints, delete the module tree, and collapse the default aggregate repository generator/template path to a single JPA-only branch. Update current-state architecture documentation in the same implementation PR so the repository, generator output, and active inventory map agree on the supported surface.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Pebble templates, JUnit 5, MockK, ripgrep, PowerShell.

## Global Constraints

- Work only in `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\remove-querydsl-module-spec` on branch `docs/remove-querydsl-module-spec`.
- Release this as a single breaking release.
- Do not write or publish a migration example for this slice.
- Update active architecture maps in the same implementation PR.
- For documentation changes, state only the current supported capability and avoid historical transition notes.
- Do not remove `ddd-domain-repo-jpa`.
- Do not remove Spring Data JPA `Specification` support.
- Do not redesign `Repository`, `RepositorySupervisor`, `AbstractJpaRepository`, `JpaPredicate`, or aggregate load-plan behavior.
- Do not introduce Jimmer, Blaze Persistence, or another query backend as a replacement.
- Do not preserve a compatibility artifact or empty shim for `ddd-domain-repo-jpa-querydsl` unless release management explicitly asks for a staged deprecation release.
- Do not commit unless the user explicitly asks for commits; commit commands below are execution checkpoints, not permission to commit by default.

---

## Current Baseline Evidence

- Primary spec: `docs/superpowers/specs/2026-07-23-cap4k-querydsl-module-removal-design.md`.
- Worktree: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\remove-querydsl-module-spec`.
- Branch: `docs/remove-querydsl-module-spec`.
- Baseline command passed in this worktree: `.\gradlew.bat help --quiet`.
- Current active reference scan found planned touchpoints in `settings.gradle.kts`, `cap4k-ddd-starter/build.gradle.kts`, `gradle/libs.versions.toml`, `ddd-domain-repo-jpa-querydsl/**`, `RepositoryArtifactPlanner.kt`, `AggregateArtifactPlannerTest.kt`, `repository.kt.peb`, `PebbleArtifactRendererTest.kt`, and `docs/superpowers/analysis/architecture-map.md`.

---

## File Structure

- `settings.gradle.kts`
  - Owns active Gradle subproject inclusion.
  - After this slice it must not include `ddd-domain-repo-jpa-querydsl`.

- `cap4k-ddd-starter/build.gradle.kts`
  - Owns starter API and test dependencies.
  - After this slice it must not expose or test-depend on `:ddd-domain-repo-jpa-querydsl`.

- `gradle/libs.versions.toml`
  - Owns shared dependency aliases.
  - After this slice it must not keep the unused `querydsl` alias.

- `ddd-domain-repo-jpa-querydsl/build.gradle.kts`
  - Deleted module build file.

- `ddd-domain-repo-jpa-querydsl/src/main/**`
  - Deleted Querydsl runtime adapter API.

- `ddd-domain-repo-jpa-querydsl/src/test/**`
  - Deleted Querydsl module tests.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
  - Owns repository artifact plan context.
  - After this slice it must not emit `supportQuerydsl`.

- `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Owns planner contract tests for repository and schema context.
  - After this slice it must assert Querydsl keys are absent.

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
  - Owns default generated aggregate repository Kotlin content.
  - After this slice it must render only `JpaRepository`, `JpaSpecificationExecutor`, and `AbstractJpaRepository`.

- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Owns default renderer output tests.
  - After this slice fixture contexts must not carry `supportQuerydsl`, and output assertions must reject Querydsl repository strings.

- `docs/superpowers/analysis/architecture-map.md`
  - Owns current repository structure facts for maintainers.
  - After this slice it must not list `ddd-domain-repo-jpa-querydsl` as a current runtime root.

---

### Task 1: Remove Querydsl From The Build Surface And Current Inventory

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `cap4k-ddd-starter/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `docs/superpowers/analysis/architecture-map.md`

**Interfaces:**
- Consumes: current Gradle include list and starter dependency list.
- Produces: active build graph with no `:ddd-domain-repo-jpa-querydsl` project dependency.
- Produces: active architecture map that lists only current runtime roots.

- [ ] **Step 1: Update `settings.gradle.kts` include list**

Replace the runtime repository include line with this exact current module set:

```kotlin
include("ddd-domain-event-jpa", "ddd-domain-repo-jpa")
```

- [ ] **Step 2: Remove starter API exposure**

In `cap4k-ddd-starter/build.gradle.kts`, delete this line:

```kotlin
api(project(":ddd-domain-repo-jpa-querydsl"))
```

Keep this line unchanged:

```kotlin
api(project(":ddd-domain-repo-jpa"))
```

- [ ] **Step 3: Remove starter test dependency**

In `cap4k-ddd-starter/build.gradle.kts`, delete this line:

```kotlin
testImplementation(project(":ddd-domain-repo-jpa-querydsl"))
```

Keep this line unchanged:

```kotlin
testImplementation(project(":ddd-domain-repo-jpa"))
```

- [ ] **Step 4: Remove unused Querydsl dependency alias**

In `gradle/libs.versions.toml`, delete this line:

```toml
querydsl = { module = "com.querydsl:querydsl-core" }
```

The persistence alias block should still keep `jpa` and `hibernate-core`:

```toml
jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
hibernate-core = { module = "org.hibernate:hibernate-core", version.ref = "hibernate-core" }
```

- [ ] **Step 5: Update current architecture map**

In `docs/superpowers/analysis/architecture-map.md`, remove this bullet from the representative runtime roots list:

```markdown
  - `ddd-domain-repo-jpa-querydsl`
```

Do not add historical wording explaining the removal.

- [ ] **Step 6: Verify starter compile classpath**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:dependencies --configuration compileClasspath --quiet
```

Expected: output contains no line matching `project :ddd-domain-repo-jpa-querydsl`.

- [ ] **Step 7: Commit checkpoint if commits are authorized**

Run only if the user has authorized commits:

```powershell
git add settings.gradle.kts cap4k-ddd-starter/build.gradle.kts gradle/libs.versions.toml docs/superpowers/analysis/architecture-map.md
git commit -m "refactor(cap4k): drop querydsl from build surface"
```

---

### Task 2: Delete The Querydsl Module

**Files:**
- Delete: `ddd-domain-repo-jpa-querydsl/build.gradle.kts`
- Delete: `ddd-domain-repo-jpa-querydsl/src/main/**`
- Delete: `ddd-domain-repo-jpa-querydsl/src/test/**`

**Interfaces:**
- Consumes: Task 1 build graph with the module no longer included.
- Produces: repository tree with no active Querydsl runtime module.

- [ ] **Step 1: Delete the module tree after path safety verification**

Run the safety check and delete in the same PowerShell session:

```powershell
$target = Resolve-Path "ddd-domain-repo-jpa-querydsl"
$root = Resolve-Path "."
if (-not $target.Path.StartsWith($root.Path)) { throw "Refusing to delete outside worktree: $target" }
Remove-Item -LiteralPath $target.Path -Recurse
```

- [ ] **Step 2: Verify the module tree is gone**

Run:

```powershell
Test-Path "ddd-domain-repo-jpa-querydsl"
```

Expected: `False`.

- [ ] **Step 3: Verify deleted package references are gone from active code and active docs**

Run:

```powershell
rg -n "ddd-domain-repo-jpa-querydsl|com\.only4\.cap4k\.ddd\.domain\.repo\.querydsl|AbstractQuerydslRepository|QuerydslPredicate|QuerydslPredicateSupport" . -g "*.kt" -g "*.kts" -g "*.toml" -g "*.md" --glob "!**/src/test/**" --glob "!**/.superpowers/**" --glob "!**/docs/superpowers/specs/**" --glob "!**/docs/superpowers/plans/**"
```

Expected: no hits in active production, build, or current-document surfaces. Template references are verified in Task 3.

- [ ] **Step 4: Commit checkpoint if commits are authorized**

Run only if the user has authorized commits:

```powershell
git add -A ddd-domain-repo-jpa-querydsl
git commit -m "refactor(cap4k): remove querydsl module"
```

---

### Task 3: Collapse Repository Generation To JPA Only

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes: existing `RepositoryArtifactPlanner.plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>`.
- Produces: repository artifact contexts without `supportQuerydsl`.
- Produces: `aggregate/repository.kt.peb` output with only `JpaRepository`, `JpaSpecificationExecutor`, and `AbstractJpaRepository`.

- [ ] **Step 1: Remove `supportQuerydsl` from planner context**

In `RepositoryArtifactPlanner.kt`, delete this context entry:

```kotlin
"supportQuerydsl" to false,
```

The end of the repository context map should be:

```kotlin
"aggregateName" to aggregateName,
"idType" to idType,
"idTypeFqn" to idTypeFqn,
"imports" to aggregateTypeImports(idType),
```

- [ ] **Step 2: Update aggregate planner tests**

In `AggregateArtifactPlannerTest.kt`, replace repository assertions that read `supportQuerydsl` as a boolean with absence checks:

```kotlin
assertFalse(repository.context.containsKey("supportQuerydsl"))
assertFalse(repositoryContext.containsKey("supportQuerydsl"))
```

Keep schema absence checks for `repositorySupportQuerydsl`:

```kotlin
assertFalse(schema.context.containsKey("repositorySupportQuerydsl"))
assertFalse(schemaContext.containsKey("repositorySupportQuerydsl"))
```

- [ ] **Step 3: Rewrite the default repository template as JPA-only**

In `repository.kt.peb`, remove every `supportQuerydsl` conditional block and use this target structure:

```peb
package {{ packageName }}

{{ use("org.springframework.data.jpa.repository.JpaRepository") -}}
{{ use("org.springframework.data.jpa.repository.JpaSpecificationExecutor") -}}
{{ use("org.springframework.stereotype.Repository") -}}
{% if aggregateElement is defined -%}
{{ use("com.only4.cap4k.ddd.core.annotation.AggregateElement") -}}
{% endif -%}
{{ use("com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository") -}}
{{ use("org.springframework.stereotype.Component") -}}
{%- if entityTypeFqn %}
{{ use(entityTypeFqn) -}}
{%- endif %}
{%- if idTypeFqn %}
{{ use(idTypeFqn) -}}
{%- endif %}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

@Repository
{% if aggregateElement is defined -%}
@AggregateElement(
    aggregate = {{ aggregateElement.aggregateKotlinStringLiteral | raw }},
    name = {{ aggregateElement.nameKotlinStringLiteral | raw }},
    packageName = {{ aggregateElement.packageNameKotlinStringLiteral | raw }},
    description = {{ aggregateElement.descriptionKotlinStringLiteral | raw }},
    type = {{ aggregateElement.typeKotlinStringLiteral | raw }},
    root = {{ aggregateElement.root }}
)
{% endif -%}
interface {{ typeName }} : JpaRepository<{{ entityName }}, {{ idType }}>, JpaSpecificationExecutor<{{ entityName }}> {

    @Component
    class {{ entityName }}JpaRepositoryAdapter(
        jpaSpecificationExecutor: JpaSpecificationExecutor<{{ entityName }}>,
        jpaRepository: JpaRepository<{{ entityName }}, {{ idType }}>
    ) : AbstractJpaRepository<{{ entityName }}, {{ idType }}>(
        jpaSpecificationExecutor,
        jpaRepository
    )
}
```

- [ ] **Step 4: Update renderer fixture contexts**

In `PebbleArtifactRendererTest.kt`, remove all fixture entries shaped like this:

```kotlin
"supportQuerydsl" to false,
```

Do not replace them with another flag.

- [ ] **Step 5: Add JPA-only renderer assertions**

Where repository template content is asserted, keep the JPA assertions and add Querydsl rejection assertions:

```kotlin
assertFalse(content.contains("QuerydslPredicateExecutor"))
assertFalse(content.contains("AbstractQuerydslRepository"))
assertFalse(content.contains("QuerydslRepositoryAdapter"))
```

For the strong-id repository template test, keep this positive assertion:

```kotlin
assertTrue(
    content.contains(
        "interface ContentRepository : JpaRepository<Content, ContentId>, JpaSpecificationExecutor<Content>"
    )
)
```

- [ ] **Step 6: Run focused generator and renderer tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: both commands pass.

- [ ] **Step 7: Commit checkpoint if commits are authorized**

Run only if the user has authorized commits:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "refactor(cap4k): remove querydsl repository generation"
```

---

### Task 4: Final Verification And Handoff Evidence

**Files:**
- Verify: repository root active code, build, templates, tests, and active docs.

**Interfaces:**
- Consumes: Task 1 through Task 3 changes.
- Produces: evidence that the active Querydsl module, active default Querydsl generation branch, and active module inventory references are absent.

- [ ] **Step 1: Run active reference scan**

Run:

```powershell
rg -n "ddd-domain-repo-jpa-querydsl|com\.only4\.cap4k\.ddd\.domain\.repo\.querydsl|AbstractQuerydslRepository|QuerydslPredicate|QuerydslPredicateSupport|supportQuerydsl|repositorySupportQuerydsl|QuerydslPredicateExecutor|com\.querydsl|org\.springframework\.data\.querydsl" . -g "*.kt" -g "*.kts" -g "*.peb" -g "*.toml" -g "*.md" --glob "!**/src/test/**" --glob "!**/.superpowers/**" --glob "!**/docs/superpowers/specs/**" --glob "!**/docs/superpowers/plans/**"
```

Expected: no hits in active production, template, build, or current-document surfaces. Test-only literals are allowed only in negative assertions that verify Querydsl output or context is absent; they do not indicate active support.

- [ ] **Step 2: Run focused build verification**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:compileKotlin
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: all three commands pass.

- [ ] **Step 3: Run broader verification when allowed**

Run only if the implementation turn allows broader Gradle verification:

```powershell
.\gradlew.bat check
```

Expected: pass, or record any failure with module, test class, and first failing assertion/exception.

If `cap4k-ddd-starter:test` fails, compare the failure with the known starter test fixture isolation debt in `AGENTS.md` before classifying it as related to this slice.

- [ ] **Step 4: Record final evidence**

Run:

```powershell
git status --short --branch
git diff --stat
```

Expected: only files from this plan are changed. Record skipped checks explicitly in the handoff summary.
