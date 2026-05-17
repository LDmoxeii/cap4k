# 媒体处理 callback 主路径

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.zh-CN.md)
- [Default Happy Path](../default-happy-path.zh-CN.md)
- [Application Guide](../application.zh-CN.md)
- [Adapter Guide](../adapter.zh-CN.md)

## Scenario

本例聚焦统一教学项目里媒体处理结果返回的推荐路径。系统先通过 `MediaProcessingCli` 把处理请求发到外部媒体平台，并记录外部任务标识。之后外部平台在处理完成或失败时，主动通过 callback 或等价的集成事件把结果推回系统；系统再把这个外部返回翻译成内部命令，推进 `MediaProcessingTask` 的状态。

这里默认已经经过一个明确交接缝：`Content` 在 `ApproveContentCmd` 中完成自己的状态推进后，application 层发出 `StartMediaProcessingCmd`；随后由 `StartMediaProcessingCommandHandler` 创建 / 启动 `MediaProcessingTask`，再调用 `MediaProcessingCli`。callback 页讲的是这个交接缝之后，结果如何沿主路径返回。

这里的主场景不是“如何发起处理”，而是“处理结果回来了以后，默认应该怎么进入内部”。默认答案是：callback / integration-event return 是主路径，polling 只是补位路径。

## Why this layer / concept

这个例子存在，是因为很多项目会把“轮询能实现”误当成“轮询就是默认设计”。但在统一教学项目里，callback 路径才是首选，原因很直接：

- 外部系统已经知道结果时，主动回推比内部反复猜测更及时。
- callback / integration event 让结果进入系统时带着明确事实边界，应用层更容易统一收敛成内部命令。
- callback 主路径能把 polling 留在兼容兜底位，不把定时 job 变成主流程真相源。

从层归位上看，这个例子同时说明三件事：

- `MediaProcessingCli` 负责外部能力端口，不负责决定内部状态机。
- 外部事实入口负责把外部处理结果接进来；callback controller、event bridge、`IntegrationEventSubscriber` 都只是实现形态。
- 进入内部后，真正推进状态的仍然是 application 命令链和 `MediaProcessingTask` 聚合行为。

## Recommended shape

推荐把 callback 主路径组织成“外发请求、外部回推、内部收敛”三段：

```text
ApproveContentCmd
  -> ApproveContentCommandHandler
  -> Content.approve()

application follow-up
StartMediaProcessingCmd
  -> StartMediaProcessingCommandHandler
  -> create or load MediaProcessingTask
  -> MediaProcessingCli.start(...)
  -> MediaProcessingTask.recordExternalTaskIdAndStart(...)

external callback / integration event
  -> external fact entry implementation
  -> IntegrationEventSubscriber or equivalent application entry point
  -> CompleteMediaProcessingCmd or FailMediaProcessingCmd
  -> corresponding command handler
  -> MediaProcessingTask.markCompleted() / markFailed()
```

推荐读者在真实项目里至少能找到这些职责点：

- `ApproveContentCmd` / `Content.approve()`：只把内容推进到审核通过，不负责创建媒体任务。
- application follow-up：负责发出 `StartMediaProcessingCmd`，把内容生命周期和媒体处理生命周期接上。
- `MediaProcessingCli`：发起外部任务，请求 / 响应协议转换，错误统一。
- `StartMediaProcessingCommandHandler`：创建 / 启动 `MediaProcessingTask`，记录外部任务标识，再把任务推进到处理中。
- callback controller 或 integration listener：接收外部 payload，不直接写聚合。
- `IntegrationEventSubscriber`：把外部回调事实翻译成内部事件推进点或内部命令调度点。
- `CompleteMediaProcessingCmd`、`FailMediaProcessingCmd`：收敛为系统内部能稳定理解的命令语义。
- `MediaProcessingTask`：只理解“已完成”“已失败”“可重试”这类内部事实，不理解外部 callback DTO 细节。

如果回传是“外部事件总线”而不是 HTTP callback，推荐形状也一样：区别只在 adapter 入口壳不同，进入 application 之后仍然收敛到同一组内部命令。

## Non-example / misuse

- 外部事实入口收到结果后直接调用仓储，把 `MediaProcessingTask.status` 改成成功或失败。
- `ApproveContentCmd` 一完成就让 `Content` 直接 new `MediaProcessingTask` 或直接发起 `MediaProcessingCli.start(...)`，绕开单独的 `StartMediaProcessingCmd` 交接缝。
- `MediaProcessingCli` 在发起请求时顺手注册“完成后自动发布内容”的业务流程，把边界实现写成流程编排器。
- callback 路径走一套 `CompleteMediaProcessingCmd`，事件总线路径又另外造一套“媒体成功同步逻辑”，没有统一命令语义。
- `IntegrationEventSubscriber` 直接识别第三方状态码并分发给多个聚合，导致外部协议扩散进内部。
- callback 成功返回后，入口层顺手发布 `Content`，把媒体处理结果回传和内容发布合并成一次外部入口动作。

这些错法本质上都在削弱“callback 只是入口，内部命令链才是写真相入口”这条边界。

## Audit cues

- 看 `MediaProcessingCli` 是否只负责对外调用和协议转换，没有自己改内部流程状态。
- 看 `ApproveContentCmd -> StartMediaProcessingCmd -> StartMediaProcessingCommandHandler` 这条交接缝是否清楚存在，而不是由 `Content` 直接吞掉媒体任务创建。
- 看 callback / integration-event 入口之后，是否先经过 `IntegrationEventSubscriber` 或等价收敛点，再进入内部命令。
- 看 `CompleteMediaProcessingCmd`、`FailMediaProcessingCmd` 是否成为 callback 主路径和其他回推入口的统一内部语义。
- 看 `MediaProcessingTask` 是否只接收内部化后的完成 / 失败事实，而不识别外部 payload 结构。
- 看团队文档和实现是否始终把 callback 描述为首选返回路径；polling 只应在 [polling 备用路径示例](media-processing-polling.zh-CN.md) 中作为 fallback 出现。
