# Unit of Work

Unit of Work 是应用层写入的提交边界。它关注一次 Command 处理中哪些聚合变化、事件释放和持久化动作应作为同一个应用写入结果被提交，而不是解释 JPA、事务代理或数据库内部机制的细节。读者可以把它理解为 application write commit boundary。

当 Command handler 已经加载 Aggregate、调用领域行为并准备保存时，Unit of Work 负责把这些变化纳入清晰的提交时机。它让写入流程不必在每个步骤都急着落库，也避免事件在状态尚未可靠提交前被当作已经完成的事实。Query 路径通常不需要这样的提交语义，因为 Query 不改变业务状态。

在 cap4k 中，Unit of Work 与 Repository、Command handler 和 Domain Event 释放共同构成写入路径。generator 可以提供稳定骨架和运行时接入点，但“哪些变化属于同一次业务提交”“失败后如何表达”“事件何时可以被 downstream reaction 消费”等判断仍要由手写应用逻辑和领域规则明确。

Unit of Work 不属于 domain dependency。Aggregate 不应为了保存自己而依赖提交机制；它只表达状态变化和领域事实。application layer 负责组织用例，Repository 负责聚合访问，Unit of Work 负责提交边界，Subscriber 或后续 reaction 在事件可消费后继续工作。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。阅读 `PublishContentCmd`、`StartMediaProcessingCmd`、`TryStartPaidPublicationCmd` 时，可以关注命令流程如何先调用 `ContentBehavior.kt` 或相关 application collaborator，再把保存和事件释放交给应用写入边界。

Unit of Work 的设计边界是“这次写入何时成为一个完成的业务结果”。常见误用包括把它写成 ORM 教程，把每个 Repository 方法都当成独立提交点，让 domain model 直接控制事务，或者在 Query 中为了缓存、统计而悄悄修改业务状态。需要恢复、重试或跨步骤推进时，可能应由 Saga、Subscriber 或 Job 表达，而不是扩大单次 Unit of Work。

审查 Unit of Work 时，可以看 Command handler 是否有明确提交边界，Repository save 是否服务聚合变化，事件释放是否与提交时机匹配，domain layer 是否没有依赖事务实现，以及文档或代码是否避免把 Unit of Work 误讲成 JPA internals。
