# Cap4k Reference Content Studio Design

Date: 2026-05-08

Status: Proposed

Scope: define the total-goal and first-version design for `cap4k-reference-content-studio`, a separate runnable reference project that should act as the official project-level evidence base for later `#17`, while staying aligned with the current Default Happy Path, authoring guides, testing contract, and framework capability boundaries.

Out of scope:

- implementing the reference project in this slice
- reviving `#14`
- enabling `enumTranslation` before `#33`
- implementing `#23` advanced read/write modeling in version one
- introducing authentication or authorization
- building a frontend demo UI
- introducing file upload/download workflows
- adding reject/re-edit/withdraw/offline lifecycle branches in version one
- patching missing framework generator families inside the reference project instead of tracking them separately

## Backlog Source

This design is the total-goal and first-version reference-project slice for:

- `#27` framework: create runnable content publishing and processing reference project

It also depends on already settled upstream guidance:

- `#18` testing skeleton feasibility
- `#21` advanced-concept practical examples
- `#22` remove wrapper from core pipeline and docs

And it must respect downstream sequencing constraints:

- `#33` before `#17` for `enumTranslation` decoupling
- `#34` for design-driven integration-event families
- `#35` for design-driven domain-service family support
- `#36` for first-class value-object generation contract

## Background

`cap4k` now has enough framework-level and documentation-level structure to stop talking about project authoring only in prose.

The current state is materially better than before:

- `#26` established a practical authoring-guide system centered on Default Happy Path
- `#21` added practical advanced-concept examples for `Value Object`, `Domain Service`, and `Saga`
- `#18` established the testing contract as docs + project-local helper + reference-project discipline rather than a heavy built-in testing DSL
- `#22` removed wrapper from the core pipeline and core docs, which means future reference material must no longer lean on wrapper-era examples

At the same time, one gap remains obvious:

- there is still no official runnable reference project that proves the current `cap4k` writing model end to end

That gap matters for both humans and later AI-collaboration work.

Without a stable runnable reference project:

- authoring guides remain partly prose-backed
- generator and handwritten ownership remain easy to misunderstand
- testing-contract guidance remains theoretical
- `#17` has no project-level anchor that is independent from ad hoc dogfood repositories

The new reference project is therefore not a demo toy and not another internal throwaway dogfood app. It is intended to become the official reference-grade sample that later documentation and AI guidance can cite directly.

## Problem

The current `cap4k` ecosystem can explain the intended project shape, but it still cannot point to one stable, independent, runnable project that proves the intended path.

The main problem is not “we need a sample app.” The real problem is:

- there is no single project that proves the current default writing path from bootstrap through runtime behavior
- there is no stable project-level artifact that shows how generated and handwritten code should coexist
- there is no independent reference repo that later `#17` can rely on instead of mixing framework guidance with unrelated business-project noise

If `#27` is handled too loosely, the outcome will drift in one of three bad directions:

1. a demo app that runs but does not prove the current framework boundaries
2. a project that quietly depends on `only-engine` or other non-core assumptions
3. a mixed “reference project + framework patching” effort that makes it unclear which behavior belongs to `cap4k` and which behavior is only temporary project glue

## Goals

1. Define `cap4k-reference-content-studio` as the long-term official reference-project line for `cap4k`.
2. Deliver a version-one runnable project that proves the current Default Happy Path rather than a simplified alternate architecture.
3. Keep the project independent from `only-engine`.
4. Make the project independently runnable through local `mavenLocal()` consumption of `cap4k`.
5. Preserve machine-readable HTTP contract evidence through runtime OpenAPI exposure and a committed static OpenAPI snapshot.
6. Preserve generated-versus-handwritten ownership evidence without making runtime correctness depend on committed build directories.
7. Consume the public testing contract from `#18` instead of inventing a second testing culture.
8. Give later `#17` a stable project-level anchor with:
   - runnable HTTP flow
   - `.http` interaction scripts
   - OpenAPI contract
   - smoke-test evidence
   - generated and handwritten ownership evidence

## Non-Goals

This slice must not:

- become a generic showcase of every current `cap4k` generator family
- block version one on `#23`, `#34`, `#35`, or `#36`
- quietly depend on capability gaps being fixed during the project build
- force a standalone read-model subsystem into version one
- use Swagger UI as the primary user-facing operation surface
- define a second testing contract outside `#18`
- turn the project into a framework-internals experiment

## Options Considered

