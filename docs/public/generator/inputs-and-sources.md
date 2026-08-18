# Inputs And Sources

generator inputs 是作者已经明确写下的架构和设计事实。它们来自 Gradle extension、DB/schema、`design/design.json`、type manifests 和 source-analysis input，而不是来自一次生成后的猜测。输入越清楚，`cap4kPlan`、`cap4kGenerate` 和 analysis evidence 越容易审查。

本页解释输入类别和它们回答的问题。精确 DSL 字段、JSON schema、schema comment annotations 和 manifest 格式请查 [Generator DSL](../reference/generator-dsl.md)、[Design JSON](../reference/design-json.md)、[DB Schema Annotations](../reference/db-schema-annotations.md)、[Value Object Manifest](../reference/value-object-manifest.md) 和 [Enum Manifest](../reference/enum-manifest.md)。

## Gradle Extension

public pipeline plugin id 是 `io.github.ldmoxeii.cap4k.pipeline`。项目通过 Gradle extension 把 module layout、source input、type manifests、templates、generators 和 analysis input 连接起来。

Gradle extension 回答的是：

- base package 是什么。
- domain、application、adapter、start module 分别在哪里。
- generator 应读取哪些 schema、design JSON 和 type manifest 文件。
- source generation 和 analysis generation 各自使用哪些 input dirs。
- output root 和 task behavior 如何配置。

Gradle extension 不是业务规则位置。它只声明 generator 读什么、写哪里，以及如何把输出接入项目结构。

## DB And Schema

DB/schema 是 persistence surface 输入。它可以表达：

- 哪些 table 对应 Aggregate Root 或 Entity。
- primary key、foreign key、unique constraint 和 column type。
- `@RefAggregate=<AggregateName>` 这类同上下文聚合引用事实。
- `@RefId=<TypeName>` 这类所在上下文外部概念引用身份。
- `@Type=<TypeName>` 这类 enum 或 Value Object type marker。

schema 能帮助 generator 生成 aggregate family、repository、factory、Strong ID、mapping 和 persistence adapter 相关结构。它不能替代 Aggregate 行为，也不能把外键关系变成跨聚合直接修改权限。

DB/schema comments 的 supported annotation closed set、relation metadata 依赖规则和 annotation 边界见 [DB Schema Annotations](../reference/db-schema-annotations.md)。

## design/design.json

`design/design.json` 是 source generation 的主要 building-block 输入。常用 tag 包括：

- `command`
- `query`
- `capability`
- `domain_event`
- `integration_event`
- `domain_service`

这些 tag 说明项目需要哪些 application entry、external capability contract、payload、event、service 或 coordination anchor。它们驱动 skeleton shape，不写业务判断。比如 `command` 可以让 Command surface 稳定存在，但何时调用 Aggregate、如何校验、如何 attach event，仍由手写 logic 决定。

## Type Inputs

`types.valueObjectManifest` 和 `types.enumManifest` 是 type input，不是普通配置附件。

`types.valueObjectManifest` 用来声明 Value Object，尤其是 JSON-backed composite value、字段、owner 和 storage 方式。它帮助 generator 生成可提交的类型结构，并让 schema marker 与 domain type 对齐。

`types.enumManifest` 用来声明 Business Enum。除既有 `value` / `name` / `desc` shape 外，可通过有序 `fields` 显式声明类型化 constructor properties；每个 item 必须提供所有声明属性，nullable 属性也要显式写 `null`，且 schema 不提供默认值。首版支持 `String`、`Boolean`、`Byte`、`Short`、`Int`、`Long`、`Float`、`Double`、`BigInteger`、`BigDecimal` 和 canonical enum constant reference，不支持集合、Value Object 或 raw Kotlin expression。

manifest Business Enum 由 `cap4kGenerate` 首次物化到 domain `src/main/kotlin`，plan ownership 为 `CHECKED_IN_SOURCE`、conflict policy 为 `SKIP`。作者可在 checked-in enum 中增加领域逻辑；后续 manifest 变化不会覆盖既有文件。它是 ordinary authoring source；`cap4kGenerateSources` 可以读取 manifest 以解析其他 build-owned artifacts 的类型，但不会物化或覆盖 Business Enum。基础 `value`、`description`、`valueOfOrNull` 和 nested `Converter` API 保持不变。

如果一个类型缺少值语义，不应为了生成方便放进 value-object manifest。如果一个 enum 实际隐藏复杂 policy，应回到 domain modeling 设计 policy，而不是把 enum 当作全部业务规则。

## Source Analysis Inputs

analysis generation 使用独立输入面，不和普通 source generation 混在一起。DSL selection 是 `sources.irAnalysis.inputDirs`。

compiler analysis output root 是 `build/cap4k-code-analysis`。analysis generator 读取的必要 IR 文件包括：

- `nodes.json`
- `rels.json`
- `aggregate-elements.json`；没有 Aggregate element 时也必须存在并写为 `[]`

`design-elements.json` 仍是可选 input。Compiler analysis 会输出以上三个必需文件以及 `design-elements.json`，共四个文件。`cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 使用 source id `ir-analysis`，并 route 到 generator ids `flow` 和 `drawing-board`。

这条路径用于观察已经存在的代码结构。Drawing Board 的普通 tag 文件可以作为显式 Design JSON 输入；独立的 `drawing_board_aggregate_elements.json` 只提供 Aggregate element 结构证据，不是 Design JSON。Analysis 路径不会生成普通 source skeleton，也不是 `cap4kPlan` / `cap4kGenerate` 的输入替身。

## Reference Project Examples

[Reference Content Studio](../examples/reference-content-studio.md) 提供可对照的输入面：

- `design/design.json`：Command、Query、Capability、Endpoint、payload、event 和 domain service 锚点。
- `design/value-objects.json`：通过 `types.valueObjectManifest` 管理 `MediaProcessingResultSnapshot`。
- `design/enums.json`：通过 `types.enumManifest` 管理 `ReleasePolicy` 和 `MediaProcessingResultStatus`。
- start module schema：表达 aggregate table、type marker、引用和 persistence surface。
- root Gradle extension：声明 module path、types、sources 和 analysis configuration。
- `analysis/flows` 与 `analysis/drawing-board`：提交后的 analysis evidence，可和本地 analysis plan 对照。

读 reference project 时，不要把本地 `build/cap4k/plan.json` 或 `build/cap4k/analysis-plan.json` 当作 committed source truth。它们是在本地运行相关任务后得到的 generated evidence，用来审查输入和输出是否一致。

## Input Feedback

进入 generation 前，authoritative input validation 来自 source provider parse、canonical assembly、`cap4kPlan` diagnostics 和 `cap4kAgentSnapshot`。如果输入有问题，应回到 `diagnostics.json`、`plan.json` 和对应 source contract 修正输入；不要在这些真实 owner 之外复制第二套输入规则。

这些信号说明输入需要回到 authoring loop：

- schema 中的 Aggregate table 和模型边界不一致。
- `design/design.json` 中的 `command` / `query` 职责混淆。
- `capability` 或 `integration_event` 没有清晰 published language。
- `types.valueObjectManifest` 的类型没有值对象语义。
- `types.enumManifest` 把复杂规则压成枚举值。
- `sources.irAnalysis.inputDirs` 指向的 IR 输出不完整，缺少 `nodes.json`、`rels.json` 或 `aggregate-elements.json`；没有 Aggregate element 时后者也必须为 `[]`。
- plan 中的 module placement 不能被 Gradle extension 和 architecture 解释。

输入审查的目标是让 generator 读取正确事实。缺少事实时，先修正模型、schema、design JSON 或 manifests，再进入 plan review。
