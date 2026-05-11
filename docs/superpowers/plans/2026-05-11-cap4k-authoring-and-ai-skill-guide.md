# Cap4k Authoring And AI Skill Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate human-facing authoring docs under `docs/public/authoring`, and add a self-contained repo-local AI authoring skill for cap4k project work.

**Architecture:** Human guidance remains public documentation under one authoring tree. AI guidance is a repo-local skill with a thin `SKILL.md`, stable rules in `rules/`, procedural workflows in `workflows/`, and lower-frequency material in `references/`; the skill is self-contained at runtime and does not require agents to load public docs or example repositories.

**Tech Stack:** Markdown documentation, Codex/agent skill folder conventions, Git, PowerShell validation commands.

---

## Repository Scope

Work only in:

```text
C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k
```

Do not modify framework runtime, generator, Gradle plugin, test fixture, or reference-project code.

## File Map

### Human Authoring Docs

- Move: `docs/public/getting-started.zh-CN.md` -> `docs/public/authoring/getting-started.zh-CN.md`
- Move: `docs/public/getting-started.md` -> `docs/public/authoring/getting-started.md`
- Move: `docs/public/framework-positioning.zh-CN.md` -> `docs/public/authoring/framework-positioning.zh-CN.md`
- Move: `docs/public/framework-positioning.md` -> `docs/public/authoring/framework-positioning.md`
- Modify: `docs/public/authoring/index.zh-CN.md`
- Modify: `docs/public/authoring/index.md`
- Modify: `README.zh-CN.md`
- Modify: `README.md`

### AI Authoring Skill

- Create: `.agents/skills/cap4k-authoring/SKILL.md`
- Create: `.agents/skills/cap4k-authoring/rules/role-boundary.md`
- Create: `.agents/skills/cap4k-authoring/rules/layering-and-tactical-model.md`
- Create: `.agents/skills/cap4k-authoring/rules/generator-ownership.md`
- Create: `.agents/skills/cap4k-authoring/rules/verification-contract.md`
- Create: `.agents/skills/cap4k-authoring/workflows/design-before-code.md`
- Create: `.agents/skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md`
- Create: `.agents/skills/cap4k-authoring/workflows/review-generated-output.md`
- Create: `.agents/skills/cap4k-authoring/workflows/close-task-with-evidence.md`
- Create: `.agents/skills/cap4k-authoring/references/gotchas.md`
- Create: `.agents/skills/cap4k-authoring/references/public-tactical-model.md`
- Create: `.agents/skills/cap4k-authoring/references/known-gaps.md`
- Create: `.agents/skills/cap4k-authoring/references/issue-lifecycle.md`
- Modify: `AGENTS.md`

---

### Task 1: Move Public Entry Docs Into Authoring

**Files:**
- Move: `docs/public/getting-started.zh-CN.md`
- Move: `docs/public/getting-started.md`
- Move: `docs/public/framework-positioning.zh-CN.md`
- Move: `docs/public/framework-positioning.md`
- Modify: `docs/public/authoring/getting-started.zh-CN.md`
- Modify: `docs/public/authoring/getting-started.md`
- Modify: `docs/public/authoring/framework-positioning.zh-CN.md`
- Modify: `docs/public/authoring/framework-positioning.md`

- [ ] **Step 1: Move the four public entry files with Git history**

Run from the repository root:

```powershell
git mv docs/public/getting-started.zh-CN.md docs/public/authoring/getting-started.zh-CN.md
git mv docs/public/getting-started.md docs/public/authoring/getting-started.md
git mv docs/public/framework-positioning.zh-CN.md docs/public/authoring/framework-positioning.zh-CN.md
git mv docs/public/framework-positioning.md docs/public/authoring/framework-positioning.md
```

Expected: `git status --short` shows four renamed files, not delete/add pairs unless Git reports them that way after content edits.

- [ ] **Step 2: Fix moved Chinese quick-start links**

In `docs/public/authoring/getting-started.zh-CN.md`, make these link targets relative to the new location:

```markdown
[English](getting-started.md)

1. [README.zh-CN.md](../../../README.zh-CN.md)
2. 先用下面的最小工作流跑一个小聚合片段
3. 需要更清楚的概念边界时，再读 [框架定位](framework-positioning.zh-CN.md)

- [框架定位](framework-positioning.zh-CN.md)
- [编写指南总览](index.zh-CN.md)
- [Default Happy Path](default-happy-path.zh-CN.md)
- [测试合同](testing-contract.zh-CN.md)
```

Expected: the moved file no longer links to `authoring/...` from inside `docs/public/authoring`.

- [ ] **Step 3: Fix moved Chinese framework-positioning links**

In `docs/public/authoring/framework-positioning.zh-CN.md`, make the ending links:

```markdown
[English](framework-positioning.md)

- [编写指南总览](index.zh-CN.md)
- [Default Happy Path](default-happy-path.zh-CN.md)
```

Expected: no links in this file point to `authoring/index.zh-CN.md` or `authoring/default-happy-path.zh-CN.md`.

- [ ] **Step 4: Fix moved English quick-start links**

In `docs/public/authoring/getting-started.md`, make equivalent English links:

```markdown
[中文](getting-started.zh-CN.md)

1. [README.md](../../../README.md)
2. Run the minimal workflow below on one aggregate slice.
3. Read [Framework Positioning](framework-positioning.md) when the concept boundaries need to be explicit.

- [Framework Positioning](framework-positioning.md)
- [Authoring Guide Overview](index.md)
- [Default Happy Path](default-happy-path.md)
```

If the current English page has shorter wording, keep that wording but use the link targets above.

- [ ] **Step 5: Fix moved English framework-positioning links**

In `docs/public/authoring/framework-positioning.md`, make equivalent English links:

```markdown
[中文](framework-positioning.zh-CN.md)

- [Authoring Guide Overview](index.md)
- [Default Happy Path](default-happy-path.md)
```

Expected: moved English files link within authoring or back to the root README with `../../../`.

- [ ] **Step 6: Validate root docs are gone and moved links are local**

Run:

```powershell
Test-Path docs/public/getting-started.zh-CN.md
Test-Path docs/public/getting-started.md
Test-Path docs/public/framework-positioning.zh-CN.md
Test-Path docs/public/framework-positioning.md
rg -n "authoring/(index|default-happy-path|testing-contract|framework-positioning|getting-started)" docs/public/authoring/getting-started*.md docs/public/authoring/framework-positioning*.md
```

Expected:

- the four `Test-Path` commands print `False`
- the `rg` command has no output; links inside authoring should be local file links

### Task 2: Update Authoring Indexes And README Navigation

**Files:**
- Modify: `docs/public/authoring/index.zh-CN.md`
- Modify: `docs/public/authoring/index.md`
- Modify: `README.zh-CN.md`
- Modify: `README.md`

- [ ] **Step 1: Update Chinese authoring index reading path**

In `docs/public/authoring/index.zh-CN.md`, change the project-author reading path to include positioning and quick start first:

```markdown
### 项目作者

1. [框架定位](framework-positioning.zh-CN.md)
2. [快速开始](getting-started.zh-CN.md)
3. [Default Happy Path](default-happy-path.zh-CN.md)
4. [生成器指南](generator/index.zh-CN.md)
5. [领域层指南](domain.zh-CN.md)
6. [应用层指南](application.zh-CN.md)
7. [测试合同](testing-contract.zh-CN.md)
8. [适配器层指南](adapter.zh-CN.md)
9. [高级概念指南](advanced/index.zh-CN.md)
```

- [ ] **Step 2: Update Chinese authoring index guide families**

In `docs/public/authoring/index.zh-CN.md`, make the guide list include the moved entry pages:

```markdown
## 主题入口

- [框架定位](framework-positioning.zh-CN.md)
- [快速开始](getting-started.zh-CN.md)
- [Default Happy Path](default-happy-path.zh-CN.md)
- [Generator Guide](generator/index.zh-CN.md)
- [Domain Authoring Guide](domain.zh-CN.md)
- [Application Authoring Guide](application.zh-CN.md)
- [Adapter Authoring Guide](adapter.zh-CN.md)
- [Advanced Concepts Guide](advanced/index.zh-CN.md)
```

Keep the existing horizontal-contract links after this section.

- [ ] **Step 3: Update English authoring index**

In `docs/public/authoring/index.md`, mirror the navigation structure:

```markdown
### Project Authors

1. [Framework Positioning](framework-positioning.md)
2. [Getting Started](getting-started.md)
3. [Default Happy Path](default-happy-path.md)
4. [Generator Guide](generator/index.zh-CN.md)
5. [Domain Authoring Guide](domain.zh-CN.md)
6. [Application Authoring Guide](application.zh-CN.md)
7. [Testing Contract](testing-contract.zh-CN.md)
8. [Adapter Authoring Guide](adapter.zh-CN.md)
9. [Advanced Concepts Guide](advanced/index.zh-CN.md)
```

Also rename the guide-family section to `## Guide Entrypoints` and include:

```markdown
- [Framework Positioning](framework-positioning.md)
- [Getting Started](getting-started.md)
- [Default Happy Path](default-happy-path.md)
- [Generator Guide](generator/index.zh-CN.md)
- [Domain Authoring Guide](domain.zh-CN.md)
- [Application Authoring Guide](application.zh-CN.md)
- [Adapter Authoring Guide](adapter.zh-CN.md)
- [Advanced Concepts Guide](advanced/index.zh-CN.md)
```

- [ ] **Step 4: Update Chinese README links**

In `README.zh-CN.md`, replace the "如何开始" links with:

```markdown
1. 先阅读本 README 中的默认 happy path。
2. 然后阅读 [快速开始](docs/public/authoring/getting-started.zh-CN.md)。
3. 在把高级概念或运行时承载面当作默认承诺之前，再阅读 [框架定位](docs/public/authoring/framework-positioning.zh-CN.md)。
```

Replace the document navigation with:

```markdown
- [编写指南总览](docs/public/authoring/index.zh-CN.md)
- [快速开始](docs/public/authoring/getting-started.zh-CN.md)
- [框架定位](docs/public/authoring/framework-positioning.zh-CN.md)
```

- [ ] **Step 5: Update English README links**

In `README.md`, replace the "How to Start" links with:

```markdown
1. Read the default happy path in this README.
2. Continue with [Getting Started](docs/public/authoring/getting-started.md).
3. Read [Framework Positioning](docs/public/authoring/framework-positioning.md) before treating advanced concepts or runtime surfaces as default promises.
```

Replace the document map with:

```markdown
- [Authoring Guide Overview](docs/public/authoring/index.md)
- [Getting Started](docs/public/authoring/getting-started.md)
- [Framework Positioning](docs/public/authoring/framework-positioning.md)
```

- [ ] **Step 6: Validate README and authoring navigation**

Run:

```powershell
rg -n "docs/public/(getting-started|framework-positioning)" README.md README.zh-CN.md docs/public
rg -n "framework-positioning|getting-started" docs/public/authoring/index.md docs/public/authoring/index.zh-CN.md README.md README.zh-CN.md
```

Expected:

- the first `rg` has no output
- the second `rg` shows authoring links only, not root-level public links

- [ ] **Step 7: Commit Tasks 1 and 2**

Run:

```powershell
git add README.md README.zh-CN.md docs/public/authoring docs/public/getting-started.md docs/public/getting-started.zh-CN.md docs/public/framework-positioning.md docs/public/framework-positioning.zh-CN.md
git commit -m "docs: consolidate public authoring entrypoints"
```

Expected: commit succeeds with only README and `docs/public` files changed.

### Task 3: Create The cap4k Authoring Skill Shell

**Files:**
- Create: `.agents/skills/cap4k-authoring/SKILL.md`

