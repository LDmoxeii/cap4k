# Execution Context And Invocation Scope

ExecutionContext 与 InvocationScope 解决两类不同问题。ExecutionContext 说明“这次调用来自谁、属于哪条追踪或环境链路”；InvocationScope 说明“当前代码正在执行 Command、Query、Capability 还是 Domain Event Handler”。前者可以跨框架拥有的异步和可靠边界传播，后者只在当前线程和调用栈内生效。

ExecutionContext 是不可变的 typed element snapshot。actor、correlation、trace、environment 或 tenant hint 可以通过稳定 key 和版本化 codec 登记，但这些值只用于归因与传递，不等于认证、授权或数据库租户隔离。可靠 Command、可靠 Domain Event、Integration Event 和 RPC 分别声明允许传播的元素；payload 与 context envelope 保持分离。

`askAsync()`、`callAsync()`、可靠 worker 和框架 transport adapter 会自动捕获、安装并清理 ExecutionContext。用户自己的 executor、`CompletableFuture.supplyAsync`、Reactor、coroutine 或 SDK callback 不会被无感接管，需要显式使用框架提供的 task/executor wrapper。传播内容不包括 Unit of Work、EntityManager、Spring transaction、InvocationScope、事件运行时状态或任意 ThreadLocal。

InvocationScope 是严格 LIFO 的本地 guard。它实现以下调用边界：Command 可以同步嵌套 Command、调用 Capability，但不能调用 Query；Query 可以同步嵌套 Query、调用 Capability，但不能发送 Command 或在 Query 内启动 `askAsync()`；Capability 只能组合 Capability，不能进入 Repository、Factory、Command、Query 或 UoW；Domain Event Handler 通过嵌套 Command 修改当前事务状态，不直接操作 Repository。

Caller Runs 不会绕过这些规则。队列饱和时，`callAsync()` 即使在 Command 线程执行，也会暂时安装 CAPABILITY scope；Repository 和 Factory 根据栈顶 scope 拒绝访问。异步任务不会继承调用方 InvocationScope，而是建立自己的目标 scope，因此线程复用和负载变化不会改变应用语义。

可靠边界在登记时捕获 snapshot，retry 和 archive 保留原始值，不使用 worker 当时的上下文覆盖来源归因。持久化记录没有 context 的历史数据按 EMPTY 读取。已知元素格式错误、版本不支持、重复或用于错误边界时必须失败；外部入口可以忽略未知元素以支持滚动升级，但不会把未知 opaque element 继续转发。

设计或审查上下文传播时，应分别回答：哪些 attribution element 可以跨哪种边界，谁负责 ingress authentication，异步任务由谁拥有，调用类型是否允许访问持久化，以及 scope 是否在 CompletionStage 完成前已经清理。不要把 ExecutionContext 当全局可变 map，也不要用它隐藏业务参数或授权决策。
