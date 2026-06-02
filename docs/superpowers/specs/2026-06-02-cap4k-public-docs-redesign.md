# cap4k Public Documentation Redesign

Date: 2026-06-02

Status: Detailed Design Checkpoint

Scope: Phase 2 detailed public-documentation design for issue #99. This file records the decisions approved so far for rebuilding `README.md` and `docs/public/`. It is intentionally self-contained so a future agent can continue after context compaction without relying on this conversation. It does not modify the coarse outline file `2026-06-01-cap4k-public-docs-outline.md`.

## Backlog Source

This design supports:

- #99: `docs: rebuild public README and user documentation from an outline`
- Shared design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- Coarse outline: `docs/superpowers/specs/2026-06-01-cap4k-public-docs-outline.md`
- Phase 1 analysis output: `docs/superpowers/analysis/`
- Runnable reference project: sibling workspace repo `cap4k-reference-content-studio/`

This is not a rewrite implementation plan. Continue brainstorming before writing the final implementation plan.

## Reader And Purpose

Public documentation is 100% human-friendly.

The default reader is a backend engineer who can understand services, persistence, HTTP, Gradle, and Spring-style projects, but is not assumed to have a systematic DDD background.

The public docs must help this reader understand:

- what cap4k is;
- why it exists;
- why generator-backed authoring is architecture control rather than ordinary scaffolding;
- how cap4k's tactical concepts fit together;
- how Clean Architecture layers are written in a cap4k project;
- how generation, handwritten implementation, testing, and analysis evidence fit into one workflow;
- how to use the runnable reference project as a concrete reading and execution anchor.

Public docs must not require issue history, internal specs, `docs/superpowers/analysis/`, or installed skills.

## Writing Stance

Public docs should read like a coherent article-grade system, not like an iteration log.

Rules:

- Do not describe capability as `before / now / future` unless a public compatibility note is unavoidable.
- Do not explain current behavior by mentioning old limitations.
- Write positive current rules: what the system is, how it is expressed, when to use it, and where its boundaries are.
- Avoid maintainer drift vocabulary such as `old docs said`, `stale wording`, `previously unsupported`, or `future extension` in public-facing prose.
- Boundary sections are allowed, but they should be framed as `适用边界`, `设计边界`, or `常见误用`, not as historical unsupported lists.

Example of the desired style:

- Good: `Value Object 通过 types.valueObjectManifest 表达，生成 JSON-backed 值对象和内嵌 converter。`
- Avoid: `以前 value object 不能生成，现在已经支持。`

## Source-Of-Truth Policy

Code remains the final source of truth.

Phase 1 analysis maps are useful fact indexes, but they are not infallible. During public design we found that the public example strategy must also account for the runnable sibling project `cap4k-reference-content-studio/`. If Phase 1 analysis, current cap4k source, and the reference project disagree, the rewrite must re-check source code and committed reference-project evidence before publishing public statements.

Public docs should not expose this maintenance policy as a user-facing story. It belongs in this design and later implementation/audit plans.

## Confirmed Public Facts

These facts were re-checked during this design checkpoint and should guide the rewrite.

### Generator And Gradle

- Gradle plugin id: `io.github.ldmoxeii.cap4k.pipeline`.
- Core public tasks include `cap4kBootstrapPlan`, `cap4kBootstrap`, `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, `cap4kAnalysisPlan`, and `cap4kAnalysisGenerate`.
- `cap4kPlan` writes `build/cap4k/plan.json`.
- `cap4kAnalysisPlan` writes `build/cap4k/analysis-plan.json`.
- compiler code-analysis snapshots use `build/cap4k-code-analysis` as the default output root.
- `sources.irAnalysis.inputDirs` is the current analysis input contract.
- Public docs must not use stale spaced paths such as `build/cap4k code analysis` or `build/cap4k/analysis plan.json`.
- Public docs must not describe `sources.irAnalysis.enabled`, `generators.flow.enabled`, or `generators.drawingBoard.enabled` as current public switches.

### Design JSON And Type Inputs

- Normal `design.json` tags currently include `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, and `saga`.
- `validator` is not a current normal `design.json` tag.
- Value Object generation is implemented through `types.valueObjectManifest`, not through a normal `design.json` entry with `tag = "value_object"`.
- Generated value-object source may contain `@BuildingBlock(tag = "value_object", family = "value-object")`; public docs must distinguish generated building-block metadata from normal `design.json` input tags.
- `types.enumManifest` and `types.valueObjectManifest` are first-class current type inputs.

