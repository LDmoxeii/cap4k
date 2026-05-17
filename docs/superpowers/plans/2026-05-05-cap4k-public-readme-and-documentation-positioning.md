# Cap4k Public README And Documentation Positioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the boilerplate public entry docs with a bilingual README surface and a minimal `docs/public/` split that expresses `cap4k` through the `#15` capability-audit baseline.

**Architecture:** Treat this slice as a documentation-entry rewrite, not a framework refactor. Keep the first screen narrow and positioning-driven, move deeper explanation into `docs/public/`, and make both language tracks structurally aligned so future updates do not drift.

**Tech Stack:** Markdown, Git, GitHub issues, repository docs, bilingual public documentation.

---

### Task 1: Replace the boilerplate README entry with the English public entry

**Files:**
- Modify: `README.md`
- Test: `README.md` content verification with `rg`

- [ ] **Step 1: Confirm the current README is still boilerplate**

Run:

```powershell
rg -n "This project uses \[Gradle\]|Gradle Wrapper|suggested multi-module setup" README.md
```

Expected:

- output proves the current `README.md` is still Gradle boilerplate
- no `cap4k` positioning language is present yet

- [ ] **Step 2: Rewrite `README.md` as the English public entry**

Replace the boilerplate with a README shaped like this:

```markdown
# cap4k

[中文文档](README.md)

> cap4k is a simplified DDD tactical framework designed for AI-assisted implementation and human review.

## Mainline

`Aggregate Root -> Entity -> Command / Query -> Domain Event -> Orchestration Surfaces`

## How to Start

1. Read the default happy path in this README.
2. Continue with [Getting Started](docs/public/getting-started.md).
3. Read [Framework Positioning](docs/public/framework-positioning.md) before treating advanced concepts or runtime surfaces as default promises.

## What cap4k Is

- an aggregate-centered DDD tactical framework
- command/query driven by default
- domain-event aware
- designed for AI-assisted implementation and human review
- able to project design, runtime, and generation layers without reducing the framework to code generation alone

## What cap4k Is Not

- not a generic code generator platform
- not a JPA wrapper first and foremost
- not an integration-event platform first and foremost
- not a frontend TypeScript generation framework
- not a framework that places every DDD pattern on the front page equally

## Default Happy Path

- single-command single-aggregate-root mutation
- aggregate root as the write-facing surface
- domain mutation converges into command handling
- domain events are registered and released by aggregate roots
- multiple handlers do not have guaranteed execution order
- `cli` is an anti-corruption boundary rather than the truth source of the process

## Documentation Map

- [Getting Started](docs/public/getting-started.md)
- [Framework Positioning](docs/public/framework-positioning.md)
- repository specs and plans under `docs/superpowers/` for internal design work
```

Implementation constraints:

- keep the first screen short
- do not mention wrapper as a valid public concept
- do not front-page `Value Object`, `Integration Event`, `Domain Service`, `Saga`, or `Strong ID`
- do not promise frontend TypeScript generation or advanced read/write weak-reference modeling as default workflow

- [ ] **Step 3: Verify the new English README sections and boundary language**

Run:

```powershell
rg -n "^# cap4k$|^\[中文文档\]|^## Mainline$|^## How to Start$|^## What cap4k Is$|^## What cap4k Is Not$|^## Default Happy Path$|^## Documentation Map$" README.md
rg -n "frontend TypeScript generation|wrapper|advanced read/write" README.md
```

Expected:

- the first command finds all required sections
- the second command returns no misleading front-page promises

- [ ] **Step 4: Commit the English README rewrite**

Run:

```bash
git add README.md
git commit -m "docs: rewrite English README entry"
```

Expected:

- one docs-only commit exists for the English README rewrite
- no runtime or source-code modules are included

### Task 2: Add the Chinese README entry with the same structure

**Files:**
- Create: `README.md`
- Test: `README.md` content verification with `rg`

- [ ] **Step 1: Create the Chinese README with the same section structure**

Write `README.md` using this structure:

```markdown
# cap4k

[English](README.md)

> cap4k 是一套面向 AI 驱动实现与人工审阅的简化版 DDD 战术框架。

## 主线

`聚合根 -> 实体 -> 命令 / 查询 -> 领域事件 -> 流程入口`

## 如何开始

1. 先阅读本 README 中的默认 happy path。
2. 然后阅读 [快速开始](docs/public/getting-started.md)。
3. 在把高级概念或运行时承载面当作默认承诺之前，再阅读 [框架定位](docs/public/framework-positioning.md)。

## cap4k 是什么

- 一套以聚合为中心的 DDD 战术框架
- 默认以命令 / 查询为主线
- 明确认可领域事件
- 面向 AI 驱动实现与人工审阅
- 能把设计、运行时、生成链路投影到同一框架世界观中，而不是把框架缩减成代码生成器

## cap4k 不是什么

- 不是一个泛化代码生成平台
- 不是一个以 JPA 包装为第一身份的框架
- 不是一个以集成事件平台为第一身份的框架
- 不是一个前端 TypeScript 生成框架
- 不是一个把所有 DDD 模式都同等前置宣传的框架

## 默认 Happy Path

- 单命令只变更一个聚合根
- 聚合根是写入主面
- 所有领域状态变更都收敛到命令处理路径
- 领域事件由聚合根统一登记与发出
- 多处理器执行顺序不承诺
- `cli` 是防腐层边界，不是主流程真相源

## 文档导航

- [快速开始](docs/public/getting-started.md)
- [框架定位](docs/public/framework-positioning.md)
- 仓库内部设计材料见 `docs/superpowers/`
```

