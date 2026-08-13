# Analyzer Evidence Model 合同

## 目标

Analyzer 必须把同一次静态 compiler observation 中产生的不同事实表达为三个逻辑分区，并为每个分区提供独立 schema、canonical ownership、完整性状态、来源和消费者边界。物理上可以继续共享一次编译、同一 inputDirs 和 raw transport bundle；canonical model 不能把不同事实混成一种 IR graph 或由消费者二次修复。

## 推荐逻辑模型

```text
AnalyzerSnapshot
├── graph
│   ├── nodes
│   └── relationships
├── designProjection
│   └── designBlocks
└── aggregateStructure
    └── aggregateElements
```

- `graph`：从代码观察到的 Cap4k tactical nodes 和静态 directed relationships。
- `designProjection`：从生成 metadata 恢复的规范化战术设计，供 Drawing Board contract 使用。
- `aggregateStructure`：对实际生成 Aggregate-related carrier 的独立结构观察。

三类事实可以共享物理生产链，但不得共享混淆后的 payload owner。一个不可变 source identity 可以被多个 view 引用，但不能复制成两个权威事实源。

## Graph 分区

- 当前最小事实粒度为稳定 node identity 和 directed relationship。
- Graph 可以保留 Query、Capability、Validator、read-side dependency 和其他低层技术关系；它们保留在 Graph 不代表进入默认 causal Flow。
- Graph 不是通用 Kotlin AST、CFG 或 runtime trace，也不证明业务设计正确或运行时顺序。
- `pipeline.flow` 只能消费 Graph 分区及其 completeness 状态，不得从 Drawing Board 或 Aggregate Structure 反向补造因果关系。

## Design Projection 分区

- Design projection 只由 `analyzer-drawing-board-contract` 定义，不重复其往返规则。
- `design-elements.json` 可以是可选 physical input，但其缺失语义必须与 metadata 丢失、空设计、未配置、不可用和 partial 区分。
- `aggregate-elements.json` 不得同时挂入 Graph model 和 Drawing Board model；它只能属于 Aggregate Structure 分区。

## Aggregate Structure 分区

### 代码事实来源

当前生产链为：

1. Generator 在实际 carrier class 上写入 BINARY-retained `AggregateElementMetadata`；
2. Kotlin compiler Analyzer 的 `Cap4kIrGenerationExtension` 收集 metadata，写出每个 analysis input directory 的 `aggregate-elements.json`；
3. `IrAnalysisSourceProvider` 读取 raw 文件，按 `carrierQualifiedName` 进行跨目录去重和冲突判断；
4. canonical assembler 建立唯一的 Aggregate Structure owner，exporter 从该 owner 生成稳定 evidence output。

当前代码事实中的最小记录包括 `carrierQualifiedName`、`aggregate`、`name`、`packageName`、`description`、`type` 和 `root`。它描述实际生成的 carrier class，不是 Design JSON building block、Graph node、Entity Method 或业务事实。

### 内容与用户问题

它可以包含当前内置 producer 标记的 Aggregate、Entity、Repository、Factory、Strong ID、Projection 等 carrier observation，具体内容以 compiler metadata 和输入目录的实际证据为准。它解决的问题是：让工程师、Agent 和结构审计能够回答“生成后实际存在哪些聚合相关结构、来源是什么、多个模块是否一致”，而不是让用户 author 一个新的 Design JSON schema。

### 为什么不是 Design JSON

- Design JSON 描述用户显式选择的 tactical authoring intent；Aggregate Structure 描述生成后实际存在的 carrier。
- Design JSON 的等价合同比较 fields、artifacts、event semantics 和其他战术语义；Aggregate Structure 只观察 carrier identity、ownership、type 和结构字段。
- Aggregate Structure 不生成 SQL、表结构、JDBC 映射或数据库关系真相；缺少相应证据时不得推断这些内容。
- Aggregate Structure 不自动反馈 Generator，也不参与 Drawing Board round-trip。

### 保留、删除、SQL/Schema projection 的意义

