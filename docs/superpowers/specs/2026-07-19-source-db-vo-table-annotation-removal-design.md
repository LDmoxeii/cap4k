# Source DB Value Object Table Annotation Removal

## Summary

This slice removes `@ValueObject` and `@VO` from the supported DB table annotation contract.

The current value-object input path is `types.valueObjectManifest`. DB schema table comments describe persistence and aggregate structure facts such as aggregate roots, owned child entities, ignored tables, and JPA provider controls. `@Parent=<table>` is the table-level owned child entity signal.

This is a source-db contract cleanup. It does not change value-object manifest loading, value-object generation, value-object render templates, JSON-backed converters, or the generated value-object data class contract.

## Issue

GitHub issue: `#114 source-db: remove @VO table annotation support`

The current source-db path still accepts table-level `@ValueObject` / `@VO` annotations and carries that table flag through source snapshots into canonical entity metadata. That shape gives authors and agents the wrong input surface: DB child tables can look like value-object shapes instead of owned child entities, while real value objects are declared through `types.valueObjectManifest`.

## Evidence In Current Source

Current live surfaces:

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
  - table aliases include `VALUEOBJECT` and `VO`
  - parsing resolves a table `valueObject` presence annotation
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
  - `DbTableSnapshot.valueObject` is populated from table annotation metadata
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - `DbTableSnapshot` exposes `valueObject`
  - `EntityModel` exposes `valueObject`
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - canonical entity metadata copies `table.valueObject`
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
  - `parseTable` also carries table-level value-object annotation parsing
  - focused search shows `parseTable` has no production caller; production table comments are parsed by `DbTableAnnotationParser`
- source-db tests and functional schema fixtures still use `@Parent=...;@VO;`
- `docs/public/reference/db-schema-annotations.md` and `skills/cap4k-generator-inputs/references/db-schema-annotations.md` still list `@ValueObject` / `@VO`

Current value-object support remains separate and must stay available:

- `types.valueObjectManifest`
- `ValueObjectManifestSnapshot`
- `ValueObjectModel`
- `ValueObjectArtifactPlanner`
- `types/value-object` artifact layout and value-object render templates
- public value-object manifest and concept docs

## Goals

- Remove `@ValueObject` and `@VO` from the supported DB table annotation set.
- Treat unsupported table annotations through a generic validation path.
- Keep `@ValueObject` and `@VO` as ordinary unsupported table annotations, not special migration cases.
- Remove source-db `valueObject` table flags from source snapshots and canonical entity metadata.
- Keep `@Parent=<table>` as the owned child entity table signal.
- Keep `types.valueObjectManifest` as the value-object input path.
- Update source-db tests and functional schema fixtures so owned child tables use `@Parent=<table>` without `@VO`.
- Update public DB annotation docs and cap4k skills so they describe only the current supported DB annotation contract.
- Keep `.agents/skills` synchronizable from canonical `cap4k/skills` without local-only wording.

## Non-Goals

- Do not preserve compatibility for `@ValueObject` or `@VO`.
- Do not add a value-object-specific rejection branch or migration diagnostic.
- Do not change value-object manifest generation.
- Do not change generated value-object data classes, nested converters, template ids, or artifact layout.
- Do not reintroduce a runtime `ValueObject` interface.
- Do not solve owned child factory creation inputs.
- Do not solve child entity ID strategy.
- Do not write public docs or skills as a migration story.
- Do not add public docs or skill wording that explains unsupported DB `@VO` behavior.

## Current DB Table Annotation Contract

Supported table annotations after this slice:

- `@Parent=<table>` / `@P=<table>`
- `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
- `@Ignore` / `@I`
- `@DynamicInsert=<bool>`
- `@DynamicUpdate=<bool>`

Current meaning:

- `@Parent=<table>` identifies an owned child entity table.
- Missing `@Parent` means the table defaults to aggregate-root semantics unless `@AggregateRoot=false` is explicitly provided.
- `@AggregateRoot=<bool>` controls aggregate-root metadata where it does not conflict with `@Parent`.
- `@Ignore` excludes a table from generation.
- `@DynamicInsert` and `@DynamicUpdate` carry JPA provider metadata.

Value objects are not table annotations. Value objects are declared through `types.valueObjectManifest`.

## Parser Design

`DbTableAnnotationParser` should own table comment parsing.

The parser should use one generic table annotation validation step:

1. Parse all `@Name` table comment annotations.
2. Validate every parsed table annotation key against the supported table annotation aliases.
3. Fail on unsupported table annotation keys with one generic unsupported-annotation diagnostic.
4. Resolve supported annotations normally.
5. Strip only supported table annotations from `cleanedComment`.

`@ValueObject`, `@VO`, and any other unsupported table annotation should follow the same generic validation path. The implementation should not add a dedicated `if key == "VO"` or `if key == "VALUEOBJECT"` branch.

Existing unsupported table annotation behavior should be reconciled into the same generic validation model where practical. If implementation keeps a more specific diagnostic for currently enforced table annotations such as `@IdGenerator`, it must not introduce a new value-object-specific branch.

## Relation Parser Boundary

`DbRelationAnnotationParser` should only parse column relation annotations.

Its `parseTable` function and `TableRelationMetadata` data class should be removed unless implementation discovers a current production caller. Current evidence shows table parsing is handled by `DbTableAnnotationParser`, while `DbRelationAnnotationParser.parseTable` is covered only by tests.

Table parser tests should move to or remain under `DbTableAnnotationParserTest`. Column relation tests should remain under `DbRelationAnnotationParserTest`.

## Data Model Cleanup

Remove source-db table value-object flags from:

- `DbTableSnapshot.valueObject`
- `EntityModel.valueObject`
- `DbTableAnnotationParseResult.valueObject`
- `TableRelationMetadata.valueObject` if `TableRelationMetadata` remains for any discovered reason

Remove canonical assembly wiring that copies source-db table value-object flags.

Do not remove or rename:

- `ValueObjectModel`
- `ValueObjectManifestSnapshot`
- `CanonicalModel.valueObjects`
- `ProjectConfig.typeRegistry.valueObjectManifestFiles`
- `ProjectConfig.artifactLayout.valueObject`

Those belong to the current manifest-driven value-object contract.

## Test And Fixture Updates

Source-db parser tests:

- Add or update coverage for generic unsupported table annotations.
- Verify `@ValueObject` and `@VO` fail through the generic unsupported table annotation path.
- Verify supported table annotations still parse correctly.
- Remove tests that assert `valueObject = true`.

DB schema source tests:

- Replace `@Parent=...;@VO;` table comments with `@Parent=...;`.
- Assert child ownership through `parentTable`, `aggregateRoot = false`, and cleaned comments.
- Remove assertions against `DbTableSnapshot.valueObject`.

Core canonical assembler tests:

- Remove `valueObject = true` from DB table test fixtures.
- Remove assertions against `EntityModel.valueObject`.
- Keep owned child entity assertions through `aggregateRoot = false` and `parentEntityName`.
- Keep manifest-driven value-object assertions through `CanonicalModel.valueObjects`.

Functional schema fixtures:

- Remove `@VO` from schema SQL files.
- Keep `@Parent=<table>` where tables are owned child entities.
- Keep manifest files and value-object manifest compile samples unchanged unless a direct assertion requires local cleanup.

## Public Docs And Skills

Public docs and skills must describe only the current supported state.

Required updates:

- `docs/public/reference/db-schema-annotations.md`
- `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- synchronized `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`

Rules:

- Do not list `@ValueObject` or `@VO` as DB schema annotations.
- Do not explain that DB `@VO` was removed.
- Do not write a migration story.
- Do not add historical notes.
- Do state that value-object inputs use `types.valueObjectManifest` where the document needs to distinguish table annotations from value-type inputs.
- Do state that owned child entity tables use `@Parent=<table>`.

Existing public value-object manifest docs should remain present-tense and should continue to describe manifest-driven value-object inputs.

## Validation

Static validation:

```powershell
rg -n "@ValueObject|@VO|\\bVO\\b" cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-gradle/src/test/resources/functional docs/public skills .agents/skills --glob "!**/build/**"
```

Expected after implementation:

- no supported DB schema annotation docs or skills list `@ValueObject` / `@VO`
- no source-db tests or functional schema fixtures use `@VO`
- code may still contain test literals only when asserting generic unsupported annotation behavior

Static value-object support preservation:

```powershell
rg -n "types\\.valueObjectManifest|ValueObjectModel|ValueObjectArtifactPlanner|value_object\\.kt\\.peb|family = \"value-object\"" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble docs/public --glob "!**/build/**"
```

Expected after implementation: matches remain.

Focused test candidates:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-source-db:test
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Run compile or tests only in an implementation turn where command side effects are allowed.

## Acceptance Criteria

- `@ValueObject` and `@VO` are not supported DB table annotations.
- Unsupported table annotations fail through a generic unsupported annotation validation path.
- There is no value-object-specific parser branch for rejecting `@ValueObject` or `@VO`.
- DB table parsing is owned by `DbTableAnnotationParser`.
- `DbRelationAnnotationParser` no longer owns table annotation parsing unless a current production caller is proven.
- `DbTableSnapshot` no longer exposes a source-db `valueObject` table flag.
- `EntityModel` no longer exposes a source-db `valueObject` table flag.
- Source-db tests and functional schema fixtures no longer model owned child tables with `@VO`.
- Public DB annotation docs no longer list `@ValueObject` or `@VO`.
- cap4k skills no longer route agents toward `@VO`.
- `.agents/skills` DB schema annotation wording can be synchronized from canonical `cap4k/skills`.
- Manifest-driven value-object support remains intact.
