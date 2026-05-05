# Application Authoring Guide

本页面向项目作者，说明在 Default Happy Path 下，哪些代码应该落在 `application`。统一教学场景仍然是“内容发布与处理示例项目”：`Content` 与 `MediaProcessingTask` 是写模型主角，外部媒体处理系统通过 `MediaProcessingCli` 进入内部；结果回传优先走 callback 主路径，polling 作为备用路径，但两条路径都必须收敛为内部命令和查询。

## 这一层负责什么

- 定义 Command / Query 契约，给外部入口和内部流程一个稳定的应用层表面。
- 编写 handler，把一个主动作推进到对应聚合根，而不是直接在入口层里改状态。
- 调度 domain、repository、`cli`、读模型或查询依赖，让“下一步做什么”在这里明确。
- 承接领域事件或集成返回之后的流程推进，例如在媒体处理结果返回后触发内部命令更新 `MediaProcessingTask`。
- 维护“一个入口推进一个主动作”的默认写法，让送审、启动处理、发布、查询详情都各自有清晰责任边界。

应用层的职责不是拥有业务真相，而是把各种入口意图整理成领域层能执行的内部动作，并把结果继续推进到下一个明确阶段。

## 这一层可以写什么

- 写命令契约，例如 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd`。
- 与媒体处理任务相关的写命令，例如 `StartMediaProcessingCmd`、`CompleteMediaProcessingCmd`、`FailMediaProcessingCmd`、`SyncMediaProcessingProgressCmd`。
- 读查询契约，例如 `GetContentDetailQry`、`GetMediaProcessingProgressQry`。
- 对应的 handler：加载一个聚合根、调用一个主行为、保存该聚合根，并在需要时发起后续协作。
- 用于推进后续动作的订阅器或应用层订阅逻辑，例如收到“媒体处理已完成”的内部事实后，触发下一个明确命令。
- 为发布决策准备的只读判断，例如读取“媒体已完成”的投影或查询结果，再决定是否允许 `PublishContentCmd` 继续推进 `Content`。

作者在这一层最常见的写法，是把一个入口意图翻译成“加载哪个聚合、调用哪个行为、保存哪个结果、是否触发下一个内部动作”。如果你写的代码主要在做这四件事，它通常就属于应用层。

## 这一层不能写什么

- 直接修改聚合内部字段，绕开 `Content` 或 `MediaProcessingTask` 的行为方法。
- 把 adapter 侧的请求 DTO、回调 payload、第三方状态码对象直接带进应用层当业务真相使用。
- 让一个入口同时推进多个主动作，例如在一次 handler 中既改 `Content` 又改 `MediaProcessingTask`。
- 用查询流程偷偷承载写逻辑，例如在 `GetContentDetailQry` 里顺手修复状态。
- 没有明确边界理由就直接依赖 `MediaProcessingCli`，把外部系统细节散落在各个 handler 中。
- 把“callback 是主路径、polling 是备用路径”写成两套完全不同的业务规则。它们可以有不同入口，但不能有不同的内部真相。

应用层也不能变成“什么都先塞进来再说”的垃圾桶。凡是已经在表达业务不变量的代码，应该回到领域层；凡是还在转换外部协议的代码，应该留在适配器层。

## 典型目录与文件骨架

```text
application/
  commands/
    content/
      CreateContentDraftCmd.kt
      SubmitContentForReviewCmd.kt
      ApproveContentCmd.kt
      PublishContentCmd.kt
      CreateContentDraftCommandHandler.kt
      SubmitContentForReviewCommandHandler.kt
      PublishContentCommandHandler.kt
    media_processing_task/
      StartMediaProcessingCmd.kt
      CompleteMediaProcessingCmd.kt
      FailMediaProcessingCmd.kt
      SyncMediaProcessingProgressCmd.kt
      StartMediaProcessingCommandHandler.kt
      CompleteMediaProcessingCommandHandler.kt
  queries/
    content/
      GetContentDetailQry.kt
      GetContentDetailQueryHandler.kt
    media_processing_task/
      GetMediaProcessingProgressQry.kt
      GetMediaProcessingProgressQueryHandler.kt
  subscribers/
    domain/
      ContentDomainEventSubscriber.kt
      MediaProcessingTaskDomainEventSubscriber.kt
    integration/
      MediaProcessingIntegrationEventSubscriber.kt
```

- `commands/` 下放写入口契约与 handler，按聚合或主动作归位。
- `queries/` 下放只读入口契约与查询组装逻辑，不要混入写路径。
- `subscribers/` 下放事件进入应用层后的推进点，但订阅器推进的仍然应该是明确的内部命令，不是直接改聚合。
- 媒体处理 callback 和 polling 进入应用层之后，都应该落到 `media_processing_task/` 相关命令族，而不是各自长出一套独立写模型。

这份骨架强调的是角色分工。看到文件名时，作者和审阅者应该能立刻判断它是在定义动作、执行动作，还是只做观察。

## 常见反例

- 一个 `PublishContentCmd` handler 同时加载 `Content` 和 `MediaProcessingTask`，并在同一次事务里一起改状态，试图“一把推完所有事情”。
- 因为想少写一个命令，就让 callback controller 或 polling job 直接改仓储，不经过应用层 handler。
- 在查询 handler 中顺手补写状态，例如“查详情时发现媒体已经完成，于是直接把 `Content` 标记成可发布”。
- handler 直接解析第三方媒体平台状态码，并把这些外部枚举到处传递，导致应用层不再稳定。
- callback 路径走 `CompleteMediaProcessingCmd`，polling 路径却直接写另外一套“轮询成功逻辑”，造成同一事实有两套推进方式。

这些反例通常会让项目后期越来越难审计，因为写入边界、失败边界和重试边界会一起变模糊。

## 最低验证与审计检查点

- 一个 handler 是否只推进一个主动作，并且只让一个聚合根进入持久化写边界。
- 写 handler 是否总是通过聚合行为推进状态，而不是直接改对象内部字段。
- 查询路径是否保持只读，`GetContentDetailQry` 与 `GetMediaProcessingProgressQry` 是否没有偷偷承载写逻辑。
- callback 主路径和 polling 备用路径进入应用层后，是否收敛为同一组内部命令语义，而不是各写各的真相。
- 使用 `MediaProcessingCli` 的地方是否有明确边界理由，而且外部协议细节没有扩散进整个应用层。
