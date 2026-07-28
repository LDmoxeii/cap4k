# Cap4k Querydsl Module Removal

## Summary

Delete `ddd-domain-repo-jpa-querydsl` as a Kotlin-first runtime slimming slice.
The module is no longer part of the default cap4k story and should stop being included in the Gradle build, stop being exposed through `cap4k-ddd-starter`, and stop being referenced by default aggregate repository templates.

This is a breaking removal for consumers that directly import `com.only4.cap4k.ddd.domain.repo.querydsl.*` or rely on the starter to make the Querydsl repository artifact available. It should be handled as an explicit removal slice, not as a side effect of wrapper cleanup, Strong ID work, Snowflake cleanup, or JPA repository redesign.

## Decision Context

This design is based on the 2026-07-23 static impact audit of the current `master` checkout and the existing Kotlin-first direction in prior specs:

- `2026-05-22-cap4k-strong-id-1-0-design.md` states that QueryDSL is not a core Kotlin-first cap4k capability and can be removed in a later slimming slice.
- `2026-07-07-cap4k-runtime-aggregate-wrapper-removal-design.md` treats `ddd-domain-repo-jpa-querydsl` as a high-priority slimming candidate and says that, if Querydsl removal is accepted first, it should delete the Querydsl module and default starter exposure in its own slice.
- The pipeline redesign keeps generator stages developer-owned and does not require historical compatibility branches to remain in default templates.

No GitHub issue matching `querydsl` or `ddd-domain-repo-jpa-querydsl` was found during the audit. This spec therefore records the local repository evidence and the proposed removal boundary so a later issue or implementation plan can reference one stable design contract.

## Current Evidence

### Direct Build References

- `settings.gradle.kts` includes `ddd-domain-repo-jpa-querydsl`; deleting only the directory would fail Gradle configuration.
- `cap4k-ddd-starter/build.gradle.kts` exposes the module with `api(project(":ddd-domain-repo-jpa-querydsl"))` and repeats it as a test dependency.
- `gradle/libs.versions.toml` defines `querydsl = { module = "com.querydsl:querydsl-core" }`; the alias is only needed by the Querydsl module in the current repository scan.

### Source References

- Outside `ddd-domain-repo-jpa-querydsl`, no Kotlin or Java production source imports `com.only4.cap4k.ddd.domain.repo.querydsl.*`.
- `ddd-core` and `ddd-domain-repo-jpa` do not hard-code Querydsl classes.
- `DefaultRepositorySupervisor` is registry-based. Querydsl support is registered by `AbstractQuerydslRepository.init()` only when a Querydsl repository bean exists.
- Removing the module therefore removes Querydsl predicate dispatch, but does not remove ordinary JPA repository dispatch.

### Generator References

- `RepositoryArtifactPlanner` currently sets `supportQuerydsl` to `false` for repository artifact context.
- `cap4k-plugin-pipeline-renderer-pebble` still contains a `supportQuerydsl` conditional branch in `aggregate/repository.kt.peb` that imports `QuerydslPredicateExecutor` and `AbstractQuerydslRepository` when enabled.
- Current default generation should remain JPA-only, but keeping a dead `supportQuerydsl` branch would preserve an unsupported render path after the module is deleted.

### Documentation References

- `README.md` and `README.en.md` do not present Querydsl as a current public module.
- Historical specs and plans under `docs/superpowers/**` mention Querydsl by design and do not need broad rewriting.
- Active analysis or architecture maps that enumerate current modules should be updated if the module is removed.

## Goals

1. Remove `ddd-domain-repo-jpa-querydsl` from the active Gradle build.
2. Remove `ddd-domain-repo-jpa-querydsl` from `cap4k-ddd-starter` API and test dependencies.
3. Delete the Querydsl module source, tests, and module-level build file.
4. Remove the default aggregate repository template branch that can generate Querydsl repository adapters.
5. Remove the generator context key and tests that preserve `supportQuerydsl` as a default repository flag.
6. Keep the generated default repository path based on `JpaRepository`, `JpaSpecificationExecutor`, `AbstractJpaRepository`, and `JpaPredicate`.
7. Document the breaking impact for external consumers and the migration direction to JPA specifications.

