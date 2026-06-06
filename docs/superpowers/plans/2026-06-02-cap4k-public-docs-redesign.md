# cap4k Public Docs Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `README.md` and rebuild `docs/public/**` as the final Phase 2 public documentation surface defined by `docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md`.

**Architecture:** Treat this as a documentation-system rewrite, not a code or runtime change. First recapture current source facts and reference-project evidence, then delete and recreate the public docs tree from the approved information architecture, write each chapter against its explicit responsibility, and finish with static audits for structure, stale terminology, links, image prompts, and spec coverage.

**Tech Stack:** Markdown, PowerShell 7, `rg`, Git diff/status, current cap4k source inspection, sibling `cap4k-reference-content-studio` source inspection. Public prose is Chinese by default; code identifiers, Gradle task names, JSON keys, paths, annotations, package names, and source IDs keep their real spelling.

---

## Execution Policy

- Do not modify Kotlin, Gradle, runtime SQL, templates, tests, skills, or internal analysis maps while executing this plan.
- Do not run Gradle, application servers, HTTP calls, package installation, or build/test commands. This documentation pass uses static inspection and text verification only.
- Do not preserve old `docs/public/**` page boundaries. The final tree is recreated from the spec.
- Do not copy old public prose. Old public pages are only stale-term and topic-reminder material.
- Keep every public page self-contained for readers. Public pages must not require issue history, internal specs, Phase 1 analysis maps, or installed skills.
- Use Chinese prose for the final public docs. Keep exact identifiers in English.
- Use image prompt blocks only as optional aids; prose must remain complete without the image.

## Execution Order Override

Execute Task 2 before Task 1. Task 2 deletes and recreates `docs/public/**`; running Task 1 first would create `docs/public/index.md` and then delete it. The effective order is:

```text
Task 0 -> Task 2 -> Task 1 -> Task 3 -> Task 4 -> Task 5 -> Task 6 -> Task 7 -> Task 8 -> Task 9 -> Task 10
```

## Source Inputs

Read these before writing public content:

- `docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md`
- `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- `docs/superpowers/specs/2026-06-01-cap4k-public-docs-outline.md`
- `docs/superpowers/analysis/README.md`
- `docs/superpowers/analysis/pipeline-and-gradle-map.md`
- `docs/superpowers/analysis/source-and-generator-contract-map.md`
- `docs/superpowers/analysis/artifact-output-and-ownership-map.md`
- `docs/superpowers/analysis/runtime-and-integration-map.md`
- `docs/superpowers/analysis/analysis-flow-and-verification-map.md`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\README.md`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\docs\modeling.md`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\design\design.json`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\design\value-objects.json`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\design\enums.json`
- sibling repository `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio\build.gradle.kts`

## Files To Replace

- `README.md`
- `docs/public/**`

## Files To Create

```text
docs/public/index.md
docs/public/concepts/index.md
docs/public/concepts/modeling-building-blocks/aggregate.md
docs/public/concepts/modeling-building-blocks/entity.md
docs/public/concepts/modeling-building-blocks/value-object.md
docs/public/concepts/modeling-building-blocks/strong-id.md
docs/public/concepts/modeling-building-blocks/factory.md
docs/public/concepts/modeling-building-blocks/domain-service.md
docs/public/concepts/modeling-building-blocks/domain-event.md
docs/public/concepts/modeling-building-blocks/integration-event.md
docs/public/concepts/modeling-building-blocks/saga.md
docs/public/concepts/execution-and-ownership/command-query-separation.md
docs/public/concepts/execution-and-ownership/command.md
docs/public/concepts/execution-and-ownership/query.md
docs/public/concepts/execution-and-ownership/subscriber.md
docs/public/concepts/execution-and-ownership/scheduled-reaction.md
docs/public/concepts/execution-and-ownership/repository.md
docs/public/concepts/execution-and-ownership/unit-of-work.md
docs/public/concepts/execution-and-ownership/mediator.md
docs/public/concepts/execution-and-ownership/external-capability-anti-corruption-layer.md
docs/public/concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md
docs/public/architecture/index.md
docs/public/architecture/clean-architecture.md
docs/public/architecture/domain-layer.md
docs/public/architecture/application-layer.md
docs/public/architecture/adapter-layer.md
docs/public/architecture/start-layer.md
docs/public/architecture/dependency-rules.md
docs/public/architecture/testing-by-layer.md
docs/public/examples/index.md
docs/public/examples/reference-content-studio.md
docs/public/examples/run-the-reference-project.md
docs/public/examples/default-publication-flow.md
docs/public/examples/paid-publication-saga-flow.md
docs/public/examples/value-object-and-type-inputs.md
docs/public/examples/generation-and-analysis-evidence.md
docs/public/authoring/index.md
docs/public/authoring/spiral-authoring-loop.md
docs/public/authoring/business-intent-and-modeling.md
docs/public/authoring/technical-design.md
docs/public/authoring/generator-input-projection.md
docs/public/authoring/plan-review-and-generation.md
docs/public/authoring/implementation-inside-generated-skeletons.md
docs/public/authoring/verification-and-feedback.md
docs/public/generator/index.md
docs/public/generator/generator-backed-authoring.md
docs/public/generator/bootstrap-project-structure.md
docs/public/generator/inputs-and-sources.md
docs/public/generator/planning-and-ownership-review.md
docs/public/generator/generation-tasks.md
docs/public/generator/analysis-evidence.md
docs/public/reference/index.md
docs/public/reference/gradle-plugin.md
docs/public/reference/generator-dsl.md
docs/public/reference/design-json.md
docs/public/reference/value-object-manifest.md
docs/public/reference/enum-manifest.md
docs/public/reference/plan-json.md
docs/public/reference/outputs.md
docs/public/reference/analysis-outputs.md
docs/public/reference/runtime-database-schema.md
docs/public/reference/common-mistakes.md
```

