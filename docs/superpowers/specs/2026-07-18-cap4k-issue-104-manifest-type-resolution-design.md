# Cap4k Issue 104 Manifest Type Resolution Design

Date: 2026-07-18

Status: Proposed

Scope: fix #104 by making design-generator type resolution consume manifest-managed enum and value-object identities from the canonical model, with aggregate-aware short-name disambiguation and without reintroducing class-name guessing or requiring duplicate `types.registryFile` entries.

## Backlog Source

This design covers:

- #104: generator: register manifest enum and value-object types for design generation.

The dogfood trigger is `booking-center`, where `cap4kPlan` fails while resolving a design payload field:

```text
failed to resolve type for field customerRef: CustomerRef (unknown short type: CustomerRef; use a fully qualified name or register it in type-registry.json)
```

`CustomerRef` is already declared through `types.valueObjectManifest`. The downstream project should not need to duplicate that type into `types.registryFile`.

## Problem

The current design generator type binding only exposes explicit project registry entries and canonical Strong IDs:

```kotlin
internal fun ProjectConfig.designTypeRegistryFqns(model: CanonicalModel): Map<String, String> =
    typeRegistryFqns() + model.strongIds.associate { strongId ->
        strongId.typeName to "${strongId.packageName}.${strongId.typeName}"
    }
```

This omits:

- `CanonicalModel.sharedEnums`;
- `CanonicalModel.valueObjects`.

That omission conflicts with the current types-input contract:

- enum manifests are configured through `types.enumManifest`;
- value-object manifests are configured through `types.valueObjectManifest`;
- manifest-managed enum and value-object entries do not need matching `types.registryFile` entries.

The failure diagnostic also nudges users toward the wrong input surface. For manifest-managed enum and value-object types, the expected source of identity is the manifest itself, not a duplicate custom registry entry.

## Existing Boundaries To Preserve

This slice must preserve the current design-generator import boundary:

- no source-code scanning;
- no class-name guessing;
- no sibling design-entry type references;
- explicit FQNs remain valid identity sources;
- generated-unit inner types still take precedence inside their namespace;
- ambiguity remains an error.

`types.registryFile` remains valid for external or handwritten types and converter policy. It must not become the required path for cap4k-owned manifest-managed enum or value-object types.

## DDD Positioning

This design does not treat `shared` and `aggregate-owned` value objects as different DDD categories of value object.

A Value Object has no conceptual identity and may be copied or shared as a value. Aggregate boundaries control consistency, mutation, and aggregate-root access; they do not mean a value-object type can only be used by one aggregate.

In cap4k, `aggregates` on enum and value-object manifests is a generator-owned scope signal:

- it records owner metadata;
- it supports package and artifact layout decisions;
- it provides a short-name disambiguation scope;
- it allows the same simple name to exist under different aggregate owners.

Therefore, aggregate-owned manifest types are still valid cap4k-owned type identities. The implementation must not exclude them merely because they have an owner.

## Chosen Approach

All manifest-managed enum and value-object types should enter a controlled design type candidate pool.

Short-name resolution then proceeds with aggregate-aware disambiguation:

1. If a design field uses an explicit FQN, keep the existing explicit-FQN behavior.
2. If the field type is an inner generated-unit type, keep the existing inner-type behavior.
3. If the field type is a short name and the current design artifact has exactly one aggregate context, first evaluate manifest candidates owned by that aggregate.
4. If exactly one aggregate-owned candidate matches, use it.
5. If no aggregate-owned candidate matches, evaluate shared manifest candidates plus explicit registry and Strong ID candidates.
6. If the final candidate set contains exactly one identity, import that FQN and render the short name.
7. If the final candidate set is empty, fail as unknown.
8. If the final candidate set has more than one identity, fail as ambiguous.

For design artifacts with no aggregate context, or with multiple aggregate contexts, short-name resolution cannot use aggregate-local narrowing. In those cases, a short name is valid only when it is globally unique across controlled identity candidates.

This keeps the core rule simple:

> Every manifest-managed enum and value-object is a legal identity source. A short name may resolve only when the current design context can identify exactly one target.

## Aggregate Context

For this slice, a design artifact has single aggregate context only when the canonical design model for that artifact contains exactly one aggregate reference.

Examples:

- `aggregates: ["Content"]` gives single aggregate context `Content`.
- omitted `aggregates` gives no aggregate context.
- `aggregates: []` gives no aggregate context.
- `aggregates: ["Content", "Review"]` gives multiple aggregate contexts and must not use aggregate-local narrowing.

If a design family already carries a resolved aggregate package or owner metadata, implementation may reuse that resolved metadata as long as the semantic rule remains "exactly one aggregate context".

## Candidate Sources

The design type candidate pool should include:

- explicit `ProjectConfig.typeRegistryFqns()` entries;
- canonical Strong IDs from `CanonicalModel.strongIds`;
- manifest-managed shared enums from `CanonicalModel.sharedEnums` where `aggregates` is empty;
- manifest-managed aggregate-owned enums from `CanonicalModel.sharedEnums` where `aggregates` has one owner;
- manifest-managed shared value objects from `CanonicalModel.valueObjects` where `aggregates` is empty;
- manifest-managed aggregate-owned value objects from `CanonicalModel.valueObjects` where `aggregates` has one owner.

The implementation should preserve current manifest ownership validation:

- enum/value-object manifest entries may declare at most one owner aggregate;
- shared names are globally unique where the current canonical validation requires that;
- aggregate-owned names are unique within owner scope;
- ambiguity fails instead of being guessed.

Enum candidate FQNs must follow current enum catalog and artifact-layout behavior. Shared enum candidates should use the same FQN that aggregate enum binding uses for shared enum definitions. Aggregate-owned enum candidates should use the same owner-aware FQN that aggregate-local enum binding uses for manifest-owned local enum definitions. Do not derive enum FQNs by string-concatenating raw manifest fields if an existing catalog/layout helper already encodes the current package rule.

Value-object candidate FQNs should use the generated value-object class FQN from the value-object model, matching the value-object planner's declared-package behavior.

## Name Resolution Rules

### No Aggregate Context

Given:

```json
{ "name": "customerRef", "type": "CustomerRef" }
```

If only one candidate named `CustomerRef` exists across manifest, Strong ID, and registry identities, the field resolves.

If two aggregate-owned value objects named `Snapshot` exist under different aggregates and the design artifact has no aggregate context, then:

```json
{ "name": "snapshot", "type": "Snapshot" }
```

must fail as ambiguous.

### Single Aggregate Context

Given a design artifact with:

```json
"aggregates": ["Content"]
```

and manifest entries:

```json
{ "name": "Snapshot", "package": "com.acme.domain.aggregates.content.values", "aggregates": ["Content"] }
{ "name": "Snapshot", "package": "com.acme.domain.aggregates.review.values", "aggregates": ["Review"] }
```

the short type `Snapshot` resolves to the Content-owned value object.

If a Content-owned value object and a shared value object have the same simple name, the Content-owned candidate wins in a Content single-aggregate context. This mirrors the current DB `@T` value-object binding behavior, where local value objects are resolved before shared value objects.

For design type resolution, the same local-first rule applies to manifest-managed enums. This is a design-generator rule only. It must not silently change DB `@T` enum converter binding semantics in this slice.

### Multiple Aggregate Contexts

Given:

```json
"aggregates": ["Content", "Review"]
```

the resolver must not choose between Content-owned and Review-owned `Snapshot` candidates. The short type is valid only if it is globally unique after considering controlled identities. Otherwise it fails as ambiguous.

## Diagnostics

Diagnostics must stay precise and avoid directing users to duplicate manifest-managed types into `type-registry.json`.

For unknown short names, prefer an error shape like:

```text
failed to resolve type for field customerRef: CustomerRef
(unknown short type: CustomerRef; use a fully qualified name, declare it in types.enumManifest or types.valueObjectManifest when it is cap4k-owned, or register external handwritten types in types.registryFile)
```

For ambiguous short names, include the relevant candidates:

```text
failed to resolve type for field snapshot: Snapshot
(ambiguous short type: Snapshot -> com.acme.domain.aggregates.content.values.Snapshot, com.acme.domain.aggregates.review.values.Snapshot; use a fully qualified name or provide a single aggregate context)
```

If ambiguity is between a local manifest type and a shared manifest type inside a single aggregate context, this design chooses local-first resolution, so that case should not be reported as ambiguous.

## Implementation Boundary

Expected cap4k repair area:

- `cap4k-plugin-pipeline-generator-design`.

