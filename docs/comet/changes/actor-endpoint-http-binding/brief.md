# Outcome

在已经接受的 transport-neutral Endpoint contract 之上交付第一个生产级 Spring MVC Provider binding。Endpoint published contract、binding-neutral Provider `EndpointHandler` 与 HTTP transport binding 分别拥有 published language、业务映射和协议决策；一个 operation 可以先没有 transport、只提供 HTTP，或在后续同时增加 RPC/其他 binding，而不修改 contract 或复制本地 Handler。Provider 作者只编写显式业务映射与不可消除的 HTTP API 决策，Spring route materialization、codec、dispatch、基础失败映射和 wiring 由 Runtime/starter 完成；Analyzer 从真实 typed binding 与独立 Handler 恢复 Actor/Graph/Flow evidence。

# Scope

- 支持 Spring MVC servlet stack 的 Provider-side Endpoint binding，不包含 Consumer client。
- 新增职责单一的 `ddd-endpoint-http` 与 `cap4k-ddd-endpoint-http-starter`；不复用 Integration Event HTTP transport，也不在 `ddd-core` 加入远程 discovery 或 route fallback。
- Provider application 以 adapter-owned、类型安全的 `EndpointMvcBinding<Request, Response>` code-first registration 显式关联 generated `OPERATION_NAME`、published Request/Response types、HTTP method 与 route；多个 registration 可以集中在一个 Spring configuration 中，不要求一 operation 一 Controller/class/file。
- Spring MVC starter 收集 binding beans，使用 WebMvc.fn `RouterFunction` / `HandlerFunction` materialize 真实 route；所有 route 都通过 `Mediator.endpoints.send/sendAsync` 进入当前进程唯一 `EndpointHandler`，不得直调 Handler 或直接发送 Command/Query/Capability。
- Provider 作者维护独立、transport-neutral、checked-in `EndpointHandler<Request, Response>`，显式完成 published Request/Response 与本地一个 Command 或 Query 的映射；该 Handler 可以在没有任何 transport binding 时存在。
- 普通 JSON binding 提供 whole-body Request / whole-response Response preset；特殊 HTTP shape 提供 typed request mapper 和 response policy，覆盖 body、path、query、header、固定 status、`RESPONSE|NONE`、固定/typed response-field header 与固定 content type。
- HTTP JSON 使用 Spring application 当前配置的 MVC `HttpMessageConverter` / Jackson codec，不使用 `RuntimeJson`，不绕开项目级 Jackson modules/customization。
- Endpoint Response 是实际 wire schema：首版不提供 `DEFAULT|RAW` envelope 开关，也不依赖 annotated-controller `ResponseBodyAdvice`。需要统一 envelope 时必须把它建模进 published Response；特殊无 body/redirect 由显式 response policy 表达。
- HTTP binding 不提供 auth DSL；route 参与应用现有 Servlet filter / Spring Security chain。匿名、角色、权限与其他安全决策由应用安全配置按 route 处理。
- Runtime 对 malformed body、缺失 required source、path/query/header conversion failure 与 Endpoint Request Bean Validation failure 提供确定性 HTTP 400；其他 Handler/domain 异常继续交给应用现有 Spring MVC exception resolvers，不建立新的全局 exception taxonomy。
- Runtime 在 route activation 前以 `operationName + Request/Response KClass` 和现有 generated outer-object structure 验证 non-blank identity、`OPERATION_NAME` 一致性、Request 的 `EndpointRequest<Response>` 泛型关系、共同 operation owner、重复 operation binding 与重复 normalized method+route；不新增 runtime/generated operation catalog。
- Analyzer 识别生产 typed binding、generated Endpoint operation evidence、独立 Provider Handler 与其显式 Command/Query invocation；不得假设 binding 与 Handler 同类或同文件。
- Command-oriented HTTP binding 形成 Actor entry 与默认 Flow root；Query-oriented binding只保留 raw Graph evidence，不增加默认 Flow。
- 同步 Runtime、Analyzer、Flow、AgentFacts、Public Docs 与 repo-local authoring Skill capability projection；Generator、Renderer、Design JSON 与 Design Projection 必须 `verified-no-change`。
- 一并删除 `NodeType.apipayload` 与 IR Drawing Board candidate 中 separator-free `apipayload` 残留，使已完成的 API Payload retirement 在 Analyzer surface 闭合。

