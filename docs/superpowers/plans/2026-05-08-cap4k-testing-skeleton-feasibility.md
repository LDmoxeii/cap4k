# Cap4k Testing Skeleton Feasibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the first-version public testing contract for `cap4k` as a docs-first surface, wire it into the existing Chinese authoring entry points, and keep the implementation strictly limited to `domain` / `application` default testing guidance.

**Architecture:** Implement the accepted `#18` design as one new standalone Chinese authoring page plus two small discoverability edits in `authoring/index.md` and `getting-started.md`. Reuse the `#21` shared reference project and default-path language, make `ApproveContentCmd -> StartMediaProcessingCmd` the default application-level testing seam, and explicitly keep bootstrap, generated artifacts, runtime test-support libraries, and infrastructure-first tests out of the first-version contract.

**Tech Stack:** Markdown docs, existing public authoring navigation, repository-internal Markdown links, manual doc regression checks with `rg`, `git diff`, and `git diff --check`.

---

## File Map

- Create: `docs/public/authoring/testing-contract.md`
  - Standalone Chinese testing contract page that answers what the official default testing boundary is, where it lives, what helper shape is allowed, what is intentionally out of scope, and how `#27` should consume the contract.
- Modify: `docs/public/authoring/index.md`
  - Surface the new testing contract in the authoring overview so project authors and reviewers can discover it without scanning the whole docs tree.
- Modify: `docs/public/getting-started.md`
  - Add the minimal “write domain/application behavior tests before broader infrastructure tests” guidance and link to the new testing contract.

## Content Contract For The New Page

The new `testing-contract.md` page should explicitly answer the four required issue questions.

1. If a testing skeleton exists, it belongs to:
   - framework-authored guidance
   - project-local helper realization
   - not framework-owned runtime test support
2. It should live in:
   - authoring docs first
   - optional thin project-local helpers second
   - not bootstrap
   - not generated artifacts
   - not a heavy runtime helper library
3. It should avoid hiding domain behavior by:
   - keeping tests behavior-first
   - keeping `domain` and `application` responsibilities visible
   - restricting helpers to thin builders / fakes / fixture setup
   - rejecting opaque local DSLs and infra-heavy defaults
4. For `#27`, the default testing boundary should be:
   - `Content` domain behavior
   - `MediaProcessingTask` domain behavior
   - one clear application orchestration seam around `ApproveContentCmd -> StartMediaProcessingCmd`

The page should also explicitly state that these tests are allowed but not part of the first-version official default contract:

- `@SpringBootTest`
- repository / JPA wiring tests
- callback controller tests
- integration listener tests
- polling job tests
- full infrastructure end-to-end tests

## Task 1: Create the standalone testing contract page

**Files:**
- Create: `docs/public/authoring/testing-contract.md`

- [ ] **Step 1: Re-read the current shared reference anchors before writing**

Run:

```powershell
Get-Content docs/public/authoring/examples/reference-project-overview.md
Get-Content docs/public/authoring/examples/content-draft-to-publish.md
Get-Content docs/public/authoring/examples/media-processing-callback.md
Get-Content docs/public/authoring/examples/advanced-concepts-overview.md
```

Expected:

- `Content`, `MediaProcessingTask`, `MediaProcessingCli`, callback-first, polling-fallback language is visible
- `ApproveContentCmd -> StartMediaProcessingCmd` is clearly established as the shared default handoff
- no alternate sample universe needs to be invented for the testing contract

- [ ] **Step 2: Create `testing-contract.md` with the final heading structure**

Write this exact top-level skeleton:

```markdown
# 测试合同

## 何时阅读本页
## 这份合同解决什么问题
## 默认边界
## 共享参考锚点
## 默认测试形态
## 允许的薄 helper
## 非默认但允许存在的测试
## 与 runnable reference project 的关系
## 审计线索
```

Expected:

- the page is a standalone contract page, not a subsection inside another file
- the structure reads like an authoring contract, not like a framework feature advertisement

- [ ] **Step 3: Fill the opening sections with the required boundary statements**

Write the first four sections so they contain these exact minimum ideas:

```markdown
## 何时阅读本页
- 当你已经按 Default Happy Path 确定 `domain` / `application` 主链路，并准备给项目建立第一版默认测试形态时，读本页。
- 如果你还没有先讲清 `Content`、`MediaProcessingTask`、`MediaProcessingCli`、callback 主路径和 polling 备用路径，先回到参考项目总览和默认主路径相关页面。

## 这份合同解决什么问题
- 不让 runnable reference project 自己发明一套测试形态。
- 不让测试骨架掩盖领域行为。
- 不让框架魔法把作者和 AI 带偏。

## 默认边界
- 第一版官方默认只塑形 `domain` 行为测试与 `application` 编排测试。
- `bootstrap` 不生成测试骨架。
- `cap4k` 不提供 built-in heavy testing DSL。
- `adapter / persistence / integration` 可以测试，但不属于第一版统一骨架。

## 共享参考锚点
- 统一参考项目仍然是 `Content`、`MediaProcessingTask`、`MediaProcessingCli`、callback 主路径、polling 备用路径。
- 默认 application 测试交接缝是 `ApproveContentCmd -> StartMediaProcessingCmd`。
```

Expected:

- the page answers “what layer owns this” and “where does it live” without ambiguity
- bootstrap, generated artifacts, and runtime helper ownership are all explicitly rejected

- [ ] **Step 4: Fill the behavior shape, helper, non-default, and review sections**

Write the remaining sections so they contain these minimum points:

```markdown
## 默认测试形态
- `domain` 测试直接暴露规则、状态推进、拒绝条件。
- `application` 测试直接暴露命令编排、端口调用和 `ApproveContentCmd -> StartMediaProcessingCmd` 交接缝。
- 主断言优先是前置事实、触发动作、业务结果，而不是容器启动。

## 允许的薄 helper
- 允许：test data builder、fake port、少量 fixture setup。
- 不允许：把业务语义折叠成 opaque DSL；把 runtime wiring 偷带进默认测试路径。

## 非默认但允许存在的测试
- `@SpringBootTest`
- repository / JPA wiring
- callback controller / integration listener
- polling job
- full infra end-to-end
- 这些测试不是被禁止，而是第一版 testing contract 不统一塑形它们。

## 与 runnable reference project 的关系
- `#27` 只负责示范这份合同，不重新定义规则。
- 最少应展示 `Content`、`MediaProcessingTask` 和 `ApproveContentCmd -> StartMediaProcessingCmd` 的 application 样本。

## 审计线索
- 看测试是否先把前置事实、触发动作、业务结果讲清。
- 看 `domain` 测试是否依赖重容器。
- 看 `application` 测试是否掉回 runtime wiring。
- 看 helper 是不是让业务更清楚，而不是更模糊。
```

Expected:

- the page clearly answers the “how do we avoid hiding domain behavior” question
- `#27` is framed as a consumer of the contract, not the source of the contract

- [ ] **Step 5: Add explicit source links back to the default-path docs**

In prose, link the new page back to these existing docs:

- `default-happy-path.md`
- `examples/reference-project-overview.md`
- `examples/content-draft-to-publish.md`
- `examples/media-processing-callback.md`

Expected:

- the testing contract obviously reuses the `#21` shared reference project
- reviewers can jump from the testing contract back to the exact authoring seams it references

- [ ] **Step 6: Run focused content verification on the new page**

Run:

```powershell
rg -n 'Content|MediaProcessingTask|MediaProcessingCli|ApproveContentCmd|StartMediaProcessingCmd|callback|polling|@SpringBootTest|JPA|fake port|builder|审计' docs/public/authoring/testing-contract.md
```

Expected:

- all shared reference anchors are present
- the non-default test list is present
- thin local helper examples are present
- audit cues are present

- [ ] **Step 7: Commit the standalone contract page**

Run:

```powershell
git add docs/public/authoring/testing-contract.md
git commit -m "docs: add testing contract"
```

Expected:

- one docs-only commit exists for the standalone contract page

## Task 2: Wire the testing contract into the authoring overview

**Files:**
- Modify: `docs/public/authoring/index.md`

- [ ] **Step 1: Re-read the current authoring overview before editing**

Run:

```powershell
Get-Content docs/public/authoring/index.md
```

Expected:

- the page currently exposes six major topics and three cross-cutting specs
- there is no testing-contract entry yet

- [ ] **Step 2: Add the testing contract to the project-author reading path**

Update the `## 阅读路径` section so the `### 项目作者` list becomes:

```markdown
1. [Default Happy Path](default-happy-path.md)
2. [生成器指南](generator/index.md)
3. [领域层指南](domain.md)
4. [应用层指南](application.md)
5. [测试合同](testing-contract.md)
6. [适配器层指南](adapter.md)
7. [高级概念指南](advanced/index.md)
```

Expected:

- the testing contract sits after `application` and before `adapter`
- the reading path reflects that first-version official test guidance targets `domain` / `application`

- [ ] **Step 3: Add the testing contract under `## 横切规范`**

Update the cross-cutting list so it contains:

```markdown
- [命名与目录规范](naming-and-layout.md)
- [生成 / 手写边界](generation-boundaries.md)
- [示例合同](example-contract.md)
- [测试合同](testing-contract.md)
```

Expected:

- the page exposes testing as a cross-cutting authoring contract, not as a runtime feature

- [ ] **Step 4: Verify the overview diff is narrow**

Run:

```powershell
git diff -- docs/public/authoring/index.md
```

Expected:

- only the reading-path insertion and cross-cutting-spec link addition appear
- no unrelated ordering or copy rewrites are introduced

- [ ] **Step 5: Commit the authoring-overview wiring**

Run:

```powershell
git add docs/public/authoring/index.md
git commit -m "docs: surface testing contract in authoring index"
```

Expected:

- one small docs-only commit exists for the overview wiring

## Task 3: Add the minimal getting-started testing guidance

**Files:**
- Modify: `docs/public/getting-started.md`

- [ ] **Step 1: Re-read the current getting-started flow**

Run:

```powershell
Get-Content docs/public/getting-started.md
```

Expected:

- the page currently defines the minimal workflow and next-step reading
- it does not yet tell authors when to add default domain/application tests

- [ ] **Step 2: Add one testing-focused step to `## 最小工作流`**

Insert this exact new step after the current step about controller/job/event-handler collaboration:

```markdown
6. 先为 `domain` 和 `application` 主链路补行为测试，再决定是否需要更重的基础设施测试
```

Expected:

- the minimal workflow now says “behavior tests first” without promising generated skeletons or runtime helpers

- [ ] **Step 3: Extend `## 下一步阅读` with the testing contract**

Update the section so it contains:

```markdown
- [框架定位](framework-positioning.md)
- [编写指南总览](authoring/index.md)
- [Default Happy Path](authoring/default-happy-path.md)
- [测试合同](authoring/testing-contract.md)
```

Expected:

- users who start from `getting-started` can discover the new contract immediately

- [ ] **Step 4: Verify no unwanted promises were added**

Run:

```powershell
rg -n '测试|test|bootstrap|生成|DSL|runtime|SpringBootTest' docs/public/getting-started.md
```

Expected:

- the page now mentions testing
- it still does not promise bootstrap-generated tests or runtime-owned test helpers

- [ ] **Step 5: Commit the getting-started update**

Run:

```powershell
git add docs/public/getting-started.md
git commit -m "docs: add default testing path to getting started"
```

Expected:

- one small docs-only commit exists for the getting-started update

## Task 4: Run docs regression checks and verify the implementation stayed inside the approved boundary

**Files:**
- Verify: `docs/public/authoring/testing-contract.md`
- Verify: `docs/public/authoring/index.md`
- Verify: `docs/public/getting-started.md`

- [ ] **Step 1: Run a focused reference-anchor check across all changed files**

Run:

```powershell
rg -n 'Content|MediaProcessingTask|ApproveContentCmd|StartMediaProcessingCmd|callback|polling|测试合同' docs/public/authoring/testing-contract.md docs/public/authoring/index.md docs/public/getting-started.md
```

Expected:

- the new contract is referenced from both entry surfaces
- the contract page clearly reuses the shared reference project anchors

- [ ] **Step 2: Run a forbidden-expansion scan**

Run:

```powershell
rg -n 'cap4k-test|generated test|bootstrap.*test|runtime test-support|test-support artifact|JUnit extension|Kotest extension' docs/public/authoring/testing-contract.md docs/public/authoring/index.md docs/public/getting-started.md
```

Expected:

- no result, or only explicit negative wording that rejects these ideas
- there is no accidental promise of a new framework-owned testing product

- [ ] **Step 3: Check the final diff and whitespace hygiene**

Run:

```powershell
git diff -- docs/public/authoring/testing-contract.md docs/public/authoring/index.md docs/public/getting-started.md
git diff --check
```

Expected:

- only the three intended files changed
- no trailing-whitespace or malformed-patch warnings appear

- [ ] **Step 4: Confirm the repository is ready for review**

Run:

```powershell
git status --short
git log --oneline -n 3
```

Expected:

- only the intended docs commits appear
- the worktree is otherwise clean

- [ ] **Step 5: Prepare the review summary**

Summarize these exact outcomes for the reviewer:

- which public pages changed
- that the new testing contract is docs-first and Chinese-only in this slice
- that bootstrap / generated artifacts / runtime helper ownership were explicitly rejected
- that the official default boundary stops at `domain` / `application`
- that `#27` is positioned as a consumer of the contract, not its source

Expected:

- the review handoff answers the core `#18` feasibility questions without re-explaining the whole spec
