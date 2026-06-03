# Domain Service

Domain Service 表达不自然归属于单个 Aggregate、Entity 或 Value Object 的领域决策。它仍然是 domain layer 的业务语言，不是 application service，也不是外部集成 client。它适合承载需要多个领域对象共同参与，但又不应强行塞进某个 Root 的判断。

触发 Domain Service 的场景通常是：决策需要读取多个聚合状态、比较策略、判断资格或组合规则，但结果仍是领域判断。参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)；在 `cap4k-reference-content-studio` 中，`PaidPublicationEligibilityService` 判断 paid publication 是否具备继续条件，这个决策靠单个 `Content` 或单个 paid state 对象都不完整。

Domain Service 与 Aggregate、Value Object、Business Enum、Domain Event 协作。它可以返回 decision object 或领域结果，由 Command 决定后续事务组织。cap4k 的 `design.json` 支持 `domain_service` tag 来表达生成骨架；generator 生成的是位置和结构，具体决策规则、返回语义和错误边界需要手写。

Specification 是 Domain Service 附近的模式，用于表达可组合、可命名、可测试的规则判断。需要时可以在 Domain Service 或 Aggregate 行为中使用 Specification，但不要因为想“显得领域化”而把简单条件抽成一堆空壳规格。

设计边界是领域决策。Domain Service 不应发送 HTTP 请求、不应保存 Repository、不应承担 command orchestration，也不应变成所有业务逻辑的垃圾桶。常见误用包括把 application command handler 改名为 Domain Service、把外部能力封装放进 domain layer、或把本该属于 Aggregate Root 的不变量移出去。

判断 Domain Service 是否用对时，可以看它是否真有跨对象领域决策，是否仍使用领域语言，是否没有依赖 adapter/protocol，返回结果是否可被 Command 清楚处理，`domain_service` 生成骨架和手写判断是否边界清楚，以及是否没有用它逃避 Aggregate 建模。
