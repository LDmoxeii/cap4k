# Generation Tasks

cap4k public pipeline plugin id 是 `io.github.ldmoxeii.cap4k.pipeline`。公开 Gradle tasks 包括：

- `cap4kAgentSnapshot`
- `cap4kPlan`
- `cap4kGenerate`
- `cap4kGenerateSources`
- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`

本页只解释任务职责和边界，不要求读者在阅读文档时运行它们。实际项目中应先阅读 plan evidence，再决定是否 generation。

## Public Task Sequence

只读项目检查入口是：

```text
cap4kAgentSnapshot -> manifest.json -> task-specific sections
```

source generation 的概念顺序是：

```text
cap4kPlan -> cap4kGenerate
```

build-owned generated source 的入口是：

```text
cap4kGenerateSources
```

analysis evidence 的概念顺序是：

```text
cap4kAnalysisPlan -> cap4kAnalysisGenerate
```

这些序列服务于不同输出面。不要把 analysis tasks 当成 ordinary source generation，也不要把 `cap4kGenerateSources` 的输出当成 checked-in handwritten area。

## cap4kAgentSnapshot

`cap4kAgentSnapshot` 写出 `build/cap4k/agent/` 下的 versioned JSON snapshot。一次执行生成 manifest、project、capabilities、inputs、ownership、runtime、analysis 和 diagnostics 分区；读取方先读小型 manifest，再按任务选择详情。

该任务只观察 Gradle 已解析配置、本地项目输入和既有机器证据，不依赖 plan/generate/analysis/compile tasks，也不连接数据库等 live source。配置无效但 task 已启动时，它尽量留下 invalid snapshot 和 diagnostics，再以非零退出。

## cap4kPlan

`cap4kPlan` 使用 source generation config。它读取 DB/schema、design JSON、enum manifest、value-object manifest、Gradle extension 和 generator configuration，写出本地 `build/cap4k/plan.json`。

`plan.json` 是 generation 前的 ownership evidence。它说明 planned output 的 `generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy` 和 output path。它不是 business decision source，也不是已提交源码事实。

## cap4kGenerate

`cap4kGenerate` materialize source generation plan。它首次写 checked-in skeletons，并按 plan 处理其他 source-generation artifacts；已有 checked-in file 一律 SKIP。

常见 checked-in skeleton root 是：

```text
<module>/src/main/kotlin
```

这些 skeleton 可以成为长期维护的入口，例如 Command、Query、Capability、Subscriber、API payload、Repository adapter、Factory、Creation、Domain Event、Value Object 或 enum。所有 checked-in skeleton 都固定使用 `SKIP`；cap4k 不承诺后续同步最新 template。

生成后仍要在 intended handwritten slot 中写业务实现。不要把“文件生成了”解释成“业务完成了”。

## cap4kGenerateSources

`cap4kGenerateSources` 只输出 `GENERATED_SOURCE`。Generated Kotlin root 是每个 target module 下的：

```text
<module>/build/generated/cap4k/main/kotlin
```

该任务会把这些 roots 注册进 Kotlin `main` source set。运行 source generation 时，cap4k 会先清理受控的 `build/generated/cap4k/main/kotlin` root，再按当前计划重建；删除输入中的 persistence projection 或其他 generated artifact 后，旧 source 会随完整重建消失。这个 root 由 build 拥有，常见 `conflictPolicy` 是 `OVERWRITE`。作者不应把它作为长期手写业务逻辑位置，也不应把 generated source snapshot 当成设计输入。

如果需要修改业务行为，应回到 checked-in skeleton 的 handwritten slot、domain/application implementation、schema 或 design JSON，而不是手改 build-owned generated source。

## Checked-In Source Vs Generated Source

`CHECKED_IN_SOURCE` 和 `GENERATED_SOURCE` 的区别是 ownership，不只是路径差异。

`CHECKED_IN_SOURCE`：

- 通常位于 `<module>/src/main/kotlin`。
- 可以作为稳定 skeleton 或 type source 提交。
- 首次 materialization 后由项目按普通提交源码维护。
- 固定 `SKIP` 保护已有手写逻辑，没有 managed-section merge/refresh 合同。

`GENERATED_SOURCE`：

- 位于 `<module>/build/generated/cap4k/main/kotlin`。
- build 负责维护。
- 可以被再次生成覆盖。
- 不作为长期手写业务区。

`OUTPUT_ARTIFACT` 表示非源码 artifact output kind。内置 planner 常见 source 生成项主要落在前两类；具体仍以 `plan.json` 为准。

## Analysis Task Boundary

`cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 不属于 ordinary source generation。它们使用 `sources.irAnalysis.inputDirs`、source id `ir-analysis`，并 route 到 generator ids `flow` 和 `drawing-board`。

analysis tasks 读取 compiler analysis output root：

```text
build/cap4k-code-analysis
```

必要 IR input 是 `nodes.json`、`rels.json` 和 `aggregate-elements.json`；没有 Aggregate element 时后者也必须存在并写为 `[]`。`design-elements.json` 仍是可选 input，compiler 会输出这四个文件。Analysis output 用于观察现有代码结构，帮助 verification 看 controller、subscriber、job、Command、Query、Capability flow 与 Aggregate element 如何连接。Drawing Board 的 Aggregate element 文件是结构证据，不是 Design JSON；analysis outputs 不生成 source skeleton。

analysis evidence 详见 [Analysis Evidence](analysis-evidence.md) 和 [Analysis Outputs](../reference/analysis-outputs.md)。

## Review Discipline

使用 generation tasks 前后的审查重点是：

- 先读 plan，再 materialize output。
- 先区分 checked-in skeleton 和 build-owned generated source，再决定在哪里写业务逻辑。
- 确认 checked-in item 固定 `SKIP`，并单独审查 build-owned output 的 `conflictPolicy`。
- 先理解 analysis tasks 的观察边界，再把 flow/drawing-board evidence 用于 verification。

public docs 把任务名解释成 authoring loop 中的证据和 ownership 节点，而不是把它们当作线性命令清单。
