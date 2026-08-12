# Dependency Rules

Clean Architecture 的依赖规则可以简化成一句话：越靠近业务真相的代码越独立，越靠近外部世界的代码越负责转换和装配。cap4k 项目中，domain、application、adapter、start 四层可以协作，但源码依赖和业务责任不能随意反向流动。

## Allowed Directions

Domain layer 是独立层。它可以使用自己的类型、领域接口和值对象，但不依赖 application、adapter、start、Spring Boot、HTTP payload、外部 service payload 或 persistence implementation。`ContentBehavior.kt` 和 `ContentFactory.kt` 应该能在不理解 Controller 的情况下被阅读和测试。

Application layer 可以依赖 domain layer 和 application-facing contracts。Command、Query、Subscriber、Scheduled Reaction 可以加载 Aggregate、调用 domain 行为、使用 Repository、Mediator 或 Capability contracts 来表达用例，但不能定位或控制 Unit of Work。application layer 可以定义 `TriggerMediaProcessing` 和 `GetMediaProcessingStatus` 这类外部能力语义，但不依赖 adapter 中的 Capability Handler 实现。

Adapter layer 可以依赖 application/domain 暴露的入口和契约。Controller 把 HTTP 请求转换成 Command 或 Query，API Payload 描述协议字段，query adapter 组织读取输出，capability-handler 把 application 的 external capability request 转换成外部调用，persistence adapter 处理存储协议。对 inbound Integration Event，cap4k integration-event transport adapter/runtime 消费 HTTP/message protocol，解析、注册并分发 typed integration event；业务项目的 application-layer inbound integration subscriber 接收 typed external fact，处理幂等和语义翻译，并在需要改变状态时委托 Command/application behavior。

Start layer 可以依赖 domain、application 和 adapter 模块来完成 Spring Boot runtime assembly。它负责 local startup、runtime config、bean wiring 和 smoke path。这个方向只用于装配，不能让 domain、application 或 adapter 反向依赖 start。

## Forbidden Directions

Domain layer 不允许引用 Controller、API Payload、HTTP status、capability-handler、Spring bean wiring、runtime config 或 external protocol DTO。领域对象不能因为某个接口字段存在就改变不变量表达方式，也不能为了适配数据库或 HTTP 结构暴露协议细节。

Application layer 不允许接收 HTTP payload details 作为用例语义，也不允许直接依赖 adapter implementation。Command handler 不应该判断 URL、header、HTTP status 或 callback 原始字段；这些内容应先由 adapter 转换成 application 可以理解的 Command、Query、Integration Event 或 external capability result。

Adapter layer 不允许成为业务真相层。Controller、query handler、Capability Handler 和 persistence adapter 可以处理 mapping、技术错误、外部协议差异和返回格式。对 inbound Integration Event，cap4k transport adapter/runtime 负责协议消费与 typed event 分发；业务 subscriber 负责幂等、语义翻译和 Command delegation。两者都不应该把 Aggregate invariant 或流程编排决策藏在协议转换代码里。

Start layer 不允许保存业务规则。runtime config 可以选择 profile、端口、bean wiring、feature wiring 或 local startup 参数，但不应该决定内容是否可发布、媒体处理是否完成或 paid publication 是否符合业务条件。

## Review Rules

审查依赖方向时，可以按四个问题走：domain 是否独立，application 是否只编排用例，adapter 是否只做 protocol conversion，start 是否只做 assembly。若发现 inner layer 需要 import outer layer 类型，优先把外部细节转换成内部请求或领域事实，而不是放宽依赖规则。

生成骨架也要按同样规则审查。generated skeleton 可以给出 Command、Query、Capability、Subscriber、api payload、domain event、integration event 或 module wiring 的稳定位置；handwritten logic 需要落在对应层的可维护位置。generator 是组织工具，不是业务决策来源。

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。可以用 `cap4k-reference-content-studio-domain`、`cap4k-reference-content-studio-application`、`cap4k-reference-content-studio-adapter` 和 `cap4k-reference-content-studio-start` 对照检查：依赖应从外向内指向更稳定的业务和用例契约，业务规则应停留在 domain/application 的手写位置。
