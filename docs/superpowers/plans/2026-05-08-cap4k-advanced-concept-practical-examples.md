# Cap4k Advanced Concept Practical Examples Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add practical, cap4k-shaped Chinese authoring examples for `Value Object`, `Domain Service`, and `Saga`, while keeping advanced concept pages as boundary references and `examples` pages as the main citation targets.

**Architecture:** Keep the docs structure stable. First extend the example contract and shared navigation, then add one comparison overview plus three concept-specific example pages grounded in the existing content-publication reference project, and finally back-link the advanced concept pages to those examples. Avoid touching active `#22` doc surfaces and avoid turning the issue into generic DDD teaching or runtime Saga feature work. Default to structured prose, but allow short supporting snippets when they make the authoring shape materially clearer.

**Tech Stack:** Markdown docs, existing public authoring structure, repository-internal doc links, manual doc regression checks.

---

## File Map

- Modify: `docs/public/authoring/example-contract.zh-CN.md`
  - Keep the five-part example contract as the baseline and add the narrow note that advanced concept examples may insert `Usage boundary`.
- Modify: `docs/public/authoring/advanced/index.zh-CN.md`
  - Add the practical examples entry point for the new overview page.
- Modify: `docs/public/authoring/advanced/value-object.zh-CN.md`
  - Add a lightweight link to the new value-object example page, without moving the page’s main conceptual framing into `examples`.
- Modify: `docs/public/authoring/advanced/domain-service.zh-CN.md`
  - Add a lightweight link to the new domain-service example page.
- Modify: `docs/public/authoring/advanced/saga.zh-CN.md`
  - Add a lightweight link to the new saga example page.
- Modify: `docs/public/authoring/examples/reference-project-overview.zh-CN.md`
  - Add a short advanced-concept jump section that ties the three concept example pages back to the shared reference project.
- Create: `docs/public/authoring/examples/advanced-concepts-overview.zh-CN.md`
  - Add the comparison page that helps authors/reviewers decide whether the problem still fits the default path or has crossed into one of the three advanced concepts.
- Create: `docs/public/authoring/examples/content-publication-value-object.zh-CN.md`
  - Add the `Value Object` practical example and non-example page.
- Create: `docs/public/authoring/examples/content-publication-domain-service.zh-CN.md`
  - Add the `Domain Service` practical example and non-example page.
- Create: `docs/public/authoring/examples/content-publication-saga.zh-CN.md`
  - Add the `Saga` practical example and non-example page.

## Snippet Policy

- Default to structured prose tied to the shared reference project.
- Supporting snippets are allowed when prose alone would leave the authoring shape or review boundary ambiguous.
- Prefer the lightest form first:
  - `text` blocks for command / event / stage transitions
  - file or directory shape blocks
  - short, non-runnable Kotlin-shaped skeletons only when needed to clarify ownership
- Snippets support the page; they do not replace `Scenario`, `Why default path is not enough`, `Recommended shape`, `Non-example / misuse`, `Usage boundary`, or `Audit cues`.
- Keep snippet usage bounded, typically one or two short blocks per page.
- Do not turn the page into a runnable tutorial, a generator DSL reference, or a framework-specific implementation walkthrough.

## Task 1: Extend the example contract and add the new overview entry points

**Files:**
- Modify: `docs/public/authoring/example-contract.zh-CN.md`
- Modify: `docs/public/authoring/advanced/index.zh-CN.md`
- Modify: `docs/public/authoring/examples/reference-project-overview.zh-CN.md`

- [ ] **Step 1: Re-read the current contract and navigation anchors**

Run:

```powershell
Get-Content docs/public/authoring/example-contract.zh-CN.md
Get-Content docs/public/authoring/advanced/index.zh-CN.md
Get-Content docs/public/authoring/examples/reference-project-overview.zh-CN.md
```

Expected:

- `example-contract` currently defines the five-part structure
- `advanced/index` currently links concept pages but not a practical examples overview
- `reference-project-overview` currently anchors the shared project but does not yet expose a dedicated advanced-concept example jump section

- [ ] **Step 2: Update `example-contract.zh-CN.md` with the narrow advanced-concept extension**

Add a short section stating:

- the baseline contract remains `Scenario / Why this layer or concept / Recommended shape / Non-example or misuse / Audit cues`
- advanced-concept example pages may add `Usage boundary`
- advanced-concept example pages may also use short supporting snippets when they improve clarity, but snippets do not replace the narrative contract
- this does not change the required baseline for the rest of the examples subtree

Expected result:

- no broad contract rewrite
- one explicit rule that supports `#21` without changing every existing example page

- [ ] **Step 3: Update `advanced/index.zh-CN.md`**

Add a short line that introduces the new practical examples entry point, linking to:

- `../examples/advanced-concepts-overview.zh-CN.md`

Expected result:

- users can enter the advanced-concept example chain from the concept index without hunting through the examples directory manually

- [ ] **Step 4: Update `reference-project-overview.zh-CN.md`**

Add a short section that says, in effect:

- the same reference project also has advanced-concept example pages
- `Value Object`, `Domain Service`, and `Saga` examples are all grounded in the same `Content` / `MediaProcessingTask` / callback / polling reference world
- links point to the three concept example pages

Expected result:

- the shared project overview becomes the stable jump-back anchor for all new concept examples

- [ ] **Step 5: Verify only the intended navigation surfaces were changed**

Run:

```powershell
git diff -- docs/public/authoring/example-contract.zh-CN.md docs/public/authoring/advanced/index.zh-CN.md docs/public/authoring/examples/reference-project-overview.zh-CN.md
```

Expected:

- diff only shows the contract note and link additions
- no edits appear in forbidden `#22` surfaces

## Task 2: Add the advanced concepts comparison overview page

**Files:**
- Create: `docs/public/authoring/examples/advanced-concepts-overview.zh-CN.md`

- [ ] **Step 1: Create the page with a comparison-first structure**

Write the page with these top-level sections:

```markdown
# 高级概念示例总览
## Scenario
## Why this layer / concept
## Recommended shape
## Non-example / misuse
## Audit cues
```

Use the body to compare:

- when the default path is still enough
- when `Value Object` is the next justified step
- when `Domain Service` is the next justified step
- when the problem has crossed into `Saga`

Expected result:

- one reviewer-friendly and citation-friendly comparison page
- primarily prose, with an optional short supporting block if a side-by-side shape comparison becomes clearer that way
- content stays anchored to the content-publication reference project

- [ ] **Step 2: Include stable deep links to the three concept example pages**

Add explicit links to:

- `content-publication-value-object.zh-CN.md`
- `content-publication-domain-service.zh-CN.md`
- `content-publication-saga.zh-CN.md`

Expected result:

- later docs can cite either the comparison page or one concept page directly

- [ ] **Step 3: Verify the overview does not drift into generic DDD textbook content**

Run:

```powershell
rg -n 'Content|MediaProcessingTask|callback|polling|PublishContentCmd|StartMediaProcessingCmd' docs/public/authoring/examples/advanced-concepts-overview.zh-CN.md
```

Expected:

- the page explicitly references the cap4k reference project rather than abstract theory only

## Task 3: Add the Value Object practical example page and wire the concept page to it

**Files:**
- Create: `docs/public/authoring/examples/content-publication-value-object.zh-CN.md`
- Modify: `docs/public/authoring/advanced/value-object.zh-CN.md`

- [ ] **Step 1: Create the Value Object example page using the six-part advanced example shape**

Write the page with these top-level sections:

```markdown
# 内容发布示例：Value Object
## Scenario
## Why default path is not enough
## Recommended shape
## Non-example / misuse
## Usage boundary
## Audit cues
```

Expected example choices:

- positive example: `ContentTitle`
- positive example: `ProcessingResultSnapshot`
- misuse contrast: lifecycle enum values are not automatically value objects
- misuse contrast: pure ID anti-mixing belongs closer to `Strong ID`
- misuse contrast: JSON storage shape is not the domain definition itself

- [ ] **Step 2: Use snippets only if they materially clarify the authoring shape**

Check the page for:

- concrete directory/object/command references instead of abstract narration only
- if a supporting snippet is used, it stays short, non-runnable, and subordinate to the prose
- any Kotlin-shaped snippet only shows boundary / ownership shape, not framework plumbing or full method bodies

