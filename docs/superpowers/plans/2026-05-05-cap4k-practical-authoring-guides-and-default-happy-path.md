# Cap4k Practical Authoring Guides And Default Happy Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first complete cap4k authoring-guide system so project authors can directly start writing real cap4k projects and reviewers can audit them against one stable Default Happy Path model.

**Architecture:** Treat this slice as a documentation-system implementation, not a runtime refactor. Build one coherent `docs/public/authoring/` tree centered on Default Happy Path, migrate generator usage guidance out of the deep module README, wire public entry docs to the new authoring tree, and keep all guides anchored to one shared teaching project and one shared set of horizontal contracts.

**Tech Stack:** Markdown, Git, GitHub issues, repository docs, bilingual public docs, PowerShell, `rg`.

---

## File Structure

### Public entry docs to modify

- `README.md` - English public entry; add authoring-guide navigation
- `README.zh-CN.md` - Chinese public entry; add authoring-guide navigation
- `docs/public/getting-started.md` - English quick-start path; point to authoring overview and Happy Path
- `docs/public/getting-started.zh-CN.md` - Chinese quick-start path; point to authoring overview and Happy Path
- `docs/public/framework-positioning.md` - English positioning doc; link authoring-system responsibilities
- `docs/public/framework-positioning.zh-CN.md` - Chinese positioning doc; link authoring-system responsibilities

### New authoring overview and normative center

- `docs/public/authoring/index.zh-CN.md` - Chinese overview and reading paths
- `docs/public/authoring/index.md` - English overview and reading paths
- `docs/public/authoring/default-happy-path.zh-CN.md` - Chinese normative center
- `docs/public/authoring/default-happy-path.md` - English normative center

### New horizontal contract docs

- `docs/public/authoring/naming-and-layout.zh-CN.md`
- `docs/public/authoring/generation-boundaries.zh-CN.md`
- `docs/public/authoring/example-contract.zh-CN.md`

### New generator guide family

- `docs/public/authoring/generator/index.zh-CN.md`
- `docs/public/authoring/generator/bootstrap.zh-CN.md`
- `docs/public/authoring/generator/code-generation.zh-CN.md`
- `docs/public/authoring/generator/code-analysis.zh-CN.md`
- `docs/public/reference/generator-dsl.zh-CN.md`
- `cap4k-plugin-pipeline-gradle/README.md` - reduce to thin pointer instead of primary authoring manual

### New layer guides

- `docs/public/authoring/domain.zh-CN.md`
- `docs/public/authoring/application.zh-CN.md`
- `docs/public/authoring/adapter.zh-CN.md`

### New advanced concept guides

- `docs/public/authoring/advanced/index.zh-CN.md`
- `docs/public/authoring/advanced/value-object.zh-CN.md`
- `docs/public/authoring/advanced/domain-service.zh-CN.md`
- `docs/public/authoring/advanced/saga.zh-CN.md`
- `docs/public/authoring/advanced/strong-id.zh-CN.md`
- `docs/public/authoring/advanced/read-only-weak-reference.zh-CN.md`

### New example appendix

- `docs/public/authoring/examples/reference-project-overview.zh-CN.md`
- `docs/public/authoring/examples/content-draft-to-publish.zh-CN.md`
- `docs/public/authoring/examples/media-processing-callback.zh-CN.md`
- `docs/public/authoring/examples/media-processing-polling.zh-CN.md`

---

### Task 1: Create the authoring overview and wire the public entry docs to it

**Files:**
- Create: `docs/public/authoring/index.zh-CN.md`
- Create: `docs/public/authoring/index.md`
- Modify: `README.md`
- Modify: `README.zh-CN.md`
- Modify: `docs/public/getting-started.md`
- Modify: `docs/public/getting-started.zh-CN.md`
- Modify: `docs/public/framework-positioning.md`
- Modify: `docs/public/framework-positioning.zh-CN.md`
- Test: authoring navigation verification with `rg`

- [ ] **Step 1: Verify the current public entry docs do not yet expose the full authoring system**

Run:

```powershell
rg -n "authoring|Default Happy Path Guide|Generator Guide|Domain Authoring Guide|Application Authoring Guide|Adapter Authoring Guide|Advanced Concepts Guide" README.md README.zh-CN.md docs/public/getting-started.md docs/public/getting-started.zh-CN.md docs/public/framework-positioning.md docs/public/framework-positioning.zh-CN.md
```

Expected:

- output is empty or only contains incidental mentions
- there is no established public navigation into a full authoring-guide system yet

- [ ] **Step 2: Create the Chinese authoring overview**

Write `docs/public/authoring/index.zh-CN.md` with this structure:

