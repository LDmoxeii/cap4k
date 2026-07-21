# Entity

Entity 是聚合边界内带身份的领域对象。它的相等性主要由身份决定，而不是所有字段的值是否完全相同。Entity 适合表达在一个 Aggregate 生命周期内持续存在、会经历状态变化、并需要被业务规则稳定引用的对象。

触发 Entity 建模的典型场景是：某个对象不是 Aggregate Root，但它在 Root 内有自己的身份、状态和生命周期，例如内容聚合内的步骤、条目或局部记录。它应由 Aggregate Root 创建、修改和删除，不能拥有独立 command 写入口，也不应该被应用层绕过 Root 单独保存。

Entity 位于 domain layer，并与 Aggregate Root、Value Object、Domain Event 协作。Root 决定 Entity 的生命周期，Entity 可以封装局部行为，但跨 Entity 的不变量和对外可见的状态转移仍应由 Root 统一保护。cap4k generator 可以生成聚合内实体结构和字段骨架；业务行为、身份规则、生命周期约束和事件条件必须手写。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可通过聚合目录阅读 Entity 如何嵌在 Root 边界下；Entity 的公共锚点不必单独成为运行入口，可沿 `ContentBehavior.kt` 或相邻聚合代码观察内部对象如何被 Root 管理。

设计边界是“内部身份”而不是“独立资源”。如果对象需要独立 Repository、独立事务一致性或独立外部 API 写入口，它可能应成为另一个 Aggregate；如果对象没有身份且只表达值组合，它更像 Value Object。常见误用包括把 Entity 当作 DTO、把 Entity 暴露给 adapter layer 直接修改、或为每个数据库子表都机械创建独立写服务。

使用 Entity 时，保持它只在 Aggregate Root 边界内被写入，身份稳定，生命周期由 Root 控制。跨对象不变量不要散落到 application layer，生成字段骨架之外的业务行为也应由手写代码表达。