### Option 1: strict default-path reference project with explicit handwritten transition surfaces

Build the project now, keep the current Default Happy Path intact, and treat missing framework generator families as explicit handwritten transition surfaces tracked by separate issues.

Pros:

- keeps `#27` moving instead of waiting for multiple framework feature slices
- preserves a clean boundary between framework gaps and project-level proof
- gives `#17` a real project anchor sooner

Cons:

- version one will contain some intentional handwritten surfaces
- issue and spec language must be very explicit so that “handwritten for now” does not look like “framework does not care”

### Option 2: wait for generator families to be complete before building the reference project

Delay `#27` until integration-event, domain-service, and value-object generation support have all been formalized.

Pros:

- the reference project could look more purely design-driven

Cons:

- blocks the project-level proof line for too long
- delays `#17`
- treats framework completeness as a prerequisite for evidence, which is the wrong dependency direction

### Option 3: patch missing generator families ad hoc inside the reference-project work

Let `#27` simultaneously build the project and close whichever framework capability gaps it encounters.

Pros:

- looks efficient on paper

Cons:

- mixes reference-project proof with framework feature development
- destroys issue boundaries
- makes later closure decisions difficult and unreliable

## Decision

Choose **Option 1**.

`#27` should become a structured reference-project line with a strict version-one scope. The reference project should not wait for all generator-family gaps to close, and it should not quietly patch those gaps inside its own implementation.

Instead:

- existing stable design-driven families should be used where possible
- current family gaps should be handled as explicit handwritten transition surfaces
- the project should document those transition surfaces honestly and point to the relevant upstream issues

## Design Principles

### 1. The reference project is a proof project, not a demo toy

It must be easy to study, but it still needs to prove current framework reality:

- bootstrap origin
- generator ownership
- handwritten ownership
- runtime behavior
- testing contract consumption
- HTTP callback path

### 2. Version one should be strict, not clever

Where the current Default Happy Path already gives a clear answer, version one should follow it directly instead of inventing a smoother but less faithful project structure.

### 3. Keep capability gaps explicit

If a current capability is missing from stable design-driven generation, version one may handwrite it, but only as an explicit and documented transition surface.

### 4. Keep the project independent from `only-engine`

Version one must compile, run, and demonstrate the main path without `only-engine`.

### 5. Distinguish behavior truth from persistence truth

Behavioral contracts should come from design input.
Persistence shape should come from schema input.
Neither side should silently invent the other.

## Issue Tree and Roadmap

`#27` should act as the total-goal issue for the full reference-project line rather than as one giant implementation ticket.

The intended issue tree is:

- `#27` total goal: `cap4k-reference-content-studio`
- `#27-v1`: strict runnable happy-path reference project
- `#27-v2`: lifecycle hardening and richer project proof
- `#27-openapi-contract-and-generator-example`: follow-up OpenAPI contract / generator-example line
- `#27-advanced-modeling`: future advanced-modeling follow-up such as `#23`

Version one is the current target. The other slices exist so that later work does not reopen first-principles design debates.

## Overall Boundaries For `#27-v1`

Version one must be written with these hard boundaries:

- separate repository/project named `cap4k-reference-content-studio`
- first implemented locally under `only-workspace`
- remote repository already exists at `git@github.com:LDmoxeii/cap4k-reference-content-studio.git`
- no dependency on `only-engine`
- no `enumTranslation`
- no `#23` advanced read/write modeling
- no authentication or authorization
- no frontend product UI
- no upload/download workflow
- no reject/re-edit/withdraw/offline lifecycle branches
- no hidden framework patching inside the project

Capability gaps that version one must treat as handwritten transition surfaces:

- integration-event design-driven families: `#34`
- domain-service design-driven family: `#35`
- first-class value-object generation contract: `#36`

## Architecture Shape, Modules, Runtime, and Interaction

Version one should be a restrained but real layered project, not a single-module teaching toy and not a production-scale module graph.

### Module Structure

Use exactly four modules:

1. `cap4k-reference-content-studio-domain`
2. `cap4k-reference-content-studio-application`
3. `cap4k-reference-content-studio-adapter`
4. `cap4k-reference-content-studio-start`

Do not split out dedicated query, integration, or persistence modules in version one.

### Package Root

Use:

- `com.only4.cap4k.reference.contentstudio`

### Runtime

Use:

- H2
- JPA
- local direct startup
- no external database
- no `only-engine`

### Dependency Consumption

