# cap4k Issue 92 Artifact Metadata And Recovery Contract Design

Date: 2026-05-29

Status: Proposed

Scope: implement #92 as the generator artifact metadata and recovery contract cleanup across cap4k pipeline APIs, design-json source, canonical assembly, built-in design/type/aggregate planners, drawing-board output, flow output, code-analysis recovery, and ddd-core concept annotations.

## Backlog Source

This design covers:

- #92: generator: unify artifact metadata and drawing-board input recovery.

This design updates the issue's current body where the brainstorming result deliberately changed earlier issue wording:

- `domain-event-handler` is renamed to public family `domain-subscriber`.
- `@Aggregate` is deleted rather than only retired from generator metadata.
- Runtime `archinfo` is removed rather than migrated.
- `@AggregateElement` is introduced for DB aggregate source derived concept metadata.
- Drawing-board and flow are both analysis/observation outputs driven by `irAnalysis.inputDirs` and default to `OVERWRITE`.
- `SourceConfig.enabled` and `GeneratorConfig.enabled` are removed from the public pipeline config model.

## Positioning

The cap4k generator produces auditable DDD tactical skeletons and structural wiring. It does not synthesize business decisions.

The generator standardizes concept family, package placement, naming, runtime annotations and interfaces, request and response carrier shape, generated-code ownership, conflict policy, plan visibility, and recovery metadata. It must not infer domain state transitions, release policy rules, Saga step order, compensation behavior, retry or recovery strategy, external callback workflow, or cross-artifact business annotations.

Generated skeletons should be self-describing as cap4k concepts. The generator is a primary producer of that metadata, but it is not the only possible producer: users may handwrite top-level skeletons and annotate them. Code analysis, drawing-board, flow, and future tools are consumers. The current iteration keeps these concept annotations `BINARY` and does not make them runtime framework inputs.

## Chosen Approach

Use a direct contract migration with no compatibility period for the old variant fields.

Public authoring source language uses semantic `tag` plus optional artifact selection:

```json
{
  "tag": "query",
  "package": "order.read",
  "name": "FindOrderPage",
  "description": "Find order page",
  "aggregates": ["Order"],
  "artifacts": [
    { "family": "query", "variant": "page" },
    { "family": "query-handler" }
  ],
  "fields": [
    { "name": "keyword", "type": "String", "nullable": true }
  ],
  "resultFields": [
    { "name": "orderNo", "type": "String" }
  ]
}
```

The pipeline keeps source ownership separate:

- `design-json` declares core skeleton blocks and artifact selection.
- `enum-manifest` declares enum types.
- `value-object-manifest` declares value-object types.
- `db` declares aggregate, persistence, relation, unique, strong-id, schema, projection, and DB-derived type facts.
- `ir-analysis` is an observation input, not an authoring source.
- `ksp-metadata` is not redesigned in #92 because it is planned for removal.

The canonical model changes asymmetrically:

- `design-json` becomes artifact-selection-native through unified `designBlocks`.
- `enum-manifest` and `value-object-manifest` keep typed canonical models but migrate ownership fields to `aggregates`.
- DB aggregate canonical models remain typed.

## Non-Goals

- No compatibility period for `role`, `traits`, `scope`, `desc`, `requestFields`, or `responseFields` in the public authoring/recovery contract.
- No public renderer template IDs or `.peb` paths in design-json or drawing-board JSON.
- No business logic recovery from code.
- No addon artifact selection or addon-generated code analysis recovery in #92.
- No DB or DDL recovery from drawing-board output.
- No broad flow redesign beyond input-driven execution and overwrite behavior.
- No migration of runtime `archinfo` to new annotations.
- No retention of old `@Aggregate` under a reduced meaning.

## Public Design Block Contract

Design-json files remain top-level JSON arrays. Each element is a design block. Drawing-board files and `design-elements.json` use the same array schema.

Public JSON field names are:

| Field | Meaning |
| --- | --- |
| `tag` | Semantic authoring type. Uses existing snake_case tag names. |
| `package` | Authoring-relative package segment. Kotlin models may keep `packageName` internally. |
| `name` | Block name without generated suffix inference. |
| `description` | Human-readable description. Replaces `desc`. |
| `aggregates` | Related or owner aggregates, depending on tag. |
| `eventName` | Event runtime name for domain and integration events only. |
| `persist` | Domain event runtime persist flag only. |
| `artifacts` | Optional complete artifact selection list. |
| `fields` | Primary payload fields. Replaces `requestFields`. |
| `resultFields` | Result payload fields. Replaces `responseFields`. |

`tag` remains snake_case. `artifacts.family`, provider IDs, and plan item generator IDs for design families use kebab-case and do not carry a `design-` prefix.

`description`, `fields`, and `resultFields` are the only accepted new names. The source should fail fast when old public names are used, rather than silently accepting stale schema.

### Field Semantics

- `fields` is the primary payload: command request, query request, client request, api-payload request, domain-event body, or integration-event body.
- `resultFields` is the optional result carrier for query, client, and api-payload.
- Command does not use `resultFields` in #92.
- Domain-service and saga may omit field arrays.
- `eventName` is valid only for `domain_event` and `integration_event`.
- `persist` is valid only for `domain_event` and maps to domain event runtime annotation behavior. It is not artifact metadata.

## Artifact Selection

Artifact selection uses `artifacts[{ family, variant? }]`.

If `artifacts` is omitted, the block expands by tag defaults. If `artifacts` is present, it is complete and authoritative; default artifacts are not mixed in.

Duplicate `{family, variant}` entries are invalid. Families that have direction or page variants also reject contradictory variants on a single block.

### Default Expansion

| `tag` | Default artifacts |
| --- | --- |
| `command` | `command` |
| `query` | `query`, `query-handler` |
| `client` | `client`, `client-handler` |
| `api_payload` | `api-payload` |
| `domain_event` | `domain-event`, `domain-subscriber` |
| `integration_event` | `integration-event` with `variant = "outbound"` |
| `domain_service` | `domain-service` |
| `saga` | `saga` |

### Public Families

Supported design artifact families are:

```text
command
query
query-handler
client
client-handler
api-payload
domain-event
domain-subscriber
integration-event
integration-subscriber
domain-service
saga
```

Variant rules:

- `query` accepts `variant = "page"`.
- `api-payload` accepts `variant = "page"`.
- `integration-event` requires `variant = "inbound" | "outbound"`.
- Other design families reject variants.

### Integration Events

- Omitting `artifacts` for `tag = "integration_event"` generates only `integration-event/outbound`.
- Explicit artifacts are authoritative.
- `integration-subscriber` is valid only with explicit `integration-event/inbound` on the same block.
- `integration-event/outbound + integration-subscriber` is invalid.
- `integration-subscriber` never auto-adds an inbound event.
- Direction is a family-local variant, not a public `role` field.

### Page Variants

- Paged query uses `{ "family": "query", "variant": "page" }`.
- Paged API payload uses `{ "family": "api-payload", "variant": "page" }`.
- Names and field shapes never imply paging.
- Explicit `query/page` does not auto-add `query-handler`.

## Aggregate Ownership Fields

There is no single public `aggregate` field and no `scope` field.

`aggregates` is interpreted by source and tag:

- `command`, `query`, `client`, `saga`, and `domain_service`: related aggregates, `0..N`.
- `domain_event`: exactly one owner aggregate.
- `enum`: `0..1`; empty means context-shared, one means aggregate-owned.
- `value_object`: `0..1`; empty means context-shared, one means aggregate-owned.

`enum-manifest` and `value-object-manifest` keep typed input and typed canonical models, but both use `aggregates` with the enum/value-object rules above. Value-object manifest removes `scope` and single `aggregate`.

DB local enum generation is not strengthened in #92. The intended future direction is enum-manifest as the formal local/owned enum authoring source.

## Canonical Model

`CanonicalModel` should stop exposing design-family typed lists as the new design-json canonical contract. Instead it should add a unified design block model for design-json input and recovered design-elements output.

Recommended internal shape:

```kotlin
data class DesignBlockModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String = "",
    val aggregates: List<String> = emptyList(),
    val eventName: String = "",
    val persist: Boolean? = null,
    val artifacts: List<ArtifactSelectionModel>,
    val fields: List<FieldModel> = emptyList(),
    val resultFields: List<FieldModel> = emptyList(),
)

data class ArtifactSelectionModel(
    val family: String,
    val variant: String = "",
)
```

The source/canonical boundary owns default expansion and validation. Individual planners select blocks by `ArtifactSelectionModel.family` and optional `variant`; planners do not reinterpret source semantics.

Enum and value-object canonical models remain typed and are not folded into `DesignBlockModel`.

## Pipeline Configuration

`SourceConfig.enabled` and `GeneratorConfig.enabled` are removed.

For source configuration:

- A source config key or discovered default input means the source participates.
- For explicit paths, missing files or empty required path lists fail fast.
- If no explicit path exists and the default location has no files, the source is skipped.
- `design-json`, `enum-manifest`, and `value-object-manifest` use the same user-provided input principle.
- `ir-analysis` uses the same input-present principle as an observation input.

For generator configuration:

- A generator config key means the generator group has configuration.
- `config.generators` is not a global allow-list for all registered providers.
- Design family generation is driven by design block `artifacts`, not generator enabled flags.
- Enum and value-object generation is driven by manifest input, not aggregate generator enablement.
- `aggregate` remains a DB aggregate generation group; its internal options keep controlling optional artifacts such as `artifact.factory`, `artifact.specification`, and `artifact.unique`.
- `aggregate-projection` remains a separate DB-derived generation group selected by config key presence.
- Drawing-board and flow are driven by `irAnalysis.inputDirs`, not explicit generator enabled flags.

This preserves the user's ability to disable generated specifications by setting the aggregate internal option `artifact.specification = false` while removing the generic enabled boolean.

## Package Layout

Package layout is cooperative composition:

```text
basePackage + familyRoot + ownerPackage/defaultPackage + packageName + familySuffix
```

- Empty segments are skipped.
- `package` in JSON maps to internal `packageName` and is an authoring-relative package segment.
- Owner package is derived from `aggregates[0]` only for owner-aware families.
- Family root, family suffix, and owner participation are family layout rules.
- Integration event inbound/outbound placement is a variant layout rule, not `role`.

Artifact layout configuration should use public family semantics rather than `design*`-prefixed names where the config is exposed or newly touched. Implementation may keep private helper names only where they are not public contract.

## Concept Annotations

Old `com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate` is deleted. Templates do not output it, code-analysis does not read it, compiler options do not default to it, and tests/fixtures move off it.

Runtime `archinfo` is removed in the same iteration. It is not migrated to the new annotations.

New annotations live in:

```kotlin
package com.only4.cap4k.ddd.core.annotation
```

Both annotations are `CLASS` target and `BINARY` retention in #92. They are core cap4k concept markers, not generator-only or analysis-only types. Runtime consumption can be reconsidered later without changing their package.

### BuildingBlock

`@BuildingBlock` marks top-level authoring-source concept skeletons. It covers design-json, enum-manifest, and value-object-manifest outputs.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BuildingBlock(
    val tag: String,
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val aggregates: Array<String> = [],
    val eventName: String = "",
    val family: String,
    val variant: String = "",
)
```

`packageName` stores the authoring-relative package segment, not the full Kotlin package. The actual full package can be read from the class FQCN by consumers that need location.

For design-json output, `family` is one of the public artifact families. For enum/value-object manifest output:

- enum: `tag = "enum"`, `family = "enum"`, `variant = ""`.
- value object: `tag = "value_object"`, `family = "value-object"`, `variant = ""`.

Nested request/response DTOs, factory payloads, and similar child blocks are not annotated.

### AggregateElement

`@AggregateElement` marks DB aggregate source derived top-level concept skeletons.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AggregateElement(
    val aggregate: String = "",
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val type: String,
    val root: Boolean = false,
)
```

`packageName` stores the full Kotlin package because DB aggregate source does not have the design-json relative package contract.

Supported `type` values:

| `type` | Template or output | Meaning |
| --- | --- | --- |
| `schema` | `aggregate/schema.kt.peb` | Query schema and specification helper for an aggregate entity. |
| `entity` | `aggregate/entity.kt.peb` | Aggregate entity; `root=true` marks aggregate root. |
| `repository` | `aggregate/repository.kt.peb` | Aggregate repository skeleton. |
| `factory` | `aggregate/factory.kt.peb` | Aggregate factory skeleton. |
| `specification` | `aggregate/specification.kt.peb` | Aggregate specification skeleton. |
| `unique-query` | `aggregate/unique_query.kt.peb` | Unique-check query request skeleton. |
| `unique-query-handler` | `aggregate/unique_query_handler.kt.peb` | Unique-check query handler skeleton. |
| `unique-validator` | `aggregate/unique_validator.kt.peb` | Unique validator skeleton. |
| `strong-id` | `aggregate/strong_id.kt.peb` | Strong ID skeleton. |
| `projection` | `aggregate_projection/entity.kt.peb` | DB aggregate derived read-model projection. |

Strong ID semantics use `type + aggregate + root`:

- Aggregate root ID: `type = "strong-id"`, `aggregate = "Content"`, `root = true`.
- Future same-context aggregate reference ID artifact: `type = "strong-id"`, `aggregate = "Content"`, `root = false`.
- External or local-language reference ID from `@RefId`: `type = "strong-id"`, `aggregate = ""`, `root = false`.

Do not annotate:

- `aggregate/behavior.kt.peb`, because it is entity behavior extension support rather than an independent concept skeleton.
- `aggregate/enum.kt.peb`, because DB enum generation should not be strengthened in #92.
- Nested factory payloads, request/response DTOs, and child blocks.

## Code Analysis And Recovery

`design-elements.json` migrates to the same design block array schema as design-json and drawing-board.

The analysis merge key is:

```text
tag + packageName + name
```

Multiple `@BuildingBlock` top-level artifacts with the same key merge into one design block. Each contributes one `{family, variant}`. `description`, `aggregates`, `eventName`, and other shared block metadata must match across contributors; conflicts fail instead of being guessed.

Fields recover only from directly provable payload/event carriers:

- command/query/client/api-payload request DTOs provide `fields`.
- query/client/api-payload response DTOs provide `resultFields`.
- domain-event and integration-event classes provide `fields`.
- handlers and subscribers contribute artifact presence only.

The collector must not infer design input from handler method signatures, repository calls, class names, suffixes, or physical paths. Old `@Aggregate`, `role`, and `traits` recovery paths are removed. `payloadToAggregateName` is removed.

Flow relationship analysis consumes `@AggregateElement` for DB aggregate source derived concepts. Existing structural fallback based on package shape and `@ApplicationSideId` is not part of the public metadata contract and can be removed or retained only as a conservative non-contract fallback. New generated output should carry the annotation and not depend on fallback.

`rels.json` remains the flow relationship graph and is not merged into design-json.

## Drawing-Board And Flow Outputs

Drawing-board output is recovery JSON, not display-only debug JSON. Its block arrays are valid design-json input for standard generated skeletons.

Drawing-board grouping may keep files such as `drawing_board_<tag>.json`, but each file contains design block array entries using the formal schema.

Drawing-board `artifacts` emission rule:

- Omit `artifacts` only when the recovered set exactly equals tag default expansion.
- Emit complete `artifacts` when the set has a variant, omits a default artifact, adds a non-default artifact, or otherwise differs from the default set.
- `integration-event/outbound` default may omit `artifacts`.
- `integration-event/inbound`, `integration-subscriber`, `query/page`, and `api-payload/page` must emit explicit `artifacts`.

Drawing-board and flow are analysis/observation outputs. If `irAnalysis.inputDirs` exist, both are produced. If no analysis input exists, neither runs. Both default to `OVERWRITE`, because stale snapshots of compiled code are misleading. This overwrite rule does not change business/generated source conflict behavior for design, manifest, DB aggregate, or addon artifacts.

## Artifact Planner Changes

Design planners select `DesignBlockModel` entries by artifact family:

- planner id `command` selects `family = "command"`.
- planner id `query` selects `family = "query"`.
- planner id `query-handler` selects `family = "query-handler"`.
- planner id `client` selects `family = "client"`.
- planner id `client-handler` selects `family = "client-handler"`.
- planner id `api-payload` selects `family = "api-payload"`.
- planner id `domain-event` selects `family = "domain-event"`.
- planner id `domain-subscriber` selects `family = "domain-subscriber"`.
- planner id `integration-event` selects `family = "integration-event"`.
- planner id `integration-subscriber` selects `family = "integration-subscriber"`.
- planner id `domain-service` selects `family = "domain-service"`.
- planner id `saga` selects `family = "saga"`.

Old `design-*` provider IDs are not kept as aliases.

Enum and value-object planners continue to consume typed canonical models and emit `@BuildingBlock`. They do not participate in design-json artifact selection.

Aggregate planners emit `@AggregateElement` on DB aggregate source derived top-level concept skeletons listed above.

The runner should execute registered built-in planners that are model/input-driven and let them return an empty plan when their canonical inputs are absent. Config-key-driven groups such as `aggregate` and `aggregate-projection` still require their config key before they produce output.

## Error Handling

Sources and canonical assembly should fail fast for invalid public contract use:

- unsupported `tag`;
- unsupported `family`;
- unsupported or missing required `variant`;
- duplicate artifact selection;
- explicit `integration-subscriber` without `integration-event/inbound`;
- `integration-event/outbound` with subscriber;
- `eventName` on non-event tags;
- `persist` on non-domain-event tags;
- `resultFields` on tags that do not support results;
- `domain_event` without exactly one aggregate;
- enum/value-object with more than one aggregate;
- old public fields `role`, `traits`, `scope`, `desc`, `requestFields`, or `responseFields`.

Analysis recovery should fail when multiple annotated top-level artifacts with the same merge key provide conflicting shared metadata or conflicting field definitions.

## Testing Strategy

Use focused tests where the contract can break:

- design-json source parses `artifacts`, default expansion, variants, `description`, `fields`, `resultFields`, and rejects removed fields.
- canonical assembly validates artifact selection, owner aggregate rules, and enum/value-object `aggregates` rules.
- design planners select by public family IDs and no longer depend on `design-*` IDs.
- integration event validation covers default outbound, inbound subscriber, outbound subscriber rejection, and missing variant rejection.
- query/api-payload page variants require explicit artifacts.
- drawing-board emits design-json-compatible blocks and omits or includes `artifacts` by default-match rules.
- code-analysis emits design block arrays, merges by `tag + packageName + name`, and rejects conflicting metadata.
- templates render `@BuildingBlock` and `@AggregateElement` on the intended top-level skeletons and never render old `@Aggregate`.
- ddd-core no longer exposes `archinfo` runtime manager or starter auto-configuration.
- pipeline config tests cover removal of `SourceConfig.enabled` and `GeneratorConfig.enabled`, source input-driven execution, and aggregate internal artifact options.
- observation tests cover `irAnalysis.inputDirs` driving drawing-board and flow with `OVERWRITE` conflict policy.

## Acceptance Criteria

- Public design input uses `tag` plus optional `artifacts[{family, variant?}]`.
- `role`, `traits`, `scope`, `desc`, `requestFields`, and `responseFields` are removed from public input and generated recovery metadata.
- Default artifact expansion follows the table in this design.
- Design family provider IDs match public family names and do not keep `design-*` aliases.
- `domain-subscriber` replaces `domain-event-handler` as the public family.
- Integration event direction is represented by `integration-event.variant`.
- Page request shape is represented by explicit `query/page` or `api-payload/page` variants.
- `aggregates` is the only authoring aggregate relationship field for design, enum, and value-object inputs.
- `SourceConfig.enabled` and `GeneratorConfig.enabled` are removed.
- Aggregate internal factory/specification/unique options remain available.
- Generated authoring-source skeletons carry `@BuildingBlock`.
- Generated DB aggregate source derived top-level concept skeletons carry `@AggregateElement` according to the type table.
- Old `@Aggregate` and runtime `archinfo` are removed.
- `design-elements.json` and drawing-board JSON use the formal design block array schema.
- Drawing-board and flow run from `irAnalysis.inputDirs` and default to `OVERWRITE`.
- Addon artifact selection and addon-generated code recovery remain out of scope.
