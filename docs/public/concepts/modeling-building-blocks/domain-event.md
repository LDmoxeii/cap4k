# Domain Event

Domain Event 是领域模型在状态变化之后记录的事实。它说明“业务上已经发生了什么”，例如内容已经达到发布准备状态，而不是命令别人接下来必须做什么。Domain Event 的名字应是过去式或事实式语言，字段应表达订阅者理解该事实所需的最小领域信息。

当 Aggregate 完成一次状态转移，并且其他流程需要基于这个事实继续工作时，应发布 Domain Event。事件由 domain model 产生，通常在 Aggregate 行为中附加，之后由 application layer 或 runtime 交给 Subscriber 处理。Subscriber 可以发送 command、调用 Mediator、写 read model 或触发后续流程，但不应把事件当成直接修改原 Aggregate 的捷径。

在 cap4k 中，Domain Event 位于 domain layer，`design.json` 支持 `domain_event` tag 生成事件骨架。generator 可以表达事件类型、字段和目录位置；事件何时产生、字段含义、订阅者如何解释这个事实，都需要手写业务语义来决定。Domain Event 与 Integration Event 的边界在于受众：Domain Event 是领域内部事实，Integration Event 是跨边界 published language。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`ContentPublicationReadyDomainEvent` 和 `ContentBehavior.kt` 展示事件在 Aggregate 状态变化之后产生；`ContentPublicationReadyDomainEventSubscriber` 展示 downstream subscriber 如何继续 immediate publication 或 paid publication 分支。

设计边界是“事实已发生”。Domain Event 不应该是 command 的别名，不应包含 HTTP request payload，不应强迫所有订阅者同步完成，也不应承载外部系统契约。常见误用包括在状态变化前发布事件、在 subscriber 中回头修补原聚合不变量、或把每个方法调用都机械建成事件。

判断 Domain Event 是否用对时，可以看事件是否由 domain model 在状态变化之后产生，命名是否表达事实，字段是否是领域语言，Subscriber 是否把它当 downstream trigger 而不是 Root 写入口，生成事件骨架与手写触发条件是否清楚分离。