```markdown
# Cap4k 编写指南总览

[English](index.md)

> 这套文档定义 cap4k 项目的默认编写方式、审计方式，以及偏离默认路径时的决策入口。

## 这套文档解决什么问题

- 让项目作者知道如何直接开始编写 cap4k 项目
- 让审阅者知道如何按 Default Happy Path 审核项目
- 为后续 AI collaboration guide 提供稳定上游规则

## 阅读路径

### 项目作者

1. [Default Happy Path](default-happy-path.zh-CN.md)
2. [生成器指南](generator/index.zh-CN.md)
3. [领域层指南](domain.zh-CN.md)
4. [应用层指南](application.zh-CN.md)
5. [适配器层指南](adapter.zh-CN.md)
6. [高级概念指南](advanced/index.zh-CN.md)

### 深度用户 / 框架贡献者

- 先完整阅读本页和 Default Happy Path
- 再按需阅读横切规范与 generator reference

## 六大主题

- [Default Happy Path](default-happy-path.zh-CN.md)
- [Generator Guide](generator/index.zh-CN.md)
- [Domain Authoring Guide](domain.zh-CN.md)
- [Application Authoring Guide](application.zh-CN.md)
- [Adapter Authoring Guide](adapter.zh-CN.md)
- [Advanced Concepts Guide](advanced/index.zh-CN.md)

## 横切规范

- [命名与目录规范](naming-and-layout.zh-CN.md)
- [生成 / 手写边界](generation-boundaries.zh-CN.md)
- [示例合同](example-contract.zh-CN.md)
```

Constraints:

- keep the page short and navigational
- make Default Happy Path the first reading step
- clearly distinguish project-author and deep-user paths

- [ ] **Step 3: Create the English authoring overview**

Write `docs/public/authoring/index.md` with the matching structure:

```markdown
# Cap4k Authoring Guide Overview

[中文](index.zh-CN.md)

> This guide system defines how cap4k projects are expected to be written, reviewed, and intentionally extended beyond the default path.

## What This Guide System Solves

- gives project authors a direct way to start writing cap4k projects
- gives reviewers a stable Default Happy Path audit baseline
- provides a durable upstream writing model for later AI collaboration guidance

## Reading Paths

### Project Authors

1. [Default Happy Path](default-happy-path.md)
2. [Generator Guide](generator/index.zh-CN.md)
3. [Domain Authoring Guide](domain.zh-CN.md)
4. [Application Authoring Guide](application.zh-CN.md)
5. [Adapter Authoring Guide](adapter.zh-CN.md)
6. [Advanced Concepts Guide](advanced/index.zh-CN.md)

### Deep Users / Framework Contributors

- start with this overview and Default Happy Path
- then move into horizontal contracts and reference material as needed

## Guide Families

- [Default Happy Path](default-happy-path.md)
- [Generator Guide](generator/index.zh-CN.md)
- [Domain Authoring Guide](domain.zh-CN.md)
- [Application Authoring Guide](application.zh-CN.md)
- [Adapter Authoring Guide](adapter.zh-CN.md)
- [Advanced Concepts Guide](advanced/index.zh-CN.md)
```

Constraints:

- English coverage is limited to overview-level navigation in phase one
- keep links stable even if deep guides stay Chinese-first

- [ ] **Step 4: Wire the public entry docs to the authoring overview**

Apply these edits:

```markdown
<!-- README.md and README.zh-CN.md -->
## Documentation Map

- [Getting Started](docs/public/getting-started.md)
- [Framework Positioning](docs/public/framework-positioning.md)
- [Authoring Guide Overview](docs/public/authoring/index.md)
```

```markdown
<!-- docs/public/getting-started.md -->
## Next Reading

- [Framework Positioning](framework-positioning.md)
- [Authoring Guide Overview](authoring/index.md)
- [Default Happy Path](authoring/default-happy-path.md)
```

```markdown
<!-- docs/public/getting-started.zh-CN.md -->
## 下一步阅读

- [框架定位](framework-positioning.zh-CN.md)
- [编写指南总览](authoring/index.zh-CN.md)
- [Default Happy Path](authoring/default-happy-path.zh-CN.md)
```

```markdown
<!-- framework-positioning*.md -->
Add one short section near the end:

## From Positioning to Authoring

- positioning explains what cap4k is and is not
- authoring guides explain how to write projects under that positioning
- continue with the authoring overview and Default Happy Path
```

- [ ] **Step 5: Verify authoring-system navigation now exists**

Run:

```powershell
rg -n "Authoring Guide Overview|编写指南总览|Default Happy Path|Generator Guide|Domain Authoring Guide|Application Authoring Guide|Adapter Authoring Guide|Advanced Concepts Guide" README.md README.zh-CN.md docs/public/getting-started.md docs/public/getting-started.zh-CN.md docs/public/framework-positioning.md docs/public/framework-positioning.zh-CN.md docs/public/authoring/index.md docs/public/authoring/index.zh-CN.md
```

Expected:

- all entry docs now point into the authoring system
- both overview docs expose the intended reading paths

- [ ] **Step 6: Commit the overview and public-entry wiring**

Run:

```bash
git add README.md README.zh-CN.md docs/public/getting-started.md docs/public/getting-started.zh-CN.md docs/public/framework-positioning.md docs/public/framework-positioning.zh-CN.md docs/public/authoring/index.md docs/public/authoring/index.zh-CN.md
git commit -m "docs: add authoring overview and entry navigation"
```

Expected:

- one docs-only commit exists for overview and public-entry navigation

