# Cap4k Remove Wrapper From Core Pipeline And Docs Design

Date: 2026-05-08
Status: Approved for implementation planning

## Summary

`wrapper` still survives in the current `cap4k` master as an active aggregate-generation option, not just as historical residue.

This slice removes that active surface from the core pipeline and from live core docs.

The intended outcome is narrow:

- `wrapper` is no longer part of the aggregate DSL contract
- the aggregate planner no longer produces wrapper artifacts
- aggregate schema rendering no longer branches on wrapper state
- core tests and fixtures stop treating wrapper as a normal supported capability
- live docs stop presenting wrapper as a current framework surface

This is a removal slice, not a broad legacy cleanup pass.

## Backlog Source Of Truth

Primary issue:

- `cap4k#22` `framework: remove wrapper from core pipeline and docs`

Related background:

- `cap4k#15` concluded that `Wrapper` is a remove-or-deprecate target rather than a long-term core framework concept
- `cap4k#24` is already complete and merged, so this slice does not need to preserve wrapper for legacy `cap4k-plugin-codegen` coexistence

The issue text defines the boundary.
This document only turns that boundary into an implementation contract.

## Baseline And Constraints

Execution baseline:

- `cap4k/master@3bb6ce76`

Hard constraints for this slice:

- do not touch `settings.gradle.kts`
- do not turn this into `cap4k-plugin-codegen` cleanup
- do not do generic repository cleanup
- do not sweep historical archive docs under `docs/superpowers/specs/**` or `docs/superpowers/plans/**`
- do not reintroduce wrapper as a recommended or transitional modeling pattern

## Current Wrapper Surface Classification

### Active Core Pipeline Surfaces

These are still part of the current active contract and must be removed, not merely relabeled:

1. Aggregate DSL and Gradle config surface
   - `cap4k-plugin-pipeline-gradle/.../Cap4kExtension.kt`
   - `cap4k-plugin-pipeline-gradle/.../Cap4kProjectConfigFactory.kt`
   - current DSL still exposes `generators.aggregate.artifacts.wrapper`
   - current project config still emits `artifact.wrapper`
   - current validation still treats wrapper as a first-class generator concern

2. Aggregate planner surface
   - `cap4k-plugin-pipeline-generator-aggregate/.../AggregateArtifactSelection.kt`
   - `cap4k-plugin-pipeline-generator-aggregate/.../AggregateArtifactPlanner.kt`
   - `cap4k-plugin-pipeline-generator-aggregate/.../AggregateWrapperArtifactPlanner.kt`
   - wrapper is still selected, planned, and emitted as a real artifact family

3. Aggregate schema render-model and template surface
   - `cap4k-plugin-pipeline-generator-aggregate/.../SchemaArtifactPlanner.kt`
   - `cap4k-plugin-pipeline-renderer-pebble/.../aggregate/schema.kt.peb`
   - current schema output still carries `wrapperEnabled`, `aggregateTypeFqn`, and wrapper-specific predicate signatures

4. Wrapper template surface
   - `cap4k-plugin-pipeline-renderer-pebble/.../aggregate/wrapper.kt.peb`
   - this is still a shipped preset template, not dead documentation

5. Core tests and fixtures
   - aggregate planner tests
   - Pebble renderer tests
   - Gradle functional tests
   - Gradle compile functional tests
   - aggregate sample fixtures that still set `wrapper.set(true)` or compile against `Agg*`

6. Live docs and capability truth sources
   - `docs/public/reference/generator-dsl.md`
   - `docs/public/authoring/generation-boundaries.md`
   - `docs/public/authoring/generator/code-generation.md`
   - `docs/superpowers/capability-matrix.md`

These are the core issue surface.

### Compatibility Residue

These are not part of the core removal target for this issue and should stay out of scope unless the removal work directly forces a tiny follow-up:

1. Runtime internal wrapper behavior in runtime modules
   - `ddd-core`
   - `ddd-domain-repo-jpa`
   - internal wrap/unwrap machinery is compatibility residue, not core pipeline positioning

2. Historical archive docs
   - prior specs
   - prior plans
   - chat-derived historical notes

3. Docs that already position wrapper as non-core or as a cautionary smell
   - `docs/public/framework-positioning.md`
   - `docs/public/framework-positioning.md`
   - `docs/public/authoring/advanced/strong-id.md`

4. `AGENTS.md`
   - current audit does not show wrapper being presented as an active supported capability
   - this file remains in the audit set, but no edit is required unless implementation-time recheck uncovers stale wording

## Remove Vs Deprecate Decision

This slice should use **true remove**, not bounded deprecate.

## Why Remove Is The Minimum Viable Strategy

`wrapper` is currently an end-to-end active contract:

- DSL knob
- project-config option
- planner branch
- artifact planner
- layout helper
- preset template
- schema render-model branch
- renderer assertions
- functional fixtures
- compile fixtures
- capability matrix row

