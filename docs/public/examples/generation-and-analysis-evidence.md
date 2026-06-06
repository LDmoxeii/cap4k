# Generation And Analysis Evidence

`cap4k-reference-content-studio` 提供一组可追踪 evidence surfaces，用来检查设计输入、schema、generated plan、analysis plan、flow output、drawing-board output 和测试之间是否一致。本页说明这些文件分别回答什么问题。已提交的 inspection surfaces 是 design inputs、schema、source、tests、`.http`、`analysis/flows` 和 `analysis/drawing-board`。

`build/cap4k/plan.json` 与 `build/cap4k/analysis-plan.json` 是运行 README generation / analysis 命令后的本地 generated evidence。

## Design Input

`design/design.json` 是主要 building-block 输入。它描述 command、query、domain event、integration event、domain service、saga、subscriber、job、client 等设计事实。它回答的问题是：“项目要求 generator 识别哪些业务入口和结构锚点？”

`design/value-objects.json` 和 `design/enums.json` 是相邻 type input manifest：

- `design/value-objects.json` 通过 `types.valueObjectManifest` 管理 `MediaProcessingResultSnapshot`。
- `design/enums.json` 通过 `types.enumManifest` 管理 `ReleasePolicy` 和 `MediaProcessingResultStatus`。

这些输入定义生成骨架的事实来源，不替代手写业务行为。

## Schema

`cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql` 是 generator 读取的数据库结构证据。它包含类型标记，例如：

- `release_policy int not null comment '@T=ReleasePolicy;'`
- `result_snapshot clob comment '@T=MediaProcessingResultSnapshot;'`

schema 与 design inputs 一起决定 generated skeleton 如何连接 persistence surface、domain types 和 application contracts。

## Generated Plan

`build/cap4k/plan.json` 是运行 `cap4kPlan` 后产生的本地 generation ownership 审查文件。它列出每个 plan item 的 `generatorId`、`moduleRole`、`templateId`、`outputPath`、`conflictPolicy`、`outputKind` 和 context。

阅读 `plan.json` 时，可以检查：

- `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentReviewCmd`、`StartMediaProcessingCmd`、`MarkMediaProcessingSucceededCmd`、`RecordContentMediaReadyCmd`、`PublishContentCmd` 是否落在 application module。
- API payload、controller、query adapter、client handler 是否落在 adapter module。
- Value Object、enum、Repository、factory、domain event 是否落在 domain module。
- `conflictPolicy` 是否保护已有手写逻辑。

README 中列出的 generation 入口是：

```bash
./gradlew cap4kPlan cap4kGenerate
```

运行 README 中的 generation 命令后，可以在本地查看 `build/cap4k/plan.json`。

不要把本地 generated plan 当作已提交的 source truth；提交面仍然是 design inputs、schema、source、tests、`.http`、`analysis/flows` 和 `analysis/drawing-board`。

## Analysis Plan

`build/cap4k/analysis-plan.json` 是运行 `cap4kAnalysisPlan` 后产生的本地 analysis generation 计划文件。它描述 drawing-board 和 flow analysis 产物如何从已存在代码结构中生成。README 中列出的 analysis 入口是：

```bash
./gradlew cap4kAnalysisPlan cap4kAnalysisGenerate
```

阅读 `analysis-plan.json` 时，重点看它是否把 command、query、domain event、integration event、saga 和 flow output 放进可审查的 project evidence surface，而不是把它当成业务规则来源。

## Flow Output

`analysis/flows` 保存 controller、subscriber 和 application flow 的结构化输出：

- `analysis/flows/index.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`

参考项目中包含 controller flow 文件，也包含 inbound integration subscriber flow 文件。它们可以帮助对照 `http/content.http`、`http/review.http`、`http/media-processing.http` 和 application subscribers，检查 framework HTTP/message transport reachability、typed inbound subscriber dispatch/reaction、Command dispatch 和 Subscriber reaction 是否连接正确。

## Drawing Board Output

`analysis/drawing-board` 保存按 building-block 分类的 drawing-board evidence：

- `analysis/drawing-board/drawing_board_client.json`
- `analysis/drawing-board/drawing_board_command.json`
- `analysis/drawing-board/drawing_board_domain_event.json`
- `analysis/drawing-board/drawing_board_integration_event.json`
- `analysis/drawing-board/drawing_board_query.json`
- `analysis/drawing-board/drawing_board_saga.json`

这些文件适合用来快速确认项目里有哪些 command、query、event、client 和 saga 锚点。比如 paid publication 可以从 `drawing_board_saga.json` 找到 `PaidPublicationSaga`，默认发布路径可以从 command、domain event 和 integration event drawing board 中找到对应入口。

## Tests As Evidence

测试不是 design input，但它们是重要证据面：

- `ContentStudioHappyPathHttpSmokeTest`：默认 HTTP happy path。
- `ContentStudioPaidPublicationSagaSmokeTest`：paid opt-in Saga runtime path。
- `MediaProcessingCallbackIntegrationEventSmokeTest`：framework integration-event HTTP transport wiring 和 application subscriber reaction smoke path。
- `ContentStudioDesignContractTest`：设计合同证据。
- `PublishContentCommandContractTest`：默认发布 command contract。
- `MediaProcessingResultSnapshotTest`：JSON-backed Value Object output。
- `ContentBehaviorTest`、`ContentFactoryTest`、`PaidPublicationEligibilityServiceTest`：domain 行为和 paid eligibility。

读测试时，重点看它们保护的是手写业务行为、运行路径、合同还是 generated output。不要把测试名当作设计输入；设计输入仍然在 `design/`、schema 和 plan evidence 中。

## Reading Checklist

推荐按这个顺序审查 reference project evidence：

1. 先读 `design/design.json`、`design/value-objects.json`、`design/enums.json`，确认输入事实。
2. 再读 schema，确认 persistence surface 和类型标记。
3. 运行 README generation 命令后，再读本地 `build/cap4k/plan.json`，确认 generator output ownership。
4. 再读真实 Kotlin 文件，确认 generated skeleton 与 handwritten logic 的边界。
5. 再读 tests，确认行为、runtime path 和合同。
6. 最后读已提交的 `analysis/flows` 和 `analysis/drawing-board`。
7. 运行 README analysis 命令后，再读取本地 `build/cap4k/analysis-plan.json`，从结构证据回看 controller、subscriber、job、Saga 和 application flow。

这组 evidence surfaces 共同服务于一个目标：让 public docs 中的概念、架构规则和 examples 页面都能回到同一个 sibling repo 中检查，而不是依赖不可追踪的叙述。
