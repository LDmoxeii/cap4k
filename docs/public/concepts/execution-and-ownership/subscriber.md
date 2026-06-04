# Subscriber

Subscriber 是事件发生之后的反应入口。它接收已经发生的事实，并决定是否要触发后续应用行为、外部通知或状态推进。Subscriber 不应该替代 Command handler 做原始写入意图，也不应该把“收到事件”误写成“重新决定过去发生了什么”。

cap4k 中需要区分两类边界。Domain Event Subscriber 监听 domain layer 释放的领域事实，例如内容已经达到可发布条件；Inbound Integration Event Subscriber 监听系统外部传入的 typed integration event，例如外部媒体处理结果。前者围绕内部领域事实做 downstream reaction，后者围绕 typed external fact 进入 application boundary，并需要做事实解释、幂等、语义翻译和可信度处理。

当 reaction 会改变本系统业务状态时，Subscriber 通常应委托给明确的 Command 或 application behavior，而不是在事件处理方法里直接写完整业务流程。这样可以复用 Command handler 的聚合加载、Unit of Work、事件释放和错误处理边界。Subscriber 可以判断是否需要反应，但不应绕过 Aggregate 行为或 Unit of Work 提交边界直接修改业务对象。

幂等是 Subscriber 的基本要求，因为事件可能重复投递、重试或在恢复路径中再次出现。Domain Event Subscriber 应能识别同一领域事实是否已经处理过；Inbound Integration Event Subscriber 应处理外部消息 id、业务 key、状态版本或结果快照，避免重复推进。幂等策略属于手写业务逻辑，generator 只能提供入口骨架。

在 cap4k 中，`domain_event` 和 `integration_event` design tags 可以生成事件与 subscriber 相关的结构。生成骨架表达“这里有一个事件反应入口”；反应条件、幂等记录、失败处理、delegation 到哪个 Command、何时调用外部 capability，都必须由手写逻辑明确。Mediator 可以作为进入 Command/Query 的 routing facade，但不是 Subscriber 的业务判断替身。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。可以阅读 `ContentPublicationReadyDomainEventSubscriber` 观察 Domain Event 之后的 downstream reaction，也可以把 `MediaProcessingCallbackIntegrationEvent` 作为 inbound integration event 的锚点，理解外部事实如何进入系统边界并转交应用行为。

Subscriber 的设计边界是事件之后的可靠反应。常见误用包括在 subscriber 中重新实现发布命令、把所有异步逻辑都塞进一个 listener、忽略重复消息，或者把外部 payload 直接当成内部领域事实。审查时可以看事件事实是否命名清楚，幂等是否可解释，状态改变是否通过 Command/application boundary，生成骨架与手写反应逻辑是否分离。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者区分 Domain Event Subscriber 与 inbound Integration Event Subscriber 的边界和后续委托。
Type: concept map
Prompt: Draw a cap4k subscriber concept map. Show Domain Event Subscriber receiving 内部领域事实 from Domain Event, inbound Integration Event Subscriber receiving 外部集成事实 from Integration Event, both applying 幂等检查, then delegating to Command or application behavior when state changes are needed. Use Chinese labels and preserve English identifiers.
Must show: Domain Event Subscriber, inbound Integration Event Subscriber, idempotency, Command delegation, application boundary, Domain Event, Integration Event
Must avoid: implying subscriber directly mutates aggregate fields, implying external payload is automatically trusted as domain fact, arrows that violate Clean Architecture dependency rules
Alt text after insertion: Subscriber 概念图，展示领域事件和入站集成事件的不同入口、幂等检查，以及需要改变状态时委托给 Command。
-->