The project should consume `cap4k` through `mavenLocal()` by default.

Composite-build or `includeBuild` local development can be documented later, but it must not be the primary project contract.

### Repository Ownership and Delivery Path

The relationship between `cap4k` and `cap4k-reference-content-studio` must be explicit.

`cap4k` owns:

- issue planning and sequencing for the reference-project line
- authoring-guide references that cite the project
- spec and plan documents that define how the project should behave

`cap4k-reference-content-studio` owns:

- runnable project code
- project README and operator instructions
- `.http` interaction files
- committed OpenAPI snapshot
- project-local snapshot sync task
- smoke tests and other project-local verification assets

The repository contract for version one is therefore:

- implementation begins locally under `only-workspace`
- the canonical project repository is `git@github.com:LDmoxeii/cap4k-reference-content-studio.git`
- version-one delivery is not complete until the runnable project is actually published to that separate repository
- `cap4k` should reference the project; it should not vendor the runnable project code back into the framework repository

### Startup Experience

After `cap4k` has been published to `mavenLocal()`, a clean clone of `cap4k-reference-content-studio` should be directly runnable.

Version one must not require a fresh manual `cap4kGenerate` run before first startup.

### Primary Manual Interaction Surface

The primary operation surface for version one should be committed `.http` files, not Swagger UI.

Use four topic files:

- `content.http`
- `review.http`
- `media-processing.http`
- `query.http`

The files should use explicit variables such as:

- `@baseUrl`
- `@contentId`
- `@externalTaskId`

State transfer between requests remains manual and obvious. Version one should not introduce scripting or auto-chaining magic.

### OpenAPI Contract

Version one still needs OpenAPI as a contract surface:

- runtime OpenAPI exposure via `springdoc-openapi`
- a committed static OpenAPI snapshot exported from runtime

The OpenAPI snapshot should be manually exported and committed in version one.

Swagger UI may still be available incidentally through runtime setup, but it is not the primary interaction surface or the main teaching path.

## Domain Model, Happy Path, and Orchestration

Version one should prove the shared reference domain already established by the public docs rather than inventing a new sample universe.

### Core Aggregates

Version one uses exactly two aggregate roots:

1. `Content`
2. `MediaProcessingTask`

The relationship is weak and one-way:

- `MediaProcessingTask` keeps `contentId`
- `Content` does not hold a task object reference
- `Content` does not mirror media-processing status

For version one, one `Content` corresponds to one `MediaProcessingTask`.

This is a version-one simplification, not a forever statement. Later versions may add task history or rerun support.

### `Content` State Model

Do not use one catch-all status enum.

Keep two distinct lines:

- `ReviewStatus`
- `ContentStatus`

`ReviewStatus` models review facts.
`ContentStatus` models content lifecycle facts.

Version one only needs the minimum status range required for the main happy path. It does not include reject/re-edit or post-publish withdraw/offline behavior.

### Minimal Draft Input

`CreateContentDraft` should stay minimal and sufficient:

- `title`
- `body`
- `mediaSourceKey`

This is enough to prove content lifecycle and media-processing handoff without dragging in category, tag, cover, or uploader workflows.

### Version-One Main Path

The manually demonstrated path is:

1. create draft
2. submit for review
3. approve review
4. callback returns media-processing success
5. query content detail
6. query current processing status

There is no manual publish HTTP endpoint in version one.

Publishing is an internal process result, not a user-triggered teaching shortcut.

### Review Approval Input

Review approval should be manual HTTP and should explicitly carry `reviewerId` in the request body.

Version one should not rely on authentication context for this.

### Approval Handoff

The approval seam must stay aligned with Default Happy Path:

- `ApproveContentCmdHandler` only modifies `Content`
- `Content.approve()` registers `ContentApprovedDomainEvent`
- a domain-event handler issues `StartMediaProcessingCmd`

Do not directly issue `StartMediaProcessingCmd` from inside `ApproveContentCmdHandler`.

### Media Processing Start

`StartMediaProcessingCmdHandler` should:

- read `Content`
- create or load `MediaProcessingTask`
- call `MediaProcessingCli`
- record `externalTaskId`
- move the task into the processing state

`Content` must not directly call the CLI and must not create the task internally.

### Callback Main Path

The callback path must use:

- `ddd-integration-event-http`

This is a real transport-level integration-event path, not a hand-written business controller pretending to be one.

