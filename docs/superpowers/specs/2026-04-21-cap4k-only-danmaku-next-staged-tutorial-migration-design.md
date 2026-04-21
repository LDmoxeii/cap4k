# Cap4k only-danmaku-next Staged Tutorial And Migration Design

## Purpose

This design defines `only-danmaku-next` as a new Cap4k consumer project with two explicit roles:

- a real consumer verification project for the new plugin stack
- a staged teaching project that shows users how a project is built step by step

This project is not a test branch of `only-danmuku`, and it is not an in-place migration of the existing repository. It is a clean new project that may selectively copy non-plugin implementation from the old project in later stages.

The project must teach users:

- how to start from a minimal multi-module baseline
- how bootstrap changes project setup
- how aggregate generation enters the project
- how design generation enters the project
- what stays handwritten
- what is generated
- how to distinguish framework gaps from project-local work

## Why This Project Exists

The current Cap4k default mainline is near completion for bounded framework slices. The next highest-value work is no longer broad feature parity in isolation. The next highest-value work is controlled consumer adoption.

Using the old `only-danmuku` project directly as the main proving ground is not the preferred path for this effort because:

- it mixes legacy structure and new plugin behavior too early
- it makes framework gaps harder to separate from old project history
- it is not a clear teaching artifact for new users

`only-danmaku-next` solves this by becoming a clean-room consumer that can still borrow selected handwritten implementation from the old project later.

## Core Positioning

`only-danmaku-next` must always be treated as all of the following:

1. a new plugin-native consumer project
2. a staged tutorial repository
3. a migration rehearsal project
4. a boundary detector for framework-vs-project issues

It must not become:

- an ad hoc fixture hidden inside the Cap4k repo
- a silent clone of `only-danmuku`
- a dumping ground for framework workaround code with no teaching structure

## Repository Boundary

`only-danmaku-next` should be created as an independent project repository or independent workspace project. It should not live as a test fixture inside the Cap4k repository.

The Cap4k repository may keep this spec and later planning documents, but the tutorial project itself should have its own Git history, commits, and tags. That separate history is part of the teaching artifact.

## Teaching Model

This project must be navigable by Git history, not only by README prose.

Every stage that users are expected to follow must have:

- a dedicated stage document
- at least one dedicated commit
- one annotated Git tag
- a runnable or verifiable checkpoint

The repository must function as a replayable tutorial. Users should be able to:

1. check out a stage tag
2. read the matching stage document
3. run the listed commands
4. observe the expected result
5. continue to the next stage

## Hard Git Rules

The following are required:

1. every user-facing stage must correspond to a stable Git checkpoint
2. every stable stage checkpoint must have an annotated tag
3. every stage tag must point to a runnable or verifiable state
4. every stage document must describe only behavior that already exists at that tag
5. later commits may clarify earlier docs, but may not silently rewrite the meaning of earlier stages

Recommended tag naming:

- `only-danmaku-next-stage-0-minimal`
- `only-danmaku-next-stage-1-bootstrap`
- `only-danmaku-next-stage-2-aggregate`
- `only-danmaku-next-stage-3-design`
- `only-danmaku-next-stage-4-runtime`
- `only-danmaku-next-stage-5-first-feature`

## Repository Layout

The project should use a teaching-oriented layout from the start:

```text
only-danmaku-next/
  README.md
  docs/
    stages/
      00-minimal-project.md
      01-bootstrap-skeleton.md
      02-aggregate-generation.md
      03-design-slice.md
      04-runtime-glue.md
      05-first-feature-port.md
  build.gradle.kts
  settings.gradle.kts
  only-danmaku-domain/
  only-danmaku-application/
  only-danmaku-adapter/
  design/
  db/
```

The actual module layout can evolve by stage, but the documentation layout should exist from the start so users can see the intended staged path.

## Root README Contract

The root README should be a navigation document, not a full tutorial chapter.

It should explain:

- what `only-danmaku-next` is
- why it is not an in-place `only-danmuku` migration
- how to use stage tags
- which command validates each stage
- which files are generated, handwritten, or copied

The README should include a stage table:

```text
Stage | Tag | Goal | Primary Command
0 | only-danmaku-next-stage-0-minimal | minimal multi-module baseline | ./gradlew build
1 | only-danmaku-next-stage-1-bootstrap | bootstrap skeleton | ./gradlew cap4kBootstrap
2 | only-danmaku-next-stage-2-aggregate | aggregate from DB schema | ./gradlew cap4kGenerate
3 | only-danmaku-next-stage-3-design | design slice | ./gradlew cap4kGenerate
4 | only-danmaku-next-stage-4-runtime | runtime glue | ./gradlew runtimeSmoke
5 | only-danmaku-next-stage-5-first-feature | first real feature port | ./gradlew build
```

## Stage Document Contract

Each file under `docs/stages/` should use the same structure:

```md
# Stage N: Name

## Goal

## Starting Point

## What Changes

## Generated By Cap4k

## Written By Hand

## Copied From only-danmuku

## Commands

## Expected Result

## Why This Matters

## Next Stage
```

The `Copied From only-danmuku` section can explicitly say "none" for early clean-room stages.

## Stage 0: Minimal Project Baseline

Tag:

- `only-danmaku-next-stage-0-minimal`

Goal:

- establish the smallest understandable Kotlin multi-module baseline
- teach the domain/application/adapter split before any plugin-generated code appears

Must include:

- root Gradle configuration
- `only-danmaku-domain`
- `only-danmaku-application`
- `only-danmaku-adapter`
- root README
- `docs/stages/00-minimal-project.md`

Must not include:

- Cap4k bootstrap
- DB schema
- aggregate generation
- design generation
- copied code from `only-danmuku`

Validation command:

```powershell
./gradlew build
```

## Stage 1: Bootstrap Skeleton

Tag:

- `only-danmaku-next-stage-1-bootstrap`

Goal:

- introduce Cap4k bootstrap as the project skeleton mechanism
- teach what bootstrap generates and what slots mean

Must include:

- Cap4k bootstrap plugin configuration
- fixed bootstrap preset usage
- at least one bounded slot example
- `docs/stages/01-bootstrap-skeleton.md`

Must explain:

- which files are bootstrap-generated
- which files are slot-provided
- which parts are not open extension points

Validation commands:

```powershell
./gradlew cap4kBootstrapPlan
./gradlew cap4kBootstrap
```

## Stage 2: Aggregate Generation

Tag:

- `only-danmaku-next-stage-2-aggregate`

Goal:

- introduce DB-backed aggregate generation
- teach how explicit DB schema/comment truth flows into aggregate output

Must include:

- `db/schema.sql`
- one minimal aggregate such as `video_post`
- aggregate generator configuration
- generated domain-side aggregate artifacts
- `docs/stages/02-aggregate-generation.md`

Must explain:

- which DB annotations are explicit truth
- which behavior is not inferred
- where generated aggregate artifacts land

Validation commands:

```powershell
./gradlew cap4kPlan
./gradlew cap4kGenerate
./gradlew :only-danmaku-domain:compileKotlin
```

## Stage 3: Design Slice

Tag:

- `only-danmaku-next-stage-3-design`

Goal:

- introduce a minimal design-generation vertical slice
- teach how design source becomes application and adapter output

Recommended generated families:

- command
- query
- api payload
- validator

The first stage may use a narrower subset if that keeps the teaching path clearer, but it must still form a coherent design slice.

Must include:

- design input under `design/`
- explicit design manifest or file list
- design generator configuration
- generated application/adapter artifacts
- `docs/stages/03-design-slice.md`

Must explain:

- what the design source is
- how generated files map to modules
- which parts are request contracts
- which parts are handlers or adapter-facing artifacts

Validation commands:

```powershell
./gradlew cap4kPlan
./gradlew cap4kGenerate
./gradlew :only-danmaku-application:compileKotlin
./gradlew :only-danmaku-adapter:compileKotlin
```

## Stage 4: Runtime Glue

Tag:

- `only-danmaku-next-stage-4-runtime`

Goal:

- move from "generated and compiled" to "minimally runnable"
- teach the boundary between generated code and handwritten glue code

Must include:

- a minimal runtime or smoke entrypoint
- minimal configuration
- enough handwritten glue to connect generated code into a small runnable path
- `docs/stages/04-runtime-glue.md`

Must explain:

- which files are handwritten
- why they are handwritten
- what the plugin should not own
- what the runtime smoke actually proves

Preferred validation command:

```powershell
./gradlew runtimeSmoke
```

`./gradlew run` is acceptable if the project intentionally exposes a small application entrypoint at this stage, but `runtimeSmoke` is preferred because it keeps the teaching target narrow.

## Stage 5: First Real Feature Port

Tag:

- `only-danmaku-next-stage-5-first-feature`

Goal:

- port one small real feature from `only-danmuku`
- prove that generated code and selected old handwritten implementation can coexist in the new project

Must include:

- one clear small feature
- copied non-plugin implementation only
- explicit copy provenance
- `docs/stages/05-first-feature-port.md`

Must not include:

- copied generated code from `only-danmuku`
- silent mass copying of old project structure
- framework workaround code without classification

Validation commands:

```powershell
./gradlew build
./gradlew runtimeSmoke
```

If `runtimeSmoke` is not yet meaningful for the selected feature, the stage document must say why and provide the strongest available command.

## Copying Rules From only-danmuku

Copying from `only-danmuku` is allowed only for non-plugin implementation.

Allowed examples:

- business services
- utility classes
- adapter glue
- configuration fragments
- tests or test data that are not tied to legacy generation output

Disallowed examples:

- generated source from legacy codegen
- legacy plugin configuration copied as-is
- old template artifacts treated as new project truth
- large directory copies with no provenance

Every copied item must be documented with:

- source path in `only-danmuku`
- destination path in `only-danmaku-next`
- whether it was modified
- why it was copied instead of regenerated or rewritten

## Gap Classification

Every issue found while building `only-danmaku-next` must be classified before fixing.

Allowed classifications:

- `project-local`
- `framework-gap`
- `not-in-scope`

### `project-local`

Use this for work that belongs in `only-danmaku-next`, such as handwritten glue, configuration, business logic, or tutorial documentation.

### `framework-gap`

Use this only when the new plugin or Cap4k framework produces incorrect output, lacks a bounded capability that should belong to the framework, or exposes an unstable contract.

Framework gaps should be fixed in Cap4k first, then consumed by `only-danmaku-next`.

### `not-in-scope`

Use this for old-project behavior that is not part of the current tutorial path or bounded framework contract.

## Stage Sequencing Rule

Stages must be implemented in order.

No later stage may silently skip an earlier teaching checkpoint. In particular:

- Stage 1 must not appear without a meaningful Stage 0 baseline
- Stage 3 must not appear before aggregate/module structure is understandable
- Stage 5 must not become the first point where copied code appears without prior generated and handwritten boundaries being explained

If a stage becomes too large, it may be split into `stage-Xa` and `stage-Xb`, but each sub-stage must still be:

- individually taggable
- individually documented
- individually verifiable

## Non-Goals

This project does not aim to:

- replace `only-danmuku` immediately
- prove full old-codegen replacement
- hide framework gaps behind project-local workarounds
- become a grab-bag sample with no teaching structure
- copy the old project wholesale
- silently promote project-specific patterns into Cap4k framework contract

## Success Criteria

This design is successful when the resulting project:

- has a clear staged Git history
- has one annotated tag per user-facing stage
- lets a new user follow the project stage by stage with docs and commands
- cleanly separates generated, handwritten, and copied code
- serves as a real Cap4k consumer project
- provides a controlled place to surface framework gaps
- does not depend on mutating the old `only-danmuku` repository in place

## Recommended First Execution Order

After this spec, implementation should begin with:

1. Stage 0 project baseline
2. Stage 1 bootstrap skeleton
3. Stage 2 aggregate slice

This order is recommended because it establishes the teaching path before introducing business migration pressure.

Stage 5 should not begin until the repository already demonstrates:

- generated project structure
- generated aggregate output
- generated design output
- handwritten runtime glue

Only after those boundaries are visible should the first real feature be copied from `only-danmuku`.
