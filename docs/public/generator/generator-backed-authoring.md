# Generator Backed Authoring

generator-backed authoring 是用 generator 固化 architecture control。作者先在业务意图、模型和技术设计中决定边界，再把这些决定投影到 schema、`design/design.json`、`types.valueObjectManifest`、`types.enumManifest` 和 Gradle extension。generator 读取这些显式输入，产出稳定 code slots、checked-in skeleton、build-owned generated source 和可审查 evidence。

它不是业务判断替代品，也不是业务规则作者。generator 可以让 Command、Query、Capability、Endpoint、Subscriber、Repository adapter、Factory、Value Object 和 enum 等结构保持一致；它不能替作者判断一个聚合何时改变状态、持久化编排何时恢复或补偿、一个 external capability 如何处理失败，或一个 Query 是否应该暴露某个业务视图。

## Architecture Control

architecture control 指的是：generator 把项目已经写明的结构事实变成可重复审查的输出合同。

- 输入事实来自 schema、design JSON、type manifests、module layout 和 Gradle extension。
- 当前项目事实来自按需读取的 `build/cap4k/agent/` snapshot；生成计划证据来自 `build/cap4k/plan.json`。
- checked-in skeleton 通常落在 `<module>/src/main/kotlin`，只由 generator 首次 materialize，之后作为仓库中的稳定入口维护。
- generated source 通常落在 `<module>/build/generated/cap4k/main/kotlin`，由 build 维护。
- analysis evidence 来自 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`，服务于 flow 和 drawing-board 观察。

这些输出帮助团队控制命名、包结构、层级位置、模板选择和 ownership。它们不说明业务含义已经正确；业务含义仍要靠手写 implementation、tests、HTTP examples 和 analysis evidence 共同验证。

## Stable Code Slots

stable code slots 是作者可以长期识别的手写位置。比如：

- Command handler 组织 application use case 和 domain behavior；外层 Command 自动拥有 REQUIRED transaction 与 UoW completion。
- Query handler 组织 read path，不推进状态。
- Subscriber 接收 domain event 或 inbound integration event，必要时委托 Command。
- Capability Handler 把 application 声明的外部能力转成 adapter protocol。
- 跨时间持久化编排使用显式 provider，cap4k 不生成内置编排骨架。
- Factory、Domain Service 和 Value Object 表达领域内的创建、协作判断和值语义。保存前不变量应由 Aggregate / Value Object / Domain Service 的明确行为承担，物理唯一性等约束由数据库 schema 保底；cap4k 不再提供专用 Specification carrier 或 Unit of Work interception。

generator 的作用是让这些 slot 在首次 materialization 时有一致名称和位置。作者的作用是把业务判断写在对应 slot 中，并在 plan review 中确认 checked-in output 固定 `SKIP`、build-owned output 的 policy 与 ownership 一致。

## Evidence Instead Of Guesswork

generator-backed authoring 要求每次生成前后都有证据可读：

- `cap4kPlan` 生成本地 `build/cap4k/plan.json`，用于审查 source generation ownership。
- `cap4kAgentSnapshot` 生成本地 `build/cap4k/agent/` 分区快照，用于发现当前 project、capability、input、ownership、runtime、analysis 和 diagnostics。
- `cap4kGenerate` 根据已审查计划和输入 materialize checked-in skeleton 与正常 source-generation artifacts。
- `cap4kGenerateSources` 只输出 `GENERATED_SOURCE`，并把 build-owned generated Kotlin root 注册进 Kotlin `main` source set。
- `cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate` 使用 analysis input，产出 flow 与 drawing-board observation evidence。

这些 evidence 可以回答“generator 打算写什么、写在哪里、谁拥有、如何处理冲突、现有代码结构如何连接”。它们不回答“业务规则是否合理”。如果 evidence 暴露错位，反馈应回到 [Business Intent And Modeling](../authoring/business-intent-and-modeling.md)、[Technical Design](../authoring/technical-design.md) 或 [Generator Input Projection](../authoring/generator-input-projection.md)。

## Public Boundary

public docs 中的 generator-backed authoring 只解释公开可用的输入、任务和证据面。需要字段定义时，转到 [Plan JSON](../reference/plan-json.md)、[Outputs](../reference/outputs.md)、[Generator DSL](../reference/generator-dsl.md)、[Design JSON](../reference/design-json.md)、[Value Object Manifest](../reference/value-object-manifest.md) 和 [Enum Manifest](../reference/enum-manifest.md)。

如果读者想看真实项目如何使用这些 mechanics，可以对照 [Reference Content Studio](../examples/reference-content-studio.md) 和 [Generation And Analysis Evidence](../examples/generation-and-analysis-evidence.md)。参考项目展示的是 generator 和手写逻辑协作，不是让 generator 接管业务设计。