## Files To Delete During Rebuild

Delete the current `docs/public/**` tree before recreating it. The final tree must not contain:

```text
docs/public/authoring/advanced/
docs/public/authoring/generator/
docs/public/authoring/examples/
docs/public/getting-started.md
docs/public/**/archive/
```

## Shared Page Contracts

### Concept Page Contract

Every concept page must read as an article, not a mechanical question list. It must still cover these points:

- definition;
- trigger scenario;
- layer and collaboration relationships;
- cap4k expression;
- generated/handwritten boundary;
- reference-project anchor;
- design boundary;
- common misuse;
- review points.

### Layer Page Contract

Every architecture layer page must cover:

- what the layer is responsible for;
- what the layer is not responsible for;
- where generated skeletons land;
- where handwritten logic belongs;
- dependency direction;
- reference-project directory anchors;
- review points.

### Image Prompt Block Contract

Use this exact format when a page requires or benefits from an image prompt:

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

The prompt wording must avoid these false contracts:

- cap4k writes business decisions automatically;
- every callback is Saga;
- the generator is a generic CRUD scaffold;
- frontend generation is a current core capability;
- arrows that violate Clean Architecture dependency rules.

---

### Task 0: Preflight And Static Fact Recapture

**Files:**
- Read: `docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md`
- Read: `docs/superpowers/analysis/*.md`
- Read: cap4k source anchors found by the commands below
- Read: sibling `cap4k-reference-content-studio` files listed in Source Inputs

- [ ] **Step 1: Confirm the branch and current dirty state**

Run:

```powershell
git status --short --branch
```

Expected:

- branch is `spec/documentation-system-redesign`;
- existing dirty files are understood before editing;
- do not revert unrelated changes.

- [ ] **Step 2: Inventory current public docs**

Run:

```powershell
rg --files docs/public
```

Expected: output shows the old `authoring/**`, nested `authoring/generator/**`, nested `authoring/examples/**`, nested `authoring/advanced/**`, and `reference/generator-dsl.md` surfaces that will be deleted and recreated.

- [ ] **Step 3: Recapture Gradle and generator source facts**

Run:

```powershell
rg -n "io.github.ldmoxeii.cap4k.pipeline|cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-core cap4k-plugin-pipeline-api
rg -n "types\\.valueObjectManifest|types\\.enumManifest|valueObjectManifest|enumManifest|design\\.json|api_payload|domain_event|integration_event|domain_service|saga|client|query|command" cap4k-plugin-pipeline-* cap4k-plugin-code-analysis-*
rg -n "build/cap4k-code-analysis|analysis-plan\\.json|sources\\.irAnalysis\\.inputDirs|nodes\\.json|rels\\.json|design-elements\\.json|flows|drawing-board" cap4k-plugin-pipeline-* cap4k-plugin-code-analysis-*
rg -n "BootstrapMode|bootstrap-plan\\.json|conflictPolicy|templates\\.conflictPolicy|templateId|outputKind|resolvedOutputRoot" cap4k-plugin-pipeline-*
```

Expected: commands provide the current facts for plugin id, task names, task outputs, supported tags, type manifests, bootstrap, ownership, conflict policy, and analysis output.

- [ ] **Step 4: Recapture runtime SQL facts**

Run:

```powershell
Test-Path ddd-domain-event-jpa/src/main/resources/event.sql
Test-Path ddd-application-request-jpa/src/main/resources/request.sql
Test-Path ddd-distributed-saga-jpa/src/main/resources/saga.sql
Test-Path ddd-distributed-locker-jdbc/src/main/resources/locker.sql
Test-Path ddd-distributed-snowflake/src/main/resources/worker_id.sql
Test-Path ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql
```

Expected: all six commands print `True`.

- [ ] **Step 5: Recapture reference project facts**

Run:

```powershell
$ref = "C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio"
rg --files $ref | rg "README|docs\\modeling|design\\design\\.json|design\\value-objects\\.json|design\\enums\\.json|build\\.gradle\\.kts|http\\.*\\.http|analysis\\flows|analysis\\drawing-board|PaidPublicationSaga|MediaProcessingResultSnapshot|ContentStudio.*Test|StartApplication"
rg -n "include\\(|cap4k-reference-content-studio-domain|cap4k-reference-content-studio-application|cap4k-reference-content-studio-adapter|cap4k-reference-content-studio-start|cap4k \\{|bootstrap \\{" $ref
rg -n "PaidPublicationSaga|MediaProcessingResultSnapshot|ReleasePolicy|MediaProcessingResultStatus|ContentStudioPaidPublicationSagaSmokeTest|ContentStudioHappyPathHttpSmokeTest" $ref
```

Expected: output proves the four modules, design files, `.http` files, analysis evidence, paid Saga, value-object evidence, and relevant tests exist.

- [ ] **Step 6: Stop on source-fact gaps**

If any required source anchor cannot be found, stop the plan and report the missing anchor. Do not fill public docs with guessed facts.

---

### Task 1: Replace README And Public Landing Page

**Files:**
- Modify: `README.md`
- Create: `docs/public/index.md`

- [ ] **Step 1: Rewrite `README.md` with the approved entry outline**

Write `README.md` with these sections and responsibilities:

```markdown
# cap4k

一句话定位：cap4k 是面向 DDD 战术建模、Clean Architecture 分层和 generator-backed authoring 的 Kotlin/Spring 后端框架。

<!-- IMAGE_PROMPT:
Purpose: 帮读者在 README 首屏建立 cap4k 的心智模型。
Type: concept map
Prompt: Create a clear concept map for cap4k showing human business intent, DDD tactical model, Clean Architecture layers, generator-backed authoring, generated skeletons, handwritten business logic, and evidence outputs. Use a restrained technical documentation style.
Must show: business intent, DDD concepts, Clean Architecture, generator plan, generated skeletons, handwritten logic, reference project, reference lookup
Must avoid: cap4k writing business decisions automatically, generic CRUD scaffold imagery, frontend generation, dependency arrows from domain outward to adapters
Alt text after insertion: cap4k connects business intent, DDD tactical concepts, layered architecture, generator-backed skeletons, handwritten logic, and evidence outputs.
-->

## 它解决什么问题

## 核心亮点

## 两条最短路径

## 文档地图

## 什么时候适合

## 什么时候不适合

## 当前 Gradle 入口

## License / links
```

Required content:

- Two shortest paths:
  - learning cap4k: mental model -> `docs/public/index.md` -> concepts -> architecture -> `cap4k-reference-content-studio`;
  - creating a new project: architecture -> `docs/public/generator/bootstrap-project-structure.md` -> authoring -> generator inputs / plan / generation.
- Gradle entry names: `io.github.ldmoxeii.cap4k.pipeline`, `cap4kBootstrapPlan`, `cap4kBootstrap`, `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`.
- Boundary language: cap4k is not a generic CRUD scaffold, not a frontend generator, and not a tool that writes business decisions automatically.

- [ ] **Step 2: Create `docs/public/index.md`**

Write `docs/public/index.md` with these sections:

```markdown
# cap4k Public Documentation

<!-- IMAGE_PROMPT:
Purpose: 帮读者选择从学习、建项目、写业务功能、查字段四种目标进入文档。
Type: workflow diagram
Prompt: Create a documentation journey diagram for cap4k readers. Show four reader goals branching into concepts, architecture, examples, authoring, generator, and reference chapters.
Must show: first-time learning, creating a new project, writing a business feature, looking up exact fields, cap4k-reference-content-studio
Must avoid: internal specs as required reading, skills as public prerequisites, any build/test command execution
Alt text after insertion: The cap4k public docs route readers from their goal into concepts, architecture, examples, authoring, generator, or reference pages.
-->

## 先选你的目标

## 推荐阅读路径

### 第一次学习

### 创建新项目

### 编写业务功能

### 查精确字段

## 文档章节

## 参考项目

## 不需要先读什么
```

Required content:

- Link chapters in the approved order: `concepts/`, `architecture/`, `examples/`, `authoring/`, `generator/`, `reference/`.
- The `不需要先读什么` section says readers do not need issue history, internal specs, Phase 1 maps, or skills.
- The page does not duplicate README positioning prose.

- [ ] **Step 3: Verify README and landing page sections**

Run:

```powershell
rg -n "^# cap4k$|^## 它解决什么问题$|^## 核心亮点$|^## 两条最短路径$|^## 文档地图$|^## 什么时候适合$|^## 什么时候不适合$|^## 当前 Gradle 入口$|^## License / links$|IMAGE_PROMPT" README.md
rg -n "^# cap4k Public Documentation$|^## 先选你的目标$|^## 推荐阅读路径$|^### 第一次学习$|^### 创建新项目$|^### 编写业务功能$|^### 查精确字段$|^## 文档章节$|^## 参考项目$|^## 不需要先读什么$|IMAGE_PROMPT" docs/public/index.md
```

Expected: all required headings and image prompt blocks are found.

---

### Task 2: Delete Old Public Tree And Recreate Final Directory Skeleton

**Files:**
- Delete: `docs/public/**`
- Create: final `docs/public/**` directories and files listed in `Files To Create`

- [ ] **Step 1: Verify delete target stays inside the repository docs directory**

Run:

```powershell
$repo = (Resolve-Path ".").Path
$docs = (Resolve-Path "docs").Path
$public = (Resolve-Path "docs/public").Path
if (-not $public.StartsWith($docs)) { throw "Refusing to delete outside docs: $public" }
"repo=$repo"
"docs=$docs"
"public=$public"
```

Expected: paths print under the current worktree; no exception is thrown.

- [ ] **Step 2: Delete and recreate `docs/public`**

Run:

```powershell
Remove-Item -LiteralPath "docs/public" -Recurse -Force
New-Item -ItemType Directory -Path "docs/public" | Out-Null
```

Expected: `docs/public` exists and old nested content is gone.

- [ ] **Step 3: Create final directories**

Run:

```powershell
$dirs = @(
  "docs/public/concepts/modeling-building-blocks",
  "docs/public/concepts/execution-and-ownership",
  "docs/public/architecture",
  "docs/public/examples",
  "docs/public/authoring",
  "docs/public/generator",
  "docs/public/reference"
)
foreach ($dir in $dirs) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
```

Expected: all final chapter directories exist.

- [ ] **Step 4: Create empty files only as immediate write targets**

Create each file listed in `Files To Create`, then fill it in during the later chapter tasks before final review. Do not leave empty files at the end of this plan.

Run:

```powershell
$files = @(
  "docs/public/index.md",
  "docs/public/concepts/index.md",
  "docs/public/concepts/modeling-building-blocks/aggregate.md",
  "docs/public/concepts/modeling-building-blocks/entity.md",
  "docs/public/concepts/modeling-building-blocks/value-object.md",
  "docs/public/concepts/modeling-building-blocks/strong-id.md",
  "docs/public/concepts/modeling-building-blocks/factory.md",
  "docs/public/concepts/modeling-building-blocks/domain-service.md",
  "docs/public/concepts/modeling-building-blocks/domain-event.md",
  "docs/public/concepts/modeling-building-blocks/integration-event.md",
  "docs/public/concepts/modeling-building-blocks/saga.md",
  "docs/public/concepts/execution-and-ownership/command-query-separation.md",
  "docs/public/concepts/execution-and-ownership/command.md",
  "docs/public/concepts/execution-and-ownership/query.md",
  "docs/public/concepts/execution-and-ownership/subscriber.md",
  "docs/public/concepts/execution-and-ownership/scheduled-reaction.md",
  "docs/public/concepts/execution-and-ownership/repository.md",
  "docs/public/concepts/execution-and-ownership/unit-of-work.md",
  "docs/public/concepts/execution-and-ownership/mediator.md",
  "docs/public/concepts/execution-and-ownership/external-capability-anti-corruption-layer.md",
  "docs/public/concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md",
  "docs/public/architecture/index.md",
  "docs/public/architecture/clean-architecture.md",
  "docs/public/architecture/domain-layer.md",
  "docs/public/architecture/application-layer.md",
  "docs/public/architecture/adapter-layer.md",
  "docs/public/architecture/start-layer.md",
  "docs/public/architecture/dependency-rules.md",
  "docs/public/architecture/testing-by-layer.md",
  "docs/public/examples/index.md",
  "docs/public/examples/reference-content-studio.md",
  "docs/public/examples/run-the-reference-project.md",
  "docs/public/examples/default-publication-flow.md",
  "docs/public/examples/paid-publication-saga-flow.md",
  "docs/public/examples/value-object-and-type-inputs.md",
  "docs/public/examples/generation-and-analysis-evidence.md",
  "docs/public/authoring/index.md",
  "docs/public/authoring/spiral-authoring-loop.md",
  "docs/public/authoring/business-intent-and-modeling.md",
  "docs/public/authoring/technical-design.md",
  "docs/public/authoring/generator-input-projection.md",
  "docs/public/authoring/plan-review-and-generation.md",
  "docs/public/authoring/implementation-inside-generated-skeletons.md",
  "docs/public/authoring/verification-and-feedback.md",
  "docs/public/generator/index.md",
  "docs/public/generator/generator-backed-authoring.md",
  "docs/public/generator/bootstrap-project-structure.md",
  "docs/public/generator/inputs-and-sources.md",
  "docs/public/generator/planning-and-ownership-review.md",
  "docs/public/generator/generation-tasks.md",
  "docs/public/generator/analysis-evidence.md",
  "docs/public/reference/index.md",
  "docs/public/reference/gradle-plugin.md",
  "docs/public/reference/generator-dsl.md",
  "docs/public/reference/design-json.md",
  "docs/public/reference/value-object-manifest.md",
  "docs/public/reference/enum-manifest.md",
  "docs/public/reference/plan-json.md",
  "docs/public/reference/outputs.md",
  "docs/public/reference/analysis-outputs.md",
  "docs/public/reference/runtime-database-schema.md",
  "docs/public/reference/common-mistakes.md"
)
foreach ($file in $files) { New-Item -ItemType File -Path $file -Force | Out-Null }
```

Expected: all final file paths exist.

- [ ] **Step 5: Verify forbidden old paths are absent**

Run:

```powershell
Test-Path docs/public/authoring/advanced
Test-Path docs/public/authoring/generator
Test-Path docs/public/authoring/examples
Test-Path docs/public/getting-started.md
```

Expected: all four commands print `False`.

---

### Task 3: Write Concepts Chapter

**Files:**
- Modify: `docs/public/concepts/index.md`
- Modify: all files under `docs/public/concepts/modeling-building-blocks/`
- Modify: all files under `docs/public/concepts/execution-and-ownership/`

- [ ] **Step 1: Write `docs/public/concepts/index.md`**

Required content:

- Define `concepts/` as cap4k building blocks for modeling, implementation, generation, and review.
- Introduce the two groups:
  - `modeling-building-blocks/`
  - `execution-and-ownership/`
- Provide a suggested reading order that starts with Aggregate, Entity, Value Object, Command/Query separation, Command, Query, Domain Event, Subscriber, Saga, generated skeleton boundaries.
- Include a selective `IMAGE_PROMPT` concept map.
- Link every concept page exactly once.

- [ ] **Step 2: Write modeling building-block pages**

Write these pages with the Concept Page Contract:

```text
docs/public/concepts/modeling-building-blocks/aggregate.md
docs/public/concepts/modeling-building-blocks/entity.md
docs/public/concepts/modeling-building-blocks/value-object.md
docs/public/concepts/modeling-building-blocks/strong-id.md
docs/public/concepts/modeling-building-blocks/factory.md
docs/public/concepts/modeling-building-blocks/domain-service.md
docs/public/concepts/modeling-building-blocks/domain-event.md
docs/public/concepts/modeling-building-blocks/integration-event.md
docs/public/concepts/modeling-building-blocks/saga.md
```

Required anchors:

- `aggregate.md`: aggregate root, boundary, invariants, transaction consistency, repository ownership, command entry, reference project `domain/aggregates/content/ContentBehavior.kt`.
- `entity.md`: identity inside an aggregate, lifecycle under aggregate root, no independent write entry for internal entities.
- `value-object.md`: immutable value, equality by value, Strong ID family, Business Enum family, composite / JSON-backed value object, `types.valueObjectManifest`, `MediaProcessingResultSnapshot`.
- `strong-id.md`: typed identifiers, avoiding bare `String` / `Long` drift, relationship to value-object source metadata.
- `factory.md`: creation rules, default state, input normalization, not a constructor wrapper, reference project `ContentFactory.kt`.
- `domain-service.md`: domain decision not owned by one aggregate or value object, Specification mentioned as nearby pattern, no standalone Specification page.
- `domain-event.md`: domain fact after aggregate state change, emitted by domain model, downstream subscriber treatment.
- `integration-event.md`: external fact, inbound/outbound distinction, published language, selective `IMAGE_PROMPT`.
- `saga.md`: persisted cross-step coordination, retry, recovery, compensation, `PaidPublicationSaga`, not ordinary callback-to-command, selective `IMAGE_PROMPT`.

