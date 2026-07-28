# Outcome

复核并修订 `docs/superpowers/specs/2026-07-25-cap4k-official-default-project-contract-design.md`，使它在当前 `master` 源码基线上完整吸收 PR #136、#138、#140 已发布且会被 Runtime starter 拆分或 Generator 默认值重构触及的合同。修订后的文档应继续作为后续 Runtime、Generator、GitHub Template 三阶段工作的唯一权威 spec，新会话无需回读三份历史 spec 才能避免行为回归。

# Scope

- 将源码基线更新到当前 `master@e8cc1247`，说明 PR #141 只迁移 Comet/workspace 治理，不改变默认项目产品合同。
- 补全 PR #136 的 generated-own-ID 生命周期：Factory 和直接 `persist(root, CREATE)` 的返回时可用性、generated owned relation `add/replace` 的 mutation 前分配、`EXISTING` 根中新 child 的补齐、最终 UoW backstop、pending child 到最外层 root 的吸收及歧义失败。
- 修正“owned child 永不成为独立 entry”的绝对表述：只有能由 pending root 证明归属的 child 才被吸收；孤立的 caller-declared `persist(child, CREATE)` 在没有静态 root/child marker 的现有合同下仍被视为 caller-declared top-level entry。
- 补全 PR #140 的字段角色分类：generated own ID、database identity/version、soft-delete system-transition field、structural parentRef、普通 managed `READ_ONLY` field 各自不同的 constructor、payload、entity property、Schema 和 runtime 生命周期。
- 补全 parentRef/owned relation 正向合同：物理 parentRef 证据保留，默认 domain/Schema scalar 与 automatic inverse navigation 删除，forward owned relation/Schema join 保留，shared-primary-key owned child 拒绝，owned-one 由 unique parentRef 加独立 child ID 证明。
- 将当前 aggregate generator 主线中的 Unique 生成能力完整删除：删除配置开关、planner、renderer template、fixture、测试和文档承诺；本轮不创建 addon、不设计 `cap4kAddon`/ServiceLoader 集成。
- 将 PR #138 的回归证据写实：保留五种 ID/storage 组合、soft-delete initializer/constructor 排除、精确方言和 identifier quoting、H2 生命周期及真实 PostgreSQL native UUID 证据；同时保持默认 Core/JPA classpath 不含 Snowflake 实现。
- 调整 Runtime/Generator 阶段门槛、验证矩阵、源码事实索引和文档自检标准，使上述合同可被实施计划直接转成任务和验收。

# Non-goals

- 本次 Shape 不修改 Runtime、Generator、starter、fixture 或 GitHub Template 实现。
- 不重新设计 Strong ID 策略/存储矩阵、soft-delete sentinel/SQL、database-entrusted field 语义、parent access、owned-child Factory payload 或 managed-field lifecycle SPI。
- 不创建或设计 Unique addon；未来若恢复 Unique 生成能力，作为独立 change 重新确认 parent-scoped/global 语义。
- 不把面向 cap4k 使用者的 `skills/cap4k-authoring` 规则用于框架贡献者工作。
- 不重新评审目标 spec 中与 PR #136/#138/#140 无交叉的既有产品决策，除非源码事实证明它们已失效。
- 不进入 Runtime 实施计划；本轮先完成权威 spec 的书面复核和修订。

# Acceptance examples

