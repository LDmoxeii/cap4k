# Query

Query 表达一次读取和观察业务状态的应用层意图。它回答“业务事实如何呈现给调用者”，而不是要求系统改变状态。Query 可以为页面、API、CLI 或后台流程提供读取结果，但它不应该偷偷修改 Aggregate、推进流程或释放业务事件。

当一个用例只需要查看内容详情、媒体处理状态、paid publication 状态，或把多个读取来源整理成适合展示的结构时，应建模为 Query。Query handler 拥有覆盖 validation、interceptor、Handler、lazy navigation 和 DTO mapping 的只读事务，可以选择 read model、projection、Repository 或 adapter data source，但必须保持无业务状态 mutation，并返回 DTO/Value，而不是泄露 lazy Entity graph。

在 cap4k 中，`query` design tag 可以让 generator 生成 Query 与 handler 的稳定入口。生成骨架负责把读取用例放到可发现的位置；读取字段、筛选语义、权限上下文、read model 选择和错误表达需要手写。Query handler 可以为调用者准备结果，但不应把写入逻辑藏在“读取时顺便更新”的路径里。

Query 与 Command 的协作边界来自意图差异。Command 通过 Repository 和 Aggregate 行为改变状态并由 Unit of Work 自动提交；Query 面向读取模型组织观察结果。Command 不调用 Query，需要当前写入判断时直接使用 Repository。Query 可以同步嵌套另一个 Query 并复用只读执行边界，但不能从 Query 内启动 `askAsync()`，避免执行器与 Caller Runs 形成不同事务快照。

`Mediator.queries.askAsync(query)` 使用与 `ask()` 相同的阻塞 Handler 形态，把调用调度到有界 Query executor 以支持调用方并行组装多个结果。队列饱和时默认 Caller Runs，因此 async 表示并行机会，而不保证切换线程或立即返回；所有失败通过 `CompletionStage` 表达。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以阅读 `GetContentDetailQry`、`GetMediaProcessingStatusQry`、`GetPaidPublicationStatusQry`，以及 adapter/application/queries/content/read 下的 query handlers，观察 read model 如何服务内容详情和状态查看。

Query 的设计边界是读取表达，不是任意查询工具箱。常见误用包括在 Query handler 里保存聚合、发送可靠 Command、推进外部编排状态、修复脏数据，或者为了方便把复杂业务决策放在读取端。需要改变事实时，应改用 Command 或明确的 reaction 流程。

审查 Query 时，可以看名称是否表达观察意图，handler 是否没有业务状态副作用，read model 是否服务调用场景，错误处理是否不掩盖写入需求，以及生成骨架与手写读取语义是否保持清晰。