- [ ] **Step 1: Create the skill directory**

Run:

```powershell
New-Item -ItemType Directory -Force .agents/skills/cap4k-authoring/rules
New-Item -ItemType Directory -Force .agents/skills/cap4k-authoring/workflows
New-Item -ItemType Directory -Force .agents/skills/cap4k-authoring/references
```

Expected: the three subdirectories exist.

- [ ] **Step 2: Create `SKILL.md` with trigger routing**

Create `.agents/skills/cap4k-authoring/SKILL.md`:

```markdown
---
name: cap4k-authoring
description: >
  Use this when working on cap4k-based project authoring, AI-assisted DDD implementation,
  generated-vs-handwritten ownership, cap4k tactical modeling, or requests such as
  "build a cap4k project", "write cap4k application/domain code", "review cap4k generated output",
  "apply the cap4k testing contract", or "update cap4k issue evidence". Activate when the task
  involves cap4k project code, docs, specs, plans, generator output, or final verification before
  human audit.
---

# Cap4k Authoring

Use this skill to help an AI author implement and review cap4k-based project work without loading public docs or example repositories as runtime context.

## When To Use

- Building or changing a cap4k project slice
- Deciding where command, query, cli, domain event, integration event, factory, repository, or domain service code belongs
- Reviewing generated output, template overrides, conflict policy, or `src-generated` snapshots
- Preparing final evidence before human review
- Updating issue status after spec, plan, implementation, or verification

## When Not To Use

- Generic Kotlin, Spring, or DDD explanations not tied to cap4k
- Framework runtime implementation inside cap4k itself unless the issue explicitly targets project-authoring rules
- Pure frontend work with no cap4k boundary

## Always Read

1. [rules/role-boundary.md](rules/role-boundary.md)
2. [rules/layering-and-tactical-model.md](rules/layering-and-tactical-model.md)
3. [rules/generator-ownership.md](rules/generator-ownership.md)

## Common Tasks

| Task | Read | Workflow |
|---|---|---|
| Clarify a requested cap4k change before code | `rules/role-boundary.md`, `references/public-tactical-model.md`, `references/known-gaps.md` | `workflows/design-before-code.md` |
| Implement a cap4k project slice | `rules/layering-and-tactical-model.md`, `rules/generator-ownership.md`, `rules/verification-contract.md`, `references/gotchas.md` | `workflows/implement-cap4k-project-slice.md` |
| Review generated output or template overrides | `rules/generator-ownership.md`, `references/gotchas.md` | `workflows/review-generated-output.md` |
| Finish work before human audit | `rules/verification-contract.md`, `references/issue-lifecycle.md`, `references/known-gaps.md` | `workflows/close-task-with-evidence.md` |

## Priority

1. Current user instruction
2. Active GitHub issue and latest approved spec/plan
3. This skill's rules
4. Existing repository patterns

When a framework capability is missing, record the gap instead of implying support.
```

- [ ] **Step 3: Validate skill shell line count**

Run:

```powershell
(Get-Content .agents/skills/cap4k-authoring/SKILL.md).Count
```

Expected: result is under `100`.

### Task 4: Add Stable Skill Rules

**Files:**
- Create: `.agents/skills/cap4k-authoring/rules/role-boundary.md`
- Create: `.agents/skills/cap4k-authoring/rules/layering-and-tactical-model.md`
- Create: `.agents/skills/cap4k-authoring/rules/generator-ownership.md`
- Create: `.agents/skills/cap4k-authoring/rules/verification-contract.md`

- [ ] **Step 1: Create role boundary rules**

Create `.agents/skills/cap4k-authoring/rules/role-boundary.md`:

```markdown
# Role Boundary

## Human Author

- Owns domain decisions, final architecture decisions, and final audit.
- Decides whether a missing capability becomes a follow-up issue.
- Reviews whether the implementation still matches the intended business flow.

## AI Author

- Assists decision-making by surfacing tradeoffs and missing information.
- Implements the main change once the direction is approved.
- Runs TDD, compile, generation, analysis, and focused verification before final audit.
- Reports evidence, residual risk, and follow-up gaps plainly.

## Guardrails

- Do not treat AI output as final audit.
- Do not hide missing cap4k support behind local project conventions.
- Do not require runtime loading of public docs or example repositories to use this skill.
```

