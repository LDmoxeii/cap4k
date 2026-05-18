# 生成输入源

本页说明业务项目作者如何准备 cap4k 生成器输入。输入源是生成器合同，不是随手写给工具看的备注。

除 `sources {}` 里的 source provider 之外，`types.registryFile` 也属于 generation input contract。它不提供 use-case surface，也不替代 DDL / design；它负责补充 `@T` 绑定的自定义类型 FQN 与 converter 策略。

## DB source

`sources.db` 读取 JDBC metadata，用数据库 schema 表达聚合结构、字段、关系、枚举、唯一约束和持久化细节。

DB 输入适合表达：

- 聚合根、子实体、table-backed 值对象表，以及通过 `@T` 绑定的自定义值类型字段；
- 字段类型、ID 策略、软删除、版本号；
- 表间引用关系；
- 聚合内唯一约束；
- 与 enum manifest 关联的共享枚举。

DB source 不替代业务流程设计。命令、查询、client、validator 和领域事件等用例意图仍应通过 design JSON 或手写代码表达。

## DB table annotations

表注释可使用这些注解：

| 注解 | 含义 |
| --- | --- |
| `@Parent=<table>` / `@P=<table>` | 当前表属于父聚合；必须显式给出父表名 |
| `@AggregateRoot=true\|false` / `@Root=true\|false` / `@R=true\|false` | 显式聚合根标记；不带 `=true/false` 的 marker 形式无效 |
| `@ValueObject` / `@VO` | table-backed 值对象表标记；marker-only，不接受显式值 |
| `@Ignore` / `@I` | 忽略该表；marker-only，不接受显式值 |
| `@DynamicInsert=true\|false` | provider dynamic insert |
| `@DynamicUpdate=true\|false` | provider dynamic update |

聚合边界应由业务模型决定，再用表注释表达。不要为了凑生成结果而让数据库结构反向决定领域边界。

## DB column annotations

列注释可使用这些注解：

| 注解 | 含义 |
| --- | --- |
| `@Type=<TypeName>` / `@T=<TypeName>` | 绑定到命名领域类型或枚举；有效写法是显式给出 type name，空值或 marker 形式会被忽略 |
| `@Enum=<...>` / `@E=0:NAME:Desc\|...` | 内联枚举项；有效写法是显式给出枚举 payload，且该 payload 仍需要同时声明 `@T` |
| `@GeneratedValue` | marker 形式表示使用默认 ID 生成 |
| `@GeneratedValue=uuid7` | UUID7 策略 |
| `@GeneratedValue=snowflake-long` | Snowflake long 策略 |
| `@GeneratedValue=identity` | 数据库 identity 策略 |
| `@GeneratedValue=database-identity` | `identity` 别名 |
| `@Deleted` | 软删除字段；marker-only，不接受显式值 |
| `@Version` | 乐观锁字段；marker-only，不接受显式值 |
| `@Managed` | 框架管理字段；marker-only，不接受显式值 |
| `@Exposed` | 对外暴露字段；marker-only，不接受显式值 |
| `@Insertable=true\|false` | JPA insertability |
| `@Updatable=true\|false` | JPA updatability |

规则：

- `@Enum=<...>` / `@E=<...>` 的显式 payload 需要配合 `@Type` / `@T`；空值或 marker 形式不会产生日志外的额外含义。
- `@Managed` 与 `@Exposed` 互斥。
- `@GeneratedValue` 既可只写 marker，也可显式写 `uuid7` / `snowflake-long` / `identity` / `database-identity`。
- 旧的 `@IdGenerator` 和 `@SoftDeleteColumn` 已被拒绝，不应继续使用。

自定义值类型字段规则：

- 对 JSON-backed 或 inline 值对象，优先在列上使用 `@T=<TypeName>`，并在 `types.registryFile` 注册 FQN 与 converter。
- 生成器只会把字段类型和 JPA converter 映射到聚合；值对象类、构造 / 校验 / 归一化、converter 仍由作者维护。
- `@VO` 表只表示 separate-table / table-backed 值对象，适用面更重；不要为了“使用值对象”而默认建独立表。

## types registry

`types.registryFile` 位于 `types {}`，不是 `sources {}` block，但它仍会影响 aggregate 字段类型和 converter 映射。

最小形状：

```json
{
  "OrderId": { "fqn": "com.acme.order.OrderId" },
  "Customer": { "fqn": "com.acme.customer.Customer", "converter": false },
  "External": {
    "fqn": "com.acme.external.ExternalValue",
    "converter": "com.acme.external.ExternalValueConverter"
  }
}
```

规则：

- key 必须是非空 simple type name，不能覆盖 `String`、`Long`、`List`、`Any` 等内建类型；
- key 在 trim 归一化后不能重复；
- entry value 必须是 object；
- `fqn` 必填，且必须是 fully qualified name；
- `converter` 只能是 `false`、`"nested"` 或 converter FQN；
- 它负责类型与 converter 合同，不负责命令、查询、事件或聚合行为建模。

## DB relation annotations

关系注释可使用这些注解：

