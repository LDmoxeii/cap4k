# Cap4k Design Api Payload Migration

Date: 2026-04-15
Status: Draft for review

## Summary

The next original-mainline slice continues bounded old design-family migration after the completed `validator` migration.

This slice migrates the old `api_payload` design family into the pipeline as a bounded adapter-side family with a helper-first template contract, without reopening generator-core boundaries or carrying forward the old alias surface.

## Goals

- Add explicit pipeline support for the old `api_payload` design family.
- Generate api-payload artifacts into the adapter module.
- Keep the migration bounded through a fixed generator id and fixed template id.
- Preserve the old api-payload contract shape:
  - outer `object`
  - nested `Request`
  - nested `Response`
  - nested payload types
- Adopt the new nested-type hierarchy contract instead of the old flat nested-type layout.
- Prove the family with planner, renderer, and functional coverage.

## Non-Goals

- Do not reopen generator-core or pipeline-stage architecture.
- Do not widen `use()` beyond design templates.
- Do not carry old alias compatibility such as `payload`, `request_payload`, `req_payload`, `request`, or `req`.
- Do not add list or page api-payload variants in this slice.
- Do not redesign the default adapter portal package family in this slice.
- Do not mix `validator` follow-up work, bootstrap, or support-track integration work into this slice.

## Current Context

The old codegen system already treats `api_payload` as a distinct design family.

Its current behavior is:

- standard `api_payload` plus several old aliases route into the same family
- output is a single payload artifact under adapter portal api payload packages
- the generated contract is an outer object containing `Request`, `Response`, and nested payload data classes

The new pipeline currently has no equivalent api-payload family.

The design-generator mainline already established a migration pattern through:

- bounded generator ids
- bounded template ids
- helper-first template contracts
- planner ownership of output paths
- regression coverage at planner, renderer, and functional levels

The api-payload migration should follow that pattern instead of reviving the old tag alias surface or the old flat nested-type template shape.

## Design Decision

This slice should use an explicit bounded api-payload generator rather than folding api-payload output into the existing `design` request generator.

The public surface should add:

- `designApiPayload`

The internal generator id should be:

- `design-api-payload`

This is preferred because:

- `api_payload` is not a request family
- its output belongs to adapter portal payload packages rather than application request packages
- a separate generator boundary keeps the existing `design` request planner from becoming an unbounded catch-all
- it matches the mainline discipline already used for `designQueryHandler`, `designClient`, and `designValidator`

## Source And Canonical Model

This slice should add a dedicated canonical api-payload slice instead of forcing api payloads into `RequestModel`.

The canonical model should gain:

- `apiPayloads: List<ApiPayloadModel>`

`ApiPayloadModel` should be minimal and contain only what this slice needs:

- `packageName`
- `typeName`
- `description`
- `requestFields`
- `responseFields`

This slice should continue to use `FieldModel` for request and response fields so it can reuse the current field parsing, type resolution, and default-value pipeline.

The source contract should remain narrow:

- only standard `tag == "api_payload"` is accepted in this slice

This slice intentionally does not normalize old aliases such as:

- `payload`
- `request_payload`
- `req_payload`
- `request`
- `req`

The naming rule should remain conservative and old-compatible:

- `batchSaveAccountList` -> `BatchSaveAccountList`
- `videoPostOwner` -> `VideoPostOwner`

No additional suffix such as `Payload` should be appended automatically.

## Public Gradle DSL

The Gradle DSL should expose one explicit generator:

```kotlin
cap4k {
    project {
        basePackage.set("com.acme.demo")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designApiPayload {
            enabled.set(true)
        }
    }
}
```

Dependency rules:

- `designApiPayload` requires enabled `designJson`
- `designApiPayload` requires `project.adapterModulePath`

This slice should not require application or domain module paths.

## Api Payload Planner

Add a dedicated planner for api-payload artifacts:

- class: `DesignApiPayloadArtifactPlanner`
- generator id: `design-api-payload`

Responsibilities:

- read canonical `apiPayloads`
- emit bounded template id:
  - `design/api_payload.kt.peb`
- write to adapter module paths:
  - `.../src/main/kotlin/<base>/adapter/portal/api/payload/<package>/<TypeName>.kt`
- render package names under:
  - `<basePackage>.adapter.portal.api.payload.<package>`

The planner should provide render context with:

- `packageName`
- `typeName`
- `description`
- `imports`
- `requestFields`
- `responseFields`
- `requestNestedTypes`
- `responseNestedTypes`

