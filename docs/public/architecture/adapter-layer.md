# Adapter Layer

Adapter layer 是 cap4k 项目连接外部世界和 application layer 的协议转换层。它负责把 HTTP、callback、external service、message、query projection 和 persistence 细节翻译成内部能理解的请求、结果或事实。

## 负责

Adapter layer 负责 handwritten Controller、Endpoint Provider Handler、typed transport binding、必要的 adapter-private DTO、query adapter、capability-handler、persistence adapter 和 protocol conversion。普通 Controller 接收 HTTP request 并调用 application entry；Endpoint Provider Handler 显式映射 published Request/Response 与本地 Command/Query，`EndpointMvcBinding` 只声明 Spring MVC method、route 与不可消除的协议映射；adapter-private DTO 只用于 published Endpoint shape 无法直接表达的特殊协议字段。query adapter 把读取结果组织成接口需要的 shape；capability-handler 执行 external capability request；persistence adapter 处理存储协议和技术映射。对 inbound Integration Event，cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command/application behavior。

adapter 的目标是隔离协议差异。它可以处理 request/response mapping、status code、header、external error、callback schema、storage mapping、serialization 和技术容错，但这些转换不应该改变业务真相。

## 不负责

Adapter layer 不负责 Aggregate invariant、Factory 创建规则、Domain Event 触发条件、Command 的业务语义、流程编排决策或 start layer runtime assembly。它也不应该因为某个 HTTP payload 字段方便，就绕过 application layer 直接修改 domain state。

如果 Controller 或 capability-handler 中出现“内容是否可发布”“paid publication 是否符合业务条件”“媒体处理完成后是否推进状态”这类判断，应检查这些逻辑是否应该移到 domain 或 application 的手写位置。

## Framework 与生成边界

cap4k 不再生成 framework-level API Payload、Controller 或 Endpoint HTTP binding。Endpoint 的 published Request/Response 由 contract module 中的 `endpoint` design artifact 生成；Provider Handler 与 typed `EndpointMvcBinding` 是 adapter-owned checked-in source。框架只 materialize route、复用 Spring MVC codec、通过 `Mediator.endpoints` dispatch，并提供最小协议失败映射。Query handler、capability-handler、persistence adapter 与 integration-event transport/runtime wiring 继续遵守各自现有 ownership。

Framework-owned runtime 负责稳定的协议执行机制，不替作者决定 method、route、status、header 或 published-to-local semantic mapping。handwritten mapping 可以补齐外部字段到内部语义的转换、错误处理、返回格式和技术容错，但不应把 business decision 写进协议适配代码。

## 手写逻辑

手写逻辑应该落在 mapping、protocol error handling、external capability adapter、query output assembly 和 persistence technical mapping 中。入站 Integration Event 要单独区分：cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收这个 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command 或 application behavior。

参考项目锚点包括 `ContentController`、`ReviewController`、`QueryController`、`GetContentDetailQryHandler`、`GetMediaProcessingStatusQryHandler`、`TriggerMediaProcessingHandler`、`GetMediaProcessingStatusHandler` 和 `MediaProcessingCallbackIntegrationEvent` typed event dispatch references。

## 依赖方向

Adapter layer 可以依赖 application/domain 暴露的 entry 和 contract，但不依赖 start。application layer 不反向依赖 adapter implementation。domain layer 不知道 adapter protocol。start layer 可以装配 adapter bean，但 adapter 不应该通过 start layer 读取业务判断。

协议转换方向应该是外部协议进入 adapter，再转换成 application 的 Command、Query、Integration Event 或 external capability result；application 需要外部能力时，先表达 external capability request，再由 adapter 的 capability-handler 实现具体协议。

## 参考项目

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读 `cap4k-reference-content-studio-adapter` 时，优先定位这些锚点：

- `ContentController`
- `ReviewController`
- `QueryController`
- `GetContentDetailQryHandler`
- `GetMediaProcessingStatusQryHandler`
- `TriggerMediaProcessingHandler`
- `GetMediaProcessingStatusHandler`
- `MediaProcessingCallbackIntegrationEvent` typed event dispatch references

这些文件能展示 adapter layer 如何把外部协议转成 application contract，同时把技术细节挡在 inner layers 之外。

## 审核

审核 adapter layer 时，先看每个 Controller、Endpoint Provider Handler、typed binding、adapter-private DTO、Query handler、capability-handler 和 persistence adapter 是否只做 protocol conversion、published-to-local mapping 或 technical mapping。对 inbound Integration Event，单独确认 cap4k integration-event transport adapter/runtime 负责 HTTP/message consumption、parse/register/dispatch typed integration event；业务项目的 application-layer inbound integration subscriber 只解释 typed external fact、处理幂等并委托 Command/application behavior。最后确认业务规则没有被写入 status code mapping、callback parsing、external error handling 或 persistence mapping 中。