Run:

```powershell
rg -n '^```|ContentTitle|ProcessingResultSnapshot|Strong ID|JSON' docs/public/authoring/examples/content-publication-value-object.zh-CN.md
```

Expected:

- the page still reads as an example narrative first
- any snippet use is clearly bounded and directly tied to the positive example or misuse boundary

- [ ] **Step 3: Add the practical example backlink from `advanced/value-object.zh-CN.md`**

Add one short “对应示例” style link to:

- `../examples/content-publication-value-object.zh-CN.md`

Expected result:

- concept page stays conceptual
- example page becomes the deep-link citation target

- [ ] **Step 4: Verify the page satisfies the issue’s minimum content**

Run:

```powershell
rg -n '^## ' docs/public/authoring/examples/content-publication-value-object.zh-CN.md
rg -n '正例|误用|边界|默认路径|审计' docs/public/authoring/examples/content-publication-value-object.zh-CN.md
```

Expected:

- the six required headings exist
- the page clearly contains a positive example, a misuse section, a usage boundary, default-path insufficiency explanation, and audit cues

## Task 4: Add the Domain Service practical example page and wire the concept page to it

**Files:**
- Create: `docs/public/authoring/examples/content-publication-domain-service.zh-CN.md`
- Modify: `docs/public/authoring/advanced/domain-service.zh-CN.md`

- [ ] **Step 1: Create the Domain Service example page**

Write the page with these top-level sections:

```markdown
# 内容发布示例：Domain Service
## Scenario
## Why default path is not enough
## Recommended shape
## Non-example / misuse
## Usage boundary
## Audit cues
```

Expected positive example:

- publication eligibility evaluation that needs already-available facts from `Content` and `MediaProcessingTask`

Expected misuse examples:

- orchestration garbage-can service
- cross-aggregate write loophole disguised as a domain service
- cross-time waiting problem incorrectly modeled as a synchronous domain service

- [ ] **Step 2: Add a short supporting shape only if prose alone is still ambiguous**

Allowed examples:

- a tiny decision-service signature
- a small `text` block that shows facts-in / conclusion-out shape

Expected result:

- readers can see what the service boundary looks like
- the page still does not become a service implementation tutorial

- [ ] **Step 3: Make the default-path boundary explicit**

The page must clearly separate:

- what still belongs inside aggregate behavior
- what can be expressed as a domain conclusion
- what should escalate to `Saga` instead

Run:

```powershell
rg -n 'Saga|聚合|编排|资格|等待|恢复' docs/public/authoring/examples/content-publication-domain-service.zh-CN.md
```

Expected:

- the page explicitly distinguishes domain service from aggregate behavior, application orchestration, and Saga

- [ ] **Step 4: Add the practical example backlink from `advanced/domain-service.zh-CN.md`**

Add one short example link to:

- `../examples/content-publication-domain-service.zh-CN.md`

- [ ] **Step 5: Verify the page keeps callback and polling normalized before domain judgment**

Run:

```powershell
rg -n 'callback|polling|统一|内部事实|领域判断' docs/public/authoring/examples/content-publication-domain-service.zh-CN.md
```

Expected:

- the page clearly says callback and polling differences must be flattened before the domain service consumes the facts

## Task 5: Add the Saga practical example page and wire the concept page to it

**Files:**
- Create: `docs/public/authoring/examples/content-publication-saga.zh-CN.md`
- Modify: `docs/public/authoring/advanced/saga.zh-CN.md`

- [ ] **Step 1: Create the Saga example page**

Write the page with these top-level sections:

```markdown
# 内容发布示例：Saga
## Scenario
## Why default path is not enough
## Recommended shape
## Non-example / misuse
## Usage boundary
## Audit cues
```

Expected positive example shape:

- a controlled extension of the same content-publication project
- waiting across time for more than one stage
- explicit recovery / timeout / compensation reasoning
- final consistency rather than one-command completion

Expected misuse examples:

- using Saga for a short callback-to-command chain
- turning Saga into a super handler
- promoting polling fallback into the primary flow truth source

- [ ] **Step 2: Add a short stage / recovery sketch only if it helps explain the cross-time boundary**

Allowed examples:

- a small `text` stage flow
- a short non-runnable state skeleton that clarifies waiting / timeout / compensation ownership

Expected result:

- the page makes the long-running boundary concrete
- the page still does not imply a runtime Saga framework feature
- [ ] **Step 3: Verify the page does not claim the current default publication chain already needs Saga**

Run:

```powershell
rg -n '默认链路|默认路径|仍然不用 Saga|受控扩展' docs/public/authoring/examples/content-publication-saga.zh-CN.md
```

Expected:

- the page clearly frames Saga as a controlled expansion of the reference project, not as the default publication model

- [ ] **Step 4: Add the practical example backlink from `advanced/saga.zh-CN.md`**

Add one short example link to:

- `../examples/content-publication-saga.zh-CN.md`

- [ ] **Step 5: Verify wrapper is not accidentally promoted**

Run:

```powershell
rg -n 'wrapper|Agg' docs/public/authoring/examples/content-publication-saga.zh-CN.md docs/public/authoring/examples/content-publication-domain-service.zh-CN.md docs/public/authoring/examples/content-publication-value-object.zh-CN.md
```

Expected:

- either no wrapper mention appears
- or any mention stays clearly non-core, off-default-path, and not-recommended

## Task 6: Run the documentation regression checks and confirm no forbidden surfaces changed

**Files:**
- No new production files beyond Tasks 1-5

- [ ] **Step 1: Verify the new and edited pages exist in the expected set**

Run:

```powershell
Get-ChildItem docs/public/authoring/examples/advanced-concepts-overview.zh-CN.md
Get-ChildItem docs/public/authoring/examples/content-publication-value-object.zh-CN.md
Get-ChildItem docs/public/authoring/examples/content-publication-domain-service.zh-CN.md
Get-ChildItem docs/public/authoring/examples/content-publication-saga.zh-CN.md
```

Expected:

- all four files exist

- [ ] **Step 2: Verify each concept example page has the six-section shape**

Run:

```powershell
rg -n '^## ' docs/public/authoring/examples/content-publication-value-object.zh-CN.md docs/public/authoring/examples/content-publication-domain-service.zh-CN.md docs/public/authoring/examples/content-publication-saga.zh-CN.md
```

Expected:

- each page contains:
  - `Scenario`
  - `Why default path is not enough`
  - `Recommended shape`
  - `Non-example / misuse`
  - `Usage boundary`
  - `Audit cues`

- [ ] **Step 3: Verify the expected advanced/example links were added**

Run:

```powershell
rg -n 'advanced-concepts-overview|content-publication-value-object|content-publication-domain-service|content-publication-saga' docs/public/authoring/advanced docs/public/authoring/examples/reference-project-overview.zh-CN.md
```

Expected:

- `advanced/index` links to the overview page
- each advanced concept page links to its example page
- `reference-project-overview` links to the advanced concept example chain

- [ ] **Step 4: Verify forbidden `#22` surfaces were not touched**

Run:

```powershell
git diff --name-only | rg 'docs/public/reference/generator-dsl\\.zh-CN\\.md|docs/public/authoring/generation-boundaries\\.zh-CN\\.md|docs/public/authoring/generator/code-generation\\.zh-CN\\.md|docs/superpowers/capability-matrix\\.md'
```

Expected:

- no matches

- [ ] **Step 5: Verify overall changed file set stays within the approved doc scope**

Run:

```powershell
git diff --name-only
```

Expected:

- only the planned doc files plus the spec/plan docs themselves appear

## Self-Review

### 1. Scope check

- wrapper cleanup: not planned
- generator DSL reference rewrite: not planned
- generation-boundaries rewrite: not planned
- Default Happy Path rewrite: not planned
- runtime Saga feature work: not planned
- English parity: not planned

### 2. Content check

- each concept gets one positive example
- each concept gets one non-example
- each concept gets a usage boundary
- each concept explains why the default path is not enough
- each concept gives reviewer-facing audit cues

### 3. Future strengthening intentionally left out

Still future work after this slice:

- English translations for the new advanced concept example chain
- larger runnable walkthrough examples, if the public examples subtree is later expanded in that direction
- deeper cross-linking from future `#27` and `#17` materials once those pages exist