Do not introduce MQ in version one.
Do not promote subscribe/unsubscribe/events management endpoints as part of the main teaching path.

The callback payload should remain minimal:

- `externalTaskId`
- `status`

### Media Processing Success Flow

After callback consumption:

1. integration event enters the system
2. integration-event handler issues `MarkMediaProcessingSucceededCmd`
3. `MarkMediaProcessingSucceededCmdHandler` only modifies `MediaProcessingTask`
4. `MediaProcessingTask` emits `MediaProcessingSucceededDomainEvent`
5. domain-event handler issues `PublishContentCmd`

### Publish Eligibility

`PublishContentCmdHandler` is allowed to read:

- `Content`
- `MediaProcessingTask`

It must then call a **pure Domain Service** that performs publish-eligibility judgment using already-loaded facts.

That Domain Service must:

- not call repositories
- not emit commands
- not wait for future callbacks or polling
- not write aggregate state

The final write remains:

- `Content.publish()`
- `contentRepository.save(content)`

`Content.publish()` must verify both:

- review already approved
- media processing already succeeded

### Query Side

Version one keeps:

- `Query`
- `QueryHandler`

But query implementation stays repository-backed. There is no separate read-model subsystem, read-store, or projection chain in version one.

The minimum query surface is:

- content detail
- current processing status

Controllers must not bypass query handlers and call repositories directly.

## Generator Inputs, Schema Inputs, and Ownership

Version one must prove both generator reality and maintainable project reality.

### Dual Input Model

Version one uses **design + schema dual input**.

`design` governs:

- commands
- queries
- CLI contracts
- domain events
- handler skeletons
- application seams and behavior contracts

`schema SQL` governs:

- persistence structure
- table and field reality
- repository and schema-side generation evidence

This relationship should be written explicitly as:

- design governs behavior
- schema governs persistence

Neither side should silently invent the other.

### Generator Coverage

Version one should run:

- `cap4kPlan`
- `cap4kGenerate`

But its core generator proof line is the aggregate mainline:

- entity
- behavior
- factory
- specification
- schema
- repository

It does not need to force flow, analysis, or every peripheral generator family into version one.

### Bootstrap Origin

Project skeleton origin should be bootstrap-based.

That does not mean every bootstrap artifact must become part of the teaching narrative, but the project should still prove that the runnable reference line can start from the current framework entrypoint.

### Generated Snapshot Directories

The project should keep committed generated evidence, but not by turning `build/generated` into a tracked runtime source tree.

Keep a strict distinction:

- generator real output path: normal compile-participating project paths
- `src-generated/main/kotlin`: committed generated snapshot directory created by the project itself

`src-generated/main/kotlin` is:

- not the generator's native output path
- not the runtime truth source
- not a new framework contract
- only snapshot evidence for review and audit

### Snapshot Sync Task

The project must define its own Gradle task to copy current generated outputs into committed snapshot directories.

This task belongs to the project contract, not to the framework contract.

The snapshot sync scope in version one should cover all currently generated artifacts actually used by the project.

Snapshot directories are allowed in:

- `domain`
- `application`
- `adapter`

Not in:

- `start`

### Handwritten and Generated Ownership

Version one should follow a restrained split:

Generated surfaces own:

- stable skeleton families
- repeatable artifact contracts
- structural mainline evidence

Handwritten surfaces own:

- domain behavior details
- application orchestration logic
- adapter entry wiring
- fake external boundary implementation
- project-local testing helpers
- explicit transition surfaces for current generator-family gaps

Because some generated families in practical use are intentionally SKIP-oriented, the runtime source of truth stays in normal source paths. Snapshot directories only preserve evidence.

### Current Explicit Handwritten Transition Surfaces

Version one should treat these as handwritten until upstream work lands:

- integration-event families: `#34`
- domain-service family: `#35`
- value-object generation contract: `#36`

## Testing Contract and Runtime Frame Slice

Version one should consume `#18` instead of redefining testing shape.

### Testing Contract

The official default testing boundary remains:

- domain behavior tests
- application seam/orchestration tests

Adapter, persistence, and integration tests may exist, but version one should not describe them as a first-version universal skeleton.

### Required Smoke Test

Version one must include one HTTP end-to-end smoke test that:

- starts from an empty business database
- uses real HTTP APIs
- hits the real public callback consume path
- covers the main happy path

### Polling Fallback

Polling exists in version one only as:

- code
- tests

It is not a parallel manual demo path.