## Non-Goals

- Do not remove `ddd-domain-repo-jpa`.
- Do not remove Spring Data JPA `Specification` support.
- Do not redesign `Repository`, `RepositorySupervisor`, `AbstractJpaRepository`, `JpaPredicate`, or aggregate load-plan behavior.
- Do not remove Snowflake support in this slice.
- Do not reopen aggregate wrapper API cleanup in this slice.
- Do not introduce Jimmer, Blaze Persistence, or another query backend as a replacement.
- Do not preserve a compatibility artifact or empty shim for `ddd-domain-repo-jpa-querydsl` unless release management explicitly asks for a staged deprecation release.

## Required Change Boundary

### Gradle Settings

Update `settings.gradle.kts`:

- remove `ddd-domain-repo-jpa-querydsl` from the include list
- keep `ddd-domain-event-jpa` and `ddd-domain-repo-jpa` included

Expected direction:

```kotlin
include("ddd-domain-event-jpa", "ddd-domain-repo-jpa")
```

### Starter Dependencies

Update `cap4k-ddd-starter/build.gradle.kts`:

- remove `api(project(":ddd-domain-repo-jpa-querydsl"))`
- remove `testImplementation(project(":ddd-domain-repo-jpa-querydsl"))`
- keep `api(project(":ddd-domain-repo-jpa"))`

This changes the starter compile classpath and published metadata. It is the main intentional external break.

### Module Deletion

Delete:

- `ddd-domain-repo-jpa-querydsl/build.gradle.kts`
- `ddd-domain-repo-jpa-querydsl/src/main/**`
- `ddd-domain-repo-jpa-querydsl/src/test/**`

The deleted public types are:

- `com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository`
- `com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate`
- `com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicateSupport`

### Version Catalog

Update `gradle/libs.versions.toml`:

- remove the `querydsl` alias if no other module starts using it before implementation
- do not remove unrelated Spring Data JPA or Jakarta Persistence aliases

### Aggregate Repository Generation

Update `cap4k-plugin-pipeline-generator-aggregate`:

- remove `supportQuerydsl` from repository artifact context
- update planner tests that assert `supportQuerydsl == false` so they instead assert the absence of a Querydsl branch or simply focus on the JPA repository context
- keep repository planning ownership in Kotlin planner code, not in Pebble template conditionals

Update `cap4k-plugin-pipeline-renderer-pebble`:

- remove `supportQuerydsl` conditionals from `presets/ddd-default/aggregate/repository.kt.peb`
- remove imports of `org.springframework.data.querydsl.QuerydslPredicateExecutor` and `com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository` from that template
- remove generation of `*QuerydslRepositoryAdapter`
- keep generated repository interfaces extending only `JpaRepository` and `JpaSpecificationExecutor`
- update renderer tests that carry `supportQuerydsl` in fixture contexts

### Active Documentation

Update active documentation in the same implementation PR when it presents module inventory or supported repository capability:

- active architecture maps must reflect the current module set and should not list `ddd-domain-repo-jpa-querydsl`
- active docs should describe the current supported repository surface: JPA repositories, Spring Data specifications, `JpaPredicate`, and `AbstractJpaRepository`
- active docs and release-facing text must not explain historical transitions; use present-tense capability statements
- historical specs and plans may remain unchanged when they are not surfaced as current user guidance

## Behavioral Contract

### Repository Runtime

- `JpaPredicate` remains the supported runtime predicate for JPA repositories.
- `AbstractJpaRepository` remains the generated adapter base class.
- `DefaultRepositorySupervisor` still resolves repositories through registered predicate and repository reflectors.
- No Querydsl predicate reflector or Querydsl repository reflector is registered by cap4k.
- Querydsl predicate classes are outside the current cap4k runtime and documentation surface.

### Generated Repository Output

Generated aggregate repositories should be JPA-only:

```kotlin
interface ExampleRepository : JpaRepository<ExampleEntity, ExampleId>, JpaSpecificationExecutor<ExampleEntity> {
    @Component
    class ExampleEntityJpaRepositoryAdapter(
        jpaSpecificationExecutor: JpaSpecificationExecutor<ExampleEntity>,
        jpaRepository: JpaRepository<ExampleEntity, ExampleId>
    ) : AbstractJpaRepository<ExampleEntity, ExampleId>(
        jpaSpecificationExecutor,
        jpaRepository,
    )
}
```

The generated output must not import or implement `QuerydslPredicateExecutor`, and must not create a `*QuerydslRepositoryAdapter`.

### Consumer Guidance

Current consumer-facing guidance should be JPA-first and present tense:

- use `JpaPredicate.bySpecification(entityClass, specification)` for specification-backed repository queries
- use generated or handwritten `AbstractJpaRepository` adapters for cap4k-managed JPA repositories
- keep any project-local Querydsl infrastructure outside cap4k ownership

Do not add a Querydsl-to-JPA migration example to current docs or release notes for this slice.

## Release And Communication Decisions

- Release this as a single breaking release; do not stage it with a deprecation release.
- Do not add a migration example for this slice.
- Update active architecture maps in the same implementation PR.
- User-facing docs should state the current supported repository model. Prefer wording such as `cap4k repository support is JPA Specification-based` over historical transition wording.
- If release notes are required, keep them current-state and terse: list the supported module surface and avoid explaining historical paths.

## Verification Plan For Implementation

Run static scans first:

```powershell
rg -n "ddd-domain-repo-jpa-querydsl|com\.only4\.cap4k\.ddd\.domain\.repo\.querydsl|AbstractQuerydslRepository|QuerydslPredicate|QuerydslPredicateSupport|supportQuerydsl|repositorySupportQuerydsl|QuerydslPredicateExecutor|com\.querydsl|org\.springframework\.data\.querydsl" . -g "*.kt" -g "*.kts" -g "*.peb" -g "*.toml" -g "*.md" --glob "!**/src/test/**" --glob "!**/.superpowers/**" --glob "!**/docs/superpowers/specs/**" --glob "!**/docs/superpowers/plans/**"
```

Expected post-removal result:

- no active production, template, build, or current-document references to `ddd-domain-repo-jpa-querydsl`
- no active production or template references to `com.only4.cap4k.ddd.domain.repo.querydsl`
- no active default template branch for `supportQuerydsl`
- test-only literals are allowed only in negative assertions that verify Querydsl output or context is absent; they do not indicate active support

Run focused Gradle checks:

```powershell
.\\gradlew.bat :cap4k-ddd-starter:compileKotlin
.\\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
.\\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

If implementation changes only build/module/template surfaces and those focused checks pass, run broader build verification as allowed by current branch policy:

```powershell
.\\gradlew.bat check
```

If full `check` fails in `cap4k-ddd-starter:test`, first compare the failure shape with the known starter test fixture isolation debt in `AGENTS.md`. Do not classify that known fixture debt as a Querydsl removal regression unless the failing stack or assertions are newly tied to Querydsl removal.

## Rollback Target

Rollback to this spec if any of these assumptions are invalidated during implementation:

- a production module outside `cap4k-ddd-starter` directly requires the Querydsl module
- generated default repository output still needs Querydsl for a supported Kotlin-first path
- a release-governance decision explicitly overrides the single breaking release decision
- removing `supportQuerydsl` breaks a non-historical generator contract that is still intentionally supported

If such evidence appears, stop deleting forward and update this design boundary before changing more files.

## Fixed Decisions

1. Release this as a single breaking release.
2. Do not write or publish a migration example for this slice.
3. Update active architecture maps in the same implementation PR.
4. For documentation changes, state only the current supported capability and avoid historical transition notes.

## Proposed Implementation Order

1. Remove starter and settings Gradle references.
2. Delete `ddd-domain-repo-jpa-querydsl`.
3. Remove Querydsl version catalog alias if unused.
4. Remove generator and template Querydsl branches.
5. Update focused generator and renderer tests.
6. Update active module inventory docs and any required current-state release notes in the same implementation PR.
7. Run the verification plan and record skipped checks explicitly.
