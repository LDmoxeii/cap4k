# Outcome

存活的 cap4k Runtime JSON 编解码统一到当前 Jackson 基线：可靠 Command/Event 本地记录、HTTP/RabbitMQ/RocketMQ Integration Event envelope，以及其他仍在运行时执行的 FastJSON/Gson 路径均由同一个框架拥有的 Jackson 边界负责。保持现有可靠投递、路由、provider 选择和状态机事实不变。

# Scope

- 从 `origin/master` 当前 Runtime map 和 #106 的全仓目标中筛选仍存活的 `ddd-*` 与 `cap4k-ddd-*-starter` Runtime owner module。
- 统一可靠 Command/Event payload persistence 与 HTTP/RabbitMQ/RocketMQ Integration Event envelope 的序列化/反序列化。
- 复用或提取一个集中、可测试、Kotlin-aware 的 Jackson codec/configuration boundary，不在各 Runtime module 建立兼容性 mapper 或旧 codec fallback。
- 覆盖 Kotlin data class、私有构造、默认值、null、集合、嵌套对象、Strong ID 标量字符串、确定性字段顺序与 map key 输出。
- 为 Command/Event/Integration Event envelope 增加往返、失败安全和 payload 不泄露的 focused tests，并更新必要 owner module 依赖。
- 在生产源码与依赖图中移除存活 Runtime codec 的 FastJSON/Gson 依赖；Pipeline 相关路径不在本分支处理。

# Non-goals

- 不修改 Integration Event routes、provider selection、HTTP 自产自销方式或 at-least-once 语义。
- 不修改 retry、claim、lease、redrive、retention、outbox 状态机和记录所有权。
- 不恢复 #159、#162、#163 退役的 Console、Snowflake、公开 Spring Data Repository。
- 不改变应用侧仓储入口 `Mediator.repositories`，不重新引入 `EventSubscriber<T>` 公共写法。
- Handler 本身保持同步；不把 `Mediator` 的 enqueue/schedule/delay 调度改成 Handler 异步执行。
- 可靠事件 payload 只能持久化 JSON 载荷，明确拒绝持久化领域实体。
- 不保留兼容桥、deprecated alias、双实现或旧 codec fallback；不迁移 Pipeline/Analyzer。

# Acceptance examples

- Kotlin data class 使用私有构造、默认值、nullable 字段、集合和嵌套 data class 时可经共享 codec round-trip，缺省值/null 语义稳定。
- 生成的 Strong ID 在 JSON 中始终是标量 canonical UUID 字符串，而不是对象树。
- Command、可靠 Domain Event、HTTP/RabbitMQ/RocketMQ Integration Event envelope 序列化后再反序列化，类型、元数据和 JSON payload 保持一致。
- 同一输入的 envelope 与 map 输出字段顺序确定；不会因 HashMap 遍历顺序造成快照漂移。
- 解码失败的异常与日志只包含 codec/type/metadata 摘要，不包含原始业务 JSON payload；可靠实体 payload 在写入前被拒绝。
- 生产源码、运行时 Gradle 依赖和 resolved dependency graph 中不再存在存活 Runtime codec 的 `com.alibaba.fastjson`、`com.google.gson` 或等价 FastJSON/Gson 入口。

# Constraints and invariants

- Jackson 基线版本和 Kotlin module 以当前仓库已存在配置为准；新增配置必须集中在 Runtime 可复用边界。
- 可靠记录仍由各自 owner module 管理，codec 只负责 JSON payload/envelope，不接管状态机、事务或 transport bookkeeping。
- Domain Event 仍是不可变业务事实，持久化层拒绝 Aggregate/Entity 实例作为可靠 payload。
- Integration Event 传输只替换编解码实现，不改变 routes、publisher/subscriber provider composition、HTTP registry 或至少一次语义。
- 任何失败诊断必须通过安全摘要表达，不能把原始 payload 放入 exception message、日志或 retry/record diagnostics。
- 没有外部用户，允许破坏性迭代；不引入兼容层来保留旧 API 或旧 codec。

# Decisions

- 本分支只处理 Runtime，Pipeline/Generator/Analyzer 的 Jackson 迁移由其他独立切片负责。
- 采用一个 Runtime-owned Jackson codec boundary；各 owner module 通过该边界调用，不各自 new 出行为不一致的 ObjectMapper。
- 可靠 payload 的唯一持久化载体是 JSON 文本/树；实体对象不会被序列化为可靠 payload。
- Handler 同步语义保持不变；`askAsync`/`callAsync` 仍只属于 Mediator 调度作用域，不在本变更中改执行模型。
- 不做旧 FastJSON/Gson fallback 或 alias；迁移完成后旧依赖和入口必须删除。

# Open questions

无。用户已确认本分支的范围、破坏性迭代许可和所有 Runtime 历史事实边界。

# Verification expectations

- 先运行 Runtime owner focused tests，再运行 `scripts/validate-current-runtime-facts.ps1` 与完整 `check`。
- 静态搜索生产 Runtime source/build files 与 resolved dependency graph，证明没有存活 FastJSON/Gson codec 依赖或旧 fallback。
- 通过 focused tests 证明 Kotlin construction/default/null/collection/nested、Strong ID scalar、三类 envelope round-trip、确定性输出、实体拒绝和 payload-safe failure diagnostics。
- `git diff --check`、Comet Native `check`、最终 PR base `master` 均通过；不修改 `docs/framework-capability-audit` 审计分支。
