# Clean Architecture

cap4k 项目的 Clean Architecture mental model 是：业务规则在内层，技术入口和运行时装配在外层，源码依赖尽量指向更内侧的抽象。这样做的目的不是制造目录层级，而是让代码审查能快速回答三个问题：业务真相在哪里，用例如何被编排，外部协议在哪里被转换。

四层从内到外分别是 domain、application、adapter 和 start。domain layer 保护业务事实和不变量；application layer 组织一个用例如何读取、调用 domain 行为、保存和触发后续反应；adapter layer 把 HTTP、callback、external service、persistence 等技术协议转换成 application layer 能理解的请求和结果；start layer 装配 Spring Boot runtime、配置和启动路径。

## Layer Responsibilities

Domain layer 是最内层。它负责 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Entity](../concepts/modeling-building-blocks/entity.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、[Factory](../concepts/modeling-building-blocks/factory.md)、[Domain Service](../concepts/modeling-building-blocks/domain-service.md) 和 [Domain Event](../concepts/modeling-building-blocks/domain-event.md)。它不应知道 Controller、API Payload、HTTP status、external service payload、Spring Boot startup 或数据库实现细节。

Application layer 负责用例编排。Command handler 处理写入意图，Query handler 处理读取意图，Subscriber 和 Scheduled Reaction 处理事实之后的反应，Saga 处理需要持久化进度的跨步骤协调。Unit of Work、Repository 和 Mediator 帮助 application layer 管理提交边界和入口路由，但业务不变量仍由 domain layer 表达。

Adapter layer 负责协议转换。Controller 接收 HTTP request，API Payload 描述对外接口字段，client-handler 调用外部能力，inbound integration listener 消费外部事实，persistence adapter 处理存储实现。adapter 可以做 mapping、错误码转换、鉴权上下文翻译和外部协议容错，但不应该成为业务真相层。

Start layer 负责 runtime assembly。它把各模块放进 Spring Boot 运行时，提供 local startup、runtime config、bean wiring 和 smoke path。start layer 可以依赖其他模块进行装配，但不应该新增业务判断，也不应该让 inner layers 反向依赖它。

## Generation Supports The Model

cap4k generation 支持这套 layer model 的方式，是把 design tags 转换成稳定骨架、命名、目录和 wiring 入口。例如 domain event、command、query、subscriber、client、api payload、saga 等输入可以让项目拥有一致的结构；真正的状态转移、业务不变量、查询语义、外部能力语义、异常处理和恢复策略仍属于 handwritten logic。

这个边界很重要。generated skeleton 提供的是 shape，不是业务结论。团队可以在 `ContentBehavior.kt`、`ContentFactory.kt`、`PublishContentCmd`、`GetContentDetailQry`、`ContentPublicationReadyDomainEventSubscriber`、`TriggerMediaProcessingCliHandler` 或 `PaidPublicationSaga` 这样的手写位置表达业务含义，同时让生成骨架保持可审查的入口和命名。

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读时可以把四个模块放在一起看：`cap4k-reference-content-studio-domain` 保护内容生命周期，`cap4k-reference-content-studio-application` 编排发布和媒体处理用例，`cap4k-reference-content-studio-adapter` 处理 HTTP 和外部能力协议，`cap4k-reference-content-studio-start` 负责 Spring Boot 运行时装配。

## Review Shape

审查 Clean Architecture 时，先确认 domain layer 是否可以脱离 adapter/start 阅读；再确认 application layer 是否只组织用例而不接收 HTTP payload details；接着确认 adapter layer 是否只做 protocol conversion 和技术边界处理；最后确认 start layer 是否只负责 assembly、config 和 smoke path。若某段代码同时承担业务不变量、HTTP 字段转换、外部调用和 runtime config，它通常应该被拆回对应层。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 cap4k 项目的 Clean Architecture 四层 mental model。
Type: architecture diagram
Prompt: Draw a Clean Architecture diagram for a cap4k project. Show four concentric or stacked layers from inner to outer: domain, application, adapter, start. Use Chinese labels while preserving English identifiers. Show dependency arrows pointing inward only, with generated skeletons as stable entry shapes and handwritten logic as business behavior locations.
Must show: domain layer as business facts and invariants, application layer as use case orchestration, adapter layer as protocol conversion, start layer as Spring Boot runtime assembly, generated skeleton versus handwritten logic boundary, reference project module names
Must avoid: arrows from domain to adapter or start, HTTP payload inside domain, application depending on start, generator writing business decisions automatically, runtime config becoming business truth
Alt text after insertion: cap4k Clean Architecture 四层图，domain 在内层，application 组织用例，adapter 转换协议，start 装配 Spring Boot runtime，依赖方向向内。
-->
