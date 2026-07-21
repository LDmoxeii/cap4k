# Cap4k Business Authoring Guidance Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework `#17` human authoring docs and AI authoring skill so they teach business-project authoring with cap4k, not cap4k framework maintenance.

**Architecture:** Use the seven `docs/superpowers/analysis/2026-05-11-cap4k-*.md` files as internal source material. Public docs become the human decision/audit path under `docs/public/authoring`; `skills/cap4k-authoring` becomes a self-contained AI business-project authoring skill with concise routing, stable rules, task workflows, and low-frequency references.

**Tech Stack:** Markdown docs, repo-local Codex skill folders, PowerShell validation, Git.

---

## Repository Scope

Work in:

```text
C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
```

Do not modify:

- runtime modules such as `ddd-core`, `ddd-domain-repo-jpa`, or integration-event modules;
- pipeline generator/plugin code;
- `cap4k-reference-content-studio`;
- downstream repositories.

## Source Material

Use these files as the implementation source of truth:

```text
docs/superpowers/analysis/2026-05-11-cap4k-business-project-authoring-capability-map.md
docs/superpowers/analysis/2026-05-11-cap4k-bootstrap-plugin-and-template-map.md
docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md
docs/superpowers/analysis/2026-05-11-cap4k-public-tactical-model-and-layering-map.md
docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md
docs/superpowers/analysis/2026-05-11-cap4k-testing-analysis-and-flow-map.md
docs/superpowers/analysis/2026-05-11-cap4k-extension-spi-addon-and-gap-map.md
```

Do not tell the final AI skill to load these analysis files during normal business-project use. They are implementation source material for this rewrite.

## File Map

### Human authoring docs

- Create: `docs/public/authoring/project-authoring-workflow.md`
- Create: `docs/public/authoring/tactical-model.md`
- Create: `docs/public/authoring/generator/input-sources.md`
- Create: `docs/public/authoring/generator/addons-and-spi.md`
- Modify: `docs/public/authoring/index.md`
- Modify: `docs/public/authoring/getting-started.md`
- Modify: `docs/public/authoring/framework-positioning.md`
- Modify: `docs/public/authoring/domain.md`
- Modify: `docs/public/authoring/application.md`
- Modify: `docs/public/authoring/adapter.md`
- Modify: `docs/public/authoring/generation-boundaries.md`
- Modify: `docs/public/authoring/testing-contract.md`
- Modify: `docs/public/authoring/generator/index.md`
- Modify: `docs/public/authoring/generator/bootstrap.md`
- Modify: `docs/public/authoring/generator/code-generation.md`
- Modify: `docs/public/authoring/generator/code-analysis.md`

### AI business author skill

- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify: `skills/cap4k-authoring/rules/role-boundary.md`
- Modify: `skills/cap4k-authoring/rules/layering-and-tactical-model.md`
- Modify: `skills/cap4k-authoring/rules/generator-ownership.md`
- Create: `skills/cap4k-authoring/rules/runtime-tactical-contract.md`
- Create: `skills/cap4k-authoring/rules/testing-and-verification.md`
- Delete: `skills/cap4k-authoring/rules/verification-contract.md`
- Create: `skills/cap4k-authoring/workflows/clarify-domain-design.md`
- Create: `skills/cap4k-authoring/workflows/bootstrap-minimal-project.md`
- Create: `skills/cap4k-authoring/workflows/generate-from-db.md`
- Create: `skills/cap4k-authoring/workflows/generate-from-design.md`
- Create: `skills/cap4k-authoring/workflows/implement-project-slice.md`
- Create: `skills/cap4k-authoring/workflows/run-analysis-and-flow-review.md`
- Modify: `skills/cap4k-authoring/workflows/review-generated-output.md`
- Delete: `skills/cap4k-authoring/workflows/design-before-code.md`
- Delete: `skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md`
- Delete: `skills/cap4k-authoring/workflows/close-task-with-evidence.md`
- Create: `skills/cap4k-authoring/references/capability-index.md`
- Modify: `skills/cap4k-authoring/references/known-gaps.md`
- Modify: `skills/cap4k-authoring/references/gotchas.md`
- Delete: `skills/cap4k-authoring/references/issue-lifecycle.md`
- Delete: `skills/cap4k-authoring/references/public-tactical-model.md`

