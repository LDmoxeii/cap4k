# Endpoint

Endpoint 是一个 transport-neutral published operation：边界双方共享稳定的 operation identity 与 Request/Response shape，HTTP、RPC、CLI 或其他 binding 不属于该契约本身。Design JSON 使用 `endpoint` tag；`Actor` 只保留为 Analyzer/Flow 对真实外部触发来源的分类，不是产品类型名。

## 三个独立角色

一个 Endpoint 的生产者侧由三个可独立演进的角色组成：

1. dependency-leaf contract module 拥有 generated operation object、`OPERATION_NAME`、`Request` 与 `Response`；
2. adapter-owned checked-in `EndpointHandler<Request, Response>` 把 published Request 显式转换为本地 Command/Query，并把结果显式转换回 published Response；
3. 每一种 Provider transport binding 只负责协议入口，并通过 `Mediator.endpoints` 调度同一个本地 Handler。

因此，一个 operation 可以先只有 contract 与 Handler，之后再增加 HTTP 或未来的 RPC binding；也可以同时拥有多种 binding，而不修改 contract 或复制业务映射。cap4k 不把一个 annotated Controller/class/file 当作 Endpoint 的统一身份。

## Published contract

一个 Endpoint 对应一个 operation，而不是聚合多个方法的 service interface。它必须声明非空 `operationName`，并通过 `fields` 与 `resultFields` 定义有序 Request/Response。默认 generator 在 `project.contractModulePath` 指向的 dependency-leaf contract module 生成：

```kotlin
object CreateBookingEndpoint {
    const val OPERATION_NAME: String = "booking.create"

    data class Request(/* fields */) : EndpointRequest<Response>

    data class Response(/* result fields */)
}
```

该 contract 不实现 Command、Query 或 Capability marker，也不包含 Spring、route、status、header、RPC client、service discovery、timeout、retry 或 persistence 行为。HTTP binding 的增加和删除不会改变 `operationName` 或 Request/Response identity。

## Provider Handler

Provider Handler 是 adapter-owned checked-in source，不带 Spring MVC route metadata：

```kotlin
@Component
class CreateBookingEndpointHandler : EndpointHandler<
    CreateBookingEndpoint.Request,
    CreateBookingEndpoint.Response,
> {
    override fun handle(request: CreateBookingEndpoint.Request): CreateBookingEndpoint.Response {
        val result = Mediator.commands.send(
            CreateBookingCmd.Request(/* explicit mapping */)
        )
        return CreateBookingEndpoint.Response(/* explicit mapping */)
    }
}
```

Handler 可以调用 `Mediator.commands` 或 `Mediator.queries`，但不能用字段同名猜测替代显式映射。HTTP、status、header、认证与错误映射不进入 Handler。现有 Handler 文件由作者维护；框架不会在后续生成中覆盖、合并或补丁修改它。

## Spring MVC Provider binding

当前已提供 typed code-first Spring MVC Provider binding：

- runtime module：`ddd-endpoint-http`；
- starter module：`cap4k-ddd-endpoint-http-starter`；
- registration type：`EndpointMvcBinding<Request, Response>`；
- ordinary JSON body preset：`EndpointMvcBinding.json(...)`；
- path/query/header 或特殊 response：`EndpointMvcBinding.special(...)`。

普通 JSON operation 可以注册为一个 Spring bean：

```kotlin
@Bean
fun createBookingHttpBinding() = EndpointMvcBinding.json(
    operationName = CreateBookingEndpoint.OPERATION_NAME,
    requestType = CreateBookingEndpoint.Request::class,
    responseType = CreateBookingEndpoint.Response::class,
    method = HttpMethod.POST,
    path = "/api/bookings",
)
```

特殊 HTTP 形态仍保持 type-safe code-first，例如 query input 与 302 `Location` response：

```kotlin
@Bean
fun getResourceHttpBinding() = EndpointMvcBinding.special(
    operationName = GetResourceEndpoint.OPERATION_NAME,
    requestType = GetResourceEndpoint.Request::class,
    responseType = GetResourceEndpoint.Response::class,
    method = HttpMethod.GET,
    path = "/file/getResource",
    requestMapper = EndpointMvcRequestMapper { request ->
        GetResourceEndpoint.Request(
            sourceName = request.query("sourceName"),
        )
    },
    responsePolicy = EndpointMvcResponsePolicy.none(
        status = 302,
        headers = listOf(
            EndpointMvcResponseHeader.property(
                "Location",
                GetResourceEndpoint.Response::url,
            )
        ),
    ),
)
```