Constraints:

- keep headings structurally aligned with `README.md`
- write real Chinese, not literal awkward mirroring
- preserve the same foreground/background concept choices as the English README

- [ ] **Step 2: Verify structural alignment and language navigation**

Run:

```powershell
rg -n "^# cap4k$|^\[English\]|^## 主线$|^## 如何开始$|^## cap4k 是什么$|^## cap4k 不是什么$|^## 默认 Happy Path$|^## 文档导航$" README.md
```

Expected:

- the Chinese README contains the same top-level structure as the English README
- language navigation back to `README.md` exists

- [ ] **Step 3: Commit the Chinese README entry**

Run:

```bash
git add README.md
git commit -m "docs: add Chinese README entry"
```

Expected:

- one docs-only commit exists for the Chinese README entry

### Task 3: Add the bilingual getting-started support docs

**Files:**
- Create: `docs/public/getting-started.md`
- Create: `docs/public/getting-started.md`
- Test: section and link verification with `rg`

- [ ] **Step 1: Create the English getting-started guide**

Write `docs/public/getting-started.md` with this structure:

```markdown
# Getting Started

[中文](getting-started.md)

## Who This Path Is For

- teams that want to land DDD without a heavy framework story
- teams that want a strong default path before touching advanced concepts

## Read in This Order

1. `README.md`
2. `docs/public/framework-positioning.md`
3. the relevant GitHub issue
4. the relevant spec or plan under `docs/superpowers/`

## Minimal Workflow

1. identify the aggregate root and entity boundary
2. define command and query intent separately
3. let mutation converge into command handling
4. release domain events from the aggregate root when process continuation is needed
5. treat controller, job, and event handlers as orchestration surfaces rather than truth sources

## Start Conservatively

- use the default happy path first
- do not start with advanced read/write weak-reference modeling
- do not start with Saga, Strong ID, or Domain Service unless the problem really requires them

## Next Reading

- [Framework Positioning](framework-positioning.md)
```

- [ ] **Step 2: Create the Chinese getting-started guide**

Write `docs/public/getting-started.md` with the matching structure:

```markdown
# 快速开始

[English](getting-started.md)

## 适用对象

- 想落地 DDD，但不想先搭一套过重框架叙事的团队
- 希望先按默认主路径推进，再考虑高级概念的团队

## 推荐阅读顺序

1. `README.md`
2. `docs/public/framework-positioning.md`
3. 对应的 GitHub issue
4. `docs/superpowers/` 下的相关 spec 或 plan

## 最小工作流

1. 先识别聚合根与实体边界
2. 分别定义命令意图与查询意图
3. 让状态变更收敛到命令处理路径
4. 需要流程继续时，由聚合根发出领域事件
5. 把 controller、job、事件处理器看作流程入口，而不是领域真相源

## 先走保守路径

- 先走默认 happy path
- 不要一开始就使用读写弱引用高级模式
- 没有明确问题前，不要先上 Saga、Strong ID、Domain Service

## 下一步阅读

- [框架定位](framework-positioning.md)
```

- [ ] **Step 3: Verify the bilingual getting-started docs**

Run:

```powershell
rg -n "^# Getting Started$|^\[中文\]|^## Who This Path Is For$|^## Read in This Order$|^## Minimal Workflow$|^## Start Conservatively$|^## Next Reading$" docs/public/getting-started.md
rg -n "^# 快速开始$|^\[English\]|^## 适用对象$|^## 推荐阅读顺序$|^## 最小工作流$|^## 先走保守路径$|^## 下一步阅读$" docs/public/getting-started.md
```

Expected:

- both language files exist
- both contain the expected section skeletons and reciprocal language links

- [ ] **Step 4: Commit the getting-started docs**

Run:

```bash
git add docs/public/getting-started.md docs/public/getting-started.md
git commit -m "docs: add public getting-started guides"
```

Expected:

- one docs-only commit exists for the getting-started pair

### Task 4: Add the bilingual framework-positioning support docs

**Files:**
- Create: `docs/public/framework-positioning.md`
- Create: `docs/public/framework-positioning.md`
- Test: section verification with `rg`

- [ ] **Step 1: Create the English framework-positioning doc**

Write `docs/public/framework-positioning.md` with this structure:

```markdown
# Framework Positioning

[中文](framework-positioning.md)

## Foreground Concepts

- Aggregate Root
- Entity
- Domain Event
- Command
- Query

## Default Happy Path

- single-command single-aggregate-root mutation
- aggregate root as the write-facing surface
- mutation converges into command handling
- domain events are registered and released by aggregate roots
- `cli` is an anti-corruption boundary, not the truth source of the process

## Background Concepts

- Value Object
- Integration Event
- Repository contract
- handler family
- cli

## Advanced But Valid Concepts

- Domain Service
- Saga
- Strong ID

## Runtime And Infra Surfaces

- JPA runtime and repository landing path
- integration-event transport and persistence adapters
- starter and auto-configuration
- other provider-specific runtime support

## Removed Or Deprecated Core Positioning

- Wrapper is not part of the public core positioning anymore

## Advanced Modeling Note

- advanced read/write model split with read-only weak-reference template context is non-default
- repository remains write-model only
```

- [ ] **Step 2: Create the Chinese framework-positioning doc**

Write `docs/public/framework-positioning.md` with the matching structure:

```markdown
# 框架定位

[English](framework-positioning.md)

## 前景概念

- 聚合根
- 实体
- 领域事件
- 命令
- 查询

## 默认 Happy Path

- 单命令只变更一个聚合根
- 聚合根是写入主面
- 状态变更收敛到命令处理路径
- 领域事件由聚合根统一登记与发出
- `cli` 是防腐层边界，不是主流程真相源

## 背景概念

- 值对象
- 集成事件
- Repository 契约
- handler 家族
- cli

## 高级但有效的概念

- Domain Service
- Saga
- Strong ID

## 运行时与基础设施承载面

- JPA 运行时与 repository 落地路径
- 集成事件传输与持久化适配
- starter 与自动配置
- 其他 provider 级运行时支持

## 已退出核心定位的概念

- Wrapper 不再属于公开核心定位

## 高级建模提示

- 读写模型分离下的只读弱引用模板上下文属于高级模式
- repository 仍然只感知写模型
```

- [ ] **Step 3: Verify the framework-positioning docs**

Run:

```powershell
rg -n "^# Framework Positioning$|^\[中文\]|^## Foreground Concepts$|^## Default Happy Path$|^## Background Concepts$|^## Advanced But Valid Concepts$|^## Runtime And Infra Surfaces$|^## Removed Or Deprecated Core Positioning$|^## Advanced Modeling Note$" docs/public/framework-positioning.md
rg -n "^# 框架定位$|^\[English\]|^## 前景概念$|^## 默认 Happy Path$|^## 背景概念$|^## 高级但有效的概念$|^## 运行时与基础设施承载面$|^## 已退出核心定位的概念$|^## 高级建模提示$" docs/public/framework-positioning.md
```

Expected:

- both files contain the required section structure
- wrapper is only described as removed/deprecated from core positioning

- [ ] **Step 4: Commit the framework-positioning docs**

Run:

```bash
git add docs/public/framework-positioning.md docs/public/framework-positioning.md
git commit -m "docs: add public framework positioning guides"
```

Expected:

- one docs-only commit exists for the framework-positioning pair

### Task 5: Verify link graph, issue lifecycle, and final docs state

**Files:**
- Modify: GitHub issue `cap4k#16`
- Test: repository status and content verification commands

- [ ] **Step 1: Verify the README and public-doc link graph**

Run:

```powershell
rg -n "README\\.zh-CN\\.md|docs/public/getting-started\\.md|docs/public/framework-positioning\\.md" README.md
rg -n "README\\.md|docs/public/getting-started\\.zh-CN\\.md|docs/public/framework-positioning\\.zh-CN\\.md" README.md
rg -n "framework-positioning|getting-started" docs/public/getting-started.md docs/public/getting-started.md docs/public/framework-positioning.md docs/public/framework-positioning.md
```

Expected:

- both READMEs link to their language peers and support docs
- support docs link to their language peers
- no required navigation path is missing

- [ ] **Step 2: Verify the docs align with `#15` foreground/background rules**

Run:

```powershell
rg -n "Aggregate Root|Entity|Domain Event|Command|Query" README.md docs/public/framework-positioning.md
rg -n "frontend TypeScript generation|wrapper|read-only weak-reference" README.md
```

Expected:

- foreground concepts are present in the public entry and positioning docs
- advanced or non-default lines are not pitched as front-page promises

- [ ] **Step 3: Update `cap4k#16` lifecycle after the documentation commits are merged**

Update the issue body checklist so it becomes:

```markdown
## Lifecycle Checklist

- [x] spec written
- [x] plan written
- [ ] implementation merged
```

Then add a comment with this shape:

```markdown
Plan written:

- plan: `docs/superpowers/plans/2026-05-05-cap4k-public-readme-and-documentation-positioning.md`
- scope: bilingual README entry plus minimal `docs/public/` split (`getting-started` and `framework-positioning`)
- remaining work: implement the docs rewrite and merge the resulting documentation commits
```

Expected:

- `cap4k#16` body reflects `plan written`
- comment links the stable plan path and makes remaining work explicit

- [ ] **Step 4: Verify final local state for the planning slice**

Run:

```powershell
git status --short
git log -1 --oneline
```

Expected:

- working tree is clean after the plan commit
- latest commit is the `#16` plan commit for this slice
