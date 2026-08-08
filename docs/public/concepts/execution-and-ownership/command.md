# Command

Command 表达一次希望改变业务状态的应用层意图。它不是任意参数对象，也不是把 controller request 原样搬进 domain layer；它应该让读者一眼看出“用户或系统想让业务发生什么变化”，例如发布内容、启动媒体处理、尝试开启 paid publication。

当一个用例需要修改 Aggregate 状态、触发领域行为、登记可靠异步工作或释放事件时，应建模为 Command。Command handler 负责通过 Repository 读取所需聚合、调用 Aggregate 行为并协调必要 collaborator。加载的 Aggregate 保持 managed，Factory/Repository 分别登记 root CREATE/DELETE，实际更新由 dirty checking 识别；应用代码不手动 save。外层 Command 自动创建 REQUIRED transaction、稳定化并完成 Unit of Work，嵌套 Command 加入同一事务和 UoW。

在 cap4k 中，`command` design tag 可以让 generator 生成 Command、handler 入口和稳定命名。生成骨架表达的是“这里有一个写入用例入口”；具体字段含义、权限上下文、聚合行为调用、异常分支、事件释放条件和保存顺序必须由手写逻辑完成。Command handler 应该让流程清楚，但不替代 domain model 做决定。

一次典型写入会从 Command 进入 application layer，通过 Repository 加载 Aggregate Root，调用 `ContentBehavior.kt` 这类领域行为，再由 Unit of Work 在提交边界内自动完成状态持久化。同步 Domain Event 在同一事务中按非重入因果 frontier 释放；可靠 Command 与 Integration Event 只在当前事务中登记，并在提交成功后唤醒后续处理。这样写入路径既能看见用例，也能保持领域规则集中。

本地异步工作使用 `Mediator.commands.enqueue(command)`、`schedule(command, executeAt)` 或 `delay(command, duration)` 登记可靠 Command。登记必须发生在活跃的物理事务内：Command record 与当前写入一起提交或回滚，worker 只在 after-commit 获得一次扫描提示。真正的执行时间、重试时间和恢复资格以数据库中的 `nextTryTime`、claim token 与 lease 为准；周期 claim 会恢复进程重启、提示丢失和 lease 过期留下的工作，不需要公开 polling/result API，也不依赖分布式 Locker。worker claim 后通过同步 `CommandSupervisor.send` 创建新的外层 Command、REQUIRED transaction 和 UoW，成功或失败再以当前 token 完成 fenced acknowledgement；后续失败不会回滚原事务。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。可以从 `PublishContentCmd` 阅读内容发布意图，从 `StartMediaProcessingCmd` 阅读媒体处理启动意图，并观察 Command 如何把 Repository、Aggregate 行为、Domain Event 与外部 Capability 协作连接起来。

Command 的设计边界是一次状态改变，不是“所有业务代码的容器”。常见误用包括把查询放进 Command handler，把外部协议 DTO 直接当 Command，把多个不相关生命周期塞进一个命令，或者在 handler 中绕过 Aggregate 直接改字段。handler 可以协调，但不能让应用层流程吞掉 Aggregate 的不变量。

审查 Command 时，可以看名称是否是动词化的业务意图，handler 是否拥有清晰的写入流程，Aggregate 行为是否承担状态判断，是否没有 completion-oriented `save()`，领域事件是否携带不可变历史事实，以及生成骨架与手写业务逻辑是否容易区分。
