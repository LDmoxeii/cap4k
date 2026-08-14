# Analyzer Evidence Model 合同

## 目标

Analyzer 必须把同一次静态 compiler observation 中产生的事实表达为三个强类型逻辑分区，并为每个分区提供独立 schema、canonical ownership、来源、完整性、计数和诊断。物理上继续允许共享一次编译、同一组 `inputDirs` 和现有 raw sidecar bundle；任何 consumer 都不得把三个分区重新压平成一种 IR graph 或从其他分区补造缺失事实。

## 权威逻辑模型

```text
AnalyzerSnapshot
├── graph: AnalyzerGraphPartition
│   ├── sources
│   ├── status / diagnostics
│   ├── nodes
│   └── relationships
├── designProjection: AnalyzerDesignProjectionPartition
│   ├── sources
│   ├── status / diagnostics
│   └── designBlocks
└── aggregateStructure: AnalyzerAggregateStructurePartition
    ├── sources
    ├── status / diagnostics
    └── aggregateElements
```

- `graph`：从代码观察到的 Cap4k tactical nodes 和静态 directed relationships。
- `designProjection`：从生成 metadata 恢复的规范化战术设计，遵循 `analyzer-drawing-board-contract`。
- `aggregateStructure`：对实际生成 Aggregate-related carrier 的独立结构观察。

`AnalyzerSnapshot` 是 source provider 与 canonical assembler 之间唯一的 Analyzer snapshot contract。不得保留一个平铺旧 snapshot 作为并列权威模型；短期内部适配只能是单向、不可公开且必须在本 Change 内删除。

## 物理 raw transport

本合同保留一次 compiler observation 和每个 analysis input directory 下的现有 sidecar：

- `nodes.json`
- `rels.json`
- `design-elements.json`
- `aggregate-elements.json`

逻辑分区不要求用户改变 `sources.ir-analysis.inputDirs`，也不要求额外 compiler invocation。文件名、目录和任务如需迁移，必须作为后续显式 wire change，不能夹带在本次模型重构中。

每个配置 input directory 必须形成稳定的 source identity。对项目内目录，公开投影使用规范化 project-relative identity；外部目录必须使用稳定、可脱敏且能够与当前配置关联的 identity，不得把不可复现的临时绝对路径当作长期 contract。每个分区引用自己的 source 集合和 diagnostic IDs。

## 分区状态和汇总

分区复用现有 `AgentSnapshotStatus` 词汇：

- `ok`：该分区所需 raw evidence 完整、可解析、无冲突，结果可以合法为空或非空；
- `partial`：已配置且存在部分可用证据，但计划 output 或 freshness 尚不完整；不得把 partial payload 当完整产品输出；
- `invalid`：required raw 缺失、JSON 无效、metadata contract 违反、identity 冲突或其他确定性错误；
- `unavailable`：未配置、未请求或没有可执行 observation，不能声称该分区存在当前事实。

顶层 Analyzer status 按确定性顺序聚合：任一 required partition 为 `invalid` 时顶层为 `invalid`；没有 invalid 但存在 required `partial` 或 `unavailable` 时顶层为 `partial`；全部 requested partition 为 `ok` 时为 `ok`；Analyzer 整体未配置时为 `unavailable`。

一个分区完整不能掩盖另一个分区不完整。完整性、来源、freshness 和诊断是横切状态，不是第四种业务事实。source observation completeness 与 artifact plan freshness 必须分别表达：mtime 或 plan freshness 不能证明 compiler metadata 完整，raw 文件存在也不能证明 output 已生成且新鲜。

## Graph 分区

