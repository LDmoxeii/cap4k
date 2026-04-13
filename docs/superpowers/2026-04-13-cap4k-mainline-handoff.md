# Cap4k Mainline Handoff

Date: 2026-04-13

## Purpose

This document is the persistent handoff for the `cap4k` redesign.

It exists so a new agent does not need to reconstruct the project roadmap from chat history.

It answers four questions:

1. What is the original mainline?
2. Which slices were completed on that mainline?
3. Which slices were support work for real-project integration rather than the mainline itself?
4. What should the next agent do when asked to continue?

## One-Screen Summary

The original mainline is:

1. reset architecture to a fixed-stage pipeline
2. migrate major capability slices into that pipeline
3. improve design-generator output quality until old template migration becomes practical

Current state:

- architecture reset is complete
- major pipeline capability slices are complete
- design-generator quality slices through `use()` are complete
- several real-project integration support slices are also complete

Next mainline step:

- design template migration / helper adoption

That is the next step unless the user explicitly asks to prioritize a real-project blocker instead.

## Stable Architectural Boundaries

These are already decided and should not be reopened casually:

- pipeline execution order is fixed by plugin developers
- repository users may enable or disable sources and generators, but may not customize stage order
- sources read input only
- core assembles canonical model and orchestrates planning
- generators consume canonical model and emit artifact plans
- renderer helpers stay thin
- type resolution stays in Kotlin code, not in Pebble templates
- sibling design-entry type references remain unsupported
- short-name resolution remains conservative
- explicit FQN and symbol identity remain the truth source

Primary source:

- [2026-04-09-cap4k-pipeline-redesign-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)

## Mainline Roadmap

The redesign has two major phases.

### Phase A: Pipeline Architecture Reset

Goal:

- replace the old mixed task/context/generator model with a fixed-stage pipeline

Completed slices:

1. `pipeline foundation`
   - plan: [2026-04-09-cap4k-pipeline-foundation.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/plans/2026-04-09-cap4k-pipeline-foundation.md)
   - result:
     - pipeline core runner
     - renderer split
     - initial design-json + ksp path

2. `db aggregate minimal`
   - spec: [2026-04-09-cap4k-pipeline-db-aggregate-minimal-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-db-aggregate-minimal-design.md)
   - result:
     - DB source
     - minimal aggregate generator
     - schema/entity/repository path

3. `ir flow`
   - spec: [2026-04-10-cap4k-pipeline-ir-flow-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-pipeline-ir-flow-design.md)
   - result:
     - IR source
     - flow export in pipeline

4. `drawing-board`
   - spec: [2026-04-10-cap4k-pipeline-drawing-board-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-pipeline-drawing-board-design.md)
   - result:
     - drawing-board pipeline slice
     - old multi-file naming preserved

5. `DSL consolidation`
   - spec: [2026-04-10-cap4k-pipeline-dsl-consolidation-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-pipeline-dsl-consolidation-design.md)
   - result:
     - `cap4k { ... }`
     - nested Gradle DSL
     - explicit source/generator enablement

Phase A status:

- complete

### Phase B: Design Generator Quality Mainline

Goal:

- move design generation from smoke-test shells to realistic, migration-ready template output

Completed mainline slices:

1. `minimal usable`
   - spec: [2026-04-10-cap4k-design-generator-minimal-usable-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-design-generator-minimal-usable-design.md)
   - result:
     - object-scoped `Cmd/Qry`
     - `Request/Response`
     - nested types
     - first usable render model

2. `type/import resolution`
   - spec: [2026-04-10-cap4k-design-generator-type-import-resolution-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-design-generator-type-import-resolution-design.md)
   - result:
     - structured type parsing
     - explicit FQN preference
     - collision-safe rendered types

3. `auto-import`
   - spec: [2026-04-11-cap4k-design-generator-auto-import-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-11-cap4k-design-generator-auto-import-design.md)
   - result:
     - symbol-aware auto-import
     - fail-fast on unknown or ambiguous short types

4. `template helpers`
   - spec: [2026-04-11-cap4k-design-generator-template-helpers-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-11-cap4k-design-generator-template-helpers-design.md)
   - result:
     - thin `type()` and `imports()` helper contract

5. `default value`
   - spec: [2026-04-12-cap4k-design-generator-default-value-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-12-cap4k-design-generator-default-value-design.md)
   - result:
     - explicit default values
     - conservative formatting and validation

6. `use() helper`
   - spec: [2026-04-12-cap4k-design-generator-use-helper-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-12-cap4k-design-generator-use-helper-design.md)
   - result:
     - design-template-only `use()`
     - explicit import registration
     - collision-safe merge with `imports()`

Phase B status:

- current mainline is complete through helper introduction
- helper adoption and template migration are still pending

## Support Tracks That Are Not the Original Mainline

These slices were necessary, but they are support work rather than the original quality-enhancement sequence.

