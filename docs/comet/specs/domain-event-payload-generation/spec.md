# Domain Event Payload Generation

## Purpose

默认 Domain Event 生成必须符合 cap4k 已接受的历史事实边界：生成结果只承载不可变业务事实，不持有 live persistent Entity/Aggregate reference。

## Requirements

### Historical-fact boundary

- 运行时 `DomainEventPayloadValidator` 保持现有严格行为。
- 生成器不得为了兼容旧模板而绕过、关闭或弱化 payload validation。
- 生成的 Domain Event payload 可以包含标量、Strong ID、Value Object、不可变快照及其集合。
- 生成的 Domain Event payload 不得包含 persistent Entity/Aggregate reference，包括直接字段、嵌套字段、集合、数组或 Map 中的引用。

### Aggregate ownership and payload separation

- `aggregates` 继续声明 Domain Event 的唯一 owning aggregate。
- owning aggregate 继续决定事件 package、BuildingBlock aggregate metadata 和相关 subscriber/handler 归属。
- owning aggregate 不得再隐式投影为 `val entity: <Aggregate>` payload 字段。
- 生成器不得从 aggregate persistence/schema fields 自动猜测事件事实快照。

### Explicit payload projection

- Domain Event payload 只包含 design JSON `fields` 明确声明的业务事实。
- 生成器不得自动追加 aggregate ID；需要 identity 时 author 必须显式声明 Strong ID 或其他稳定标识字段。
- 显式字段的标量、Strong ID、Value Object、嵌套不可变值类型、集合、数组和 Map 继续使用当前 semantic type projection 与 import 规则。
- 没有显式 fields 时生成合法的无参 marker event。
- generator plan/template context 不再携带仅用于隐式 aggregate payload 的 `aggregateName` 或 `aggregateType`。

### Generator and source consistency

- design JSON、canonical model、artifact plan 和模板必须对 payload 来源保持同一契约。
- 与旧隐式 `entity` 契约相关的错误信息、render context、fixtures、tests 和文档必须同步更新，不能保留相互矛盾的事实。
- generated source 和 handwritten behavior 的 ownership 规则保持不变。

### Entity reference validation

- 对每一个 Domain Event，pipeline 必须在写出生成文件前检查已经解析的完整 semantic field graph。
- 检查必须覆盖直接字段、nested value definitions、List、Set、Array、Map key 和 Map value。
- 当任一 resolved named type 对应 canonical model 中的已知 Entity/Aggregate 时，pipeline 必须失败。
- 失败信息必须包含 Domain Event 名称、可定位的字段路径和违规类型。
- 该规则同时适用于 `persist=true` 和 `persist=false`；历史事实边界不取决于可靠持久化开关。
- Strong ID、普通 Value Object、标量和不可变 snapshot 必须继续允许。
- 对 generator 无法证明其内部结构的未知命名类型，runtime validator 继续作为最终安全网。
- design JSON 不得仅因为 Domain Event 字段名为 `entity` 而拒绝输入；安全判断必须基于 resolved semantic type graph。
- 如果名为 `entity` 的字段解析为已知 Entity/Aggregate，仍按同一类型规则失败；如果解析为非实体标量或值类型，则允许生成。

### Breaking reset and ownership

- 本变更不保留旧隐式 aggregate payload 的 render context、template variable、compatibility alias、deprecation period 或 legacy toggle。
- `DesignRenderModel`、generator plan 和 template context 中只为旧 `entity` payload 服务的 `aggregateName`/`aggregateType` 必须直接删除，不保留空值 shim。
- 仓库内 fixtures、samples、assertions 和文档必须在同一变更中切换到显式 fields 契约；不维护旧行为双轨测试。
- 全局 `SKIP` conflict/ownership 语义保持不变，因为它保护 checked-in handwritten source，不是 Domain Event 兼容机制。
- 不提供通过放宽 runtime validator 来运行旧事件的开关。

### Cross-block verification

- 测试必须覆盖 design input 到 rendered Kotlin source。
- 测试必须证明 rendered event 可编译并通过未修改的 runtime payload validator。
- 测试必须保留 runtime 对直接、嵌套和集合 Entity reference 的拒绝证据。

## Non-goals

- 不改变 Domain Event dispatch、persistence、UoW 或 subscriber 执行语义。
- 不改变 Integration Event payload。
- 不自动生成完整 aggregate snapshot。
- 不新增 Event Sourcing 或 Saga 能力。