- 已确认保留：继续提供独立的结构观察 output；它不退役，也不改造成 SQL/Schema projection。本 Change 保留现有 `drawing_board_aggregate_elements.json` 文件名、输出位置和公开 output identity，避免把模型 ownership 修正扩大成 wire/路径迁移；该文件必须由独立 Aggregate Structure canonical owner 驱动，不再属于 Drawing Board design model。后续若改名或迁移物理位置，必须作为显式合同变化处理。
- 删除：表示不再提供该类 carrier observation；会损失生成结构审计、Agent 读取和跨模块一致性核验能力，必须由明确产品决定承担。
- 转成 SQL/Schema projection：会把观察目标改成数据库结构或持久化 projection，需要新的 source evidence、字段和产品合同，不能作为当前 Aggregate Structure 的隐式重命名。

### 当前类型边界

目标闭集只有：

- `schema`
- `entity`
- `repository`
- `factory`
- `strong-id`
- `projection`

`specification`、`unique-query`、`unique-query-handler`、`unique-validator` 是已退役 cap4k Aggregate Specification / Unique 生成能力的 drift。它们不是兼容承诺，不得恢复 alias、deprecated value、silent mapping 或 migration bridge。Spring Data JPA 的 `org.springframework.data.jpa.domain.Specification` 查询谓词能力不属于本项删除范围。

## 完整性、ownership 和 wire

- Metadata 缺失、raw 文件缺失、输入覆盖、freshness、解析冲突和诊断属于横切 completeness 状态，不是第四类业务事实。
- 请求某个分区时，该分区不完整必须失败或明确不可用；其他分区完整不能掩盖它，也不能生成外观完整的 partial output。
- `analysis.json` 当前仍是 `cap4k.agent.analysis.v1` 的平铺 `AgentAnalysisSection`，不足以表达三个分区的独立 status、count、source attribution、freshness、planned/available paths 和 diagnostics；升级 wire schema 是后置实现缺口，不进入本 Change Build。
- `CapabilityContractFacts` 已在主线表达 `surface.analyzer` 和传播边，后续实现必须继续由生产代码派生 Analyzer 子契约、消费者依赖和闭包；Public Docs、Skill、AgentFacts 不能反向构造事实。

## Issue 与后续实现

- [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25) 继续承担后置 Graph、Design Projection、Aggregate Structure 边界和 transport/model 的实现优先级与状态管理；本 Change 已实施范围之外的 Snapshot/round-trip 工作均以该 Issue 为 backlog owner。
- Aggregate Structure 已纳入本 Change Build；若实现调查暴露超出本合同、无法独立验收的剩余工作，必须由 Issue 明确管理，不能藏在 Drawing Board 或 Snapshot 文档任务中。
- 每个未纳入本 Change Build 的 implementation slice 都从届时最新 `origin/master` 建立自己的 worktree 和 Comet Change，读取本合同，补充自己的范围、验收、focused tests、capability propagation 和真实项目证据。

## 本 Change 的边界

- 本合同是 canonical target contract，不是当前实现完成声明。
- Shape 阶段不修改产品代码。当前 Build 必须完成 Aggregate Structure 的 compiler type 闭集、raw 生成/解析与稳定 merge、独立 canonical ownership、现有 evidence artifact 驱动、focused tests 和实际受影响的 capability propagation。
- 当前 Build 可以暂时保留平铺 `IrAnalysisSnapshot` raw transport，但 `CanonicalModel` 必须提供唯一 Aggregate Structure owner，`AnalysisGraphModel` 与 `DrawingBoardModel` 不得继续重复拥有 `aggregateElements`。
- 当前 Build 不实现完整 `AnalyzerSnapshot` 三分区 transport、per-partition completeness、`cap4k.agent.analysis.v2` 或完整 Drawing Board round-trip gate；这些边界继续由 [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25) 和后续 Change 承担。
- 自动化证据和真实项目证据必须分别记录；旧审计 Verifier 的 `passed` 只证明审计材料覆盖，不证明本合同已经实现。
- 未实现目标不得提前写入 Public Docs、AgentFacts 或 Skill 的当前支持状态。