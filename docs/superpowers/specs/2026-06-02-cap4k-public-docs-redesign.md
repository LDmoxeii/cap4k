# cap4k Public Documentation Redesign

Date: 2026-06-02

Status: Final Detailed Design

Scope: Phase 2 public-documentation redesign for issue #99. This spec defines the exact public documentation surface for `README.md` and `docs/public/**`, including page lists, chapter boundaries, example strategy, generator/reference placement, image-prompt policy, and deletion/migration rules for the current public tree.

This spec replaces the earlier detailed-design checkpoint content in this file and records the final Phase 2 public-doc design decisions.

## Backlog Source

This design supports:

- GitHub issue #99: `docs: rebuild public README and user documentation from an outline`
- Shared system design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- Coarse public outline: `docs/superpowers/specs/2026-06-01-cap4k-public-docs-outline.md`
- Phase 1 analysis maps: `docs/superpowers/analysis/**`
- Runnable reference project: sibling workspace repository `cap4k-reference-content-studio/`

The final public docs must be self-contained for readers. They must not require issue history, internal specs, Phase 1 analysis maps, or installed agent skills.

## Design Authority

Code remains the final source of truth.

Phase 1 analysis maps are fact indexes, not infallible contracts. The implementation pass must re-check current cap4k source and the reference project before publishing factual claims about Gradle tasks, generator DSL, supported design tags, type manifests, runtime SQL, analysis outputs, or reference-project examples.

The public docs should not expose this maintenance policy as a user-facing story. It belongs in this design and later implementation/audit plans.

## Reader And Purpose

The default reader is a backend engineer who understands services, persistence, HTTP, Gradle, and Spring-style projects, but is not assumed to have systematic DDD background.

The public docs must help this reader understand:

- what cap4k is;
- why it exists;
- how DDD tactical concepts are expressed in cap4k;
- how Clean Architecture layers are written in a cap4k project;
- why command/query separation is used instead of a single service bucket;
- why generator-backed authoring is architecture control rather than ordinary scaffolding;
- how generation, handwritten implementation, testing, and analysis evidence fit into one workflow;
- how to use `cap4k-reference-content-studio` as the concrete reading and execution anchor;
- where to look up exact DSL, JSON, plan, output, and runtime database contracts.

## Public Writing Stance

Public docs should read like a coherent article-grade system, not like an iteration log.

Rules:

- Do not describe capability as `before / now / future` unless a public compatibility note is unavoidable.
- Do not explain current behavior by mentioning old limitations.
- Write positive current rules: what the system is, how it is expressed, when to use it, and where its boundaries are.
- Avoid maintainer drift vocabulary such as `old docs said`, `stale wording`, `previously unsupported`, or `future extension` in public-facing prose.
- Boundary sections are allowed, but they should be framed as `适用边界`, `设计边界`, or `常见误用`, not as historical unsupported lists.
- Public prose should be Chinese by default, while code identifiers, file paths, Gradle task names, JSON keys, annotations, and package names should keep their real spelling.
- Concept pages should be article-like, with a fixed tail for quick orientation. They should cover definition, trigger scenario, layer/collaboration relationships, cap4k expression, generated/handwritten boundary, reference-project anchor, design boundary, common misuse, and review points. They should not become mechanical Q&A pages.

Example of the desired style:

- Good: `Value Object 通过 types.valueObjectManifest 表达，生成 JSON-backed 值对象和内嵌 converter。`
- Avoid: `以前 value object 不能生成，现在已经支持。`

## Confirmed Public Facts

These facts were checked during this design pass and must be re-checked during implementation before final publication.

### Generator And Gradle