The implementation may add a helper near `DesignTypeRegistryBindings` or replace the map-only binding with a richer candidate model if needed.

The existing map shape may be enough for globally unique cases, but aggregate-aware narrowing likely needs more metadata than `Map<String, String>`. If so, prefer a small design-generator-local type identity helper over leaking the rule into pipeline core or templates.

Do not move resolution into Pebble templates.

Do not modify source providers.

Do not modify manifest JSON contracts unless implementation reveals a current validation bug unrelated to #104.

Do not add downstream `booking-center` or other dogfood workarounds.

## Non-Goals

- Do not add `type-registry.json` entries to downstream projects as a workaround.
- Do not require design authors to write FQNs for manifest-managed types when the short name is unique in context.
- Do not scan Kotlin source files for type names.
- Do not restore legacy class-name import guessing.
- Do not support sibling design-entry short-name references.
- Do not change enum or value-object manifest file shape.
- Do not redesign aggregate ownership semantics.
- Do not change value-object runtime behavior.
- Do not enter downstream handwritten implementation.

## Testing Strategy

Add focused design-generator tests before implementation.

Minimum cases:

- shared manifest value object short name resolves in command or payload field imports;
- shared manifest enum short name resolves in a design field;
- manifest type resolves inside nested generic type, for example `List<DocumentType>`;
- canonical Strong ID resolution still works;
- explicit `types.registryFile` resolution still works for external handwritten types;
- no-context duplicate aggregate-owned value-object short name fails as ambiguous;
- single-aggregate context resolves the matching aggregate-owned value object;
- single-aggregate context prefers matching aggregate-owned value object over same-name shared value object;
- single-aggregate context prefers matching aggregate-owned enum over same-name shared enum for design type resolution;
- multi-aggregate context does not choose between aggregate-owned same-name candidates;
- unresolved short-type diagnostic mentions manifest type inputs and does not imply manifest-backed types must be duplicated into `type-registry.json`.

If implementation touches shared import resolution infrastructure, include representative tests for commands, queries, API payloads, domain events, integration events, and sagas only where necessary to prove common path coverage. Avoid broad test churn if a single common factory test proves the behavior.

## Acceptance Criteria

- Design generator short-name resolution includes manifest-managed enum identities from `CanonicalModel.sharedEnums`.
- Design generator short-name resolution includes manifest-managed value-object identities from `CanonicalModel.valueObjects`.
- Aggregate-owned manifest types are not excluded from design resolution solely because they have an owner aggregate.
- No-context and multi-context design artifacts only accept globally unique short-name matches.
- Single-aggregate-context design artifacts resolve matching aggregate-owned manifest candidates before shared candidates.
- Existing Strong ID resolution behavior remains intact.
- Existing explicit `types.registryFile` behavior remains intact for external handwritten types.
- Short manifest types resolve inside generic fields such as `List<DocumentType>`.
- Ambiguous short-name cases fail fast with candidate information.
- Unknown short-name diagnostics point users to FQN, manifest type inputs, or `types.registryFile` according to ownership, not only to `type-registry.json`.
- `booking-center` can run `cap4kPlan` past the `CustomerRef` failure without adding a custom registry workaround.
- #104 remains open after implementation until release and downstream verification needs are resolved.

## Risks

### Risk 1: Turning aggregate-owned types into unsafe globals

If all aggregate-owned manifest types are flattened into a simple global map, same-name types under different aggregates may become ambiguous too early or, worse, one may silently override another.

Mitigation:

- preserve candidate identity with owner metadata;
- resolve short names only after considering aggregate context;
- fail on unresolved ambiguity.

### Risk 2: Reintroducing class-name guessing

If implementation starts inferring package names from conventions or source trees, it violates the design-generator import boundary.

Mitigation:

- use only canonical manifest identities, Strong IDs, explicit FQNs, and explicit registry entries.

### Risk 3: Over-broad diagnostic advice

If the diagnostic still says only "register it in type-registry.json", users will keep duplicating cap4k-owned manifest types.

Mitigation:

- update unknown and ambiguous short-name diagnostics to mention manifest inputs for cap4k-owned types and `types.registryFile` only for external handwritten types.

## Lifecycle Notes

After this spec lands:

- update #104 with a stable link to this spec and check `spec written`;
- keep #104 open for plan, implementation, release if required, and downstream verification;
- write a separate implementation plan before changing production code.