### Task 2: Write the normative center and the horizontal contracts

**Files:**
- Create: `docs/public/authoring/default-happy-path.zh-CN.md`
- Create: `docs/public/authoring/default-happy-path.md`
- Create: `docs/public/authoring/naming-and-layout.zh-CN.md`
- Create: `docs/public/authoring/generation-boundaries.zh-CN.md`
- Create: `docs/public/authoring/example-contract.zh-CN.md`
- Test: heading and rule verification with `rg`

- [ ] **Step 1: Create the Chinese Default Happy Path guide**

Write `docs/public/authoring/default-happy-path.zh-CN.md` with this structure:

```markdown
# Default Happy Path

[English](default-happy-path.md)

## 规则强度说明

- `Must`
- `Default`
- `Avoid`
- `Advanced`

## 硬规则总表

| 规则 | 强度 | 核心约束 |
| --- | --- | --- |
| 单命令单聚合根变更 | Must | 一个命令处理路径内只允许一个聚合根进入持久化变更边界 |
| 状态变更收敛到命令处理路径 | Must | controller/job/subscriber 不直接改聚合 |
| 聚合根是唯一写入主面 | Must | 子实体不作为外部写入目标 |
| 领域事件由聚合根统一登记和发出 | Must | 事件可以描述子实体变化，但归属和登记主体属于聚合根 |
| 默认禁止跨聚合写模型强引用 | Default | 只读弱引用属于高级模式 |
| cli 是防腐边界，不是主流程真相源 | Must | 外部能力必须先穿过边界 |
| 多 handler 顺序不保证 | Default | 顺序依赖应拆成阶段化流程 |
| 单一主动作 | Default | 写入口一次只推进一个命令，查入口一次只推进一个查询 |

## 建模

### 单命令单聚合根变更
- Why
- Non-example
- Audit cues

### 聚合根是唯一写入主面
- Why
- Non-example
- Audit cues

## 命令

### 状态变更收敛到命令处理路径
- Why
- Non-example
- Audit cues

## 事件

### 领域事件由聚合根统一登记和发出
- Why
- Non-example
- Audit cues

### 多 handler 顺序不保证
- Why
- Non-example
- Audit cues

## 编排

### 单一主动作
- Why
- Non-example
- Audit cues

## 查询

### 查询观察不反向污染写模型
- Why
- Non-example
- Audit cues

## 集成边界

### cli 是防腐边界
- Why
- Non-example
- Audit cues

## 最小工作流合同

1. 先判断是否需要 spec / plan
2. 先跑 `cap4kPlan`
3. 再跑 `cap4kGenerate`
4. 只有在分析链路明确需要时才进入 `cap4kAnalysis*`
5. 生成后再进入手写补全
6. 完成后必须验证
7. 验证通过后再进入 review
```

Constraints:

- keep the rule table compact
- tie every section back to the shared teaching project
- do not hide rule strength; print it explicitly

- [ ] **Step 2: Create the English Default Happy Path guide**

Write `docs/public/authoring/default-happy-path.md` with the matching structure:

```markdown
# Default Happy Path

[中文](default-happy-path.zh-CN.md)

## Rule Strengths

- `Must`
- `Default`
- `Avoid`
- `Advanced`

## Compact Rule Table

| Rule | Strength | Constraint |
| --- | --- | --- |
| single-command single-aggregate-root mutation | Must | one command path may only enter one aggregate-root persistence boundary |
| mutation converges into command handling | Must | controller/job/subscriber surfaces do not directly mutate aggregates |
| aggregate root is the only write-facing surface | Must | child entities are not external write targets |
| domain events are registered and released by aggregate roots | Must | event content may describe child change, but event ownership remains at the root |
| cross-aggregate write-model strong reference is forbidden by default | Default | read-only weak reference is advanced only |
| cli is an anti-corruption boundary rather than process truth | Must | external capabilities must cross a boundary first |
| multiple handlers have no guaranteed order | Default | sequencing should be made explicit through staged flow |
| one main action per surface | Default | write surfaces advance one command, read surfaces advance one query |

## Modeling
## Command
## Event
## Orchestration
## Query
## Integration Boundary
```

Constraints:

- keep the English page brief but normative
- it is allowed to be narrower than the Chinese deep explanation, but not to contradict it

- [ ] **Step 3: Create the naming and layout contract**

Write `docs/public/authoring/naming-and-layout.zh-CN.md` with these sections:

```markdown
# 命名与目录规范

## 推荐目录结构

- `domain/aggregates/...`
- `application/commands/...`
- `application/queries/...`
- `application/subscribers/domain/...`
- `application/subscribers/integration/...`
- `adapter/application/...`
- `adapter/portal/...`
- `adapter/integration/...`

## 命名规范

- 聚合根：业务名词
- 实体：业务名词
- 值对象：业务概念值名
- 命令：`*Cmd`
- 查询：`*Qry`
- 外部边界：`*Cli`
- 领域事件：`*DomainEvent`
- 命令处理器：`*CommandHandler`
- 查询处理器：`*QueryHandler`
- 领域订阅器：`*DomainEventSubscriber`
- 集成订阅器：`*IntegrationEventSubscriber`
- 仓储：`*Repository`

## 文件归位规则

- 每类对象必须出现在它的责任目录
- 不能为了方便把跨层对象放进相邻目录

## 审计检查点

- 名称是否能直接反推出角色
- 目录是否和责任一致
```