- 最小事实粒度为稳定 node identity 和 directed relationship identity。
- Graph 可以保留 Query、Capability、Validator、read-side dependency 和其他低层技术关系；它们保留在 Graph 不代表进入默认 causal Flow。
- 默认业务因果入口按触发来源解释为 Actor、Event、Time 三类，但分类不是封闭 NodeType allowlist。每个当前支持入口都必须由生产 Analyzer 观察到实际代码节点及明确 relationship evidence；未来 adapter 通过新增真实 detector 扩展，不由 Flow、文档或 generic fallback 猜测。
- Actor 当前生产 evidence 为 Spring HTTP Controller method 到 Command；RPC、GraphQL、CLI、Admin 与 workflow task 属于 Actor 概念家族，但在没有对应 Analyzer detector 前不得声明为当前支持。
- Event 当前生产 evidence 为无上游 Inbound Integration Event 经实际 `@EventListener` Handler 到 Command。Domain Event Handler、outbound Integration Event 和有上游 Integration Event 是已有因果链 continuation，不是新入口。
- Time 当前生产 evidence 为带 `org.springframework.scheduling.annotation.Scheduled` 的实际 method。Analyzer 必须使用该 method 的稳定 identity，生成 `temporaltriggermethod` node；当方法直接发送 Command 时生成 `TemporalTriggerMethodToCommand` relationship。只执行 Query、Capability 或纯技术逻辑的 scheduled method 不产生默认 causal entry evidence。
- `commandsendermethod` NodeType 与 `CommandSenderMethodToCommand` RelationshipType 完全删除。普通未分类方法即使直接发送 Command，也不得生成 generic sender node、relationship、alias、deprecated value 或迁移桥；需要成为入口时必须先实现明确 trigger detector。
- Graph 不是通用 Kotlin AST、CFG 或 runtime trace，也不证明业务设计正确或运行时顺序。
- 同一 node identity 的 `name`、`fullName` 和 `type` 必须一致；等价重复稳定去重，`missingMetadata` 可以稳定合并，非空 `metadataOwner` 冲突必须失败并保留来源诊断。
- Relationship 以稳定的 from/to/type/label identity 去重；任何丢失 endpoint 或无效 identity 必须形成 Graph diagnostic，不得由其他分区补齐。
- `pipeline.generator.flow` 只能消费 Graph 分区及其状态，不能读取 Drawing Board 或 Aggregate Structure 反向生成因果关系。

## Design Projection 分区
- Design Projection 的业务字段和往返语义由 `analyzer-drawing-board-contract` 定义。
- `design-elements.json` 可以在没有任何 design candidate 的 input directory 中合法缺失或为空；该情形必须与未配置、不可用和无效区分。
- 如果某个 input directory 存在需要 `DesignBlockMetadata` 的候选节点，但 sidecar 缺失、projection 为空或 metadata 丢失，则该来源的 Design Projection 为 `invalid`，请求 Drawing Board 时必须失败，不得生成伪完整 board。
- 跨模块相同 design block identity 的等价 fragment 可以合并；description、aggregate ownership、event semantics、fields、resultFields 或其他战术语义冲突必须失败并关联全部来源。
- `aggregate-elements.json` 不得挂入 Graph 或 Drawing Board model；它只属于 Aggregate Structure 分区。

## Aggregate Structure 分区

### 代码事实来源

生产链为：

1. Generator 在实际 carrier class 上写入 BINARY-retained `AggregateElementMetadata`；
2. Kotlin compiler Analyzer 收集 metadata，为每个 input directory 写 `aggregate-elements.json`；
3. IR source 按 `carrierQualifiedName` 跨目录去重并拒绝冲突；
4. canonical assembler 建立唯一 Aggregate Structure owner，structure evidence exporter 从该 owner 生成稳定 output。

最小记录包括 `carrierQualifiedName`、`aggregate`、`name`、`packageName`、`description`、`type` 和 `root`。它描述实际生成 carrier，不是 Design JSON building block、Graph node、Entity Method、业务事实或数据库 schema。

### 类型闭集

受支持 type 只有：

- `schema`
- `entity`
- `repository`
- `factory`
- `strong-id`
- `projection`

`specification`、`unique-query`、`unique-query-handler`、`unique-validator` 是已退役 drift，不得恢复 alias、deprecated value、silent mapping 或 migration bridge。Spring Data JPA `Specification` 查询谓词不属于该删除范围。

### 输出边界

