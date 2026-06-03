# Aggregate

Aggregate Root 是领域模型的事务一致性边界。它负责保护一组强相关对象的业务不变量，决定哪些状态变化可以在同一次命令处理中提交，哪些协作必须通过事件、应用层命令或外部能力拆开。Aggregate 不是数据库表的别名，也不是把所有相关字段放进一个大对象；它的边界来自业务规则对一致性的要求。

当一个用例需要在一次写入中同时判断状态、修改内部对象、记录领域事实并保证不变量时，就应该从 Aggregate 开始建模。Command 进入应用层后，应加载 Aggregate Root，调用它暴露的行为方法，再通过 Repository 持久化。内部 Entity 和 Value Object 可以参与判断，但不能绕过 Aggregate Root 成为独立写入口。

在 cap4k 项目中，Aggregate 位于 domain layer。generator 可以依据输入生成聚合目录、实体骨架、Repository 接口或事件骨架等稳定结构；真正的状态转移、业务不变量、事件触发条件和异常处理必须由手写逻辑表达。Repository 拥有聚合级持久化入口，Unit of Work 管理提交时机，Command 负责组织事务内的读取、行为调用和保存。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`ContentBehavior.kt` 是阅读 Aggregate 行为的直接锚点，展示 `Content` 如何围绕发布准备、review approval、媒体处理和 `ContentPublicationReadyDomainEvent` 保护边界。

设计边界要围绕“一次事务必须一致”的问题来划定。跨聚合协作不应该靠一个 Aggregate 直接修改另一个 Aggregate；可以通过 Domain Event、Integration Event、Saga 或应用层 command 编排完成。常见误用包括把 Aggregate 做成贫血数据容器、让内部 Entity 暴露独立保存入口、把外部 HTTP 协议字段放进 Aggregate、或为了代码复用把多个业务生命周期塞进一个 Root。

判断 Aggregate 是否用对时，可以看不变量是否集中在 Root 行为中，Command 是否只通过 Root 进入写入，Repository 是否以聚合为单位保存，领域事件是否发生在状态变化之后，以及生成骨架与手写业务逻辑的边界是否清晰。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 Aggregate Root 是事务一致性边界和命令写入口。
Type: concept map
Prompt: Draw a concept map for an Aggregate Root in cap4k. Put Aggregate Root at the center, internal Entity and Value Object inside its boundary, Command and Repository outside the boundary, Domain Event emitted after state change, and Unit of Work around persistence. Use Chinese labels and preserve English identifiers.
Must show: Aggregate Root boundary, invariants, transaction consistency, Repository ownership, Command entry, Domain Event after state change
Must avoid: showing internal Entity as an independent write entry, implying Repository saves arbitrary objects outside aggregate ownership, arrows that violate Clean Architecture dependency rules
Alt text after insertion: Aggregate Root 作为事务一致性边界，Command 进入 Root，Repository 按聚合保存，状态变化后产生 Domain Event。
-->
