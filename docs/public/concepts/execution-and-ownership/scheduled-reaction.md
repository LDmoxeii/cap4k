# Scheduled Reaction

Scheduled Reaction 是由时间触发的应用层反应，用来处理轮询、超时、补偿检查、恢复推进或周期性同步。它和 Subscriber 一样属于“事情发生之后或条件成熟之后的反应”，但触发来源不是事件投递，而是时间、调度或周期性扫描。

当外部系统无法稳定 callback、业务需要超时恢复、异步流程需要轮询状态，或系统重启后需要继续推进未完成工作时，应考虑 Scheduled Reaction。它适合把“到时间检查一次并决定是否触发后续用例”的逻辑显式命名，而不是把这些路径藏进 controller、query handler 或随机后台线程。

在 cap4k 项目中，Job 是 Scheduled Reaction 的实现 surface。Job 可以承载调度入口、加载待处理记录、调用外部 capability、判断是否需要发送 Command 或推进 Saga。Job 本身不是本组概念页要展开的独立概念；读者应把它理解为时间触发 reaction 的落点，而不是新的业务建模中心。

Scheduled Reaction 与其他边界的协作要保持应用层意图清楚。需要改变业务状态时，Job 应委托给 Command 或明确的 application behavior；需要读取外部状态时，可以通过 external capability anti-corruption layer 调用 `client` / `client-handler`；需要持久化跨步骤协调时，可以与 Saga 配合。轮询结果不应绕过 Aggregate 或 Unit of Work 直接写业务状态。

generator 可以提供 Job 或 handler 的稳定结构，但不会自动决定轮询频率、恢复条件、幂等策略、失败重试或状态推进含义。这些判断来自手写逻辑。尤其在 recovery 路径中，代码应能解释为什么某条记录可以再次尝试、为什么某个外部结果可以推进内部状态，以及重复执行时如何保持安全。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。`MediaProcessingPollingFallbackJob` 是时间触发 reaction 的直接锚点；它可以和 `StartMediaProcessingCmd`、`GetMediaProcessingStatusCli`、`FakeMediaProcessingCli` 以及媒体处理 callback 相关入口一起阅读，理解 callback 不可靠时如何用 polling fallback 补足恢复能力。

Scheduled Reaction 的设计边界是时间触发的可靠检查和推进。常见误用包括把 Job 写成所有业务规则的集合，把轮询结果直接改成内部状态，忽略重复执行，或者让 scheduled path 与 callback path 推进出不同语义。审查时可以看触发条件是否清楚，幂等和恢复是否可解释，状态改变是否委托给 Command/application boundary，以及 Job 是否只作为实现 surface 出现。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 Scheduled Reaction 如何用 Job 承载时间触发、轮询和恢复推进。
Type: workflow diagram
Prompt: Draw a cap4k scheduled reaction workflow for MediaProcessingPollingFallbackJob. Show 时间触发, 查询待处理记录, 调用 external capability, 幂等检查, delegating to Command or application behavior, and recovery path. Use Chinese labels and preserve English identifiers.
Must show: Scheduled Reaction, Job implementation surface, polling, recovery, idempotency, external capability, Command delegation
Must avoid: presenting Job as a standalone domain concept page, implying polling bypasses application write boundary, implying generator chooses business recovery rules automatically
Alt text after insertion: MediaProcessingPollingFallbackJob 工作流，展示时间触发、轮询外部状态、幂等检查、恢复路径和 Command 委托。
-->
