# Repository

Repository 是 Aggregate 的访问边界。它让 application layer 以聚合为单位加载和保存业务对象，而不是直接暴露底层持久化细节。Repository 不是业务决策桶，也不是任意查询 service；它的职责是保护聚合访问入口，让写入流程围绕 Aggregate Root 展开。

当 Command handler 需要取得某个 Aggregate Root、调用它的行为并保存变化时，应通过 Repository 完成访问。Repository 可以隐藏存储实现、标识解析和持久化细节，但不应该替代 Aggregate 判断状态变化是否允许，也不应该把跨聚合业务规则集中到数据访问方法里。

在 cap4k 中，generator 可以根据聚合模型生成 Repository 接口或稳定访问骨架。生成部分表达的是聚合访问契约；具体查询条件、加载策略、保存时机、异常语义以及与 Unit of Work 的配合需要结合项目手写。Repository 与 Unit of Work 共同服务 application write boundary，但二者不是同一个概念。

Repository 与层级协作的关系要保持清楚：domain layer 通过 Aggregate 保护业务不变量，application layer 在 Command handler 中组织用例，Repository 负责聚合级访问，Unit of Work 负责提交边界。Query handler 可以使用适合读取的 read model，不应把 Repository 扩展成所有展示查询的集合。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以把 `PublishContentCmd` 等写入命令与 `ContentBehavior.kt`、`ContentFactory.kt` 一起阅读，观察内容聚合如何被加载、执行业务行为并保存；读取状态的 `GetContentDetailQry` 则展示了 Query 路径不应被混进聚合写入仓储。

Repository 的设计边界是 Aggregate access。常见误用包括在 Repository 方法里写发布规则、把多个不相关聚合拼成一个“万能仓储”、让 Repository 直接调用外部 capability，或者把复杂 read projection 都塞进聚合仓储。需要业务决策时应回到 Aggregate、Domain Service、Command handler 或 Saga 的合适位置。

审查 Repository 时，可以看它是否以 Aggregate Root 为中心，是否避免承载业务不变量，是否没有变成任意查询服务，是否与 Unit of Work 的提交边界配合清楚，以及生成访问骨架和手写存储语义是否容易区分。
