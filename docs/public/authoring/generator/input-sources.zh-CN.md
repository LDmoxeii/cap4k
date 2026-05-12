# 生成输入源

本页说明业务项目作者如何准备 cap4k 生成器输入。输入源是生成器合同，不是随手写给工具看的备注。

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
| `@Parent` / `@P=<table>` | 当前表属于父聚合 |
| `@AggregateRoot` / `@Root` / `@R=true\|false` | 显式聚合根标记 |
| `@ValueObject` / `@VO` | table-backed 值对象表标记，不是所有 Value Object 的默认写法 |
| `@Ignore` / `@I` | 忽略该表 |
| `@DynamicInsert=true\|false` | provider dynamic insert |
| `@DynamicUpdate=true\|false` | provider dynamic update |

聚合边界应由业务模型决定，再用表注释表达。不要为了凑生成结果而让数据库结构反向决定领域边界。

## DB column annotations

列注释可使用这些注解：

| 注解 | 含义 |
| --- | --- |
| `@Type` / `@T=<TypeName>` | 绑定到命名领域类型或枚举 |
| `@Enum` / `@E=0:NAME:Desc\|...` | 内联枚举项；需要同时声明 `@T` |
| `@GeneratedValue` | 使用默认 ID 生成 |
| `@GeneratedValue=uuid7` | UUID7 策略 |
| `@GeneratedValue=snowflake-long` | Snowflake long 策略 |
| `@GeneratedValue=identity` | 数据库 identity 策略 |
| `@Deleted` | 软删除字段 |
| `@Version` | 乐观锁字段 |
| `@Managed` | 框架管理字段 |
| `@Exposed` | 对外暴露字段 |
| `@Insertable=true\|false` | JPA insertability |
| `@Updatable=true\|false` | JPA updatability |

规则：

- `@Enum` 需要配合 `@Type` / `@T`。
- `@Managed` 与 `@Exposed` 互斥。
- 旧的 `@IdGenerator` 和 `@SoftDeleteColumn` 已被拒绝，不应继续使用。

自定义值类型字段规则：

- 对 JSON-backed 或 inline 值对象，优先在列上使用 `@T=<TypeName>`，并在 `types.registryFile` 注册 FQN 与 converter。
- 生成器只会把字段类型和 JPA converter 映射到聚合；值对象类、构造 / 校验 / 归一化、converter 仍由作者维护。
- `@VO` 表只表示 separate-table / table-backed 值对象，适用面更重；不要为了“使用值对象”而默认建独立表。

## DB relation annotations

关系注释可使用这些注解：

| 注解 | 含义 |
| --- | --- |
| `@Reference` / `@Ref=<table>` | 引用目标表 |
| `@Relation` / `@Rel=ManyToOne\|OneToOne\|*:1\|1:1` | 关系类型 |
| `@Lazy` / `@L=true\|false` | 懒加载 |
| `@Count` / `@C=<hint>` | count hint |

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
| `query` | query request / response |
| `client` | external client/cli request |
| `api_payload` | adapter API payload |
| `domain_event` | domain event payload |
| `validator` | validation annotation and validator |

常见字段包括 `package`、`name`、`desc`、`aggregates`、`requestFields`、`responseFields`。

附加规则：

- `query` 和 `api_payload` 支持 request trait `page`；
- `domain_event` 支持 `persist`；
- `domain_event` 可以省略 package，并可使用保留 request field `entity`；
- `validator` 支持 `message`、`targets`、`valueType`、`parameters`；
- manifest-file 模式读取相对 manifest 的 design 文件列表，并拒绝路径逃逸和重复项。

## unsupported design tags

当前 design JSON 暂不支持：

- `integration_event`
- `value_object`
- `domain_service`

这些是明确缺口，不是隐藏能力。需要集成事件、值对象或领域服务时，当前应通过 DB `@T` 类型绑定、必要时的 table-backed `@VO` 表、手写模型、后续 addon 或未来生成能力处理，并在项目审计中记录。

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
