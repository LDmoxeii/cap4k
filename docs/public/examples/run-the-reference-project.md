# Run The Reference Project

本页说明如何阅读和运行 sibling repo `cap4k-reference-content-studio`。这里不发明新的命令，也不把 Swagger / OpenAPI 当作主操作面；本地流程以 README 中列出的启动命令和 `http/` 目录里的 `.http` 文件为准。

## Read First

如果只是理解项目，不需要先启动应用。推荐先按这个顺序看文件：

1. `README.md`：确认模块、前置条件、启动入口、`.http` 顺序和 v1 范围。
2. `design/design.json`：查看 command、query、event、subscriber、saga、job 等设计输入。
3. `design/value-objects.json` 和 `design/enums.json`：查看 type input manifest。
4. `cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql`：查看 schema 和字段类型标记。
5. 运行 README 中的 generation 命令后，本地 `build/cap4k/plan.json` 可用于查看 generator 输出计划和 ownership。
6. `analysis/flows` 与 `analysis/drawing-board`：查看已提交的 flow 和 drawing-board 证据。
7. domain / application / adapter / start 模块下的真实 Kotlin 文件和测试。

对应 public docs 的阅读入口是 [Reference Content Studio](reference-content-studio.md)。

## Start Command From README

README 中给出的最短启动路径是在 sibling repo 根目录执行：

```bash
./gradlew :cap4k-reference-content-studio-start:bootRun
```

Windows 上对应命令是：

```powershell
.\gradlew.bat :cap4k-reference-content-studio-start:bootRun
```

README 说明应用默认运行在 `http://localhost:8080`。本页只记录这些 README 已列出的命令；public docs 不增加其他启动方式。

README 还列出两个与 generation / analysis 相关的入口：

```bash
./gradlew cap4kPlan cap4kGenerate
./gradlew cap4kAnalysisPlan cap4kAnalysisGenerate
```

这些命令用于在本地更新生成计划、生成输出或分析产物。`build/cap4k/plan.json` 和 `build/cap4k/analysis-plan.json` 是运行 README generation / analysis 命令后的本地 generated evidence。

已提交的 inspection surfaces 是 design inputs、schema、source、tests、`.http`、`analysis/flows` 和 `analysis/drawing-board`。

## HTTP Operation Order

README 验证的默认 `.http` 顺序是：

1. `http/content.http`
   - `POST /contents`
   - 创建 draft，复制响应里的 `contentId`。
2. `http/review.http`
   - `POST /contents/{contentId}/submit-review`
   - `POST /contents/{contentId}/approve`
   - 填入 `contentId` 后依次提交审核、审核通过。
3. `http/query.http`
   - `GET /contents/{contentId}`
   - `GET /media-processing/{contentId}`
   - 查看内容和媒体处理状态；任务出现后复制 `task.externalTaskId`。
4. `http/media-processing.http`
   - `POST /cap4k/integration-event/http/consume?event=cap4k.reference.contentstudio.media-processing.completed&uuid=...`
   - 填入 `contentId` 和 `externalTaskId`，发送媒体处理成功 callback。
5. 再运行一次 `http/query.http`
   - 观察 `contentStatus = PUBLISHED` 和 `processingStatus = SUCCEEDED`。

这条顺序对应默认内容发布路径，详细设计解释见 [Default Publication Flow](default-publication-flow.md)。

## Paid Opt-In Path

Paid publication 不是默认路径。它从 `http/paid-publication.http` 开始：

1. `POST /advanced/contents/paid` 创建 paid draft。
2. 继续执行 `http/review.http` 的提交和审核通过。
3. 执行 `http/query.http`，复制 `task.externalTaskId`。
4. 执行 `http/media-processing.http` 的 callback。
5. 再执行 `http/query.http` 观察 paid publication Saga 完成后的内容状态。

这条路径用来阅读 `PaidPublicationSaga`、`TryStartPaidPublicationCmd` 和 `PublishPaidPublicationContentCmd`，详细解释见 [Paid Publication Saga Flow](paid-publication-saga-flow.md)。

## What To Check After Running

运行本地流程时，`.http` 返回值是主要观察面。默认路径的关键状态是 `contentStatus = PUBLISHED` 和 `processingStatus = SUCCEEDED`。paid opt-in 路径还应回到 `PaidPublicationSaga`、`PaidPublicationTask` 相关命令和 `ContentStudioPaidPublicationSagaSmokeTest`，确认 Saga 正向步骤与补偿语义。

如果只做静态阅读，可以直接看 `ContentStudioHappyPathHttpSmokeTest`、`MediaProcessingCallbackIntegrationEventSmokeTest` 和 `ContentStudioPaidPublicationSagaSmokeTest`。这些测试把 README 中的手工路径和 runtime 行为放在同一组证据里。