# Non-goals

- RPC Provider、Consumer proxy、Feign/gRPC client/stub、service discovery、timeout、retry 或网络 topology 实现。
- WebFlux、HTTP consumer client、OpenAPI authoring/generation；OpenAPI 只能作为未来从 production binding registry 与 generated Endpoint types 派生的 projection。
- 自动发布任意 Command、Query 或 Capability，或根据名称/字段形状自动猜测 Endpoint 到本地消息的映射。
- 通用 authentication/authorization framework、业务 exception taxonomy、application-wide response envelope 或强制替换 handwritten Controllers。
- 将 Query 纳入默认 causal Flow。
- 修改 transport-neutral Endpoint operation identity、Request/Response semantic roles、contract-module dependency leaf 或 Endpoint Mediator semantics。
- 自研完整 FastEndpoints-style `Configure + Handle` framework，或创建强迫 HTTP、Feign、RPC、GraphQL、CLI、streaming 等 binding 采用同一物理代码形态的 universal DSL。
- custom manifest、Pipeline source/canonical family、generated binding catalog、per-operation Controller/binding generator 或 framework-level API Payload replacement。
- multipart upload、buffered binary response、streaming、download attachment、dynamic status selection 或 transport-native published types。
- 让 Spring annotated-controller `ResponseBodyAdvice`、Sa-Token annotations 或其他 application-specific MVC advice 成为 functional binding 的隐式契约。

# Acceptance examples

- A1：`ddd-endpoint-http` 暴露 typed `EndpointMvcBinding<Request, Response>` registration contract，`cap4k-ddd-endpoint-http-starter` 收集其 Spring beans 并 materialize WebMvc.fn routes；module dependencies 保持 contract leaf 与 Runtime ownership 边界。
- A2：registration 必须显式提供 generated `OPERATION_NAME`、Request/Response type、supported HTTP method 与 absolute route；runtime 无 catalog 地校验 outer operation owner、`OPERATION_NAME`、`EndpointRequest<Response>` 泛型一致性及 non-blank identity，错误在 route activation 前给出稳定可操作诊断。
- A3：同一个 operation 的第二个 HTTP binding 与任意重复 normalized method+route 都在 activation 前失败；不同 operation 的不冲突 routes 可同时注册；Handler-only operation 合法且不自动暴露 route。
- A4：普通 JSON preset 使用应用配置的 Spring MVC message converters 将 whole body 解码为 published Request、将 published Response 直接编码为 response body，并且 route 只调用 `Mediator.endpoints.send/sendAsync`。
- A5：typed special request mapper 能显式读取 named body/path/query/header source 并直接构造 published Request；缺失 required value、malformed input 或 conversion failure 稳定映射为 HTTP 400，不依赖参数名猜测或 generated transport DTO。
- A6：response policy 支持 fixed success status（默认 200）、`RESPONSE|NONE`、fixed header、typed direct Response-property header 与 optional fixed content type；302 + empty body + `Location <- response.url` 可表达且 Provider Handler 不接触 `ResponseEntity`/`ServerResponse`。
- A7：HTTP wire body 与 published Endpoint Response 保持同一 schema；不存在 `DEFAULT|RAW` envelope 开关或 framework wrapper。Endpoint Request Bean Validation failure 映射 400，其他 Handler/domain 异常交给现有 Spring MVC resolver；route 继承应用 filter/security chain而不新增 auth DSL。
- A8：独立 checked-in Provider `EndpointHandler` 显式把 Endpoint Request 映射为一个 authored Command 或 Query，并显式构造 Endpoint Response；不包含 Spring route、HTTP method/status/header/auth/error metadata，且 binding 增删不改变其 identity。
- A9：Analyzer 只从真实 typed HTTP binding 建立 Actor entry，并通过 Request/operation evidence跨类、跨文件关联对应 Provider Handler；contract declaration、Handler-only、local `Mediator.endpoints` dispatch 或缺失真实 route 均不产生 HTTP Actor entry。
- A10：真实 HTTP Endpoint 到 Command 生成稳定 Endpoint HTTP binding-to-Command Graph relationship和一张 entry-centered Flow；到 Query 只生成 Graph relationship，不增加默认 Flow count。
- A11：WebMvc.fn Endpoint binding 不被 generic annotated Controller detector重复计数；普通 handwritten Controller 的现有 Controller-to-Command/Query/Capability evidence 与 Flow 行为无回归。
- A12：`NodeType.apipayload` 与 IR Drawing Board candidate 的 `apipayload` 残留完全删除；Analyzer、Design Projection、Drawing Board 与 capability facts 不再投影 API Payload，同时 Endpoint contract Design round-trip 保持不变。
- A13：实现不新增 Design JSON tag、Pipeline source/canonical family/planner/renderer、generated adapter artifact或 operation catalog；Generator/Renderer/Design Projection tests证明现有 endpoint contract generation无需修改即可支撑 binding association。
- A14：Runtime descriptor/facts、Analyzer descriptor/wire、Flow、Public Docs 与 authoring Skill 一致使用 `Endpoint` product vocabulary，声明 Spring MVC Provider binding、独立 Handler、真实 entry evidence、security/error/envelope边界及明确非目标；canonical capability 为 `endpoint-contract`，旧 `actor-endpoint-contract`/`ActorEndpoint` surface 无 alias 或兼容桥，并通过 capability propagation guards。
- A15：真实 multi-module functional fixture 同时证明 contract module、adapter Handler、typed binding、Spring application route、普通 JSON、特殊 query/redirect、400 failure、Command Flow、Query non-Flow、零 generated Controller/binding artifact和完整编译/运行结果。

