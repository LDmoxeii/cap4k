# Adapter Layer

Adapter layer 是 cap4k 项目连接外部世界和 application layer 的协议转换层。它负责把 HTTP、callback、external service、message、query projection 和 persistence 细节翻译成内部能理解的请求、结果或事实。

## 负责

Adapter layer 负责 Controller、API Payload、query adapter、client-handler、persistence adapter 和 protocol conversion。Controller 接收 HTTP request 并调用 application entry；API Payload 描述外部接口字段；query adapter 把读取结果组织成接口需要的 shape；client-handler 执行 external capability request；persistence adapter 处理存储协议和技术映射。对 inbound Integration Event，cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command/application behavior。

adapter 的目标是隔离协议差异。它可以处理 request/response mapping、status code、header、external error、callback schema、storage mapping、serialization 和技术容错，但这些转换不应该改变业务真相。

## 不负责

Adapter layer 不负责 Aggregate invariant、Factory 创建规则、Domain Event 触发条件、Command 的业务语义、Saga 的业务步骤决策或 start layer runtime assembly。它也不应该因为某个 HTTP payload 字段方便，就绕过 application layer 直接修改 domain state。

如果 Controller 或 client-handler 中出现“内容是否可发布”“paid publication 是否符合业务条件”“媒体处理完成后是否推进状态”这类判断，应检查这些逻辑是否应该移到 domain 或 application 的手写位置。

## 生成骨架

cap4k generation 可以为 Controller、API Payload、Query handler、client-handler、persistence adapter 以及 integration-event transport/runtime wiring references 提供稳定骨架。骨架帮助 adapter layer 保持清晰入口，例如 `ContentController`、`ReviewController`、`QueryController`、`AdvancedPaidPublicationController`、`GetContentDetailQryHandler`、`TriggerMediaProcessingCliHandler` 或 `MediaProcessingCallbackIntegrationEvent` typed event dispatch references。

生成骨架负责协议入口和结构一致性，不负责业务规则。handwritten mapping 可以补齐外部字段到内部语义的转换、错误处理、返回格式和技术容错，但不应把 business decision 写进协议适配代码。

## 手写逻辑

手写逻辑应该落在 mapping、protocol error handling、external capability adapter、query output assembly 和 persistence technical mapping 中。入站 Integration Event 要单独区分：cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收这个 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command 或 application behavior。

参考项目锚点包括 `ContentController`、`ReviewController`、`QueryController`、`AdvancedPaidPublicationController`、`GetContentDetailQryHandler`、`GetMediaProcessingStatusQryHandler`、`TriggerMediaProcessingCliHandler`、`GetMediaProcessingStatusCliHandler`、paid publication `*CliHandler` 和 `MediaProcessingCallbackIntegrationEvent` typed event dispatch references。

## 依赖方向

Adapter layer 可以依赖 application/domain 暴露的 entry 和 contract，但不依赖 start。application layer 不反向依赖 adapter implementation。domain layer 不知道 adapter protocol。start layer 可以装配 adapter bean，但 adapter 不应该通过 start layer 读取业务判断。

协议转换方向应该是外部协议进入 adapter，再转换成 application 的 Command、Query、Integration Event 或 external capability result；application 需要外部能力时，先表达 external capability request，再由 adapter 的 client-handler 实现具体协议。

## 参考项目

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读 `cap4k-reference-content-studio-adapter` 时，优先定位这些锚点：

- `ContentController`
- `ReviewController`
- `QueryController`
- `AdvancedPaidPublicationController`
- `GetContentDetailQryHandler`
- `GetMediaProcessingStatusQryHandler`
- `TriggerMediaProcessingCliHandler`
- `GetMediaProcessingStatusCliHandler`
- paid publication `*CliHandler`
- `MediaProcessingCallbackIntegrationEvent` typed event dispatch references

这些文件能展示 adapter layer 如何把外部协议转成 application contract，同时把技术细节挡在 inner layers 之外。

## 审核

审核 adapter layer 时，先看每个 Controller、Payload、Query handler、client-handler 和 persistence adapter 是否只做 protocol conversion 或 technical mapping。对 inbound Integration Event，单独确认 cap4k integration-event transport adapter/runtime 负责 HTTP/message consumption、parse/register/dispatch typed integration event；业务项目的 application-layer inbound integration subscriber 只解释 typed external fact、处理幂等并委托 Command/application behavior。最后确认业务规则没有被写入 status code mapping、callback parsing、external error handling 或 persistence mapping 中。
