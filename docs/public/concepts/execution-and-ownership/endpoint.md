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

## Consumer boundary

当前没有 RPC Provider dispatcher 或 Consumer proxy generation。未来 Consumer transport proxy 可以在消费者进程实现同一个 `EndpointHandler<Request, Response>`，由业务代码继续无感调用 `Mediator.endpoints.send(...)`。若消费者不愿直接接受对方的 published language，可以先调用本地 Capability，再由 adapter-owned Capability Handler 映射到 Endpoint Request；Capability 是本地防腐边界，Endpoint 是双方共享的 published language。

## Analyzer 与 Flow

Endpoint contract declaration、Provider Handler 单独存在以及本地 `Mediator.endpoints` dispatch 都只是非入口证据。真实 typed `EndpointMvcBinding` registration 才生成 `endpointhttpbinding` Actor node；Analyzer 通过 generated Request/operation identity 跨类、跨文件关联独立 Provider Handler。`EndpointHttpBindingToCommand` 可以形成默认 Flow root，`EndpointHttpBindingToQuery` 只保留在 raw Graph，不增加默认 Flow 数量。
