# 2026-04-23 cap4k Design Family Canonical + Provider Normalization Design

## Background

The current `cmd/qry/cli` pipeline in `cap4k` has two linked problems:

1. **Canonical modeling is too coarse.**
   `designJson` `cmd/qry/cli` entries are assembled into one `RequestModel` family plus `RequestKind`.
   That loses real semantic distinctions that already exist in the old codegen line and partially leak back into the new pipeline through late heuristics such as query variant guessing.

2. **Public generator/provider naming is not honest enough.**
   Public names such as `design` and `design-client` do not form a clean family.
   `design` looks like the umbrella generator for all design outputs, but in reality it only covers the command/query request family.

These are not separate issues. The public naming problem is downstream of the canonical modeling problem.

## Goal

Normalize the `cmd/qry/cli` design family in one slice by doing both of the following together:

- replace `RequestModel + RequestKind` with canonical models that express the real request-family semantics
- rename the public generator/provider contract so the provider family matches the canonical family

This slice intentionally does **not** change the `designJson` source format.

## Non-Goals

This slice does not:

- redesign `designJson`
- change `domain_event`, `validator`, or `api_payload` canonical modeling
- define the real detection rule for `VOID` commands
- keep long-term compatibility shims on `master`

Temporary aliases during feature-branch implementation are allowed only if needed to keep tests or migration steps moving. They must be removed before merge to `master`.

## Current Problems

### 1. `RequestModel + RequestKind` is too coarse

The current canonical layer compresses all `cmd/qry/cli` design entries into:

- `RequestModel`
- `RequestKind.COMMAND`
- `RequestKind.QUERY`
- `RequestKind.CLIENT`

This is no longer a faithful semantic model.

The old codegen line already proves that:

- query is not a single shape
  - `query`
  - `query_list`
  - `query_page`
- command is not guaranteed to stay single-shape forever
  - the system already needs a place for a future `VOID` command distinction

The current pipeline partially acknowledges this, but too late:

- query variants are guessed in generator space by `DesignQueryVariantResolver`
- command variants have no formal place at all

That means canonical truth is incomplete and generator code has to recover meaning later.

### 2. Public provider names are not normalized

The current public design family mixes different naming axes:

- `design`
- `design-query-handler`
- `design-client`
- `design-client-handler`

This is awkward because:

- `design` sounds like the umbrella provider
- `design-client` sounds like one sibling within that umbrella
- but in reality `design` only covers the command/query request family

The contract is not honest enough for users reading the README or configuring generators.

## Design Summary

This slice adopts three explicit rules:

1. **Do not change the source layer.**
   `designJson` remains the source. The normalization starts at canonical assembly.

2. **Canonical truth must become explicit.**
   `cmd/qry/cli` are assembled into separate canonical model families:
   - `CommandModel`
   - `QueryModel`
   - `ClientModel`

3. **Public providers must align with canonical truth.**
   The public provider family becomes:
   - `designCommand`
   - `designQuery`
   - `designClient`
   - `designQueryHandler`
   - `designClientHandler`
   - existing `designDomainEvent`, `designDomainEventHandler`, `designValidator`, and `designApiPayload` remain as already explicit family members

## Canonical Model Design

### CanonicalModel changes

`CanonicalModel` will stop carrying a single `requests` list for `cmd/qry/cli`.

Instead it will expose separate collections:

```kotlin
data class CanonicalModel(
    val commands: List<CommandModel> = emptyList(),
    val queries: List<QueryModel> = emptyList(),
    val clients: List<ClientModel> = emptyList(),
    ...
)
```

This is intentional. The canonical model should not merely hold typed sub-objects in one mixed list and force generators to reclassify them again.

### Shared payload base

`cmd/qry/cli` still share a lower-level payload structure. That shared structure remains explicit, but only at the payload layer:

```kotlin
sealed interface DesignInteractionModel {
    val packageName: String
    val typeName: String
    val description: String
    val aggregateRef: AggregateRef?
    val requestFields: List<FieldModel>
    val responseFields: List<FieldModel>
}
```

```kotlin
data class AggregateRef(
    val name: String,
    val packageName: String,
)
```

This shared base exists because request/response payload construction, nested type shaping, and import planning remain common concerns. It does **not** justify keeping a single coarse `RequestModel`.

### Specialized canonical models

Canonical request-family models become:

```kotlin
data class CommandModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel>,
    override val responseFields: List<FieldModel>,
    val variant: CommandVariant,
) : DesignInteractionModel
```

```kotlin
data class QueryModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel>,
    override val responseFields: List<FieldModel>,
    val variant: QueryVariant,
) : DesignInteractionModel
```

```kotlin
data class ClientModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel>,
    override val responseFields: List<FieldModel>,
) : DesignInteractionModel
```

### Variant enums

`QueryVariant` becomes canonical truth instead of a generator-time guess:

```kotlin
enum class QueryVariant {
    DEFAULT,
    LIST,
    PAGE,
}
```

`CommandVariant` also gets a formal home immediately, but the first iteration intentionally leaves real detection unimplemented:

```kotlin
enum class CommandVariant {
    DEFAULT,
    VOID,
}
```

In this slice, every assembled command is assigned `CommandVariant.DEFAULT`.

This is deliberate. The structural slot must exist now so later `VOID` work does not require another canonical redesign, but this slice does not invent a half-baked detection rule.

## Canonical Assembly Rules

`DefaultCanonicalAssembler` becomes the only place where `designJson` request-family tags are translated into canonical request-family semantics.

Assembly rules:

- `cmd` -> `CommandModel`
- `qry` -> `QueryModel`
- `cli` -> `ClientModel`

Query variant resolution moves into canonical assembly:

- `typeName.endsWith("PageQry")` -> `QueryVariant.PAGE`
- `typeName.endsWith("ListQry")` -> `QueryVariant.LIST`
- otherwise -> `QueryVariant.DEFAULT`

Command variant resolution for this slice:

- every command -> `CommandVariant.DEFAULT`

As a result, generator-time helpers such as `DesignQueryVariantResolver` are removed or reduced to dead code and then deleted.

## Provider / Generator Normalization

The public generator/provider family for design request-like artifacts is renamed to match canonical truth.

### New provider ids

The request-family public providers become:

- `designCommand`
- `designQuery`
- `designClient`
- `designQueryHandler`
- `designClientHandler`

Related family members that are already explicit remain as-is:

- `designDomainEvent`
- `designDomainEventHandler`
- `designValidator`
- `designApiPayload`

### Removed public ids

By the time this slice reaches `master`, the following public ids must no longer remain:

- `design`
- `design-client`

No long-term compatibility alias is kept on `master`.

If branch-local aliases are used to keep intermediate tests green, they must be removed before merge.

## Generator Implementation Boundary

This slice does **not** require copying all low-level payload logic three times.

The system should distinguish two layers:

1. **semantic planner entrypoints**
   - `DesignCommandArtifactPlanner`
   - `DesignQueryArtifactPlanner`
   - `DesignClientArtifactPlanner`

2. **shared payload machinery**
   - request/response namespace construction
   - nested type preparation
   - type/import planning
   - payload render-model assembly

That shared machinery should remain single-source and may be renamed to reflect its narrower role more honestly, for example:

- `DesignRenderModelFactory` -> `DesignPayloadRenderModelFactory`

The point of the redesign is not to duplicate payload mechanics. The point is to stop using one coarse semantic entry model for multiple request families that no longer share one semantic shape.

## Expected Code-Level Changes

### API layer

`cap4k-plugin-pipeline-api` changes:

- remove `RequestKind`
- remove `RequestModel`
- add:
  - `AggregateRef`
  - `DesignInteractionModel`
  - `CommandModel`
  - `QueryModel`
  - `ClientModel`
  - `CommandVariant`
  - `QueryVariant`
- update `CanonicalModel`

### Canonical assembly layer

`cap4k-plugin-pipeline-core` changes:

- update `DefaultCanonicalAssembler` to emit `commands`, `queries`, `clients`
- move query variant detection into canonical assembly
- stop producing `requests`

### Generator layer

`cap4k-plugin-pipeline-generator-design` changes:

- replace `DesignArtifactPlanner` with:
  - `DesignCommandArtifactPlanner`
  - `DesignQueryArtifactPlanner`
- keep or refine `DesignClientArtifactPlanner`
- replace `RequestModel` inputs with typed canonical inputs
- remove `DesignQueryVariantResolver`
- update handler planners to consume `QueryModel` and `ClientModel` directly

### Gradle/configuration layer

`cap4k-plugin-pipeline-gradle` changes:

- update provider registration to the new ids
- update enable/disable validation and README-visible provider lists
- remove old ids from final registration state

## Testing Strategy

This slice needs explicit coverage in four places.

### 1. Canonical assembly tests

Add or update tests to prove:

- `cmd` assembles into `CommandModel`
- `qry` assembles into `QueryModel`
- `cli` assembles into `ClientModel`
- `PageQry` becomes `QueryVariant.PAGE`
- `ListQry` becomes `QueryVariant.LIST`
- ordinary query becomes `QueryVariant.DEFAULT`
- commands currently default to `CommandVariant.DEFAULT`
- `CanonicalModel.requests` no longer exists

### 2. Generator tests

Add or update tests to prove:

- command generators consume `model.commands`
- query generators consume `model.queries`
- client generators consume `model.clients`
- query handler generators consume typed query canonical models
- client handler generators consume typed client canonical models

### 3. Gradle/plugin registration tests

Add or update tests to prove final provider registration exposes:

- `designCommand`
- `designQuery`
- `designClient`
- `designQueryHandler`
- `designClientHandler`

and does not expose:

- `design`
- `design-client`

### 4. README / contract tests

At minimum, README examples and provider tables must be updated in the same slice so documentation does not describe removed ids.

## Risks and Tradeoffs

### Risk: broad rename churn

This slice will touch:

- API model types
- assembler logic
- design generator tests
- Gradle plugin registration
- README and examples

That is acceptable because leaving canonical and provider layers half-renamed would be worse. This slice is intentionally complete within its scope.

### Risk: `CommandVariant.VOID` is not implemented yet

That is acceptable because this slice is about structural normalization, not guessing a command semantic rule prematurely.

The system will be cleaner after this slice because:

- command has a formal variant slot
- query variants become canonical truth
- provider naming stops pretending `design` is the umbrella request generator

## Acceptance Criteria

This slice is complete only when all of the following are true on `master`:

- `RequestKind` is gone
- `RequestModel` is gone
- `CanonicalModel` holds `commands`, `queries`, and `clients`
- query variants are assembled canonically, not inferred in generators
- command canonical models expose `variant`, currently defaulting to `DEFAULT`
- public provider ids are:
  - `designCommand`
  - `designQuery`
  - `designClient`
  - `designQueryHandler`
  - `designClientHandler`
- public ids `design` and `design-client` are gone
- README and examples no longer describe the removed ids
- no compatibility alias remains in the final merged branch

## Follow-Up Work Explicitly Deferred

This slice intentionally leaves these topics for later:

- real `CommandVariant.VOID` detection
- any redesign of `designJson`
- normalization of `domain_event`, `validator`, or `api_payload` canonical models
- any further unification or cleanup of the broader design family beyond `cmd/qry/cli`
