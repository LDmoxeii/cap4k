# 内容草稿到发布主链路

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.zh-CN.md)
- [Default Happy Path](../default-happy-path.zh-CN.md)
- [Application Guide](../application.zh-CN.md)
- [Domain Guide](../domain.zh-CN.md)

## Scenario

本例聚焦统一教学项目里的内容主链路：作者先创建草稿，再送审，审核通过后满足发布前提，最后发布内容。这里刻意只覆盖 `Content` 自己的生命周期推进，不把媒体处理结果回传入口塞进来混讲；媒体处理入口和回传分别见 [callback 主路径示例](media-processing-callback.zh-CN.md) 与 [polling 备用路径示例](media-processing-polling.zh-CN.md)。

这一条主链路至少要覆盖四个内部命令：

1. `CreateContentDraftCmd`
2. `SubmitContentForReviewCmd`
3. `ApproveContentCmd`
4. `PublishContentCmd`

它们表达的是四个不同阶段的主动作，而不是一个“万能内容更新命令”的四种分支。

## Why this layer / concept

这个示例的重点是说明默认路径下，内容生命周期应该怎样被命令链和聚合行为清晰推进。

- 对 domain 来说，它说明 `Content` 聚合真正负责哪些业务真相：能否从草稿进入送审、能否被批准、能否进入发布。
- 对 application 来说，它说明为什么要拆成 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd` 四个明确入口，而不是在一个 handler 里把内容状态推到底。
- 对 adapter 来说，它说明 Web 入口最多只负责把外部请求翻译成这些内部命令，不应该直接改 `Content`。

这也是 Default Happy Path 的典型“单命令单聚合根变更”样例：四个命令都只推进 `Content` 一个聚合根。即使 `PublishContentCmd` 在发布前需要确认媒体处理是否已完成，它也应该通过只读查询、投影或既有事实判断来决定能否继续，而不是顺手把 `MediaProcessingTask` 一起改掉。

## Recommended shape

推荐形状是“一条内容主命令链 + 一个内容聚合 + 必要的只读前置判断”：

```text
HTTP / UI request
  -> CreateContentDraftCmd
  -> CreateContentDraftCommandHandler
  -> Content.createDraft(...)

HTTP / UI request
  -> SubmitContentForReviewCmd
  -> SubmitContentForReviewCommandHandler
  -> Content.submitForReview()

Reviewer action / internal approval
  -> ApproveContentCmd
  -> ApproveContentCommandHandler
  -> Content.approve()

Publish request
  -> PublishContentCmd
  -> PublishContentCommandHandler
  -> read media-processing readiness
  -> Content.publish()
```

推荐把主链路讲清楚时，至少点出这些落点：

- `CreateContentDraftCmd` 负责创建最初的 `Content` 草稿，不顺手送审。
- `SubmitContentForReviewCmd` 只负责把草稿推进到送审状态，不顺手批准。
- `ApproveContentCmd` 只负责登记审核通过这件事实，不顺手发布。
- `PublishContentCmd` 只在前置条件已满足时推进发布，不顺手修复媒体处理状态。

如果项目需要把“媒体处理已完成”作为发布前提，推荐形状是：

- `MediaProcessingTask` 先在自己的命令链里被推进到完成。
- `PublishContentCmd` handler 在 application 层读取“媒体已完成”的只读事实。
- 条件满足时再调用 `Content.publish()`。

也就是说，发布依赖媒体处理结果，不等于发布命令要接管媒体处理聚合。

## Non-example / misuse

- 一个 `UpsertContentCmd` 同时兼做创建草稿、送审、批准、发布，靠分支参数决定走哪一步。
- `ApproveContentCmd` handler 审核通过后，顺手调用 `PublishContentCmd` 逻辑，把批准和发布绑成一个主动作。
- `PublishContentCmd` handler 直接加载 `MediaProcessingTask` 并修改它的状态，试图一次事务里把两个聚合都收尾。
- controller 直接调用仓储修改 `Content.status`，绕过 `Content` 聚合行为。
- 为了省一个命令，把“查询详情时发现媒体已完成”写成查询路径里的补写逻辑，然后顺手自动发布内容。

这些错法的共同问题是：把本来应该分阶段审计的内容生命周期，糊成了一个“能跑就行”的入口。

## Audit cues

- 看 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd` 是否是四个语义清楚的主动作，而不是参数分支。
- 看每个 handler 是否都只推进 `Content` 一个聚合根进入写边界。
- 看 `PublishContentCmd` 是否只消费媒体处理的只读事实，而没有顺手回写 `MediaProcessingTask`。
- 看 `Content` 聚合是否自己掌握草稿、送审、批准、发布的状态迁移规则。
- 看媒体处理回传入口是否被另放在 [callback 主路径示例](media-processing-callback.zh-CN.md) 和 [polling 备用路径示例](media-processing-polling.zh-CN.md) 中讲清，而不是混进这条主链路里。
