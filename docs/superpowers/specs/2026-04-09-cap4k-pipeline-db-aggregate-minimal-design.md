# Cap4k Pipeline DB Aggregate Minimal Slice

## Summary

This design defines the next implementation slice after the existing `design-json + ksp-metadata + design generator` vertical slice.

The new slice adds a database-driven aggregate path to the new pipeline architecture with the smallest scope that still proves the architecture works for aggregate generation.

The slice includes:

- one new source module: `cap4k-plugin-pipeline-source-db`
- one new generator module: `cap4k-plugin-pipeline-generator-aggregate`
- three aggregate artifact planners:
  - schema
  - entity
  - repository
- canonical model expansion in `pipeline-api` and `pipeline-core`
- fixture-based tests and Gradle TestKit coverage only

The slice explicitly does not include a real project smoke run or the higher-complexity aggregate generators from the legacy system.

## Goals

- Add a database-backed source to the new pipeline architecture.
- Prove that aggregate-oriented artifacts can be planned from canonical models instead of task-local mutable context.
- Generate three minimal artifact families from DB schema metadata:
  - `Schema`
  - `Entity`
  - `Repository`
- Keep source, canonical assembly, planning, rendering, and Gradle wiring separate.
- Validate the slice with fixtures, module tests, and TestKit only.

## Non-Goals

- Do not migrate `Factory`, `Specification`, `AggregateWrapper`, `DomainEvent`, `UniqueQuery`, or `UniqueValidator`.
- Do not reproduce the legacy `SqlSchemaUtils.context` global mutable design inside the new pipeline.
- Do not support relation-heavy aggregate modeling in this slice.
- Do not add repository-user configuration for enabling/disabling aggregate planners yet.
- Do not run the slice against `only-danmuku` or any real database owned by an application project.

## Scope

This slice covers one narrow path:

`db source -> DbSchemaSnapshot -> canonical aggregate model -> aggregate planners -> Pebble render -> TestKit fixture`

It is intentionally narrower than the legacy `GenAggregateTask`.

## Architecture

### Source Layer

Add `cap4k-plugin-pipeline-source-db`.

Responsibility:

- connect to a configured JDBC database
- inspect table, column, primary key, comment, and unique-constraint metadata
- map DB metadata into a raw `DbSchemaSnapshot`

Constraints:

- no package inference in the source layer
- no template context generation in the source layer
- no planner logic in the source layer

### Canonical Layer

Expand `CanonicalModel` so aggregate planners consume canonical objects, not raw JDBC results.

Minimum canonical additions:

- `SchemaModel`
- `EntityModel`
- `RepositoryModel`

The canonical assembler owns the translation from `DbSchemaSnapshot` to these planner-facing models.

### Planning Layer

Add `cap4k-plugin-pipeline-generator-aggregate`.

Responsibility:

- convert aggregate-oriented canonical models into `ArtifactPlanItem`
- keep one planner per artifact family:
  - `SchemaArtifactPlanner`
  - `EntityArtifactPlanner`
  - `RepositoryArtifactPlanner`

Constraints:

- planners do not talk to JDBC
- planners do not resolve templates directly
- planners do not write files

### Rendering Layer

Reuse `cap4k-plugin-pipeline-renderer-pebble`.

Add aggregate preset templates under:

- `presets/ddd-default/aggregate/schema.kt.peb`
- `presets/ddd-default/aggregate/entity.kt.peb`
- `presets/ddd-default/aggregate/repository.kt.peb`

These templates should stay intentionally simple in this slice. They only need enough structure to prove the planners and canonical model are correct.

### Gradle Layer

Do not broaden the public Gradle DSL yet.

Use a fixture-only Gradle TestKit path to prove:

- DB source can be configured
- aggregate planners are wired into the runner
- `cap4kPlan` and `cap4kGenerate` work for the aggregate path

## Data Model

### Source Snapshot

Add `DbSchemaSnapshot` to `pipeline-api`.

Recommended minimum shape:

- `DbSchemaSnapshot`
  - `tables: List<DbTableSnapshot>`
- `DbTableSnapshot`
  - `tableName`
  - `comment`
  - `columns`
  - `primaryKey`
  - `uniqueConstraints`
- `DbColumnSnapshot`
  - `name`
  - `dbType`
  - `kotlinType`
  - `nullable`
  - `defaultValue`
  - `comment`
  - `isPrimaryKey`

This snapshot represents raw database structure only.

### Canonical Model

Add the following minimum planner-facing structures:

- `SchemaModel`
  - `name`
  - `packageName`
  - `entityName`
  - `comment`
  - `fields`
- `EntityModel`
  - `name`
  - `packageName`
  - `tableName`
  - `comment`
  - `fields`
  - `idField`
- `RepositoryModel`
  - `name`
  - `packageName`
  - `entityName`
  - `idType`

The assembler should derive names and package roles from a fixed strategy, not from template logic.

## Naming Strategy

For this minimal slice, naming should be deterministic and simple:

- table `video_post` -> entity `VideoPost`
- schema type -> `SVideoPost`
- repository type -> `VideoPostRepository`

Package roles should be fixed by artifact family, not inferred dynamically from user scripts:

- schema -> domain share/meta package
- entity -> domain aggregate/entity package
- repository -> domain repository package

The exact package strings can be refined in implementation, but the strategy must stay fixed and testable.

## Testing Strategy

### Source Tests

Use an in-memory JDBC fixture database.

Verify:

- table metadata is read
- column metadata is read
- primary key is detected
- unique constraints are detected
- Kotlin type mapping is stable for the supported test types

### Core Tests

Expand `DefaultCanonicalAssemblerTest`.

Verify:

- `DbSchemaSnapshot` becomes the expected canonical aggregate models
- entity, schema, and repository names are stable
- unsupported or malformed tables fail clearly when necessary

### Planner Tests

Each aggregate planner gets its own test coverage.

Verify:

- generated `ArtifactPlanItem.templateId`
- output path
- package name context
- type names
- id type propagation for repository planning

### Renderer Tests

Add render coverage for the three aggregate templates to ensure:

- package declarations render correctly
- basic field lists render correctly
- repository/entity/schema names remain stable

### Gradle Tests

Use a new TestKit fixture that creates an in-memory schema during build execution.

Verify:

- `cap4kPlan` contains aggregate artifact plans
- `cap4kGenerate` writes schema/entity/repository files

## Risks

### Risk 1: Reintroducing legacy mutable context

If the implementation reuses legacy aggregate-generation internals too directly, the new pipeline will inherit the same coupling problems it is meant to remove.

Mitigation:

- keep JDBC reading in the source module only
- keep canonical assembly explicit
- keep planners stateless

### Risk 2: Over-scoping into legacy parity

The legacy aggregate path contains many more generators than this slice needs.

Mitigation:

- stop strictly at `Schema`, `Entity`, and `Repository`
- defer all higher-order aggregate generation to follow-up plans

### Risk 3: Naming/package drift

Aggregate generation is sensitive to package and type naming rules.

Mitigation:

- encode naming rules in tests
- keep naming fixed and explicit in this slice

## Follow-up After This Slice

If this minimal slice is stable, the next aggregate-focused increments should be:

1. richer canonical modeling for relations and child entities
2. additional aggregate planners:
   - `Factory`
   - `Specification`
   - `AggregateWrapper`
3. eventually the legacy advanced generators:
   - `DomainEvent`
   - `UniqueQuery`
   - `UniqueValidator`

This ordering preserves the architecture while avoiding a one-shot port of the old aggregate subsystem.
