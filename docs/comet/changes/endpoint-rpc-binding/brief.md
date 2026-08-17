# Outcome

在已接受的 transport-neutral Endpoint published contract 与 Spring MVC Provider binding 之上，交付 sibling-service 场景的首个生产级 Endpoint RPC binding：Provider 通过稳定 `serviceId + operationName` 接收入站 RPC 并只经 `Mediator.endpoints` 调用现有本地 `EndpointHandler`；Provider build 从同一 Endpoint canonical model 生成独立、可发布但不属于默认 DDD 分层的 Consumer client artifact，使 Consumer 进程自动获得每个 published Request 对应的 concrete remote `EndpointHandler<Request, Response>`。业务代码只构造 published Request 并调用 `Mediator.endpoints.send/sendAsync`，不定义、注入或直接调用 Feign、RPC client、transport stub 或生成 proxy。

首个 backend 采用 unary HTTP/JSON RPC，而不是公开 Feign/gRPC service interface：固定 RPC endpoint、versioned envelope、typed codec、route resolver 与 generic `EndpointTransportInvoker` 属于 transport runtime；Feign、gRPC 或 service-discovery middleware 未来只能作为 invoker/backend 内部实现，不改变业务调用面或 Endpoint contract。

# Scope

- 新增 transport-neutral `ddd-endpoint-rpc`，定义 typed Provider binding、Consumer invocation SPI、wire codec/envelope、remote failure taxonomy 与稳定 identity validation；不修改 `EndpointRequest`、`EndpointHandler`、`EndpointSupervisor`、`Mediator.endpoints` 的已接受语义。
- 新增首个生产 backend `ddd-endpoint-rpc-http` 与 `cap4k-ddd-endpoint-rpc-http-starter`。Provider 以固定 `POST /cap4k/endpoints/rpc` 接收 versioned JSON envelope；Consumer 通过静态 route map 默认实现的 `EndpointRpcRouteResolver` 与 HTTP invoker 发起同步 unary request/response。
- 三个 RPC 模块不是同层并列：`ddd-endpoint-rpc` 是与现有 Endpoint HTTP binding 相对应的 RPC semantic/runtime ABI；`ddd-endpoint-rpc-http` 是其首个 HTTP/JSON backend；starter只负责Spring Boot assembly。generated `endpoint-client`只编译依赖`ddd-endpoint-rpc`，不得依赖HTTP backend或starter；Consumer `start`通过starter选择具体backend。
- RPC envelope 只携带 protocol version、`serviceId`、`operationName`、typed payload、允许跨 `RPC` boundary 的 ExecutionContext 与 transport success/failure information；published Endpoint Request/Response 仍是唯一业务 wire schema，不创建 API Payload、transport DTO tactical role 或隐式业务 response wrapper。
- Provider activation 收集 `EndpointRpcProviderBinding<Request, Response>`，验证 generated operation owner、`OPERATION_NAME`、Request/Response generic coherence、`serviceId + operationName` 唯一性以及当前 Provider service identity；解码后必须安装 external ExecutionContext scope并调用 `Mediator.endpoints.send`，不得直接查找/调用 Provider Handler或发送本地 Command/Query。
- Consumer remote Handler 委托 generic `EndpointTransportInvoker`；该 invoker拥有 route resolution、codec、network invocation、deadline/timeout、auth/request customization 与 sanitized failure mapping。`ddd-core` 不做远程探测、fallback、discovery 或透明重试。
- 扩展 RPC 能力专用的 Gradle packaging role `project.endpointClientModulePath`，投影为 `endpoint-client`。它不是官方默认四模块拓扑中的第五个 DDD layer，也不承载 checked-in 业务代码；仅在启用 `generators.endpointRpc`、需要发布可复用 Consumer artifact 时成为必需输出。配置要求 non-blank stable `serviceId` 与 non-empty selected `operationNames`；只有被显式选择的 Endpoint 才获得 RPC Provider binding 与 Consumer proxy，避免自动发布所有内部消息或所有 Endpoint。
- `cap4kGenerateSources` 从同一 canonical Endpoint model 与 RPC binding configuration 生成：既有 adapter module 中的 typed Provider binding registration；feature-scoped `endpoint-client` packaging module 中每 operation 一个 concrete remote `EndpointHandler<Request, Response>` 与聚合 auto-configuration。client artifact 依赖 contract 与 RPC runtime，contract 不反向依赖 client/runtime；remote Handler 的逻辑职责仍是 Consumer outbound adapter，独立 module 只解决跨仓发布与多 Consumer 复用。
- generated client artifact 通过 Spring Boot auto-configuration 自动注册 remote Handler；Consumer assembly 只需选择 client artifact、HTTP RPC starter及 `serviceId -> base URI`/timeout/auth配置。业务/application code 不注入 generated Handler、auto-configuration、invoker或HTTP client。
- Provider generated registration与Consumer generated proxy均为 framework-owned `GENERATED_SOURCE`；稳定 auto-configuration metadata作为受管生成资源。Endpoint contract本身继续是 `CHECKED_IN_SOURCE` / AUTHORING lane；不新增 public task。
- 默认不自动 retry。连接、响应超时或响应丢失可能对应已执行 Command；在没有单独 idempotency contract前，任何 retry只能由Consumer assembly显式提供的 invoker decorator负责，且不成为Endpoint contract或默认行为。
- Provider HTTP RPC endpoint参与应用现有Servlet filter/Spring Security chain；Consumer凭据/headers由assembly-owned request customizer提供。credential、token、raw header、URI、payload、provider exception message/stack均不得进入 generated contract、AgentFacts、diagnostics或remote exception。
- Analyzer新增真实 RPC Provider entry evidence：`endpointrpcproviderbinding` node及 `EndpointRpcProviderBindingToCommand|Query` relationships，只从真实 typed Provider registration、generated provenance与独立 Provider Handler的handle-reachable Command/Query invocation联合推导。
- Command-oriented RPC Provider binding成为默认 Actor Flow root；Query-oriented binding只保留raw Graph。Consumer proxy、contract-only、Handler-only、local `Mediator.endpoints` dispatch、copied operation literal、缺失registration或伪造relationship均不是Actor entry。
- HTTP 与 RPC Provider binding 复用同一 transport-neutral operation evidence、Request/Response coherence、Mediator-only dispatch 与 Analyzer Handler join 规则；HTTP 的 method/path/mapper/response policy 与 RPC 的 serviceId/envelope/client/routing 仍保持协议专属。同步 Runtime、Generator、Renderer、Analyzer、Flow、AgentFacts、Public Docs与repo-local authoring Skill；HTTP Provider binding与API Payload退役边界保持不变。

