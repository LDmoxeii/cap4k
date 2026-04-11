# Cap4k Design Generator Auto-Import Upgrade

## Summary

This design upgrades auto-import behavior for the new pipeline design generator.

The previous design-generator slices already established:

- object-scoped `Cmd/Qry` generation
- nested `Request/Response` structures
- parsed type trees
- collision-safe type rendering
- thin template helpers for `type()` and `imports()`

What remains weak is symbol sourcing for import decisions.

The current generator can safely render explicit FQNs and already avoids unsafe shortening in many cases, but it still lacks a dedicated symbol-identity layer for controlled auto-import decisions.

This slice adds a conservative auto-import model based on:

- explicit FQNs
- current generated-unit inner types
- pipeline-known symbols
- fail-fast behavior for unresolved or ambiguous short names

It explicitly does **not** restore class-name guessing.

## Why This Slice

The real-project integration round exposed the practical boundary clearly:

- generated output is already structurally usable
- type rendering is already much safer than the legacy class-name map
- but import behavior still needs a stronger symbol source before it can be trusted in more realistic projects

The user also established a hard requirement:

- future auto-import must not rely on simple class-name mapping
- same simple names from different packages must not silently resolve to the wrong import

That means the next correct move is not broader template compatibility or default-value work.

The next correct move is a stronger import truth source.

## Goals

- Introduce a controlled symbol registry for design generation
- Base auto-import on symbol identity rather than class-name mapping
- Support safe shortening when symbol identity is unique
- Keep explicit FQNs as the highest-priority truth source
- Keep current generated-unit inner types non-imported and higher priority than external symbols
- Fail fast when unresolved or ambiguous short names would make generated code unsafe
- Preserve generator ownership of all import decisions

## Non-Goals

- Scan the whole project source tree to discover symbols
- Reintroduce legacy class-name import guessing
- Reintroduce `use()`
- Implement default-value inference
- Change aggregate, flow, or drawing-board generation
- Move import logic into Pebble templates
- Change pipeline core or source-provider contracts

## Scope Decision

Three approaches were considered:

1. a controlled symbol registry fed only by pipeline-known information
2. whole-project source scanning to build a wide symbol index
3. a user-maintained import-map file

The recommended choice is option 1.

Reasons:

- it preserves architecture boundaries
- it stays deterministic
- it avoids source-tree coupling and performance drag
- it solves the real class-name conflict problem without reintroducing heuristics

Option 2 is too expensive and too brittle for this stage.

Option 3 can still exist later as an escape hatch, but it should not be the primary path.

## Design Principles

This slice follows five rules:

1. explicit identity beats inferred identity
2. inner generated types beat external types with the same simple name
3. symbol identity is the truth source, not simple name
4. ambiguity is an error, not a guess
5. templates consume resolved output but do not participate in resolution

The practical consequence is:

- safe imports may be emitted
- conflicting symbols remain qualified
- unresolved short names fail fast

## Symbol Source Layers

Symbol sourcing is ordered and non-overridable.

### 1. Explicit Type Identity

If a field type is written as a fully qualified name, that is the truth source.

Examples:

- `java.time.LocalDateTime`
- `com.foo.Status`

No inferred rule may override an explicit FQN.

### 2. Current Generated-Unit Inner Types

The current generated `Cmd/Qry` contributes its own inner symbols:

- `Request`
- `Response`
- nested request types
- nested response types

These symbols are never imported.

They also take precedence over external symbols that share the same simple name.

### 3. Pipeline-Known Symbols

The generator may register symbols that are already known from pipeline data.

Examples:

- KSP-derived aggregate identities
- generator-known package conventions
- future design-related known symbols that have complete package identity

These symbols may participate in auto-import only when their identity is complete and stable.

### 4. Unknown Short Names

Short names without explicit identity and without a registry hit remain unresolved.

This slice does not guess them from project source layout.

## Symbol Model

This slice introduces a dedicated symbol identity model.

### `SymbolIdentity`

Recommended fields:

- `packageName`
- `typeName`
- optional `moduleRole`
- optional `source`

This object represents real identity.

It is not a string alias.

### `DesignSymbolRegistry`

The registry stores known identities for the current generation run.

It should:

- index by simple name for lookup
- retain full identity for all candidates
- allow multiple candidates for the same simple name

