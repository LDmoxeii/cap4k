# Cap4k Pipeline Local Maven Integration For only-danmuku

## Summary

This design validates the first `cap4k` pipeline vertical slice in a real consumer project without introducing remote-release variables.

The validation path is intentionally narrow:

- publish the new `cap4k-plugin-pipeline-*` modules to `mavenLocal()`
- switch `only-danmuku` root build from the legacy codegen plugin to the new pipeline plugin for a smoke run
- feed the pipeline a single test design file containing one command and one query
- run `cap4kPlan` and `cap4kGenerate`
- inspect the generated artifacts under `only-danmuku-application`

## Goals

- Verify the new Gradle plugin can be resolved from `mavenLocal()` by a real project.
- Verify `design-json + ksp-metadata + design generator` works outside the fixture project.
- Generate one test `Cmd` and one test `Qry` in `only-danmuku`.
- Keep the validation surface small enough to debug quickly.

## Non-Goals

- Do not publish to the remote AliYun Maven repository yet.
- Do not migrate `only-danmuku` off the legacy plugin system permanently.
- Do not validate DB source, IR analysis, aggregate generation, drawing-board generation, or flow export.
- Do not expand the new plugin DSL during this smoke run.

## Approach

The integration uses the plugin exactly as implemented on `master`:

- plugin id: `com.only4.cap4k.plugin.pipeline`
- sources: `design-json`, `ksp-metadata`
- generator: `design`
- tasks: `cap4kPlan`, `cap4kGenerate`

`only-danmuku` already has compatible prerequisites:

- `mavenLocal()` is already present in `settings.gradle.kts`
- `only-danmuku-domain` already produces KSP aggregate metadata
- `only-danmuku-application` is the intended output module

## Consumer Configuration

`only-danmuku` will be configured with:

- `basePackage = "edu.only4.danmuku"`
- `applicationModulePath = "only-danmuku-application"`
- `designFiles = iterate/pipeline-smoke/pipeline_smoke_gen.json`
- `kspMetadataDir = only-danmuku-domain/build/generated/ksp/main/resources/metadata`
- `templateOverrideDir = codegen/templates`

The smoke design file will target an existing aggregate so the KSP metadata lookup succeeds.

## Validation

Success means all of the following are true:

- `cap4k` publishes all required pipeline artifacts to `mavenLocal()`
- `only-danmuku` resolves the plugin from `mavenLocal()`
- `cap4kPlan` produces `build/cap4k/plan.json`
- `cap4kGenerate` writes one `Cmd` and one `Qry`
- generated files land under `only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/...`

## Risks

- The pipeline plugin currently assumes a repository-relative filesystem path for the application module, not a Gradle module path.
- The smoke design file must use the current simplified JSON schema and only `cmd/qry` tags.
- The generated templates are intentionally minimal, so a successful smoke run does not prove the final production code shape is ready.