多个 binding bean 可以集中在一个 adapter configuration class 中；不要求每个 operation 额外生成 Controller 或独立 binding 文件。Provider Handler 仍是独立的 checked-in implementation。Starter 收集 immutable typed registrations，验证 generated owner、`OPERATION_NAME`、Request/Response generic、HTTP method/path、重复 operation 与重复 route，然后用 WebMvc.fn materialize routes。请求/响应使用应用已有的 Spring MVC message converters，唯一 Endpoint dispatch 是 `Mediator.endpoints.send(request)`。

协议解析、缺失参数、类型转换与 Endpoint Bean Validation 失败映射为 400；其他异常继续交给应用已有的 MVC exception resolver。Route 仍由 Spring MVC `DispatcherServlet` 承载，因此继续经过应用的 servlet filter/security chain。首版没有 route-level auth DSL、结果 envelope 开关、multipart/streaming、动态 status、consumer proxy 或 client generation；这些不能通过把 Spring/HTTP metadata 塞回 published contract 来绕过。

## Endpoint RPC Provider 与 Consumer

Sibling-service RPC 使用三层模块，而不是三个并列 binding：

- `ddd-endpoint-rpc`：transport-neutral RPC ABI，拥有 typed Provider binding、Consumer invocation SPI、versioned envelope/codec 与安全 failure taxonomy；
- `ddd-endpoint-rpc-http`：首个同步 unary HTTP/JSON backend，拥有 route resolution 与网络 invocation；
- `cap4k-ddd-endpoint-rpc-http-starter`：Spring Boot Provider/Consumer assembly，拥有固定 Servlet endpoint、codec/configuration 与 bean materialization。

Direct HTTP 通常承载 North-South 流量，RPC 通常承载 East-West 流量，但这只是典型部署说明，不是 contract、artifact、package、capability 或 Analyzer identity。RPC Provider 使用固定 `POST /cap4k/endpoints/rpc`，解码后只通过 `Mediator.endpoints.send(request)` 到达独立的 checked-in Provider Handler；transport 不直接查找 Handler，也不直接发送 Command/Query。

RPC 发布必须显式配置稳定、非空的 `serviceId` 和非空、无重复的 selected `operationNames`。同一 canonical Endpoint model 生成 adapter module 中的 typed Provider registrations，以及 feature-scoped `endpoint-client` packaging module 中每 operation 一个 concrete remote `EndpointHandler<Request, Response>`、聚合 auto-configuration 和受管 registration metadata。`endpoint-client` 是 Provider 发布的 generated Consumer outbound adapter artifact，不是默认 `domain` / `application` / `adapter` / `start` 拓扑之外的第五层，也不承载 checked-in 业务代码；它只依赖 published contract 与 `ddd-endpoint-rpc`，具体 HTTP backend 由 Consumer `start` 通过 starter 选择。

Consumer 业务代码只构造 published Request 并调用 `Mediator.endpoints.send/sendAsync`，不注入或直接调用 generated Handler、transport invoker 或 HTTP client。需要本地防腐语言时，使用 `Mediator.capabilities -> adapter-owned CapabilityHandler -> Mediator.endpoints`。默认 backend 使用 assembly-owned static `serviceId -> base URI` route map、positive connect/response timeout 与 request customizer；URI、credential、header、timeout 和 retry policy 不进入 contract 或 generated Handler。默认没有自动 retry；只有 Consumer 明确拥有幂等决策时，才可在 assembly 中提供 invoker decorator。

当前不提供 OpenFeign、gRPC、Spring Cloud discovery/load-balancer、动态 fallback 或业务可注入的 service proxy。未来 backend 只能隐藏在 `EndpointTransportInvoker` / `EndpointRpcRouteResolver` 后，不能改变 Mediator-only 业务调用面。一个进程对同一 concrete Request 仍只能有一个适用 Handler，不会猜测 local/remote 优先级。

## Analyzer 与 Flow

Endpoint contract declaration、Provider Handler 单独存在以及本地 `Mediator.endpoints` dispatch 都只是非入口证据。真实 typed `EndpointMvcBinding` registration 才生成 `endpointhttpbinding` Actor node；真实 generated `EndpointRpcProviderBinding` registration 才生成 `endpointrpcproviderbinding` Actor node。Analyzer 通过 generated Request/operation identity 跨类、跨文件关联独立 Provider Handler。HTTP/RPC 到 Command 的协议专属关系可以分别形成默认 Flow root；到 Query 的关系只保留在 raw Graph，不增加默认 Flow 数量。generated Consumer remote Handler/client artifact 不是 Provider Actor evidence，也不会产生 Consumer Flow root；同一 operation 的 HTTP 与 RPC Provider registrations 保持两个独立 entry identity，即使它们共享 Handler 与下游节点。