- Gradle plugin id: `io.github.ldmoxeii.cap4k.pipeline`.
- Core public tasks include `cap4kBootstrapPlan`, `cap4kBootstrap`, `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, `cap4kAnalysisPlan`, and `cap4kAnalysisGenerate`.
- `cap4kPlan` writes `build/cap4k/plan.json`.
- `cap4kAnalysisPlan` writes `build/cap4k/analysis-plan.json`.
- Compiler code-analysis snapshots use `build/cap4k-code-analysis` as the default output root.
- `sources.irAnalysis.inputDirs` is the current analysis input contract.
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
- Public docs may describe Saga as persisted cross-step coordination with retry, recovery, and compensation.
- Public docs should not describe ordinary media-processing callback handling as callback-resume Saga. Callback-to-command remains an integration/application flow unless the model explicitly needs Saga coordination.

### Runtime SQL Source Anchors

`reference/runtime-database-schema.md` must be based on these source anchors:

- `ddd-domain-event-jpa/src/main/resources/event.sql`
- `ddd-application-request-jpa/src/main/resources/request.sql`
- `ddd-distributed-saga-jpa/src/main/resources/saga.sql`
- `ddd-distributed-locker-jdbc/src/main/resources/locker.sql`
- `ddd-distributed-snowflake/src/main/resources/worker_id.sql`
- `ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql`

## Reference Project Strategy

Use sibling repository `cap4k-reference-content-studio/` as the single primary public example.

Confirmed reference-project facts:

- It is a runnable cap4k reference application.
- It has four modules: `cap4k-reference-content-studio-domain`, `cap4k-reference-content-studio-application`, `cap4k-reference-content-studio-adapter`, and `cap4k-reference-content-studio-start`.
- It contains a runnable content publishing flow using `.http` files.
- It contains `design/design.json`, `design/value-objects.json`, `design/enums.json`, schema, `build/cap4k/plan.json`, `build/cap4k/analysis-plan.json`, and committed `analysis/flows` / `analysis/drawing-board` evidence.
- It shows `MediaProcessingResultSnapshot` generated from `types.valueObjectManifest`.
- It shows `PaidPublicationSaga` as a real Saga example.
- It has a local modeling guide at `cap4k-reference-content-studio/docs/modeling.md`.

Public docs should be self-contained explanations. The reference project should provide runnable anchors and code evidence.

Do not copy the whole reference project into cap4k public docs. Instead:

- explain the concept or workflow in `docs/public/`;
- point to the reference project for full code, `.http` execution, `plan.json`, analysis outputs, and tests;
- quote or reproduce only small, necessary snippets;
- keep cap4k public docs and the reference project from becoming two divergent example systems.

## Main Reader Journey

The approved main reading journey is:

```text
README.md
  -> docs/public/index.md
  -> DDD tactical concepts
  -> Clean Architecture layers
  -> cap4k-reference-content-studio as concrete anchor
  -> authoring workflow
  -> generator-backed mechanics
  -> reference lookup pages
```

This intentionally places the reference project after concepts and architecture, before authoring and generator mechanics. The reader should first understand the mental model, then see it in a concrete project, then learn how to author and generate similar work.

## Final Top-Level Public Tree

The final public documentation surface is:

```text
README.md
docs/public/
  index.md
  concepts/
  architecture/
  examples/
  authoring/
  generator/
  reference/
```

Forbidden final-tree shapes:

- `docs/public/authoring/advanced/`
- `docs/public/authoring/generator/`
- `docs/public/authoring/examples/`
- standalone `docs/public/getting-started.md`
- archive folders inside `docs/public/`
- path-by-path preservation of the current public tree

## README.md

`README.md` is the repository front door. It should be short, high-signal, and should send readers into the public docs rather than duplicating them.

Required outline:

```text
# cap4k
一句话定位
IMAGE_PROMPT: cap4k mental model
## 它解决什么问题
## 核心亮点
## 两条最短路径
## 文档地图
## 什么时候适合
## 什么时候不适合
## 当前 Gradle 入口
## License / links
```

Required two shortest paths:

- Learning cap4k: start from the mental model and run/read `cap4k-reference-content-studio`.
- Creating a new project: use `cap4kBootstrapPlan` / `cap4kBootstrap`, or equivalent module/layout configuration when bootstrap is not the chosen entry.

The README should not become an API reference. It should name the plugin id and key tasks, then link to the exact reference pages.

## docs/public/index.md

`docs/public/index.md` is the public documentation landing page. It should make the reader choose an intent quickly and then follow a stable path.

Required outline:

```text
# cap4k Public Documentation
## 先选你的目标
## 推荐阅读路径
  - 第一次学习
  - 创建新项目
  - 编写业务功能
  - 查精确字段
