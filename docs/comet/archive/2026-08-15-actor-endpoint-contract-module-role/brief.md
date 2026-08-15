# Outcome

建立一等、传输中立的 ActorEndpoint 发布契约，并为 Pipeline 增加依赖叶子 `contract` module role。一个 ActorEndpoint 表示一个可发布操作，其 Request 通过专用 Endpoint Mediator 分发；Provider 本地实现与 Consumer RPC proxy 都是该 Request 在各自进程中的 `EndpointHandler`。

同时将 Integration Event 的发布 payload 归入同一个 contract 边界，使兄弟服务只依赖纯契约模块，而不依赖 Provider 的 application、adapter、Spring 或传输实现。

# Scope

- 新增显式 Design JSON `endpoint` tactical carrier。
- 一个 ActorEndpoint 对应一个 operation，包含非空稳定 `operationName`、Request shape 和 Response shape。
- 在 canonical model 中建立一等 ActorEndpoint operation，而不是伪装成 Query、Capability 或普通 `DesignBlockModel` 分支。
- 生成一个操作级 Kotlin object 到 `contract` module；其中 Request 实现轻量 `EndpointRequest<Response>`，并包含嵌套 Request/Response 与稳定 operation identity 常量。
- 在 Runtime 增加 `EndpointHandler<Request, Response>`、`EndpointSupervisor` 和 `Mediator.endpoints.send/sendAsync`，保持现有 Request + Handler + Response 分发方式。
- Provider 进程注册本地业务 `EndpointHandler`；Consumer 进程在后续 #194 注册由生成 RPC proxy 实现的 `EndpointHandler`。HTTP/RPC Provider binding 与选择直接依赖发布契约的 Consumer 通过 `Mediator.endpoints` 分发，不直接调用或注入 proxy。需要防腐时，Consumer application 可先调用本地 `Mediator.capabilities`，由 Capability Handler 将本地语义映射为 published Endpoint Request，再转交 `Mediator.endpoints`。
- 新增 `project.contractModulePath`、`modules["contract"]`、contract package layout、planner/output ownership 和条件化缺失诊断。
- 新增最小 `cap4k-contract-api` 发布物，只容纳共享契约所需的 `EndpointRequest` 与稳定 annotation；不得引入 Spring、Mediator 实现、持久化、消息传输或完整 `ddd-core`。
- 将 `@IntegrationEvent` 及生成的 inbound/outbound Integration Event payload 迁移到 contract 边界；subscriber 和本地 reaction 继续归 application。
- 扩展 Design JSON → canonical → generated Kotlin → Analyzer design projection → Drawing Board → Design JSON 的语义 round-trip。
- 更新 capability descriptors、AgentFacts、Public Docs 和 authoring Skill 的直接与传递投影。
- 为 #193 HTTP binding 与 #194 RPC provider/client proxy 暴露稳定的 canonical identity、Request 类型和 Handler dispatch extension point。

# Non-goals

- 不生成或运行 HTTP Controller、route、codec、认证、状态码或异常映射。
- 不实现 RPC provider dispatcher、client proxy、service discovery、超时、重试或负载均衡。
- 不让业务代码直接注入或调用 Consumer proxy；proxy 必须作为本地 Endpoint Handler 接入 Mediator。Consumer 可选择直接使用 published Endpoint Request，或通过本地 Capability 建立防腐层。
- 不从 Command、Query 或 Capability 自动推导/公开 ActorEndpoint。
- 不引入大型 service interface；canonical unit 始终是单个 operation。
- 不恢复无语义的通用 `Request` marker；Endpoint 使用专用 `EndpointRequest`/`EndpointHandler` family。
- 不把 transport topology、queue/topic/group/URL 或 provider 配置写入 published contract。
- 不移动 Integration Event subscriber、本地 Command/Query reaction、业务幂等、事务或补偿逻辑。
- 不新增 Actor graph node、Endpoint-to-Command/Query relationship 或 Flow root；真实 HTTP/RPC detector 分别留给 #193/#194。
- 不新增用户自定义 Pipeline stage/order，也不恢复旧 client generator 或 bootstrap 能力。

# Acceptance examples

