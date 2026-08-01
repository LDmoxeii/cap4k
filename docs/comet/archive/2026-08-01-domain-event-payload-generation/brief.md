# Outcome

修复默认 Domain Event 生成器相对运行时历史事实边界的漂移：新生成的 Domain Event 不再持有持久化 Entity/Aggregate 引用，并通过生成器、渲染器与运行时之间的跨块契约测试证明一致性。

# Scope

- `domain_event` design JSON 到 canonical model、generator plan 和 Pebble template 的 payload 投影契约。
- 默认 Domain Event 模板、相关 generator/renderer/Gradle functional tests。
- 至少一个“生成事件源码 → 编译 → 运行时 payload validation”跨块契约测试。
- 与该生成契约直接相关的 public docs 和 skill facts。

# Non-goals

- 不放宽、绕过或回滚运行时 `DomainEventPayloadValidator`。
- 不改变 Domain Event attach/release、同步订阅、可靠持久化、UoW 或 after-commit 语义。
- 不改变 Integration Event payload 或 Published Language 契约。
- 不在本变更中引入 Event Sourcing、Saga/Process Manager 或新的事件运行时 provider。
- 不把 aggregate 数据库结构自动镜像为事件 payload。
- 不改变全局 `SKIP` conflict policy；它保护 generated/handwritten ownership，不作为旧 Domain Event 契约的兼容层。

# Acceptance examples

- 给定归属于 `Order` 聚合的 `OrderCreated` Domain Event，新生成的事件源码不得包含 `val entity: Order` 或其他持久化 Entity/Aggregate 字段。
- 给定显式历史事实字段 `orderId: OrderId` 和 `occurredReason: String`，生成源码只保留这些字段、嵌套值类型和必要 imports，并可被 Kotlin 编译器编译。
- 给定没有显式 fields 的 Domain Event，生成无参 marker event，不自动注入 aggregate ID、Entity 或 aggregate snapshot。
- 生成事件实例通过未修改的 `DomainEventPayloadValidator`；显式持有持久化 Entity 的负例仍被运行时拒绝。
- `aggregates: ["Order"]` 继续用于事件归属、package、BuildingBlock metadata 和 handler 规划，不再隐式决定 payload 对象。
- generator、renderer 和 functional tests 不再断言或输出隐式 `entity` 字段。
- 给定直接字段 `order: Order`、嵌套 `snapshot.order: Order`、`List<Order>` 或 Map value 中的 `Order`，pipeline 在写出生成文件前失败，并报告事件名、字段路径和违规 Entity/Aggregate 类型。
- 给定 `orderId: OrderId`、普通 Value Object 或不可变 snapshot，pipeline 正常生成；未知包装类型内部隐藏的 Entity 仍由 runtime guard 兜底。
- 给定字段名 `entity` 但类型为非 Entity/Aggregate 的标量或值类型，design JSON 不再因字段名本身拒绝；若其 resolved type 是 `Order` 等已知 Entity，仍按类型检查失败。
- generator plan、render model、template context、fixtures、tests 和仓库文档中不保留 `aggregateName`/`aggregateType` payload shim、deprecated alias 或 legacy toggle。
- 仓库内所有仍依赖隐式 `entity` payload 的 fixtures、samples 和 assertions 在同一变更中直接更新或删除。

# Constraints and invariants

- Domain Event 是已经发生的不可变历史事实；payload 可以包含标量、Strong ID、Value Object、不可变快照和这些值的集合，但不能包含 live persistent Entity/Aggregate reference。
- 运行时历史事实校验是已接受契约，修复责任属于 generator。
- 生成器输入必须显式表达业务事实，不得从 aggregate persistence shape 猜测事件快照。
- `domain_event` 仍必须声明且只声明一个 owning aggregate，以保持当前 package、metadata 和 handler 归属契约。
- 保持固定 pipeline 阶段、保守类型解析和 generated/handwritten ownership 边界。

# Decisions

- 2026-08-01：运行时拒绝持久化 Entity/Aggregate payload 的行为保持不变。
- 2026-08-01：当前 `val entity: <Aggregate>` 属于生成器漂移，而不是需要恢复的兼容行为。
- 2026-08-01：事件 payload 不得自动镜像 aggregate 数据库字段。
- 2026-08-01：Domain Event payload 仅由 design JSON 显式 `fields` 决定；`aggregates` 只负责归属、package 和 BuildingBlock metadata。
- 2026-08-01：需要 aggregate identity 时由 author 显式声明 Strong ID 字段；生成器不自动追加 ID。
- 2026-08-01：没有显式 fields 时生成无参 marker event。
- 2026-08-01：显式字段图中出现任何 cap4k 已知 Entity/Aggregate 类型时，在生成文件前失败；检查覆盖直接、嵌套、集合、数组和 Map key/value。
- 2026-08-01：早期失败适用于 `persist=true` 和 `persist=false` 的所有 Domain Event；运行时 validator 继续兜底未知包装类型和非生成代码。
- 2026-08-01：cap4k 当前没有外部用户，本变更采用 breaking reset；不提供旧 payload context、兼容别名、deprecation period、legacy toggle 或面向外部用户的迁移层。
- 2026-08-01：仓库内旧 fixtures/samples/assertions 直接按新契约更新或删除。
- 2026-08-01：保持全局 `SKIP` ownership，因为它保护 handwritten source，不用于保留旧 Domain Event 行为。
- 2026-08-01：删除 `entity` 字段名的特殊保留规则；generator 只根据 resolved semantic type graph 判断 Entity/Aggregate 引用，命名质量由 skill/design review 管理。
- 2026-08-01：用户确认上述 breaking reset 共享理解并授权进入 Build。

# Open questions

- 无。

# Verification expectations

- 运行 design JSON source、design generator、Pebble renderer、Gradle functional 和 `ddd-core` payload validator 聚焦测试。
- 新增跨块测试，确保默认生成结果能够编译并通过未修改的运行时 payload validator。
- 负例覆盖直接 Entity、嵌套 Entity 和集合中的 Entity 仍被运行时拒绝。
- 运行 skill validator、相关文档/fixture 静态扫描、`git diff --check` 和 Comet acceptance evidence。
