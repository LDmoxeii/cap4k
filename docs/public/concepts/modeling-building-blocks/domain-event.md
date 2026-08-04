# Domain Event

Domain Event 是领域模型在状态变化之后记录的不可变历史事实。它说明“业务上已经发生了什么”，例如内容已经达到发布准备状态，而不是命令别人接下来必须做什么。payload 应携带理解发生时刻所需的 ID、标量、时间、old/new value、Value Object 和不可变集合，不得携带 Aggregate、Entity、持久化 proxy 或 mutable carrier 引用。

当 Aggregate 完成一次状态转移，并且其他流程需要基于这个事实继续工作时，应附加 Domain Event。cap4k 唯一的运行时作者入口是接收一个具体事件类型的方法级 Spring `@EventListener` Handler；生成代码中名为 `*Subscriber` 的 class 只是放置 reaction methods 的 application-layer 容器，不是另一套分发 API。Handler 在当前 Command 事务和 UoW 中同步、串行、fail-fast 执行，可以发送嵌套 Command、查询 Query 或调用 Capability；处理期间产生的新事件进入下一因果 frontier，不能递归重入当前 frontier。Repository 查询观察的是查询时的当前 UoW 状态，不是事件发生时快照。

在 cap4k 中，Domain Event 位于 domain layer，`design.json` 支持 `domain_event` tag 生成事件骨架。`fields` 是生成 payload 的唯一来源；`aggregates` 只确定事件归属、package 和相关 handler/subscriber 规划，不会隐式添加 Aggregate、Entity、Strong ID 或 snapshot 字段。没有 `fields` 时会生成无 payload 的 marker event。generator 可以表达事件类型、显式字段和目录位置；事件何时产生、字段含义、订阅者如何解释这个事实，都需要手写业务语义来决定。Domain Event 与 Integration Event 的边界在于受众：Domain Event 是领域内部事实，Integration Event 是跨边界 published language。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`ContentPublicationReadyDomainEvent` 和 `ContentBehavior.kt` 展示事件在 Aggregate 状态变化之后产生；`ContentPublicationReadyDomainEventSubscriber` class 中的方法级 Handler 展示 downstream reaction 如何通过明确 Command 继续应用反应。

设计边界是“事实已发生”。生成前的 semantic type 检查拒绝直接或嵌套包含 cap4k 已知 Aggregate/Entity 的 payload；标量、Strong ID、Value Object、enum 和专用不可变 snapshot 是正常事实载体。运行时 payload validator 继续拒绝可达对象图中的持久化 Entity，是生成器之外的最终安全网，不能为了适配生成结果而放宽。普通 Domain Event 使用同步、串行、fail-fast Handler：首个失败会立即停止当前 frontier、丢弃待处理派生 frontier 并回滚本地事务。不同方法级 `@Order` 值按数值从小到大执行；相同值仍不承诺次序，所以正确性不能依赖相同 order 或注册顺序。

Handler 必须返回 `Unit/void`。`@Async`、`suspend`、`@TransactionalEventListener`、`defaultExecution=false`、多事件声明和多态订阅会在启动发现阶段被拒绝。Handler 内可以用 `askAsync()` / `callAsync()` 并行收集信息；方法返回后 Runtime 会等待当前 Handler scope 中的全部受管任务，任一失败都会使 Handler 失败。收集结果后既可以同步 `send` Command，也可以选择可靠 Command 的 `enqueue`、`schedule` 或 `delay`；需要脱离当前调用栈改变状态的工作必须使用后一类可靠调度，而不是异步 Event Handler。Domain Event 不应该是 Command 的别名，不应包含 HTTP payload，也不应承载外部系统契约。

判断 Domain Event 是否用对时，可以看事件是否由 domain model 在状态变化之后产生，命名是否表达事实，字段是否是领域语言，Event Handler 是否把它当 downstream trigger 而不是 Root 写入口，生成事件骨架与手写触发条件是否清楚分离。