- 给定应用侧 UUIDv7 root 和 owned children，经 Factory 或直接 `persist(root, CREATE)` 登记后，root 与当前可达 child ID 在调用返回前可读；预赋值 ID 不被覆盖；Factory 不 flush。
- 给定已加载 aggregate，generated owned relation 执行 `add(child)` 或 `replace(child)` 时，child ID 在 mutation 返回前可读；分配失败时原 relation 不变；`replace(null)` 不分配 ID；UoW save 仍作为最终 idempotent backstop。
- 给定 pending root 与 separately pending reachable child，save 前 child entry 被吸收到最外层 root，child 不独立调用 JPA persist；同一 child 属于两个无关 root 时确定性失败。给定没有任何 pending owner 的孤立 `persist(child, CREATE)`，不新增静态 marker 来猜测它是 child。
- 给定 database identity/version entity，Factory payload/constructor 不包含这些字段，Entity property 为 nullable/null，Schema 保留物理 DB nullability，成功 save/flush 后 root 和 owned child 的值可观察；普通 managed `READ_ONLY` field 不自动获得同样语义。
- 给定 structural parentRef，DB/canonical relation binding 和 cardinality 证据保留，但 Entity/Schema scalar、Factory 输入和 automatic inverse navigation 不出现；shared-PK child 被拒绝；unique parentRef 加独立 child ID 可推导 owned-one；forward join 仍生成。
- 给定数据库唯一约束（包括包含 parentRef 的约束），Generator 不再自动生成 Unique Query/Handler/Validator；物理 unique metadata 和数据库约束继续保留，并仍可用于 owned-one cardinality inference。
- 给定 soft-delete 的 identity、Snowflake Long/String、UUID7 String、native UUID 组合，Factory/Entity refactor 不恢复 deleted constructor/payload，不改变 sentinel 或 SQL；H2 与真实 PostgreSQL 证据继续覆盖当前已支持路径。
- 给定只有 Core/JPA starter 的默认项目，UUIDv7/registry/UoW 完整可用且生产依赖树没有 Snowflake；加入 Snowflake starter 只增加 Snowflake strategy。

# Constraints and invariants

- 目标 superpowers spec 仍是唯一权威文档；历史 PR specs 只作为本次核查证据，修订后不得成为实施前置阅读。
- 当前生产源码和合并后的回归测试优先于历史会话记忆。
- `ddd-core` 保持无 JPA/Hibernate/Spring Data 类型；generated accessor/catalog/registry 契约保持持久化中立。
- ID 分配只走 generated typed accessor/catalog 与 registry；不得恢复 `@ApplicationSideId`、`JpaApplicationSideIdSupport`、Strong ID companion 反射或 `snowflake-long` alias。
- provider-assigned identity/version 依赖现有 JPA persist/cascade/flush/必要 refresh 语义；不得借“观察”措辞新增通用 managed-field lifecycle SPI。
- parentRef 的物理关系证据与 domain/API 投影必须分开描述，不能因删除 scalar 而删除 relation binding、cardinality 或 forward join 所需信息。
- 删除 Unique 生成产物不等于删除数据库 unique constraint metadata；后者仍服务存储完整性和 relation cardinality inference。
- Snowflake 是可选 starter；soft-delete Snowflake 回归可以是组合测试，但不得制造 Core/JPA 生产依赖边。
- 本次实际目标文件编辑必须发生在当前非保护分支，并保留用户已有提交和改动。

# Decisions

- 当前 spec 的二次修订方向正确，不应回滚现有 112 行补充；需要在其基础上补齐遗漏和修正过度绝对化表述。
- 采用“最小但完整的合同补强”：不复制三份历史 spec 的全部实现细节，只提升会被本轮 Runtime/Generator 工作触及、且足以阻止错误实现的产品行为、边界和验收证据。
- application-side ID 的准确表述改为“凡模型选择 application-side own ID 的生成实体均使用专属 Strong ID；官方默认 strategy 为 UUIDv7”，避免误读为 database identity 也必须包装成 Strong ID。
- 本次 Generator 阶段只删除主线 Unique 生成功能，不建设替代 addon；未来 addon 不属于本文产品答案。
- provider-assigned 字段分类仅限 resolved database identity 与 resolved version；`READ_ONLY` 本身不构成该分类。
- Strong ID 生命周期必须包含 relation mutation 和 pending child reconciliation，而不只包含 Factory CREATE。
- 用户于 2026-07-28 确认按更新后的边界修订目标 spec：补强 PR #136/#138/#140 的交叉合同，只删除生成主线 Unique 功能，不创建或设计 Unique addon。

# Open questions

- 无。

# Verification expectations

- 对目标 spec 执行 `git diff --check`。
- 静态检查目标 spec 不含未决 `TBD`/无意 `TODO`，且更新后的基线、阶段门槛、验证矩阵和自检标准彼此一致。
- 使用 `rg` 交叉核对 generated own-ID lifecycle、OwnedEntityList hook、UoW reconciliation、soft-delete matrix、entrusted-field classification、parentRef/forward relation，以及 Unique 生成主线的配置/planner/template/fixture/test 调用面。
- 检查所有新增或调整的仓库内 Markdown 链接可解析。
- 本轮若只改文档，不声称 Gradle 测试已运行；如修订引入对代码行为的新判断，则仅以现有源码/已合并测试证据支撑，不伪造运行结果。
