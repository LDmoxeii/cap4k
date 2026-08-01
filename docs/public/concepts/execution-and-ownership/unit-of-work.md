# Unit of Work

Unit of Work 是外层 Command 拥有的应用写入边界。它关注一次物理事务中哪些聚合变化、managed persistence enrichment、同步 Domain Event frontier、可靠 Command 和 Integration Event 登记应作为同一个应用结果稳定化并提交，而不是解释 JPA、事务代理或数据库内部机制的细节。

Command handler 加载 Aggregate、调用领域行为并返回后，外层 Coordinator 自动反复执行候选变化识别、managed persistence enrichment、最终变化识别、provider flush 与同步事件 frontier，直到状态稳定。普通应用代码不需要调用 `save()`、`persist()` 或 `flush()`。Query 不创建 write UoW。

Managed persistence enrichment 是持久化稳定化的一部分：先识别业务候选变化，再让 qualifier-owned Enricher 处理其声明的 managed fields，最后重新识别最终变化。审计时间和审计操作者是标准 policy，而不是 extension point 本身。干净的已加载 Aggregate 不进入 enrichment，因此读取不会仅为审计产生 UPDATE。

每个 Enricher 只能收到自己 qualifier 对应的 field handles。框架在每次调用前后独立比较 Hibernate provider-property 变化，拒绝超出声明 mutation footprint 的写入。不同 qualifier 之间不承诺顺序；需要协调的字段应由同一 qualifier 使用 slots 统一处理。子 Entity 的变化可以 enrich 自己的字段，但当前实现不会因为子 Entity 单独变化就强制推进 Aggregate Root 的 version 或 audit fields。

在 cap4k 中，同一物理事务只有一个 UoW Context 和一个外层 Coordinator。嵌套 Command 与同步 Domain Event Handler 可以继续修改聚合，但只登记下一轮工作，不能独立提交或递归释放事件。同步失败 fail-fast 并回滚整个事务；提交后的可靠工作拥有独立失败域。

Unit of Work 不属于 domain dependency。Aggregate 不应为了保存自己而依赖提交机制；它只表达状态变化和领域事实。application layer 负责组织用例，Repository 负责聚合访问，Unit of Work 负责提交边界，Subscriber 或后续 reaction 在事件可消费后继续工作。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。阅读 `PublishContentCmd`、`StartMediaProcessingCmd` 时，可以关注命令流程如何先调用 `ContentBehavior.kt` 或相关 application collaborator，再把持久化意图、稳定化、提交和事件释放交给框架写入边界。

Unit of Work 的设计边界是“这次写入何时成为一个完成的业务结果”。常见误用包括把它写成 ORM 教程，把每个 Repository 方法都当成独立提交点，让 domain model 直接控制事务，或者在 Query 中为了缓存、统计而悄悄修改业务状态。需要恢复、重试或跨事务推进时，应使用可靠 Command、Integration Event、Scheduled Reaction 或显式 provider-owned orchestration，而不是扩大单次 Unit of Work。

审查 Unit of Work 时，可以看 Command handler 是否依靠外层自动提交边界，Repository 加载的 managed Aggregate 是否由框架观察，Factory 创建和 Repository 删除是否形成明确 root intent，事件释放是否与提交时机匹配，以及 domain/application 代码是否没有直接控制 UoW 或 provider flush。