### Thin routing shells

- Modify: `AGENTS.md`
- Modify: `.agents/skills/cap4k-authoring/SKILL.md`
- Modify: `.cursor/skills/cap4k-authoring/SKILL.md`

---

### Task 1: Add Human Authoring Capability Pages

**Files:**
- Create: `docs/public/authoring/project-authoring-workflow.md`
- Create: `docs/public/authoring/tactical-model.md`
- Create: `docs/public/authoring/generator/input-sources.md`
- Create: `docs/public/authoring/generator/addons-and-spi.md`

- [ ] **Step 1: Create `project-authoring-workflow.md`**

Write a human-facing workflow page with these sections:

```markdown
# 项目编写工作流

## 读者

## 总流程

## 1. 和用户收敛业务流程

## 2. 形成领域模型、DDL、design

## 3. 先 plan 后 generate

## 4. 区分生成物、骨架、快照、手写代码

## 5. 实现命令、查询、订阅、适配器

## 6. 测试与本地运行

## 7. 生成 analysis 和流程图

## 8. 人类审计

## 保留缺口
```

Required facts:

- human author owns decisions and final audit;
- AI can be main implementer but must provide evidence before audit;
- complex DDD flow should have a readable technical方案 before implementation;
- `src-generated/main/kotlin` is a copied audit snapshot, not active generator output.

- [ ] **Step 2: Create `tactical-model.md`**

Write a tactical-model page with these sections:

```markdown
# 公开战术模型

## 分层责任

## Mediator

## 命令处理器

## 查询和 client/cli 处理器

## 流程编排

## 内置仓储

## 工厂

## 工作单元

## 生命周期

## 规约

## 领域服务

## 领域事件与集成事件

## 不要误用
```

Required facts:

- use static `Mediator.cmd`, `Mediator.qry`, `Mediator.requests`, `Mediator.repositories`, `Mediator.factories`, `Mediator.services`, and `Mediator.uow`;
- command handlers may use repository/factory/domain service/UoW;
- query and client/cli handlers are adapter-side physical handlers by default;
- orchestration can send commands, queries, and client/cli requests;
- lifecycle hooks are `onCreate`, `onUpdate`, `onDelete`;
- only aggregate roots should have factories.

- [ ] **Step 3: Create `generator/input-sources.md`**

Write a generator input page covering:

- DB source;
- DB table annotations;
- DB column annotations;
- DB relation annotations;
- uniqueness naming conventions;
- design JSON supported tags;
- unsupported design tags;
- enum manifest;
- KSP metadata;
- IR analysis source.

Required unsupported design tags:

```text
integration_event
value_object
domain_service
```

- [ ] **Step 4: Create `generator/addons-and-spi.md`**

Write an addon page from the business-project user perspective:

- `cap4kAddon` dependency;
- addon artifacts behave like built-in artifacts;
- template override paths can override addon templates;
- `templateConflictPolicies` can apply to addon template IDs;
- enum translation is addon-owned and not a core aggregate DSL toggle;
- addon author guidance is separate from business-project addon usage.

- [ ] **Step 5: Validate new pages contain capability anchors**

Run:

```powershell
rg -n "Mediator|工作单元|内置仓储|工厂|生命周期|规约|领域服务|集成事件" docs/public/authoring/tactical-model.md
rg -n "integration_event|value_object|domain_service|@GeneratedValue|@Reference|@Enum|唯一|enum manifest|KSP|IR" docs/public/authoring/generator/input-sources.md
rg -n "cap4kAddon|addon|templateConflictPolicies|enum translation|覆盖" docs/public/authoring/generator/addons-and-spi.md
```

