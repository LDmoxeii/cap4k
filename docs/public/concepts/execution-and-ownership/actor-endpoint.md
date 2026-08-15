# Actor Endpoint

Actor Endpoint 是一个 transport-neutral published operation：边界双方共享稳定 operation identity 与 Request/Response shape，但 HTTP、RPC、CLI 或其他 Actor binding 不属于该契约本身。Design JSON 使用 `endpoint` tag，因为 `endpoint` 是作者显式建模的可调用 operation；`Actor` 则是 Analyzer/Flow 对真实触发来源的概念分类。

一个 Endpoint 对应一个 operation，而不是一个聚合多个方法的 service interface。它必须声明非空 `operationName`，并通过 `fields` 与 `resultFields` 定义有序 Request/Response。默认 generator 在 `project.contractModulePath` 指向的 dependency-leaf contract module 生成一个 operation object：嵌套 `Request` 实现 `EndpointRequest<Response>`，嵌套 `Response` 表达结果，`OPERATION_NAME` 保留稳定 published identity。该 contract 不实现 Command、Query 或 CapabilityCall，也不包含 Spring、route、RPC service、client、discovery、timeout、retry 或 persistence 行为。

运行时通过 `EndpointHandler<Request, Response>`、`EndpointSupervisor` 和 `Mediator.endpoints` 保持 Request + Handler + Response 模型。Provider 进程注册本地 Handler，并可在其中调用 `Mediator.commands` / `Mediator.queries` 转换到内部用例；Consumer 进程在后续 RPC 能力中可注册 proxy Handler。两侧业务代码都调用 `Mediator.endpoints.send/sendAsync`，不直接注入或调用 Handler/proxy。

Consumer 是否直接使用 published language 是本地边界选择。协作紧密时可以直接创建 Endpoint Request；需要防腐时，application 先调用本地 Capability，由 adapter-owned Capability Handler 映射为 Endpoint Request，再通过 `Mediator.endpoints` 调用。Capability 保护本地语言，Endpoint 保持双方共享的 published language。

当前已提供的是 Endpoint contract generation、contract module role、Endpoint Mediator family，以及 Analyzer Design Projection / Drawing Board round-trip。当前不提供 HTTP Actor binding、RPC Provider dispatcher 或 Consumer proxy generation。仅声明或本地 dispatch Endpoint 也不会产生 Analyzer Graph Actor node、causal relationship 或 Flow root；这些证据必须等待 Analyzer 观察到真实 transport binding。
