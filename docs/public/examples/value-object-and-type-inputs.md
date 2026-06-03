# Value Object And Type Inputs

本页用 `cap4k-reference-content-studio` 说明 [Value Object](../concepts/modeling-building-blocks/value-object.md) 和相邻 type input manifest 如何进入 generation evidence。示例只覆盖 sibling repo 中已经存在的三个类型：`MediaProcessingResultSnapshot`、`ReleasePolicy` 和 `MediaProcessingResultStatus`。

## MediaProcessingResultSnapshot

`MediaProcessingResultSnapshot` 是由 `types.valueObjectManifest` 生成的 JSON-backed Value Object。输入文件是 sibling repo 的 `design/value-objects.json`，其中声明：

- `name`: `MediaProcessingResultSnapshot`
- `aggregates`: `MediaProcessingTask`
- `package`: `com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.values`
- `storage`: `json`
- fields: `mediaProcessingTaskId`、`contentId`、`externalTaskId`、`resultStatus`、`assetSha256`、`assetLocation`、`completedAt`、`dbCreatedAt`、`dbUpdatedAt`

生成输出位于 domain module。`MediaProcessingResultSnapshot.kt` 是 data class，并且在同一个 value object class 中嵌套 `Converter : AttributeConverter<MediaProcessingResultSnapshot, String>`。这个 converter 负责 JSON-backed persistence conversion，不承载业务发布决策。

持久化锚点在 schema 中：`media_processing_task.result_snapshot` 带有 `@T=MediaProcessingResultSnapshot;` 类型标记。媒体处理成功后，`MarkMediaProcessingSucceededCmd` 写入结果快照，`MediaProcessingResultSnapshotTest` 检查 type output 与转换行为。

## ReleasePolicy

`ReleasePolicy` 由 `types.enumManifest` 管理，输入文件是 `design/enums.json`。它的 package 是 `com.only4.cap4k.reference.contentstudio.domain.aggregates.content.enums`，items 包含：

- `IMMEDIATE`
- `PAID`

`ReleasePolicy` 仍然在 `Content` 聚合本地 enum package 中。manifest 不额外表达 shared / local；package path 本身就是边界。默认发布路径只发布 `ReleasePolicy.IMMEDIATE` 内容，paid opt-in 路径只在 `ReleasePolicy.PAID` 且 readiness 满足时进入 `PaidPublicationSaga`。

## MediaProcessingResultStatus

`MediaProcessingResultStatus` 同样由 `types.enumManifest` 管理，输入文件也是 `design/enums.json`。它的 package 是 `com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.enums`，items 包含：

- `SUCCEEDED`
- `FAILED`

`MediaProcessingResultSnapshot.resultStatus` 引用这个 enum。`http/media-processing.http` 的成功 callback 使用 `status = "SUCCEEDED"`，进入 application layer 后会形成媒体处理成功的业务事实，并最终让 `processingStatus = SUCCEEDED` 出现在查询观察面。

## How The Evidence Connects

这三个类型可以沿着同一条证据链阅读：

1. `design/value-objects.json` 和 `design/enums.json` 给出 type input manifest。
2. `cap4k-reference-content-studio-start/src/main/resources/db/schema/content-studio-schema.sql` 给出字段类型标记和 persistence surface。
3. 运行 README generation 命令后，本地 `build/cap4k/plan.json` 记录 value object、enum 和相关 Kotlin 输出的 generator plan。
4. `MediaProcessingResultSnapshot.kt`、`ReleasePolicy`、`MediaProcessingResultStatus` 展示实际生成结果。
5. `MediaProcessingResultSnapshotTest`、`ContentStudioDesignContractTest` 和 `PublishContentCommandContractTest` 提供静态合同和行为证据。

概念层面请先读 [Value Object](../concepts/modeling-building-blocks/value-object.md)。项目总览见 [Reference Content Studio](reference-content-studio.md)。如果要把 type input 和 generator ownership 放在一起检查，请继续读 [Generation And Analysis Evidence](generation-and-analysis-evidence.md)。

## Boundaries

`types.valueObjectManifest` 和 `types.enumManifest` 是 type input path，不是业务决策引擎。它们可以让 generator 输出稳定的 value object、enum、converter 和 metadata；但哪些状态代表发布准备、什么时候发布、paid publication 是否可启动、失败时如何补偿，仍然由 `ContentBehavior.kt`、`PaidPublicationEligibilityService`、`PublishContentCmd` 和 `PaidPublicationSaga` 里的手写逻辑负责。