### 1. Real Project Local Integration

Docs:

- [2026-04-09-cap4k-only-danmuku-local-integration-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-only-danmuku-local-integration-design.md)
- [2026-04-09-cap4k-only-danmuku-local-integration.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/plans/2026-04-09-cap4k-only-danmuku-local-integration.md)

Purpose:

- validate that the pipeline can be consumed by `only-danmuku`

Use this track when:

- the user asks to publish to Maven local or run real-project smoke verification

### 2. Real Project Integration Boundaries

Docs:

- [2026-04-11-cap4k-real-project-integration-boundaries-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-11-cap4k-real-project-integration-boundaries-design.md)
- [2026-04-11-cap4k-real-project-integration-boundaries.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/plans/2026-04-11-cap4k-real-project-integration-boundaries.md)

Purpose:

- make real-project edges explicit
- add aggregate unsupported-table policy
- add design manifest-first input selection

This is support work, not the next mainline target.

### 3. Project Type Registry

Docs:

- [2026-04-12-cap4k-project-type-registry-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-12-cap4k-project-type-registry-design.md)
- [2026-04-12-cap4k-project-type-registry.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/plans/2026-04-12-cap4k-project-type-registry.md)

Purpose:

- provide project-level explicit short-name to FQN mapping
- unblock real project design generation without weakening symbol rules globally

This is also support work, not the next mainline target.

## Dependency Map Between Slices

Use this as the practical dependency order.

### Hard dependencies

- `pipeline foundation` -> everything else
- `design minimal usable` -> `type/import resolution`
- `type/import resolution` -> `auto-import`
- `type/import resolution` -> `template helpers`
- `template helpers` -> `use() helper`
- `default value` depends on the render-model and helper direction established by the previous design slices

### Soft/support dependencies

- `real-project integration boundaries` depends on pipeline DSL and aggregate/design slices already existing
- `project type registry` depends on conservative short-type handling already being in place
- local integration docs depend on the current pipeline being publishable and consumable

### What does not block the mainline

- sibling design-entry references staying unsupported
- aggregate composite primary key support
- enum-specific registry automation

Those are not prerequisites for continuing the original mainline.

## Current Contract That Next Agents Must Respect

These points were re-confirmed during implementation and real-project verification:

- `use()` is design-template-only
- `use()` only accepts explicit FQN strings
- `use()` is only for explicit imports, not type resolution
- `imports()` remains the output path for import lines
- `defaultValue` in render models is already Kotlin-ready, not raw source text
- project type registry is a conservative fallback, not a new global type-inference engine
- real-project archived design debt must not silently become new framework rules

## What The Next Agent Should Do If The User Says “Continue The Original Mainline”

Start from this target:

### Next Mainline Slice

`design template migration / helper adoption`

Recommended scope:

1. define the recommended design-template contract around:
   - `type()`
   - `imports()`
   - `use()`
   - formatted `defaultValue`
2. migrate default design templates and migration-friendly override patterns toward that contract
3. add functional fixtures that exercise realistic template migration scenarios

Recommended non-goals for that slice:

- do not reopen generator-core architecture
- do not add sibling design-entry type support
- do not widen `use()` beyond design templates
- do not turn project type registry into implicit auto-discovery

## What The Next Agent Should Do If The User Says “Unblock Real Project”

Do not start from the mainline slice above.

Instead:

1. read the integration-boundary spec
2. determine whether the blocker is:
   - design input debt
   - unsupported schema shape
   - project-specific type registration
   - old archived design semantics
3. solve it as project-side or integration-side work first
4. only promote it into framework behavior if the user explicitly approves that change in contract

## Recommended Reading Order For A New Agent

1. [AGENTS.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/AGENTS.md)
2. [2026-04-13-cap4k-mainline-handoff.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/2026-04-13-cap4k-mainline-handoff.md)
3. [2026-04-09-cap4k-pipeline-redesign-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
4. the latest mainline design-generator specs in this order:
   - [2026-04-10-cap4k-design-generator-minimal-usable-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-design-generator-minimal-usable-design.md)
   - [2026-04-10-cap4k-design-generator-type-import-resolution-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-10-cap4k-design-generator-type-import-resolution-design.md)
   - [2026-04-11-cap4k-design-generator-auto-import-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-11-cap4k-design-generator-auto-import-design.md)
   - [2026-04-11-cap4k-design-generator-template-helpers-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-11-cap4k-design-generator-template-helpers-design.md)
   - [2026-04-12-cap4k-design-generator-default-value-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-12-cap4k-design-generator-default-value-design.md)
   - [2026-04-12-cap4k-design-generator-use-helper-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-12-cap4k-design-generator-use-helper-design.md)

## Bottom Line

If you are a new agent and the user says only “continue”:

- continue the original mainline
- do not default to another integration workaround
- the next target is template migration / helper adoption