- **A1**：配置 `project.contractModulePath` 后，`ProjectConfig.modules` 与 Agent project facts 都出现 `contract -> <path>`；未选择 contract-owned artifact 时该路径可省略，选择后缺失会给出稳定明确诊断。
- **A2**：一个显式 `endpoint` 输入产生唯一的一等 canonical operation，保留 `operationName`、有序 Request/Response fields、嵌套 DTO、类型、nullability 与 defaults；空白/重复 operation identity 或非法 shape 在 source/canonical 阶段失败。
- **A3**：默认 `endpoint` artifact 生成到 contract module 的 checked-in source；产物是操作级 object，其 Request 实现轻量 `EndpointRequest<Response>`，包含 Response 和稳定 identity 常量，不引用 domain/application/adapter 类型。
- **A4**：`Mediator.endpoints.send(request)` 与 `sendAsync(request)` 按 Request concrete type 分发到唯一 `EndpointHandler<Request, Response>`，并保持 validation、invocation scope、execution context 和异步失败传播等现有 application dispatcher 约束。
- **A5**：Provider 的本地 Endpoint Handler 可将 published Request 映射到本地 Command/Query；Consumer 侧 RPC proxy 可作为同一 Request 的本地 Endpoint Handler。Consumer 可直接调用 `Mediator.endpoints`，也可由本地 Capability Handler 先完成防腐映射后再调用 `Mediator.endpoints`；任何路径都不直接调用 proxy。
- **A6**：`cap4k-contract-api` 可独立编译与发布，只依赖 Kotlin/JDK 基础；包含 `EndpointRequest` 和共享 Integration Event annotation，但不包含 Spring、Mediator/Supervisor 实现、持久化或 transport runtime。
- **A7**：生成的 inbound/outbound Integration Event payload 归 `moduleRole=contract`，继续保留稳定 `eventName` 与 direction metadata；integration subscriber 仍归 application，既有 topology/ack/retry/inbox 语义不变。
- **A8**：ActorEndpoint 与迁移后的 Integration Event 通过真实 Kotlin Analyzer 恢复到 Drawing Board，并可作为普通 Design JSON 重新生成，保持 operation/event identity、artifact selection 和 semantic shapes。
- **A9**：仅生成或本地分发 ActorEndpoint contract 不增加 Analyzer Graph 节点、关系、Flow root 或 flow 数量；HTTP/RPC Actor evidence 必须等待真实 binding detector。
- **A10**：capability descriptor、provider registry、AgentFacts、Public Docs 与 authoring Skill 都从生产合同投影新的 carrier/module ownership；现有 domain/application/adapter planner 输出保持不变，consumer fixture 只依赖 contract artifact 与所选 endpoint/RPC runtime。

# Constraints and invariants

- `contract` 是发布语言的依赖叶子：不得依赖本服务 domain、application、adapter、persistence 或 transport implementation。
- ActorEndpoint published Request/Response 不复用内部 Command/Query 类型；Provider Handler 中的映射属于 Adapter/Application 边界。
- Endpoint 是专用 Mediator category，不使用无语义的通用 Request，也不把 Endpoint Request 当成 Command、Query 或 CapabilityCall。
- Consumer proxy 是 `EndpointHandler` 的 transport 实现，不是业务代码直接依赖的 client service。Consumer application 是否直接依赖 published Endpoint contract 是本地边界选择：简单协作可直接使用，需隔离外部语言时使用 Capability 防腐层。
- operation identity 是显式 published identity，不由 Kotlin 包名、HTTP route、RPC service name 或方法名隐式推导。
- 一个 ActorEndpoint 只描述一个操作；接口聚合只能是后续 projection，不能成为 canonical source of truth。
- Integration Event 的 `eventName`、inbound/outbound direction 与 provider topology 继续遵守现有 canonical/runtime 合同；移动输出 owner 不改变语义。
- contract artifacts 使用 `CHECKED_IN_SOURCE`，归 AUTHORING lane，由现有 `cap4kPlan`/`cap4kGenerate` 管理，不进入 generated-source 清理根。
- Analyzer 只有在观察真实 binding/handler call evidence 后才能建立 Actor causal entry；声明 contract 或 Mediator 本地 dispatch 本身不是 transport entry evidence。
- 当前无外部用户，不提供旧包路径、旧 module ownership 或旧 DSL 的兼容桥。

# Decisions

