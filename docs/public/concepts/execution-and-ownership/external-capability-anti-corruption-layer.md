# External Capability Anti-Corruption Layer

External Capability Anti-Corruption Layer 是 application layer 声明外部能力、adapter layer 完成协议转换的边界。它让内部用例说“我需要媒体处理能力”或“我需要查询外部处理状态”，而不是让 domain model 或 Command handler 直接依赖外部 HTTP、SDK、消息格式或供应商字段。

当业务流程需要调用外部系统、读取外部状态、发送 callback payload 或把外部结果转成内部事实时，应把外部能力建模成清晰的 capability。application side 看到的是稳定的内部语义；adapter side 负责把内部 request 转成外部协议，把外部 response 转成内部 result，并处理认证、错误码、超时和兼容性差异。

在 cap4k 中，`client` 和 `client-handler` 是 generator 表达外部能力防腐层的常见方式。`client` 可以声明 application 需要的外部能力接口，`client-handler` 可以落在 adapter 侧实现协议转换。生成骨架提供命名、位置和连接面；外部系统语义、错误映射、重试策略、payload 转换和业务可接受结果必须手写。

这条边界保护 Clean Architecture 的依赖方向。domain layer 不应直接调用外部 capability；Aggregate 可以释放领域事件或表达需要的状态变化，但外部交互应由 application workflow、Subscriber、Scheduled Reaction 或 Saga 通过声明的 capability 完成。adapter 依赖内部声明，协议细节停留在外层。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。可以阅读 `TriggerMediaProcessingCli`、`GetMediaProcessingStatusCli`、`*CliHandler` 和 `FakeMediaProcessingCli`，观察 application 声明媒体处理能力，adapter handler 承担协议转换和 fake implementation，Command 或 Job 只面对内部能力语义。

External Capability Anti-Corruption Layer 的设计边界是外部协议与内部模型之间的翻译。常见误用包括把外部 response DTO 放进 Aggregate，把 HTTP error code 当成领域状态，让 domain service 直接调用 SDK，或者把 adapter handler 写成业务决策中心。外部事实需要进入内部状态时，应先转换成明确的 application result、Command、Integration Event 或领域可理解的数据。

审查外部能力边界时，可以看 capability 名称是否表达业务需要，`client` 是否位于 application 可依赖的位置，`client-handler` 是否只做协议和适配，外部错误是否被转换成内部可处理语义，以及 generator 骨架是否没有被误解为自动完成业务判断。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 application 声明 external capability，adapter 通过 client-handler 做协议转换。
Type: architecture diagram
Prompt: Draw a cap4k external capability anti-corruption layer architecture diagram. Show application layer declaring client, adapter layer implementing client-handler, external system outside the boundary, and Command, Scheduled Reaction, or Subscriber using the internal capability. Use Chinese labels and preserve English identifiers.
Must show: application client declaration, adapter client-handler, protocol conversion, external system, Command or Job caller, Clean Architecture dependency direction
Must avoid: domain layer depending on external SDK, adapter making core business decisions, arrows that make external protocol leak into Aggregate
Alt text after insertion: 外部能力防腐层架构图，展示 application 的 client 声明、adapter 的 client-handler 协议转换，以及外部系统隔离在边界外。
-->
