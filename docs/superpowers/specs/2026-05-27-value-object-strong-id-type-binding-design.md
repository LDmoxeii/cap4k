# Value Object Strong ID Type Binding Design

Date: 2026-05-27

Status: Proposed

Scope: fix #93 by making value-object generation reuse cap4k-owned Strong ID type bindings from the canonical model, without expanding KSP metadata or changing Strong ID runtime behavior.

## Backlog Source

This design covers:

- #93: generator: bind canonical strong ids in value-object type resolution.

It is intentionally separate from:

- #75: Strong ID annotation conflict diagnostic context;
- #76: Spring MVC path/query binding for Strong IDs;
- #92: artifact metadata and drawing-board input recovery.

## Problem

`cap4k-reference-content-studio` currently declares cap4k-owned Strong ID types in `design/types.json`, including `ContentId`, `MediaProcessingTaskId`, `PaidPublicationTaskId`, and `ReviewerId`.

The original concern was that design input fields needed these duplicate `types.registryFile` entries. That is not accurate for design-generated artifacts. The design generator already merges `CanonicalModel.strongIds` into its type resolution view:

```kotlin
typeRegistryFqns() + model.strongIds.associate { strongId ->
    strongId.typeName to "${strongId.packageName}.${strongId.typeName}"
}
```

The confirmed gap is narrower. `ValueObjectArtifactPlanner` currently resolves manifest value-object fields from explicit registry entries plus manifest value objects:

```kotlin
val typeRegistry = config.typeRegistryFqns() + model.manifestValueObjectTypeLookup()
```

It does not merge `CanonicalModel.strongIds`. As a result, a manifest-managed JSON value object can reference cap4k-owned Strong IDs but still require duplicate `types.registryFile` entries to generate correct imports.

The dogfood case is `MediaProcessingResultSnapshot` in `cap4k-reference-content-studio/design/value-objects.json`. It references `MediaProcessingTaskId` and `ContentId`, both of which are available through DB-derived canonical Strong IDs.

## Goal

Value-object generation should resolve cap4k-owned Strong IDs the same way design generation does.

A manifest-managed JSON value object should be able to declare fields such as:

```json
{ "name": "contentId", "type": "ContentId" }
```

or:

```json
{ "name": "reviewerId", "type": "ReviewerId" }
```

without repeating those Strong IDs in `types.registryFile`, provided the IDs are already present in `CanonicalModel.strongIds`.

`types.registryFile` remains the escape hatch for external or manually authored types that cap4k does not own or cannot reliably discover.

## Chosen Approach

Extract or reuse a small generator-level type-binding helper that composes the available canonical type bindings in one place.

The binding order is:

1. explicit `ProjectConfig.typeRegistryFqns()`;
2. manifest-managed value objects;
3. canonical Strong IDs from `CanonicalModel.strongIds`.

Later entries override earlier entries. This preserves the current design generator behavior where cap4k-owned Strong IDs win over same-name explicit registry entries.

The priority rule is intentional:

- cap4k-owned Strong IDs are framework-owned structural types;
- `types.registryFile` should not silently replace a Strong ID that cap4k has already generated or discovered;
- external or handwritten types still belong in `types.registryFile` when they are not present in the canonical model.

This helper should be used by `ValueObjectArtifactPlanner`. Design generator behavior may either keep its existing helper or delegate to the shared helper if doing so keeps the implementation clearer. The design behavior must not regress.

## Non-Goals

- Do not expand or depend on KSP metadata.
- Do not change Strong ID runtime semantics.
- Do not change Strong ID generation from DB metadata.
- Do not change Spring MVC Strong ID binding; that remains #76.
- Do not change Strong ID annotation conflict diagnostics; that remains #75.
- Do not remove `types.registryFile`.
- Do not begin the broader public authoring contract changes from #92.

## Expected Behavior

Given a canonical model with:

```kotlin
StrongIdModel(
    typeName = "ContentId",
    packageName = "com.acme.domain.aggregates.content",
    kind = StrongIdKind.AGGREGATE_ROOT,
)
```

and a manifest value object field:

```json
{ "name": "contentId", "type": "ContentId" }
```

the generated value object imports `com.acme.domain.aggregates.content.ContentId` and renders the field as `ContentId`.

Given a canonical model with:

```kotlin
StrongIdModel(
    typeName = "ReviewerId",
    packageName = "com.acme.domain.shared.ids",
    kind = StrongIdKind.REFERENCE,
)
```

and a manifest value object field:

```json
{ "name": "reviewerId", "type": "ReviewerId" }
```

the generated value object imports `com.acme.domain.shared.ids.ReviewerId` and renders the field as `ReviewerId`.

If `types.registryFile` contains an external type such as `CurrencyCode`, and that type is not present in `CanonicalModel.strongIds`, value-object generation continues to resolve it from the registry.

If `types.registryFile` declares `ContentId` but `CanonicalModel.strongIds` also contains `ContentId`, the canonical Strong ID binding wins. This matches the current design generator behavior and keeps cap4k-owned generated types authoritative.

## Testing

Add focused tests in `cap4k-plugin-pipeline-generator-types`.

Required cases:

- value-object field references an aggregate-root Strong ID without `TypeRegistryConfig`;
- value-object field references a reference Strong ID from `@RefId` without `TypeRegistryConfig`;
- explicit registry entries still resolve external/manual types;
- same-name registry and canonical Strong ID resolves to the canonical Strong ID binding.

The tests should inspect the planned artifact context, especially field rendered types and imports. They do not need to render full Kotlin source unless existing local patterns make that clearer.

## Dogfood Verification

After implementation and local publication, verify `cap4k-reference-content-studio` by removing redundant cap4k-owned Strong ID entries from `design/types.json` and regenerating the value-object artifact.

Expected downstream result:

- `MediaProcessingResultSnapshot` still imports and renders `MediaProcessingTaskId` and `ContentId` correctly;
- external/manual type registry entries, if any remain, still work;
- no KSP metadata dependency is introduced.

The downstream cleanup can happen after the cap4k fix is released and consumed by the reference project.

## Acceptance Criteria

- [ ] `ValueObjectArtifactPlanner` resolves `CanonicalModel.strongIds`.
- [ ] DB-discovered aggregate-root Strong IDs can be used by value-object manifest fields without `types.registryFile` duplication.
- [ ] DB-discovered `@RefId` Strong IDs can be used by value-object manifest fields without `types.registryFile` duplication.
- [ ] External/manual registry entries still work.
- [ ] Same-name registry entries do not override canonical Strong IDs.
- [ ] Focused generator tests cover the binding rules.
- [ ] `cap4k-reference-content-studio` can remove redundant Strong ID entries from `design/types.json` after consuming the fix.

## Lifecycle Checklist

- [ ] spec written
- [ ] plan written
- [ ] implementation merged
- [ ] released if required
- [ ] downstream verified if required