### Saga

- `saga` is a supported design tag and design generator family.
- Runtime Saga supports persisted records, `schedule`, `resume`, `retry`, `sendProcess`, `sendCompensableProcess`, `requestCompensation`, compensation resume, archive, and scheduled recovery paths.
- `cap4k-reference-content-studio` contains a real `PaidPublicationSaga` example.
- Public docs may describe Saga as persisted cross-step coordination with retry/recovery/compensation.
- Public docs should not describe ordinary media-processing callback handling as a callback-resume Saga. Callback-to-command remains an integration/application flow unless the model explicitly needs Saga coordination.

## Reference Project Strategy

Use `cap4k-reference-content-studio/` as the single primary public example.

Confirmed reference-project facts:

- It is a runnable cap4k reference application in a sibling workspace repository.
- It has four modules: `cap4k-reference-content-studio-domain`, `cap4k-reference-content-studio-application`, `cap4k-reference-content-studio-adapter`, and `cap4k-reference-content-studio-start`.
- It contains a runnable content publishing flow using `.http` files.
- It contains `design/design.json`, `design/value-objects.json`, `design/enums.json`, schema, `build/cap4k/plan.json`, `build/cap4k/analysis-plan.json`, and committed `analysis/flows` / `analysis/drawing-board` evidence.
- It shows `MediaProcessingResultSnapshot` generated from `types.valueObjectManifest`.
- It shows `PaidPublicationSaga` as a real Saga example.
- It already has a useful local modeling guide at `cap4k-reference-content-studio/docs/modeling.md`.

Public docs should be self-contained explanations. The reference project should provide runnable anchors and code evidence.

Do not copy the whole reference project into cap4k public docs. Instead:

- explain the concept or workflow in `docs/public/`;
- point to the reference project for full code, `.http` execution, `plan.json`, analysis outputs, and tests;
- quote or reproduce only small, necessary snippets;
- keep cap4k public docs and the reference project from becoming two divergent example systems.

## Main Reader Journey

The approved main order is:

```text
README.md
 -> docs/public/index.md
 -> concepts before mechanics
 -> Clean Architecture layers
 -> authoring workflow
 -> generator narrative
 -> runnable reference project guide
 -> reference lookup pages
```

Rationale:

- The reader must first understand cap4k's mental model.
- Generator mechanics make more sense after tactical concepts and layers are clear.
- Reference pages should support lookup and configuration, not carry the article narrative.

## README Role

`README.md` should be a strong entry point, not a full manual.

It should contain:

- concise project positioning;
- the problem cap4k solves;
- core highlights;
- who should use cap4k;
- when cap4k is not a good fit;
- shortest path to the runnable reference project;
- public documentation map;
- current public Gradle/plugin entry hints without turning into DSL reference.

It should not duplicate the full public manual.

## Public Documentation Pillars

The public system needs two parallel main knowledge pillars.

### Pillar 1: Tactical Concepts

This pillar explains the cap4k building blocks.

Candidate concepts include:

- Aggregate
- Entity
- Value Object
- Strong ID
- Command
- Query
- Domain Event
- Integration Event
- Repository
- Factory
- Domain Service
- Saga
- Job
- External Client / Open Host Service style boundary
- Unit of Work
- Mediator
- generated skeleton vs handwritten logic

Every concept page should answer the same reader questions:

- What problem should make the author think of this concept?
- When should the concept be used?
- How is it expressed in cap4k modeling or generator inputs?
- What skeletons can cap4k generate?
- Where does handwritten business logic live?
- Which layer owns it?
- Where is it visible in `cap4k-reference-content-studio`?
- What are the common misuse boundaries?
- How should a reviewer audit whether the concept is used correctly?

There should be no `advanced/` hierarchy that implies Value Object, Saga, or Domain Service are second-class or risky by default. These are normal concepts. A simple slice may not need all of them; a richer model should use them when the problem calls for them.

### Pillar 2: Clean Architecture Layers

This pillar explains how a cap4k project is structured and written.

