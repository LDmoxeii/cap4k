# Cap4k Design Generator Template Helper Upgrade

## Summary

This design adds a thin template-helper layer for the new pipeline design generator.

The previous slices already established:

- object-scoped `Cmd/Qry` generation
- nested `Request/Response` structures
- parsed and resolved type modeling
- collision-safe import planning
- precomputed `renderedType` and `imports`

What remains awkward is template ergonomics.

Templates currently work, but they still read low-level context structures directly:

- `field.renderedType`
- `imports`

This is correct, but it is more verbose than necessary and does not yet provide a stable helper-level template API.

This slice introduces lightweight Pebble helpers for `type()` and `imports()` without moving type or import decision-making back into the template layer.

## Why This Slice

The last completed slice established the real foundation:

- parser
- resolver
- import planner
- rendered type propagation into render models

That means the system now has a trustworthy source of truth for:

- what type should be rendered
- whether a type should stay qualified
- which imports are safe to emit

At this point, adding a thin helper layer is useful because:

- it improves template readability
- it stabilizes a template-facing API
- it avoids leaking internal render-model property names across every template
- it still keeps all real type/import reasoning in Kotlin code

This is the right moment to add helpers because the data contract is now strong enough. Doing it earlier would have frozen weak heuristics into the template interface.

## Goals

- Add a thin `type()` helper for Pebble templates
- Add a thin `imports()` helper for Pebble templates
- Keep helpers read-only and formatting-oriented
- Preserve generator ownership of all real type and import decisions
- Allow default design templates to migrate from raw property reads to helper usage
- Keep generated output behavior unchanged

## Non-Goals

- Reintroduce the legacy `use()` runtime
- Reintroduce full legacy `imports()` semantics
- Reintroduce template-side type parsing or nullable assembly
- Let templates infer imports from fields
- Let templates guess package names or resolve symbols
- Move helper logic into pipeline core
- Change aggregate, flow, or drawing-board generation

## Scope Decision

Three possible approaches were considered:

1. add thin read-only Pebble helpers on top of existing precomputed context
2. avoid helpers entirely and keep exposing raw render-model fields
3. restore a larger legacy-style helper/runtime layer with `type()`, `imports()`, and `use()`

The recommended choice is option 1.

Reasons:

- it improves template readability without changing data ownership
- it keeps the contract aligned with the current generator architecture
- it avoids generator-side formatting leakage such as prebuilt source lines
- it avoids reintroducing legacy runtime complexity too early

## Current Behavior

The default design templates currently render directly from the raw context:

- iterate `imports`
- emit `field.renderedType`

This works, but it couples every template to current property names and low-level structure.

The current renderer extension, in `PipelinePebbleExtension`, only exposes:

- `json`

There is no template helper API yet for design-specific type/import consumption.

## Design Principles

This slice follows three rules:

1. helpers may read precomputed decisions, but may not make new decisions
2. helpers should reduce template noise, not introduce another type system
3. template behavior after migration should remain equivalent to direct property reads

The practical consequence is:

- `type()` returns an already-computed type string
- `imports()` returns an already-computed import list
- neither helper performs symbol resolution, parsing, or collision decisions

## Helper API

Two helpers are introduced.

### `type()`

Primary purpose:

- make template field emission easier to read

Target usage:

```pebble
{{ type(field) }}
```

Allowed inputs:

- a render-field object containing `renderedType`
- a map containing `renderedType`
- a plain string, which is returned unchanged

Behavior:

- if input exposes `renderedType`, return that value
- if input is a string, return it unchanged
- otherwise fail fast with a clear template-usage error

Explicitly out of scope:

- parsing raw type expressions
- combining nullable flags
- deciding imports
- resolving aliases
- taking extra behavior parameters

This is not a type engine. It is a template readability helper.

### `imports()`

Primary purpose:

- make template import emission more uniform

Target usage:

```pebble
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

Preferred input:

- `List<String>`

Optional tolerated input:

- a map containing `imports`

Behavior:

- return a stable list of imports
- preserve the generator-provided order
- deduplicate repeated values if needed
- return an empty list for empty input

Explicitly out of scope:

- deriving imports from fields
- sorting by package policy beyond stable normalization
- filtering by package scope
- resolving collisions

Again, this helper is consumption-oriented, not decision-oriented.

## Module Boundary

This slice should stay entirely inside `cap4k-plugin-pipeline-renderer-pebble` plus tests and default design templates.

Expected implementation locations:

- `PebbleArtifactRenderer.kt`
  - extend `PipelinePebbleExtension`
  - register helper functions alongside the existing `json` filter support

- default design templates
  - `design/command.kt.peb`
  - `design/query.kt.peb`

- renderer tests
  - `PebbleArtifactRendererTest.kt`

- functional regression tests
  - `PipelinePluginFunctionalTest.kt`

This slice should not require changes in:

- pipeline core
- source providers
- aggregate/flow/drawing-board generators

## Template Migration Strategy

Default design templates should migrate from:

```pebble
{{ field.renderedType }}
{% for import in imports %}
```

to:

```pebble
{{ type(field) }}
{% for import in imports(imports) %}
```

This migration is intended to improve readability and create a stable helper contract.

It should not change generated output.

The migration should remain narrow:

- only the default design templates move to the helper form
- no mass rewrite of unrelated templates is needed

## Error Handling

Helpers should fail fast on invalid usage.

### `type()`

Fail when:

- input is null
- input does not expose `renderedType`
- input is neither string nor supported field/map shape

Error message should clearly say the template helper was used with an unsupported value.

### `imports()`

Fail when:

- input is neither a string list nor a supported map shape

For empty or missing import lists within a supported shape:

- return an empty list rather than failing

This balance keeps template debugging sharp without making normal empty-import cases noisy.

## Testing Strategy

Testing should stay layered.

### Renderer Tests

Add focused tests for:

- `type(field)` returns `renderedType`
- `type("String")` returns `String`
- `imports(imports)` returns the expected stable list
- helper misuse produces clear failures
- default design templates still render:
  - safe imports
  - qualified collision types
  - nested inner types
  - nullable rendered types

### Functional Tests

Do not retest helper internals.

Instead verify that end-to-end generated output remains correct when default templates use helpers:

- safe import still imported
- collision imports still suppressed
- qualified collision types still rendered fully qualified
- nested inner types still rendered under `Request` and `Response`
- nullable fields still remain nullable

## Why `use()` Stays Deferred

`type()` and `imports()` have a clean data source today:

- `renderedType`
- `imports`

`use()` does not.

It would immediately expand the scope into:

- context aliasing
- template variable registration
- cross-template helper state
- legacy compatibility semantics

That would turn a narrow renderer helper slice into a partial template-runtime rewrite.

So this design explicitly does not introduce:

- a placeholder `use()` API
- a half-compatible compatibility shim
- any template-side stateful helper system

## Risks

### Risk 1: Smuggling logic back into templates

If `type()` starts combining nullable flags, flattening generics, or choosing imports, this slice breaks its own architecture boundary.

Mitigation:

- keep helper implementation read-only
- make helpers depend only on precomputed fields

### Risk 2: Overfitting helper signatures to current templates

If helpers support too many shapes too early, their API will become hard to reason about.

Mitigation:

- support only the minimum shapes needed:
  - field-like object or map for `type()`
  - import list or import-containing map for `imports()`

### Risk 3: Pretending this is legacy compatibility

This slice improves ergonomics, not legacy parity.

Mitigation:

- keep helper semantics narrow
- do not treat this as a reintroduction of legacy runtime behavior

## Follow-Up Slices

If this helper slice lands cleanly, the next design-generator upgrades should remain:

- richer auto-import behavior when more explicit symbol identity becomes available
- default-value inference
- broader legacy compatibility only where still worthwhile
- eventual reconsideration of `use()` only if a strong scoped need emerges

The key rule after this slice is:

- helper ergonomics may grow
- helper authority may not

Generator code must remain the source of truth for type and import decisions.
