# Reference Content Studio

`cap4k-reference-content-studio` 是 cap4k public docs 共用的可运行参考项目。它展示一个小而完整的内容发布系统：创建内容草稿，提交审核，审核通过后启动媒体处理，通过 HTTP integration-event callback 标记媒体处理成功，最后让内容进入 `PUBLISHED` 状态。

本章所有 examples 页面都只围绕这个 sibling repo 展开。public docs 解释设计意图和阅读顺序；参考项目保存完整代码、设计输入、生成计划、测试、`.http` 文件和分析证据。

## Four Modules

参考项目分成四个 Gradle 模块，分别映射到 public docs 的 architecture 章节：

- `cap4k-reference-content-studio-domain`：领域模型、Aggregate、Value Object、Factory、Domain Service、Domain Event 和领域行为测试。
- `cap4k-reference-content-studio-application`：Command、Query、Subscriber、Saga、job 和用例编排。
- `cap4k-reference-content-studio-adapter`：HTTP controller、query adapter、persistence adapter、external client adapter 和 integration event consume path。
- `cap4k-reference-content-studio-start`：Spring Boot runtime assembly、local startup、schema、smoke tests 和 contract tests。

这些模块对应 [Architecture](../architecture/index.md) 中的 domain、application、adapter、start 四层。阅读代码时，可以先用模块边界判断职责，再进入具体类看 generated skeleton 和 handwritten logic 的分工。

## Business Scenario

默认业务主线是：

1. `http/content.http` 创建内容草稿。
2. `http/review.http` 提交并通过审核。
3. 审核通过后，内容需要媒体处理，application layer 创建媒体处理任务。
4. `http/media-processing.http` 通过 HTTP integration-event callback 标记媒体处理成功。
5. 媒体就绪后，`Content` 发出 `ContentPublicationReadyDomainEvent`，application layer 用 `PublishContentCmd` 发布内容。

Paid publication 是显式 opt-in 的高级路径。只有通过 `http/paid-publication.http` 创建 paid draft，且内容审核与媒体处理都完成后，`PaidPublicationSaga` 才会协调 payout hold、entitlement plan、内容发布、权益激活和补偿。

## Design Inputs

参考项目的主要设计输入在 sibling repo 的 `design/` 和 schema 中：

- `design/design.json`：描述 command、query、domain event、integration event、domain service、saga 等 building blocks。
- `design/value-objects.json`：通过 `types.valueObjectManifest` 管理 `MediaProcessingResultSnapshot`。
- `design/enums.json`：通过 `types.enumManifest` 管理 `ReleasePolicy` 和 `MediaProcessingResultStatus`。
- `cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql`：提供 generator 读取的数据库结构和字段类型标记，例如 `media_processing_task.result_snapshot`。

这些输入不会替团队写业务决策。它们定义生成骨架需要消费的事实；状态推进、发布规则、Saga 步骤、补偿语义和幂等判断仍在手写代码里表达。

## Generated Plan

`build/cap4k/plan.json` 是运行 README generation 命令后产生的本地 generation ownership 证据面。`plan.json` 列出 generator 计划写入的 output path、`generatorId`、`templateId`、`moduleRole`、`conflictPolicy` 和 output kind。

它不属于 sibling repo 的提交源码面。已提交的 inspection surfaces 是 design inputs、schema、source、tests、`.http`、`analysis/flows` 和 `analysis/drawing-board`。

阅读 plan 时，重点不是把它当作业务说明，而是确认这些问题：

- 哪些文件由 generator 输出，哪些文件承载手写业务逻辑。
- command、query、subscriber、payload、client、value object、enum 等输出是否落在正确模块。
- `conflictPolicy` 是否能保护已经存在的手写实现。

## Code And Tests

推荐先用这些文件建立阅读锚点：

- `ContentBehavior.kt`：`Content` 的审核、媒体就绪、发布准备和 `ContentPublicationReadyDomainEvent`。
- `ContentFactory.kt`：草稿创建、默认状态和 factory payload。
- `MediaProcessingResultSnapshot.kt`：JSON-backed Value Object 及其 nested converter。
- `PaidPublicationEligibilityService`：paid publication 是否可启动的领域判断。
- `PaidPublicationSaga`：paid publication 的持久化跨步骤协调和补偿。
- `MediaProcessingPollingFallbackJob`：媒体处理状态的 scheduled reaction 示例。

测试面可以按责任阅读：

- `ContentBehaviorTest`、`ContentFactoryTest`、`PaidPublicationEligibilityServiceTest`：domain layer 行为证据。
- `ContentStudioHappyPathHttpSmokeTest`、`ContentStudioPaidPublicationSagaSmokeTest`、`MediaProcessingCallbackIntegrationEventSmokeTest`：runtime smoke path。
- `ContentStudioDesignContractTest`、`PublishContentCommandContractTest`、`MediaProcessingResultSnapshotTest`：设计合同、命令合同和 type output 证据。

## Operation And Evidence

`.http` 文件是本地手工操作主入口：

- `http/content.http`
- `http/review.http`
- `http/query.http`
- `http/media-processing.http`
- `http/paid-publication.http`

分析证据面用于观察 controller、subscriber、job、Saga 和 application flow 的结构。已提交的分析 inspection surfaces 包括：

- `analysis/flows/index.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`
- `analysis/drawing-board/drawing_board_client.json`
- `analysis/drawing-board/drawing_board_command.json`
- `analysis/drawing-board/drawing_board_domain_event.json`
- `analysis/drawing-board/drawing_board_integration_event.json`
- `analysis/drawing-board/drawing_board_query.json`
- `analysis/drawing-board/drawing_board_saga.json`

运行 README analysis 命令后，本地 `build/cap4k/analysis-plan.json` 可作为 analysis generation plan 证据面。

下一步可以按目标进入 [Run The Reference Project](run-the-reference-project.md)、[Default Publication Flow](default-publication-flow.md)、[Paid Publication Saga Flow](paid-publication-saga-flow.md)、[Value Object And Type Inputs](value-object-and-type-inputs.md) 或 [Generation And Analysis Evidence](generation-and-analysis-evidence.md)。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者把 cap4k-reference-content-studio 的项目结构、输入文件、生成计划、运行入口和分析证据放在一张图里理解。
Type: architecture diagram
Prompt: Draw a cap4k project structure diagram for cap4k-reference-content-studio. Show four modules as layered boxes: cap4k-reference-content-studio-domain, cap4k-reference-content-studio-application, cap4k-reference-content-studio-adapter, cap4k-reference-content-studio-start. Around them show design/design.json, design/value-objects.json, design/enums.json, db schema, build/cap4k/plan.json, .http files, tests, analysis/flows, and analysis/drawing-board. Use Chinese labels and preserve English identifiers.
Must show: four modules, design inputs, schema input, generated plan, .http operation files, tests, analysis flows, drawing-board evidence
Must avoid: introducing another project, implying generator writes business decisions automatically, showing adapter or start as domain owners
Alt text after insertion: cap4k-reference-content-studio 项目结构图，展示四个模块、设计输入、生成计划、HTTP 操作文件、测试和分析证据面。
-->
