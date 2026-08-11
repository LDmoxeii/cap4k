# Runtime Jackson-only Codec Contract

## Outcome

存活 Runtime 的可靠记录和 Integration Event envelope 统一使用当前 Jackson 基线，删除 FastJSON/Gson 的 Runtime 编解码入口，且不改变可靠投递和 transport 状态语义。

## In-scope owners

- Command reliable record payload persistence owner。
- Domain Event reliable record payload persistence owner。
- HTTP Integration Event envelope publisher/subscriber owner。
- RabbitMQ Integration Event envelope publisher/subscriber owner。
- RocketMQ Integration Event envelope publisher/subscriber owner。
- 这些 owner 使用的 Runtime starter、共享 codec/configuration、focused tests 和 Runtime 依赖声明。

Pipeline/source/renderer/Gradle plugin JSON 路径不属于本 spec；它们由独立 Pipeline Jackson 变更处理。

## Codec contract

1. Runtime 使用一个框架拥有的 Jackson codec boundary，统一注册 Kotlin module、Strong ID scalar serializers/deserializers、默认值/null/私有构造、集合和嵌套对象处理，以及确定性的 property/map-key 输出。
2. codec 的输入和输出是 JSON 文本或 JSON tree；可靠事件 payload 不接受 Aggregate、Entity 或其他持久化领域实体实例。
3. Strong ID 在 JSON 中编码为 canonical scalar string，并通过同一类型契约解码；不能退化为 `{ "value": ... }` 等对象形状。
4. 业务 payload 仅在 owner 已决定其类型后解码；缺失/非法 metadata 或无法构造 payload 时 fail fast。
5. 异常和日志只携带 codec、目标类型、envelope metadata 的安全摘要，不携带原始业务 JSON。
6. 输出属性顺序和 map key 顺序稳定，以便 envelope 快照、重试记录和测试结果确定。

## Envelope contract

Command/Event reliable record 与 HTTP/RabbitMQ/RocketMQ Integration Event envelope 保持既有字段和语义。PR #164 的迁移只替换 serializer/deserializer 实现，当时不负责改变 transport topology。以下事实由该切片保持：

- routes、publisher/provider selection 和 HTTP self-routing；
- at-least-once delivery、retry、claim、lease、redrive、retention、outbox state machine；
- origin ExecutionContext / reliable delivery context 的现有归属；
- handler 同步、顺序/失败传播和 Mediator enqueue/schedule/delay 调度边界。

后续 PR #177 和 PR #179 完成 transport reset，删除 HTTP dynamic subscriber registry、subscriber capabilities、JPA carrier 和 table。当前 Runtime 不存在 HTTP subscriber registry；本 spec 中的历史迁移边界不能被解读为保留或恢复该 registry。

每个 envelope 必须有 round-trip tests，覆盖 metadata、payload、null/default、嵌套和 Strong ID 字段。

## Dependency and removal contract

- Runtime production source 不能再引用 `com.alibaba.fastjson*`、`com.google.gson*` 或等价 FastJSON/Gson API。
- Runtime owner Gradle modules 不再声明这些 codec 依赖；resolved dependency graph 不能通过 starter/transitive path 带入它们。
- 不保留旧 codec fallback、deprecated alias、compatibility bridge 或 dual implementation。
- Pipeline 依赖和历史 docs 可由其他切片处理；本分支验收只对存活 Runtime 范围做强断言，并明确排除 Pipeline。

## Verification matrix

| Contract | Required evidence |
| --- | --- |
| Kotlin construction | data class, private constructor, default values, null, collections, nested object round-trip |
| Identity | Strong ID always scalar canonical string |
| Persistence | reliable Command/Event payload JSON round-trip and entity rejection |
| Transport | HTTP/RabbitMQ/RocketMQ envelope round-trip |
| Determinism | stable field and map-key output |
| Safety | decode/encode failure diagnostics omit raw payload |
| Removal | Runtime source/dependency graph has no active FastJSON/Gson codec or HTTP subscriber registry |
| Regression | owner focused tests, current-runtime-facts validation, full `check` |

## Explicit non-goals

This change did not restore retired Console, Snowflake, or public Spring Data Repository surfaces; did not change `Mediator.repositories`; did not introduce public `EventSubscriber<T>`; did not make handlers asynchronous; and did not alter routes, provider selection, HTTP self-routing, at-least-once semantics, or any reliable-record state machine. Later transport changes may and did remove obsolete topology surfaces without changing this codec contract.