- [ ] **Step 2: Create layering and tactical model rules**

Create `.agents/skills/cap4k-authoring/rules/layering-and-tactical-model.md`:

```markdown
# Layering And Tactical Model

## Layer Rules

- Domain owns aggregate behavior, entity behavior, value concepts, domain events, and invariant language.
- Application owns command/query/cli handlers, process orchestration, subscribers, and transaction-facing use-case work.
- Adapter owns HTTP, persistence adapters, external callbacks, jobs, and transport bridges.
- Infrastructure details must not become the public tactical model.

## Write-Side Rules

- Commands own write-side business behavior.
- A command should normally mutate one aggregate root.
- Repository, factory, domain service, and unit of work usage belongs in command handling.
- Use `Mediator.cmd` for command dispatch and `Mediator.uow` for unit-of-work execution when persistence is involved.

## Orchestration Rules

- Process orchestration may use command, query, and cli boundaries.
- Domain-event and integration-event subscribers are orchestration entry points when they continue a business process.
- A subscriber with meaningful business work should use a semantic handler method name, not a generic default name.
- Command handlers should not use queries unless a fresh issue explicitly accepts that exception.
- Command handlers should avoid cli calls unless the command depends on an external capability result.
```

- [ ] **Step 3: Create generator ownership rules**

Create `.agents/skills/cap4k-authoring/rules/generator-ownership.md`:

```markdown
# Generator Ownership

## Ownership Classes

- Generated source is owned by the generator and participates in compile when configured that way.
- Handwritten source is owned by the project author.
- `src-generated/main/kotlin` is a copied snapshot for audit and learning, not the active generated output path.
- Template overrides are project-owned customizations of generator behavior.

## Conflict Policy

- Handler and factory skeletons should normally use `SKIP` conflict policy.
- Regenerating should not overwrite project-owned business logic.
- If a generated artifact has no business purpose in the current slice, disable or remove it instead of keeping noise.

## Input Sources

- DB input should use supported database annotations and naming contracts instead of hardcoded tactical metadata.
- Design input should drive commands, queries, cli, domain events, and future supported tactical contracts.
- Missing design support for a tactical concept must be recorded as a gap.

## Addon And SPI

- Addon-generated artifacts must behave like built-in artifacts from the project user's perspective.
- Project template overrides and conflict policies must work the same for built-in and addon artifacts.
```

- [ ] **Step 4: Create verification contract rules**

Create `.agents/skills/cap4k-authoring/rules/verification-contract.md`:

```markdown
# Verification Contract

## Required Evidence

- Run focused domain/application behavior tests when behavior changes.
- Run compile for modules touched by generated or handwritten Kotlin.
- Run generation plan/generate commands when generator config or generated output changes.
- Run analysis plan/generate commands when analysis configuration or output changes.
- Run link/path scans when public docs move.

## Reporting

- Report exact commands and outcomes.
- Separate blocking failures from known unrelated fixture debt.
- List residual risks and follow-up gaps.

## Prohibited Claims

- Do not claim completion without command evidence.
- Do not claim a framework feature exists because a local workaround exists.
- Do not close an issue unless the issue's acceptance criteria and downstream checks are satisfied.
```

- [ ] **Step 5: Validate rules avoid external runtime dependencies**

Run:

```powershell
rg -n "cap4k-reference-content-studio|read docs/public/authoring|load docs/public/authoring|example repositories" .agents/skills/cap4k-authoring/rules
```

Expected: no output, except no match is acceptable with exit code `1`.

### Task 5: Add Skill Workflows

