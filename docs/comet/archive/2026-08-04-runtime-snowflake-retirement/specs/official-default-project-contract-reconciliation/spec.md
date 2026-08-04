# 官方默认项目契约合并后复核

## 目标

`docs/superpowers/specs/2026-07-25-cap4k-official-default-project-contract-design.md` 必须在当前 master 源码基线上，完整、独立地约束官方默认项目以及为实现它而进行的 Runtime、Generator、GitHub Template 三阶段工作。PR #136、#138、#140 的历史设计文档不得成为后续实施的前置阅读，但其中会被 starter 拆分、Factory 默认值重构、Unique 主线删除或 Entity/Schema planner 变更触及的已发布合同必须在目标 spec 中明确重述。

## 基线与文档权威

- 修订基线使用当前 `master@e8cc1247`。
- PR #136、#138、#140 的合并结果属于产品源码基线。
- PR #141 只改变 Comet/workspace 治理，不改变官方默认项目的 Runtime/Generator 产品合同。
- 目标 superpowers spec 是后续工作的唯一权威产品 spec；历史 spec、PR 正文和当前实现只用于本轮核查及未来事实索引。

## Application-side ID 合同

- 凡模型选择 application-side own ID 的生成实体，ID 都是该实体专属的 Strong ID；database identity 不因此被强制包装为 Strong ID。
- `uuid7` 是唯一内置 application-side Strong ID 分配策略；database identity 仍是 persistence policy，不是 application-side generator。
- 生成器继续产生每个 eligible entity 的 typed `GeneratedOwnIdAccessor` 和每模块 catalog；Runtime 通过 `GeneratedOwnIdRegistry` 确定性发现它们。
- 不得恢复 `@ApplicationSideId`、`JpaApplicationSideIdSupport`、annotation scan、Strong ID companion 反射、generic runtime converter 或任何已退役策略的 compatibility alias。

## ID 生命周期

- Aggregate Factory 只构造模型当前支持的根图，不直接调用 ID generator、不 flush、不访问 `EntityManager`。
- Factory provider 登记 `persist(root, CREATE)`；Factory 和直接 `persist(root, CREATE)` 都必须在返回前完成 root 与当前可达 generated-own-ID child 的缺失 ID。
- generated owned relation 的 `add(child)` 与非空 `replace(child)` 在修改 relation 前直接调用 child typed accessor；失败时 relation 不变，`replace(null)` 不分配，预赋值 ID 保留。
- 对 `EXISTING` root，UoW 保留 repository observation/managed evidence，保护既有 root/child identity，只为新可达且缺失 ID 的 owned child 分配，并在 save 前再次 idempotent completion。
- 若 separately pending child 可由 pending root 的 forward owned graph 证明归属，save 前必须吸收到最外层 root entry；child 不独立进入 interceptor/listener/JPA persist 集合。
- 同一 child 可达于两个无关 pending root 时确定性失败；直接 remove reachable child 仍失败。
- 没有 pending owner 的孤立 caller-declared `persist(child, CREATE)` 继续按 caller-declared top-level entry 处理；本轮不新增静态 root/child marker 或公共 child-persist API 来改变该边界。

## 字段角色分类

目标 spec 必须明确区分下列角色，不能用统一的“框架管理字段”规则代替：

| 角色 | Factory payload / constructor | Entity property | Schema / 物理模型 | Runtime 生命周期 |
|---|---|---|---|---|
| application-side generated own ID | 排除 own ID 输入 | Strong ID，按现有 generated-own-ID shape | 按 JDBC storage-nearest backing | typed accessor 在 lifecycle entry 分配，UoW backstop |
| resolved database identity | 排除 | nullable，初始 `null` | 保留 DB 物理 nullability/type | JPA persist/flush 后赋值并可观察 |
| resolved version | 排除 | nullable，初始 `null` | 保留 DB 物理 nullability/type | JPA provider 在 save/flush 生命周期赋值；支持已批准 integral 类型 |
| soft-delete `SYSTEM_TRANSITION_ONLY` | 排除 | mapped property 带已批准 active initializer | 保留物理 deleted column | Hibernate soft-delete transition 使用已批准 sentinel/SQL |
| structural parentRef | 不作为 domain/Factory scalar | 不生成默认 scalar 或 automatic inverse navigation | 保留 DB/关系绑定/cardinality 证据 | 由 parent-side forward owned relation 和 cascade 使用 |
| ordinary managed `READ_ONLY` | 维持当前 constructor 规则；无法推导时仍是 blocker | 不自动变成 nullable/null | 维持当前物理模型 | 不获得 identity/version lifecycle；等待独立设计 |

- provider-assigned 分类只由 resolved database-side own-ID role 和 resolved version policy确定；`READ_ONLY` 本身不构成分类。
- Entity Kotlin nullability 不改变 Schema/DB 的物理 nullability。
- Runtime 依赖现有 JPA persist/cascade/flush 与必要的 root refresh 使 provider-assigned 值可观察；不新增通用 managed-field lifecycle SPI。
- provider 已赋值后事务回滚，cap4k 不清空内存值。

