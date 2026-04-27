# Cap4k Analysis Design Projection Normalization Design

Date: 2026-04-27
Status: Draft for review

## Summary

This slice normalizes the analysis-produced design projection used by `drawingBoard`.

The current compiler analysis output contains:

- `nodes.json`
- `rels.json`
- `design-elements.json`

`nodes.json` and `rels.json` are true analysis graph artifacts.
`design-elements.json` is different: it is a projection from Kotlin source code back into design input records.

That projection still carries old plugin terminology such as:

- `cmd`
- `qry`
- `cli`
- `payload`
- `de`

The new pipeline has already moved to standard design tags such as:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `validator`

The mismatch makes the analysis output harder to reason about and leaves an obvious gap: validators can be generated from design input, but validators are not currently projected back into stable design input by analysis.

This slice makes `design-elements.json` an analysis-side design projection in the new pipeline language.

It is not a full IR analysis rewrite.
It is not a validator business-logic generator.
It is not a compatibility layer for every old project pattern.

## Why This Slice Exists

The current `drawingBoard` path is intended to support this loop:

1. analyze existing Kotlin source
2. export `drawing_board_*.json`
3. use that JSON as stable input to `cap4kGenerate`

For that loop to be credible, drawing-board output and generate input must speak the same design language.

Today that is only partly true.

`DefaultCanonicalAssembler` already normalizes old analysis tags into new drawing-board tags:

- `cmd` -> `command`
- `qry` -> `query`
- `cli` -> `client`
- `payload` -> `api_payload`
- `de` -> `domain_event`

But this is a symptom of the deeper boundary issue.
The compiler-side projection is still old-plugin-shaped, and `drawingBoard` has to compensate.

Validator exposes the problem clearly:

- `designValidator` can generate validator artifacts from `tag == "validator"`
- `DesignElementCollector` does not collect validator annotations
- `DrawingBoardArtifactPlanner` does not emit a validator drawing-board file
- current `ValidatorModel` is too weak to represent common class-level validator structure

If we only add `"validator"` to a drawing-board whitelist, the pipeline still lacks a coherent analysis projection contract.

## Goals

- Treat `design-elements.json` as a new-pipeline design projection, not an old-plugin drawing-board payload.
- Standardize projected tags to the new design input tags.
- Add validator projection for the supported validator contract.
- Preserve `nodes.json` and `rels.json` as analysis graph artifacts without redesigning them.
- Keep `drawingBoard` output directly consumable by `cap4kGenerate`.
- Keep the first validator projection deliberately structural and bounded.
- Make unsupported validator patterns explicit instead of silently encoding them into the new contract.

## Non-Goals

This slice will not:

- rewrite the whole IR analysis system
- redesign `nodes.json`
- redesign `rels.json`
- add a new normalization exporter between `drawingBoard` and `cap4kGenerate`
- infer or generate arbitrary validator business logic
- support validators bound to concrete generated request classes
- introduce trait/interface-based validator targets
- automatically attach validators to request fields or request classes
- migrate old alias tags in `designJson`
- solve broader validator generation capability expansion in one step
- change aggregate, repository, schema, enum, or bootstrap generation

## Core Boundary Decision

The analysis graph and the design projection are different products.

`nodes.json` and `rels.json` describe source-code structure.
They may continue to use analysis-oriented models.

`design-elements.json` describes regeneratable design input.
It should use the same stable tag language expected by `cap4kGenerate`.

That means this slice should not be framed as a full `irAnalysis` restructuring.
It is a targeted normalization of the analysis-to-design projection.

## Current State

### Analysis Core Projection Model

The current analysis projection model is generic:

```kotlin
data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val desc: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignField> = emptyList(),
    val responseFields: List<DesignField> = emptyList()
)
```

This generic shape is useful for command/query/client/payload/domain-event families, but it cannot currently express validator-specific structure.

The pipeline API mirrors the same projection with `DesignElementSnapshot`.
It also lacks validator fields such as `targets`, `valueType`, or annotation parameters.