- [ ] **Step 4: Create the generation-boundaries and example-contract docs**

Write `docs/public/authoring/generation-boundaries.zh-CN.md` with this structure:

```markdown
# 生成 / 手写边界

## 全局边界矩阵

| 产物类型 | 默认归属 | 说明 |
| --- | --- | --- |
| 生成的聚合骨架 | 生成主面 | 作者在手写文件中补行为，不直接抢 generator-owned 文件 |
| 生成的命令/查询契约骨架 | 生成主面 | 手写逻辑放在 handler 或 adapter 侧 |
| application orchestration 具体逻辑 | 手写主面 | 不能期待 generator 完成项目特有流程编排 |
| 外部系统协议转换 | 手写主面 | 适配器负责 |
| 模板覆盖 | Advanced | 有升级与审计代价 |

## Current Reality

- 当前模板覆盖采用项目级 `overrideDirs`
- 当前是 preset 路径替换，不是 artifact/family 粒度 override
- 覆盖会带来升级漂移与额外审计成本

## Future Strengthening Directions

- 更细粒度模板 override
- artifact/family 级覆盖控制
- 更强 generated / handwritten ownership 标记
- override 漂移诊断
```

Write `docs/public/authoring/example-contract.zh-CN.md` with this structure:

```markdown
# 示例合同

所有示例都必须包含：

1. Scenario
2. Why this layer / concept
3. Recommended shape
4. Non-example / misuse
5. Audit cues

## 示例不是随手代码片段

- 必须说明它在统一参考项目中的位置
- 必须说明为什么归到这一层
- 必须给出错误写法
```

- [ ] **Step 5: Verify the normative center and horizontal contracts**

Run:

```powershell
rg -n "单命令单聚合根变更|状态变更收敛到命令处理路径|聚合根是唯一写入主面|领域事件由聚合根统一登记和发出|多 handler 顺序不保证|单一主动作" docs/public/authoring/default-happy-path.zh-CN.md
rg -n "最小工作流合同|cap4kPlan|cap4kGenerate|cap4kAnalysis" docs/public/authoring/default-happy-path.zh-CN.md
rg -n "overrideDirs|Current Reality|Future Strengthening Directions" docs/public/authoring/generation-boundaries.zh-CN.md
rg -n "Scenario|Why this layer / concept|Recommended shape|Non-example / misuse|Audit cues" docs/public/authoring/example-contract.zh-CN.md
```

Expected:

- the Happy Path guide contains the fixed hard rules
- the Happy Path guide also contains the minimal workflow contract
- generation boundaries distinguish current reality from future strengthening
- example contract defines the required five-part example structure

- [ ] **Step 6: Commit the normative center and horizontal contracts**

Run:

```bash
git add docs/public/authoring/default-happy-path.zh-CN.md docs/public/authoring/default-happy-path.md docs/public/authoring/naming-and-layout.zh-CN.md docs/public/authoring/generation-boundaries.zh-CN.md docs/public/authoring/example-contract.zh-CN.md
git commit -m "docs: add authoring rules and default happy path"
```

Expected:

- one docs-only commit exists for the normative center and the three horizontal contracts

### Task 3: Deliver the generator guide family and migrate module-local guidance upward

**Files:**
- Create: `docs/public/authoring/generator/index.zh-CN.md`
- Create: `docs/public/authoring/generator/bootstrap.zh-CN.md`
- Create: `docs/public/authoring/generator/code-generation.zh-CN.md`
- Create: `docs/public/authoring/generator/code-analysis.zh-CN.md`
- Create: `docs/public/reference/generator-dsl.zh-CN.md`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Test: task-family, DSL-reference, and pointer verification with `rg`

- [ ] **Step 1: Confirm the current module README is still the deep mixed generator manual**

Run:

```powershell
rg -n "Quick Start|Architecture|Source Provider|Generator Provider|Canonical Model|Fixture|Testing|二次开发|Second Development" cap4k-plugin-pipeline-gradle/README.md
```

Expected:

- output shows the current README mixes project-author usage, architecture, and deep internal material
- this justifies extracting public authoring guidance upward

- [ ] **Step 2: Create the generator overview**

Write `docs/public/authoring/generator/index.zh-CN.md` with this structure:

```markdown
# Generator Guide

## 这条指南解决什么问题

- 什么时候跑 bootstrap
- 什么时候跑 plan / generate
- 什么时候进入 analysis 任务族
- 什么时候需要看 `plan.json`

## 最小原理

- fixed-stage pipeline 不是自由拼装工作流
- `cap4kPlan` 先回答“将生成什么”
- `cap4kGenerate` 再真正落产物
- `cap4kAnalysis*` 是分析观察与投影链路，不是默认主生成路径

## 阅读顺序

1. [Bootstrap](bootstrap.zh-CN.md)
2. [Code Generation](code-generation.zh-CN.md)
3. [Code Analysis](code-analysis.zh-CN.md)
4. [Generator DSL Reference](../../reference/generator-dsl.zh-CN.md)
```