The layer pages should be as important as concept pages. A user does not know cap4k just because they know the individual DDD terms; they must also know where the code belongs.

Layer pages should cover:

- `domain`: aggregate behavior, entities, value objects, factories, domain services, domain events, domain tests.
- `application`: commands, queries, subscribers, Saga, jobs, external capability ports, UoW save boundaries, orchestration.
- `adapter`: HTTP controllers, payloads, query adapters, external client adapters, integration listeners, persistence adapters, protocol conversion.
- `start`: Spring Boot runtime assembly, local app startup, runtime config, smoke path, not a business truth layer.

Every layer page should answer:

- What belongs in this layer?
- What does not belong here?
- Which generated skeletons land here?
- What handwritten logic should be added here?
- How does this layer call or depend on other layers?
- Which reference project directories demonstrate the rule?
- What should reviewers check?

## Generator Documentation Role

Generator docs are a major public chapter, but they should come after concepts and layers.

The generator narrative should explain:

- generator-backed authoring is a way to control architecture drift;
- the generator produces skeletons, contracts, paths, and reviewable plans;
- complex business behavior is implemented in the generated skeleton structure;
- bypassing generator-owned skeletons is a design decision, not an implementation shortcut;
- `cap4kPlan` is the review step before writing files;
- `plan.json` is an ownership and review artifact;
- `cap4kGenerate` materializes planned source artifacts;
- `cap4kGenerateSources` handles build-owned generated Kotlin source;
- `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` produce observation/evidence outputs, not business source skeletons.

Generator docs should not begin as a DSL table. The DSL details belong in reference.

## Reference Documentation Role

Reference pages must be detailed enough for actual configuration and review.

Target reference pages include:

- `reference/gradle-plugin.md`: plugin id, extension overview, task table, task outputs.
- `reference/generator-dsl.md`: `cap4k { project/types/sources/generators/templates/bootstrap/layout/addons }` blocks, fields, and minimal snippets.
- `reference/design-json.md`: supported normal design tags, fields, result fields, event names, `artifacts` family/variant, and tag-specific constraints.
- `reference/value-object-manifest.md`: `types.valueObjectManifest` schema, shared vs aggregate-owned shape through `aggregates`, JSON-backed example, generated output notes.
- `reference/enum-manifest.md`: `types.enumManifest` schema and enum generation notes.
- `reference/plan-json.md`: how to read `generatorId`, `templateId`, `outputKind`, `resolvedOutputRoot`, `conflictPolicy`, and paths.
- `reference/outputs.md`: `CHECKED_IN_SOURCE`, `GENERATED_SOURCE`, `OUTPUT_ARTIFACT`, `build/generated/cap4k/main/kotlin`, analysis outputs, `flows`, `drawing-board`.
- `reference/common-mistakes.md`: current-system misuse boundaries, written without historical drift language.

Reference pages should avoid article-like repetition. They are lookup surfaces.

## Boundary Writing Policy

Public docs should include boundaries, but not as historical unsupported lists.

Preferred forms:

- `适用边界`
- `设计边界`
- `常见误用`
- `审核要点`

Examples:

- Value Object belongs to `types.valueObjectManifest` when it is generated as a JSON-backed value object. Do not teach it as a normal `design.json` tag.
- Saga is for persisted cross-step coordination, retry, recovery, and compensation. Do not wrap every callback-to-command flow as a Saga.
- Integration Event is an external fact / cross-service contract boundary. It is not a Domain Event renamed for transport.
- Generated source under `build/generated/cap4k/main/kotlin` is build-owned. Do not put handwritten business logic there.
- Checked-in generated skeletons become implementation surfaces under conflict-policy protection; handwritten logic should live inside the intended skeleton unless design chooses otherwise.

## Proposed Directory Shape

The exact final tree is still open for refinement, but decisions so far support this shape:

```text
README.md
docs/public/
  index.md
  concepts/
  architecture/
  authoring/
  generator/
  examples/
  reference/
```

Current confirmed responsibility split:

- `concepts/`: tactical concepts and modeling building blocks.
- `architecture/`: Clean Architecture layers and code placement.
- `authoring/`: end-to-end human workflow from business problem to generated and handwritten code.
- `generator/`: narrative generator guide, plan/generate/analysis workflow, ownership model.
- `examples/`: guide to `cap4k-reference-content-studio`, not a separate fictional example universe.
- `reference/`: DSL, schema, task, output, plan, and mistake lookup.

