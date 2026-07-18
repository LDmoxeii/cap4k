# DB Schema Annotations

DB/schema comment 是 persistence surface 和聚合结构的输入事实。generator 会从 DDL 或数据库 metadata 中读取这些注解。

这些注解只描述 schema 中已经存在的事实，例如 table hierarchy、Aggregate Root 标记、Value Object shape、field type marker、relation metadata 和 persistence marker。它们不替代 Aggregate 行为，也不会因为存在外键或引用就允许跨聚合直接写入。

## Table Comment 注解

| 注解 | 说明 |
| --- | --- |
| `@Parent=<table>` / `@P=<table>` | 标记该 table 是另一个 table 的 child。 |
| `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>` | 显式标记 Aggregate Root 状态。 |
| `@ValueObject` / `@VO` | 标记从 table 推导出的 Value Object shape。 |
| `@Ignore` / `@I` | 从 generation 中排除该 table。 |
| `@DynamicInsert=<bool>` | 声明 dynamic insert metadata。 |
| `@DynamicUpdate=<bool>` | 声明 dynamic update metadata。 |

## Column Comment 注解

| 注解 | 说明 |
| --- | --- |
| `@T=<TypeName>` / `@TYPE=<TypeName>` | 覆盖或绑定生成字段的类型。 |
| `@E=<items>` / `@ENUM=<items>` | 声明 enum items；必须同时提供 `@T` / `@TYPE`。 |
| `@RefId=<TypeName>` | 标记所在上下文中的外部引用身份类型。 |
| `@Deleted` | 标记 soft-delete column。 |
| `@Version` | 标记 optimistic-lock version column。 |
| `@GeneratedValue=identity` / `@GeneratedValue=database-identity` | 标记明确的 database identity 语义。 |
| `@Managed` | 标记 framework-managed field metadata。 |
| `@Inherited` | 标记 inherited field metadata。 |
| `@Reference=<table>` / `@Ref=<table>` | 为 relation metadata 指定被引用的 table。 |
| `@Relation=<type>` / `@Rel=<type>` | 指定 relation type。 |
| `@Lazy=<bool>` / `@L=<bool>` | 标记 lazy relation metadata。 |
| `@Count=<value>` / `@C=<value>` | 标记 relation count metadata。 |
| `@RefAggregate=<AggregateName>` | 按 Aggregate name 引用另一个 Aggregate。 |

## 冲突与依赖规则

- `@Parent` / `@P` 不能和 aggregate-root true 同时使用。
- presence annotation 不接收显式值。
- boolean value 使用小写 `true` 或 `false`。
- `@E` / `@ENUM` 依赖 `@T` / `@TYPE`。
- `@Relation` / `@Rel`、`@Lazy` / `@L`、`@Count` / `@C` 都依赖 `@Reference` / `@Ref`。
- `@RefAggregate` 不能和 `@Reference` / `@Ref` 同时使用。
- `@RefAggregate` 不能和 `@RefId` 同时使用。

## 注解边界

- database identity 使用 column `@GeneratedValue=identity` 或 `@GeneratedValue=database-identity`；不要使用 table `@IdGenerator` 或 `@IG`。
- soft delete 使用 column `@Deleted`；不要使用 table `@SoftDeleteColumn`。
- column 不使用 `@Exposed`、`@Insertable` 或 `@Updatable`。