- [ ] **Step 3: Create the bootstrap, code-generation, and code-analysis guides**

Write `docs/public/authoring/generator/bootstrap.zh-CN.md` with sections:

```markdown
# Bootstrap Guide

## 什么时候使用 bootstrap
## bootstrap 生成什么
## bootstrap 不负责什么
## bootstrap 之后作者下一步做什么
## 常见反例
## 最低验证
```

Write `docs/public/authoring/generator/code-generation.zh-CN.md` with sections:

```markdown
# Code Generation Guide

## 何时先跑 `cap4kPlan`
## 何时再跑 `cap4kGenerate`
## 如何阅读 `plan.json`
## 生成主面与手写主面的连接方式
## 常见生成误用
## 最低验证
```

Write `docs/public/authoring/generator/code-analysis.zh-CN.md` with sections:

```markdown
# Code Analysis Guide

## 何时进入 `cap4kAnalysisPlan`
## 何时进入 `cap4kAnalysisGenerate`
## flow / drawing-board 的作者视角用途
## analysis 不是默认主生成路径
## 常见误用
## 最低验证
```

- [ ] **Step 4: Create the DSL reference and slim the deep module README into a pointer**

Write `docs/public/reference/generator-dsl.zh-CN.md` with at least:

```markdown
# Generator DSL Reference

## 顶层 `cap4k { }`
## `bootstrap { }`
## `sources { }`
## `generators { }`
## `aggregate { }`
## `preset / overrideDirs`
## 常见最小配置示例
```

Rewrite `cap4k-plugin-pipeline-gradle/README.md` into a thin pointer:

```markdown
# cap4k-plugin-pipeline-gradle

This module provides the Gradle integration for the cap4k pipeline.

For project-author usage guidance, read:

- `docs/public/authoring/generator/index.zh-CN.md`
- `docs/public/authoring/generator/bootstrap.zh-CN.md`
- `docs/public/authoring/generator/code-generation.zh-CN.md`
- `docs/public/authoring/generator/code-analysis.zh-CN.md`
- `docs/public/reference/generator-dsl.zh-CN.md`

This module README is no longer the primary public authoring manual.
```

Constraint:

- do not write a second-development guide here

- [ ] **Step 5: Verify the generator guide family and module README demotion**

Run:

```powershell
rg -n "cap4kPlan|cap4kGenerate|cap4kAnalysisPlan|cap4kAnalysisGenerate|plan.json|fixed-stage pipeline" docs/public/authoring/generator/index.zh-CN.md docs/public/authoring/generator/code-generation.zh-CN.md docs/public/authoring/generator/code-analysis.zh-CN.md
rg -n "primary public authoring manual|Generator Guide|generator-dsl" cap4k-plugin-pipeline-gradle/README.md
```

Expected:

- the generator docs explicitly define task-family boundaries
- the module README now behaves as a pointer, not the main manual

- [ ] **Step 6: Commit the generator guide family**

Run:

```bash
git add docs/public/authoring/generator/index.zh-CN.md docs/public/authoring/generator/bootstrap.zh-CN.md docs/public/authoring/generator/code-generation.zh-CN.md docs/public/authoring/generator/code-analysis.zh-CN.md docs/public/reference/generator-dsl.zh-CN.md cap4k-plugin-pipeline-gradle/README.md
git commit -m "docs: add generator authoring guides"
```

Expected:

- one docs-only commit exists for generator guides and the module README pointer rewrite

### Task 4: Deliver the domain, application, and adapter authoring guides

**Files:**
- Create: `docs/public/authoring/domain.zh-CN.md`
- Create: `docs/public/authoring/application.zh-CN.md`
- Create: `docs/public/authoring/adapter.zh-CN.md`
- Test: guide-skeleton verification with `rg`

- [ ] **Step 1: Create the domain guide using the shared guide skeleton**

Write `docs/public/authoring/domain.zh-CN.md` with this structure:

```markdown
# Domain Authoring Guide

## 这一层负责什么

- 聚合根
- 实体
- 值对象
- 领域行为
- 生命周期 / 状态机
- 领域事件登记

## 这一层可以写什么

- `Content` 聚合根
- `MediaProcessingTask` 聚合根
- `ContentStatus` / `ReviewStatus` / `MediaProcessingStatus`
- 聚合内部行为方法
- 领域事件登记逻辑

## 这一层不能写什么

- controller / job / subscriber 编排
- 外部系统协议转换
- 查询拼装
- 直接跨聚合修改

## 典型目录与文件骨架

- `domain/aggregates/content/...`
- `domain/aggregates/media_processing_task/...`

## 常见反例

- 把外部系统调用写进聚合
- 把查询投影结构当作写模型
- 让子实体成为外部命令目标

## 最低验证与审计检查点

- 状态迁移是否只通过聚合行为发生
- 领域事件是否由聚合根统一登记
```

