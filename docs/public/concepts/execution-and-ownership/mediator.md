# Mediator

Mediator 是运行时路由 facade，用来把 Command、Query、Capability call 或 published Endpoint Request 交给对应 Handler。它让调用方不必直接知道每个 Handler 的具体类名和装配方式，但它不是业务编排引擎，也不是 domain layer 的依赖。

当 controller、subscriber、job、adapter entry 或 consumer application 需要触发行为时，可以通过 `Mediator.commands`、`Mediator.queries`、`Mediator.capabilities` 或 `Mediator.endpoints` 进入明确分类。Mediator 不暴露 generic `requests` 或应用侧 `uow` 逃生口；四个 family 各自保留独立调用规则。

Query、Capability 与 Endpoint 都通过阻塞 Handler 同时支持同步和异步 Supervisor API。`askAsync()`、`callAsync()`、`sendAsync()` 使用框架有界 executor 和 ExecutionContext 传播；它们提供可控并行机会，不承诺线程切换或强制取消。异步 Command 则是可靠登记和新事务执行，不能退化成 Caller Runs。

Endpoint 是 published operation dispatch category。一个生成的 `Endpoint.Request` 实现轻量 `EndpointRequest<Response>`，调用方使用 `Mediator.endpoints.send(request)` 或 `sendAsync(request)`；运行时按具体 Request 类型选择唯一 `EndpointHandler<Request, Response>`。Provider 进程中的 Handler 可以把 published Request 转换为本地 Command/Query；Consumer 进程中的 Handler 可以在后续 RPC binding 中成为 transport proxy。无论哪一侧，业务代码都不直接注入或调用 Handler/proxy。

简单的 Consumer 可以直接依赖 Provider 的 published contract 并调用 `Mediator.endpoints`。需要防腐时，Consumer application 先通过 `Mediator.capabilities` 使用自己的业务语言，由 adapter-owned Capability Handler 映射为 published Endpoint Request，再调用 `Mediator.endpoints`。Capability 与 Endpoint 因此可以组合，但两者不是同一个契约：Capability 表达本地 application-facing external capability，Endpoint 表达边界双方共享的 published operation。

在 cap4k 中，generator 可以为 Command、Query、Capability、Endpoint 与各自受支持的 Handler surface 生成稳定入口。生成骨架表达 message-to-handler 的连接面；业务规则、状态判断、外部能力调用时机和事务边界仍在 Handler、Aggregate、Domain Service 或 Subscriber 的手写逻辑中。当前 Endpoint 能力只包含 contract、Handler/Supervisor 与 `Mediator.endpoints`；HTTP/RPC Provider binding、RPC Consumer proxy、route、service discovery、timeout 和 retry 尚未提供。

Mediator 与 Clean Architecture 的关系要保持克制。domain layer 不应依赖 Mediator 来“调用外部世界”或“派发下一个用例”；Aggregate 应只表达领域行为和事件。application layer 可以使用 Mediator 做明确路由；需要持久化跨事务状态时，应组合可靠 Command、Integration Event 或选定的外部编排 provider。

Mediator 的设计边界是 routing，不是 orchestration。常见误用包括让 Mediator 在 domain object 中出现，把一串业务步骤藏在通用 dispatch helper 里，直接调用 Consumer proxy，或者让调用方发送含糊的 message 再由 Mediator 决定业务含义。业务意图必须在 Command、Query、Capability、Endpoint 或 event reaction 中命名清楚。

审查 Mediator 使用时，可以看 message 名称是否表达明确语义，handler 是否仍是业务流程的阅读入口，domain layer 是否没有依赖 Mediator，Provider/Consumer Endpoint 是否都经 `Mediator.endpoints`，Subscriber 或 Job 是否只通过明确 delegation 进入应用层，以及生成路由骨架是否没有被误解成自动业务编排。
