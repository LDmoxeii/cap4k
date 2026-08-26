# Outcome

让 cap4k 默认聚合实体生成器保留数据库列注释，并将其稳定生成到 Kotlin 实体字段的 KDoc 中，使 `schema.sql` 或 JDBC REMARKS 中已有的字段说明在生成源码中可直接阅读。

# Scope

- 将 `DbColumnSnapshot.comment` 贯穿到聚合实体字段的 canonical/render model。
- 为实体字段渲染上下文提供安全的 KDoc/comment 文本和 Kotlin 字符串字面量。
- 修改默认 aggregate entity 模板，在 scalar field、Strong ID/embedded field 和可表达列注释的 relation field 前输出字段级 KDoc。
- 保留现有表级/聚合元素描述注释。
- 增加 source、assembler、planner、renderer 和 Gradle fixture 回归测试。
- 记录 reference-payment 真实 schema 作为下游 follow-up 验证样例，但不把 reference-payment 代码复制进 cap4k 测试；下游重生成不作为本 change 的完成门槛。

# Non-goals

- 不改变数据库映射、JPA 注解、字段命名、类型解析、持久化策略或关系语义。
- 不改变值对象、命令、查询、Endpoint 或事件字段注释策略。
- 不从字段名猜测缺失描述；空 comment 不生成空 KDoc。
- 不覆盖或重写下游项目的手写实体逻辑。
- 不引入运行时反射、额外数据库查询或新的注释存储格式。

# Acceptance examples

- 给定包含列注释的 DB snapshot，canonical aggregate entity field 保留该 comment；没有 comment 的列保持无字段 KDoc。
- 默认生成的实体源码在字段声明前输出对应 KDoc，例如 `merchant_id` 的 comment `商户标识` 生成 `/** 商户标识 */`，并且原有 `@Column`、类型、可空性和 setter 行为不变。
- Strong ID、embedded ID、普通 scalar field 以及有明确列来源的 relation field 不会因为增加 KDoc 而改变生成结构。
- 表级 `@AggregateElementMetadata(description = ...)` 和字段级 KDoc 同时保留。
- 空注释、换行、KDoc 特殊字符和 Kotlin 字符串特殊字符经过转义后仍能生成可编译 Kotlin 源码。
- 默认 generator、renderer 与 Gradle functional fixture 回归测试覆盖上述行为。

# Constraints and invariants

- 从最新 `origin/master` 的 `master` 创建独立 worktree；实现分支为 `comet/entity-field-kdoc-generation`，目标分支为 `master`。
- 这是生成器能力修复，必须保持 canonical model 为唯一字段描述来源，不在模板中访问数据库或读取外部文件。
- 生成器默认的 `SKIP` 行为和 checked-in source ownership 不变。
- 生成输出必须保持现有 import、Kotlin formatting、JPA annotation 和 source compatibility 约束。
- cap4k 的 Runtime、Generator、Analyzer capability contract 只在实际受影响时更新，并同步验证相关事实投影。

# Decisions

- 使用现有 `DbColumnSnapshot.comment` 作为实体字段描述的唯一 DB 来源。
- 优先扩展现有 `FieldModel`/field render context，而不是另建第二套字段注释目录。
- KDoc 输出位置放在生成实体字段属性/关系属性之前，而不是只放在构造函数参数之前，以确保 Kotlin API 文档能够绑定到实际属性。
- 不对缺少 schema comment 的字段进行自动命名推断。
- reference-payment 只作为 cap4k 完结后的下游 follow-up 验证样例；cap4k 本 change 不依赖其源码路径，也不以该验证作为完成门槛。

# Open questions

None.

# Verification expectations

Run at minimum:

- `:cap4k-plugin-pipeline-core:test`
- `:cap4k-plugin-pipeline-generator-aggregate:test`
- `:cap4k-plugin-pipeline-renderer-pebble:test`
- focused DB-source / assembler / planner / template tests covering column comment propagation
- relevant `:cap4k-plugin-pipeline-gradle:test` functional fixture coverage
- full `check` or `build` when the affected module graph is stable
- downstream reference-payment regeneration and full build after the cap4k change is accepted
