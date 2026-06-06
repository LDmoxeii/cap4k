# Query

Query 表达一次读取和观察业务状态的应用层意图。它回答“当前业务事实如何呈现给调用者”，而不是要求系统改变状态。Query 可以为页面、API、CLI 或后台流程提供读取结果，但它不应该偷偷修改 Aggregate、推进流程或释放业务事件。

当一个用例只需要查看内容详情、媒体处理状态、paid publication 状态，或把多个读取来源整理成适合展示的结构时，应建模为 Query。Query handler 拥有读取组织权，可以选择合适的 read model、projection、repository read method 或 adapter data source，但必须保持无业务状态 mutation。

在 cap4k 中，`query` design tag 可以让 generator 生成 Query 与 handler 的稳定入口。生成骨架负责把读取用例放到可发现的位置；读取字段、筛选语义、权限上下文、read model 选择和错误表达需要手写。Query handler 可以为调用者准备结果，但不应把写入逻辑藏在“读取时顺便更新”的路径里。

Query 与 Command 的协作边界来自意图差异。Command 通过 Aggregate 行为改变状态并在 Unit of Work 中提交；Query 面向读取模型组织观察结果。Mediator 可以统一路由 Command 和 Query，但路由统一不代表职责混合。读取路径保持简单，写入路径保留事务和领域事件语义。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以阅读 `GetContentDetailQry`、`GetMediaProcessingStatusQry`、`GetPaidPublicationStatusQry`，以及 adapter/application/queries/content/read 下的 query handlers，观察 read model 如何服务内容详情和状态查看。

Query 的设计边界是读取表达，不是任意查询工具箱。常见误用包括在 Query handler 里保存聚合、发送外部命令、推进 Saga、修复脏数据，或者为了方便把复杂业务决策放在读取端。需要改变事实时，应改用 Command 或明确的 reaction 流程。

审查 Query 时，可以看名称是否表达观察意图，handler 是否没有业务状态副作用，read model 是否服务调用场景，错误处理是否不掩盖写入需求，以及生成骨架与手写读取语义是否保持清晰。
