# Cap4k Analysis Output Correctness Design

Date: 2026-05-06
Status: Proposed

## Summary

This slice closes one narrow correctness group in the `analysis -> drawing-board -> flow` output chain:

- `#1` restore `CommandHandlerToEntityMethod` for top-level aggregate behavior extensions
- `#2` disable HTML escaping in drawing-board JSON output
- `#3` preserve stable `defaultValue` expressions through analysis and drawing-board projection

The goal is to repair the current broken outputs with the smallest defensible changes inside `cap4k`.

This is not a broad `irAnalysis` rewrite.
This is not a new projection architecture.
This is not a downstream dogfood execution slice.

## Backlog Source Of Truth

This design combines these open issues:

- `cap4k#1` `analysis: restore CommandHandlerToEntityMethod for top-level behavior extensions`
- `cap4k#2` `renderer: disable HTML escaping in drawing-board JSON output`
- `cap4k#3` `analysis: preserve stable defaultValue expressions through analysis and drawing-board projection`

The issue texts are the backlog boundary.
This document only turns that boundary into a focused implementation contract.

## Why This Slice Exists

Current dogfood evidence shows the output chain is losing or degrading information that already exists in source:

- command handlers calling aggregate behavior through top-level extension functions lose `CommandHandlerToEntityMethod`
- drawing-board JSON escapes generic angle brackets as `\u003c` and `\u003e`
- stable supported `defaultValue` expressions such as `null`, `emptyList()`, and enum constants do not survive analysis projection reliably

These are correctness problems, not new feature requests.

## Scope

Primary implementation focus:

- `cap4k-plugin-code-analysis-compiler`
- `cap4k-plugin-pipeline-renderer-pebble`

Secondary verification focus:

- `cap4k-plugin-code-analysis-flow-export`
- `cap4k-plugin-pipeline-generator-flow`
- `cap4k-plugin-pipeline-generator-drawing-board`

Modules expected to remain structurally unchanged unless a narrow test adjustment proves necessary:

- `cap4k-plugin-code-analysis-core`
- `cap4k-plugin-code-analysis-flow-export`
- `cap4k-plugin-pipeline-core`
- `cap4k-plugin-pipeline-source-ir-analysis`

## Non-Goals

This slice will not:

- do broad `irAnalysis` restructuring
- redesign flow rendering
- change unrelated anonymous-flow aggregation behavior
- introduce a new frontend or API projection topic
- create a new diagnostics subsystem for unsupported defaults
- redesign the drawing-board schema
- add arbitrary Kotlin expression serialization
- require downstream `only-danmuku-zero` dogfood execution inside this slice

## Current Failure Points

### `#1` Top-Level Aggregate Behavior Extensions

`CommandHandlerToEntityMethod` is currently recovered primarily from member-call ownership.

That works for behavior declared on aggregate entity classes.
It does not fully cover top-level extension functions in `*Behavior.kt`, where:

- the semantic target belongs to aggregate behavior
- the IR function owner is top-level or file-level
- the aggregate entity identity lives on the extension receiver type rather than on the function parent class

The result is that handler-to-entity-method edges disappear even though the command handler is calling aggregate behavior.

### `#2` HTML Escaping In Drawing-Board JSON

The flow generator already uses a Gson instance with HTML escaping disabled.

The shared Pebble `json` filter still uses default `Gson()`.
Drawing-board templates rely on that shared filter, so readable generic strings such as `List<Response>` become escaped strings such as `List\u003cResponse\u003e`.

The JSON remains valid, but artifact readability regresses.

### `#3` Stable `defaultValue` Projection

The analysis collector currently resolves defaults from a narrow constant-only path.

That preserves some literal values, but misses stable expressions that are already within the intended supported subset, including:

- `null`
- empty collection factories such as `emptyList()`
- enum or constant references such as `CaptchaChannel.INLINE`

Downstream source parsing and drawing-board projection can already carry string `defaultValue` values.
The main information loss happens at analysis collection time.

## Core Boundary Decision

The smallest correct fix is to repair information extraction at the earliest broken stage and keep downstream stages dumb.

That means:

- recover missing relationship edges in analysis, not by compensating in flow export
- preserve stable supported defaults in analysis, not by inventing a later normalization layer
- fix shared JSON escaping behavior in the renderer helper, not by adding drawing-board-specific escaping rules

## Design Decisions

### Decision 1: Restore `CommandHandlerToEntityMethod` From Extension Receiver Semantics

`cap4k-plugin-code-analysis-compiler` should continue to emit the existing relationship types:

- `CommandHandlerToEntityMethod`
- `AggregateToEntityMethod`
- existing `EntityMethodToDomainEvent`

No new relationship type is needed.

When a command handler calls a top-level function, the analysis should additionally check whether:

- the callee has an extension receiver
- the extension receiver type resolves to an aggregate entity

If so, the callee should be treated as an aggregate behavior surface for edge emission even when the callee parent is not the entity class itself.

The method identity should remain coherent with the current entity-method graph shape.
This slice should not redefine all entity-method IDs.
It only needs a stable, repeatable ID and display name that allow:

- handler-to-method edges
- aggregate-to-method edges
- existing domain-event traversal from the same method body

Member-method behavior must remain intact.

### Decision 2: Disable HTML Escaping In The Shared Pebble JSON Filter