The existing `docs/public/authoring/advanced/` should not survive as an `advanced` hierarchy. Its usable concepts should be rewritten into first-class concept pages.

## Context7 Compatibility

Public docs should be Context7-ready, but Context7 submission is out of scope for this phase.

Implications:

- stable titles;
- one primary topic per page;
- complete code blocks with language markers;
- short reference pages for lookup-heavy topics;
- no dependence on internal analysis/spec/plan material;
- README plus `docs/public/` should be indexable without including `docs/superpowers/analysis/` or `skills/`.

## Image Prompt Placeholder Policy

Public docs may include future image-generation prompt placeholders. They are optional human-maintainer aids, not required content.

Use only where a future diagram would improve human understanding:

- cap4k mental model;
- Clean Architecture layers;
- generator-backed authoring workflow;
- generated skeleton vs handwritten logic;
- reference project flow from HTTP request through command/event/subscriber;
- `cap4kPlan` / `cap4kGenerate` / `cap4kAnalysisGenerate` relationship.

Required placeholder format:

```markdown
<!-- IMAGE_PROMPT:
Purpose: <why this image helps this page>
Type: <architecture diagram | workflow diagram | concept map>
Prompt: <generation prompt>
Must show: <required concepts>
Must avoid: <misleading imagery or false contracts>
Alt text after insertion: <future alt text>
-->
```

The prose must remain complete without the image.

## Migration And Deletion Direction

This is an aggressive rewrite. Existing public page boundaries are not preservation constraints.

Likely actions:

- replace the current weak README with a strong entry README;
- create `docs/public/index.md` as the public reading front door;
- split concept material out of old `authoring/` pages into `concepts/`;
- split layer material into `architecture/`;
- remove `advanced/` as a hierarchy;
- rewrite examples as reference-project guides;
- rewrite generator pages into narrative guide plus detailed reference pages;
- delete stale pages rather than preserving an archive directory.

Do not modify the coarse public outline during this design checkpoint. It can be deleted later only after the detailed design and implementation plan are approved.

## Open Decisions To Continue Brainstorming

These items are intentionally not final yet:

- exact page list and filenames under `concepts/`;
- exact page list and filenames under `architecture/`;
- whether `authoring/` should be one end-to-end workflow page or several workflow pages;
- exact generator chapter split;
- exact `examples/` shape and how many cross-repo links are acceptable;
- exact reference page schema depth and sample count;
- image prompt placeholder placement;
- final deletion map from current public files to new files;
- final audit checklist and implementation plan.

## Acceptance Criteria For Final Detailed Design

Before moving to implementation planning, the final public detailed design should define:

- exact `README.md` outline;
- exact `docs/public/` directory tree;
- exact concept page list;
- exact Clean Architecture layer page list;
- exact authoring workflow page list;
- exact generator narrative page list;
- exact reference page list;
- reference project usage rules;
- image prompt placeholder placement rules;
- deletion/migration map for old public files;
- code/reference-project verification checklist;
- Context7-ready checklist;
- handoff notes for a new-context implementation agent.

## Handoff For Future Agents

If context is compacted, restart Phase 2 from this file plus these inputs:

1. `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
2. `docs/superpowers/specs/2026-06-01-cap4k-public-docs-outline.md`
3. `docs/superpowers/analysis/README.md`
4. `docs/superpowers/analysis/pipeline-and-gradle-map.md`
5. `docs/superpowers/analysis/source-and-generator-contract-map.md`
6. `docs/superpowers/analysis/artifact-output-and-ownership-map.md`
7. `docs/superpowers/analysis/runtime-and-integration-map.md`
8. `docs/superpowers/analysis/analysis-flow-and-verification-map.md`
9. sibling repo `cap4k-reference-content-studio/README.md`
10. sibling repo `cap4k-reference-content-studio/docs/modeling.md`
11. sibling repo `cap4k-reference-content-studio/design/design.json`
12. sibling repo `cap4k-reference-content-studio/design/value-objects.json`
13. sibling repo `cap4k-reference-content-studio/build.gradle.kts`

Do not continue from conversation memory alone. Treat current code and the runnable reference project as required evidence.