### Analysis Compiler Collection

`DesignElementCollector` currently collects only:

- request-like classes implementing `RequestParam`
- API payload objects under `adapter.portal.api.payload`
- domain events detected by annotation or aggregate metadata

It does not collect annotation classes that define Bean Validation constraints.

Therefore validator support cannot be completed only in `drawingBoard`.
If the goal is code -> design projection, `analysis-compiler` must learn how to project supported validator annotation classes.

### Drawing-Board Canonical Assembly

`DefaultCanonicalAssembler` currently normalizes old analysis tags into new drawing-board tags:

```kotlin
"cmd", "command" -> "command"
"qry", "query" -> "query"
"cli", "client", "clients" -> "client"
"payload", "api_payload" -> "api_payload"
"de", "domain_event" -> "domain_event"
```

This normalization keeps existing analysis fixtures working, but it also hides the fact that the producer still speaks the old tag dialect.

The assembler also filters supported drawing-board tags through:

```kotlin
setOf("command", "query", "client", "api_payload", "domain_event")
```

`validator` is not included.

### Drawing-Board Planner

`DrawingBoardArtifactPlanner` currently emits only:

```kotlin
listOf("command", "query", "client", "api_payload", "domain_event")
```

So even if a validator projection record existed, it would not currently produce `drawing_board_validator.json`.

### Validator Generation

`designValidator` currently supports only a minimal model:

```kotlin
data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val valueType: String,
)
```

The current canonical assembler hard-codes:

```kotlin
valueType = "Long"
```

The current template always emits:

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
class Validator : ConstraintValidator<Annotation, Long>
```

That was enough for the first validator-family migration, but it is not enough for analysis projection parity.

## Supported Validator Contract

This slice introduces the minimum validator contract needed for stable code -> design -> code projection.

The supported validator kinds are:

- field-level validators
- value-parameter-level validators
- reusable class-level validators with `Any` value type

### Field-Level Validators

Field-level validators validate a single value.

Example shape:

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Constraint(validatedBy = [CategoryMustExist.Validator::class])
annotation class CategoryMustExist(...)
```

The nested validator should use the field value type:

```kotlin
class Validator : ConstraintValidator<CategoryMustExist, Long>
```

The projected design should preserve:

- annotation name
- package
- description when available
- `targets`
- `valueType`
- supported annotation parameters

### Reusable Class-Level Validators

Reusable class-level validators validate a request-like object but should not bind to a concrete generated request class.

The supported value type is:

```kotlin
Any
```

Example shape:

```kotlin
@Target(AnnotationTarget.CLASS)
@Constraint(validatedBy = [DanmukuDeletePermission.Validator::class])
annotation class DanmukuDeletePermission(
    val message: String = "无权限删除该弹幕",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val danmukuIdField: String = "danmukuId",
    val operatorIdField: String = "operatorId",
) {
    class Validator : ConstraintValidator<DanmukuDeletePermission, Any>
}
```

The field binding is explicit annotation-parameter data.
The validator body may use reflection, query calls, repositories, or other hand-written code.

That body is not part of the structural design projection.

### Concrete Request-Type Validators Are Not Supported

Validators bound to a concrete generated request type are not part of the new pipeline contract.

This shape is explicitly unsupported:

```kotlin
class Validator : ConstraintValidator<VideoDeletePermission, DeleteVideoPostCmd.Request>
```

Reason:

- it ties a validator artifact to one generated request class
- it prevents reuse across command/query/client requests
- it leaks generated source layout into validator design input
- it makes code -> design projection depend on a specific generated request type
- it encourages generator output to become order-sensitive and cyclic

In project terms, this shape is a migration defect or hand-written special case.
It should not be normalized into stable design input.

The supported class-level form is:

```kotlin
class Validator : ConstraintValidator<VideoDeletePermission, Any>
```

with explicit annotation parameters for field names.

### Future Trait-Based Validators

A future enhancement may support generated target traits:

```kotlin
interface DanmukuDeletePermissionTarget {
    val danmukuId: Long
    val operatorId: Long?
}

class Validator : ConstraintValidator<DanmukuDeletePermission, DanmukuDeletePermissionTarget>
```

Generated requests could then implement the trait.

That design is intentionally out of scope for this slice because it affects:

- validator generation
- request generation
- import planning
- multi-validator interface composition
- field conflict handling
- analysis projection of implemented interfaces

The first normalized contract remains `Any + annotation field parameters`.

## Design Input Shape

The normalized validator design entry should support:

```json
{
  "tag": "validator",
  "package": "danmuku",
  "name": "DanmukuDeletePermission",
  "desc": "校验弹幕删除权限",
  "targets": ["CLASS"],
  "valueType": "Any",
  "parameters": [
    {
      "name": "danmukuIdField",
      "type": "String",
      "defaultValue": "danmukuId"
    },
    {
      "name": "operatorIdField",
      "type": "String",
      "defaultValue": "operatorId"
    }
  ]
}
```

For a field-level validator:

```json
{
  "tag": "validator",
  "package": "category",
  "name": "CategoryMustExist",
  "desc": "分类必须存在",
  "targets": ["FIELD", "VALUE_PARAMETER"],
  "valueType": "Long",
  "parameters": []
}
```

Analysis projection should prefer the source annotation class simple name for `name`.
User-authored `designJson` may still use the existing accepted spelling styles as long as canonical type-name normalization resolves to the same annotation class name.

### `targets`

`targets` should use stable design strings rather than Kotlin enum FQCNs:

- `CLASS`
- `FIELD`
- `VALUE_PARAMETER`

The generator maps these to:

- `AnnotationTarget.CLASS`
- `AnnotationTarget.FIELD`
- `AnnotationTarget.VALUE_PARAMETER`

Unknown targets have different handling depending on the source:

- `designJson` input should fail fast because user-authored input is expected to be intentional
- analysis projection should skip unsupported validator projection instead of emitting misleading stable input

### `valueType`

`valueType` is the second generic argument of `ConstraintValidator<Annotation, ValueType>`.

Supported first-slice values:

- Kotlin primitive/value types such as `Long`, `String`, `Int`, `Boolean`
- nullable type spellings when they already appear in code, though validator implementations normally receive nullable values through `isValid(value: T?)`
- `Any` for reusable class-level validators

Unsupported first-slice values:

- concrete generated request types
- generated request nested types
- generated target trait types
- unresolved local or anonymous types

### `parameters`

`parameters` represent annotation constructor parameters beyond standard Bean Validation fields.

The standard fields should not be projected as custom parameters:

- `message`
- `groups`
- `payload`

Supported first-slice parameter value types:

- `String`
- `Int`
- `Long`
- `Boolean`

The primary target for this slice is string field-name parameters such as:

- `danmukuIdField`
- `operatorIdField`
- `videoIdField`
- `userIdField`

The projection does not interpret these fields semantically.
It only preserves the annotation contract.

## Source And Canonical Model Changes

The model should evolve in one line, not through parallel old/new models.

Recommended raw projection model additions:

```kotlin
data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val desc: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignField> = emptyList(),
    val responseFields: List<DesignField> = emptyList(),
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<DesignParameter> = emptyList()
)
```

The pipeline API snapshot should mirror those additions.

Recommended parameter model:

```kotlin
data class DesignParameter(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null
)
```

The canonical validator model should become:

```kotlin
data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val targets: List<String>,
    val valueType: String,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)
```

There should not be two validator model lines.
The existing hard-coded `valueType = "Long"` should be removed once this slice is implemented.

## Analysis Compiler Projection Rules