- [ ] **Step 2: Create the application guide using the shared guide skeleton**

Write `docs/public/authoring/application.zh-CN.md` with this structure:

```markdown
# Application Authoring Guide

## 这一层负责什么

- Command / Query
- Handler
- 流程推进
- 调度 domain / repository / cli

## 这一层可以写什么

- `CreateContentDraftCmd`
- `SubmitContentForReviewCmd`
- `StartMediaProcessingCmd`
- `PublishContentCmd`
- `GetContentDetailQry`
- `GetMediaProcessingProgressQry`

## 这一层不能写什么

- 直接修改聚合内部状态
- 把 adapter 协议对象直接带进来
- 让一个入口同时推进多个主动作

## 典型目录与文件骨架

- `application/commands/...`
- `application/queries/...`

## 常见反例

- handler 内连续改多个聚合根
- 用查询流程偷偷承载写逻辑
- 没有理由就直接依赖 `cli`

## 最低验证与审计检查点

- 一个 handler 是否只推进一个主动作
- 查询路径是否保持只读
```

- [ ] **Step 3: Create the adapter guide using the shared guide skeleton**

Write `docs/public/authoring/adapter.zh-CN.md` with this structure:

```markdown
# Adapter Authoring Guide

## 这一层负责什么

- controller 入口
- persistence adapter
- domain subscriber 入口
- integration subscriber 入口
- job 轮询入口
- 外部协议转换

## 这一层可以写什么

- Web 请求转内部命令 / 查询
- 外部回调转内部命令
- polling job 调度
- persistence 适配实现

## 这一层不能写什么

- 聚合业务真相
- 状态机规则
- 跨层偷塞 application/domain 逻辑

## 典型目录与文件骨架

- `adapter/portal/...`
- `adapter/application/...`
- `adapter/integration/...`

## 常见反例

- controller 直接改聚合
- job 直接改聚合
- 把外部 DTO 当作领域对象

## 最低验证与审计检查点

- 外部输入是否被转换为内部命令 / 查询
- adapter 是否只承担边界与适配职责
```

- [ ] **Step 4: Verify all three layer guides share the intended skeleton**

Run:

```powershell
rg -n "^## 这一层负责什么$|^## 这一层可以写什么$|^## 这一层不能写什么$|^## 典型目录与文件骨架$|^## 常见反例$|^## 最低验证与审计检查点$" docs/public/authoring/domain.zh-CN.md docs/public/authoring/application.zh-CN.md docs/public/authoring/adapter.zh-CN.md
```

Expected:

- each file contains the same six second-level sections
- differences appear only in layer-specific content, not structure

- [ ] **Step 5: Commit the layer guides**

Run:

```bash
git add docs/public/authoring/domain.zh-CN.md docs/public/authoring/application.zh-CN.md docs/public/authoring/adapter.zh-CN.md
git commit -m "docs: add layer authoring guides"
```

Expected:

- one docs-only commit exists for domain/application/adapter guides

### Task 5: Deliver the advanced concepts guide family

**Files:**
- Create: `docs/public/authoring/advanced/index.zh-CN.md`
- Create: `docs/public/authoring/advanced/value-object.zh-CN.md`
- Create: `docs/public/authoring/advanced/domain-service.zh-CN.md`
- Create: `docs/public/authoring/advanced/saga.zh-CN.md`
- Create: `docs/public/authoring/advanced/strong-id.zh-CN.md`
- Create: `docs/public/authoring/advanced/read-only-weak-reference.zh-CN.md`
- Test: decision-entry and concept coverage verification with `rg`

- [ ] **Step 1: Create the advanced overview as a deviation-from-default decision page**

Write `docs/public/authoring/advanced/index.zh-CN.md` with this structure:

```markdown
# Advanced Concepts Guide

## 什么时候默认路径已经不够

- 默认聚合 + 命令 + 事件写法已经无法清晰表达问题
- 继续硬塞会让默认模型失真

## 决策入口

- 什么时候需要更完整的值语义 -> [Value Object](value-object.zh-CN.md)
- 什么时候行为不自然属于某个聚合 -> [Domain Service](domain-service.zh-CN.md)
- 什么时候进入长流程协调 -> [Saga](saga.zh-CN.md)
- 什么时候需要更强的 ID 类型安全 -> [Strong ID](strong-id.zh-CN.md)
- 什么时候需要只读弱引用统一表达 -> [Read-only Weak Reference](read-only-weak-reference.zh-CN.md)

## 默认前提

- 先用 Default Happy Path
- 偏离前先能说明为什么默认路径不够
```

- [ ] **Step 2: Create the five concept pages with the shared example contract**

Each of the five concept pages must include:

```markdown
# [Concept]

## 什么时候需要它
## 为什么默认路径不够
## 推荐形态
## 常见误用
## 审计检查点
```

Concept-specific anchors:

```markdown
<!-- value-object.zh-CN.md -->
- 区分 enum、primitive、复合值对象
- 解释为什么“JSON 字段”只是承载方式，不是值对象定义本身
```

