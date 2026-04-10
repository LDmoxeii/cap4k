# Cap4k Design Generator Type And Import Resolution Upgrade

## Summary

This design upgrades the new pipeline design generator from "minimally usable" output to a stable type-rendering and import-resolution foundation.

The previous slice already introduced:

- object-scoped `Cmd/Qry` generation
- inner `Request/Response`
- nested request/response type generation
- a generator-local render model

What still remains weak is type identity and import correctness.

Current behavior still relies on string-oriented rendering inside `DesignRenderModelFactory`. That is enough for simple cases, but it is not strong enough for:

- explicit FQCN handling
- simple-name collision safety
- recursive generic rendering
- future template helpers such as `type()`
- future import planning improvements

This slice introduces a dedicated type/import resolution layer inside `generator-design` and keeps the decision-making in Kotlin code rather than Pebble templates.

## Why This Slice

The pipeline migration has already landed the main architecture and repository DSL:

1. `design-json + ksp-metadata + design generator`
2. `db + aggregate generator`
3. `ir-analysis + flow generator`
4. `ir-analysis + drawing-board generator`
5. repository DSL consolidation into `cap4k { ... }`
6. minimal usable design generator output

The next bottleneck is no longer architecture. It is output correctness.

The specific problem to solve now is import safety.

The old generator's auto-import behavior is not acceptable as a long-term model because it can resolve imports by simple class name and produce wrong results when multiple packages contain the same name.

The new generator must not repeat that mistake.

## Goals

- Introduce a generator-local type parsing and resolution layer
- Separate parsing, identity resolution, and final rendering concerns
- Prefer explicit symbol identity over simple-name guessing
- Treat explicit FQCN as the strongest source of truth
- Support recursive generic type rendering
- Compute imports in generator code, not in templates
- Preserve correctness under simple-name collisions by falling back to qualified rendering
- Prepare a clean base for later lightweight `type()` and `imports()` template helpers

## Non-Goals

- Reintroduce the full legacy `use()` mechanism
- Reintroduce the full legacy `imports()` runtime
- Implement package guessing by scanning the whole project or module graph
- Implement default-value inference
- Implement advanced alias expansion
- Implement complete legacy template parity
- Move these concerns into pipeline core
- Change aggregate, flow, or drawing-board generation

## Scope Decision

Three approaches were considered:

1. add a dedicated type/import resolution layer inside `generator-design`
2. keep using raw strings and push more logic into templates
3. transplant the old generator import runtime into the new pipeline

The recommended choice is option 1.

Reasons:

- it keeps the new pipeline architecture clean
- it prevents template logic from expanding into business logic
- it avoids carrying legacy coupling back into the new generator
- it directly addresses the user's core concern: import correctness under same-name collisions
- it creates a stable base for later `type()`, `imports()`, and improved auto-import work

## Current Behavior

Today `DesignRenderModelFactory` still performs type rendering mostly as string rewriting:

- built-in types are recognized from a hard-coded set
- explicit FQCN imports are shortened when their simple names do not collide
- nested type protection is handled by simple reserved-name checks
- unresolved short names are kept as-is

This logic is useful but too implicit. It does not yet model:

- parsed type structure
- resolved type identity
- import planning as a distinct step
- future template-facing type metadata

## Design Principles

This slice follows four rules:

1. correctness is more important than aggressive shortening
2. type identity must not be inferred from simple name alone
3. templates should consume decisions, not make decisions
4. when the generator cannot prove a safe import, it must not guess

The practical consequence is:

- if the generator knows the exact symbol identity, it may import
- if the generator sees a collision, it should render a qualified name instead
- if the generator only sees an unresolved short name, it should keep that short name and avoid importing it

## Architecture

This slice adds a design-generator-local type/rendering kernel. It stays inside `cap4k-plugin-pipeline-generator-design`.

Recommended layers:

### ParsedTypeRef

Represents syntactic structure parsed from the raw type string.

Responsibilities:

- preserve nullability
- preserve generic nesting
- preserve root type token
- preserve type arguments as child nodes

Examples:

- `String`
- `String?`
- `List<String>`
- `List<com.foo.Status?>`

This layer answers: "what did the input type string say?"

### ResolvedTypeRef

Represents semantic identity after the generator classifies the parsed type.

Recommended categories:

- built-in Kotlin type
- built-in collection wrapper
- current Request/Response inner type
- explicit external FQCN
- unresolved short name

This layer answers: "what kind of thing is this type?"

### RenderedTypeRef

Represents the final rendering decision for template consumption.

Recommended fields:

- `renderedText`
- `requiresImport`
- `importFqcn`
- `isQualified`

This layer answers: "what should the template print?"

### ImportPlan

Represents the final list of imports chosen for a generated file.

Responsibilities:

- collect candidate imports
- remove duplicates
- detect simple-name collisions across external FQCN types
- exclude built-ins and inner types
- exclude same-package references if later needed

This layer answers: "which imports are safe to emit?"

## Parsing Rules

Type strings should first be parsed into trees before any import decision is made.

This slice should support:

1. nullable suffixes
   - `String?`
   - `List<Long>?`

2. generic recursion
   - `List<String>`
   - `Map<String, Long>`
   - `List<com.foo.Status>`

3. explicit FQCN tokens
   - `java.time.LocalDateTime`
   - `com.foo.Status`

4. short-name tokens
   - `Status`
   - `UserId`

5. current generated inner types
   - `Address`
   - `Item`

Failure conditions:

- empty generic arguments where a type is required
- mismatched `<` / `>` pairs
- malformed nullable suffix placement

Those should fail fast rather than silently degrade.

## Resolution Rules

Resolution should follow a strict order.

### 1. Built-in Kotlin Types

Recognize language and common standard collection types first.

Examples:

- `String`
- `Long`
- `Boolean`
- `List`
- `Set`
- `Map`
- `MutableList`

These never require imports.

### 2. Current Request/Response Inner Types

If a type token matches a nested type generated inside the current `Request` or `Response` namespace, classify it as an inner type.

These never require imports.

Inner types take precedence over external same-name types.

### 3. Explicit FQCN

If the parsed token is fully qualified, treat it as a known external symbol.

Do not discard the FQCN identity.

The later import plan may choose:

- import and shorten it
- or keep it qualified if the simple name collides

### 4. Unresolved Short Names

Any remaining short name without explicit identity stays unresolved.

This slice does not try to discover its package automatically.

That type should be rendered conservatively:

- keep the short name
- do not import it
- do not guess where it came from

## Import Decision Rules

Import planning should happen after all field and nested-type references have been resolved.

### Built-ins

- never import

### Inner Types

- never import

### Explicit FQCN Types

- if the simple name is unique across imported external types, import it and render the short name
- if the simple name collides with another external FQCN, import neither and render both as qualified names
- if the simple name collides with an inner generated type, prefer the inner type name and keep the external type qualified

### Unresolved Short Names

- never import
- render as-is

This is intentionally conservative.

The generator must prefer:

- safe qualified rendering

over:

- incorrect but shorter rendering

## Template API Boundary

This slice does not restore the full legacy helper runtime.

Templates should only consume stable, precomputed values.

Recommended template-facing changes:

- fields expose `renderedType` rather than raw `type`
- templates receive `imports` as an already-decided `List<String>`
- future helpers such as `type(field)` may be added later, but only as thin readers over precomputed render data

This means Pebble templates should stop making type decisions.

They should only:

- render imports
- render field declarations
- render nested type declarations

They should not:

- infer package names
- detect collisions
- assemble qualified names dynamically

## Example Outcomes

### Safe Import

Input fields:

- `createdAt: java.time.LocalDateTime`

If there is no collision on `LocalDateTime`, the result may be:

- import `java.time.LocalDateTime`
- render field as `LocalDateTime`

### FQCN Collision

Input fields reference:

- `com.foo.Status`
- `com.bar.Status`

Result:

- import neither
- render as `com.foo.Status` and `com.bar.Status`

### Inner Type Collision

Generated nested type:

- `Response.Item`

External field type:

- `com.foo.Item`

Result:

- nested `Item` remains unqualified as inner type
- external type renders as `com.foo.Item`
- no import for external `Item`

### Unresolved Short Name

Input field type:

- `UserId`

No explicit FQCN, no inner type match.

Result:

- render as `UserId`
- do not import it

## Implementation Shape

This slice should stay inside `generator-design` and related template/tests.

Expected implementation areas:

- replace string-only type rewriting in `DesignRenderModelFactory`
- introduce parser, resolver, renderer, and import-plan helpers
- update render models so fields expose rendered type values
- update templates to use rendered type output instead of rebuilding type text
- add focused tests around parsing, collisions, and import decisions

This slice should not require changes in:

- pipeline core
- repository DSL
- non-design generators

## Testing Strategy

Testing should be split across three levels.

### Unit Tests

Add focused tests for:

- generic parsing
- nullable parsing
- explicit FQCN rendering
- external simple-name collision handling
- inner-type precedence over external same-name types
- unresolved short-name behavior
- malformed type parse failures

### Generator Tests

Add `DesignRenderModelFactory` tests that verify:

- rendered field type text
- nested type rendering
- import list correctness
- qualified fallback on collisions

### Renderer And Functional Tests

Update existing Pebble and Gradle fixture tests to verify:

- generated imports
- `Request/Response` field types use rendered text
- collision scenarios remain correct in generated Kotlin output

## Risks

The main risk is over-designing the type system too early.

This slice avoids that by keeping the scope narrow:

- no project-wide symbol search
- no legacy runtime transplant
- no default-value system

Another risk is confusing template-facing shape with semantic type identity.

That is why parsing, resolution, rendering, and import planning are separated.

## Follow-Up Slices

This slice intentionally prepares but does not yet implement:

- lightweight `type()` helper exposure
- improved `imports()` helper exposure
- better auto-import when explicit symbol identity is available from richer sources
- default-value inference
- broader legacy compatibility where still worthwhile

After this slice lands, the next design-generator upgrades should build on these identities and import plans instead of adding more string heuristics.