## 文档章节
## 参考项目
## 不需要先读什么
```

It must include a reader-journey `IMAGE_PROMPT`.

The `不需要先读什么` section should explicitly lower cognitive cost: readers do not need internal specs, Phase 1 maps, issue history, or agent skills to understand the public docs.

## concepts/

The `concepts/` chapter teaches cap4k tactical building blocks and execution ownership. It is the first deep chapter after the landing page because the target reader may not already share the DDD vocabulary.

Final tree:

```text
docs/public/concepts/
  index.md
  modeling-building-blocks/
    aggregate.md
    entity.md
    value-object.md
    strong-id.md
    factory.md
    domain-service.md
    domain-event.md
    integration-event.md
    saga.md
  execution-and-ownership/
    command-query-separation.md
    command.md
    query.md
    subscriber.md
    scheduled-reaction.md
    repository.md
    unit-of-work.md
    mediator.md
    external-capability-anti-corruption-layer.md
    generated-skeleton-and-handwritten-logic.md
```

### Concept Index

`concepts/index.md` should introduce the two groups:

- `modeling-building-blocks/`: what the business model is made from.
- `execution-and-ownership/`: how requests, reads, reactions, persistence, generated code, and handwritten logic are owned.

It should include a short suggested order. It should not repeat every page in detail.

### Modeling Building Blocks

`aggregate.md` explains aggregate boundaries, invariants, transaction consistency, repository ownership, and how commands should enter aggregate behavior.

`entity.md` explains identity inside an aggregate, lifecycle ownership, and when not to promote every object to an aggregate root.

`value-object.md` explains immutable descriptive values, equality by value, Strong ID as an identity-reference value type, Business Enum as part of the value/type family, composite / JSON-backed value objects, generated value-object source, and the relationship to `types.valueObjectManifest`.

`strong-id.md` explains typed identifiers, why they reduce cross-aggregate confusion, and how they relate to generated value-object/source metadata.

`factory.md` explains creation logic that protects invariants when constructors would become noisy or misleading.

`domain-service.md` explains domain behavior that does not naturally belong to one entity or value object. It may mention Specification as a pattern used near aggregate/domain-service decisions, but Specification is not a standalone public concept page.

`domain-event.md` explains facts that happened inside the domain model, when to emit them, and how downstream subscribers should treat them.

`integration-event.md` explains externally visible facts, published language, and service-boundary communication. It should contrast with `domain-event.md`.

`saga.md` explains persisted cross-step coordination with retry, recovery, and compensation. Use `PaidPublicationSaga` as the concrete example anchor.

### Execution And Ownership

`command-query-separation.md` is required. It should explain why cap4k separates commands and queries instead of putting all application behavior into one traditional service class. It should help three-layer-architecture readers understand that command paths protect business decisions and write consistency, while query paths optimize reading and projection without pretending to be domain behavior.

`command.md` explains intent-bearing write requests, generated command skeletons, handler ownership, validation boundaries, aggregate access, and Unit of Work.

`query.md` explains read requests, read-model or projection-oriented code, why queries should not mutate business state, and how this differs from command handlers.

`subscriber.md` explains event reaction ownership, domain-event/integration-event subscribers, idempotency expectations, and when a reaction should delegate to command/application behavior.

`scheduled-reaction.md` explains jobs/timers/scheduled recovery as implementation surfaces for time-based behavior. `Job` is not a standalone concept page.

`repository.md` explains aggregate persistence access, repository intent, and why repository APIs should not become arbitrary query services.

`unit-of-work.md` explains transaction/persistence boundary and the relationship between command execution and commit.

`mediator.md` explains command/query/event dispatch as routing, not business decision-making.

`external-capability-anti-corruption-layer.md` explains external clients and adapters as anti-corruption boundaries. `Client` should be treated here as a generated expression of external capability access, not as a separate concept page.

`generated-skeleton-and-handwritten-logic.md` explains generated ownership markers, protected handwritten regions, and why generation creates architectural slots rather than business decisions.

### Concept Exclusions

These are deliberate non-pages:

- Specification: mention in aggregate/domain-service/reference/common-mistakes where relevant.
- Business Enum: treat as part of the value/type family; exact schema belongs in `reference/enum-manifest.md`.
- API Payload: adapter-layer topic plus exact input contract in `reference/design-json.md`.
- Handler: implementation surface inside command/query/subscriber pages.
- Job: implementation surface inside `scheduled-reaction.md`.
- Client: generator expression inside `external-capability-anti-corruption-layer.md`.

Concept pages should use image prompts selectively. There is no hard one-image-per-page requirement, and there is no hard one-image-per-page maximum.

Priority concept pages for selective `IMAGE_PROMPT` placement:

- `concepts/index.md`
- `modeling-building-blocks/aggregate.md`
- `modeling-building-blocks/value-object.md`
- `modeling-building-blocks/integration-event.md`
- `modeling-building-blocks/saga.md`
- `execution-and-ownership/subscriber.md`
- `execution-and-ownership/scheduled-reaction.md`
- `execution-and-ownership/external-capability-anti-corruption-layer.md`
- `execution-and-ownership/generated-skeleton-and-handwritten-logic.md`

## architecture/

The `architecture/` chapter explains the Clean Architecture shape of a cap4k project. It should be more rule-oriented than concept pages, but still public prose.

Final tree:

```text
docs/public/architecture/
  index.md
  clean-architecture.md
  domain-layer.md
  application-layer.md
  adapter-layer.md
  start-layer.md
  dependency-rules.md
  testing-by-layer.md