**Files:**
- Create: `.agents/skills/cap4k-authoring/workflows/design-before-code.md`
- Create: `.agents/skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md`
- Create: `.agents/skills/cap4k-authoring/workflows/review-generated-output.md`
- Create: `.agents/skills/cap4k-authoring/workflows/close-task-with-evidence.md`

- [ ] **Step 1: Create design-before-code workflow**

Create `.agents/skills/cap4k-authoring/workflows/design-before-code.md`:

```markdown
# Design Before Code

1. State the business process in command/event/query language.
2. Identify aggregate roots and which command mutates each aggregate.
3. Identify external boundaries: HTTP, callback, job, cli, or integration event.
4. Identify generated artifacts, handwritten artifacts, copied snapshots, and template overrides.
5. Check `references/known-gaps.md` before promising value object, saga, domain service, or integration-event generation.
6. Present a concise design or write a spec when the task is multi-step.
7. Wait for approval before implementation when the user is still discussing direction.

Output should name the layer and tactical object for every meaningful behavior.
```

- [ ] **Step 2: Create implementation workflow**

Create `.agents/skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md`:

```markdown
# Implement Cap4k Project Slice

1. Confirm the active branch/worktree and read the latest approved spec/plan.
2. Inspect existing generated and handwritten ownership before editing.
3. Add or update focused tests before behavior implementation when behavior changes.
4. Apply generation or template changes before handwritten code that depends on them.
5. Keep command handlers responsible for write-side behavior and use `Mediator.uow` for persistence.
6. Keep process orchestration in application subscribers or orchestration surfaces.
7. Keep adapter code as transport/input/output glue.
8. Run focused tests and compile.
9. Run generation or analysis commands if their inputs or outputs changed.
10. Record exact evidence and residual risks.
```

- [ ] **Step 3: Create generated-output review workflow**

Create `.agents/skills/cap4k-authoring/workflows/review-generated-output.md`:

```markdown
# Review Generated Output

1. Identify the source of each changed generated file: DB input, design input, addon, or template override.
2. Check whether the artifact should be active generated output, handwritten source, or copied snapshot.
3. Verify conflict policy for skeleton artifacts that users are expected to edit.
4. Verify template overrides are project-local and do not hide framework gaps.
5. Compare generated package names and file paths with the intended layered model.
6. Remove unused generated artifacts when they add no value to the example or project slice.
7. Run the relevant generator plan and generate tasks.
8. Report changed generated artifacts separately from handwritten logic.
```

- [ ] **Step 4: Create close-task workflow**

Create `.agents/skills/cap4k-authoring/workflows/close-task-with-evidence.md`:

```markdown
# Close Task With Evidence

1. Run `git status --short` and inspect all changed files.
2. Run `git diff --check`.
3. Run focused tests, compile, generation, analysis, or link scans required by the changed files.
4. Search for known forbidden claims from `references/gotchas.md`.
5. Update the governing issue with spec, plan, implementation, verification, and follow-up status when requested or when lifecycle state changed.
6. Summarize:
   - files changed
   - commands run
   - results
   - known gaps
   - whether the issue can continue, merge, or close
```

- [ ] **Step 5: Validate workflows route to known gaps and evidence**

Run:

```powershell
rg -n "known-gaps|evidence|Mediator\.uow|generation|analysis" .agents/skills/cap4k-authoring/workflows
```

Expected: output shows matches in the workflows created above.

### Task 6: Add Skill References

**Files:**
- Create: `.agents/skills/cap4k-authoring/references/gotchas.md`
- Create: `.agents/skills/cap4k-authoring/references/public-tactical-model.md`
- Create: `.agents/skills/cap4k-authoring/references/known-gaps.md`
- Create: `.agents/skills/cap4k-authoring/references/issue-lifecycle.md`

- [ ] **Step 1: Create gotchas reference**

Create `.agents/skills/cap4k-authoring/references/gotchas.md`:

```markdown
# Gotchas

## Runtime Context Bloat

Do not instruct agents to read the public authoring docs or example repositories during normal skill use. Put required rules in this skill.

## Mediator Bypass

Do not inject command/query handlers directly when the intended cap4k path is `Mediator.cmd`, `Mediator.qry`, `Mediator.cli`, or `Mediator.uow`.

## Repository Misplacement

Jobs, controllers, and transport adapters should not directly become business persistence surfaces. Route write behavior through commands and use queries for read-oriented views.

## Generated Snapshot Confusion

`src-generated/main/kotlin` is an audit snapshot when copied into a reference project. It is not necessarily the active generator output directory.

## Skeleton Overwrite

Handler and factory skeletons that are meant to receive handwritten logic should use `SKIP` conflict policy.

## Unsupported Capability Drift

If value object, saga, domain service, or integration-event generation is not supported by the current generator, record that as a gap instead of inventing a local convention and calling it framework behavior.
```

- [ ] **Step 2: Create public tactical model reference**

Create `.agents/skills/cap4k-authoring/references/public-tactical-model.md`:

```markdown
# Public Tactical Model

## Foreground Concepts

- Aggregate root
- Entity
- Command
- Query
- Domain event
- Application orchestration

## Supported Runtime Surfaces To Respect

- Built-in repository and unit of work
- Factory skeletons for aggregate roots
- Behavior files for aggregate-root behavior
- Command, query, cli, and subscriber handlers
- Integration-event transport adapters when present

## Advanced Or Incomplete Concepts

- Value object
- Saga
- Domain service
- Integration event design/generation
- Public tactical model projection
- Addon-provided artifacts

Use advanced concepts only when the business process needs them or the issue explicitly targets them.
```

- [ ] **Step 3: Create known gaps reference**

Create `.agents/skills/cap4k-authoring/references/known-gaps.md`:

```markdown
# Known Gaps

These gaps must be surfaced during design and review. Do not present them as completed framework capabilities.

## Authoring And Generator Gaps

- Value object authoring needs stronger public qualification.
- Value object generator support is not complete.
- Saga authoring needs stronger public qualification.
- Saga generator support is not complete.
- Domain service generator support is not complete.
- Integration event generator support is not complete.
- Design support for integration events needs issue tracking if absent in the current slice.
- Design support for value objects and domain services needs issue tracking if absent in the current slice.

## Modeling Gaps

- Layered model qualification needs continued refinement.
- Public tactical model qualification needs continued refinement.
- Command, query, cli, domain event, integration event, value object, and domain service should eventually be driven by design where supported.

## External Collaboration Gaps

- `drawing_board.json` can become a later integration-event communication surface.
- Addon and SPI guidance exists as a direction, but advanced authoring rules should expand only after real use.
```

- [ ] **Step 4: Create issue lifecycle reference**

Create `.agents/skills/cap4k-authoring/references/issue-lifecycle.md`:

```markdown
# Issue Lifecycle

## Status Updates

Update the governing issue when one of these milestones changes:

- spec written or approved
- plan written or approved
- implementation completed
- verification completed
- downstream reference project validated
- follow-up gap identified

## Close Criteria

An issue can close only when its accepted scope is implemented, verified, and no downstream blocker remains for the issue's stated goal.

## Follow-Up Handling

- Record out-of-scope gaps explicitly.
- Do not close a gap by saying it is "future work" without either linking an issue or stating who owns the next decision.
- If the gap belongs to cap4k public docs, keep it in cap4k.
- If the gap belongs to a downstream project or addon, link that downstream issue from the cap4k issue.
```

- [ ] **Step 5: Validate known gaps cover #17 extension points**

Run:

```powershell
rg -n "Value object|Saga|Domain service|Integration event|drawing_board|Addon|SPI|Layered|Public tactical" .agents/skills/cap4k-authoring/references/known-gaps.md
```

Expected: every listed extension area has a match.

### Task 7: Add Thin AGENTS Routing And Validate Skill Shape

**Files:**
- Modify: `AGENTS.md`
- All `.agents/skills/cap4k-authoring/**`

- [ ] **Step 1: Add a thin AI authoring skill route to `AGENTS.md`**