The registry must not collapse:

- `com.foo.Status`
- `com.bar.Status`

into a single `Status`.

## Auto-Import Resolution Order

Import resolution should follow a strict decision sequence.

### Built-In Types And Standard Collections

Examples:

- `String`
- `Long`
- `Boolean`
- `List<T>`
- `Set<T>`
- `Map<K, V>`

Behavior:

- never import
- render directly

### Inner Generated Types

Examples:

- `Request`
- `Response`
- `Address`
- `Snapshot`

Behavior:

- never import
- render by inner short name

### Explicit FQNs

Behavior:

- if the simple name is unique among external candidates, emit import and render short name
- if the simple name conflicts with another external symbol or an inner symbol, do not import and render the FQN

### Unique Registry Hits For Short Names

If a short name maps to exactly one controlled symbol identity:

- emit import
- render short name

### Ambiguous Registry Hits

If a short name maps to multiple candidates:

- do not guess
- fail fast for compile-oriented generation

### Unknown Short Names

If a short name has no identity source:

- do not guess
- fail fast for compile-oriented generation

## Conflict Policy

This slice intentionally prefers correctness over permissiveness.

### Inner-Type Conflict

If an external symbol shares a simple name with an inner generated type:

- the inner type keeps the short name
- the external symbol remains fully qualified
- no import is emitted for the external symbol

### External Conflict

If two external symbols share a simple name:

- explicit FQNs may still be rendered as FQNs
- short-name-based resolution must fail fast

### Unknown Symbol

If a short-name symbol has no explicit identity and no unique registry candidate:

- fail fast with a clear diagnostic

## Generator Boundary

This slice should remain inside `cap4k-plugin-pipeline-generator-design` plus tests and downstream functional coverage.

Expected implementation areas:

- new symbol and resolver classes in the design generator module
- render-model enrichment inside the design generator
- planner integration
- design-generator tests
- Gradle functional tests

This slice should not require changes in:

- pipeline core
- source providers
- aggregate/flow/drawing-board generators

## Template Contract

Templates should continue to consume:

- `imports`
- `field.renderedType`
- thin helper accessors layered on top of those values

Templates must not:

- resolve symbols
- infer package names
- decide whether an import is safe

This slice improves generator-side import decisions, not template-side intelligence.

## Error Handling

The generator should fail fast with clear diagnostics in these cases:

- ambiguous short name with multiple registry candidates
- unknown short name with no symbol identity source
- invalid type metadata that cannot be resolved into a rendered type

The diagnostics should clearly indicate:

- the field name
- the raw type text
- why resolution failed

This keeps failures actionable and avoids silent bad imports.

## Testing Strategy

Testing should stay layered.

### Generator Tests

Add focused tests for:

- explicit FQN imported safely when unique
- explicit FQN kept qualified when conflicting
- inner type winning over external same-name symbol
- unique registry hit for short name
- ambiguous short name fail-fast
- unknown short name fail-fast

### Renderer Tests

No new import-decision logic should live here.

Renderer tests only need to confirm templates still consume the precomputed output.

### Functional Tests

Add end-to-end design generation coverage for:

- safe import
- FQN-preserved conflict
- inner-type precedence
- unresolved short-name failure path

## Risks

### Risk 1: Reintroducing class-name heuristics through the registry

If the registry collapses multiple identities into a single simple-name decision, this slice fails its main goal.

Mitigation:

- store full symbol identities
- allow multiple candidates per simple name
- make ambiguity explicit

### Risk 2: Expanding scope into source scanning

If this slice starts scanning modules for Kotlin symbols, it will become slower, blur architecture boundaries, and produce harder-to-debug results.

Mitigation:

- keep symbol sourcing limited to explicit FQNs and pipeline-known symbols

### Risk 3: Over-permissive fallback

If unresolved short names are silently emitted, the generator pushes the ambiguity downstream and weakens trust.

Mitigation:

- fail fast for compile-oriented generation

## Follow-Up Slices

If this slice lands cleanly, the next likely upgrades remain:

- default-value inference
- broader helper/runtime compatibility where still worthwhile
- possible user-supplied symbol augmentation if a clear need emerges

The key rule after this slice is:

- import ergonomics may improve
- import authority must remain generator-owned and identity-based
