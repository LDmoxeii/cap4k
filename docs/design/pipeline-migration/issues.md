# Pipeline Full-Replacement - Exploratory Backlog

> 用于记录如果未来要推进 `cap4k-plugin-pipeline-*` 与旧 `cap4k-plugin-codegen` 做 full parity 时，需要重新激活的 gap backlog。
> 这不是当前仓库的默认主线，也不是当前执行路线图。
> 当前默认方向仍以 `AGENTS.md` 和 `docs/superpowers/mainline-roadmap.md` 为准。

---

## Purpose

这份文档只回答一个问题：

- 如果未来明确要做 old-codegen parity / full replacement，还缺哪些 backlog item 需要重新激活

这份文档不回答：

- 当前主线下一步做什么
- 当前仓库默认应该优先做什么
- bootstrap 或 integration 是否应当并入当前 mainline

## Status Legend

- `Exploratory`: 只是 parity backlog，不是当前 active work
- `Separate Track`: 必须在独立方向下处理，不能混进默认主线
- `Completed Elsewhere`: 已由当前主线或已有 slice 覆盖，不再属于待办

## Exploratory Backlog

### 1. Cross-Generator Type-Reference Parity

**Status**: Exploratory

**Problem**:
- 旧 codegen 依赖共享 `typeMapping`
- pipeline 默认走 immutable canonical model + conservative explicit identity

**Focus**:
- 审计旧模板里所有 `typeMapping` 使用点
- 判断哪些引用可以从 `CanonicalModel` 或稳定命名约定直接推导
- 仅在必要处讨论 registry / derived type mapping fallback

**Reference**:
- [feasibility-analysis.md](feasibility-analysis.md#4-cross-generator-type-references)

### 2. Bootstrap / Scaffolding Parity

**Status**: Separate Track

**Problem**:
- 旧 `GenArchTask` 能从 JSON 模板树生成项目骨架
- pipeline 目前没有等价能力

**Constraint**:
- 这一项必须服从仓库已接受的 bootstrap 决策
- 不能直接把它降格成普通 `GeneratorProvider` backlog

**Required alignment**:
- bootstrap remains a separate capability
- public contract uses slot-based extension
- old `archTemplate` JSON is migration input / reference material, not the future public runtime contract

**Reference**:
- [feasibility-analysis.md](feasibility-analysis.md#5-bootstrap--scaffolding-parity)

### 3. Aggregate-Side Parity Gaps

**Status**: Exploratory

**Problem**:
- pipeline aggregate side still lacks several old-codegen parity capabilities

**Candidate items**:
- factory generation parity
- specification generation parity
- domain-event / domain-event-handler parity
- enum / enum-translation parity
- aggregate wrapper parity
- unique-query / unique-validator parity

**Note**:
- 这些项只有在 full parity 被明确激活时才应该拆成正式 slice

### 4. Remaining Design-Side Parity Gaps

**Status**: Exploratory

**Current note**:
- `api_payload` 已经由当前 mainline 迁移完成，不再属于待办 parity issue

**Possible remaining candidates**:
- design-level domain-event parity
- design-level domain-event-handler parity
- 其他旧 design family 的 residual parity audit

### 5. Relation / Association Parity

**Status**: Exploratory

**Problem**:
- 旧 codegen 依赖 DB comment annotation 解析和 relation inference
- 当前 pipeline db source 仍是纯 JDBC metadata 视角

**Focus**:
- DB annotation carriage strategy
- canonical relation model
- relation inference ownership
- entity template relation rendering

**Reference**:
- [feasibility-analysis.md](feasibility-analysis.md#1-relation--association-generation)

### 6. JPA Annotation Fine-Grained Control Parity

**Status**: Exploratory

**Problem**:
- 旧 entity generation 包含大量细粒度 JPA / framework annotation 逻辑
- 当前 pipeline entity context 仍然偏薄

**Focus**:
- entity / field metadata enrichment
- template-side annotation inference vs planner-side annotation materialization
- soft delete / version / id strategy / relation annotation parity

**Reference**:
- [feasibility-analysis.md](feasibility-analysis.md#2-jpa-annotation-fine-grained-control)

### 7. User-Code Preservation Parity

**Status**: Exploratory

**Problem**:
- 旧 codegen 支持 marker-based in-file regeneration
- 当前 pipeline 只有 file-level conflict policy

**Constraint**:
- 这项不能在没有额外边界讨论的情况下直接默认进入 exporter
- 任何 `MERGE` 方案都会扩大 exporter/file-merging responsibility

**Reference**:
- [feasibility-analysis.md](feasibility-analysis.md#3-user-code-preservation)

## Not Current Mainline

以下内容都不是当前默认主线：

- cross-generator type-reference parity
- aggregate parity completion
- relation parity
- JPA annotation parity
- user-code preservation parity
- bootstrap / scaffolding parity

当前默认主线继续规则见：

- [AGENTS.md](../../../AGENTS.md)
- [mainline-roadmap.md](../../superpowers/mainline-roadmap.md)

## Activation Conditions

只有在以下条件下，这份 backlog 才应被重新激活为 planning input：

- 用户明确要求 full replacement / old-codegen parity
- 仓库 roadmap 明确从 bounded family migration 前移到 parity track
- bootstrap / arch-template migration 被单独重开