### Starter Default Capabilities And Runtime Tables

The runtime callback path depends on current starter event infrastructure reality.

Current `cap4k-ddd-starter` does not expose a clean, user-facing fine-grained property switch for all default event, request, saga, and locker capabilities. The practical current mechanism for removing whole auto-configuration blocks is:

- `spring.autoconfigure.exclude=...`

However, version one should not try to reduce the HTTP integration-event callback path to only `__event_http_subscriber` by replacing framework internals inside the reference project.

The stable version-one approach is to accept a **minimal complete frame slice** for current starter + HTTP integration-event support.

That slice should be prepared in H2-compatible runtime initialization SQL and should include at least:

- `__event`
- `__archived_event`
- `__event_http_subscriber`
- `__locker`

The source reference is:

- `only-danmuku-zero/only-danmuku-start/src/main/resources/db.migration/V2025.0217.000000__frame_init.sql`

But version one must only extract the necessary pieces and rewrite them for H2 instead of copying the full MySQL frame migration wholesale.

These frame tables belong in **runtime init SQL**, not in business schema/generator input SQL.

## Delivery Materials, Completion Criteria, and Later Slices

### README And Manual Materials

The version-one README should lead with the shortest runnable path, not with a long positioning essay.

Recommended order:

1. what the project is
2. prerequisites, including `cap4k` published to `mavenLocal()`
3. shortest startup command
4. how to run the main path through `.http` files
5. where runtime OpenAPI and committed OpenAPI snapshot live
6. where generator input, schema input, generated snapshots, and testing contract fit

The main manual evidence set should include:

- `content.http`
- `review.http`
- `media-processing.http`
- `query.http`

### OpenAPI Materials

Version one must keep both:

- runtime OpenAPI exposure
- committed static OpenAPI snapshot

The static snapshot exists as long-lived machine-readable evidence for later contract review and possible follow-up generator examples. It is not a revival of `#14`.

### Version-One Completion Standard

`#27-v1` should not be considered complete until all of these are true:

1. `cap4k-reference-content-studio` runs independently as a separate project.
2. It does not depend on `only-engine`.
3. The main happy path can be executed from an empty business database through committed `.http` files.
4. The callback main path uses the real `ddd-integration-event-http` consume path.
5. Publish is internally progressed rather than exposed as a manual HTTP shortcut.
6. One HTTP end-to-end smoke test covers the main happy path.
7. Generated-versus-handwritten ownership is visible through committed snapshot evidence.
8. OpenAPI is visible at runtime and also committed as a static snapshot.
9. Polling exists only as fallback code-and-test proof.
10. Current framework capability gaps are called out honestly rather than implied to already be solved.

### Version-Two Boundary

`#27-v2` should stay out of version one, but its later direction can already be stated:

- richer lifecycle hardening
- clearer polling/project proof
- possible multi-task history or rerun support
- broader operational and audit hardening

`#27-v2` must not reopen version-one boundary decisions such as:

- no `only-engine` dependency
- no `enumTranslation` before `#33`
- no alternate happy path

### OpenAPI Contract / Generator Follow-Up

A future `#27` child slice may build on the committed OpenAPI contract for example or generator-related demonstrations.

That follow-up:

- must belong to the `#27` reference-project line
- must not be framed as reactivating `#14`
- must remain downstream of a stable version one contract

### Advanced Modeling Follow-Up

If later advanced modeling such as `#23` is brought into the reference project, it should happen as a dedicated `#27` follow-up slice, not inside version one.

### Relationship To `#33`

This dependency must be explicit and hard:

- version one must not enable `enumTranslation`
- this is not an implementation choice left to the project author
- this remains true until `#33` resolves the current implicit coupling problem

### Relationship To `#17`

`#17` should remain downstream of:

- `#27`
- `#33`

because the final AI-collaboration guidance needs stable project-level proof, not just framework prose.

When version one is complete, `#17` gains:

- a separate official reference repository
- a runnable happy path
- committed `.http` operation scripts
- OpenAPI contract evidence
- smoke-test evidence
- generated-versus-handwritten ownership evidence
- concrete consumption of the public testing contract

## Open Questions Deferred Out Of This Slice

This design intentionally does not settle:

- final `#27-v2` content in detail
- OpenAPI-generator example mechanics
- advanced read/write modeling enablement
- generator-family feature work covered by `#34`, `#35`, and `#36`

Those remain separate follow-up responsibilities by design.