# Non-goals

- 重新设计或重命名 Endpoint published contract、恢复 `ActorEndpoint` product surface、generic Request、API Payload或自动公开 Command/Query/Capability。
- 让业务代码定义、注入或直接调用 Feign interface、gRPC stub、RPC service object、generated proxy或`EndpointTransportInvoker`。
- 在本 change 交付 OpenFeign、gRPC、Spring Cloud discovery/load-balancer、WebFlux、streaming、multipart、binary、bidirectional RPC或universal transport DSL。
- 在 `ddd-core` 或 contract中加入service discovery、address、timeout、retry、auth、codec、protocol route、client stub、network fallback或拓扑推断。
- 分布式事务、exactly-once、自动幂等、默认重试、跨服务事务传播或将network success解释为业务最终成功。
- 把 `endpoint-client` 描述成默认业务层或让业务团队在其中维护 checked-in client code；框架也不自动创建Gradle module、build script、发布仓库或Maven publication，生成器只管理该feature-scoped packaging module的源代码与受管资源。
- 支持同一进程对同一published Request同时注册local Provider Handler与remote Handler；未来显式routing contract另行设计。
- 把Consumer outbound proxy计为Provider Actor entry或默认Flow root；不新增Consumer outbound Graph projection，除非未来有独立生产需求。
- 修改已接受的typed Spring MVC HTTP binding、published Response direct-schema、HTTP error/security policy或API Payload retirement。

# Acceptance examples

