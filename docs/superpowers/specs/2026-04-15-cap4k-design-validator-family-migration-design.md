# Cap4k Design Validator Family Migration

Date: 2026-04-15
Status: Draft for review

## Summary

The next original-mainline slice continues bounded old design-family migration after the completed `client / client_handler` migration.

This slice migrates the old `validator` design family into the pipeline as a bounded application-side family with a helper-first template contract, without reopening generator-core boundaries or expanding validator semantics beyond the old default behavior.

## Goals

- Add explicit pipeline support for the old `validator` design family.
- Generate validator artifacts into the application module.
- Keep the migration bounded through a fixed generator id and fixed template id.
- Preserve the old validator contract shape:
  - annotation class
  - nested `ConstraintValidator`
  - fixed `Long` validation value type in this slice
- Prove the family with planner, renderer, and functional coverage.

## Non-Goals

- Do not reopen generator-core or pipeline-stage architecture.
- Do not widen `use()` beyond design templates.
- Do not add old alias compatibility such as `validators`, `validater`, or `validate`.
- Do not add configurable `valueType` in this slice.
- Do not mix aggregate `unique_validator` migration into this work.
- Do not mix `api_payload`, bootstrap, or support-track integration work into this slice.

## Current Context

The old codegen system already treats `validator` as a distinct design family.

Its current behavior is narrow:

- only the standard `validator` family is generated
- output is a single validator artifact
- the generated contract is an annotation with nested validator implementation
- the validator value type is effectively fixed to `Long`

The new pipeline currently has no equivalent validator family.

The design-generator mainline already established a migration pattern through:

- bounded generator ids
- bounded template ids
- helper-first template contracts
- planner ownership of output paths
- regression coverage at planner, renderer, and functional levels

The validator migration should follow that pattern instead of reusing the old template runtime or introducing a new validator DSL.

## Design Decision

This slice should use an explicit bounded validator generator rather than folding validator output into the existing `design` generator.

The public surface should add:

- `designValidator`

The internal generator id should be:

- `design-validator`

This is preferred because:

- `validator` is not a request family
- its output contract is annotation-based rather than request/handler-based
- a separate generator boundary keeps the existing `design` request planner from becoming an unbounded catch-all
- it matches the mainline discipline already used for `designQueryHandler` and `designClient`

## Source And Canonical Model

This slice should add a dedicated canonical validator slice instead of forcing validator into `RequestModel`.

The canonical model should gain:

- `validators: List<ValidatorModel>`

`ValidatorModel` should be minimal and contain only what this slice needs:

- `packageName`
- `typeName`
- `description`
- `valueType`

The source contract should remain narrow:

- only standard `tag == "validator"` is accepted in this slice

This slice intentionally does not normalize old aliases such as:

- `validators`
- `validater`
- `validate`

The naming rule should remain conservative and old-compatible:

- `issueToken` -> `IssueToken`
- `videoPostOwner` -> `VideoPostOwner`

No additional suffix such as `Validator` should be appended automatically.

The validator value type should be fixed by Kotlin-side assembly in this slice:

- `valueType = "Long"`

That keeps the migration bounded while leaving room for a later explicit `valueType` iteration if the mainline needs it.

## Public Gradle DSL

The Gradle DSL should expose one explicit generator:

```kotlin
cap4k {
    project {
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designValidator {
            enabled.set(true)
        }
    }
}
```

Dependency rules:

- `designValidator` requires enabled `designJson`
- `designValidator` requires `project.applicationModulePath`

This slice should not require adapter or domain module paths.

## Validator Planner

Add a dedicated planner for validator artifacts:

- class: `DesignValidatorArtifactPlanner`
- generator id: `design-validator`

Responsibilities:

- read canonical `validators`
- emit bounded template id:
  - `design/validator.kt.peb`
- write to application module paths:
  - `.../src/main/kotlin/<base>/application/validators/<package>/<TypeName>.kt`
- render package names under:
  - `<basePackage>.application.validators.<package>`

The planner should provide render context with:

- `packageName`
- `typeName`
- `description`
- `valueType`
- `imports`

In this slice:

- `valueType` is always `Long`
- `imports` may remain empty apart from helper-driven imports introduced by template use

This planner should not be folded into the request-family planner because validator output has a different contract shape and belongs in a different package family.

## Template Contract

This slice introduces exactly one bounded preset template:

- `design/validator.kt.peb`

The generated artifact should preserve the old validator contract shape:

- annotation class `{{ typeName }}`
- nested `class Validator : ConstraintValidator<{{ typeName }}, {{ valueType }}>`

The template should use the helper-first contract:

- `use("jakarta.validation.Constraint")`
- `use("jakarta.validation.ConstraintValidator")`
- `use("jakarta.validation.ConstraintValidatorContext")`
- `use("jakarta.validation.Payload")`
- `use("kotlin.reflect.KClass")`
- `imports(imports)` for explicit import rendering

The validator template should not introduce additional configurable parameters beyond the old minimal contract.

In this slice the generated annotation should retain:

- `message`
- `groups`
- `payload`

and the nested validator should retain a minimal `isValid` body that returns `true`.

## Override Contract

Template override remains bounded and unchanged in mechanism:

```kotlin
templates {
    overrideDirs.from("codegen/templates")
}
```

User overrides may replace only:

- `design/validator.kt.peb`

This slice does not introduce pattern routing, validator alias routing, or validator-specific runtime hooks.

## Validation Strategy

Validation should cover three levels.

### Planner / Unit

Add planner-level tests that verify:

- `validator` entries land in canonical `validators`
- the generated type name does not gain an extra suffix
- planner output path lands under application `validators`
- planner package name lands under `application.validators`
- planner context fixes `valueType` to `Long`
- enabling `designValidator` without `applicationModulePath` fails during configuration
- enabling `designValidator` without enabled `designJson` fails during configuration

### Renderer

Add renderer regression coverage that verifies:

- `design/validator.kt.peb` renders `@Constraint`
- the generated annotation includes `message`, `groups`, and `payload`
- the nested validator implements `ConstraintValidator<ValidatorName, Long>`
- helper-driven imports are emitted through `imports(imports)`
- override template resolution works for `design/validator.kt.peb`

### Functional

Add a representative functional fixture that:

- enables `designValidator`
- supplies at least one `validator` design entry
- verifies `cap4kPlan` emits `design/validator.kt.peb`
- verifies `cap4kGenerate` writes a validator file under `application/validators`
- verifies override template replacement for `design/validator.kt.peb`
- verifies invalid config failure when `designValidator` is enabled without required inputs

## Recommended Fixture Shape

The first representative fixture should stay minimal:

- one `validator` entry
- one package
- one generated validator file
- fixed `Long` value type

This slice does not need configurable validator value types, aggregate-aware validation logic, or project-specific validation conventions to prove the migration contract.

## Non-Default Follow-Up

After this slice, likely adjacent design-family migration candidates include:

- `api_payload`

A later validator-specific enhancement slice may revisit:

- explicit `valueType`

Those remain separate follow-up slices and should not be mixed into this implementation.