- [ ] **Step 3: Write execution-and-ownership pages**

Write these pages with the Concept Page Contract:

```text
docs/public/concepts/execution-and-ownership/command-query-separation.md
docs/public/concepts/execution-and-ownership/command.md
docs/public/concepts/execution-and-ownership/query.md
docs/public/concepts/execution-and-ownership/subscriber.md
docs/public/concepts/execution-and-ownership/scheduled-reaction.md
docs/public/concepts/execution-and-ownership/repository.md
docs/public/concepts/execution-and-ownership/unit-of-work.md
docs/public/concepts/execution-and-ownership/mediator.md
docs/public/concepts/execution-and-ownership/external-capability-anti-corruption-layer.md
docs/public/concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md
```

Required anchors:

- `command-query-separation.md`: explain why cap4k does not put command and query behavior into one traditional service class; explicitly say this is not a demand for full CQRS infrastructure.
- `command.md`: state-changing intent, handler ownership, aggregate behavior, Unit of Work save, event release.
- `query.md`: read observation intent, query handler, read model, no mutation of business state.
- `subscriber.md`: Domain Event Subscriber and inbound Integration Event Subscriber boundaries, idempotency, selective `IMAGE_PROMPT`.
- `scheduled-reaction.md`: time-based reaction, polling, recovery, Job as implementation surface, selective `IMAGE_PROMPT`.
- `repository.md`: aggregate access boundary, not a business decision bucket and not arbitrary query service.
- `unit-of-work.md`: application write commit boundary, not a JPA internals page.
- `mediator.md`: runtime routing facade, not business orchestration engine, not domain dependency.
- `external-capability-anti-corruption-layer.md`: application declares external capability, adapter performs protocol conversion, `client` / `client-handler` as generator expression, selective `IMAGE_PROMPT`.
- `generated-skeleton-and-handwritten-logic.md`: checked-in skeletons, build-owned generated source, handwritten logic locations, fallback rule when bypassing skeletons, selective `IMAGE_PROMPT`.

- [ ] **Step 4: Verify concept structure and required terms**

Run:

```powershell
rg --files docs/public/concepts
rg -n "定义|触发|层|协作|cap4k|生成|手写|参考项目|设计边界|常见误用|审核" docs/public/concepts -g "*.md"
rg -n "Specification|Business Enum|API Payload|Handler|Job|Client|not a standalone|不是独立" docs/public/concepts -g "*.md"
rg -n "IMAGE_PROMPT" docs/public/concepts
```

Expected:

- the file list matches the approved concepts tree;
- every concept page contains the shared coverage ideas in natural prose;
- non-page concept decisions are represented;
- selective image prompt pages are present.

---

### Task 4: Write Architecture Chapter

**Files:**
- Modify: `docs/public/architecture/index.md`
- Modify: `docs/public/architecture/clean-architecture.md`
- Modify: `docs/public/architecture/domain-layer.md`
- Modify: `docs/public/architecture/application-layer.md`
- Modify: `docs/public/architecture/adapter-layer.md`
- Modify: `docs/public/architecture/start-layer.md`
- Modify: `docs/public/architecture/dependency-rules.md`
- Modify: `docs/public/architecture/testing-by-layer.md`

- [ ] **Step 1: Write architecture index**

Required content:

- Explain four layers: domain, application, adapter, start.
- Explain cross-cutting pages: clean architecture, dependency rules, testing by layer.
- Link concept pages where layer responsibilities depend on tactical concepts.
- Link example project pages as concrete anchors.

- [ ] **Step 2: Write clean architecture and dependency rules pages**

Required content:

- `clean-architecture.md`: complete mental model, layer responsibilities, why cap4k generation supports the layer model, required architecture `IMAGE_PROMPT`.
- `dependency-rules.md`: allowed and forbidden dependency directions, domain independence, application orchestration, adapter protocol conversion, start assembly, required dependency-rule `IMAGE_PROMPT`.

- [ ] **Step 3: Write layer pages**

Write these pages with the Layer Page Contract:

```text
docs/public/architecture/domain-layer.md
docs/public/architecture/application-layer.md
docs/public/architecture/adapter-layer.md
docs/public/architecture/start-layer.md
```

Required anchors:

- `domain-layer.md`: Aggregate, Entity, Value Object, Factory, Domain Service, Domain Event, domain tests, no adapter/protocol concerns.
- `application-layer.md`: Command, Query, Subscriber, Saga, Scheduled Reaction, Unit of Work, Mediator, external capability requests, no HTTP payload details.
- `adapter-layer.md`: Controller, API Payload, query adapter, client-handler, inbound integration listener, persistence adapter, protocol conversion.
- `start-layer.md`: Spring Boot runtime assembly, local startup, runtime config, smoke path, not a business truth layer.

- [ ] **Step 4: Write testing-by-layer**

Required content:

- Explain what should be tested per layer.
- Emphasize handwritten business behavior and generated skeleton boundaries.
- Link to examples and reference project tests as evidence anchors.
- Include optional `IMAGE_PROMPT` only if it clarifies test responsibility.

- [ ] **Step 5: Verify architecture pages**

Run:

```powershell
rg --files docs/public/architecture
rg -n "负责|不负责|生成骨架|手写逻辑|依赖方向|参考项目|审核" docs/public/architecture -g "*.md"
rg -n "IMAGE_PROMPT" docs/public/architecture/clean-architecture.md docs/public/architecture/dependency-rules.md
```

Expected:

- all eight architecture files exist;
- every layer page includes the fixed coverage items;
- required image prompt blocks exist.

---

### Task 5: Write Examples Chapter