# Constraints and invariants

- Endpoint 仍是一 operation 一 identity 的 transport-neutral published contract；HTTP method、route、status、header、安全与错误策略都不进入 generated contract 或 `cap4k-contract-api`。
- Provider Handler 是 operation 的本地实现，不属于任何 transport；Provider HTTP/RPC binding 都必须通过 `Mediator.endpoints` 进入同一个本地 Handler。
- Consumer transport proxy 与 Provider Handler未来都可以实现 `EndpointHandler<Request, Response>`，但位于不同进程并承担不同本地实现；同一进程对具体 Request 仍最多只有一个适用 Handler。
- binding declaration 是少量 adapter-owned typed code；route registration、protocol conversion、Spring codec接入、dispatch、基础 HTTP failure mapping与wiring属于 Runtime/starter。
- HTTP Response 直接以 published Response 为 schema authority；framework不能在它外面隐式增加 envelope。确有特殊 wire shape时，只能由显式 binding mapper/policy和必要的 adapter-private handwritten type承担，不创建新的 framework schema family。
- Provider Handler 属于 adapter-owned `CHECKED_IN_SOURCE`。作者可手写，或由 authoring Skill 可选地首次创建 skeleton；若 scaffold存在，已有文件必须 `SKIP`，后续不覆盖/merge/patch业务映射。
- Analyzer/Flow 只能从生产代码中的真实 binding evidence 推导 Actor entry，不能从 Design declaration、Provider Handler单独存在、local dispatch或文档猜测。
- 当前默认 Flow 只让 Command-oriented HTTP binding成为 causal root；Query保留在 raw Graph。
- 当前框架没有外部用户；本 change 不承担兼容 alias、deprecated bridge、双写 surface 或保留错误/重复 capability 的义务。

# Decisions