- A1：新增 `ddd-endpoint-rpc`、`ddd-endpoint-rpc-http`、`cap4k-ddd-endpoint-rpc-http-starter`，模块依赖保持 contract leaf、core dispatch与transport ownership边界；generated `endpoint-client`的compile/runtime graph包含`ddd-endpoint-rpc`但不包含`ddd-endpoint-rpc-http`或starter，具体backend只由Consumer assembly选择；`ddd-core`及`cap4k-contract-api`无RPC/discovery/client fallback逻辑。
- A2：`generators.endpointRpc` 必须显式提供 non-blank `serviceId`、non-empty且无重复的 `operationNames`，并要求 `adapterModulePath`、`contractModulePath`与feature-scoped `endpointClientModulePath`；`endpoint-client`只承载generated Consumer outbound adapter artifact，不改变官方默认四模块业务拓扑。unknown operation、blank identity、duplicate selection在planning前稳定失败，未选Endpoint不生成RPC暴露。
- A3：同一canonical Endpoint输入生成adapter侧typed Provider registrations，以及`endpoint-client`侧每operation一个concrete remote `EndpointHandler<Request, Response>`、聚合auto-configuration与受管registration metadata；输出使用generated-source lane、稳定path/template/ownership且不修改Endpoint contract artifact。
- A4：generated client artifact只依赖published contract、RPC runtime与必要auto-configuration API；contract module对client、Spring、RPC和Provider实现保持零反向依赖。Consumer增加artifact与starter后自动注册remote Handlers，无handwritten service-specific client package。
- A5：Provider activation校验 `serviceId + operationName` 唯一、generated outer owner、public `OPERATION_NAME`、nested Request/Response owner和 `EndpointRequest<Response>` generic一致性；duplicate/mismatch/unknown service在接受流量前给出确定性诊断。
- A6：HTTP/JSON RPC backend只接受固定POST endpoint与受支持protocol version；malformed envelope、unknown service/operation、unsupported version、context decode、request decode和response encode失败形成稳定protocol failure，不能调用业务Handler。
- A7：合法Provider invocation解码published Request，使用`ExecutionContextBoundary.RPC` external decode/install，且只执行 `Mediator.endpoints.send(request)`；独立Provider Handler继续显式映射到一个本地Command或Query，transport registration增删不改变Handler identity。
- A8：Consumer业务代码只调用 `Mediator.endpoints.send/sendAsync(publishedRequest)`；remote Handler内部通过`EndpointTransportInvoker`完成route、codec和network。Capability ACL路径仍为 `Mediator.capabilities -> mapping Handler -> Mediator.endpoints`，两条路径均无direct proxy/Feign/RPC call。
- A9：默认HTTP backend通过assembly-owned `serviceId -> base URI` route map、positive connect/response timeout与request customizer工作；credential和URI不进入contract/generated handler/facts。默认无自动retry，remote failure抛出sanitized `EndpointRemoteInvocationException`，不泄露provider exception message/type/stack/payload。
- A10：published Endpoint Request/Response是RPC业务payload schema；transport envelope不成为Design tag、canonical semantic role、generated tactical carrier、Analyzer node或API Payload replacement，且codec/version mismatch fail closed。
- A11：同一进程对同一Request出现local Handler + remote Handler、两个client artifacts或两个remote backends时，沿现有唯一Handler规则确定性失败；Runtime不猜测本地/远程优先级。
- A12：真实跨模块、跨进程functional fixture启动Provider与Consumer：Provider generated binding + independent Handler，Consumer generated client artifact + starter，direct Endpoint与Capability ACL均完成真实HTTP roundtrip；Consumer源码不存在transport interface、proxy `.handle()`或HTTP client直接调用。
- A13：Analyzer只从真实 `EndpointRpcProviderBinding` production registration建立 `endpointrpcproviderbinding`，stable identity包含binding kind、serviceId与operationName，并以generated metadata/Request FQN关联independent Handler；contract-only、Handler-only、Consumer proxy、local dispatch、copied literal、mismatched provenance和缺失registration均为零RPC Actor evidence。
- A14：RPC Provider Handler到Command生成 `EndpointRpcProviderBindingToCommand` 与一张Actor Flow；到Query只生成 `EndpointRpcProviderBindingToQuery` raw Graph且不增加flowCount。handle-reachable helper生效，unused helper与Capability call不伪造Command/Query edge。
- A15：同一operation同时注册已接受HTTP binding与新RPC binding时产生两个独立transport Actor nodes/entries，可共享同一Provider Handler与下游Command/Query；不重复Handler，不合并binding identity，不由Consumer proxy增加第三个entry。
- A16：Runtime Agent facts新增 `runtime.endpoint-rpc-provider` 与 `runtime.endpoint-rpc-consumer`，准确声明shared/runtime/backend/starter ownership且保持assembly `UNKNOWN`、observation/verification `NOT_PERFORMED`；Integration Event provider catalog保持原三项，不混入Endpoint RPC identity。
- A17：production descriptors、plan evidence、Agent ownership、Public Docs与Skill一致声明explicit selection、generated provider/client artifacts、Mediator-only consumer、HTTP/JSON backend、route/timeout/auth/retry边界、Analyzer/Flow语义与未支持的Feign/gRPC/discovery；旧`ActorEndpoint`与API Payload surface不恢复。
- A18：focused runtime/generator/renderer/compiler/IR/Flow/Agent tests、真实multi-module network fixture、capability propagation guards、current Runtime facts、Skill/PR workflow validators、`git diff --check`与完整Gradle `check`全部通过；未执行外部downstream项目时明确记录 `NOT_PERFORMED`。