Expected: all commands return matches.

### Task 2: Wire Human Authoring Navigation

**Files:**
- Modify: `docs/public/authoring/index.md`
- Modify: `docs/public/authoring/getting-started.md`
- Modify: `docs/public/authoring/framework-positioning.md`
- Modify: `docs/public/authoring/domain.md`
- Modify: `docs/public/authoring/application.md`
- Modify: `docs/public/authoring/adapter.md`
- Modify: `docs/public/authoring/generation-boundaries.md`
- Modify: `docs/public/authoring/testing-contract.md`
- Modify: `docs/public/authoring/generator/index.md`
- Modify: `docs/public/authoring/generator/bootstrap.md`
- Modify: `docs/public/authoring/generator/code-generation.md`
- Modify: `docs/public/authoring/generator/code-analysis.md`

- [ ] **Step 1: Update `index.md`**

Add the new pages into the primary authoring route:

```markdown
- [项目编写工作流](project-authoring-workflow.md)
- [公开战术模型](tactical-model.md)
- [生成输入源](generator/input-sources.md)
- [Addon 与 SPI 使用](generator/addons-and-spi.md)
```

Keep `index.md` navigational; do not paste the full content of the new pages into it.

- [ ] **Step 2: Update generator index**

In `docs/public/authoring/generator/index.md`, add links to:

```markdown
- [输入源：DB / design / enum manifest / KSP / IR](input-sources.md)
- [Addon 与 SPI 使用](addons-and-spi.md)
```

- [ ] **Step 3: Add cross-links from existing pages**

Add targeted links:

- `getting-started.md`: link to project workflow and bootstrap page.
- `framework-positioning.md`: link to tactical model and known gaps in relevant wording.
- `domain.md`: link to tactical model sections for aggregate behavior, factories, lifecycle, specs, and domain services.
- `application.md`: link to tactical model sections for command handler, orchestration, subscribers, and request dispatch.
- `adapter.md`: link to tactical model sections for query/client handlers and adapter boundaries.
- `generation-boundaries.md`: link to generated-output ownership and input-sources page.
- `testing-contract.md`: link to project workflow test/analysis steps.
- `bootstrap.md`: link to project workflow and generator ownership.
- `code-generation.md`: link to input sources and generated-output ownership.
- `code-analysis.md`: link to project workflow analysis step.

- [ ] **Step 4: Validate public navigation**

Run:

```powershell
rg -n "project-authoring-workflow|tactical-model|input-sources|addons-and-spi" docs/public/authoring -g "*.md"
rg -n "docs/public/authoring/project-authoring-workflow|docs/public/authoring/tactical-model" README.md README.md docs/public/authoring -g "*.md"
```

Expected:

- the first command shows links across authoring docs;
- the second command has no matches in authoring docs, because links inside authoring should be relative.

### Task 3: Rebuild The AI Skill Around Business-Project Authoring

**Files:**
- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify/Create/Delete files under `skills/cap4k-authoring/rules`
- Modify/Create/Delete files under `skills/cap4k-authoring/workflows`
- Modify/Create/Delete files under `skills/cap4k-authoring/references`

- [ ] **Step 1: Rewrite `SKILL.md` as a concise router**

`SKILL.md` must state that the skill is for AI authors implementing business projects using cap4k.

Common task routes must include:

- clarify domain/design before code;
- bootstrap a minimal project;
- generate from DB;
- generate from design;
- implement a project slice;
- review generated output;
- run analysis and flow review.

Do not route business-project use through issue lifecycle governance.

- [ ] **Step 2: Rewrite rule files**

Create or rewrite these rules:

```text
rules/role-boundary.md
rules/layering-and-tactical-model.md
rules/generator-ownership.md
rules/runtime-tactical-contract.md
rules/testing-and-verification.md
```

Required corrections:

- human final audit remains outside the AI skill's authority;
- query and client/cli handlers are adapter-side physical handlers by default;
- application owns request contracts, write-side command handling, orchestration, subscribers, validators, and process intent;
- command handlers can use repository/factory/domain service/UoW;
- command handlers should use client/cli only when command result depends on that external capability result;
- jobs and controllers should not become direct business persistence surfaces;
- generated source, checked-in skeleton, copied snapshot, template override, and handwritten code are separate ownership classes.

- [ ] **Step 3: Rewrite workflow files**

Create or rewrite these workflows:

```text
workflows/clarify-domain-design.md
workflows/bootstrap-minimal-project.md
workflows/generate-from-db.md
workflows/generate-from-design.md
workflows/implement-project-slice.md
workflows/review-generated-output.md
workflows/run-analysis-and-flow-review.md
```

Required coverage:

- discuss with user to derive domain model, DDL, design JSON, and technical方案;
- run bootstrap plan before bootstrap;
- run generation plan before generation;
- inspect plan item `generatorId`, `templateId`, `outputPath`, `outputKind`, and `conflictPolicy`;
- use DB annotations and design supported tags correctly;
- run compile/tests;
- run `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` when analysis output is part of the task.

- [ ] **Step 4: Rewrite references**

Create or rewrite:

```text
references/capability-index.md
references/known-gaps.md
references/gotchas.md
```

`capability-index.md` should summarize bootstrap, plugin config, DB/design input, generator output verification, tactical model, integration events, testing, analysis, and addon usage.

`known-gaps.md` must include:

- no design support for `integration_event`;
- no design support for `value_object`;
- no design support for `domain_service`;
- lifecycle recognition limitation;
- enum translation is addon-owned;
- integration event HTTP-JPA table requirement.

`gotchas.md` must contain business-project pitfalls only. Remove skill-maintenance meta sections such as "Runtime Context Bloat", "Thin Shell Drift", and rationalization tables.

- [ ] **Step 5: Delete business-irrelevant old skill files**

Delete:

```text
skills/cap4k-authoring/references/issue-lifecycle.md
skills/cap4k-authoring/references/public-tactical-model.md
skills/cap4k-authoring/rules/verification-contract.md
skills/cap4k-authoring/workflows/design-before-code.md
skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md
skills/cap4k-authoring/workflows/close-task-with-evidence.md
```

- [ ] **Step 6: Validate skill role and rule cleanup**

Run:

```powershell
(Get-Content skills/cap4k-authoring/SKILL.md).Count
rg -n "issue-lifecycle|Runtime Context Bloat|Thin Shell Drift|Rationalizations To Reject|Application owns command/query/cli handlers|Command handlers should not use queries unless" skills/cap4k-authoring .agents/skills/cap4k-authoring .cursor/skills/cap4k-authoring AGENTS.md
rg -n "business projects using cap4k|adapter-side|Mediator\.uow|cap4kAnalysisPlan|cap4kAnalysisGenerate|integration_event|value_object|domain_service" skills/cap4k-authoring
```

Expected:

- `SKILL.md` stays under 100 lines;
- the second command has no matches;
- the third command returns matches.

### Task 4: Update Thin Routing Shells

**Files:**
- Modify: `AGENTS.md`
- Modify: `.agents/skills/cap4k-authoring/SKILL.md`
- Modify: `.cursor/skills/cap4k-authoring/SKILL.md`

- [ ] **Step 1: Update `.agents` and `.cursor` shells**

Keep both files as routing shells only. They should route to the new formal skill tasks:

