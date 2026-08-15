# cap4k Public Documentation

## 先选你的目标

- 第一次学习 cap4k：先建立 DDD 战术概念和 Clean Architecture 心智模型，再看真实参考项目。
- 创建新项目：从官方 GitHub Template 建立四层项目，再用生成器输入推进计划审阅和业务实现。
- 编写业务功能：从业务意图、建模、技术设计、生成输入、计划审阅、手写实现到验证反馈形成循环。
- 查精确字段：直接进入 reference，按 Gradle task、DSL、JSON、manifest、plan、output、analysis 或 runtime SQL 查表。

## 推荐阅读路径

### 第一次学习

```text
README.md -> docs/public/index.md -> concepts -> architecture -> examples/reference-content-studio.md
```

建议先读 [concepts](concepts/index.md)，理解 Aggregate、Command、Query、Capability、Actor Endpoint、Event、Repository、Unit of Work 和 generated/handwritten boundary。然后读 [architecture](architecture/index.md)，确认可选 contract role 与 domain、application、adapter、start 的责任和依赖方向。最后进入 [reference content studio](examples/reference-content-studio.md)，把概念对照到 `cap4k-reference-content-studio` 的真实模块和流程。

### 创建新项目

```text
official GitHub Template -> architecture -> authoring -> generator/inputs-and-sources.md -> generator/planning-and-ownership-review.md -> generator/generation-tasks.md
```

先通过 [官方 GitHub Template](https://github.com/LDmoxeii/cap4k-template/generate) 建立项目，再从 [architecture](architecture/index.md) 确认目标项目的分层方式。随后用 [authoring](authoring/index.md) 组织业务意图和技术设计，把输入投影到 [inputs-and-sources](generator/inputs-and-sources.md)，先做 [planning-and-ownership-review](generator/planning-and-ownership-review.md)，最后按 [generation-tasks](generator/generation-tasks.md) 执行生成。

### 编写业务功能

```text
authoring/business-intent-and-modeling.md -> authoring/technical-design.md -> authoring/generator-input-projection.md -> authoring/implementation-inside-generated-skeletons.md -> authoring/verification-and-feedback.md
```

业务功能不是从 Gradle task 开始，而是从意图和边界开始。先写清业务规则、聚合边界、命令/查询职责和事件语义，再把它们投影成 generator inputs。生成骨架以后，只在手写槽位补充业务逻辑，并用分析证据和测试策略反馈设计。

### 查精确字段

```text
reference/index.md -> gradle-plugin.md | generator-dsl.md | design-json.md | value-object-manifest.md | enum-manifest.md | plan-json.md | outputs.md | analysis-outputs.md | runtime-database-schema.md
```

需要任务名、DSL 字段、JSON tag、manifest schema、`plan.json` 字段、输出类型、分析路径或 runtime SQL 时，直接从 [reference](reference/index.md) 进入对应页面。参考页是查表入口，不替代概念和作者流程。

## 文档章节

- [concepts](concepts/index.md)：解释 DDD 战术构件和执行所有权，包括 modeling building blocks 与 command/query、subscriber、repository、mediator、generated skeleton 等执行边界。
- [architecture](architecture/index.md)：解释 dependency-leaf contract role、Clean Architecture 四层职责、依赖规则和按层测试。
- [examples](examples/index.md)：用 `cap4k-reference-content-studio` 展示四层多模块结构、默认发布流、Value Object 类型输入、外部能力边界和分析证据。
- [authoring](authoring/index.md)：组织从业务意图到验证反馈的 spiral authoring loop。
- [generator](generator/index.md)：解释 generator-backed authoring、Agent snapshot、输入来源、计划审阅、生成任务和分析证据。
- [reference](reference/index.md)：提供 Gradle plugin、generator DSL、design JSON、type manifests、plan/output、analysis output、runtime database schema 和 common mistakes 的精确查询。

## 参考项目

主要参考项目是 `cap4k-reference-content-studio`。它提供：

- `cap4k-reference-content-studio-domain`、`cap4k-reference-content-studio-application`、`cap4k-reference-content-studio-adapter`、`cap4k-reference-content-studio-start` 四个模块。
- `design/design.json`、`design/value-objects.json`、`design/enums.json` 等生成输入。
- 默认内容发布流程、`MediaProcessingResultSnapshot` Value Object 示例和媒体处理 Capability 示例。
- `plan.json`、`analysis-plan.json`、`analysis/flows`、`analysis/drawing-board` 等审阅和分析证据。

公开文档会解释概念和路径。先读 [reference-content-studio](examples/reference-content-studio.md) 了解参考项目结构和使用方式；需要运行锚点时，再读 [run-the-reference-project](examples/run-the-reference-project.md)。参考项目提供完整代码、`.http` 执行锚点和证据文件。

## 不需要先读什么

公开文档独立说明当前支持能力、使用方式和边界；读者可按本页路径直接进入架构、建模、生成和参考内容。

你也不需要先读完整 reference 章节。reference 用来查精确字段；第一次学习时，先按 concepts、architecture、examples 的顺序建立模型会更稳。
