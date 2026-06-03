# Factory

Factory 负责创建时的业务规则。它把“一个对象怎样从输入变成合法初始状态”的知识集中起来，包括必填校验、默认状态、输入规范化、初始子对象组装和创建事件准备。Factory 不是构造函数包装器；如果它只是把参数原样传给 constructor，就没有提供领域价值。

当创建逻辑开始包含默认状态、派生字段、枚举选择、初始生命周期判断或多字段一致校验时，应考虑 Factory。Command 可以收集输入并调用 Factory，但不应在 application layer 手写一堆分散的创建规则，再把半成品塞进 Aggregate。

Factory 位于 domain layer，并通常与 Aggregate Root、Value Object 和 Business Enum 协作。cap4k generator 可以生成 Factory 类型、输入 payload 形状或相邻目录骨架；创建规则、输入 normalization、默认状态和异常语义必须手写。Repository 不属于 Factory，它只负责保存创建完成后的 Aggregate。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`ContentFactory.kt` 是直接锚点，展示创建内容草稿时如何处理 `ContentFactory.Payload`、默认 `ReleasePolicy`、初始状态和输入字段，而不是只把参数转发给 `Content` constructor。

设计边界是创建规则。已有对象的状态转移不应该继续塞进 Factory；跨聚合流程也不应由 Factory 编排。常见误用包括把 Factory 写成静态 helper、把 adapter payload 原样作为 Factory input、在 Factory 中调用外部服务、或让 Factory 保存 Aggregate。

使用 Factory 时，保持创建规则集中、默认状态明确、输入经过领域语义 normalization。Command 只负责组织调用，生成骨架和手写创建逻辑分开，Factory 也不要承担持久化和后续流程编排。
