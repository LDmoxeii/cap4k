# Cap4k Project Type Registry Design

## Summary

This slice adds a minimal project-level type registry for the design generator.

The registry is a strict fallback source for short type names that cannot be resolved from:

- Kotlin built-in types and standard collection wrappers
- the current generated unit's own inner types
- explicit fully qualified type names

The goal is to support real-project design inputs that need stable references to existing project types without reopening class-name guessing.

This slice does not support:

- sibling references between design entries in the same manifest batch
- payload entries acting as type providers for command/query entries
- automatic import of aggregate-generated enums
- source-code scanning

The registry is project-owned, explicit, and limited to `shortName -> FQN` mappings.

## Why This Slice

Recent real integration in `only-danmuku` exposed a gap between the current strict auto-import rules and real project input.

The current design generator now correctly rejects unsafe short-type guessing, but that also means a project must provide a stable identity source for existing project-local types when it does not want to write FQNs directly into every design field.

There are two categories of unresolved short types:

1. sibling design-entry names in the same manifest batch
2. existing project-local types such as enums or shared models

The first category is explicitly out of scope and will remain invalid design input.

The second category is a legitimate integration need. This slice addresses only that need and keeps the solution explicit.

## Goals

- Provide a project-owned explicit fallback for short type names in design generation
- Keep current safe type-resolution ordering intact
- Avoid class-name guessing and source scanning
- Keep the feature scoped to the design generator
- Preserve existing fail-fast behavior for unresolved or ambiguous types

## Non-Goals

- Support sibling design-entry references inside one manifest batch
- Let `payload` design entries serve as reusable type definitions for `cmd/qry`
- Auto-discover project types by scanning source code
- Introduce wildcard mappings, alias groups, or package-prefix heuristics
- Make aggregate-generated enums auto-importable in this slice
- Change aggregate generation ownership or enum-generation rules

## Scope Decision

Three approaches were considered.

1. add a project-level explicit type-registry file and use it as a strict fallback
2. add inline Gradle DSL registrations directly in `build.gradle.kts`
3. scan project sources and build a symbol index automatically

The recommended choice is option 1.

Reasons:

- it keeps project type knowledge out of Gradle build scripts
- it remains explicit and easy to diff
- it avoids source scanning and class-name guessing
- it does not pollute pipeline core or aggregate generation rules

## Design Contract

### New Project-Level Configuration

`cap4k` adds a new top-level block:

```kotlin
cap4k {
  types {
    registryFile.set("iterate/type-registry.json")
  }
}
```

This is project-level configuration, not framework-global configuration.

It is not a `source`, not a `generator`, and not a template concern.

It exists only to help design generation resolve explicitly registered short type names.

### Registry File Format

The registry file is a JSON object:

```json
{
  "VideoStatus": "edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus",
  "QualityAuthPolicy": "edu.only4.danmuku.domain.aggregates.video_quality_policy.enums.QualityAuthPolicy"
}
```

Rules:

- keys must be simple type names
- values must be fully qualified type names
- one key maps to exactly one FQN

This slice does not support:

- multiple registries
- layered registry precedence
- one short name mapping to multiple candidates

## Resolution Order

The design generator resolves types in this fixed order:

1. Kotlin built-in types and standard collections
2. current generated unit inner types
3. explicit FQNs written directly in design fields
4. project type registry
5. unresolved -> fail-fast

This means the registry is a fallback only.

It must not override:

- built-in types such as `String` or `List`
- inner types already owned by the current generated command/query object
- explicit FQNs already written in the design file

## Explicitly Unsupported Inputs

The following remain invalid design input after this slice:

- `cmd/qry` fields referencing another design entry by short name
- `cmd/qry` fields referencing a `payload` entry name by short name
- any short type name that is not built-in, not inner, not explicit FQN, and not registered

Example:

- `List<VideoPostProcessingFileSpec>` remains invalid if `VideoPostProcessingFileSpec` is only another design entry name in the same manifest batch

This is intentional.

Each design entry is still treated as independent input.

## Error Behavior

### Registry Validation Errors

The system must fail fast for invalid registry configuration.

Examples:

- `type registry file does not exist: ...`
- `type registry file is not a valid JSON object: ...`
- `type registry entry key must be a simple type name: ...`
- `type registry entry value must be a fully qualified type name: ...`
- `type registry entry must not override built-in type: String`

### Design Type Resolution Errors

If a field still cannot be resolved, the generator must fail fast and include:

- field name
- raw type text
- why resolution failed
- how to fix it

Example error shape:

`failed to resolve type for field targetStatus: VideoStatus (unknown short type: VideoStatus; use a fully qualified name or register it in type-registry.json)`

For sibling design-entry references, the message should make the unsupported rule explicit.

Example:

`failed to resolve type for field fileList: List<VideoPostProcessingFileSpec> (short type VideoPostProcessingFileSpec is not registered; sibling design-entry references are not supported)`

## Architecture Impact

This slice should only affect the Gradle configuration layer and the design generator.

### `cap4k-plugin-pipeline-gradle`

- add `types.registryFile`
- resolve the configured path relative to project root
- validate presence only when the property is set
- load registry entries before building `ProjectConfig`

Recommended implementation choice:

- Gradle reads the file and passes normalized registry entries into `ProjectConfig`
- downstream generator code should not read files directly

### `cap4k-plugin-pipeline-api`

- extend `ProjectConfig` with a minimal project type-registry model
- keep it as normalized data, not file handles

### `cap4k-plugin-pipeline-generator-design`

- merge project registry entries into `DesignSymbolRegistry`
- keep current resolution priority unchanged
- continue to fail fast on unresolved or ambiguous types

### Modules Out of Scope

This slice should not modify:

- pipeline core stage ordering
- `design-json` source parsing rules
- aggregate generator ownership rules
- drawing-board or flow generation

## Testing Strategy

### Unit Tests

Add design-generator tests for:

- resolving a short type from project registry
- refusing to override built-in types
- preserving explicit FQN precedence over registry entries
- keeping inner types higher priority than registry entries
- unresolved sibling design-entry short type still failing

### Gradle Functional Tests

Add at least two fixtures:

1. success case
- design file contains a short project-local type
- registry file provides the FQN
- generated code imports the registered type

2. failure case
- design file references a sibling design-entry short type
- no registry mapping exists
- task fails with the explicit unsupported-reference message

## Risks

The main risk is accidentally broadening this feature into a generic symbol-discovery system.

That would make the framework unstable again by reintroducing guesswork.

This slice avoids that by enforcing:

- explicit project ownership
- explicit registry entries
- fallback-only usage
- no source scanning
- no sibling-entry reuse

## Follow-Up

This slice does not solve aggregate-generated enum auto-import.

If that becomes important later, it should be designed separately as:

- aggregate-owned enum registry
- explicit owner rules for shared enums
- symbol-aware import integration for known aggregate-generated enums

That is a later slice and should not be folded into this one.
