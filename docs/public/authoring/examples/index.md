# 示例总览

> 本目录是 authoring 文档的统一示例附录。各概念页讲规则时都应回到同一个参考项目，避免每页临时发明一套业务语境。

## 统一参考项目

- [内容发布与处理示例项目总览](reference-project-overview.md)：先读这一页，固定 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径和 polling 备用路径的共同语境。
- [内容草稿到发布主链路](content-draft-to-publish.md)：看 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentReviewCmd`、`PublishContentCmd` 如何推进 `Content` 生命周期。
- [媒体处理 callback 主路径](media-processing-callback.md)：看外部媒体处理结果如何通过 inbound integration event 收敛成 `MarkMediaProcessingSucceededCmd`。
- [媒体处理 polling 备用路径](media-processing-polling.md)：看 `MediaProcessingPollingFallbackJob` 如何通过 `RefreshMediaProcessingTaskStatusCmd` 观察外部状态，再复用同一条成功推进命令。

## 概念选择示例

- [概念选择实践示例总览](advanced-concepts-overview.md)：先判断问题能否由默认路径清楚表达，还是需要引入更贴切的概念。
- [内容发布示例：Value Object](content-publication-value-object.md)：用 `MediaProcessingResultSnapshot` 说明值语义和 JSON-backed composite value object。
- [内容发布示例：Strong ID](content-publication-strong-id.md)：用 `ContentId`、`AuthorId`、`MediaProcessingTaskId` 说明 Strong ID 默认生成边界。
- [内容发布示例：Domain Service](content-publication-domain-service.md)：说明跨聚合判断何时进入领域服务。
- [内容发布示例：Saga](content-publication-saga.md)：用 `PaidPublicationSaga` 说明 persisted compensation / recovery，而不是把默认链路包装成 Saga。

## 使用规则

- 概念页讲规则时，应链接到本总览或具体示例页，并继续使用同一批对象名、命令名、事件名。
- 示例页可以补充局部细节，但不能改写参考项目主语境。
- 如果真实参考项目的命名或流程变化，应优先更新本目录，再同步引用它的概念页。