```

`architecture/index.md` introduces the layer model and links concepts to layer responsibilities.

`clean-architecture.md` gives the whole system shape and must include an `IMAGE_PROMPT`.

`domain-layer.md` explains aggregates, entities, value objects, domain services, domain events, factories, and domain-only rules.

`application-layer.md` explains command/query handlers, subscribers, orchestration, Unit of Work, Saga coordination, and Mediator usage.

`adapter-layer.md` explains HTTP adapters, API payloads, persistence adapters where relevant, external capability clients, anti-corruption mapping, and integration boundaries.

`start-layer.md` is an independent page. It may be short, but it must explain bootstrapping, dependency assembly, runtime configuration, and application entry responsibilities.

`dependency-rules.md` defines allowed dependencies and forbidden dependency directions. It must include an `IMAGE_PROMPT`.

`testing-by-layer.md` explains what to test at each layer and how generated skeletons affect test focus. Its `IMAGE_PROMPT` is optional.

Each layer page must cover:

- what the layer is responsible for;
- what the layer is not responsible for;
- where generated skeletons land;
- where handwritten logic belongs;
- dependency direction;
- reference-project directory anchors;
- review points.

## examples/

The `examples/` chapter uses only `cap4k-reference-content-studio` as the example universe. It formally appears after architecture in the main reader journey.

Final tree:

```text
docs/public/examples/
  index.md
  reference-content-studio.md
  run-the-reference-project.md
  default-publication-flow.md
  paid-publication-saga-flow.md
  value-object-and-type-inputs.md
  generation-and-analysis-evidence.md
```

`examples/index.md` explains how to use examples: read the public page for the idea, then inspect or run the sibling repository for full code.

`reference-content-studio.md` introduces the reference project, its four modules, its business scenario, and where to find its design files, generated plan, tests, `.http` files, and analysis evidence. It should include a project-structure `IMAGE_PROMPT`.

`run-the-reference-project.md` explains the read/run path at a high level and points to the sibling repository for executable details. It must not invent commands that are not verified in the project.

`default-publication-flow.md` explains the normal content publication path. It should include a workflow `IMAGE_PROMPT`.

`paid-publication-saga-flow.md` explains `PaidPublicationSaga` as the Saga example. It should include a workflow `IMAGE_PROMPT`.

`value-object-and-type-inputs.md` explains `MediaProcessingResultSnapshot`, `types.valueObjectManifest`, `types.enumManifest`, and how generated type input evidence connects to concept/reference pages.

`generation-and-analysis-evidence.md` explains committed `plan.json`, `analysis-plan.json`, `analysis/flows`, and `analysis/drawing-board` evidence. Its image prompt is optional.

## authoring/

The `authoring/` chapter explains how a human designs and evolves a cap4k business slice. It is a spiral, evidence-correcting process, not a linear checklist.

Final tree:

```text
docs/public/authoring/
  index.md
  spiral-authoring-loop.md
  business-intent-and-modeling.md
  technical-design.md
  generator-input-projection.md
  plan-review-and-generation.md
  implementation-inside-generated-skeletons.md
  verification-and-feedback.md