保留现有 `drawing_board_aggregate_elements.json` 文件名、路径和公开 output identity，但它必须继续由 `CanonicalModel.aggregateStructure` 驱动，不属于 Drawing Board design model，也不参与 Design JSON round-trip。删除或转成 SQL/Schema projection 均需要新的产品合同。

## Canonical ownership

Canonical assembler 必须建立且只建立以下关系：

- `AnalyzerSnapshot.graph` → `CanonicalModel.analysisGraph`
- `AnalyzerSnapshot.designProjection` → `CanonicalModel.drawingBoard`
- `AnalyzerSnapshot.aggregateStructure` → `CanonicalModel.aggregateStructure`

Assembler 不得跨分区复制事实，不得从消费者需求倒推 source payload，也不得让某个分区的非空集合替另一个分区制造完整状态。

## Agent analysis wire

`analysis.json` 必须能够直接表达三分区，而不是只提供平铺的 node/edge/design counts。顶层 section 保留配置、整体 status 和公共 evidence；每个 partition 至少投影：

- stable partition id；
- status；
- type-specific counts；
- source attribution；
- evidence freshness；
- planned output paths；
- available output paths；
- diagnostic IDs；
- reason / next action。

Analyzer collect、parse 或 merge 异常不得再被 `.getOrNull()` 静默吞掉。异常必须转换为稳定 diagnostics，并只污染实际受影响的分区；未受影响分区继续报告自己的状态。

`analysis.json` 直接以 `cap4k.agent.analysis.v2` 替换 `cap4k.agent.analysis.v1`。不同时输出 v1/v2，不保留公开兼容桥，也不长期维持两个并列权威 schema。当前仓内没有生产 decoder 依赖 v1，项目处于 breaking redesign；所有仓内 producer、codec、manifest reference、tests、validators 和当前能力投影必须在同一 Change 中迁移。Public Docs、Skill 或手写 JSON 不得反向成为 Analyzer 事实源。

## Capability contract propagation

生产 capability declarations 必须派生并公开以下直接 consumer 关系：

- Graph → Pipeline Flow；
- Design Projection → Drawing Board；
- Aggregate Structure → structure evidence output。

`CapabilityContractFacts`、AgentFacts、Public Docs 和 Skill 按依赖图传播；只有实际代码完成的 schema、status、outputs 和 consumer contract 才能声明为当前支持能力。canonical spec 可以先于实现，但不能冒充当前代码事实。

## 验证合同

- API tests 证明只有一个权威 `AnalyzerSnapshot` 和三个强类型分区，并证明 `temporaltriggermethod` / `TemporalTriggerMethodToCommand` 是唯一新增 Time trigger evidence，generic Command sender 类型已经删除。
- IR compiler/source tests 覆盖 Spring `@Scheduled` method detection、Temporal Trigger 到 Command、scheduled Query/Capability 非 Flow 入口、普通方法不再产生 generic Command sender，以及每目录 required/optional/empty/invalid raw、metadata completeness、稳定来源、node/edge/design/aggregate 去重与冲突。
- Canonical tests 证明三个分区各自进入唯一 owner，消费者不会跨分区读取。
- Agent codec/service/task/functional tests 证明分区 schema、manifest、counts、freshness、outputs、diagnostics 和状态聚合。
- Capability facts 与 validator tests 证明子契约和传播闭包由生产代码派生。
- Drawing Board 仓内 compiler-backed 自动化与下游项目证据边界遵循 `analyzer-drawing-board-contract`。

## 非目标

- 增加 process projection、scheduler/Job runtime、Job generator 或调度 provider 管理；
- 改变 raw sidecar 名称、inputDirs DSL、公开任务或 compiler invocation 数量；
- 自动把 Analyzer output 注册为 Generator input；
- 把 Aggregate Structure 改造成 Design JSON 或 SQL/Schema projection；
- 从 runtime trace 或任意 Kotlin 结构推断缺失设计。
- 在本合同中新增 RPC、GraphQL、CLI、workflow、CDC 或非 Spring scheduling detector；这些入口只能在后续生产 evidence Change 中扩展。