```markdown
<!-- domain-service.zh-CN.md -->
- 解释什么叫“明显属于领域，但不自然属于某个聚合或值对象”
- 明确它不是 application orchestration 垃圾桶
```

```markdown
<!-- saga.zh-CN.md -->
- 解释它是长流程/最终一致性协调概念
- 明确它不是默认路径前排
```

```markdown
<!-- strong-id.zh-CN.md -->
- 解释它是工程强化概念而非 DDD 核心 building block
- 明确后续应与 wrapper 脱钩
```

```markdown
<!-- read-only-weak-reference.zh-CN.md -->
- 明确 repository 仍只感知写模型
- 只统一类型表达和导航 surface
- 第一阶段推荐“两套类、统一上下文”
```

- [ ] **Step 3: Verify advanced guides really route from deviation need to concept choice**

Run:

```powershell
rg -n "什么时候默认路径已经不够|先用 Default Happy Path|什么时候需要它|为什么默认路径不够|推荐形态|常见误用|审计检查点" docs/public/authoring/advanced/index.zh-CN.md docs/public/authoring/advanced/value-object.zh-CN.md docs/public/authoring/advanced/domain-service.zh-CN.md docs/public/authoring/advanced/saga.zh-CN.md docs/public/authoring/advanced/strong-id.zh-CN.md docs/public/authoring/advanced/read-only-weak-reference.zh-CN.md
```

Expected:

- the advanced overview behaves as a decision entry
- each concept page uses the same decision-oriented structure

- [ ] **Step 4: Commit the advanced guides**

Run:

```bash
git add docs/public/authoring/advanced/index.zh-CN.md docs/public/authoring/advanced/value-object.zh-CN.md docs/public/authoring/advanced/domain-service.zh-CN.md docs/public/authoring/advanced/saga.zh-CN.md docs/public/authoring/advanced/strong-id.zh-CN.md docs/public/authoring/advanced/read-only-weak-reference.zh-CN.md
git commit -m "docs: add advanced concept guides"
```

Expected:

- one docs-only commit exists for the advanced concept family

### Task 6: Deliver the shared example appendix and the minimum verification surface

**Files:**
- Create: `docs/public/authoring/examples/reference-project-overview.zh-CN.md`
- Create: `docs/public/authoring/examples/content-draft-to-publish.zh-CN.md`
- Create: `docs/public/authoring/examples/media-processing-callback.zh-CN.md`
- Create: `docs/public/authoring/examples/media-processing-polling.zh-CN.md`
- Modify: `docs/public/authoring/domain.zh-CN.md`
- Modify: `docs/public/authoring/application.zh-CN.md`
- Modify: `docs/public/authoring/adapter.zh-CN.md`
- Modify: `docs/public/authoring/advanced/index.zh-CN.md`
- Test: example-contract and cross-link verification with `rg`

- [ ] **Step 1: Create the reference-project overview example**

Write `docs/public/authoring/examples/reference-project-overview.zh-CN.md` with this structure:

```markdown
# 内容发布与处理示例项目总览

## Scenario

- 一个单一限界上下文内的内容发布与媒体处理项目

## Why this project exists

- 作为整套作者指南的统一样例

## Recommended shape

- 聚合根：`Content`、`MediaProcessingTask`
- 外部边界：`MediaProcessingCli`
- 主路径：回调 / 集成事件
- 替代路径：轮询 job

## Non-example / misuse

- 每篇指南换一套不同例子
- 把媒体处理全部吞进 `Content`

## Audit cues

- 六大指南是否都引用同一主场景
```

- [ ] **Step 2: Create the three scenario slices**

Write `content-draft-to-publish.zh-CN.md` with:

```markdown
# 从内容草稿到发布

## Scenario
## Why this chain belongs to the default path
## Recommended shape
- `CreateContentDraftCmd`
- `SubmitContentForReviewCmd`
- `ApproveContentCmd`
- `PublishContentCmd`
## Non-example / misuse
## Audit cues
```

Write `media-processing-callback.zh-CN.md` with:

```markdown
# 媒体处理回调主路径

## Scenario
## Why integration-event return is the preferred path
## Recommended shape
- `MediaProcessingCli`
- 外部回调 / 集成事件
- `IntegrationEventSubscriber`
- 内部命令推进 `MediaProcessingTask`
## Non-example / misuse
## Audit cues
```

Write `media-processing-polling.zh-CN.md` with:

```markdown
# 媒体处理轮询替代路径

## Scenario
## Why polling is fallback only
## Recommended shape
- 记录外部任务标识
- `job` 轮询外部状态
- 内部命令推进 `MediaProcessingTask`
## Non-example / misuse
## Audit cues
```

- [ ] **Step 3: Link the layer and advanced guides back to the shared examples**

Add short “See also” style sections:

```markdown
<!-- domain.zh-CN.md -->
## 对应示例

- [内容发布与处理示例项目总览](authoring/examples/reference-project-overview.zh-CN.md)
- [从内容草稿到发布](authoring/examples/content-draft-to-publish.zh-CN.md)
```