```

`authoring/index.md` introduces the authoring loop and links to the reference project as a running example.

`spiral-authoring-loop.md` is the core page. It must include an `IMAGE_PROMPT`. It should show that the workflow repeatedly moves through intent, model, technical design, generator inputs, plan review, generation, handwritten implementation, verification, and feedback.

`business-intent-and-modeling.md` explains how to capture business intent, aggregates, commands, queries, events, external capabilities, and policies before writing generator inputs.

`technical-design.md` explains how the business model becomes layer-aware design: command/query boundaries, events, Saga decisions, adapter boundaries, persistence expectations, and testing expectations.

`generator-input-projection.md` explains how design decisions are projected into `design/design.json`, `design/value-objects.json`, `design/enums.json`, module layout, and Gradle extension configuration.

`plan-review-and-generation.md` explains `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, ownership review, and when to stop before generation.

`implementation-inside-generated-skeletons.md` explains how handwritten logic belongs inside generated architectural slots, how to avoid fighting generator ownership, and how to use generated skeletons as contracts.

`verification-and-feedback.md` explains code review, focused tests, generation evidence, analysis evidence, and feeding findings back into the next authoring spiral.

## generator/

The `generator/` chapter comes after authoring. It explains mechanics that support the authoring loop.

Final tree:

```text
docs/public/generator/
  index.md
  generator-backed-authoring.md
  bootstrap-project-structure.md
  inputs-and-sources.md
  planning-and-ownership-review.md
  generation-tasks.md
  analysis-evidence.md
```

`generator/index.md` introduces generator-backed authoring and links to exact reference pages.

`generator-backed-authoring.md` explains that the generator turns explicit architecture/design inputs into stable code slots and evidence. It must include an `IMAGE_PROMPT`. It must avoid describing the generator as a generic CRUD scaffold or automatic business-decision writer.

`bootstrap-project-structure.md` explains bootstrap as the default new-project structure entry. It is not the default learning entry and does not replace modeling, schema, design JSON, value-object manifests, or enum manifests. It should include an optional structure `IMAGE_PROMPT`.

`inputs-and-sources.md` explains the generator input families: Gradle extension, design JSON, value-object manifest, enum manifest, source analysis inputs, and reference project examples.

`planning-and-ownership-review.md` explains `cap4kPlan`, `bootstrap-plan.json`, `plan.json`, generated-vs-handwritten ownership, managed sections, `conflictPolicy`, and review before generation. It should include an `IMAGE_PROMPT`.

`generation-tasks.md` explains the public Gradle task sequence and what each task is for.

`analysis-evidence.md` explains analysis plan/generation output, flow evidence, drawing-board evidence, and how to use evidence during verification. It should include an `IMAGE_PROMPT`.

## reference/

The `reference/` chapter is lookup-focused. It should provide exact contracts, minimal examples, and common mistakes. It should not become tutorial prose.

Final tree:

```text
docs/public/reference/
  index.md
  gradle-plugin.md
  generator-dsl.md
  design-json.md
  value-object-manifest.md
  enum-manifest.md
  plan-json.md
  outputs.md
  analysis-outputs.md
  runtime-database-schema.md
  common-mistakes.md
```

`reference/index.md` explains how to use the reference chapter and routes readers by lookup need.

`gradle-plugin.md` documents plugin id, public task names, task outputs, and minimal Gradle entry examples.

`generator-dsl.md` documents the Gradle extension shape, including bootstrap-related DSL and source/generator configuration that is verified as current.

`design-json.md` documents supported normal `design.json` tags, expected fields, minimal examples, and tag-specific boundaries.

`value-object-manifest.md` documents `types.valueObjectManifest`, value-object source generation, JSON-backed value objects, converters, and relationship to generated `@BuildingBlock` metadata.

`enum-manifest.md` documents `types.enumManifest` and enum generation.

`plan-json.md` documents `build/cap4k/plan.json`, ownership review, managed sections, and bootstrap plan output where relevant.

`outputs.md` documents generated source/output locations and ownership boundaries.

`analysis-outputs.md` documents `build/cap4k/analysis-plan.json`, `build/cap4k-code-analysis`, flow output, drawing-board output, and source-analysis input directories.

`runtime-database-schema.md` documents runtime database schema files and their purpose. It should include the SQL source anchors listed in this spec. There is no separate `runtime-configuration.md` page in Phase 2.

`common-mistakes.md` documents recurring mistakes across modeling, design JSON, type manifests, generation ownership, bootstrap, runtime schema, command/query separation, Saga, and stale terminology.

