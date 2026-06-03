# Strong ID

Strong ID 是 typed identifier，用值对象形式表达业务身份。它的目标是让 `ContentId`、`AuthorId`、`MediaProcessingTaskId` 这类身份在类型系统中彼此区分，避免裸 `String`、`Long` 或 UUID 在不同业务对象之间漂移。

当多个标识底层类型相同，但业务含义完全不同，或 command、event、repository 之间频繁传递身份值时，应优先使用 Strong ID。它让方法签名、事件字段和持久化映射更接近领域语言，也让读者能直接看出某个 ID 属于哪个聚合或外部对象。

Strong ID 与 Value Object 同属值语义家族，通常位于 domain layer，并被 Aggregate、Entity、Domain Event、Integration Event 和 Repository 接口引用。cap4k 可以通过类型输入和 source metadata 表达 Strong ID 相关生成结果；generator 负责稳定的类型和映射骨架，业务命名、边界归属、跨上下文转换和是否暴露给 published language 仍由人工决定。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，读者可以沿聚合、命令和事件代码观察 ID 类型如何流动。Strong ID 页面不要求单独运行示例；它的判断应和 Aggregate、Value Object、Repository 页面一起阅读。

设计边界是类型安全的身份，不是给每个普通字段包一层类型。常见误用包括在应用层重新拆回裸 `String`、让两个不同业务身份共用同一个 Strong ID、把外部系统 ID 直接当作内部 Aggregate ID，或为了迁就 adapter payload 放弃领域层 typed identifier。

判断 Strong ID 是否用对时，可以看它是否消除了裸 ID 漂移，命名是否体现业务身份，是否被 Repository 和 Event 字段一致使用，外部 ID 与内部 ID 是否有清晰转换边界，以及生成类型与手写业务规则是否没有互相混淆。
