# Cap4k Design Generator Minimal Usable Upgrade

## Summary

This design upgrades the new pipeline design generator from a smoke-test shell into a minimally usable Kotlin request/response generator.

The current implementation proves that the pipeline architecture works, but the generated command and query outputs are still only:

```kotlin
package ...

class XxxCmd
```

This upgrade keeps the pipeline architecture unchanged and focuses only on improving the design generator output shape and template context.

The target output becomes object-scoped request/response types with nested request/response field structures, nested inner types, and a small but correct import/type rendering layer.

This slice intentionally does not yet reintroduce the full legacy template runtime such as `use()`, `imports()`, full auto-import logic, or default-value inference.

## Why This Slice

The pipeline migration has already completed the major architecture work:

1. `design-json + ksp-metadata + design generator`
2. `db + aggregate generator`
3. `ir-analysis + flow generator`
4. `ir-analysis + drawing-board generator`
5. repository Gradle DSL consolidation into `cap4k { ... }`

What remains weak is the design generator output quality.

Current problems:

- generated `Cmd/Qry` files are empty shells
- request and response fields are not rendered
- nested object structures are not represented
- import handling is too small to support realistic output
- the new generator is not yet close enough to replace the legacy generator for real use

This slice addresses the minimum viable output quality without dragging the full legacy template runtime back into the new pipeline.

## Goals

- Upgrade command and query generation from empty shell classes to structured object-scoped output
- Render request and response fields into Kotlin `data class` definitions
- Support nested request and response object types as inner classes under `Request` and `Response`
- Add a generator-local design render model for template consumption
- Add a minimal type rendering and import collection layer
- Keep import resolution aligned with symbol identity rather than simple class-name matching
- Keep the upgrade contained inside the design generator and its templates/tests

## Non-Goals

- Reintroduce the full legacy design template runtime
- Implement `use()`
- Implement the full legacy `imports()` function system
- Implement complete auto-import parity with the old generator
- Implement default-value inference
- Implement advanced alias systems
- Change pipeline stages, DSL, or non-design generators
- Change aggregate, flow, or drawing-board generation

## Scope Decision

Three implementation directions were considered:

1. strengthen the planner with a dedicated design render model and keep templates relatively simple
2. keep the planner thin and move most structure logic into Pebble templates
3. directly transplant the old design field resolver and template runtime into the new pipeline

The recommended choice is option 1.

Reasons:

- it keeps canonical input separate from generator-specific render structure
- it avoids pushing structural logic into templates
- it avoids reintroducing old task/context coupling
- it creates a clean base for later `type()`, `imports()`, auto-import, and default-value work

## Current Behavior

Today `DesignArtifactPlanner` only passes a minimal context:

- `packageName`
- `typeName`
- `description`
- `aggregateName`

Default templates are also minimal:

- `design/command.kt.peb`
- `design/query.kt.peb`

Both currently render only package plus an empty class declaration.

## Output Contract

The upgraded output should move to an object-scoped shape.

Recommended command output:

```kotlin
package com.example.demo.application.commands.order.submit

object SubmitOrderCmd {
    data class Request(
        val id: Long,
        val address: Address?
    ) {
        data class Address(
            val city: String,
            val zipCode: String
        )
    }

    data class Response(
        val success: Boolean
    )
}
```

Recommended query output:

```kotlin
package com.example.demo.application.queries.order.read

object FindOrderQry {
    data class Request(
        val id: Long
    )

    data class Response(
        val item: Item?
    ) {
        data class Item(
            val id: Long,
            val title: String
        )
    }
}
```

Important output rules:

- top-level generated type is `object XxxCmd` or `object XxxQry`
- `Request` and `Response` are inner types under that object
- nested request-only types are defined inside `Request`
- nested response-only types are defined inside `Response`
- nested type names do not repeat the outer command/query object name
- nested type names rely on the inner namespace for context

## Render Model Design

This slice should not expand `CanonicalModel` for design rendering concerns.

Instead, the design generator module should introduce generator-local render models.

Recommended models:

```kotlin
data class DesignFieldRenderModel(
    val name: String,
    val typeName: String,
    val nullable: Boolean = false,
    val isCollection: Boolean = false,
    val comment: String? = null,
)

data class DesignTypeRenderModel(
    val name: String,
    val fields: List<DesignFieldRenderModel>,
)

data class DesignRequestRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String?,
    val requestFields: List<DesignFieldRenderModel>,
    val responseFields: List<DesignFieldRenderModel>,
    val requestNestedTypes: List<DesignTypeRenderModel>,
    val responseNestedTypes: List<DesignTypeRenderModel>,
    val imports: List<String>,
)
```

Rules:

- `CanonicalModel.RequestModel` remains the generator input contract
- `DesignRequestRenderModel` is generator-local and template-facing
- import computation and nested-type flattening live in generator code, not template code

## Field Parsing Rules

This slice should support a bounded field system.

### Supported Inputs

1. scalar types
   - `String`
   - `Int`
   - `Long`
   - `Boolean`
   - `Double`
   - `Float`
   - explicit time or library types when already fully described by the input

2. nullable fields
   - if the input marks a field nullable, render `Type?`

3. collection fields
   - support one-layer collection wrappers such as:
     - `List<T>`
     - `Set<T>`
     - `MutableList<T>`

