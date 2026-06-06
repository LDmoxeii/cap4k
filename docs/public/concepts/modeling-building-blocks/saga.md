# Saga

Saga 是持久化的跨步骤协调模型，用来管理无法在单个事务中完成、但又需要可靠推进的业务流程。它关注步骤状态、重试、恢复、补偿和归档，而不是把任意 callback 串成一条调用链。Saga 的存在理由是流程本身有业务含义，并且失败后的处理同样需要建模。

当一个用例跨多个聚合、外部能力或异步步骤，并且需要在系统重启、部分失败或超时之后继续推进时，应考虑 Saga。普通 callback-to-command、单次 subscriber 反应或简单 command orchestration 不一定是 Saga；只有当流程需要持久化协调、retry/recovery/compensation 语义时，Saga 才是合适的概念。

在 cap4k 中，`design.json` 支持 `saga` tag，runtime Saga 支持持久化记录、schedule、resume、retry、compensable process、compensation resume 和 archive 等路径。generator 可以生成 Saga 结构和入口骨架；每个步骤的业务含义、失败判定、补偿动作、幂等策略和恢复边界必须手写。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`PaidPublicationSaga` 是直接锚点，协调 paid publication 中的 payout hold、entitlement plan、内容发布、激活和失败补偿；`TryStartPaidPublicationCmd` 负责在满足条件时调度 Saga，并记录 Saga id。

Saga 的设计边界是跨步骤可靠协调。不要把同步函数调用、媒体处理 callback、一次性 subscriber 或普通 application command handler 改名为 Saga。Saga 也不应该替代 Aggregate 不变量；它协调多个步骤，但每个聚合自己的状态规则仍应由各自 Root 保护。

判断 Saga 是否用对时，可以看流程是否确实需要持久化状态，步骤是否可重试或可恢复，补偿是否有明确业务语义，Saga 与 Command/Subscriber 的职责是否分开，`PaidPublicationSaga` 这类 reference anchor 是否能映射到设计说明，以及生成骨架和手写流程判断是否清晰。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者把 Saga 理解为持久化跨步骤协调，而不是普通 callback 链。
Type: workflow diagram
Prompt: Draw a cap4k Saga workflow for PaidPublicationSaga. Show schedule, persisted saga state, forward steps, retry, recovery, compensation, and archive. Include Command and external capabilities as collaborators around the Saga. Use Chinese labels and preserve English identifiers.
Must show: persisted coordination, retry, recovery, compensation, PaidPublicationSaga, command trigger, cross-step state
Must avoid: implying every callback is Saga, implying Saga replaces aggregate invariants, implying cap4k writes business decisions automatically
Alt text after insertion: PaidPublicationSaga 工作流，展示持久化状态、正向步骤、重试、恢复、补偿和归档。
-->
