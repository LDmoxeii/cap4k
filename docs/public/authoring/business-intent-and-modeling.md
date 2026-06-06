# Business Intent And Modeling

业务意图和建模是 authoring 螺旋的第一圈。这里先回答“这个 business slice 为什么存在、哪些事实会改变、哪些词必须稳定、哪些边界应该被保护”，再进入 generator inputs。没有这一层，`design/design.json`、schema 或生成骨架只能得到一些名字，不能得到业务判断。

[Reference Content Studio](../examples/reference-content-studio.md) 的默认意图很小：内容作者创建草稿，审核通过后触发媒体处理，媒体处理成功 callback 到来后内容发布。paid publication 是显式 opt-in 的高级路径，用 Saga 表达 payout hold、entitlement plan、发布、激活和补偿。两条路径共享内容发布语境，但不应该把 paid publication 变成默认发布规则。

## Business Intent

业务意图要先写出会被人类审查的句子，而不是先写类名。对内容发布示例，关键句子包括：

- 内容草稿必须先提交审核，再由 reviewer approve。
- 审核通过后，如果内容需要媒体处理，系统启动媒体处理任务。
- 媒体处理成功是外部事实，进入系统后要转成内部命令和领域事实。
- 默认发布路径在内容审核通过且媒体就绪后发布。
- paid publication 只有显式创建 paid draft 时才进入 Saga。

这些句子会影响后续每一层。比如“媒体处理成功是外部事实”意味着 callback 不应该伪装成 Domain Event；“默认发布路径不是 Saga”意味着 `ContentPublicationReadyDomainEvent` 后续可以是 application reaction；“paid publication 是 opt-in”意味着它不能污染默认 `Content` 发布行为。

## Ubiquitous Terms

通用语言要先稳定词义。`Content`、`MediaProcessingTask`、`ReleasePolicy`、`MediaProcessingResultSnapshot`、`PaidPublicationTask`、`ContentPublicationReadyDomainEvent`、`MediaProcessingCallbackIntegrationEvent` 这些名字不只是代码命名，它们也是作者和审查者共享语境的入口。

如果一个词在业务叙述、schema、design input、test name 和 `.http` example 中含义不同，后续生成计划通常会暴露更多错位。此时应该回到词义，而不是在 adapter 或 handler 中补转换补丁。

## Boundaries Before Inputs

建模先问边界，再写输入：

- 哪些事实必须在同一次事务中保持一致。
- 哪些对象只有值语义，没有独立生命周期。
- 哪些事实已经发生，应该表达成 Event。
- 哪些外部系统能力需要通过 application-facing contract 使用。
- 哪些规则只是当前事务内判断，哪些需要跨步骤持久化协调。

这些问题决定 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、Domain Event、Integration Event、Domain Service 和 Saga 是否成立。generator 可以根据输入生成骨架，但它不会替作者决定边界。

## Aggregate

Aggregate 是事务一致性边界，不是表的别名。默认内容发布中，`Content` 保护内容审核、媒体就绪、发布状态和发布事件；`MediaProcessingTask` 保护媒体处理任务状态、外部任务标识和结果快照；paid 路径中，`PaidPublicationTask` 记录跨步骤发布状态和补偿相关事实。

判断 Aggregate 边界时，重点看一次命令处理必须保护哪些不变量。`Content` 不应该直接修改 `MediaProcessingTask`，`MediaProcessingTask` 也不应该决定 `Content` 是否可发布；它们通过 Command、Domain Event、Integration Event、Subscriber 或 Saga 协作。

## Value Object And Business Enum

Value Object 适合表达一组必须一起理解、一起校验、一起持久化的值。参考项目中，`MediaProcessingResultSnapshot` 是 JSON-backed Value Object，用来保存媒体处理结果快照。它不是 adapter payload，也不是独立 Aggregate；它被 `MediaProcessingTask` 持有。

Business Enum 是相邻类型输入。`ReleasePolicy` 和 `MediaProcessingResultStatus` 通过 `types.enumManifest` 管理，帮助作者把有限业务选项放进稳定类型，而不是让裸整数或裸字符串在 command、schema 和 domain behavior 中漂移。

## Events

Domain Event 表达领域内部已经发生的事实，例如 `ContentPublicationReadyDomainEvent` 或 `MediaProcessingSucceededDomainEvent`。Integration Event 表达跨边界事实，例如 `MediaProcessingCallbackIntegrationEvent` 代表外部媒体平台回传结果，`ContentPublishedIntegrationEvent` 代表对外发布事实。

拆事件时先看事实是否不同，而不是看消费者数量。多个 Subscriber 可以响应同一个领域事实；如果只是后续动作不同，不需要把事实拆成多个 Event。外部 callback 进入系统时，也不应该因为最终会触发领域行为就被改名成 Domain Event。

## External Capabilities

外部能力要用业务语言命名，而不是用传输协议命名。`TriggerMediaProcessingCli` 和 `GetMediaProcessingStatusCli` 表达媒体处理能力；paid publication 中的 payout hold 和 entitlement plan client 表达付费发布能力。adapter 负责协议转换，application 只看到能力合同。

如果一个外部能力调用会影响业务状态，作者还要决定它出现在哪个用例边界：Command handler、Subscriber、Scheduled Reaction 或 Saga step。这个决定属于技术设计，但必须以业务意图和模型为基础。

## Policies

Policy 是业务规则，不是配置散点。默认发布规则、paid opt-in 规则、审核规则、媒体就绪规则和补偿规则都应该先在业务语言中说清，再决定落在 Aggregate behavior、Domain Service、Command handler、Subscriber 或 Saga 中。

当规则需要的数据都在当前事务内可得，优先放在 Aggregate 或 Domain Service 中表达。当规则需要跨时间等待、恢复、retry 或 compensation，才考虑 Saga。不要因为流程名字听起来高级，就把简单 reaction 建成 Saga。

## Feedback Signals

这些信号说明作者应该回到业务意图和建模：

- 同一个业务词在页面、输入和代码中有不同含义。
- 一个 Command 名称无法说明它改变了什么事实。
- Value Object 只是外部 DTO 的复制。
- Integration Event 被当成 Domain Event 使用。
- Saga 没有持久化进度、恢复或补偿语义。
- adapter 或 test 中出现了本应属于领域的发布条件。

这些问题不应通过局部代码补丁掩盖。它们应该进入下一圈 authoring，先修正模型，再更新技术设计和生成输入。