# Constraints and invariants

- canonical unit仍是一个Endpoint operation；service grouping只属于RPC binding/client artifact projection，不能重定义operation identity。
- `serviceId`是稳定logical binding identity；base URI、instance topology、credentials、timeouts与discovery provider是runtime assembly configuration，不进入Design JSON Endpoint block或generated contract。
- RPC选择必须显式；Endpoint authored不等于自动RPC发布，Command/Query存在更不等于Endpoint发布。
- Provider inbound与Consumer outbound位于不同进程但实现同一`EndpointHandler<Request, Response>` local seam；业务只见Mediator，transport只见published Request/Response与binding identity。
- Provider transport必须经过Endpoint Supervisor；Consumer transport必须表现为Endpoint Handler。不得创建第二套业务dispatcher、service facade或direct client API。
- Provider Handler保持adapter-owned、transport-neutral、checked-in；generated Provider registration与Consumer handler不得包含业务Command/Query mapping。
- RPC envelope是transport control structure而非published业务schema。成功payload必须精确解码为published Response；失败为out-of-band sanitized transport failure，不增加隐式业务Response envelope。
- `ExecutionContextBoundary.RPC`只传播显式允许且可编解码的elements，external unknown elements按既有external policy处理；不得信任或转发任意ThreadLocal/credential。
- 一个concrete Request在一个进程仍只有一个适用Handler；无本地/远程fallback、动态选择或按地址猜测。
- Provider RPC Actor evidence来自真实入站binding；Consumer proxy不是Actor entry。Command是默认causal root target，Query保持Graph-only。
- 现有Spring MVC Endpoint HTTP Provider、published Response schema、API Payload retirement和Issue #193边界均保持接受状态，不在本change重开。

# Decisions