| 注解 | 含义 |
| --- | --- |
| `@Reference=<table>` / `@Ref=<table>` | 引用目标表；必须显式给出表名 |
| `@Relation=<kind>` / `@Rel=ManyToOne\|OneToOne\|*:1\|1:1` | 关系类型；必须显式给出值 |
| `@Lazy=<true\|false>` / `@L=true\|false` | 懒加载；必须显式给出值 |
| `@Count=<hint>` / `@C=<hint>` | count hint；必须显式给出值 |

规则：

- `@Relation`、`@Lazy`、`@Count` 必须配合 `@Reference`。
- many-to-many 当前不支持。
- 引用关系不等于聚合间可写强引用；写模型边界仍由聚合根负责。

## 唯一约束命名

唯一约束是 aggregate unique helper、unique query、unique validator 的来源。约束名应稳定、可读、能表达业务含义。

建议：

- 使用能表达业务键的名字，例如 `uk_content_tenant_slug`；
- 不要依赖数据库自动生成的随机约束名；
- 不要把技术临时字段放进业务唯一性；
- 只有当项目真的需要一等查询或校验形态时，才启用对应 unique helper 生成。

审阅时要确认唯一约束来自业务规则，而不是为了让生成器多产文件。

## design JSON supported tags

`sources.designJson` 用 JSON 显式表达用例和接口意图。当前支持的 `tag` 包括：

| Tag | 生成族 |
| --- | --- |
| `command` | command request / response / handler skeleton |
| `query` | query contract，以及配套 query handler family |
| `client` | external capability client contract，以及配套 client handler family |
| `api_payload` | adapter API payload |
| `domain_event` | domain event contract，以及配套 subscriber / handler skeleton |
| `integration_event` | application integration event contract and inbound subscriber skeleton |
| `validator` | validation annotation and validator |

常见字段包括 `package`、`name`、`desc`、`aggregates`、`requestFields`、`responseFields`。

附加规则：

- `query` 和 `api_payload` 支持 request trait `page`；
- `domain_event` 支持 `persist`；
- `domain_event` 可以省略 package；它必须恰好声明一个 aggregate，保留 request field `entity` 不允许作者显式声明，因为它会从 `aggregates[0]` 派生。缺失或空 aggregate 都属于不完整 modeling input；
- `integration_event` 支持 `role`（`inbound` / `outbound`）和 `eventName`，必须至少声明一个 `requestFields` 字段，且 `responseFields` 必须为空；`inbound` 可生成 `@EventListener` subscriber 骨架，`outbound` 只生成事件契约；
- `validator` 的 `targets` 只支持 `CLASS` / `FIELD` / `VALUE_PARAMETER`，`valueType` 只支持 `Any` / `String` / `Long` / `Int` / `Boolean`；`CLASS` target 只能配 `Any`。`parameters` 名称不能是 `message` / `groups` / `payload`，必须是合法 Kotlin 标识符、不可空、不可重复，类型只支持 `String` / `Int` / `Long` / `Boolean`，并且不能 nullable；
- manifest-file 模式把 manifest 中的 design 文件 entry 解析为相对 `projectDir` 的路径，并拒绝空白 `manifestFile`、空 manifest、空白 entry、重复 entry，以及逃出 `projectDir` 的路径。

## unsupported design tags

当前 design JSON 暂不支持：

- `value_object`
- `domain_service`

这些是明确缺口，不是隐藏能力。需要值对象或领域服务时，当前应通过 DB `@T` 类型绑定、必要时的 table-backed `@VO` 表、手写模型、后续 addon 或未来生成能力处理，并在项目审计中记录。需要集成事件时，使用 `integration_event` 设计契约；MQ 绑定和外部协议适配仍由项目手写。

## enum manifest

enum manifest 是 JSON 数组，每个枚举包含：

- `name`
- `package`
- `items[]`，其中有 `value`、`name`、`desc`

它配合 DB `@T=<TypeName>` 使用。重复 type name 会被拒绝。

`generateTranslation` 已从 enum manifest 移除。enum translation 属于 addon 生成方向，不属于核心 aggregate generation 开关。

## KSP metadata

`sources.kspMetadata` 用于读取聚合元数据，支撑 design-driven artifact 生成。典型场景是 design generator 需要理解已有聚合、请求契约或元数据关系。

作者规则：

- 需要 KSP metadata 的 design 生成，应先确保相关模块已能产生 metadata；
- 当 `cap4kPlan` 依赖 KSP 输出时，先看计划和任务依赖，不要手工移动 generated metadata；
- metadata 是生成 / 分析输入，不是业务逻辑落点。

## IR analysis

`sources.irAnalysis` 读取编译分析输出，用于 flow 和 drawing-board 这类观察型产物。它不是默认业务源码输入。

常见输入目录是各模块：

```text
build/cap4k-code-analysis
```

使用顺序：

1. 先让相关模块完成编译分析输出。
2. 配置 `sources.irAnalysis.inputDirs`。
3. 跑 `cap4kAnalysisPlan`。
4. 确认 `build/cap4k/analysis-plan.json`。
5. 再跑 `cap4kAnalysisGenerate`。

IR 分析适合审阅流程、关系和设计结果，不替代 `cap4kPlan` / `cap4kGenerate` 的源码生成链路。
