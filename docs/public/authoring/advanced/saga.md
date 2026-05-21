# Saga

对应示例：[内容发布示例：Saga](../examples/content-publication-saga.md)

## 什么时候需要它

- 你的流程已经不是“一个命令推进一个聚合行为”就能讲清，而是需要把长时间跨度内的协调、重试、恢复、补偿持久化下来。
- 这个流程天然接受最终一致性，而不是要求一次事务里同步完成所有写入。
- 你已经能明确列出前向步骤、补偿点、恢复点和人工修复边界，而不是只想找个地方把多步逻辑塞进去。

在教学项目里，默认链路仍然是：内容送审、媒体处理启动、外部系统通过 callback 回传结果，polling 只是兼容性备用路径。当前 cap4k 已实现的 Saga runtime，优先支持的是 compensation-oriented persisted coordination，也就是把前向步骤、重试恢复、补偿意图、补偿回放持久化下来；它不是一个通用的 waiting-style Saga，也不是“等外部 callback 回来后从某个 step 精确 resume”的完整 workflow engine。

## 为什么默认路径不够

默认路径的前排武器是聚合、命令、领域事件和阶段化命令链。对共享教学项目的大多数需求，这已经够用：callback 回来就转内部命令，polling 作为备用入口补同一条命令语义，不需要额外上 Saga。

Saga 是长流程 / 最终一致性协调概念，不是默认路径前排。当前 runtime 这次真正落地的是“补偿导向”的切片：它擅长把已完成的前向步骤记下来，在显式补偿请求后按逆序回滚、失败重试、定时恢复、必要时进入人工修复。它没有把 Saga 扩展成一个泛化的 callback-step resume workflow，所以不要把“未来可能会有等待式工作流”提前写成今天已经承诺的能力。

## 推荐形态

- 只有当流程同时满足“长时间跨度、最终一致性、补偿或恢复要求”时，才考虑 Saga。
- 对 `Content` / `MediaProcessingTask` 的发布路径，先用一个明确分界：
  - 如果审核结果、媒体处理结果等事实在决定发布时都已经可用，那仍然留在领域逻辑或 [Domain Service](domain-service.md)。
  - 只有当你必须把协调状态跨时间保存下来，并且要在失败、拒绝、撤销、人工介入后执行恢复或补偿，Saga 才是对的模型。
- 先把默认主链保持清楚：`Content` 仍然只管内容生命周期，`MediaProcessingTask` 仍然只管媒体处理生命周期。
- Saga 负责协调阶段，不负责吞掉聚合边界。当前更推荐的写法是把“需要补偿的前向步骤”显式写成 `execCompensableProcess(...)`，把“业务已经知道必须停主流程并反向回滚”的信号写成 `requestCompensation(...)`。
- callback 仍然是媒体结果进入系统的主路径；polling 仍然只是备用补位。即使引入 Saga，也不应该把 polling 反客为主。
- 每个步骤都要能回答三件事：成功后推进什么、需要回滚时补偿什么、哪些情况会落到人工修复边界。

## 当前 runtime 能力切片

- 需要运行时管理补偿的前向步骤，优先用 `execCompensableProcess(processCode, request, compensationCode, compensationRequest)`。
- `execCompensableProcess(...)` 会在前向步骤成功后，立刻根据前向结果构造并持久化最终补偿请求，而不是等到将来补偿时再临时推导。
- 业务代码在确认主流程不能继续时，用 `requestCompensation(code, reason)` 显式进入补偿；这是一个 Saga 控制信号，不是“抛个异常让框架猜”。
- `requestCompensation(...)` 会立即停止 forward path，并进入同一个持久化状态机里的补偿流程。
- forward retry exhaustion 默认进入 `EXHAUSTED`，不会自动触发 compensation。是否补偿，必须来自显式业务意图或 operator 意图。
- 调度恢复和手工恢复现在优先服务于 persisted replay：Saga 可能继续 forward replay，也可能在 `COMPENSATION_REQUESTED` / `COMPENSATING` 状态下继续 compensation replay。
- 当前自动进入 `MANUAL_REPAIR_REQUIRED` 的已实现路径，要按“补偿扫描到已执行步骤但缺少 compensation request”来理解，而不是把它表述成“补偿重试耗尽后的统一自动终态”。
- waiting-style Saga、外部 callback-step resume、把 Saga 当成通用等待工作流引擎，不在这次能力切片里。

## 仍然保留的教学主次关系

- callback 仍然是外部媒体结果进入系统的主路径。
- polling 仍然只是 fallback，用来补同一条内部命令语义，不提升成主要真相源。
- 这次引入补偿型 Saga，不代表教学项目推荐用 polling 驱动长流程，更不代表 callback 主路径失效。

## 常见误用

- 默认链路还没拆清楚，就直接上 Saga，试图把 `Content` 发布和 `MediaProcessingTask` 状态推进一起包进一个大编排。
- 把 Saga 当成 application 的超级 handler，里面什么都做，最后谁负责业务真相都说不清。
- callback 一套阶段推进，polling 另一套阶段推进，导致同一个长流程有两份不同的事实机。
- 明明只是同步校验或短链路命令，却为了“以后可能更复杂”提前引入 Saga。
- 明明需要补偿，却继续用 handler 里的手写 `try/catch` 临时拼逆向命令，而不是用 `execCompensableProcess(...)` + `requestCompensation(...)` 把运行时契约写清楚。
- 把 forward retry exhaustion 当成补偿触发器，仿佛“多试几次失败了就自动回滚”已经是框架默认语义。当前 runtime 不是这样。
- 把 waiting-style Saga、外部 callback resume、人工确认工作流这些未来扩展，写成当前公开文档已经稳定支持的能力。

## 审计检查点

- 当前问题是否真的属于长流程 / 最终一致性协调，而不是普通阶段化命令链。
- 不引入 Saga 时，是否已经尝试过用 [Default Happy Path](../default-happy-path.md) 的命令、事件和阶段拆分表达。
- 如果作者把发布路径建模成 Saga，审阅者是否能看到明确的 persisted coordination、恢复或补偿理由，而不是把已知事实判断过度升级。
- Saga 是否只负责协调阶段，而不是篡改 `Content`、`MediaProcessingTask` 的聚合职责。
- callback 主路径和 polling 备用路径进入长流程后，是否仍收敛为一致的内部命令语义。
- 需要补偿的前向步骤是否显式建模为 `execCompensableProcess(...)`，而不是在失败时再临时拼补偿动作。
- 补偿触发是否来自 `requestCompensation(...)` 或 operator 侧同一状态机控制，而不是把 retry exhaustion 偷换成补偿意图。
- 文档是否明确说明 waiting-style Saga / 外部 callback-step resume 不在当前 runtime 切片里。
