# Command

Command 表达一次希望改变业务状态的应用层意图。它不是任意参数对象，也不是把 controller request 原样搬进 domain layer；它应该让读者一眼看出“用户或系统想让业务发生什么变化”，例如发布内容、启动媒体处理、尝试开启 paid publication。

当一个用例需要修改 Aggregate 状态、触发领域行为、保存结果或释放事件时，应建模为 Command。Command handler 拥有这次应用层写入流程的组织权：它负责读取所需聚合，调用 Aggregate 行为方法，协调必要的 application collaborator，并把保存交给 Repository 和 Unit of Work。真正的业务不变量仍在 Aggregate 内部，不应散落在 handler 的流程判断里。

在 cap4k 中，`command` design tag 可以让 generator 生成 Command、handler 入口和稳定命名。生成骨架表达的是“这里有一个写入用例入口”；具体字段含义、权限上下文、聚合行为调用、异常分支、事件释放条件和保存顺序必须由手写逻辑完成。Command handler 应该让流程清楚，但不替代 domain model 做决定。

一次典型写入会从 Command 进入 application layer，加载 Aggregate Root，调用 `ContentBehavior.kt` 这类领域行为，借助 Repository 保存聚合，并在 Unit of Work 提交边界内完成状态持久化。领域事件可以在聚合状态变化后产生，并在提交完成后的合适阶段被 subscriber 消费。这样写入路径既能看见用例，也能保持领域规则集中。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。可以从 `PublishContentCmd` 阅读内容发布意图，从 `StartMediaProcessingCmd` 阅读媒体处理启动意图，从 `TryStartPaidPublicationCmd` 和 `PublishPaidPublicationContentCmd` 阅读 paid publication 如何把命令、聚合行为和 Saga 协作连接起来。

Command 的设计边界是一次状态改变，不是“所有业务代码的容器”。常见误用包括把查询放进 Command handler，把外部协议 DTO 直接当 Command，把多个不相关生命周期塞进一个命令，或者在 handler 中绕过 Aggregate 直接改字段。handler 可以协调，但不能让应用层流程吞掉 Aggregate 的不变量。

审查 Command 时，可以看名称是否是动词化的业务意图，handler 是否拥有清晰的写入流程，Aggregate 行为是否承担状态判断，Unit of Work save 是否在应用写入边界内完成，领域事件是否来自状态变化之后，以及生成骨架与手写业务逻辑是否容易区分。
