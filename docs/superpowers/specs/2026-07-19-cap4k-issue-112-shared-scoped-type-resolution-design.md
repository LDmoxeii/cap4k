# Cap4k Issue 112 Shared Scoped Type Resolution Design

Date: 2026-07-19

Status: Proposed

Scope: replace the value-object generator's separate short-name registry with a shared scoped type symbol catalog and selector that both design and value-object generators consume, while keeping parser, rendering, diagnostics, and generated-context policy generator-specific.

## Backlog Source

This design covers:

- #112: generator: resolve enum imports for value-object fields.

Issue #112 was triggered by `booking-center` dogfood output. `types-value-object` generated Kotlin data classes with enum field short names but without enum imports. Representative missing imports include:

- `DemandSnapshot.transportType: TransportType` -> `com.only4.booking.center.domain.shared.enums.TransportType`
- `DemandDocumentRequirement.requiredDocumentTypes: List<DocumentType>` -> `com.only4.booking.center.domain.shared.enums.DocumentType`
- `CarrierResourceIdentity.resourceType: CarrierResourceType` -> `com.only4.booking.center.domain.aggregates.carrier_resource_confirmation.enums.CarrierResourceType`

Issue #104 already fixed manifest enum and value-object short-name resolution for the design generator. The current problem is not that #104 failed; it is that `types-value-object` rebuilt a narrower value-object-only binding path instead of consuming the same scoped symbol semantics.

## Current Evidence

The current branch state used for this spec is:

- base branch: `spec/issue-112-value-object-enum-imports`
- base commit: `988ec9b4 fix: preserve value object enum binding provenance`
- new worktree branch: `spec/issue-112-shared-scoped-type-resolution`

The current implementation already added a private `ValueObjectTypeBindings` helper in:

- `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`

That helper still feeds `ValueObjectTypeResolver` with `Map<String, String>`. The registry is flattened before field resolution, so source and owner context are lost early. A later focused verification surfaced the same design problem in another shape: an owner-local enum named `Status` could conflict with an unrelated aggregate-local value object named `Status` because the unrelated value object had already entered the flattened registry.

The design generator already has richer semantics:

- `SymbolIdentity` records `source`, `ownerAggregateName`, `manifestOwned`, and `shared`;
- `DesignSymbolRegistry` stores multiple candidates for the same simple name;
- `ImportResolver.selectShortTypeCandidates` narrows candidates by single aggregate context and prefers matching aggregate-owned manifest candidates.

These semantics are the behavior to align with. The concrete design module classes are not the dependency surface to share because `cap4k-plugin-pipeline-generator-design` and `cap4k-plugin-pipeline-generator-types` are sibling modules.

## Compatibility Position

No compatibility workarounds are required for this cap4k iteration. cap4k does not currently have external users whose historical invalid inputs must keep generating output.

Therefore:

- unknown short type names in value-object fields should fail planning instead of being rendered as unresolved Kotlin;
- ambiguous short type names should fail planning instead of selecting a candidate by map order;
- downstream projects should use manifest inputs, explicit registry entries, or FQNs instead of relying on unresolved short names passing through.

This removes the previous value-object-specific compatibility pressure to preserve `ValueObjectTypeResolver.resolveBase` returning `ResolvedType(tokenText)` for unknown short names.

## Design Principle

Do not extract a generic "type resolver".

The true commonality is narrower:

- canonical type identities can come from project type registry, Strong IDs, manifest enums, manifest value objects, and explicit FQNs;
- those identities may share the same simple name;
- manifest-owned identities may be shared or aggregate-owned;
- aggregate-owned manifest identities require aggregate context to disambiguate safely;
- single aggregate context selects matching local manifest candidates before global candidates;
- no aggregate context and multi-aggregate context do not guess an owner.

The following must remain generator-specific:

- parsing raw field type syntax;
- inner/generated request type handling in design artifacts;
- explicit-FQN rendering and qualified fallback policy;
- unknown-type diagnostics;
- rendered field context shape;
- template context construction.

This keeps the abstraction stable if design and value-object generators later develop different rendering rules. The shared layer answers "which candidate identities match this simple name in this aggregate context"; each generator decides what to do with that answer.

## Chosen Architecture

Create a new sibling module:

- `cap4k-plugin-pipeline-generator-common`

This module depends on `cap4k-plugin-pipeline-api` and contains generator-internal common type identity utilities. It is intentionally not placed in `cap4k-plugin-pipeline-api`; the canonical model and enum catalog remain API, while generator resolution policy remains generator implementation support.

The new module provides:

- `TypeSymbolIdentity`
- `TypeSymbolRegistry`
- `TypeSymbolSelector`
- `CanonicalTypeSymbolRegistryFactory`

Suggested package:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types
```

`cap4k-plugin-pipeline-generator-design` and `cap4k-plugin-pipeline-generator-types` both depend on the common module.

## Shared Types

`TypeSymbolIdentity` represents one known type candidate:

```kotlin
data class TypeSymbolIdentity(
    val packageName: String,
    val typeName: String,
    val moduleRole: String? = null,
    val source: String? = null,
    val ownerAggregateName: String? = null,
    val manifestOwned: Boolean = false,
    val shared: Boolean = false,
) {
    val simpleName: String
        get() = typeName.substringAfterLast('.')

    val fqcn: String
        get() = if (packageName.isBlank()) typeName else "$packageName.$typeName"
}
```

Source constants:

```kotlin
const val PROJECT_TYPE_REGISTRY_SOURCE = "project-type-registry"
const val STRONG_ID_SOURCE = "strong-id"
const val MANIFEST_ENUM_SOURCE = "manifest-enum"
const val MANIFEST_VALUE_OBJECT_SOURCE = "manifest-value-object"
const val EXPLICIT_FQCN_SOURCE = "explicit-fqcn"
const val AGGREGATE_SOURCE = "aggregate"
```

`TypeSymbolRegistry` stores multiple candidates per simple name:

```kotlin
class TypeSymbolRegistry(symbols: Iterable<TypeSymbolIdentity> = emptyList()) {
    fun register(symbol: TypeSymbolIdentity)
    fun findBySimpleName(simpleName: String): List<TypeSymbolIdentity>
    fun allSymbols(): List<TypeSymbolIdentity>
}
```

`TypeSymbolSelector` provides the shared scoped selection rule:

```kotlin
object TypeSymbolSelector {
    fun selectShortNameCandidates(
        candidates: List<TypeSymbolIdentity>,
        aggregateContext: List<String>,
    ): List<TypeSymbolIdentity>
}
```

Selection semantics:

1. Deduplicate candidates by FQN while preserving order.
2. If aggregate context contains exactly one nonblank distinct aggregate name, first select manifest-owned, non-shared candidates whose `ownerAggregateName` matches that aggregate.
3. If that local candidate set is non-empty, return only that set.
4. Otherwise return all unique candidates.

The selector deliberately does not decide unknown or ambiguous behavior. A generator receiving zero or multiple candidates must apply its own failure message and context.

## Canonical Registry Construction

`CanonicalTypeSymbolRegistryFactory` builds a full registry from:

- `ProjectConfig.typeRegistryFqns()`
- `CanonicalModel.strongIds`
- `CanonicalModel.sharedEnums`
- `CanonicalModel.valueObjects`
- `CanonicalEnumCatalog`
- `ArtifactLayoutResolver`

The factory must preserve the #104 approach of splitting enum catalog construction:

- shared enum definitions go through a catalog built from `aggregates.isEmpty()`;
- aggregate-owned enum definitions go through a catalog built from `aggregates.isNotEmpty()`;
- both catalog calls use `emptyMap()` for registry entries so DB `@T` binding ambiguity does not reject same-name shared/local manifest enum candidates before the scoped selector can evaluate them.

For manifest value objects:

- `aggregates.isEmpty()` registers a shared manifest value-object identity;
- `ownerAggregate` from `ValueObjectModel.aggregates.singleOrNull()` registers an aggregate-owned manifest value-object identity;
- multi-aggregate value-object ownership does not become a local single-owner candidate.

For aggregate-owned manifest enums:

- `SharedEnumDefinition.aggregates.singleOrNull()` supplies `ownerAggregateName`;
- entity package ownership is resolved with the same parent/root algorithm used by #104;
- duplicate entity names or parent/child entity graphs must not be simplified to name-only lookup.

The existing design-side owner resolution logic should move into the common factory instead of being reimplemented in the value-object generator.

## Design Generator Integration

The design generator keeps its parser and rendering behavior.

Changes:

- replace design-local `SymbolIdentity` and `DesignSymbolRegistry` implementation with common `TypeSymbolIdentity` and `TypeSymbolRegistry`;
- replace design-local short-name candidate selection with `TypeSymbolSelector.selectShortNameCandidates`;
- replace `ProjectConfig.designSymbolRegistry(model)` construction with common registry construction;
- keep `ImportResolver.UnknownShortTypeFailure`, `AmbiguousShortTypeFailure`, `innerTypeNames`, explicit-FQN conflict handling, and `qualifiedFallback`.

Expected behavior after migration is unchanged for #104:

- manifest enum and value-object short names still resolve;
- single aggregate context still prefers matching local manifest candidates;
- no context and multi-context ambiguity still fail;
- design unknown short names still fail.

## Value-Object Generator Integration

The value-object generator keeps its type parser and render model shape, but no longer uses a flattened `Map<String, String>` registry.

Changes:

- delete the private value-object-specific `ValueObjectTypeBindings` helper;
- delete value-object-specific manifest/strong-id lookup maps that duplicate common registry construction;
- pass the common `TypeSymbolRegistry` into value-object field type resolution;
- use `valueObject.aggregates` as aggregate context;
- use `TypeSymbolSelector.selectShortNameCandidates` for unresolved short type tokens;
- collect imports recursively for generic arguments.

Value-object field resolution rules:

1. Kotlin built-ins render with no import.
2. Explicit FQNs resolve to their exact FQN. If importing the short name would conflict with a known non-registry candidate, render the FQN as a qualified fallback rather than importing a misleading short name.
3. Short names resolve through the common registry and selector.
4. A selected single candidate renders as the short name and imports the candidate FQN.
5. Zero selected candidates fail as unknown.
6. More than one selected candidate fails as ambiguous and lists candidate FQNs.

This is intentionally stricter than the prior value-object behavior. The generator must not produce checked-in Kotlin source with unresolved type references when the short name cannot be resolved from canonical inputs.

## Worktree And Iteration Policy

This spec and its plan are written in a new worktree:

- `cap4k/.worktrees/issue-112-shared-scoped-type-resolution`
- branch `spec/issue-112-shared-scoped-type-resolution`

The current implementation worktree remains untouched during this documentation iteration:

- `cap4k/.worktrees/issue-112-spec`
- branch `spec/issue-112-value-object-enum-imports`

After this spec/plan iteration is accepted, merge the documentation branch back into `spec/issue-112-value-object-enum-imports`, then continue implementation there.

The uncommitted value-object diagnostic patch in the current implementation worktree is not part of this spec. Treat it as evidence that flattened registries are the wrong direction. Do not build the final solution on top of that patch without first removing or replacing it deliberately.

## Non-Goals

- Do not patch downstream `booking-center` generated files as the upstream fix.
- Do not add enum/value-object manifest entries to downstream `type-registry.json` as a workaround.
- Do not keep unknown short type pass-through behavior for value-object fields.
- Do not make `cap4k-plugin-pipeline-generator-types` depend on `cap4k-plugin-pipeline-generator-design`.
- Do not move generator resolution policy into `cap4k-plugin-pipeline-api`.
- Do not merge parser or full rendering logic between design and value-object generators.
- Do not change manifest JSON shape.
- Do not change template rendering for `types/value-object`; the renderer already consumes `context.imports`.
- Do not broaden into downstream handwritten implementation.

## Testing Strategy

Add common module tests for:

- registry stores multiple candidates for the same simple name;
- selector deduplicates by FQN;
- selector prefers matching aggregate-owned manifest candidates in single aggregate context;
- selector does not narrow in no-context or multi-context cases;
- canonical registry construction includes project registry, Strong IDs, shared manifest enums, local manifest enums, shared manifest value objects, and local manifest value objects;
- canonical registry construction handles parent/child entity root resolution and duplicate entity names consistently with #104.

Update design generator tests to prove behavior is unchanged:

- #104 shared manifest enum/value-object resolution still works;
- single aggregate context still selects matching local manifest type;
- no-context or multi-context duplicate local candidates still fail ambiguous;
- explicit-FQN and inner type behavior still works.

Update value-object generator tests to prove #112 and the stricter policy:

- shared enum field imports shared enum FQN;
- enum inside generic imports enum FQN;
- aggregate-local enum field imports owner aggregate enum FQN;
- manifest value-object imports still work;
- Strong ID imports still work;
- same-name unrelated aggregate-local value object does not block owner-local enum resolution;
- unknown short value-object field type fails planning;
- same-name local candidates in no-context or multi-context fail ambiguous;
- explicit FQN fields do not import an unsafe short name when another known candidate would make the short name ambiguous.

Per user constraint, implementation subagents must not run compile or test commands. They may write or update tests and perform static review. The main agent performs one final compile/test verification after implementation is complete.

## Acceptance Criteria

- A new `cap4k-plugin-pipeline-generator-common` module exists and is included in Gradle settings.
- Shared symbol identity, registry, selector, and canonical registry construction live in the common module.
- Design generator consumes common symbol identities and selector without changing #104 behavior.
- Value-object generator consumes common symbol identities and selector instead of `Map<String, String>` binding.
- `ValueObjectTypeBindings` and value-object-specific manifest/Strong ID lookup duplication are removed.
- `types-value-object` resolves shared enum imports for value-object fields.
- `types-value-object` resolves enum imports inside generic fields.
- `types-value-object` resolves aggregate-local enum imports using owner aggregate context.
- Unknown short type names in value-object fields fail planning.
- Ambiguous short type names fail planning with candidate FQNs.
- Existing manifest value-object and Strong ID imports remain covered.
- The implementation does not modify value-object templates to compensate for missing planner context.
- Final main-agent verification runs once after implementation and includes common, design, and types generator tests.

## Rollback Triggers

Roll back to brainstorming before implementation if:

- common extraction requires design or value-object parsers to share a type AST;
- common extraction requires changing manifest JSON contracts;
- common extraction requires `generator-types` to depend on `generator-design`;
- the value-object generator needs unresolved short type pass-through for a currently valid cap4k input surface;
- the canonical registry cannot preserve #104 entity root resolution without copying private design assumptions into a weaker form.

Roll back to a narrower #112 hotfix only if:

- adding a common module causes unexpected Gradle publication or dependency problems that cannot be resolved locally;
- design generator migration becomes materially larger than the value-object bugfix and risks delaying a blocking P0 repair.

## Lifecycle Notes

After this spec and its plan are accepted:

- merge branch `spec/issue-112-shared-scoped-type-resolution` into `spec/issue-112-value-object-enum-imports`;
- continue implementation in the existing issue #112 implementation worktree;
- remove or replace the current uncommitted diagnostic value-object patch before final implementation;
- keep #112 open until implementation, release if required, and downstream verification are complete.