4. nested object fields
   - nested request or response structures should produce inner `data class` types under `Request` or `Response`

### Out of Scope for This Slice

- advanced generic nesting rules
- map-specific modeling improvements
- default-value inference
- type alias expansion
- complete legacy field-syntax compatibility

## Nested Type Strategy

Nested types should live inside the nearest relevant namespace rather than receive long flattened names.

Examples:

- `SubmitOrderCmd.Request`
- `SubmitOrderCmd.Request.Address`
- `SubmitOrderCmd.Response`
- `SubmitOrderCmd.Response.Item`

This design choice is intentional.

Reasons:

- the outer command/query object already provides context
- call sites naturally use dotted access such as `SubmitOrderCmd.Request`
- nested types avoid repeating the main generated type name
- fewer imports are needed because inner types remain namespaced

Conflict rule:

- if two nested structures within the same `Request` or `Response` namespace resolve to the same generated type name, fail fast
- do not auto-rename or guess

## Type Rendering Rules

This slice should add a minimal but correct type renderer.

### Basic Rules

- built-in Kotlin scalar types render as-is
- nullable types append `?`
- collection wrappers preserve their outer type and render the inner type recursively
- inner nested request/response types render by short name

Examples:

- `String`
- `Long?`
- `List<String>`
- `List<Address>`

### Unknown Type Handling

The generator must not rely on class-name-only matching.

Rules:

1. explicit FQN wins
2. known cap4k-internal symbol identity wins over short-name guessing
3. short-name-only lookup is fallback only if the result is unique
4. if resolution is ambiguous, do not guess

Fallback behavior:

- if an unambiguous FQN exists, render FQN or import it
- if no stable identity exists, fail fast

This rule is critical because the old class-name-based import approach is known to break when different packages contain the same type name.

## Import Rules

This slice should generate imports conservatively.

### Import What Is Safe

- explicit external FQNs that are not in the current package
- known external types whose symbol identity is stable

### Do Not Auto-Import Unsafely

- ambiguous short names
- types whose identity cannot be determined
- nested request/response types local to the current generated file

### Conflict Policy

If an import candidate is ambiguous:

- prefer rendering the full qualified name when available
- otherwise fail fast

Never choose an import target solely because the simple class name matches.

## Planner Changes

`DesignArtifactPlanner` should be upgraded to:

- build a render model from each canonical request
- compute field lists
- compute nested request and response types
- compute imports
- pass the richer context into the existing command/query templates

Recommended additional context keys:

- `imports`
- `requestFields`
- `responseFields`
- `requestNestedTypes`
- `responseNestedTypes`

Existing keys should remain:

- `packageName`
- `typeName`
- `description`
- `aggregateName`

## Template Changes

The default design templates should evolve from empty-shell rendering into object-scoped rendering.

### `design/command.kt.peb`

Should render:

- package declaration
- import block
- `object XxxCmd`
- inner `data class Request`
- inner `data class Response`
- request nested types under `Request`
- response nested types under `Response`

### `design/query.kt.peb`

Should render the same structure with `XxxQry`.

The templates should stay mostly declarative:

- render prepared field lists
- render prepared nested types
- do not attempt complex structural inference inside Pebble

## Testing Strategy

This slice should use the same layered test style as the rest of the pipeline.

### Design Planner Tests

Add tests for:

- request and response fields flowing into context
- nested request types
- nested response types
- fail-fast on duplicate nested type names in the same namespace
- minimal import collection behavior
- ambiguous import cases failing clearly

### Renderer Tests

Add Pebble rendering tests for:

- command template rendering object/request/response shape
- query template rendering object/request/response shape
- nested inner type rendering
- import block rendering

### Functional Gradle Tests

Extend the existing design fixture expectations so generated files prove:

- `object XxxCmd` / `object XxxQry`
- inner `Request` / `Response`
- nested types are present when source design fields require them

The fixture does not need to cover every edge case. One nested request example and one nested response example are enough.

## Error Handling

This slice should fail clearly for:

- duplicate nested type names within one `Request` or `Response` namespace
- unresolved type identity
- ambiguous external type candidates without explicit FQN
- invalid field structure that cannot be normalized into a stable render model

Recommended failure style:

- identify whether the error belongs to request or response rendering
- include generated type name
- include conflicting field or type name

## Risks And Mitigations

### Risk: Smuggling legacy runtime complexity back in

If the implementation starts recreating the old template helper runtime inside this slice, the scope will expand and the new generator boundary will blur.

Mitigation:

- keep the upgrade limited to render-model preparation plus template rendering
- explicitly defer `use()`, `imports()`, and full auto-import parity

### Risk: Reintroducing unsafe class-name-based import behavior

This is already a known problem in the old system.

Mitigation:

- require symbol identity or explicit FQN
- treat short-name-only matching as last-resort and unique-only
- fail fast on ambiguity

### Risk: Templates becoming the new logic layer

If field and nested-type logic gets pushed into Pebble, later evolution will be painful.

Mitigation:

- keep planner responsible for structure
- keep templates responsible only for rendering prepared models

## Follow-Up Work

After this minimal usable slice, the next planned design-generator upgrades remain:

- `type()` helper formalization
- richer `imports()` template support
- `use()` helper support
- improved auto-import resolution
- default-value support
- broader legacy syntax coverage

Those should build on the render-model structure introduced here rather than replacing it.
