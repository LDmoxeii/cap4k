# Domain Service

Domain Service 表达不自然归属于单个 Aggregate、Entity 或 Value Object 的领域决策。它仍然是 domain layer 的业务语言，不是 application service，也不是外部集成 client。它适合承载需要多个领域对象共同参与，但又不应强行塞进某个 Root 的判断。

触发 Domain Service 的场景通常是：决策需要读取多个聚合状态、比较策略、判断资格或组合规则，但结果仍是领域判断。参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)；在 `cap4k-reference-content-studio` 中，`PaidPublicationEligibilityService` 判断 paid publication 是否具备继续条件，这个决策靠单个 `Content` 或单个 paid state 对象都不完整。

分类树排序是一个容易混淆的例子。如果同一父节点下的 sibling order 必须在一次事务里保持一致，优先考虑让 `CategoryTree`、`CategoryParent` 或类似 Root 拥有这组子节点顺序；插入节点并调整相邻兄弟位置就是这个聚合的行为。如果每个分类节点都是独立 Aggregate，Domain Service 可以计算移动方案或资格判断，但不应该伪装成单个节点聚合内部行为，也不应该自己承担持久化提交。

Domain Service 与 Aggregate、Value Object、Business Enum、Domain Event 协作。它可以返回 decision object 或领域结果，由 Command 决定后续事务组织。cap4k 的 `design.json` 支持 `domain_service` tag 来表达生成骨架；generator 生成的是位置和结构，具体决策规则、返回语义和错误边界需要手写。

在 cap4k 中，Specification 更接近聚合持久化前的约束检查，而不是 Domain Service 可以随意组合调用的通用规则库。运行时通过 `SpecificationUnitOfWorkInterceptor` 在 Unit of Work 的 `beforeTransaction` 和 `preInTransaction` 阶段检查待持久化实体，规格检查失败会拒绝提交。需要显式业务判断时，先确认它应该是 Aggregate 行为、Domain Service decision，还是 UoW 保存前约束。

设计边界是领域决策。Domain Service 不应发送 HTTP 请求、不应访问 Repository 或 Unit of Work 来承担持久化编排、不应承担 command orchestration，也不应变成所有业务逻辑的垃圾桶。常见误用包括把 application command handler 改名为 Domain Service、把外部能力封装放进 domain layer、或把本该属于 Aggregate Root 的不变量移出去。

判断 Domain Service 是否用对时，可以看它是否真有跨对象领域决策，是否仍使用领域语言，是否没有依赖 adapter/protocol，返回结果是否可被 Command 清楚处理，`domain_service` 生成骨架和手写判断是否边界清楚，以及是否没有用它逃避 Aggregate 建模。