**Files:**
- Modify: `docs/public/examples/index.md`
- Modify: `docs/public/examples/reference-content-studio.md`
- Modify: `docs/public/examples/run-the-reference-project.md`
- Modify: `docs/public/examples/default-publication-flow.md`
- Modify: `docs/public/examples/paid-publication-saga-flow.md`
- Modify: `docs/public/examples/value-object-and-type-inputs.md`
- Modify: `docs/public/examples/generation-and-analysis-evidence.md`

- [ ] **Step 1: Write examples index and reference project overview**

Required content:

- Say examples use only sibling repo `cap4k-reference-content-studio`.
- Explain public docs provide explanation; reference project provides runnable code evidence.
- `reference-content-studio.md` must cover four modules, business scope, design files, generated plan, tests, `.http`, analysis evidence, and include a project-structure `IMAGE_PROMPT`.

- [ ] **Step 2: Write run and flow pages**

Required content:

- `run-the-reference-project.md`: describe read/run path and `.http` files without inventing unverified commands. Link or name `http/content.http`, `http/review.http`, `http/media-processing.http`, `http/paid-publication.http`, `http/query.http`.
- `default-publication-flow.md`: explain default content publication flow and include a workflow `IMAGE_PROMPT`.
- `paid-publication-saga-flow.md`: explain opt-in paid publication Saga, compensation, and include a Saga workflow `IMAGE_PROMPT`.

- [ ] **Step 3: Write type input and evidence pages**

Required content:

- `value-object-and-type-inputs.md`: explain `MediaProcessingResultSnapshot`, `ReleasePolicy`, `MediaProcessingResultStatus`, `types.valueObjectManifest`, and `types.enumManifest`.
- `generation-and-analysis-evidence.md`: explain `design/design.json`, schema, `plan.json`, `analysis-plan.json`, `analysis/flows`, `analysis/drawing-board`, and tests as evidence surfaces.

- [ ] **Step 4: Verify example pages against sibling repo**

Run:

```powershell
$ref = "C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k-reference-content-studio"
rg -n "cap4k-reference-content-studio-domain|cap4k-reference-content-studio-application|cap4k-reference-content-studio-adapter|cap4k-reference-content-studio-start|design/design\\.json|design/value-objects\\.json|design/enums\\.json|analysis/flows|analysis/drawing-board|\\.http|PaidPublicationSaga|MediaProcessingResultSnapshot" docs/public/examples -g "*.md"
rg -n "PaidPublicationSaga|MediaProcessingResultSnapshot|ReleasePolicy|MediaProcessingResultStatus|ContentStudioPaidPublicationSagaSmokeTest|ContentStudioHappyPathHttpSmokeTest" $ref
rg -n "IMAGE_PROMPT" docs/public/examples/reference-content-studio.md docs/public/examples/default-publication-flow.md docs/public/examples/paid-publication-saga-flow.md
```

Expected:

- example docs mention only verified reference-project files and concepts;
- required image prompt blocks exist;
- no alternate fictional example universe is introduced.

---

### Task 6: Write Authoring Chapter

**Files:**
- Modify: `docs/public/authoring/index.md`
- Modify: `docs/public/authoring/spiral-authoring-loop.md`
- Modify: `docs/public/authoring/business-intent-and-modeling.md`
- Modify: `docs/public/authoring/technical-design.md`
- Modify: `docs/public/authoring/generator-input-projection.md`
- Modify: `docs/public/authoring/plan-review-and-generation.md`
- Modify: `docs/public/authoring/implementation-inside-generated-skeletons.md`
- Modify: `docs/public/authoring/verification-and-feedback.md`

- [ ] **Step 1: Write authoring index and spiral loop**

Required content:

- `index.md`: authoring is a public workflow explanation, not a skill execution manual.
- `spiral-authoring-loop.md`: intent -> model -> technical design -> generator inputs -> plan review -> generation -> handwritten implementation -> verification -> feedback; include required spiral `IMAGE_PROMPT`.
- Explain that later evidence can send the author back to earlier decisions.

- [ ] **Step 2: Write modeling, technical design, and input projection pages**

Required content:

- `business-intent-and-modeling.md`: business intent, ubiquitous terms, boundaries, Aggregate, Value Object, Event, external capabilities, policies.
- `technical-design.md`: Clean Architecture, modules, command/query boundaries, events, Saga, Subscriber, Scheduled Reaction, adapter boundaries, persistence expectations, testing expectations.
- `generator-input-projection.md`: projection to DB schema, `design/design.json`, `types.enumManifest`, `types.valueObjectManifest`, module layout, Gradle extension configuration.

- [ ] **Step 3: Write plan/generation, implementation, and verification pages**

Required content:

- `plan-review-and-generation.md`: `cap4kPlan`, `cap4kBootstrapPlan`, ownership, plan review, when to stop before generation.
- `implementation-inside-generated-skeletons.md`: complex business logic belongs in intended generated skeleton surfaces; bypassing skeletons means returning to design.
- `verification-and-feedback.md`: static review, tests/HTTP/analysis evidence as public workflow concepts, without telling this execution agent to run build/test/HTTP.

- [ ] **Step 4: Verify authoring pages**

Run:

```powershell
rg --files docs/public/authoring
rg -n "螺旋|业务意图|技术设计|design\\.json|types\\.enumManifest|types\\.valueObjectManifest|cap4kPlan|cap4kBootstrapPlan|生成骨架|反馈" docs/public/authoring -g "*.md"
rg -n "skills|技能|执行手册" docs/public/authoring -g "*.md"
rg -n "IMAGE_PROMPT" docs/public/authoring/spiral-authoring-loop.md
```

Expected:

- all authoring pages exist;
- pages describe the spiral workflow and generation evidence;
- any mention of skills appears only as a boundary that public docs are not skill execution manuals;
- required image prompt block exists.

---

### Task 7: Write Generator Chapter