`DesignElementCollector` should keep collecting command/query/client/api-payload/domain-event families, but projected tags should use the new standard values:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`

The collector should also collect supported validator annotation classes.

### Validator Detection

A class is a validator design projection candidate when:

- it is an annotation class
- it has `@Constraint`
- it has a nested validator class
- the nested validator implements `ConstraintValidator<ThisAnnotation, ValueType>`

The collector should derive:

- `tag = "validator"`
- `package` from the package segment below `.application.validators`
- `name` from the annotation class simple name
- `desc` from KDoc only if a stable KDoc extraction mechanism already exists; otherwise empty string
- `targets` from `@Target(...)`
- `valueType` from the second `ConstraintValidator` generic argument
- `parameters` from annotation constructor parameters excluding `message`, `groups`, and `payload`

If the annotation is under the application validator package root with no subpackage, `package` should be empty.

### Unsupported Validator Projection

The collector should not emit a stable validator design element for:

- `ConstraintValidator<Annotation, ConcreteRequest.Request>`
- validator classes without an inspectable `ConstraintValidator` generic argument
- validators with unsupported targets only
- validators with unsupported custom parameter types only
- validators whose annotation class cannot be resolved safely

The analysis tooling may record diagnostics later, but this slice does not require a diagnostics file format change.

The important contract is that unsupported validators do not become misleading stable generate input.

## Drawing-Board Changes

The drawing-board canonical model should include validator elements.

Supported drawing-board tags should become:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `validator`

The planner should produce a validator drawing-board file when validator elements exist.

Recommended default file name:

```text
drawing_board_validator.json
```

This follows the current new pipeline naming pattern:

- `drawing_board_command.json`
- `drawing_board_query.json`
- `drawing_board_client.json`
- `drawing_board_api_payload.json`
- `drawing_board_domain_event.json`

If existing documentation or fixtures still mention old names such as `drawing_board_cmd.json`, those references should be corrected as part of implementation cleanup.

The drawing-board template should render validator-specific fields only when present:

- `targets`
- `valueType`
- `parameters`

For non-validator entries, those fields should be omitted to keep design JSON compact.

## Design JSON Source Changes

`designJson` should parse the new validator fields:

- `targets`
- `valueType`
- `parameters`

For the first implementation:

- missing `targets` may default to `["FIELD", "VALUE_PARAMETER"]` for compatibility with the current minimal validator behavior
- missing `valueType` may default to `"Long"` only if needed to preserve existing tests
- new tests should prefer explicit `targets` and `valueType`

This defaulting is an implementation bridge, not the future design style.

After this slice, new examples should write validator intent explicitly.

## Validator Generator Changes

The validator generator should consume the expanded `ValidatorModel`.

Template responsibilities:

- render `@Target(...)` from `targets`
- render custom annotation parameters
- render `ConstraintValidator<Annotation, valueType>`
- preserve standard Bean Validation parameters:
  - `message`
  - `groups`
  - `payload`

The generated validator body remains a safe stub:

```kotlin
override fun isValid(value: ValueType?, context: ConstraintValidatorContext): Boolean = true
```

For `valueType = "Any"`, the generated signature becomes:

```kotlin
override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
```

This is enough to compile and enough to let project code replace the stub with hand-written logic.

The slice does not attempt to generate reflection field extraction logic.
That can be discussed later as part of validator capability expansion.

## Compatibility Policy

There is no requirement to preserve old plugin semantics.

However, short-lived implementation bridges are acceptable if they keep existing pipeline tests readable while the source fixtures are updated.

Allowed temporary bridges:

- parse old analysis tags in `DefaultCanonicalAssembler` while tests are migrated
- default missing validator `valueType` to `Long`
- default missing validator `targets` to field/value-parameter targets

Not allowed as a stable contract:

- continuing to emit old tags from `analysis-compiler`
- treating concrete request-type validators as supported
- adding a second drawing-board normalization layer
- making `drawingBoard` output a dialect that `cap4kGenerate` cannot consume directly

## Testing Strategy

### Unit Tests

Add or update analysis compiler tests for:

- projected request tags use `command`, `query`, and `client`
- payload projection uses `api_payload`
- domain-event projection uses `domain_event`
- field-level validator projection emits:
  - `tag = "validator"`
  - `targets = ["FIELD", "VALUE_PARAMETER"]`
  - `valueType = "Long"`
- class-level `Any` validator projection emits:
  - `targets = ["CLASS"]`
  - `valueType = "Any"`
  - custom field-name parameters
- concrete request-type validator is not projected as stable input

Add or update source parsing tests for:

- `targets`
- `valueType`
- `parameters`

Add or update canonical tests for:

- validator design entries assemble into expanded `ValidatorModel`
- drawing-board model includes validator elements
- unsupported or unknown drawing-board tags do not leak into output

Add or update planner/template tests for:

- `drawing_board_validator.json` is planned when validator elements exist
- validator JSON includes `targets`, `valueType`, and `parameters`
- validator template renders class-level `Any`
- validator template renders field-level `Long`

### Functional Tests

Add one compile-level fixture that proves:

- a design JSON validator with `targets = ["CLASS"]` and `valueType = "Any"` compiles
- custom string annotation parameters compile

Add one analysis-to-drawing-board fixture that proves:

- source validator annotation is collected
- `cap4kAnalysisGenerate` exports validator drawing-board JSON
- that JSON can be used as `designJson` input for `cap4kGenerate`

Full business validator body behavior is not required in this slice.

## Recommended Implementation Order

1. Expand API and analysis-core projection models.
2. Expand `designJson` parsing for validator fields.
3. Expand canonical `ValidatorModel` and validator assembler logic.
4. Update validator render model and preset template.
5. Normalize analysis compiler projected tags to new standard tags.
6. Add validator collection in `DesignElementCollector`.
7. Add validator support to drawing-board canonical filtering and planner output.
8. Add tests from the smallest unit outward.
9. Update docs and fixtures that still describe old drawing-board tag names.

This order keeps generator input parsing ahead of analysis output.
That matters because the exported validator drawing-board JSON should be immediately consumable once analysis starts producing it.

## Risks

### Risk: Over-Expanding Validator Semantics

Validator generation can become a large feature quickly.

Mitigation:

- this slice only standardizes structural annotation metadata
- business logic remains hand-written
- trait-based targets are deferred
- request auto-attachment is deferred

### Risk: Treating Migration Bugs As Contract

Existing real-project code contains validators bound to concrete request classes.

Mitigation:

- mark concrete request-type validators unsupported
- do not project them into stable design input
- do not add generator support for them

### Risk: Hidden Old Tag Dialect Survives

If the compiler keeps emitting old tags and only the canonical layer normalizes them, the new pipeline remains dependent on old plugin language.

Mitigation:

- update `analysis-compiler` to emit standard tags
- keep assembler compatibility only as a temporary bridge if needed
- update fixtures to assert new tag names

### Risk: Analysis-Core Becomes Design-Specific

Adding validator fields to `DesignElement` makes the projection model more design-specific.

Mitigation:

- acknowledge that `design-elements.json` is a design projection, not pure IR
- keep `nodes.json` and `rels.json` unchanged
- do not move generation planning into analysis modules

## Acceptance Criteria

- `design-elements.json` produced by analysis compiler uses standard new-pipeline tags for supported design families.
- Supported validator annotations are projected as `tag = "validator"`.
- Projected validators include `targets`, `valueType`, and supported custom annotation parameters.
- Concrete request-type validators are not treated as supported stable input.
- `cap4kAnalysisGenerate` can produce `drawing_board_validator.json`.
- The generated validator drawing-board JSON can be fed into `cap4kGenerate` as design input.
- Existing `nodes.json` and `rels.json` behavior remains unchanged.
- The implementation does not introduce a separate normalization exporter between drawing-board and generate.

## Final Position

This is a reasonable framework-stability requirement.

The requirement is not that every hand-written Kotlin validator body can be reverse-engineered.
That would be unstable and too expensive.

The requirement is that the structural part of a supported validator contract can round-trip:

```text
design input -> generated code -> analysis projection -> drawing-board JSON -> design input
```

That is the right boundary for the new pipeline.
