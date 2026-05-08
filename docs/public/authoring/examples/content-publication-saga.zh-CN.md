# 内容发布示例：Saga

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.zh-CN.md)
- [Default Happy Path](../default-happy-path.zh-CN.md)
- [Saga](../advanced/saga.zh-CN.md)
- [媒体处理 callback 主路径示例](media-processing-callback.zh-CN.md)
- [媒体处理 polling 备用路径示例](media-processing-polling.zh-CN.md)

## Scenario

本页不是换一个业务域重新讲 Saga，而是在共享的 content-publication / media-processing 教学项目里做一次受控扩展。默认参考项目仍然是：内容送审、审核通过、启动媒体处理、外部系统通过 callback 回传结果，polling 只是备用补位路径。

这里额外加入一个明确跨时间等待点：媒体处理已经完成，但内容还不能立刻发布，因为平台还要等待“版权复核结果 + 发布时间窗 + 人工发布确认”中的一个或多个条件。也就是说，`Content` 已经具备发布候选资格，`MediaProcessingTask` 也已经完成，但系统还要把“等未来事实回来再继续”这件事跨时间保存下来。

这个场景的重点不是证明当前默认链路已经需要 Saga。恰好相反，当前默认发布 / 媒体处理链路本身仍然不需要 Saga；Saga 只在这个受控扩展里，用来承接“媒体已完成之后，仍有额外长等待、恢复和补偿要求”的后半段协调。

## Why default path is not enough

默认路径擅长的是：事实一旦到齐，就把内部命令链清楚推进下去。对当前参考项目来说，这已经足够，所以默认路径仍然不需要 Saga。

但在这个受控扩展里，问题不再只是“媒体处理结果有没有回来”，而是：

- callback 已经把媒体完成事实带回来了；
- 发布还要再等一个可能晚很多小时甚至几天才出现的外部或人工事实；
- 等待过程中要能超时、恢复、人工接管，必要时还要撤销预留状态或终止发布。

这时如果还硬塞回普通短命令链，文档就很难回答几个关键问题：系统此刻到底在等什么、超时后怎么恢复、版权复核失败后补偿什么、人工确认迟迟不到时谁来停止继续发布。默认链路不是错，只是它不负责表达这种跨时间等待后的恢复 / 补偿边界。

## Recommended shape

推荐形态仍然以默认参考项目为主干，再在“媒体处理已完成之后”增加一个很窄的 Saga 协调层：

- `Content` 继续只负责内容生命周期事实。
- `MediaProcessingTask` 继续只负责媒体处理生命周期事实。
- callback 仍然是媒体结果进入系统的主路径；polling 仍然只是 fallback，用来补同一条内部命令语义，不提升成主真相路径。
- Saga 只负责记住“发布前还在等哪些跨时间条件”，以及条件满足、超时、失败时分别发什么下一条内部命令。

一个足够短的阶段 / 恢复草图可以是：

```text
ApproveContentCmd
  -> StartMediaProcessingCmd
  -> external processing
  -> callback returns completed
  -> mark MediaProcessingTask completed
  -> PublicationReleaseSaga enters waiting-for-release-readiness

waiting-for-release-readiness
  -> wait copyright recheck passed
  -> wait release window opens
  -> wait manual release confirmation
  -> then issue PublishContentCmd

timeout / failure
  -> stop further publish attempt
  -> issue internal cancel / hold command if needed
  -> leave auditable reason for manual recovery
```

这个草图只是在说明边界，不是在暗示教学项目已经提供某个运行时 Saga 功能或教程实现。推荐读者从中看到的应该是：

- Saga 从媒体处理完成之后才开始变得有意义，而不是从默认审核到 callback 整条链路一上来就接管。
- 等待条件必须是“未来才知道”的事实，而不是当前就能同步判断的前置校验。
- 恢复点要具体，比如超时后转人工确认、版权复核失败后终止自动发布、发布时间窗错过后改为重新排程，而不是只写一个模糊的“稍后再试”。
- 补偿也要具体，比如撤销预占发布槽位、取消待发布标记、记录停止原因，不能把补偿偷换成“重新轮询看看”。

## Non-example / misuse

- 媒体平台 callback 一回来，马上转一条内部命令就能完成后续推进，却为了形式感把这段短 callback-to-command 链升级成 Saga。
- 把 Saga 写成一个 application 超级 handler，审核、媒体处理、发布判断、人工确认、补偿全塞进一个大类里。
- 明明 callback 已经是主路径，却让 polling 变成真正的发布真相源，再把 callback 降成可有可无的旁路。
- 还没出现跨时间等待，只是想把“以后可能会复杂”的担心预埋进去，就提前把默认链路整体包装成 Saga。
- 用一个包装器把命令链重新套壳，却没有明确等待点、恢复点、补偿点，结果只是把原本清楚的边界讲乱。

## Usage boundary

这页的使用边界必须讲死：

- 当前默认 publication / media-processing 链路仍然不需要 Saga。
- 这页只是同一参考项目里的受控扩展示例，不是建议把默认主链改写成 Saga。
- 只有在媒体处理已经完成之后，仍然存在跨时间等待、超时恢复、失败补偿、人工接管这些要求时，Saga 才开始合理。
- 如果条件都已经是已知事实，或者 callback 回来后只差一两条普通内部命令，那么继续用默认命令链即可。
- callback 保持主路径，polling 保持 fallback；无论是否引入 Saga，都不应该把 polling 升格成主要真相来源。

## Audit cues

- 文档是否明确写出“当前默认路径 / 默认链路仍然不需要 Saga”，而不是暗示默认设计不完整。
- 示例是否被表述成同一 reference project 的受控扩展，而不是跳到新的业务域。
- 等待点是否具体到“版权复核 / 发布时间窗 / 人工确认”这类未来事实，而不是泛泛写“等待其他系统”。
- 恢复点和补偿点是否可审计，例如超时转人工、停止自动发布、撤销预留状态、记录原因，而不是空泛的重试口号。
- callback 是否仍被描述为主路径，polling 是否仍只作为 fallback 收敛到同一内部命令语义。
- 页面是否避免把 Saga 包装器本身写成中心角色，而是始终把注意力放在等待、恢复、补偿边界上。