- Change 对应 GitHub #192，工作分支为 `feature/actor-endpoint-contract-module-role`，后续 #193/#194 分别拥有 HTTP 与 RPC binding。
- canonical tag 使用 `endpoint`；primary artifact family 使用 `endpoint`。
- ActorEndpoint 使用显式非空 `operationName`，与 Kotlin type identity 分离；HTTP/RPC 后续绑定消费该 identity，但不得把 route/topology 反写进 contract。
- 共享发布结构采用“一操作一个 object + Request/Response”，不采用“一服务多方法”的 canonical service interface。
- Request 实现专用 `EndpointRequest<Response>`；本地 Provider implementation 与 Consumer proxy 都实现 `EndpointHandler<Request, Response>`，统一由 `Mediator.endpoints` 调用。Consumer 可直接发送 Endpoint Request，也可通过本地 Capability Handler 映射后发送。
- 新增轻量 `cap4k-contract-api`，只承载跨服务必须共享的 marker/annotation；Handler、Supervisor 与 Mediator 接入仍归 `ddd-core`。
- Integration Event payload 在本 Change 一并迁移到 contract module；subscriber 与业务 reaction 保持 application-owned。
- Analyzer design projection / Drawing Board 支持 ActorEndpoint round-trip；Analyzer Graph/Flow 在本 Change为 verified-no-change。

# Capability impact matrix

| Surface | Status | Evidence / reason |
| --- | --- | --- |
| Runtime | `modified` | `cap4k-contract-api` owns the lightweight `EndpointRequest` and shared Integration Event annotation; `ddd-core` adds the Endpoint Handler/Supervisor family and `Mediator.endpoints`; core-starter wiring and focused runtime tests cover binding, selection, validation, scope, sync/async propagation, and Provider/Consumer-shaped Handler dispatch. This is a local runtime seam only, not an HTTP/RPC implementation. |
| Generator | `modified` | Pipeline API/config, Design JSON source, canonical assembler, design planner/renderer, artifact layout, provider registry, and Gradle plugin now project `endpoint` plus contract-owned Integration Event payloads into checked-in `contract` outputs; functional generation and compile fixtures exercise the production tasks. |
| Analyzer | `modified` | Compiler analysis and IR projection recover endpoint operation metadata and semantic Request/Response shapes for Drawing Board round-trip. Graph/Flow transport-entry semantics are deliberately unchanged; the production compiler + IR source + Flow generator negative functional regression verifies no Actor root, causal endpoint relationship, or flow-count increase from declaration/metadata/local dispatch alone. |
| AgentFacts | `modified` | Pipeline production descriptors and Agent snapshot projection expose the `contract` module role and endpoint/contract artifact ownership; snapshot/task tests verify facts remain derived from production contracts rather than a handwritten duplicate catalog. |
| Public Docs | `modified` | Architecture, dependency, authoring, generator, runtime ownership, Mediator, Endpoint, Integration Event, Design JSON, DSL, and outputs documentation describe the shipped contract/module/runtime seam and explicitly exclude HTTP/RPC binding, proxy generation, serialization, networking, discovery, timeout, and retry. |
| Skill | `modified` | Authoring references add the endpoint tactical carrier, contract ownership, dependency-leaf rule, and Mediator dispatch boundary while keeping HTTP/RPC binding outside current support. |

# Open questions

- 无。

# Verification expectations

- 针对 Design JSON、canonical assembly、semantic roles、duplicate/blank identity 与 Drawing Board normalization 的单元测试。
- 针对 EndpointSupervisor/Handler selection、validation、scope、sync/async failure semantics 的 Runtime tests。
- 针对 contract module path 条件验证、planner ownership/path、provider registry、capability descriptors 与 Agent snapshot 的 focused tests。
- 一个真实 Gradle functional fixture，生成并编译 contract module、Provider Handler module 与 Consumer proxy fixture，并证明 Consumer 的直接 Endpoint 调用和 Capability 防腐调用最终都通过 `Mediator.endpoints` 分发。
- 扩展真实 design round-trip gate，覆盖 `endpoint` 以及 contract-owned inbound/outbound Integration Event。
- Analyzer Graph/Flow regression 证明 contract declaration 与本地 Endpoint dispatch 不制造 transport causal entry evidence。
- 运行 capability contract facts export/validation、Skill/Runtime/public-doc guards、`git diff --check` 与受影响 Gradle checks；最终完整检查范围由 Build/Verify 阶段根据实际改动确定。



