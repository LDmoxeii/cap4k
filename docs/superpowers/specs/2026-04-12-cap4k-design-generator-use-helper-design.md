# Cap4k Design Generator `use()` Helper Design

Date: 2026-04-12
Status: Draft for review

## Background

The pipeline design generator already supports:

- structured request/response and nested type rendering
- stable `type()` helper behavior via `renderedType`
- stable `imports()` helper behavior via precomputed import lists
- symbol-aware import resolution, project type registry, and explicit default value rendering

What is still missing relative to the old codegen template experience is a narrow, explicit way for templates to request additional imports without reopening the old implicit import/runtime model.

The old `cap4k-plugin-codegen` templates use `use(...)` heavily. The pipeline templates do not currently support it. This creates a gap for templates that need to opt into explicit imports while keeping type resolution, auto-import, and symbol identity decisions in Kotlin code rather than Pebble templates.

This slice introduces a minimal, explicit `use()` helper for the pipeline design templates only.

## Goal

Add a thin `use()` helper to the pipeline Pebble renderer that:

- is available to pipeline design templates
- accepts only explicit FQN string inputs
- registers an additional import for the current artifact render
- merges with the existing import list used by `imports()`
- does not participate in type inference or symbol resolution
- does not restore the legacy codegen template runtime

## Non-Goals

This slice will not:

- restore the old codegen `use()` runtime semantics
- allow short-name imports such as `use("LocalDateTime")`
- allow wildcard imports
- allow `use()` to influence `type()` output
- allow `use()` to resolve sibling design-entry references
- change generator-side import resolution
- change project type registry behavior
- add support for aggregate, flow, or drawing-board templates

## Recommended Approach

### Option 1: Design-template-only explicit import helper

Expose a narrow `use()` helper in the pipeline Pebble extension, intended for design templates only.

Behavior:

- `use("java.time.LocalDateTime")` registers that FQN as an explicit import for the current artifact render
- `use()` returns an empty string so it can be used as a top-of-template declarative statement
- explicit imports are merged with the render model `imports` list before final import emission

Pros:

- smallest behavior surface
- consistent with the existing “decision in Kotlin, declaration in template” design
- useful immediately for design templates

Cons:

- not globally available to all template categories yet

### Option 2: Generic helper for every pipeline template

Expose `use()` to all pipeline template categories.

Pros:

- uniform API

Cons:

- larger scope without a proven need outside design templates
- expands testing and conflict surface unnecessarily

### Option 3: Restore a broader legacy runtime

Make `use()` part of a larger old-style runtime with tighter interaction between imports, types, and template-time decisions.

Pros:

- closer to old codegen behavior

Cons:

- reintroduces old complexity
- weakens the new pipeline architecture boundaries

### Recommendation

Implement Option 1.

This solves the current template ergonomics gap without reopening type/import resolution logic inside templates.

## Helper Contract

### Supported usage

The helper is intended to be used like this:

```pebble
{{ use("java.time.LocalDateTime") -}}
package ...

{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

### Rules

- `use()` accepts exactly one argument
- the argument must be a string
- the string must be a valid fully qualified type name
- the helper returns an empty string
- the helper only registers an explicit import for the current render

### Unsupported usage

These remain invalid:

- `use()`
- `use("LocalDateTime")`
- `use("java.time.*")`
- `use(badValue)` where `badValue` is not a string
- using `use()` to repair unresolved sibling design entry short names

## Interaction with Existing Helpers

### `type()`

`type()` remains unchanged.

It continues to render:

- a plain string input, or
- a field-like object exposing `renderedType`

`use()` must not change the rendered type string.

### `imports()`

`imports()` remains the only helper that emits the final normalized import list.

The final import set will be computed from:

- render-model imports already provided by the generator
- explicit imports registered through `use()` during this artifact render

This keeps import output flowing through one normalization path.

## Conflict Policy

The helper must remain conservative.

### Valid explicit imports

If the explicit import:

- is a valid FQN
- does not collide with the current import/simple-name set

then it is added to the final import list.

### Duplicate explicit imports

If the same FQN is registered multiple times:

- it is kept once

### Collision behavior

If an explicit import introduces a conflicting simple name against:

- an already computed import, or
- another explicit `use()` import

then rendering fails fast.

Example:

- existing computed import: `com.foo.Status`
- template says: `{{ use("com.bar.Status") }}`

Result:

- fail fast with a clear import conflict message

This slice will not attempt to downgrade one side to FQN automatically.

## Rendering Lifecycle Design

The explicit import collector must be scoped to a single artifact render.

Recommended behavior:

- each artifact render creates a fresh explicit-import collector
- the Pebble extension instance used for that render owns the collector reference
- `use()` appends into that collector
- after template execution, the renderer merges explicit imports with the existing import list
- the collector is discarded after the artifact completes

This prevents:

- cross-artifact leakage
- global mutable state
- concurrency contamination if rendering strategy changes later

## Module Boundaries

This slice is limited to:

- `cap4k-plugin-pipeline-renderer-pebble`

It should not require changes to:

- `cap4k-plugin-pipeline-generator-design`
- `cap4k-plugin-pipeline-api`
- `cap4k-plugin-pipeline-core`
- `cap4k-plugin-pipeline-gradle`

Generator-side imports remain the source of truth for computed imports. The renderer only adds a narrow explicit import registration layer.

## Error Messages

Expected fail-fast errors should be direct and user-facing.

Examples:

- `use() requires exactly one argument.`
- `use() requires a string fully qualified type name.`
- `use() requires a fully qualified type name: LocalDateTime`
- `use() import conflict: Status is already bound to com.foo.Status, cannot also import com.bar.Status`

The wording may vary slightly in implementation, but the message should always identify:

- that `use()` is the failing helper
- what input was invalid
- when applicable, what symbol is already bound

## Testing Strategy

### Renderer helper tests

Add Pebble renderer tests for:

1. basic `use()` success
   - `use("java.time.LocalDateTime")`
   - explicit import appears in final normalized imports

2. merge with existing imports
   - precomputed imports plus explicit `use()`
   - combined output is deduplicated and stable

3. duplicate `use()` calls
   - same FQN declared multiple times
   - output contains one import

4. invalid calls
   - missing argument
   - non-string input
   - short name
   - wildcard import
   - invalid FQN format

5. collision failure
   - conflicting simple names between computed import and explicit import
   - conflicting explicit imports across multiple `use()` calls

### Design template coverage

Add or adjust design-template-level renderer tests to confirm:

- `use()` can be used in design templates without changing `type()` semantics
- imports still flow through `imports(imports)`

## Rollout Notes

This slice intentionally does not convert the default design templates to start using `use()` immediately.

First, the helper is introduced and verified. Template adoption can then happen incrementally and only where explicit imports improve readability or maintainability.

## Acceptance Criteria

The slice is complete when:

- `use()` is available in pipeline design template rendering
- only explicit FQN string arguments are accepted
- `use()` registers imports without changing `type()` behavior
- final imports merge computed imports and explicit imports through the existing normalization path
- duplicate imports are deduplicated
- conflicting imports fail fast
- renderer tests cover success and failure paths
- no generator/core/API changes are needed for this behavior
