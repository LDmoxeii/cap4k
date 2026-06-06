# cap4k Public Documentation

<!-- IMAGE_PROMPT:
Purpose: 帮助读者从入口页按目标选择阅读路径，理解学习、建项、编写业务功能和查精确字段之间的顺序关系。
Type: workflow diagram
Prompt: Create a reader journey workflow diagram for cap4k public documentation. Start with docs/public/index.md, branch into four reader goals: first-time learning, creating a new project, writing business features, and looking up exact fields. Show each branch moving through concepts, architecture, examples, authoring, generator, and reference as appropriate. Use a clear documentation navigation style.
Must show: docs/public/index.md, first-time learning, creating a new project, writing business features, exact field lookup, concepts, architecture, examples, authoring, generator, reference, cap4k-reference-content-studio
Must avoid: 不要暗示读者必须先读 issue history、internal specs、Phase 1 maps 或 skills；不要把生成器画成会自动完成业务决策；不要把参考页画成学习入口的唯一前置；不要画出违反 Clean Architecture 依赖方向的箭头
Alt text after insertion: cap4k 文档阅读路径图，展示首次学习、创建新项目、编写业务功能和查精确字段四类目标如何进入各章节。
-->

## 先选你的目标

- 第一次学习 cap4k：先建立 DDD 战术概念和 Clean Architecture 心智模型，再看真实参考项目。
- 创建新项目：先确认分层结构，再用 bootstrap 和生成器输入推进项目骨架与计划审阅。
- 编写业务功能：从业务意图、建模、技术设计、生成输入、计划审阅、手写实现到验证反馈形成循环。
- 查精确字段：直接进入 reference，按 Gradle task、DSL、JSON、manifest、plan、output、analysis 或 runtime SQL 查表。

## 推荐阅读路径

### 第一次学习

```text
README.md -> docs/public/index.md -> concepts -> architecture -> examples/reference-content-studio.md
```

建议先读 [concepts](concepts/index.md)，理解 Aggregate、Command、Query、Event、Saga、Repository、Unit of Work 和 generated/handwritten boundary。然后读 [architecture](architecture/index.md)，确认 domain、application、adapter、start 的责任和依赖方向。最后进入 [reference content studio](examples/reference-content-studio.md)，把概念对照到 `cap4k-reference-content-studio` 的真实模块和流程。

### 创建新项目

```text
architecture -> generator/bootstrap-project-structure.md -> authoring -> generator/inputs-and-sources.md -> generator/planning-and-ownership-review.md -> generator/generation-tasks.md
```

先从 [architecture](architecture/index.md) 确认目标项目的分层方式，再读 [bootstrap-project-structure](generator/bootstrap-project-structure.md)。随后用 [authoring](authoring/index.md) 组织业务意图和技术设计，把输入投影到 [inputs-and-sources](generator/inputs-and-sources.md)，先做 [planning-and-ownership-review](generator/planning-and-ownership-review.md)，最后按 [generation-tasks](generator/generation-tasks.md) 执行生成。

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
- [architecture](architecture/index.md)：解释 Clean Architecture 分层、domain/application/adapter/start 的职责、依赖规则和按层测试。
- [examples](examples/index.md)：用 `cap4k-reference-content-studio` 展示四层多模块结构、默认发布流、付费发布 Saga、Value Object 类型输入和分析证据。
- [authoring](authoring/index.md)：组织从业务意图到验证反馈的 spiral authoring loop。
- [generator](generator/index.md)：解释 generator-backed authoring、bootstrap、输入来源、计划审阅、生成任务和分析证据。
- [reference](reference/index.md)：提供 Gradle plugin、generator DSL、design JSON、type manifests、plan/output、analysis output、runtime database schema 和 common mistakes 的精确查询。

## 参考项目

主要参考项目是 `cap4k-reference-content-studio`。它提供：

- `cap4k-reference-content-studio-domain`、`cap4k-reference-content-studio-application`、`cap4k-reference-content-studio-adapter`、`cap4k-reference-content-studio-start` 四个模块。
- `design/design.json`、`design/value-objects.json`、`design/enums.json` 等生成输入。
- 默认内容发布流程、`MediaProcessingResultSnapshot` Value Object 示例和 `PaidPublicationSaga` Saga 示例。
- `plan.json`、`analysis-plan.json`、`analysis/flows`、`analysis/drawing-board` 等审阅和分析证据。

公开文档会解释概念和路径。先读 [reference-content-studio](examples/reference-content-studio.md) 了解参考项目结构和使用方式；需要运行锚点时，再读 [run-the-reference-project](examples/run-the-reference-project.md)。参考项目提供完整代码、`.http` 执行锚点和证据文件。

## 不需要先读什么

理解这些公开文档不需要先阅读 issue history、internal specs、Phase 1 maps 或 skills。那些材料只服务于维护和实现过程，不是读者学习 cap4k 的前置条件。

你也不需要先读完整 reference 章节。reference 用来查精确字段；第一次学习时，先按 concepts、architecture、examples 的顺序建立模型会更稳。