- Change对应Parent #191的child Issue #194，承担ACTOR-RPC-1并为ACTOR-COMPOSE-1提供最终sibling slice evidence；保持单一Native change，不拆Supervisor children，因为generated Provider registration、client artifact、runtime protocol与Analyzer evidence共同定义同一个可验收RPC Actor entry，分拆会在未稳定的wire/identity上制造跨change耦合。
- 基线固定为包含PR #198的`origin/master` merge commit `c578fb59bd6a24a6df2655ea1db5cd8c7e0ec120`；直接消费canonical `endpoint-contract`与`endpoint-http-binding`，不重开Endpoint vocabulary、published schema、Mediator family、HTTP provider或API Payload退役。
- 首个生产RPC backend选择unary HTTP/JSON fixed endpoint，而不是OpenFeign/gRPC public surface。使用generic `EndpointTransportInvoker`隔离backend；未来Feign/gRPC只能作为内部invoker实现。
- RPC authoring source采用独立generator binding configuration：stable `serviceId` + explicit selected `operationNames`。不向Endpoint Design JSON或canonical published contract加入service/address/timeout/retry/auth字段。
- Provider build从同一canonical Endpoint source同时生成既有adapter侧typed Provider registrations和独立`endpoint-client` packaging module中的Consumer remote Handlers/auto-configuration；remote Handler在逻辑上仍是Consumer outbound adapter，独立artifact只承担版本化发布与复用。Consumer不运行第二套contract generator，也不维护handwritten service-specific client package。
- Client artifacts使用新`project.endpointClientModulePath` / `endpoint-client` feature-scoped packaging role；它不加入官方默认项目四模块清单，也不是新的DDD layer。provider registrations与client code/resources归`cap4kGenerateSources`受管生成，不新增public task，不自动创建module/build script/publication。
- 每operation生成concrete remote EndpointHandler，聚合auto-configuration只负责bean registration；不生成供业务注入的service interface。Feign annotation/interface不出现在contract、generated business API或首个backend中。
- Provider HTTP RPC route固定为`POST /cap4k/endpoints/rpc`，wire envelope versioned；payload复用published Endpoint Request/Response，ExecutionContext使用现有RPC boundary codec registry，失败只返回sanitized protocol category/code。
- Consumer route resolver默认使用显式static `serviceId -> base URI` map；discovery可未来实现同一SPI。connect/response timeout属于backend配置，auth属于request customizer；自动retry默认不存在。
- Runtime不改Endpoint Supervisor/Mediator并沿用唯一Handler失败语义；同进程local+remote或duplicate remote Handler不做隐式routing。
- Analyzer identity采用`endpointrpcproviderbinding`与`EndpointRpcProviderBindingToCommand|Query`；node identity包含`endpoint-rpc:<serviceId>:<operationName>`。只Provider inbound binding是Actor entry，Consumer proxy不进入默认Graph/Flow。
- 保留已接受的`ddd-endpoint-http`与`cap4k-ddd-endpoint-http-starter`名称，不做兼容alias或迁移层。这里的`http`准确表达direct HTTP Provider binding技术所有权；`ddd-endpoint-rpc-http`中的`http`表达RPC的具体carrier/backend。North-South与East-West只作为典型架构流量说明，不进入artifact、package、capability或Analyzer identity。

# Open questions

- None. User confirmed the complete Shape on 2026-08-16, including retaining the accepted direct HTTP module names, the three-layer RPC runtime/backend/starter split, generated endpoint-client ownership, Mediator-only Consumer use, explicit service/operation selection, default no-retry policy and Provider-only Actor evidence.

# Verification expectations

- `ddd-endpoint-rpc` focused tests：typed binding/client descriptor、generated owner与generic coherence、service/operation uniqueness、wire version、codec/context、安全failure taxonomy及无payload泄露。
- HTTP backend/starter tests：fixed route/method、Servlet security/filter participation、route map与URI normalization、timeouts、request customization、Provider Mediator-only dispatch、external ExecutionContext install、unknown/malformed/version/error behavior和sanitized exception。
- Generator/Renderer/Gradle tests：`endpointRpc` DSL/validation、explicit selection、adapter Provider registrations、endpoint-client remote Handlers、auto-configuration metadata、generated-source ownership、source-set/resource wiring、stable plan evidence与无新public task。
- real multi-module/multi-process fixture：provider contract+Handler+generated RPC binding、published endpoint-client artifact、consumer starter/config、direct Endpoint与Capability ACL真实network roundtrip、HTTP+RPC sibling composition、duplicate Handler/identity negatives。
- Compiler Analyzer与IR source tests：real RPC Provider binding node/relationships、generated provenance、cross-file Handler association、handle-reachable mapping、contract/Handler/client/local-dispatch/copied-literal/mismatch/missing-registration negatives。
- Flow focused/functional tests：RPC Command root、RPC Query non-root、HTTP+RPC two roots/shared downstream、零Consumer proxy root、零generic `*ToCommand` fallback、stable identity与deterministic conflict diagnostics。
- Runtime/Generator/Analyzer capability descriptors、capability facts、Runtime Agent facts、Agent snapshot/plan ownership、Public Docs、Skill、`scripts/validate-capability-contract.ps1`、`scripts/test-capability-contract.ps1`、`scripts/validate-current-runtime-facts.ps1`、`skills/scripts/validate-cap4k-skills.ps1`、PR workflow guards、`git diff --check`与完整Gradle `check`。
