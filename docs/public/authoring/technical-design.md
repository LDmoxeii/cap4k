# Technical Design

technical design 把业务模型放进 cap4k 的 Clean Architecture。它回答“代码责任落在哪一层、哪些入口是 Command 或 Query、哪些反应由 Subscriber 或 Scheduled Reaction 承担、哪些跨步骤协调需要 Saga、adapter 和 persistence 应该停在哪里、测试要证明什么”。

如果业务意图和建模还不稳定，先回到 [Business Intent And Modeling](business-intent-and-modeling.md)。technical design 不是把所有设计都写进 generator inputs 的中转站；它是 authoring 螺旋中保护边界的关键面。

## Clean Architecture

cap4k public docs 使用 [Architecture](../architecture/index.md) 中的四层模型：

- domain layer：Aggregate、Entity、Value Object、Factory、Domain Service 和 Domain Event。
- application layer：Command、Query、Subscriber、Saga、Scheduled Reaction、Unit of Work、Mediator 和 external capability requests。
- adapter layer：Controller、API Payload、query adapter、client-handler、inbound integration listener 和 persistence adapter。
- start layer：Spring Boot runtime assembly、local startup、runtime config 和 smoke path。

依赖方向向内。domain 不知道 HTTP、callback、client-handler 或 start assembly。application 可以组织用例，但不承载领域不变量。adapter 处理协议转换，但不决定业务真相。start 装配 runtime，但不承载业务规则。

## Modules

[Reference Content Studio](../examples/reference-content-studio.md) 使用四个 Gradle 模块：

- `cap4k-reference-content-studio-domain`
- `cap4k-reference-content-studio-application`
- `cap4k-reference-content-studio-adapter`
- `cap4k-reference-content-studio-start`

authoring 时先把 building blocks 放进正确 module，再写 generator inputs。比如 `Content` behavior 和 `MediaProcessingResultSnapshot` 属于 domain；`PublishContentCmd`、`MediaProcessingCallbackIntegrationEventSubscriber` 和 `PaidPublicationSaga` 属于 application；`ContentController`、query handler、client-handler 和 callback consumption adapter 属于 adapter；boot application、runtime config 和 smoke path 属于 start。

## Command And Query Boundaries

[Command Query Separation](../concepts/execution-and-ownership/command-query-separation.md) 是 application design 的默认入口。Command 表达改变业务事实的意图，例如 `CreateContentDraftCmd`、`ApproveContentReviewCmd`、`StartMediaProcessingCmd`、`MarkMediaProcessingSucceededCmd`、`RecordContentMediaReadyCmd` 和 `PublishContentCmd`。Query 表达观察，例如 `GetContentDetailQry`、`GetMediaProcessingStatusQry` 和 `GetPaidPublicationStatusQry`。

写入路径应该加载 Aggregate、调用 domain behavior、保存并释放事件。读取路径应该组织 read model 或展示 shape，不应偷偷改变状态。如果一个 handler 同时查询展示数据和推进业务事实，technical design 应该拆开它。

## Events And Reactions

Domain Event 是领域内部事实，Integration Event 是跨边界事实。technical design 要决定每个 Event 后续由谁响应：

- Domain Event Subscriber：响应内部领域事实，例如内容发布准备完成后发送 `PublishContentCmd` 或进入 paid publication 判断。
- Integration Event Subscriber：把外部事实转成内部命令，例如媒体处理 callback 转成 `MarkMediaProcessingSucceededCmd`。
- outbound integration event：由 application reaction 基于内部事实 attach，对外表达跨边界事实。

Subscriber 不应该成为中心业务判断器。它可以路由后续 Command，但真正的写入资格仍应由 Command 和 Aggregate 重新校验。

## Saga Decisions

Saga 用于需要持久化进度、retry、recovery 或 compensation 的跨步骤协调。它不等同于“多个动作的流程图”。默认内容发布路径在媒体处理成功后发布内容，不需要因为存在多个步骤就自动成为 Saga。

paid publication 是显式 opt-in 的 Saga 示例：它要协调 payout hold、entitlement plan、内容发布、权益激活和补偿。这个流程需要记录子步骤状态，并在失败时执行可恢复或可补偿动作，因此适合 `PaidPublicationSaga` 和 `PaidPublicationTask`。

判断是否使用 Saga 时，先问是否存在跨时间状态、恢复点、重试点或补偿点。答案不成立时，优先使用 Command、Subscriber 或 Scheduled Reaction。

## Subscriber And Scheduled Reaction

Subscriber 处理事件后的 application reaction。它应该保持薄而明确：接收事件，做必要过滤，发送内部 Command 或 Query，不直接把 adapter payload 或外部协议塞进领域。

Scheduled Reaction 表达定时或轮询触发的 application reaction。参考项目中的 `MediaProcessingPollingFallbackJob` 是媒体处理状态的 fallback reaction；它不推翻 callback-first 的业务主路径。polling 读取外部状态后也应收敛到内部命令语义，而不是自己写入 `Content` 或 `MediaProcessingTask`。

## Adapter Boundaries

[Adapter Layer](../architecture/adapter-layer.md) 负责协议转换。Controller 把 HTTP request 转成 Command 或 Query；callback consumption 把外部事实转成 Integration Event 或内部命令入口；client-handler 把 application-facing external capability request 转成具体外部调用；query adapter 组织读取 shape；persistence adapter 处理技术映射。

如果 adapter 中出现“内容是否可发布”“是否应该启动 paid publication”“媒体处理成功后是否更新内容状态”这类判断，就说明业务规则越界。adapter 可以识别外部状态码和错误，但业务含义要回到 application/domain。

## Persistence Expectations

persistence design 要服务 Aggregate ownership。schema 可以表达聚合表、ID、引用、enum type marker、JSON-backed value marker 和唯一约束；Repository 和 Unit of Work 负责按聚合保存。不要把数据库外键或查询便利性误当作跨聚合可写引用。

参考项目 schema 中，`content`、`media_processing_task` 和 `paid_publication_task` 都标记为 Aggregate Root；`media_processing_task.result_snapshot` 通过 `@T=MediaProcessingResultSnapshot;` 指向 JSON-backed Value Object；`release_policy` 通过 `@T=ReleasePolicy;` 指向 enum manifest。technical design 要确认这些 persistence facts 和模型一致。

## Testing Expectations

[Testing By Layer](../architecture/testing-by-layer.md) 是 technical design 的验证边界。domain tests 应直接覆盖业务不变量和状态变化；application tests 覆盖 Command、Subscriber、Saga、Scheduled Reaction 和 external capability semantics；adapter tests 覆盖 protocol conversion；start tests 覆盖 runtime assembly 和 smoke path。

测试设计不应把全部正确性压在 HTTP smoke path 上。smoke path 能证明系统连通，不能替代 `ContentBehaviorTest`、`ContentFactoryTest`、`PublishContentCommandContractTest` 或 Saga compensation 相关的 focused evidence。

## Feedback Signals

这些信号说明 technical design 需要回到前一圈或重写边界：

- application handler 开始承载领域不变量。
- adapter 直接修改 Aggregate 或解释业务发布条件。
- Query handler 出现状态变更。
- Subscriber 变成中心流程所有者。
- Saga 没有持久化协调语义。
- polling fallback 和 callback 主路径产生两套事实来源。
- 测试只覆盖外层 smoke，内层行为没有 focused evidence。

technical design 的目标是让这些问题有明确归位，而不是把它们留给生成或实现阶段临时处理。
