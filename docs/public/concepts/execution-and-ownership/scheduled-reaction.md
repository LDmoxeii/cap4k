# Scheduled Reaction

Scheduled Reaction 是由时间触发的应用层反应，用来处理轮询、超时、补偿检查、恢复推进或周期性同步。它和 Subscriber 一样属于“事情发生之后或条件成熟之后的反应”，但触发来源不是事件投递，而是时间、调度或周期性扫描。

当外部系统无法稳定 callback、业务需要超时恢复、异步流程需要轮询状态，或系统重启后需要继续推进未完成工作时，应考虑 Scheduled Reaction。它适合把“到时间检查一次并决定是否触发后续用例”的逻辑显式命名，而不是把这些路径藏进 controller、query handler 或随机后台线程。

在 cap4k 项目中，Job 是 Scheduled Reaction 的实现 surface。Job 可以承载调度入口、加载待处理记录、调用外部 Capability，并判断是否需要发送 Command。Job 本身不是新的业务建模中心。

Scheduled Reaction 与其他边界的协作要保持应用层意图清楚。需要改变业务状态时，Job 应委托给 Command；需要读取外部状态时，可以通过 external capability anti-corruption layer 调用 `capability` / `capability-handler`。如果流程需要持久化进度或补偿，应在技术设计中选择显式 provider-owned orchestration，而不是伪造框架内置骨架。

cap4k 当前不生成 Scheduled Reaction 或 Job。Job 是项目手写的 application implementation surface；generator 只为当前支持的 Command、Query、Capability、Subscriber 及相关 handler wiring 提供骨架。轮询频率、恢复条件、幂等策略、失败重试和状态推进含义都来自手写逻辑。尤其在 recovery 路径中，代码应能解释为什么某条记录可以再次尝试、为什么某个外部结果可以推进内部状态，以及重复执行时如何保持安全。

Analyzer 会把真实的 Spring `@Scheduled` method 识别为 Time 类入口节点 `temporaltriggermethod`。只有该 method 直接发送 Command 时，才会产生 `TemporalTriggerMethodToCommand` evidence 并进入默认 Flow；只发送 Query、调用 Capability 或执行纯技术逻辑不会形成 Flow。这个 detection 仅观察已有代码，不提供 scheduler runtime、Job generator、cron、misfire、retry 或跨入口 process stitching。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。`MediaProcessingPollingFallbackJob` 是时间触发 reaction 的直接锚点；它可以和 `StartMediaProcessingCmd`、`GetMediaProcessingStatus` 及媒体处理 callback 相关入口一起阅读，理解 callback 不可靠时如何用 polling fallback 补足恢复能力。

Scheduled Reaction 的设计边界是时间触发的可靠检查和推进。常见误用包括把 Job 写成所有业务规则的集合，把轮询结果直接改成内部状态，忽略重复执行，或者让 scheduled path 与 callback path 推进出不同语义。审查时可以看触发条件是否清楚，幂等和恢复是否可解释，状态改变是否委托给 Command/application boundary，以及 Job 是否只作为实现 surface 出现。