**Files:**
- Modify: `docs/public/generator/index.md`
- Modify: `docs/public/generator/generator-backed-authoring.md`
- Modify: `docs/public/generator/bootstrap-project-structure.md`
- Modify: `docs/public/generator/inputs-and-sources.md`
- Modify: `docs/public/generator/planning-and-ownership-review.md`
- Modify: `docs/public/generator/generation-tasks.md`
- Modify: `docs/public/generator/analysis-evidence.md`

- [ ] **Step 1: Write generator index and generator-backed authoring page**

Required content:

- `index.md`: generator mechanics support authoring; exact fields belong in reference pages.
- `generator-backed-authoring.md`: generator is architecture control, not ordinary scaffold; generator creates stable code slots and evidence; include required `IMAGE_PROMPT`.

- [ ] **Step 2: Write bootstrap and inputs pages**

Required content:

- `bootstrap-project-structure.md`: bootstrap is default new-project structure entry; learning path still starts with concepts/architecture/reference project; bootstrap does not replace modeling, schema, `design.json`, value-object manifest, enum manifest; mention equivalent manual four-layer multi-module layout.
- `inputs-and-sources.md`: Gradle extension, DB/schema, `design.json`, `types.valueObjectManifest`, `types.enumManifest`, source-analysis inputs, reference-project examples.

- [ ] **Step 3: Write planning, tasks, and evidence pages**

Required content:

- `planning-and-ownership-review.md`: `cap4kPlan`, `bootstrap-plan.json`, `plan.json`, `conflictPolicy`, generated-vs-handwritten ownership, managed sections, review before generation; include required `IMAGE_PROMPT`.
- `generation-tasks.md`: `cap4kGenerate`, `cap4kGenerateSources`, checked-in source vs generated source, analysis task boundary.
- `analysis-evidence.md`: `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`, flows, drawing-board, analysis evidence; include required `IMAGE_PROMPT`.

- [ ] **Step 4: Verify generator pages**

Run:

```powershell
rg --files docs/public/generator
rg -n "architecture control|scaffold|cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|bootstrap-plan\\.json|plan\\.json|conflictPolicy|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate|flows|drawing-board" docs/public/generator -g "*.md"
rg -n "IMAGE_PROMPT" docs/public/generator/generator-backed-authoring.md docs/public/generator/planning-and-ownership-review.md docs/public/generator/analysis-evidence.md
```

Expected:

- all generator pages exist;
- bootstrap and plan ownership details are present;
- required image prompt blocks exist;
- generator is not described as a generic CRUD scaffold.

---

### Task 8: Write Reference Chapter

**Files:**
- Modify: all files under `docs/public/reference/`

- [ ] **Step 1: Write reference index and Gradle pages**

Required content:

- `index.md`: route lookup readers by task, DSL, design JSON, type manifests, plan, outputs, analysis, runtime schema, and mistakes.
- `gradle-plugin.md`: plugin id `io.github.ldmoxeii.cap4k.pipeline`, task table, task outputs, minimal Gradle entry snippets.
- `generator-dsl.md`: `cap4k { project/types/sources/generators/templates/bootstrap/layout/addons }` blocks, fields, minimal snippets, bootstrap block.

- [ ] **Step 2: Write input contract pages**

Required content:

- `design-json.md`: supported normal tags `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, `saga`; expected fields; `resultFields`; `eventName`; artifact family/variant; tag constraints; explicitly exclude normal `validator` and normal `value_object` design tags.
- `value-object-manifest.md`: `types.valueObjectManifest`, shared vs aggregate-owned shape, JSON-backed example, generated output notes, generated `@BuildingBlock(tag = "value_object", family = "value-object")` as output metadata rather than normal design input.
- `enum-manifest.md`: `types.enumManifest` schema and enum generation notes.

- [ ] **Step 3: Write plan/output/analysis/runtime pages**

Required content:

- `plan-json.md`: `generatorId`, `templateId`, `outputKind`, `resolvedOutputRoot`, `conflictPolicy`, paths, `bootstrap-plan.json`.
- `outputs.md`: `CHECKED_IN_SOURCE`, `GENERATED_SOURCE`, `OUTPUT_ARTIFACT`, `build/generated/cap4k/main/kotlin`, generated-vs-handwritten ownership.
- `analysis-outputs.md`: `build/cap4k-code-analysis`, `nodes.json`, `rels.json`, `design-elements.json`, `build/cap4k/analysis-plan.json`, `flows`, `drawing-board`, `sources.irAnalysis.inputDirs`.
- `runtime-database-schema.md`: table/resource purposes for `event.sql`, `request.sql`, `saga.sql`, `locker.sql`, `worker_id.sql`, `event_http_subscriber.sql`; no full runtime configuration reference.

- [ ] **Step 4: Write common mistakes**

Required content:

- command/query separation mistakes;
- treating generator as CRUD scaffold;
- treating `cap4kAnalysisGenerate` as source generation;
- using stale `sources.irAnalysis.enabled`, `generators.flow.enabled`, or `generators.drawingBoard.enabled`;
- using normal `design.json` `value_object` or `validator` tags;
- not reviewing `plan.json` ownership before generation;
- using bootstrap without matching module path/base package expectations;
- treating every callback as Saga;
- putting adapter/protocol concerns into domain.

- [ ] **Step 5: Verify reference pages**

Run:

```powershell
rg --files docs/public/reference
rg -n "io.github.ldmoxeii.cap4k.pipeline|cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" docs/public/reference -g "*.md"
rg -n "types\\.valueObjectManifest|types\\.enumManifest|command|query|client|api_payload|domain_event|integration_event|domain_service|saga|validator|value_object" docs/public/reference -g "*.md"
rg -n "generatorId|templateId|outputKind|resolvedOutputRoot|conflictPolicy|CHECKED_IN_SOURCE|GENERATED_SOURCE|OUTPUT_ARTIFACT|build/generated/cap4k/main/kotlin" docs/public/reference -g "*.md"
rg -n "event\\.sql|request\\.sql|saga\\.sql|locker\\.sql|worker_id\\.sql|event_http_subscriber\\.sql|build/cap4k-code-analysis|nodes\\.json|rels\\.json|design-elements\\.json|analysis-plan\\.json|sources\\.irAnalysis\\.inputDirs" docs/public/reference -g "*.md"
```

Expected:

- all reference pages exist;
- exact identifiers are present;
- stale terms appear only as explicit mistakes or exclusions.

---

### Task 9: Cross-Link, Image Prompt, And Stale-Term Audit

**Files:**
- Modify: any public docs page found by this audit

- [ ] **Step 1: Verify exact final tree**

Run:

```powershell
rg --files docs/public | Sort-Object
```

Expected: output matches the `Files To Create` list exactly, with no extra old paths.

- [ ] **Step 2: Verify required image prompts**

Run:

```powershell
rg -n "IMAGE_PROMPT" README.md docs/public
rg -n "Purpose:|Type:|Prompt:|Must show:|Must avoid:|Alt text after insertion:" README.md docs/public
```

Expected:

- required image prompt pages contain full prompt blocks;
- selective concept image pages are present where the spec requires priority handling;
- each block contains all required fields.

- [ ] **Step 3: Verify no forbidden final tree shapes remain**

Run:

```powershell
Test-Path docs/public/authoring/advanced
Test-Path docs/public/authoring/generator
Test-Path docs/public/authoring/examples
Test-Path docs/public/getting-started.md
rg --files docs/public | rg "archive|authoring[\\/]advanced|authoring[\\/]generator|authoring[\\/]examples|gettting-started|getting-started"
```

Expected:

- each `Test-Path` prints `False`;
- final `rg` command prints no output.

- [ ] **Step 4: Run stale-term scan**

Run:

```powershell
rg -n "build/cap4k code analysis|build/cap4k/analysis plan\\.json|sources\\.irAnalysis\\.enabled|generators\\.flow\\.enabled|generators\\.drawingBoard\\.enabled|tag = \"value_object\"|tag = 'value_object'|validator.*design|KSP-only|frontend generation|generic CRUD scaffold" README.md docs/public
```

Expected:

- no stale term appears in README or tutorial prose;
- stale terms may appear in `docs/public/reference/common-mistakes.md` or reference exclusion notes only as explicit mistakes or non-contracts.

- [ ] **Step 5: Verify public docs do not require internal materials**

Run:

```powershell
rg -n "docs/superpowers|Phase 1|analysis map|issue history|skill|技能|internal spec|内部 spec" README.md docs/public
```

Expected:

- no public page tells readers to read internal specs, Phase 1 maps, issue history, or skills as a prerequisite;
- any mention is a boundary statement saying those are not required.

- [ ] **Step 6: Verify code fences have language markers**

Run:

```powershell
rg -n "^```$" README.md docs/public
```

Expected: no output. Every code fence should be marked as `text`, `kotlin`, `json`, `powershell`, `sql`, `markdown`, or another specific language.

---

### Task 10: Spec Coverage And Final Diff Review

**Files:**
- Read: `docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md`
- Read: all changed public docs

- [ ] **Step 1: Verify spec-required paths exist**

Run:

```powershell
rg --files docs/public | rg "command-query-separation\\.md|runtime-database-schema\\.md|bootstrap-project-structure\\.md|reference-content-studio\\.md|spiral-authoring-loop\\.md|generator-backed-authoring\\.md|planning-and-ownership-review\\.md|analysis-evidence\\.md"
```

Expected: all listed paths are present.

- [ ] **Step 2: Verify required entry points and chapter indexes**

Run:

```powershell
Test-Path README.md
Test-Path docs/public/index.md
Test-Path docs/public/concepts/index.md
Test-Path docs/public/architecture/index.md
Test-Path docs/public/examples/index.md
Test-Path docs/public/authoring/index.md
Test-Path docs/public/generator/index.md
Test-Path docs/public/reference/index.md
```

Expected: all commands print `True`.

- [ ] **Step 3: Review diff for accidental scope expansion**

Run:

```powershell
git diff --stat
git diff -- README.md docs/public docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md
git status --short
```

Expected:

- changed files are limited to `README.md`, `docs/public/**`, and already-approved spec/plan files;
- no Kotlin, Gradle, runtime SQL, tests, or skills changed.

- [ ] **Step 4: Run final public-doc static scan**

Run:

```powershell
rg -n -e "Detailed Design Checkpoint" -e "Open Decisions" -e "Continue Brainstorming" -e "Candidate concepts" -e "Proposed Directory Shape" -e "Likely actions" -e "T[B]D" -e "T[O]DO" README.md docs/public docs/superpowers/specs/2026-06-02-cap4k-public-docs-redesign.md
```

Expected: no output.

- [ ] **Step 5: Produce final implementation summary**

Write a concise summary for review containing:

- README replaced;
- `docs/public/**` deleted and recreated from the approved tree;
- chapters completed: concepts, architecture, examples, authoring, generator, reference;
- facts rechecked from cap4k source and reference project;
- static checks run and their results;
- checks intentionally not run because this plan forbids build/test/app/http execution.

---

## Self-Review Checklist For This Plan

- The plan starts from the final spec and does not rely on conversation memory.
- It covers every final public page path from the spec.
- It includes the deletion/recreation policy for old `docs/public/**`.
- It includes source fact recapture before writing factual claims.
- It includes reference-project evidence recapture before writing examples.
- It includes required `IMAGE_PROMPT` pages and the prompt block contract.
- It includes stale-term, structure, link, and code-fence static scans.
- It does not instruct the implementing agent to run Gradle, application servers, HTTP calls, installs, or build/test commands.