```markdown
| Task | Read | Workflow |
|---|---|---|
| Clarify domain/design before code | `../../../skills/cap4k-authoring/SKILL.md`, `../../../skills/cap4k-authoring/rules/role-boundary.md` | `../../../skills/cap4k-authoring/workflows/clarify-domain-design.md` |
| Bootstrap a minimal project | `../../../skills/cap4k-authoring/SKILL.md`, `../../../skills/cap4k-authoring/rules/generator-ownership.md` | `../../../skills/cap4k-authoring/workflows/bootstrap-minimal-project.md` |
| Generate from DB or design | `../../../skills/cap4k-authoring/SKILL.md`, `../../../skills/cap4k-authoring/rules/generator-ownership.md` | `../../../skills/cap4k-authoring/workflows/generate-from-db.md` or `../../../skills/cap4k-authoring/workflows/generate-from-design.md` |
| Implement a project slice | `../../../skills/cap4k-authoring/SKILL.md`, `../../../skills/cap4k-authoring/rules/layering-and-tactical-model.md`, `../../../skills/cap4k-authoring/rules/runtime-tactical-contract.md` | `../../../skills/cap4k-authoring/workflows/implement-project-slice.md` |
| Review generated output or analysis | `../../../skills/cap4k-authoring/SKILL.md`, `../../../skills/cap4k-authoring/rules/testing-and-verification.md` | `../../../skills/cap4k-authoring/workflows/review-generated-output.md` or `../../../skills/cap4k-authoring/workflows/run-analysis-and-flow-review.md` |
```

- [ ] **Step 2: Update `AGENTS.md` routing**

If `AGENTS.md` has a cap4k authoring route, update it to the same new workflow names. Do not paste formal business-project rules into `AGENTS.md`.

- [ ] **Step 3: Validate shells**

Run:

```powershell
Get-Content AGENTS.md -TotalCount 60 | rg -n "cap4k-authoring|Task \\| Read \\| Workflow"
rg -n "clarify-domain-design|bootstrap-minimal-project|generate-from-db|generate-from-design|implement-project-slice|run-analysis-and-flow-review" .agents/skills/cap4k-authoring/SKILL.md .cursor/skills/cap4k-authoring/SKILL.md AGENTS.md
```

Expected: shells route to the new workflow names and do not duplicate rule content.

### Task 5: Final Validation

**Files:**
- All changed docs and skill files

- [ ] **Step 1: Check changed path scope**

Run:

```powershell
git diff --name-only | rg -v "^(docs/public/authoring/|skills/cap4k-authoring/|\\.agents/skills/cap4k-authoring/|\\.cursor/skills/cap4k-authoring/|AGENTS\\.md|docs/superpowers/specs/2026-05-11-cap4k-business-authoring-guidance-redesign-design\\.md|docs/superpowers/plans/2026-05-11-cap4k-business-authoring-guidance-redesign\\.md)"
```

Expected: no output.

- [ ] **Step 2: Check whitespace**

Run:

```powershell
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Check public docs capability coverage**

Run:

```powershell
rg -n "项目编写工作流|公开战术模型|输入源|Addon|Mediator|工作单元|内置仓储|cap4kAnalysisPlan|cap4kAnalysisGenerate" docs/public/authoring -g "*.md"
```

Expected: matches in the new and updated authoring docs.

- [ ] **Step 4: Check skill cleanup**

Run:

```powershell
rg -n "issue-lifecycle|Runtime Context Bloat|Thin Shell Drift|Rationalizations To Reject|Application owns command/query/cli handlers|Command handlers should not use queries unless" skills/cap4k-authoring .agents/skills/cap4k-authoring .cursor/skills/cap4k-authoring AGENTS.md
```

Expected: no output.

- [ ] **Step 5: Check known gaps remain visible**

Run:

```powershell
rg -n "integration_event|value_object|domain_service|生命周期|enum translation|HTTP-JPA|cap4kAddon" docs/public/authoring skills/cap4k-authoring -g "*.md"
```

Expected: matches in public docs and skill references.

- [ ] **Step 6: Commit**

Run:

```powershell
git add docs/public/authoring skills/cap4k-authoring .agents/skills/cap4k-authoring .cursor/skills/cap4k-authoring AGENTS.md docs/superpowers/specs/2026-05-11-cap4k-business-authoring-guidance-redesign-design.md docs/superpowers/plans/2026-05-11-cap4k-business-authoring-guidance-redesign.md
git commit -m "docs: redesign business authoring guidance"
```

Expected: commit succeeds with docs and skill files only.
