# Repository

Repository 是 Aggregate 的读取和访问边界。它让 application layer 以聚合为单位加载业务对象，而不是直接暴露底层持久化细节。业务代码唯一的聚合仓储入口是 `Mediator.repositories`，不注入 generated Spring Data repository。cap4k 的 Repository 能力不提供保存操作：Command 中加载的 Aggregate 保持 managed 并由当前 write UoW 自动观察，实际变化由 Hibernate dirty checking 识别；root 删除通过 `RepositorySupervisor.remove` 明确登记。Repository 不是业务决策桶，也不是任意查询 service。

当 Command handler 需要取得某个 Aggregate Root 并调用它的行为时，应通过 Repository 完成读取。状态变化完成后直接返回，由外层 Command 自动稳定化和提交；没有手动 save 或 UoW 登记步骤。一个 Command 可以读取多个 Aggregate，但只有真实变化的 Aggregate 进入最终持久化变化集。Repository 不应该替代 Aggregate 判断状态变化是否允许，也不应该把跨聚合业务规则集中到数据访问方法里。

在 cap4k 中，DB Source 根据 schema 事实驱动 generator 生成 framework-owned、provider-private 的 JPA carrier。carrier 只负责把 `Mediator.repositories` 接到 `AbstractJpaRepository`，不是业务代码可注入的公开接口，也不是 Design JSON 输入。具体查询条件与异常语义由业务代码通过 `Mediator.repositories` 表达。框架不再通过 `AggregateLoadPlan` 强制展开完整对象图：Command 事务保证按需 lazy navigation，UoW 只检查已初始化或实际发生 queued/orphan 变化的 owned 关系。

Repository 与层级协作的关系要保持清楚：domain layer 通过 Aggregate 保护业务不变量，application layer 在 Command handler 中组织用例，Repository 负责聚合级访问，外层 UoW 负责提交边界。Query handler 也可以在 Handler 全程的只读事务里使用 Repository 并按需 lazy navigation，但列表、报表和跨形状读取通常仍应使用 Criteria、projection 或独立查询组件。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以把 `PublishContentCmd` 等写入命令与 `ContentBehavior.kt`、`ContentFactory.kt` 一起阅读，观察内容聚合如何被加载、执行业务行为，并由外层 Command 自动提交真实变化；读取状态的 `GetContentDetailQry` 展示 Query 如何组织读取而不承担写入。

Repository 的设计边界是 Aggregate access。常见误用包括在 Repository 方法里写发布规则、把多个不相关聚合拼成一个“万能仓储”、让 Repository 直接调用外部 capability，或者把复杂 read projection 都塞进聚合仓储。需要业务决策时应回到 Aggregate、Domain Service、Command handler 或显式 provider-owned orchestration 的合适位置。

审查 Repository 时，可以看它是否以 Aggregate Root 为中心，是否避免承载业务不变量，是否没有变成任意查询服务，是否与 Unit of Work 的提交边界配合清楚，以及生成访问骨架和手写存储语义是否容易区分。