The shared Pebble `json` filter should use `GsonBuilder().disableHtmlEscaping().create()`.

This is a shared renderer behavior change, not a drawing-board-only special case.

Expected effects:

- drawing-board JSON preserves readable generic strings
- other artifacts using the shared filter remain semantically valid JSON
- no schema-level or template-level contract changes are required

### Decision 3: Expand `defaultValue` Collection Only For A Stable Supported Subset

`cap4k-plugin-code-analysis-compiler` should expand `defaultValue` extraction from constant-only handling to a small explicit whitelist.

Supported in this slice:

- `null`
- string literals
- numeric literals
- boolean literals
- `emptyList()`
- `emptySet()`
- `emptyMap()`
- enum constant references
- stable constant or object references that can be rendered as a Kotlin-ready qualified expression

Not supported in this slice:

- string templates
- lambda expressions
- arbitrary function calls
- time-sensitive or runtime-dependent factories such as `now()`
- inferred or approximated replacements for unsupported expressions

Unsupported expressions must not be silently dropped.

For this slice, the narrow compliant behavior is:

- preserve the supported subset exactly
- fail fast in analysis projection when a declared default expression exists but is outside the supported subset
- emit a clear compiler-side error that surfaces which projected field or parameter could not be preserved

This slice still does not add a separate diagnostics file format or a new reporting subsystem.
The explicit surfacing mechanism is compiler-side failure, not silent omission.

### Decision 4: Preserve The Existing Downstream Contract

Once the compiler emits the corrected relationship edges and stable `defaultValue` strings:

- `source-ir` should keep parsing them as-is
- canonical assembly should keep carrying them as-is
- drawing-board generation should keep rendering them as-is
- flow generation should continue to rely on existing allowed edge types

This slice is intentionally upstream-heavy and downstream-light.

## File-Level Expectations

Expected production changes:

- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`

Expected test changes:

- compiler extraction / relationship tests under `cap4k-plugin-code-analysis-compiler`
- renderer tests under `cap4k-plugin-pipeline-renderer-pebble`
- light chain-verification tests in drawing-board or flow-related modules only if needed to prove no downstream regression

## Testing Strategy

### Compiler Regression Coverage

Add focused compiler-side regression tests for:

- command handler calling a top-level aggregate behavior extension emits `CommandHandlerToEntityMethod`
- the same path still emits `EntityMethodToDomainEvent` when the behavior body creates a domain event
- supported stable defaults survive analysis projection:
  - `null`
  - scalar literals
  - `emptyList()`
  - `emptySet()`
  - `emptyMap()`
  - enum or constant references
- unsupported defaults fail analysis projection with an explicit error message rather than disappearing from output

### Renderer Regression Coverage

Add renderer-level regression coverage proving that drawing-board JSON keeps readable generic strings such as:

- `List<Response>`
- `Map<String, Response>`

The assertions should verify the rendered artifact contains angle brackets directly and does not contain `\u003c` or `\u003e`.

### Chain-Level Light Coverage

Keep chain-level verification minimal.
Only add or update tests needed to prove the corrected `defaultValue` values still appear in drawing-board output.

This slice does not need a new large functional fixture if module-level regression tests already prove the contract.

## Acceptance Criteria

### Issue `#1`

- `CommandHandlerToEntityMethod` is emitted when a command handler calls aggregate behavior exposed as a top-level extension function
- existing member-method behavior remains intact
- `EntityMethodToDomainEvent` remains present for the same behavior path

### Issue `#2`

- drawing-board JSON preserves readable generic type strings with angle brackets
- semantic JSON structure remains unchanged
- renderer-level regression coverage exists for this behavior

### Issue `#3`

- supported stable defaults survive analysis projection
- those preserved defaults still appear in drawing-board JSON output
- unsupported defaults fail fast during analysis projection with an explicit compiler-side error
- regression coverage exists for:
  - `null`
  - scalar literals
  - empty collection factories
  - enum or constant references
  - at least one unsupported complex expression

## Verification Boundary

Required verification for this slice is limited to `cap4k` repository regression evidence:

- targeted module tests
- targeted regression tests added by this slice

Downstream dogfood verification against `only-danmuku-zero` is intentionally deferred.
That verification may still be desirable before issue closure, but it is not part of this implementation contract.

## Risks

### Risk: Over-Generalizing Extension Behavior Recovery

If the fix tries to treat every top-level function as an entity method candidate, false-positive edges will appear.

Mitigation:

- require aggregate-entity semantics on the extension receiver
- keep the new branch narrow to handler-call analysis

### Risk: Expanding `defaultValue` Too Far

If the collector attempts to serialize arbitrary IR expressions, the slice will grow into a new subsystem.

Mitigation:

- keep an explicit whitelist
- fail fast for unsupported expressions instead of trying to approximate or silently omitting them

### Risk: Shared JSON Filter Side Effects

Changing the shared `json` filter affects all artifacts using it.

Mitigation:

- keep the change limited to HTML escaping behavior
- prove behavior with renderer regression tests rather than template rewrites

## Final Position

This is a narrow correctness repair slice for one output chain.

The correct boundary is:

- fix missing semantics in analysis
- fix shared readability loss in the renderer helper
- prove the repaired values survive into drawing-board and flow-facing outputs

Anything broader would turn three bounded issues into a new architecture project, and that is explicitly out of scope.
