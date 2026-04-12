# Cap4k Design Generator Default Value Upgrade

## Summary

This slice upgrades the new pipeline design generator so that explicit field default values in design input can be rendered into stable Kotlin source.

The current design-generator pipeline already supports:

- object-scoped `Cmd/Qry`
- nested `Request/Response`
- nested inner types
- collision-safe type rendering
- helper-based `type()` / `imports()`
- project type registry fallback

What remains incomplete is the default-value path.

The current render model already carries `defaultValue`, but it is not yet treated as a generator-owned, validated Kotlin expression contract. Templates also do not emit default values.

This slice closes that gap.

## Why This Slice

The design generator is already structurally usable, but field declarations still miss one part that matters in real request/response models:

- explicit default values written by the project

This is the right next step because:

- it improves generated Kotlin usability directly
- it does not require reopening pipeline architecture work
- it does not force broader legacy-template compatibility work
- it can remain generator-local and deterministic

This slice intentionally does **not** introduce inferred defaults.

It only makes explicit defaults safe and renderable.

## Goals

- Support explicit `defaultValue` from design input for generated design fields
- Normalize supported defaults into Kotlin-safe expressions before they reach templates
- Keep default-value decisions inside generator code
- Keep templates thin and output-only
- Fail fast on unsupported or contradictory defaults
- Keep this work limited to design generation

## Non-Goals

- Infer defaults from field type when input omits `defaultValue`
- Infer defaults from database schema, aggregate metadata, or project code
- Reintroduce legacy runtime behavior
- Expand auto-import rules
- Expand project type registry rules
- Change aggregate, flow, or drawing-board generation
- Add a general-purpose default-value template helper

## Scope Decision

Three approaches were considered:

1. explicit default values only, with generator-side normalization
2. type-driven inferred defaults when input omits values
3. template-side default-value formatting

The recommended choice is option 1.

Reasons:

- it preserves generator ownership of syntax decisions
- it avoids silently inventing business defaults
- it keeps templates simple
- it gives immediate real-project value without reopening broader design-runtime complexity

Option 2 is too speculative for this stage.

Option 3 would push correctness-sensitive logic back into Pebble templates and weaken maintainability.

## Design Principles

This slice follows four rules:

1. only explicit user-provided defaults are considered
2. formatted defaults must already be valid Kotlin source before entering the render model
3. unsupported defaults fail fast rather than degrade silently
4. templates never decide what a default means

The practical effect is:

- generator code validates and formats
- templates only emit `= ...`

## Supported Default Categories

This slice supports a bounded set of default-value categories.

### `null`

Supported only when the field is nullable.

Rendered as:

- `null`

If a field is non-nullable and the input default is `null`, generation fails.

### `String`

Supported forms:

- raw string such as `demo`
- already-quoted string such as `"demo"`
- already-single-quoted string such as `'demo'`

Behavior:

- raw string values are normalized to double-quoted Kotlin string literals
- already-quoted strings are preserved

This slice does not attempt deep semantic interpretation beyond safe literal normalization.

### Numeric Literals

Supported categories:

- `Int`
- `Long`
- `Double`
- `Float`

Behavior:

- valid numeric literals are preserved
- `Long` literals are normalized to Kotlin form and may receive an `L` suffix when needed
- invalid numeric literals fail fast

### `Boolean`

Only these values are accepted:

- `true`
- `false`

Any other input fails fast.

### Explicit Constant Expressions

Examples:

- `VideoStatus.PROCESSING`
- `java.time.LocalDateTime.MIN`

When the input clearly looks like a code expression rather than a literal string, it is preserved as-is.

This slice treats these as explicit expressions, not as candidates for quoting or reinterpretation.

### Limited Empty Collection Expressions

Supported forms:

- `emptyList()`
- `emptySet()`
- `mutableListOf()`
- `mutableSetOf()`

These are preserved as-is.

This slice does not attempt to support arbitrary collection construction syntax.

## Unsupported Categories

This slice intentionally does not support:

- inferred defaults for omitted values
- object construction expressions that require semantic validation
- broad collection expressions such as arbitrary `listOf(...)` / `mapOf(...)`
- time-string to time-object conversion
- enum short-name completion
- coupling default formatting with import inference

Unsupported input fails fast.

## Render Model Contract

The existing design render model already includes `defaultValue`.

This slice strengthens its meaning:

- `defaultValue` must represent a Kotlin-ready right-hand-side expression
- it is no longer treated as raw user text after formatting

Examples of valid render-model values:

- `"demo"`
- `0`
- `1L`
- `true`
- `null`
- `VideoStatus.PROCESSING`

This contract keeps all formatting and validation in generator code and keeps templates simple.

## Template Contract

Default design templates currently emit fields without default values.

This slice updates them from:

```pebble
val {{ field.name }}: {{ type(field) | raw }}
```

to:

```pebble
val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}
```

This is intentionally small:

- no new default-value helper
- no template-side formatting
- no template-side type-aware decisions

## Error Handling

This slice prefers explicit errors over permissive output.

### Invalid `null`

If:

- `defaultValue = "null"`
- and field is non-nullable

Then generation fails.

### Invalid Boolean Literal

If a Boolean field default is not `true` or `false`, generation fails.

### Invalid Numeric Literal

If a numeric field default cannot be interpreted as a valid numeric literal for the target type, generation fails.

### Unsupported Free-Form Expression

If the default value is neither:

- supported literal
- supported empty-collection expression
- nor a clear explicit constant/code expression

generation fails.

The failure message should include:

- field name
- original type
- original default value

so the project can fix the design input directly.

## Implementation Boundary

This slice should remain inside design generation and template rendering only.

Expected implementation locations:

- `cap4k-plugin-pipeline-generator-design`
  - `DesignRenderModelFactory.kt`
  - a small generator-local formatter such as `DefaultValueFormatter.kt`
  - render-model semantics

- `cap4k-plugin-pipeline-renderer-pebble`
  - `presets/ddd-default/design/command.kt.peb`
  - `presets/ddd-default/design/query.kt.peb`

Required tests:

- design-generator unit tests for formatting and failure paths
- renderer tests for template emission
- Gradle functional tests for end-to-end generation

This slice should not require changes in:

- pipeline core
- source providers
- aggregate/flow/drawing-board generators
- project type registry design

## Testing Strategy

### Generator Unit Tests

Cover at least:

- raw string -> quoted string
- already-quoted string remains unchanged
- `Long` receives `L` when needed
- valid Boolean values succeed
- invalid Boolean values fail
- nullable field with `null` succeeds
- non-nullable field with `null` fails
- explicit constant expression is preserved
- supported empty collection expressions are preserved
- invalid default values fail fast

### Renderer Tests

Verify that default templates render:

- no `= ...` segment when `defaultValue` is absent
- `= ...` when `defaultValue` is present

Renderer tests should not duplicate formatter semantics in depth.

### Gradle Functional Tests

Add a design fixture that generates fields such as:

- `val status: VideoStatus = VideoStatus.PROCESSING`
- `val title: String = "demo"`
- `val retryCount: Long = 1L`

Add a failing fixture for unsupported default input.

## Expected Outcome

After this slice:

- design input may provide explicit default values for supported cases
- generated `Cmd/Qry` Kotlin files render those defaults correctly
- invalid defaults are rejected early and clearly
- the generator becomes more useful in real project usage without broadening the architecture surface
