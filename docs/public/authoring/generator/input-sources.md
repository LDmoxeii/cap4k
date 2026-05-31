# 生成输入源

本页说明业务项目作者如何准备 cap4k 生成器输入。输入源是生成器合同，不是随手写给工具看的备注。

本页示例语境统一回到 [示例总览](../examples/index.md)：DB / design / enum manifest / value-object manifest / IR 输入最终都服务于同一个内容发布与媒体处理项目。

除 `sources {}` 里的 source provider 之外，`types {}` 里的 `enumManifest`、`valueObjectManifest` 和 `registryFile` 也属于 generation input contract。它们不提供 use-case surface，也不替代 DDL / design。

## DB source

`sources.db` 读取 JDBC metadata，用数据库 schema 表达聚合结构、字段、关系、枚举、唯一约束和持久化细节。

DB 输入适合表达：

- 聚合根、子实体、table-backed 值对象表，以及通过 `@T` 绑定的自定义值类型字段；
- 字段类型、Strong ID 身份边界、软删除、版本号；
- 表间引用关系；
- 聚合内唯一约束；
- 与 `types.enumManifest` 关联的共享枚举；
- 与 `types.valueObjectManifest` 或 `types.registryFile` 关联的 JSON-backed / inline custom value carrier。

DB source 不替代业务流程设计。命令、查询、client 和领域事件等用例意图仍应通过 design JSON 或手写代码表达；core design JSON 不生成通用 validator。

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
| `@RefId=<TypeName>` | 当前上下文的引用身份；适合把外部概念映射成本地语言里的 ID 类型 |
| `@RefAggregate=<AggregateName>` | 同一上下文内引用另一个聚合根；生成目标聚合的 ID 类型 |
| `@GeneratedValue=identity` | 数据库 identity 策略 |
| `@GeneratedValue=database-identity` | `identity` 别名 |
| `@Deleted` | 软删除字段；marker-only，不接受显式值 |
| `@Version` | 乐观锁字段；marker-only，不接受显式值 |
| `@Managed` | 框架管理字段；marker-only，不接受显式值 |
| `@Inherited` | 该列由实体父类或模板 override 声明；canonical facts 保留，但默认 entity 不重复生成字段 |

规则：

- `@Enum=<...>` / `@E=<...>` 的显式 payload 需要配合 `@Type` / `@T`；空值或 marker 形式不会产生日志外的额外含义。
- 数据库 primary-key metadata 是 ID source of truth；列注释不再表达主键身份。
- 默认聚合 ID 生成不依赖 nil UUID sentinel 或保存时反射赋值；Strong ID 聚合根 ID 由生成的 ID 类型在工厂创建时产生。
- 只有需要表达数据库 identity 语义时，才在主键列上使用 `@GeneratedValue=identity` 或 `@GeneratedValue=database-identity`。
- `@Inherited` 用在 ID 列时，必须有 mapped superclass 或 template override 提供 ID mapping；默认生成的 entity source 会省略 inherited fields。
- 旧的 ID generator 和 soft-delete column 注释已被拒绝，不应继续使用。

Strong ID 输入示例：

```sql
comment on table content is '@AggregateRoot=true;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
```

生成含义：

```kotlin
class Content(
    val id: ContentId,
    val authorId: AuthorId,
    val mediaProcessingTaskId: MediaProcessingTaskId?,
)
```

`@RefAggregate=MediaProcessingTask` 表示同一上下文内的聚合引用，字段类型应跟随 `MediaProcessingTaskId`。`@RefId=AuthorId` 表示当前内容上下文里的作者身份，即使上游系统把这个概念叫 user，也不要在 `Content` 内直接建模跨上下文的 `UserId`。

自定义值类型字段规则：

- 对 JSON-backed 或 inline 值对象，优先在列上使用 `@T=<TypeName>`，再选择 `types.valueObjectManifest` 或 `types.registryFile` 表达类型合同。
- 使用 `types.valueObjectManifest` 时，生成器会生成 checked-in value-object source，并在值对象类内直接嵌套 JPA converter；不需要再额外写一条 `types.registryFile` entry。
- 使用 `types.registryFile` 时，生成器只消费字段类型和 converter 映射；值对象 class、构造 / 校验 / 归一化、converter 仍由作者维护。
- `@VO` 表只表示 separate-table / table-backed 值对象，适用面更重；不要为了“使用值对象”而默认建独立表。