This planner should not be folded into the request-family planner because api-payload output has a different module role, package family, and outer contract shape.

## Nested Type Hierarchy Contract

This slice must follow the current pipeline nested-type contract rather than the old flat nested-type contract.

The required hierarchy is:

- request nested types render only under `Request`
- response nested types render only under `Response`
- nested types do not render directly under the outer payload object

The pipeline rules remain:

- nested field paths must have exactly one level:
  - `address.city` is allowed
  - `address.location.city` is not allowed
- each nested group requires a compatible direct root field:
  - `address: Address`
  - `address: Address?`
  - `address: List<Address>`
- a nested group without a compatible direct root field must fail fast
- a direct root field with an incompatible type must fail fast
- duplicate direct root declarations for the same nested group must fail fast
- request and response nested namespaces are isolated from each other

That means the migrated api-payload template should generate this shape:

```kotlin
object BatchSaveAccountList {
    data class Request(
        val address: Address?,
    ) {
        data class Address(
            val city: String,
        )
    }

    data class Response(
        val result: Result?,
    ) {
        data class Result(
            val success: Boolean,
        )
    }
}
```

and not the old flat shape where nested data classes were emitted directly under the outer object.

## Template Contract

This slice introduces exactly one bounded preset template:

- `design/api_payload.kt.peb`

The generated artifact should preserve the old api-payload contract shape:

- outer `object {{ typeName }}`
- nested `Request`
- nested `Response`

but it should consume the new pipeline nested-type contract:

- `requestNestedTypes` render inside `Request`
- `responseNestedTypes` render inside `Response`

The template should use the helper-first contract through:

- `imports(imports)` for explicit import rendering
- `type(field)` or `type(field.type)` for resolved type rendering
- Kotlin-ready `defaultValue`

This slice does not need `use()` because the default api-payload contract does not rely on explicit framework import helpers.

The template should preserve the old empty-contract behavior:

- when request fields are absent, render empty `Request`
- when response fields are absent, render empty `Response`

This slice should not introduce additional wrapper interfaces, request contracts, or adapter runtime hooks.

## Override Contract

Template override remains bounded and unchanged in mechanism:

```kotlin
templates {
    overrideDirs.from("codegen/templates")
}
```

User overrides may replace only:

- `design/api_payload.kt.peb`

This slice does not introduce pattern routing, alias routing, or api-payload-specific runtime hooks.

## Validation Strategy

Validation should cover three levels.

### Planner / Unit

Add planner-level tests that verify:

- only `api_payload` entries land in canonical `apiPayloads`
- old alias tags do not enter canonical `apiPayloads`
- the generated type name does not gain an extra suffix
- planner output path lands under adapter `portal/api/payload`
- planner package name lands under `adapter.portal.api.payload`
- planner context includes `requestFields`, `responseFields`, `requestNestedTypes`, and `responseNestedTypes`
- nested groups follow the current pipeline hierarchy rules
- enabling `designApiPayload` without `adapterModulePath` fails during configuration
- enabling `designApiPayload` without enabled `designJson` fails during configuration

### Renderer

Add renderer regression coverage that verifies:

- `design/api_payload.kt.peb` renders the outer payload object
- `Request` and `Response` render with helper-driven imports
- nested request types render inside `Request`
- nested response types render inside `Response`
- default values render from Kotlin-ready `defaultValue`
- override template resolution works for `design/api_payload.kt.peb`

### Functional

Add a representative functional fixture that:

- enables `designApiPayload`
- supplies at least one `api_payload` design entry
- verifies `cap4kPlan` emits `design/api_payload.kt.peb`
- verifies `cap4kGenerate` writes a payload file under `adapter/portal/api/payload`
- verifies override template replacement for `design/api_payload.kt.peb`
- verifies invalid config failure when `designApiPayload` is enabled without required inputs

## Recommended Fixture Shape

The first representative fixture should stay minimal:

- one `api_payload` entry
- one adapter package
- one generated payload file
- one request nested type
- one response nested type

This slice does not need alias compatibility, list/page payload variants, or project-specific portal conventions to prove the migration contract.

## Non-Default Follow-Up

After this slice, likely adjacent design-family migration candidates include whatever bounded family remains after `api_payload`.

Possible later follow-up slices may revisit:

- whether any old api-payload aliases deserve explicit migration support
- whether empty `Request` and `Response` forms should be further normalized across families

Those remain separate follow-up slices and should not be mixed into this implementation.
