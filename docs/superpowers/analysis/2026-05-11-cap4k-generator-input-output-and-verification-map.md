# cap4k Generator Input, Output, And Verification Map

Date: 2026-05-11

This file maps generator inputs, generated artifacts, and review checks for business-project authoring.

## Source Providers

| Source | DSL key | Main use |
|---|---|---|
| DB schema | `sources.db` | Aggregate model, relations, enums, repositories, schema metadata, factories/specifications/unique helpers |
| Design JSON | `sources.designJson` | Commands, queries, clients, payloads, validators, domain events, integration events, handlers |
| Enum manifest | `sources.enumManifest` | Shared enum definitions referenced by DB `@Type` |
| KSP metadata | `sources.kspMetadata` | Aggregate metadata for design-driven artifacts |
| IR analysis | `sources.irAnalysis` | Flow and drawing-board analysis artifacts |
| Type registry | `types.registryFile` | Simple-name type bindings and converter policy loaded into `ProjectConfig.typeRegistry` for canonical type resolution |

`buildSourceRunner` registers `db`, `enum-manifest`, `design-json`, and `ksp-metadata` as source-generation providers. `buildAnalysisRunner` registers `ir-analysis` separately for `cap4kAnalysisPlan` and `cap4kAnalysisGenerate`.

`types.registryFile` is outside `sources {}` and lives under `types {}` in the DSL, but it is still part of the source-generation input contract: `Cap4kProjectConfigFactory` loads it into `ProjectConfig.typeRegistry`, `generatedSourceTaskInputFiles(...)` includes the file as a tracked generation input, and `generatedSourceTaskInputSnapshot(...)` records the resolved registry in the task input snapshot.

## DB Annotation Surface

Table comment annotations:

| Annotation | Meaning |
|---|---|
| `@Parent` / `@P=<table>` | Child table belongs to parent aggregate |
| `@AggregateRoot` / `@Root` / `@R=true|false` | Explicit aggregate-root marker |
| `@ValueObject` / `@VO` | Value-object table marker |
| `@Ignore` / `@I` | Ignore table |
| `@DynamicInsert=true|false` | Provider dynamic insert |
| `@DynamicUpdate=true|false` | Provider dynamic update |

Column comment annotations:

| Annotation | Meaning |
|---|---|
| `@Type` / `@T=<TypeName>` | Bind DB column to named domain type/enum |
| `@Enum` / `@E=0:NAME:Desc|...` | Inline enum items; requires `@T` |
| `@GeneratedValue` | Use default ID generation |
| `@GeneratedValue=uuid7` | UUID7 strategy |
| `@GeneratedValue=snowflake-long` | Snowflake long strategy |
| `@GeneratedValue=identity` | Database identity strategy |
| `@Deleted` | Soft delete marker |
| `@Version` | Optimistic lock marker |
| `@Managed` | Framework-managed field |
| `@Exposed` | Exposed field |
| `@Insertable=true|false` | JPA insertability |
| `@Updatable=true|false` | JPA updatability |

Relation annotations:

| Annotation | Meaning |
|---|---|
| `@Reference` / `@Ref=<table>` | Reference target table |
| `@Relation` / `@Rel=ManyToOne|OneToOne|*:1|1:1` | Relation type |
| `@Lazy` / `@L=true|false` | Lazy loading |
| `@Count` / `@C=<hint>` | Count hint |

Rules:

- `@Relation`, `@Lazy`, and `@Count` require `@Reference`.
- Many-to-many is unsupported.
- Legacy `@IdGenerator` and `@SoftDeleteColumn` are rejected.
- `@Managed` and `@Exposed` are mutually exclusive.
- Unique constraints are the source for aggregate unique helper generation; names should be stable and meaningful.

## Design JSON Surface

Supported `tag` values:

| Tag | Generated family |
|---|---|
| `command` | command request/response/handler skeleton |
| `query` | query request/response |
| `client` | external client/cli request |
| `api_payload` | adapter API payload |
| `domain_event` | domain event payload |
| `integration_event` | application integration event contract and inbound subscriber skeleton |
| `validator` | validation annotation and validator |

Common fields include `package`, `name`, `desc`, `aggregates`, `requestFields`, and `responseFields`.

Additional support:

- `query` and `api_payload` support request trait `page`.
- `domain_event` supports `persist`.
- `domain_event` can omit package and can use reserved request field `entity`.
- `integration_event` requires `role` (`inbound` or `outbound`) and non-blank `eventName`.
- `integration_event` must declare at least one request field; request fields become the event payload. `responseFields` must be empty.
- `integration_event` with `role = inbound` can generate a Spring `@EventListener` subscriber; `role = outbound` generates only the event contract.
- `validator` supports `message`, `targets`, `valueType`, and `parameters`.
- Manifest-file mode reads a list of design files relative to the manifest and rejects path escape/duplicates.

Unsupported design tags today:

- `value_object`
- `domain_service`

These unsupported tags are important future backlog candidates because business authors want value objects and domain services to be as explicit as the supported command, query, client, domain event, and integration event contracts.

## Enum Manifest

Enum manifest is a JSON array with:

- `name`
- `package`
- `items[]` containing `value`, `name`, `desc`

It is used with DB `@T=<TypeName>`. Duplicate type names are rejected.

`generateTranslation` has been removed from enum manifest. Enum translation belongs to addon generation, not core aggregate generation.

## Type Registry

`types.registryFile` must point to a JSON object keyed by simple type name. Each value must provide:

- `fqn`;
- optional `converter`, which may be `false`, `"nested"`, or a converter FQN.

The config loader rejects blank keys, dotted keys, built-in type overrides, duplicate names after normalization, duplicate fields, unsupported fields, and non-FQN values. During canonical assembly, `CanonicalEnumCatalog` uses this registry when resolving field type bindings and rejects collisions with shared enums or local enums that use the same simple name.

## Aggregate Outputs

Built-in aggregate planning covers:

- entity;
- behavior;
- schema;
- repository;
- factory;
- specification;
- unique query;
- unique query handler;
- unique validator;
- shared enum;
- local enum.

Built-in aggregate projection planning is separate from aggregate generation:

- DSL block: `generators.aggregateProjection.enabled`;
- default: disabled;
- source requirement: enabled `sources.db`;
- generator id: `aggregate-projection`;
- output root: adapter module `build/generated/cap4k/main/kotlin`;
- fixed package root: `<basePackage>.adapter.application.projections`;
- template id: `aggregate_projection/entity.kt.peb`.

The built-in template emits JPA-flavored scalar projection classes only. It exposes relation metadata in template context for overrides, but it does not render relation object graph fields by default.

Output ownership:

| Output kind | Root | Conflict policy |
|---|---|---|
| `GENERATED_SOURCE` | module `build/generated/cap4k/main/kotlin` | `OVERWRITE` |
| `CHECKED_IN_SOURCE` | configured source package roots | normally `SKIP` |

Important artifact boundaries:

- Entity/schema/repository are generated source when produced by `cap4kGenerateSources`.
- Aggregate projection classes are generated source under the adapter module when `aggregateProjection` is enabled.
- Behavior is checked-in author surface and fixed to `SKIP`.
- Factory and specification are optional checked-in skeletons and should be `SKIP` when intended for human implementation.
- Factory generation is important for aggregate-root creation; only aggregate roots should have factories.
- Specification should not be generated if the project does not intend to use it.
- Unique helpers should be generated only when the DB uniqueness contract needs a first-class query/validator shape.

## Design Outputs

Design generator families:

- `designCommand`
- `designQuery`
- `designQueryHandler`
- `designClient`
- `designClientHandler`
- `designValidator`
- `designApiPayload`
- `designDomainEvent`
- `designDomainEventHandler`
- `designIntegrationEvent`
- `designIntegrationEventSubscriber`

Handler skeletons are author-maintained code. If generated into active source roots, their conflict policy should preserve user edits after the first generation.

Integration event contracts are generated under `<basePackage>.application.subscribers.integration.<role>.<designPackage>`. Subscriber skeletons are generated only for inbound events under `<basePackage>.application.subscribers.integration` and use Spring `@EventListener`; outbound events expose the contract but do not subscribe to themselves.

## Addon Outputs

Artifact addons are not business-modeling sources and do not participate in `SourceProvider.collect(...)`. They are loaded separately and receive `ArtifactAddonContext(config, model, options)` after canonical assembly.

Addon plan items still appear in `cap4kPlan` as normal `ArtifactPlanItem`s. In `DefaultPipelineRunner`, built-in planner items and addon planner items are merged, then reviewed through the same ownership fields:

- `generatorId`;
- `templateId`;
- `outputPath`;
- `outputKind`;
- `conflictPolicy`;
- `resolvedOutputRoot`.

Conflict-policy handling is also shared: generated-source items resolve to `OVERWRITE`; checked-in items use template-level overrides when present, otherwise the emitted item policy.

## Verification Workflow

Before writing files:

1. Run the matching plan task, usually `cap4kPlan` or `cap4kBootstrapPlan`.
2. Inspect each plan item for `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
3. Check whether the target is build-owned generated source or checked-in source.

After writing files:

1. Run compile/tests for affected modules.
2. Run `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` when analysis output is part of the example or review.
3. For generated snapshots, copy from build-owned `build/generated/cap4k/main/kotlin` into `src-generated/main/kotlin` only as an audit/learning snapshot.

Snapshot rule:

- `src-generated/main/kotlin` is not the active generator output path.
- Active generated code lives under each module's `build/generated/cap4k/main/kotlin`.
- Snapshot copy tasks are project-specific teaching aids and should be explicitly named as such.

## Boundary Fallback

The current repository implementation workflows separate missing generation inputs from missing generator-capable skeletons.

Return to `cap4k-generation` when the business facts already exist but a supported generated surface is missing, including:

- `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, client, validator, payload, domain event, integration event, or subscriber skeletons;
- aggregate, repository, factory, specification, enum, relation, field-mapping, or unique-helper skeletons after DDL / enum / type-registry facts already exist.

Return to `cap4k-modeling` when the missing piece is the fact contract generation depends on, including:

- a design entry;
- a DDL annotation or DDL contract;
- an enum manifest entry;
- a `types.registryFile` entry.

Return to `cap4k-generation` / compile / setup when the business facts already exist but the design-generation pipeline is missing required derived output or setup, including:

- KSP metadata output;
- KSP metadata configuration;
- the compile/setup path that produces KSP metadata for design generation.

## Reference-Project Checks

A business reference project should demonstrate:

- DB annotations for enum, relation, generated ID, version, soft delete, managed fields, and uniqueness when used;
- design JSON for command/query/client/api_payload/domain_event/integration_event/handler families;
- `cap4kGenerateSources` for build-owned source;
- optional `aggregateProjection` output when the project wants generated adapter read models;
- checked-in handlers/factories as user-maintained code;
- generated snapshot copy only for audit;
- analysis output generated after compile.