There is no `reference/bootstrap.md`. Bootstrap contracts belong in `gradle-plugin.md`, `generator-dsl.md`, `plan-json.md`, and `common-mistakes.md`.

## Image Prompt Policy

Images are optional aids. Prose must remain complete without them.

Every image prompt must use this exact block format:

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

Global `Must avoid` themes:

- Do not imply cap4k writes business decisions automatically.
- Do not imply every callback is Saga.
- Do not imply the generator is a generic CRUD scaffold.
- Do not imply frontend generation is a current core capability.
- Do not show arrows that violate Clean Architecture dependency rules.

Required image prompts:

- `README.md`: cap4k mental model.
- `docs/public/index.md`: reader journey.
- `docs/public/architecture/clean-architecture.md`: Clean Architecture overview.
- `docs/public/architecture/dependency-rules.md`: dependency rules.
- `docs/public/examples/reference-content-studio.md`: reference project structure.
- `docs/public/examples/default-publication-flow.md`: default publication flow.
- `docs/public/examples/paid-publication-saga-flow.md`: paid publication Saga flow.
- `docs/public/authoring/spiral-authoring-loop.md`: spiral authoring loop.
- `docs/public/generator/generator-backed-authoring.md`: generator-backed authoring.
- `docs/public/generator/planning-and-ownership-review.md`: plan ownership review.
- `docs/public/generator/analysis-evidence.md`: analysis evidence.

Optional image prompts:

- selected concept pages where a concept map will reduce confusion;
- `docs/public/architecture/testing-by-layer.md`;
- `docs/public/examples/generation-and-analysis-evidence.md`;
- `docs/public/generator/bootstrap-project-structure.md`.

## Rewrite And Migration Policy

Rewrite policy:

```text
README.md -> replace entirely
docs/public/** -> delete existing tree as old documentation surface -> recreate from approved tree
```

Current public prose is allowed only as a stale-term audit source or a reminder of topics to cover. Do not copy old prose. Do not preserve old directory boundaries.

### Current Public Tree Disposal Map

This map is a disposal and topic-projection map, not a path-preservation plan.

| Current surface | Final action |
| --- | --- |
| `docs/public/reference/generator-dsl.md` | Delete and recreate as `docs/public/reference/generator-dsl.md` with current fact audit. |
| `docs/public/authoring/index.md` | Delete and recreate as `docs/public/authoring/index.md` under the spiral authoring chapter. |
| `docs/public/authoring/getting-started.md` | Delete. Its intent is covered by `README.md`, `docs/public/index.md`, `examples/run-the-reference-project.md`, and `generator/bootstrap-project-structure.md`. No standalone final page. |
| `docs/public/authoring/framework-positioning.md` | Delete. Re-express only current positioning in `README.md`, `docs/public/index.md`, and `generator/generator-backed-authoring.md`. |
| `docs/public/authoring/tactical-model.md` | Delete. Re-project topics into `concepts/modeling-building-blocks/**`. |
| `docs/public/authoring/domain.md` | Delete. Re-project current, verified material into `architecture/domain-layer.md` and relevant concept pages. |
| `docs/public/authoring/application.md` | Delete. Re-project current, verified material into `architecture/application-layer.md` and execution concept pages. |
| `docs/public/authoring/adapter.md` | Delete. Re-project current, verified material into `architecture/adapter-layer.md` and `concepts/external-capability-anti-corruption-layer.md`. |
| `docs/public/authoring/default-happy-path.md` | Delete. Rebuild the topic as `examples/default-publication-flow.md` using the reference project. |
| `docs/public/authoring/example-contract.md` | Delete. Re-project exact contracts into `examples/index.md`, `examples/reference-content-studio.md`, and lookup reference pages. |
| `docs/public/authoring/testing-contract.md` | Delete. Rebuild as `architecture/testing-by-layer.md` and `authoring/verification-and-feedback.md`. |
| `docs/public/authoring/naming-and-layout.md` | Delete. Re-project verified parts into `generator/bootstrap-project-structure.md`, `reference/outputs.md`, and `reference/common-mistakes.md`. |
| `docs/public/authoring/generation-boundaries.md` | Delete. Rebuild as `concepts/generated-skeleton-and-handwritten-logic.md`, `generator/planning-and-ownership-review.md`, and `reference/outputs.md`. |
| `docs/public/authoring/project-authoring-workflow.md` | Delete. Rebuild as `authoring/spiral-authoring-loop.md` plus the authoring chapter pages. |
| `docs/public/authoring/generator/**` | Delete nested generator chapter. Recreate as top-level `docs/public/generator/**`. |
| `docs/public/authoring/examples/**` | Delete nested examples chapter. Recreate as top-level `docs/public/examples/**` using only `cap4k-reference-content-studio`. |
| `docs/public/authoring/advanced/**` | Delete the advanced hierarchy. Re-project topics into first-class concept pages, architecture pages, reference pages, or `reference/common-mistakes.md`. |

