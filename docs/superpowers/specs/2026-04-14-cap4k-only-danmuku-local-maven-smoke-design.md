# Cap4k only-danmuku Local Maven Smoke Integration

## Summary

This design validates the current `cap4k` pipeline in a real consumer project by publishing the local `master` build to `mavenLocal()` and temporarily wiring `only-danmuku` to the new Gradle plugin for a narrow smoke run.

The validation surface is intentionally small:

- publish the current `cap4k` pipeline artifacts to `mavenLocal()`
- temporarily switch `only-danmuku` root build from the legacy codegen plugins to the pipeline plugin
- feed one smoke `design-json` file targeting one existing aggregate
- run `cap4kPlan` and `cap4kGenerate`
- inspect generated command/query/query-handler artifacts
- restore the temporary consumer changes after the run

This is support-track integration work. It is not a permanent consumer migration and it does not change the original mainline roadmap.

## Goals

- Verify `com.only4.cap4k.plugin.pipeline` can be resolved from `mavenLocal()` by `only-danmuku`.
- Verify the current pipeline design slice works in a real project:
  - `design-json`
  - `ksp-metadata`
  - `design`
  - `design-query-handler`
- Generate one command family artifact and three query family artifacts:
  - default query
  - list query
  - page query
- Verify the corresponding query-handler family lands in the adapter module.
- Keep the validation narrow enough that failures can be attributed quickly.

## Non-Goals

- Do not publish anything to a remote Maven repository.
- Do not migrate `only-danmuku` permanently to the pipeline plugin.
- Do not keep legacy codegen and the new pipeline plugin running together in the same smoke run.
- Do not validate `db`, `aggregate`, `drawing-board`, or `flow`.
- Do not validate bootstrap / arch-template migration.
- Do not make the generated code compile as a success criterion for this slice.
- Do not introduce template override variables into the smoke run.

## Current Context

The current consumer and producer state is compatible with a local smoke run:

- `cap4k` publishes Gradle plugin modules with group `com.only4` and version `0.4.2-SNAPSHOT`.
- `cap4k` pipeline plugin id is `com.only4.cap4k.plugin.pipeline`.
- `only-danmuku/settings.gradle.kts` already includes `mavenLocal()` in plugin and dependency repositories.
- `only-danmuku` root build still uses the legacy plugins:
  - `com.only4.cap4k.plugin.codegen`
  - `com.only4.cap4k.plugin.codeanalysis.flow-export`
- `only-danmuku-domain/build/generated/ksp/main/resources/metadata` already exists and contains aggregate metadata.

This means the smoke run can reuse an existing aggregate and does not need a separate synthetic domain module.

## Recommended Approach

Use a temporary root-build switch in the real `only-danmuku` workspace, run a local smoke validation, then restore the consumer worktree.

This is preferred over:

- a detached sample consumer, because that would not exercise the real Gradle graph
- temporary coexistence with the legacy plugins, because that would introduce unnecessary task/configuration conflicts into a smoke-only validation

## Temporary Consumer Configuration

The temporary `only-danmuku/build.gradle.kts` should apply only the pipeline plugin:

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.only4.cap4k.plugin.pipeline") version "0.4.2-SNAPSHOT"
}
```

The temporary `cap4k` block should stay minimal:

```kotlin
cap4k {
    project {
        basePackage.set("edu.only4.danmuku")
        applicationModulePath.set("only-danmuku-application")
        adapterModulePath.set("only-danmuku-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("only-danmuku-domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        design {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
    }
}
```

Important constraints:

- do not carry over `cap4kCodegen { ... }` settings such as `archTemplate`, database config, or type mappings
- do not wire `templates.overrideDirs`
- do not enable any other pipeline source or generator

The smoke run should rely on the default `ddd-default` preset to avoid mixing old codegen template assumptions into a pipeline validation.

## Smoke Input Design

The smoke design file should be added at:

- `only-danmuku/iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json`

It should target one existing aggregate with known KSP metadata:

- `VideoPost`

The design file should emit four requests under an isolated package namespace so the generated output does not collide with existing business files:

- command:
  - `CreatePipelineSmokeVideoPostCmd`
- queries:
  - `FindPipelineSmokeVideoPostQry`
  - `FindPipelineSmokeVideoPostListQry`
  - `FindPipelineSmokeVideoPostPageQry`

The namespace should stay under a dedicated integration path, for example:

- application command package:
  - `edu.only4.danmuku.application.commands.pipeline.integration.video_post.create`
- application query package:
  - `edu.only4.danmuku.application.queries.pipeline.integration.video_post.read`
- adapter query-handler package:
  - `edu.only4.danmuku.adapter.application.queries.pipeline.integration.video_post.read`

The file names and package names should include `PipelineSmoke` or `pipeline.integration` to make cleanup unambiguous.

## Execution Sequence

The smoke run should execute in this order:

1. Publish the current `cap4k` workspace to `mavenLocal()`.
2. Back up `only-danmuku/build.gradle.kts`.
3. Replace the root build with the temporary pipeline-only smoke configuration.
4. Add the smoke `design-json` file.
5. Run `cap4kPlan` in `only-danmuku`.
6. Run `cap4kGenerate` in `only-danmuku`.
7. Inspect generated output paths and contents.
8. Restore the original `only-danmuku/build.gradle.kts`.
9. Remove the temporary smoke design file.

`only-danmuku-domain` metadata should be reused as-is. Only if the smoke run shows missing or stale metadata should the integration path expand to rebuilding the domain module.

## Validation Targets

The smoke run is successful when all of the following are true:

- `cap4k` pipeline artifacts are published to `mavenLocal()`.
- `only-danmuku` resolves the pipeline plugin from `mavenLocal()`.
- `cap4kPlan` succeeds and writes a plan report.
- `cap4kGenerate` succeeds.
- generated application artifacts appear under:
  - `only-danmuku-application/src/main/kotlin/.../application/commands/pipeline/integration/...`
  - `only-danmuku-application/src/main/kotlin/.../application/queries/pipeline/integration/...`
- generated adapter artifacts appear under:
  - `only-danmuku-adapter/src/main/kotlin/.../adapter/application/queries/pipeline/integration/...`
- the generated family includes:
  - one command
  - one default query
  - one list query
  - one page query
  - matching query handlers for the three query variants

Compilation of the entire consumer repository is explicitly not required for this validation slice.

## Expected Failure Modes

If the smoke run fails, the first debugging targets should be:

- plugin resolution from `mavenLocal()`
- repository-relative module path resolution for `applicationModulePath` and `adapterModulePath`
- missing or stale KSP metadata under `only-danmuku-domain/build/generated/ksp/main/resources/metadata`
- `design-json` schema mismatches against the current pipeline source parser
- domain aggregate naming mismatches between the smoke file and the metadata snapshot

These failures are in scope for local investigation. Broader consumer migration issues are not.

## Restoration Rules

The smoke run must leave `only-danmuku` in its original state after validation:

- restore the original root `build.gradle.kts`
- delete the temporary smoke `design-json`
- do not commit any temporary consumer modifications as part of this slice

The lasting output of this work is:

- the published local Maven artifacts
- the validation result
- any support-track notes or follow-up fixes required in `cap4k`

## Follow-Up Boundary

If the smoke run reveals framework issues, follow-up work should stay narrowly scoped to pipeline integration boundaries.

This slice must not expand into:

- permanent `only-danmuku` migration
- bootstrap / arch-template migration
- reopening mainline design-template scope
- reintroducing legacy codegen template or routing semantics into the pipeline public contract