## ParentRef 与 owned relation

- parentRef 物理列仍是 direct owned-parent binding 和 cardinality inference 的必需证据，即使数据库没有物理 FK constraint。
- parentRef 不投影为默认 Entity scalar、Schema scalar、Factory payload 或自动 child-to-parent inverse navigation。
- parent-side forward owned relation、Schema join、nested forward join 与 cascade 保留。
- parentRef 等于 child primary key 的 shared-primary-key owned child 必须拒绝。
- owned-one 由 unique parentRef 加独立 child primary key 证明；其他已支持 parentRef 形态按现有 owned-many 规则处理。
- 新增 parent ID/entity 访问模式、owned-child Factory payload 或 public child Factory 仍是非目标。

## Unique 生成主线删除

- 从 aggregate generator 主线完整删除基于数据库唯一约束生成 Query、Handler 和 Validator 的能力。
- 删除 `artifacts.unique` 配置表面、相关 planner、renderer template、fixture、测试以及 public docs/skill/capability map 中的生成承诺。
- 本轮不创建 Unique addon artifact，不引入 `cap4kAddon` 选择面，不增加 ServiceLoader 集成，也不定义未来 addon 的 parent-scoped/global 行为。
- 删除生成能力不删除 DB schema/canonical model 中的 physical unique constraint metadata；数据库约束继续承担最终存储完整性。
- owned relation cardinality inference 继续使用已经批准的 unique parentRef 等物理证据；不能为了删除 Unique 产物而删除或弱化该 metadata。
- 若未来重新提供 Unique 生成能力，必须作为独立 change 重新定义输入、scope、异常传播、parentRef 约束和产物所有权。

## Soft-delete 回归

- 保留 identity/integral ZERO、UUID7 String/character NIL_UUID、UUID7 native UUID/NIL_UUID 三种已支持组合。
- `deleted` 保持 mapped property 和已批准 initializer，但不进入 entity constructor、Factory payload 或 owned-child spec。
- storage classification 仍由 shared catalog 负责；core policy 不生成 SQL；aggregate generator 负责已批准 dialect、identifier quoting 和 literal rendering；未知 dialect 不回退。
- Factory/Entity 默认值重构不得改变 self-ID tombstone、sentinel、Hibernate `SQLDelete`/`Where` 或 create/query/delete 行为。
- 验证保留 H2 standard、H2 MySQL mode、mixed-case UUID String 以及真实 PostgreSQL native UUID metadata-to-executed-SQL 路径。
- Soft-delete 回归只覆盖 identity 与 UUID7 的已支持 storage 组合；已退役的 application-side identifier strategy 不进入组合 fixture 或生产依赖树。

## 阶段验收

### Runtime 阶段

- Core starter 提供 UUIDv7、Identifier registry/generator 与 generated-own-ID registry；UUIDv7 是唯一内置 application-side 分配策略。
- JPA starter 保持 CREATE、EXISTING、新 owned child、pending child reconciliation、root-only final entry 和 database-entrusted save/flush 行为。
- Strong ID、soft-delete、entrusted owned graph 的旧聚合 starter runtime tests 被迁移到能表达真实能力组合的新所有者；迁移不能通过把所有实现重新聚合到测试宿主的生产 classpath 来伪造边界。
- 删除旧 starter 不得恢复任何 PR #136 已删除的 compatibility path。

### Generator 阶段

- Factory 必生成，但只在普通业务字段或尚未设计的 managed field 真正无法推导时进入明确 TODO 路径。
- field-role taxonomy 在 Entity、Factory、Schema、Relation 和 Projection 的 planner/renderer tests 中保持一致。
- generated owned relation 的 typed accessor hook、forward joins、parentRef physical evidence、entrusted field shape 与 soft-delete initializer 都有正向回归。
- Unique 配置、planner、template、fixture、测试和文档生成承诺已删除；physical unique metadata 与 owned-one 推导回归仍通过。
- H2 与真实 PostgreSQL soft-delete 证据在相关 refactor 后仍通过。

### GitHub Template 阶段

- 默认项目继续只选择 Core/JPA 的 UUIDv7 application-side ID 基线；database identity 按 persistence policy 输入生成。
- 空项目无生成输入时 no-op；加入 schema 后，application-side 与 database-side ID 依据输入合同生成，而不是由 Template 硬编码实体 ID 类型。

## 非目标

- 不修改 Strong ID backing/storage/JSON/JPA matrix。
- 不修改 soft-delete sentinel、dialect 或 SQL contract。
- 不新增 parent access、owned-child Factory payload、shared-PK support 或 generic managed-field lifecycle。
- 不创建或设计 Unique addon。
- 不改变 UUID7、identity/version、owned relation 的既有业务语义，只约束模块迁移和 Generator refactor 不得回归。