Add this section after "Continuing Work" or before "Current Planning State":

```markdown
## AI Authoring Skill

When a task involves cap4k project authoring, AI-assisted DDD implementation, generated-vs-handwritten ownership, tactical model placement, testing-contract evidence, or issue lifecycle updates for project-authoring work, use the repo-local skill:

- [.agents/skills/cap4k-authoring/SKILL.md](.agents/skills/cap4k-authoring/SKILL.md)

Keep this file as a routing shell. Do not duplicate the skill's rules here.
```

- [ ] **Step 2: Validate `SKILL.md` is a router, not a full guide**

Run:

```powershell
(Get-Content .agents/skills/cap4k-authoring/SKILL.md).Count
rg -n "^## Common Tasks|rules/|workflows/|references/" .agents/skills/cap4k-authoring/SKILL.md
```

Expected:

- line count is under `100`
- routing entries point one level deep to `rules/`, `workflows/`, and `references/`

- [ ] **Step 3: Validate skill does not depend on public docs or example repositories**

Run:

```powershell
rg -n "cap4k-reference-content-studio|read docs/public/authoring|load docs/public/authoring|example repositories" .agents/skills/cap4k-authoring AGENTS.md
```

Expected: no output, except an explicit prohibition in `references/gotchas.md` is acceptable. If the command finds an instruction to load those sources, rewrite it.

- [ ] **Step 4: Commit Tasks 3 through 7**

Run:

```powershell
git add AGENTS.md .agents/skills/cap4k-authoring
git commit -m "docs: add cap4k authoring agent skill"
```

Expected: commit succeeds with only `AGENTS.md` and `.agents/skills/cap4k-authoring/**` changed.

### Task 8: Final Validation And Issue Evidence

**Files:**
- Existing changed docs and skill files

- [ ] **Step 1: Run whitespace validation**

Run:

```powershell
git diff --check HEAD~2..HEAD
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Verify removed root public docs are not referenced**

Run:

```powershell
rg -n "docs/public/(getting-started|framework-positioning)" README.md README.zh-CN.md docs/public AGENTS.md .agents/skills/cap4k-authoring
```

Expected: no output.

- [ ] **Step 3: Verify root public docs do not exist**

Run:

```powershell
Test-Path docs/public/getting-started.zh-CN.md
Test-Path docs/public/getting-started.md
Test-Path docs/public/framework-positioning.zh-CN.md
Test-Path docs/public/framework-positioning.md
```

Expected: all four commands print `False`.

- [ ] **Step 4: Verify no runtime or generator files changed**

Run:

```powershell
git diff --name-only HEAD~2..HEAD | rg -v "^(README\.md|README\.zh-CN\.md|AGENTS\.md|docs/public/authoring/|\.agents/skills/cap4k-authoring/)"
```

Expected: no output. If the plan file or spec commit is included in the range, adjust the range or accept only `docs/superpowers/` as an additional documentation path.

- [ ] **Step 5: Prepare issue update evidence**

Prepare a concise `#17` issue comment with:

```markdown
Implemented #17-v1 local docs/skill slice.

- Human authoring guidance is now consolidated under `docs/public/authoring`.
- Root-level `getting-started*` and `framework-positioning*` docs were moved into authoring.
- README navigation now points to authoring.
- Added repo-local AI authoring skill under `.agents/skills/cap4k-authoring`.
- Skill is self-contained at runtime and does not require loading public docs or example repositories.
- Known gaps remain explicit in `references/known-gaps.md`.

Validation:
- `git diff --check HEAD~2..HEAD`
- `rg -n "docs/public/(getting-started|framework-positioning)" README.md README.zh-CN.md docs/public AGENTS.md .agents/skills/cap4k-authoring`
- `Test-Path ...` for removed root docs
- `git diff --name-only HEAD~2..HEAD | rg -v ...` to confirm no runtime/generator changes
```

Do not close `#17` until the user confirms the final branch result and any required issue lifecycle action.
