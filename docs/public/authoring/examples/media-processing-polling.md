# 媒体处理 polling 备用路径

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.md)
- [Default Happy Path](../default-happy-path.md)
- [Application Guide](../application.md)
- [Adapter Guide](../adapter.md)
- [媒体处理 callback 主路径](media-processing-callback.md)

## Scenario

本例聚焦统一教学项目里的备用返回路径：系统已经通过 `TriggerMediaProcessingCli` 发起外部处理，并保存了外部任务标识；但由于第三方平台不支持 callback、callback 可靠性不足，或集成链路暂时不可用，系统只能通过定时 job 按外部任务 ID 轮询状态，再把结果同步回内部。

和 callback 主路径一样，这里也默认前面已经走过 `ApproveContentReviewCmd -> StartMediaProcessingCmd -> StartMediaProcessingCmd.Handler` 的交接缝：`Content` 审核通过后，由领域事件订阅器创建 / 启动独立的 `MediaProcessingTask`。polling 是内部触发入口的一种实现。它可以定时观察外部任务状态，但只负责把观察结果翻译成内部命令，不取得聚合写入特权。

这个例子的前提非常重要：polling 不是和 callback 并列的默认设计，而是 callback 不可用时的 fallback。它解决的是兼容性问题，不是替代默认主路径。

## Why this layer / concept

这个示例要说明的不是“polling 能不能做”，而是“polling 为什么只能做备用”。原因通常有三类：

- 它天然更慢，结果可见性依赖调度周期，而不是外部事实一到就回推。
- 它更容易把内部触发入口写成主流程真相源，导致边界、重试、幂等和状态判断全都挤在 adapter 侧。
- 它如果不收敛回同一套内部命令语义，就会和 callback 路径长出两套事实机。

因此，文档必须把 polling 讲清楚，但又不能把它讲成推荐默认路径。它是“保持内部模型一致时，如何接纳较差外部条件”的示例。

## Recommended shape

推荐把 polling 备用路径组织成“已有外部任务 ID + job 轮询 + 内部命令推进”：

```text
StartMediaProcessingCmd
  -> StartMediaProcessingCmd.Handler
  -> create or load MediaProcessingTask
  -> TriggerMediaProcessingCli.Request(...)
  -> MediaProcessingTask records ExternalMediaTaskId and enters processing

scheduled polling job
  -> load pending external task ids
  -> RefreshMediaProcessingTaskStatusCmd
  -> GetMediaProcessingStatusCli.Request(externalTaskId)
  -> translate result
  -> MarkMediaProcessingSucceededCmd when status is SUCCEEDED
  -> MediaProcessingTask progresses internally
```

推荐形状里要强调三点：

- polling job 只负责“去外部看一眼，再把看到的结果翻译回来”。
- job 不直接写聚合状态，而是转成 `RefreshMediaProcessingTaskStatusCmd`；该命令读取外部状态，成功时再转成 `MarkMediaProcessingSucceededCmd`。
- 如果未来 callback 恢复可用，polling 应该能自然退回 fallback 角色，因为两条路径本来就共享同一套内部命令语义。

在这条路径里，外部任务 ID 是关键边界对象。它把“外部世界里那个处理任务”和“内部 `MediaProcessingTask` 这个聚合根”关联起来，但不会因此让 polling job 取得聚合写入特权。

为了让 fallback 收敛点可执行，推荐把外部观察状态明确映射成下面三类内部命令：

| 外部观察状态 | 当前参考项目内部命令 | 说明 |
| --- | --- | --- |
| 仍在排队、上传中、处理中，但还没有终态 | `RefreshMediaProcessingTaskStatusCmd` 后 no-op | 只观察外部状态，不把任务收尾 |
| 已完成，且外部结果已可用于后续业务 | `RefreshMediaProcessingTaskStatusCmd -> MarkMediaProcessingSucceededCmd` | 把 `MediaProcessingTask` 推进到成功态，后续是否允许发布仍由内部规则决定 |
| 已失败、已取消、已超时，且外部系统已给出终态失败事实 | 当前示例暂不展开失败命令 | 失败推进应同样收敛为内部命令，不能由 polling job 直接写聚合 |

换句话说，当前参考项目只把 polling 作为成功状态的补位观察入口；处理中状态 no-op，失败命令作为后续扩展点保留。不要因为 polling 能看到很多中间状态，就把所有状态都硬塞成成功。

## Non-example / misuse

- polling job 直接把 `MediaProcessingTask` 标记成功 / 失败，不经过内部命令处理。
- polling job 看见任意外部状态变化都直接发 `MarkMediaProcessingSucceededCmd`，或者把“处理中 60%”这类中间状态误当成成功。
- 因为 polling 会定期跑，就把它写成唯一结果来源，callback 即使存在也被忽略。
- callback 路径推进 `MarkMediaProcessingSucceededCmd`，polling 路径却另写一套“轮询成功后发布内容”的特殊逻辑。
- job 除了同步媒体状态，还顺手检查 `Content` 是否可发布并直接发布，把备用入口写成万能编排器。
- job 每次轮询都把第三方状态码和 payload 继续往里传，直到 `MediaProcessingTask` 也开始识别外部协议。

这些错法会让 polling 从“兼容入口”变成“主流程控制器”，这是默认路径明确要避免的。

## Audit cues

- 看文档和代码是否明确把 polling 描述为 fallback，而不是和 callback 并列主路径。
- 看 `ApproveContentReviewCmd -> StartMediaProcessingCmd -> StartMediaProcessingCmd.Handler` 这条前置交接缝是否已讲清，确保 polling 处理的是已有外部任务，而不是在 fallback 页里临时补造媒体任务。
- 看 polling job 是否基于已有 `ExternalMediaTaskId` 工作，而不是自己发明新的写模型主键语义。
- 看 job 是否把外部状态观察映射成 `RefreshMediaProcessingTaskStatusCmd`，并且只在终态成功时继续进入 `MarkMediaProcessingSucceededCmd`。
- 看 polling 与 callback 是否最终进入同一套内部命令 / 聚合行为语义，而不是各自一套状态推进。
- 看 polling job 是否没有顺手承担 `Content` 发布、领域规则判断或外部协议向内扩散等越层责任。