Leaving any of those as a deprecated but still-shipping core path would preserve:

- schema contract branching
- planner complexity
- public DSL baggage
- compile-fixture and functional-test maintenance burden
- user-facing ambiguity about whether wrapper is still part of the supported happy path

That would fail the issue goal of making wrapper exit the core pipeline and core docs.

True removal is also operationally safe here because:

- wrapper is already default-off
- public framework positioning already says wrapper has exited core positioning
- `#24` already removed the need to keep this around for legacy module coordination
- the user explicitly prefers deletion

## Core Design Decisions

### 1. Remove Wrapper From Aggregate DSL And Config Projection

Delete the wrapper property from the aggregate artifact DSL.

Concretely:

- remove `AggregateGeneratorArtifactsExtension.wrapper`
- stop validating wrapper-specific dependencies
- stop emitting `artifact.wrapper` into `ProjectConfig.generators["aggregate"].options`

No deprecated no-op shim should remain in the DSL.
If a downstream build still calls `wrapper.set(...)`, it should fail at configuration/compile time and force the caller to stop depending on a removed core surface.

### 2. Remove Wrapper Planning Entirely

Delete the wrapper planner from the aggregate generator family.

Concretely:

- remove `wrapperEnabled` from `AggregateArtifactSelection`
- delete `AggregateWrapperArtifactPlanner.kt`
- remove the wrapper delegate from `AggregateArtifactPlanner`
- stop generating any plan item with `templateId = "aggregate/wrapper.kt.peb"`

This is a real contract removal, not a hidden no-op.

### 3. Collapse Aggregate Schema Back To The Non-Wrapper Contract

The aggregate schema surface should no longer know whether wrapper ever existed.

Concretely:

- remove `wrapperEnabled` from the schema render model
- remove `aggregateTypeFqn` from the schema render model
- stop computing wrapper-specific aggregate FQNs in `SchemaArtifactPlanner`
- update `aggregate/schema.kt.peb` so root schema predicate helpers always expose `JpaPredicate<Entity>`

The post-removal schema contract becomes the only contract:

- `predicateById(id: Any): JpaPredicate<Entity>`
- `predicateByIds(...): JpaPredicate<Entity>`
- `predicate(...): JpaPredicate<Entity>`

No wrapper-specific `AggregatePredicate<AggX, X>` branch remains in the core template.

### 4. Remove Dead Layout Support

If wrapper generation is deleted, the layout API should not keep a wrapper-specific package helper.

Concretely:

- remove `ArtifactLayoutResolver.aggregateWrapperPackage(...)`
- remove or rewrite its tests

This keeps the public planner/template contract honest.

### 5. Reframe Core Tests Around Remaining Supported Aggregate Capabilities

Tests should prove the surviving contract rather than continuing to model wrapper as optional support.

Required changes:

- planner tests stop asserting wrapper artifacts or wrapper render-model fields
- renderer tests stop rendering `aggregate/wrapper.kt.peb`
- schema renderer tests assert only `JpaPredicate<Entity>` output
- functional aggregate sample no longer sets `wrapper.set(true)`
- compile fixture no longer depends on `AggVideoPost.Id`

The compile fixture should continue proving that checked-in aggregate helpers participate in domain compilation, but that proof should be carried by the remaining supported surfaces such as `factory` and `specification`.

### 6. Remove Wrapper From Live Docs And Capability Truth Sources

Live docs must stop listing wrapper as a normal aggregate artifact.

Required docs changes:

- remove `artifacts.wrapper` from generator DSL reference
- remove wrapper rows and prose from generation-boundaries guidance
- remove wrapper from code-generation checked-in artifact guidance
- remove `aggregate.wrapper` from `docs/superpowers/capability-matrix.md`

Docs already stating that wrapper has exited core positioning may remain, because they do not present wrapper as supported core capability.

## File-Level Expectations

Expected production deletions or modifications:

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`

Expected test and fixture work:

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

Expected live docs work:

- Modify: `docs/public/reference/generator-dsl.md`
- Modify: `docs/public/authoring/generation-boundaries.md`
- Modify: `docs/public/authoring/generator/code-generation.md`
- Modify: `docs/superpowers/capability-matrix.md`

Likely no-op audit target:

- `AGENTS.md`

## Verification Strategy

Minimum framework verification for this slice:

1. aggregate planner unit tests
2. Pebble renderer unit tests
3. pipeline Gradle config factory unit tests
4. aggregate functional test proving plan/generate output without wrapper
5. aggregate compile functional test proving domain compilation without `Agg*`

Representative commands:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

## Non-Goals

This slice will not:

- remove runtime internal wrapper compatibility machinery
- rewrite aggregate runtime semantics
- touch `settings.gradle.kts`
- make decisions about `cap4k-plugin-codegen` existence
- sweep broad legacy docs unrelated to current wrapper positioning
- redesign aggregate schema beyond collapsing the wrapper branch
- turn this issue into a generic aggregate redesign
