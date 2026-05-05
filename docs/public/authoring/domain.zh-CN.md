# Domain Authoring Guide

本页面向项目作者，说明在 Default Happy Path 下，哪些代码应该落在 `domain`。统一教学场景仍然是“内容发布与处理示例项目”：核心写模型是 `Content` 与 `MediaProcessingTask`，外部媒体处理系统通过边界进入，结果返回优先走 callback 主路径，polling 只是兼容性备用路径。

## 这一层负责什么

- 承载 `Content` 与 `MediaProcessingTask` 两个聚合根各自的业务真相。
- 定义值对象、状态值和生命周期规则，例如 `ContentStatus`、`ReviewStatus`、`MediaProcessingStatus`。
- 把“当前能不能做某个动作、做完后状态如何变化、什么情况下必须拒绝”写成聚合行为。
- 在聚合根内部统一登记领域事件，让外部层消费事实，而不是代替领域层宣布事实。
- 保护聚合边界：`Content` 负责内容草稿、送审、审核结果、发布相关规则；`MediaProcessingTask` 负责媒体处理发起、执行结果、失败与重试相关规则。

这里要抓住一个判断标准：如果某段代码表达的是业务真相本身，而不是“谁来调用”“从哪里进来”“调完以后通知谁”，它大概率属于领域层。

## 这一层可以写什么

- `Content` 聚合根，以及它的本地行为方法，例如创建草稿、提交送审、接受审核结论、进入可发布状态。
- `MediaProcessingTask` 聚合根，以及它的本地行为方法，例如记录外部任务标识、标记处理中、标记成功、标记失败、准备重试。
- `ContentStatus`、`ReviewStatus`、`MediaProcessingStatus` 这类能直接表达生命周期阶段的值。
- 聚合内部使用的值对象，例如标题、失败原因、处理结果摘要、外部任务标识。
- 领域事件登记逻辑，例如 `ContentSubmittedForReviewDomainEvent`、`MediaProcessingCompletedDomainEvent` 由对应聚合根在行为完成后统一登记。
- 不变量校验与拒绝逻辑，例如“未送审不能批准”“未开始处理不能标记成功”“已经发布的内容不能再次回到草稿”。

作者可以把领域层理解成“只回答业务上是否成立”。它不关心这个动作是从 Web 请求进来的，还是从回调、消息、轮询 job 进来的；它只关心当 `Content` 或 `MediaProcessingTask` 收到一个内部动作时，状态迁移是否合法。

## 这一层不能写什么

- controller、job、subscriber 的流程推进代码，包括 callback 主路径与 polling 备用路径的入口调度。
- `MediaProcessingCli`、第三方媒体服务 DTO、回调 payload、轮询结果等外部协议转换逻辑。
- `GetContentDetailQry`、`GetMediaProcessingProgressQry` 这类查询投影、列表组装、详情组装逻辑。
- 一个聚合根直接修改另一个聚合根的内部状态，或在写模型中持有对方的可写强引用。
- 把跨聚合编排偷塞进聚合行为里，例如让 `Content` 直接去驱动外部媒体处理，或让 `MediaProcessingTask` 直接决定内容是否发布。

如果一段代码需要知道“现在该调用哪个入口”“要不要发 HTTP 请求”“回调和轮询哪条路径先到”，它已经在谈编排和边界，而不是在谈领域真相。

## 典型目录与文件骨架

```text
domain/
  aggregates/
    content/
      Content.kt
      ContentStatus.kt
      ReviewStatus.kt
      events/
        ContentDraftCreatedDomainEvent.kt
        ContentSubmittedForReviewDomainEvent.kt
        ContentPublishedDomainEvent.kt
      value/
        ContentTitle.kt
        PublishWindow.kt
    media_processing_task/
      MediaProcessingTask.kt
      MediaProcessingStatus.kt
      events/
        MediaProcessingStartedDomainEvent.kt
        MediaProcessingCompletedDomainEvent.kt
        MediaProcessingFailedDomainEvent.kt
      value/
        ExternalMediaTaskId.kt
        MediaFailureReason.kt
```

- `content/` 目录只放和 `Content` 生命周期直接相关的聚合、状态、事件、值对象。
- `media_processing_task/` 目录只放和媒体处理任务生命周期直接相关的对象。
- `events/` 下的事件文件应该表达“已经发生了什么”，而不是“接下来想做什么”。
- `value/` 下的对象用来收敛业务值语义，不是为了包装一切字段。

这份骨架不是要求逐字照抄，而是要求作者按聚合边界归位文件。只要一个文件同时在讲内容发布真相和媒体处理真相，就应该重新检查是不是边界放错了。

## 常见反例

- 把外部媒体处理调用直接写进 `Content` 聚合，导致聚合一边改状态一边依赖外部系统返回。
- 因为“发布前要等媒体完成”，就让 `Content` 直接持有并修改 `MediaProcessingTask`，把两个生命周期绑成一个聚合。
- 把查询投影结构当成写模型使用，例如为了返回详情方便，直接把详情对象塞回领域层并参与状态判断。
- 让子实体或状态片段成为外部命令目标，例如单独暴露一个“审核步骤对象”给外部修改，而不是通过 `Content` 根行为收敛。
- 因为 callback 和 polling 都会返回状态，就在领域层里区分“这是回调更新”还是“这是轮询更新”。领域层只应该接收统一后的内部动作，不应该知道入口差异。

这些反例的共同问题是：把“业务真相”和“技术入口”混在一起，最后谁都说不清哪一层应该负责修复问题。

## 最低验证与审计检查点

- `Content` 与 `MediaProcessingTask` 的状态迁移是否都只能通过聚合行为方法发生，而不是外部直接改字段。
- 领域事件是否都由聚合根统一登记，事件内容表达的是事实而不是命令意图。
- 领域层文件里是否没有出现 controller、job、外部回调 DTO、轮询结果对象这类边界概念。
- `Content` 是否仍只负责内容生命周期，`MediaProcessingTask` 是否仍只负责媒体处理生命周期，没有互相吞并。
- callback 主路径和 polling 备用路径进入领域层之后，是否都已经收敛成同一套内部业务语义，而不是把入口差异带进聚合。