- Change 对应 Parent #191 的 child Issue #193，并只承担 ACTOR-HTTP-1；保持一个 Comet change，不拆成 Supervisor children，因为 Runtime materialization 与 Analyzer evidence共同定义同一个可验收 HTTP Actor entry，Analyzer实现依赖最终 typed binding code form。
- 基础 contract 直接消费已合并的 #192 与 canonical `endpoint-contract`；prerequisite `retire-design-api-payload` 已于 2026-08-15 合入 `master`，本 Shape 已从该新基线刷新。
- 放弃“一个 class 同时承担 Spring MVC binding 与 Provider EndpointHandler”的默认形态。Handler 与 binding 分离；同一个 operation 可独立增加多个 transport kinds，而不修改 contract或复制业务实现。
- HTTP authoring source 采用 typed code-first `EndpointMvcBinding`，不是 manifest/OpenAPI/Controller generation。多个 binding可集中在一个配置类；exact factory/builder naming是实现选择，但 production descriptor必须是 immutable、typed、可被 Runtime与Analyzer稳定识别的 registration。
- Spring MVC materialization采用 WebMvc.fn；binding作者不直接实现普通 Controller或`HandlerFunction`。cap4k只提供薄 registration + runtime materializer，不实现完整 FastEndpoints DSL。
- Operation activation validation复用现有 generated structure：outer object的public `OPERATION_NAME`、nested Request/Response type evidence及Request generic marker；不新增 runtime catalog、generated catalog或Generator职责。Analyzer再以compile-time metadata验证 provenance。
- 普通 HTTP JSON使用Spring application-configured MVC message converters/Jackson；`RuntimeJson`继续只服务其既有可靠记录/Integration Event runtime边界，不成为application HTTP API codec。
- 删除 response envelope选项。由于published Response已经是唯一外部 schema authority，Runtime直接编码它；functional endpoints也不把annotated-controller `ResponseBodyAdvice`当作契约。需要统一包装时在Endpoint Response中显式建模。
- 首版security只继承application Servlet filter/Spring Security chain，不增加binding auth metadata或Sa-Token耦合。首版error mapping只稳定处理HTTP解析/转换/required/Endpoint validation为400，其余异常交给application resolver。
- 普通 JSON直接复用published Request/Response；special body shape只有在协议确实不同且无法直接复用时才可使用adapter-private handwritten intermediate type，不进入Design JSON、canonical model、Analyzer node、AgentFacts或published contract。
- multipart/binary/streaming延后，transport-native types不得进入Endpoint contract。未来必须先定义transport-neutral content/reference、lifecycle、size、repeatability、temporary storage与cleanup合同。
- Feign/RPC Consumer application未来不定义、注入或调用transport interface；Consumer assembly选择的remote Handler负责network/discovery，业务仍只调用`Mediator.endpoints`。该能力不在#193实现。
- Generator、Renderer、Design JSON、Design Projection保持verified-no-change；Analyzer遗留的separator-free `apipayload` enum/candidate在#193内随Analyzer模型修改一并清除。
- 正式产品 vocabulary 统一收敛为 `Endpoint`：generated objects、runtime types、HTTP binding API、capability Specs、Public Docs 与 Skill 均不使用复合词 `ActorEndpoint`。新 API 为 `EndpointMvcBinding`，Graph relationships 为 `EndpointHttpBindingToCommand|Query`，canonical capability 从 `actor-endpoint-contract` breaking rename为 `endpoint-contract`；旧 capability直接删除，不保留alias或兼容桥。`Actor`只保留为Analyzer/Flow对真实外部触发来源的概念分类，active Comet change/GitHub Issue名称作为治理历史无需改名。

# Open questions

- [blocking] CONFIRM: 确认以上 refreshed Shape，尤其是“统一使用 Endpoint vocabulary”“typed `EndpointMvcBinding` + WebMvc.fn runtime”“独立 Provider Handler”“published Response直接作为wire schema且无envelope开关”“Spring codec / application security chain / 最小400映射”“无manifest、无binding generator、无catalog”以及 A1-A15 后进入 Build。

# Verification expectations

- `ddd-endpoint-http` focused tests：typed registration、generated-structure coherence、method/path normalization、duplicate operation、duplicate route、request reader、response policy、header property conversion和deterministic diagnostics。
- starter tests：Spring bean collection、WebMvc.fn route activation、application-configured Jackson/message converters、whole-body/whole-response、special query/path/header、redirect、400 failures、Mediator-only dispatch、security/filter-chain participation与exception resolver propagation。
- real multi-module functional fixture：dependency-leaf contract、adapter Handler、typed binding、running Spring MVC route、Command/Query variants和零 generated adapter artifact。
- Compiler Analyzer与IR source tests：Endpoint HTTP binding node/relationships、cross-file Handler association、operation/type provenance、local-only negatives、generic Controller coexistence、API Payload residue retirement。
- Flow focused/functional tests：Command root、Query non-root、零误报、零duplicate root、stable entry identity和ordinary Controller regression。
- Capability facts、current Runtime facts、Agent snapshot、Public Docs、Skill、PR workflow、`git diff --check`与完整 Gradle `check`。