## types contracts

`enumManifest`、`valueObjectManifest` 和 `registryFile` 都位于 `types {}`，不是 `sources {}` block。

最小 DSL：

```kotlin
types {
    enumManifest {
        files.from("design/enums.json")
    }
    valueObjectManifest {
        files.from("design/value-objects.json")
    }
    registryFile.set("design/types-registry.json")
}
```

`enumManifest` entry 和 `valueObjectManifest` entry 不需要在 `registryFile` 里重复注册。`registryFile` 只用于不由 manifest 生成、但仍需要通过 `@T` 绑定的外部或手写类型。

### types registry

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

### value-object manifest

value-object manifest 是 JSON 数组。当前支持 JSON-backed 值对象，并生成 checked-in Kotlin source，默认跟随 `templates.conflictPolicy`；默认配置下是 `SKIP`，适合作为作者后续维护的 source。

最小形状：

```json
[
  {
    "name": "MediaProcessingResultSnapshot",
    "aggregates": ["MediaProcessingTask"],
    "package": "com.acme.demo.domain.media.values",
    "storage": "json",
    "fields": [
      { "name": "assetUrl", "type": "String" },
      { "name": "durationSeconds", "type": "Long", "nullable": true }
    ]
  }
]
```

规则：

- `aggregates` 为空表示 shared；只写一个值表示 aggregate-owned；
- `aggregates` 最多一个值对象 owner aggregate；同名值对象只在同一 aggregate 内唯一；
- `storage` 当前只支持 `json`；
- 每个值对象必须声明至少一个 field；
- field 必须声明 `name` 和 `type`，可选 `nullable`；
- 生成的 converter 直接嵌套在 value-object class 内，不需要单独 converter FQN。

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
| `domain_service` | domain service skeleton |
| `saga` | saga param / result / handler skeleton |

常见字段包括 `package`、`name`、`description`、`aggregates`、`fields`、`resultFields`。

附加规则：

- `query` 和 `api_payload` 支持 `artifacts` 里的 `variant = "page"`；
- `domain_event` 支持 `persist`；
- `domain_event` 可以省略 package；它必须恰好声明一个 aggregate，保留 field `entity` 不允许作者显式声明，因为它会从 `aggregates[0]` 派生。缺失或空 aggregate 都属于不完整 modeling input；
- `integration_event` 支持 `artifacts[{ family: "integration-event", variant: "inbound" | "outbound" }]` 和 `eventName`，必须至少声明一个 `fields` 字段，且 `resultFields` 必须为空；`integration-subscriber` 只允许搭配 inbound integration event，outbound 只生成事件契约；
- `domain_service` 表达领域服务 skeleton，生成到 domain module；
- `saga` 表达 saga param / result / handler skeleton，生成到 application module；
- manifest-file 模式把 manifest 中的 design 文件 entry 解析为相对 `projectDir` 的路径，并拒绝空白 `manifestFile`、空 manifest、空白 entry、重复 entry，以及逃出 `projectDir` 的路径。

## unsupported design tags

当前 design JSON 暂不支持：

- `value_object`
- `validator`

这些是明确缺口，不是隐藏能力。需要值对象时，当前使用 `types.valueObjectManifest`、DB `@T` 类型绑定、必要时的 table-backed `@VO` 表或 `types.registryFile`。通用 validator 不是 cap4k core design tag；如果需要额外 validator artifact，应由 addon 贡献，不应修改 canonical model 或内置 render context。需要集成事件时，使用 `integration_event` 设计契约；MQ 绑定和外部协议适配仍由项目手写。

## enum manifest

enum manifest 是 JSON 数组，每个枚举包含：

- `name`
- `package`
- `items[]`，其中有 `value`、`name`、`desc`

它配合 DB `@T=<TypeName>` 使用。重复 type name 会被拒绝。manifest entry 不需要再写 `types.registryFile` entry。

`generateTranslation` 已从 enum manifest 移除。enum translation 属于 addon 生成方向，不属于核心 aggregate generation 开关。

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
