# Cap4k Design Template Migration / Helper Adoption Design

Date: 2026-04-13
Status: Implemented and merged

## Summary

This document is the historical design record for the helper-adoption slice that has now been implemented and merged into mainline.

The pipeline design-generator mainline already has the helper surface it needed:

- `type()` for template-side type rendering
- `imports()` for final import emission
- `use()` for explicit design-template-only imports
- Kotlin-ready `defaultValue` in render models

At design time, what was missing was the adoption slice that turns those helper capabilities into a stable migration contract for design templates.

The default preset templates were already close to the intended direction, but the project lacked a clearly defined, migration-friendly template contract and a functional fixture that proved realistic helper combinations remained stable in override templates.

This slice formalized that contract, kept the default design templates aligned with it, and added functional migration fixtures that validate helper-first template overrides under realistic design-generation scenarios.

## Why This Slice

The mainline is no longer blocked on architecture or on adding new design-generator capabilities.

Completed mainline work already covers:

- pipeline architecture reset
- fixed pipeline DSL
- design minimal usable output
- type and import resolution
- auto-import
- template helpers
- default value formatting
- `use()` helper introduction

That means the next quality step is not another generator feature.

It is the compatibility and migration step that makes the current helper-first design usable as the stable landing zone for old design-template migration.

In practical terms, this slice should answer:

- what design templates are expected to read directly from context
- where `type()`, `imports()`, `use()`, and `defaultValue` each belong
- how override templates should adopt the helper-first style without depending on low-level render-model details

## Goals

- Define the recommended helper-first contract for pipeline design templates
- Keep default design templates aligned with that contract
- Reduce template dependence on low-level context details such as `field.renderedType`
- Clarify when `use()` is appropriate and when it is not
- Add functional fixtures that model realistic design-template migration patterns
- Verify helper combinations remain stable in override templates

## Non-Goals

This slice will not:

- add a new helper beyond `type()`, `imports()`, and `use()`
- expand `use()` semantics
- reintroduce legacy `cap4k-plugin-codegen` runtime behavior
- add sibling design-entry type support
- widen short-name inference rules
- turn project type registry into a broader inference engine
- reopen generator-core, source-layer, or pipeline architecture decisions
- produce a full old-template to new-template compatibility layer
- produce a mapping document for every old `cap4k-plugin-codegen` template pattern

## Scope Decision

Three approaches were considered.

### Option 1: Contract convergence plus migration fixtures

Define the helper-first contract, keep default templates aligned with it, and add realistic override fixtures that exercise `type()`, `imports()`, `use()`, and `defaultValue` together.

Pros:

- matches the handoff-defined next mainline step
- improves migration readiness without changing architectural ownership
- keeps the work focused on template adoption rather than helper invention

Cons:

- the slice mostly improves stability and migration clarity rather than adding visible new capability

### Option 2: Fixture-only validation without touching default templates

Document the contract and prove it through fixtures, but do not tighten default templates into a recommendation-grade example.

Pros:

- lowest implementation risk

Cons:

- leaves the preset templates weaker as the canonical recommended style
- pushes too much contract weight into docs alone

### Option 3: Expand template runtime ergonomics further

Add more helpers or broader template abstractions so old templates can migrate with less rewriting.

Pros:

- shorter templates

Cons:

- reopens the runtime surface
- weakens the rule that Kotlin owns type and import decisions
- drifts away from the current mainline boundary

### Recommendation

Implement Option 1.

This is the smallest slice that turns the existing helper set into a stable, migration-friendly contract without reopening settled architectural boundaries.

## Current Behavior

The default design preset templates already use:

- `imports(imports)`
- `type(field)`
- `field.defaultValue`

This means the preset templates are already close to the intended steady state.

At design time, two gaps remained:

1. the recommended contract is not yet written down as a clear design rule set
2. the functional override fixture still represents an earlier helper-only stage and does not prove the full migration-oriented helper combination, especially around `use()` plus default-value-aware field rendering

So the gap was no longer helper existence.

The gap was helper adoption discipline.

## Design Principles

This slice follows five rules:

1. Kotlin code remains the only owner of type and import decisions
2. templates consume prepared data and declare a small amount of explicit import intent
3. `use()` remains optional and narrow, not a mandatory part of every template
4. default templates should serve as the canonical example of recommended template style
5. migration fixtures should validate realistic override usage rather than synthetic helper demos

The practical consequence is:

- templates should use helpers where the helper defines the stable contract
- templates may still read structure-oriented context properties directly
- templates should avoid depending on low-level decision-oriented properties when a helper already exists

## Recommended Template Contract

The recommended pipeline design-template contract is:

### Type Rendering

Render field types through:

```pebble
{{ type(field) | raw }}
```

Do not prefer:

```pebble
{{ field.renderedType | raw }}
```

Reason:

- `type()` is the stable template-facing API
- `renderedType` remains an internal render-model detail even if it still exists underneath

### Import Emission

Render import lines through:

```pebble
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

Reason:

- `imports()` is the single output path for final normalized imports
- it preserves the merge path with any `use()` registrations

Templates should not hand-roll import emission from lower-level structures.

### Explicit Template-Owned Imports

Use `use()` only when the template itself introduces an additional explicit dependency that is not naturally implied by the render-model type graph.

Recommended example:

```pebble
{{ use("java.time.LocalDateTime") -}}
package {{ packageName }}
```

Appropriate usage includes:

- annotation imports introduced by the template
- marker interface imports introduced by the template
- helper API types explicitly referenced by the template body

Not recommended:

- using `use()` for field types already handled by generator import planning
- using `use()` to repair unresolved short names
- using `use()` to influence `type()` output

### Default Value Emission

Render defaults only as already prepared Kotlin expressions:

```pebble
val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}
```

Templates must not:

- quote raw strings themselves
- append numeric suffixes
- interpret nullability rules
- infer fallback defaults

### Direct Context Reads That Remain Acceptable

It remains acceptable for design templates to read structure-oriented fields directly:

- `packageName`
- `typeName`
- `requestFields`
- `responseFields`
- `requestNestedTypes`
- `responseNestedTypes`
- `field.name`
- `nestedType.name`

This slice does not attempt to hide structural context behind more helpers.

The contract only narrows decision-oriented reads where helper APIs already exist.

## Default Template Alignment

The default preset design templates should remain helper-first and be normalized into a consistent style across:

- request fields
- request nested types
- response fields
- response nested types

That means the field declaration pattern should stay the same in every field-emitting block:

```pebble
val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}
```

The import block should also stay uniform:

```pebble
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

### `use()` in Default Templates

This slice does not require default `command` or `query` preset templates to add `use()` calls just to demonstrate that the helper exists.

Reason:

- the current default templates do not introduce extra framework types that require explicit imports
- forcing `use()` into templates without a real dependency would blur its purpose

The rule is:

- default templates may adopt `use()` only when there is a genuine template-owned import need
- otherwise they should remain clean helper-first examples using `type()`, `imports()`, and `defaultValue`

## Migration-Friendly Override Template Guidance

Override templates should be able to migrate toward the following pattern:

```pebble
{{ use("java.time.LocalDateTime") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    )
}
```

This pattern demonstrates the intended division of responsibility:

- `use()` for template-owned explicit imports
- `imports()` for final import output
- `type()` for stable type rendering
- `defaultValue` as a preformatted Kotlin expression

The migration guidance should remain implicit in code and fixtures rather than becoming a legacy compatibility shim.

## Module Boundaries

This slice should remain focused on:

- `cap4k-plugin-pipeline-renderer-pebble`
  - preset design templates
  - renderer-level tests that need contract coverage
- `cap4k-plugin-pipeline-gradle`
  - functional fixtures
  - end-to-end migration-style verification
- `docs/superpowers/specs`
  - the design record for the contract

This slice should not require changes in:

- `cap4k-plugin-pipeline-generator-design` helper semantics
- pipeline core
- pipeline API
- source providers
- aggregate, flow, or drawing-board generation

## Functional Fixture Plan

Add or upgrade a functional fixture so it reflects a migration-oriented override template rather than a minimal helper smoke test.

The fixture should prove that an override template can:

- use `imports(imports)` for final imports
- use `type(field)` for rendered types
- emit `field.defaultValue` directly as Kotlin-ready source
- register at least one explicit template-owned import through `use()`
- still coexist with:
  - nullable fields
  - nested request or response types
  - collision-safe fully qualified type output
  - precomputed imports from generator-side planning

The fixture should simulate a realistic override author, not a helper test harness.

That means the template should look like something a migrating project could plausibly keep.

## Testing Strategy

Testing should stay narrow and contract-focused.

### Preset Template Regression Coverage

Keep or extend tests that ensure preset command and query templates still render correctly under helper-first usage.

Focus on:

- safe imports still emitted
- collision types still remain fully qualified when needed
- nested request and response types still render correctly
- nullable types remain correct
- default values still emit correctly

### Migration Fixture Functional Coverage

Add or upgrade a functional test that verifies a migration-friendly override template using the helper contract.

The end-to-end assertions should confirm:

- explicit `use()` imports are merged into final imports
- generator-owned collision decisions are preserved
- default values remain correctly rendered
- nested types and nullable fields still render correctly
- the override template can avoid direct use of `field.renderedType`

### No New Helper Semantics

This slice should not broaden helper internals.

So tests should avoid redoing the entire `type()`, `imports()`, or `use()` semantic suite unless preset or migration-usage coverage genuinely needs it.

## Risks

### Risk 1: Smuggling generator decisions back into templates

If this slice starts recommending direct fallback reads like `field.renderedType` or template-side import guessing, it weakens the helper contract.

Mitigation:

- document `type()` and `imports()` as the preferred interface
- validate migration fixtures against that usage style

### Risk 2: Overusing `use()`

If templates begin to treat `use()` as a substitute for generator import planning, the contract becomes confusing.

Mitigation:

- explicitly limit `use()` to template-owned imports
- avoid adding artificial `use()` calls to default templates

### Risk 3: Mistaking this for a legacy compatibility layer

This slice is about a stable migration target, not a full recreation of the old runtime.

Mitigation:

- keep the scope on template contract and fixtures
- do not add mapping shims or broader legacy semantics

## Acceptance Criteria

This slice was considered complete when:

- the recommended helper-first contract for pipeline design templates is explicit
- default command and query preset templates reflect that contract consistently
- `use()` remains optional and narrow in default templates
- a migration-oriented functional override fixture validates realistic helper composition
- end-to-end tests confirm `type()`, `imports()`, `use()`, and `defaultValue` work together in override templates
- no new generator logic or template runtime expansion is required

## Expected Outcome

After this slice (now achieved):

- the pipeline design-template surface has a stable migration contract
- preset design templates act as the canonical example of that contract
- override template authors have a realistic, tested helper-first pattern to follow
- the mainline continues forward on design-generator quality without reopening integration-boundary or architecture work