## Context7-Ready Requirements

The final public docs should be easy to index and retrieve through Context7 or similar documentation search.

Requirements:

- Stable page titles.
- One primary topic per page.
- Code fences must include language markers.
- Reference pages must be lookup-friendly and use exact identifiers.
- `README.md` and `docs/public/index.md` must be self-contained entry points.
- Public docs must not depend on internal specs, Phase 1 analysis, or skills.
- Submission/publishing mechanics are out of scope for Phase 2.

## Global Audit Checklist

Run this checklist before treating Phase 2 implementation as complete.

### Code Fact Audit

- Re-check plugin id.
- Re-check public Gradle task names and outputs.
- Re-check supported normal `design.json` tags.
- Re-check `types.valueObjectManifest` and `types.enumManifest`.
- Re-check bootstrap DSL, bootstrap tasks, and bootstrap plan output.
- Re-check generated output locations and ownership markers.
- Re-check runtime SQL files.
- Re-check analysis plan/output contracts.

### Reference Project Audit

- Re-check the four-module structure.
- Re-check bootstrap managed sections.
- Re-check `design/design.json`, `design/value-objects.json`, and `design/enums.json`.
- Re-check committed `plan.json` and `analysis-plan.json`.
- Re-check default publication flow.
- Re-check paid publication Saga flow.
- Re-check `MediaProcessingResultSnapshot`.
- Re-check `.http` files.
- Re-check tests.
- Re-check `analysis/flows` and `analysis/drawing-board`.

### Public Writing Audit

- No public before/now/future iteration language.
- No advanced hierarchy.
- No internal analysis/spec/skill dependency as a public prerequisite.
- No skill execution gates as the public story.
- Chinese prose with real code identifiers preserved.
- Commands and paths are verified before publication.

### Stale Term Audit

Reject or correct these stale terms and claims in public output:

- `build/cap4k code analysis`
- `build/cap4k/analysis plan.json`
- `sources.irAnalysis.enabled`
- `generators.flow.enabled`
- `generators.drawingBoard.enabled`
- `value_object` as a normal `design.json` input tag
- `validator` as a normal `design.json` input tag
- stale client/CLI wording that is not supported by current source
- unsupported KSP-only plan/generate wording

### Structure Audit

- Final tree matches this spec exactly.
- No old nested `authoring/generator/`, `authoring/examples/`, or `authoring/advanced/` directories remain.
- No standalone public `getting-started.md` remains.
- All final chapter indexes exist.
- Cross-links follow the approved reader journey.

### Image Prompt Audit

- Required `IMAGE_PROMPT` blocks exist.
- Prompt blocks use the exact required format.
- Prompt wording avoids false contracts.
- Prose remains complete without images.

### Link And Path Audit

- Links to sibling reference project paths are accurate.
- Public docs do not link to internal specs as required reading.
- All referenced Gradle task names, output paths, JSON keys, and source files exist.

## Implementation Handoff

Implementation planning should start from this spec.

Before writing the implementation plan, re-read:

- shared system design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`;
- coarse public outline: `docs/superpowers/specs/2026-06-01-cap4k-public-docs-outline.md`;
- active Phase 1 maps under `docs/superpowers/analysis/`;
- relevant cap4k source files for Gradle/plugin/generator/runtime facts;
- sibling reference project files under `cap4k-reference-content-studio/`.

The implementation plan should be organized around:

- replacing `README.md`;
- deleting and recreating `docs/public/**`;
- writing public concept, architecture, examples, authoring, generator, and reference chapters;
- auditing source facts and reference-project facts;
- scanning for stale public wording;
- verifying final tree, links, and image prompts.

Do not proceed directly from this spec to implementation without a separate implementation plan and user review.
