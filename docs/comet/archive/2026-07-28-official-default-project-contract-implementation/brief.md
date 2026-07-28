# Outcome

实现并验证 `docs/superpowers/specs/2026-07-25-cap4k-official-default-project-contract-design.md` 已确认的官方默认项目合同，使 Runtime starter、Mediator、同步/可靠请求与事件边界、Generator 默认值、无输入编译接线和 Bootstrap 责任在当前源码中真正落地，并删除已明确废弃的旧聚合 starter 与生成主线能力。

# Scope

- 将 `cap4k-ddd-starter` 拆分为 Core、JPA、Request JPA、Domain Event JPA、Saga JPA、Locker JDBC、Snowflake 和 HTTP/HTTP-JPA/RabbitMQ/RocketMQ transport starters；新 starter 只传递自身能力所需依赖。
- 将 `Mediator` 收敛为静态 namespace；删除 `DefaultMediator`、`X`、instance 与短别名；可选能力缺失时在调用点给出明确 capability unavailable 错误，多 provider 冲突在装配时失败。
- Core starter 提供 IoC、UUIDv7、Identifier registry/generator、generated-own-ID registry、同步 Request、Domain Service、本地同步 Domain Event 和显式事件类型目录，不引入 Snowflake、JPA、可靠队列或 transport。
- 将同步 Request 与可靠 Request 分离；将本地 Domain Event 与可靠持久化 Event 分离；删除事件 package scan 配置，transport 使用显式 `EventTypeCatalog`。
- JPA starter 保留 Repository/UoW、Strong ID、owned graph、soft-delete 与 database-entrusted 字段行为，并迁移旧 starter 的关键 runtime tests。
- 删除 aggregate generator 主线中的 Specification、Unique Query/Handler/Validator 与对应 DSL/template/fixture/test/docs 承诺；Factory 始终生成。
- 删除内置官方 Bootstrap preset，但保留通用 Bootstrap API、外部 template bundle 和测试专用 bundle。
- 对只配置项目布局、没有生成输入的项目，让 `cap4kGenerateSources` 成功 no-op，并保持模块 `compileKotlin` 对它的依赖。
- 更新公共文档、capability matrix、分析文档和 contributor-facing skill references，使其与实现一致。

# Non-goals

- 不创建、设计或接入 Unique addon。
- 不使用面向 cap4k 业务项目使用者的 `skills/cap4k-authoring` 指导框架贡献实现。
- 不改变 Strong ID backing/storage/JSON/JPA matrix、soft-delete sentinel/SQL contract、parent access 或 owned-child Factory payload。
- 不保留旧 `cap4k-ddd-starter` 兼容 alias、enable 开关、event scan package 或旧 Mediator API。
- 不在正式 Runtime/Generator 版本发布前把外部 GitHub Template 声称为可消费的稳定入口；本仓先完成可发布能力和模板合同。

# Acceptance examples

- 只依赖 Core starter 的应用可使用 UUIDv7、ID registry、同步 Request、Domain Service 和本地事件；依赖树中没有 JPA、Snowflake、可靠队列或 transport 实现。
- 只依赖 JPA starter 的应用获得 Repository/UoW 与已批准的 Strong ID、owned graph、soft-delete、identity/version 行为，但不会隐式安装 Request/Event/Saga/Locker/Snowflake/transport。
- 调用未安装的 reliable request、reliable event、repository、factory、UoW 或 integration-event capability 时，抛出明确且独立的 capability unavailable 错误；发现多个 provider 时应用启动失败并列出冲突 bean。
- 同步 Request handler/validation 异常保持原始异常；可靠 schedule/result 只有安装 Request JPA starter 后可用。
- 本地 Domain Event 同步进入 Spring publisher；persist/delay 事件只有安装 Domain Event JPA starter 后可用；不再通过 package scan 查找 event class。
- HTTP、RabbitMQ、RocketMQ starter 只装配各自 transport；HTTP-JPA 只替换 subscriber register 的持久化实现。
- 旧聚合 starter、Specification runtime/generator、Reentrant wrapper 和生成主线 Unique 产物不存在；physical unique metadata 与 owned-one inference 仍保留。
- 没有 schema/design 输入的已配置多模块项目执行模块编译时，`cap4kGenerateSources` 在任务图中并成功 no-op。
- 通用 Bootstrap API 可继续用外部/override template bundle；生产资源中不存在内置官方默认项目 skeleton。

# Constraints and invariants

- `ddd-core` 不得编译依赖 JPA/Hibernate/Spring Data；兼容 Spring Data aggregate root 只能使用无类型依赖的边界处理。
- Runtime provider 拆分必须保持默认实现可独立替换；starter 不得用“全家桶测试宿主”掩盖生产 classpath 边界。
- Application-side ID 只通过 generated typed accessor/catalog/registry 分配；不得恢复 annotation scan、Strong ID companion 反射或 `snowflake-long` alias。
- Snowflake 必须保持可选 starter，并且 Core/JPA 的生产依赖树中没有 Snowflake 实现。
- 删除 Unique 生成能力不删除 canonical/schema 中的 physical unique constraint metadata。
- 修改发生在当前非保护分支；不提交、不推送、不创建 PR，除非用户另行要求。

# Decisions

- 用户已确认按更新后的权威 spec 开始实现，并要求在没有新产品决策时持续完成，不暂停。
- 本轮 Unique 范围只删除 aggregate generator 主线功能，不处理 addon。
- Runtime starter 采用能力所有权拆分，不提供旧聚合 starter 兼容入口。
- GitHub Template 外部发布以正式可消费版本为前置；本仓实现与验证不能伪造尚未发布的远程模板完成状态。

# Open questions

- 无。

# Verification expectations

- 对 `ddd-core`、全部新 starter、底层 JPA/event/transport 模块运行编译和测试。
- 运行 aggregate generator、renderer、Gradle plugin 的单元/功能测试，覆盖 Factory 默认生成、Unique/Specification 删除、无输入 no-op 编译接线和 Bootstrap 外部模板边界。
- 运行全仓 `check`，并记录已配置 skip 与新增失败的区别。
- 静态扫描旧 starter、旧 Mediator API、event scan package、旧 enable 开关、Specification/Unique/Reentrant 运行时符号和 stale public docs。
- 运行 `git diff --check` 与 Native text check；所有声称通过的验证必须来自实际命令结果。