```markdown
<!-- application.zh-CN.md -->
## 对应示例

- [从内容草稿到发布](authoring/examples/content-draft-to-publish.zh-CN.md)
- [媒体处理回调主路径](authoring/examples/media-processing-callback.zh-CN.md)
- [媒体处理轮询替代路径](authoring/examples/media-processing-polling.zh-CN.md)
```

```markdown
<!-- adapter.zh-CN.md -->
## 对应示例

- [媒体处理回调主路径](authoring/examples/media-processing-callback.zh-CN.md)
- [媒体处理轮询替代路径](authoring/examples/media-processing-polling.zh-CN.md)
```

```markdown
<!-- advanced/index.zh-CN.md -->
## 参考主场景

- [内容发布与处理示例项目总览](../examples/reference-project-overview.zh-CN.md)
```

Use correct relative links when writing each file.

- [ ] **Step 4: Verify the example appendix follows the shared example contract**

Run:

```powershell
rg -n "^## Scenario$|^## Why this layer / concept$|^## Recommended shape$|^## Non-example / misuse$|^## Audit cues$" docs/public/authoring/examples/*.zh-CN.md
rg -n "对应示例|参考主场景" docs/public/authoring/domain.zh-CN.md docs/public/authoring/application.zh-CN.md docs/public/authoring/adapter.zh-CN.md docs/public/authoring/advanced/index.zh-CN.md
```

Expected:

- all example pages expose the five required example-contract sections
- the layer and advanced guides now point back to shared example slices

- [ ] **Step 5: Commit the example appendix and guide cross-links**

Run:

```bash
git add docs/public/authoring/examples/reference-project-overview.zh-CN.md docs/public/authoring/examples/content-draft-to-publish.zh-CN.md docs/public/authoring/examples/media-processing-callback.zh-CN.md docs/public/authoring/examples/media-processing-polling.zh-CN.md docs/public/authoring/domain.zh-CN.md docs/public/authoring/application.zh-CN.md docs/public/authoring/adapter.zh-CN.md docs/public/authoring/advanced/index.zh-CN.md
git commit -m "docs: add shared authoring examples"
```

Expected:

- one docs-only commit exists for the example appendix and cross-links

### Task 7: Verify the authoring-doc system as a complete slice and update issue lifecycle

**Files:**
- No new content files by default
- Remote: `cap4k#26`

- [ ] **Step 1: Verify the full authoring-tree footprint**

Run:

```powershell
Get-ChildItem -Path docs/public/authoring -Recurse -File | Select-Object -ExpandProperty FullName
rg -n "^# " docs/public/authoring/index.zh-CN.md docs/public/authoring/default-happy-path.zh-CN.md docs/public/authoring/domain.zh-CN.md docs/public/authoring/application.zh-CN.md docs/public/authoring/adapter.zh-CN.md docs/public/authoring/generator/index.zh-CN.md docs/public/authoring/advanced/index.zh-CN.md docs/public/reference/generator-dsl.zh-CN.md
```

Expected:

- the expected authoring tree exists
- all required top-level guide files have real headings and are not empty placeholders

- [ ] **Step 2: Verify navigation and rule anchors across the whole system**

Run:

```powershell
rg -n "Default Happy Path|Generator Guide|Domain Authoring Guide|Application Authoring Guide|Adapter Authoring Guide|Advanced Concepts Guide|命名与目录规范|生成 / 手写边界|示例合同" docs/public/authoring/**/*.md docs/public/authoring/*.md README.md README.zh-CN.md docs/public/getting-started*.md docs/public/framework-positioning*.md
```

Expected:

- overview, entry docs, and topic guides are visibly linked
- no guide family is orphaned from the rest of the system

- [ ] **Step 3: Verify the worktree is docs-only and record whether one final integration commit is required**

Run:

```bash
git status --short
```

Expected:

- only planned documentation files changed
- if verification produced a last docs-only edit set, commit it with:

```bash
git add README.md README.zh-CN.md docs/public/getting-started.md docs/public/getting-started.zh-CN.md docs/public/framework-positioning.md docs/public/framework-positioning.zh-CN.md docs/public/authoring docs/public/reference/generator-dsl.zh-CN.md cap4k-plugin-pipeline-gradle/README.md
git commit -m "docs: finalize practical authoring guides"
```

- [ ] **Step 4: Update `cap4k#26` after implementation is merged**

Update the issue body checklist to:

```markdown
- [x] spec written
- [x] plan written
- [x] implementation merged
```

Add a comment with:

```markdown
Implementation merged.

- Spec: `docs/superpowers/specs/2026-05-05-cap4k-practical-authoring-guides-and-default-happy-path-design.md`
- Plan: `docs/superpowers/plans/2026-05-05-cap4k-practical-authoring-guides-and-default-happy-path.md`
- Public authoring tree: `docs/public/authoring/`
- Generator DSL reference: `docs/public/reference/generator-dsl.zh-CN.md`

This closes the first-phase authoring-guide system and establishes the human-facing writing model that later AI-collaboration guidance (`#17`) can depend on.
```

Expected:

- `#26` lifecycle reflects the merged implementation
- stable references point to the spec, plan, and implemented documentation tree
