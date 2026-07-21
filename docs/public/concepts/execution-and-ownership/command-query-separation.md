# Command Query Separation

Command Query Separation 是把“改变业务状态的意图”和“观察业务状态的读取”分开表达。应用层用例边界应清晰区分写入路径和读取路径：写入路径围绕命令、聚合行为、Unit of Work 提交和事件释放展开；读取路径围绕查询意图、read model 和展示所需数据展开。

cap4k 这样拆分，并不是要求项目引入完整 CQRS 基础设施，也不是要求一定建立独立数据库、消息复制或复杂 read side。它首先是一条代码组织和职责边界：不要把所有 application behavior 都塞进一个传统 service class，再让同一个方法集合同时承担校验、写入、查询、事件反应和外部调用。

当一个用例需要改变内容状态、启动媒体处理、推进 paid publication，或者释放领域事件时，应走 Command 路径。当一个用例只是读取内容详情、媒体处理状态或 paid publication 状态时，应走 Query 路径。这样的分离让 application layer 更容易被阅读：看到 `PublishContentCmd` 就知道它表达写入意图，看到 `GetContentDetailQry` 就知道它表达读取观察。

在 cap4k 项目中，Command handler 可以通过 Repository 加载 Aggregate，调用 domain layer 暴露的行为，再通过 Unit of Work 记录持久化意图并完成提交。Query handler 则可以面向 read model 或适合展示的数据结构组织读取，不应偷偷修改业务状态。Mediator 可以在运行时把 Command 或 Query 路由到对应 handler，但它只是入口 facade，不是把业务流程混在一起的 orchestration engine。

generator 可以依据 `command`、`query` 等 design tags 生成稳定骨架、命名和入口位置；业务决策、聚合行为、查询含义、异常处理和协作顺序仍属于手写逻辑。生成骨架降低的是组织成本，不会替代团队决定“这件事是写入还是读取”。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以把 `PublishContentCmd`、`StartMediaProcessingCmd`、`TryStartPaidPublicationCmd` 与 `GetContentDetailQry`、`GetMediaProcessingStatusQry`、`GetPaidPublicationStatusQry` 对照阅读，观察写入和读取如何分别落在 application workflow 中。

设计边界的核心问题是“这个用例是否改变业务事实”。常见误用包括把查询方法放进 Command handler，为了复用把写入和读取塞进一个大 service，或者把 Command Query Separation 理解成必须建设完整 CQRS 系统。审查时可以看命名是否表达意图，写入是否通过聚合和提交边界完成，读取是否保持无副作用，以及 handler 是否让层级协作保持清晰。
