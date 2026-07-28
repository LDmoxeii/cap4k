# 官方默认项目合同

## Runtime 模块

cap4k 的官方 Runtime 必须由以下独立 starter 组成：Core、JPA、Request JPA、Domain Event JPA、Saga JPA、Locker JDBC、Snowflake、Integration Event HTTP、HTTP JPA、RabbitMQ、RocketMQ。每个 starter 只传递其能力所需依赖；旧 `cap4k-ddd-starter` 不存在，也没有兼容 alias。

Core starter 提供 IoC、UUIDv7、Identifier strategy registry/generator、generated-own-ID registry、同步 Request、Domain Service、本地同步 Domain Event 和事件类型目录。Core/JPA 默认组合不包含 Snowflake、可靠 Request/Event、Saga、Locker 或 transport 实现。Snowflake 只能由独立 starter 增加。

JPA starter 提供 Repository、Aggregate Factory、UnitOfWork、持久化上下文清理及 JPA 所需配置。它保持 CREATE/EXISTING、generated-own-ID completion、pending owned child reconciliation、root-only final entry、soft-delete 和 provider-assigned identity/version 的已批准行为。

Request JPA、Domain Event JPA 与 Saga JPA 分别拥有其持久化 repository、scheduler 和补偿/归档任务，并显式要求 Locker provider；安装相应 starter 而没有 Locker 时，应用启动必须失败，而不是静默关闭调度。

HTTP、RabbitMQ 与 RocketMQ starter 分别拥有 transport publisher/subscriber adapter。HTTP-JPA 只把 HTTP subscriber register 从内存实现替换为 JPA 实现。transport 需要可靠 event repository 时必须明确失败，不得回退到不可靠发送。

## Mediator 与能力装配

`Mediator` 是纯静态 namespace，只暴露 canonical 名称及 Strong ID 所需 identifiers。`DefaultMediator`、`X`、`Mediator.instance` 和 `cmd/qry/req/repo/fac/svc` 等短别名不存在。

默认 provider 必须能被业务 bean 独立替换。未安装的可选 capability 在真正调用时抛出 `CapabilityUnavailableException`，错误指出 capability；同一 capability 存在多个 provider 时，Spring 装配阶段确定性失败并列出冲突 bean。

## Request 与 Event 边界

同步 `RequestSupervisor` 只负责 validation、interceptor 和 handler 调用，不依赖持久化 repository 或调度线程池。handler 与 validation 抛出的业务异常保持原始异常。schedule/result 等可靠语义通过可选 `ReliableRequestSupervisor` 提供。

本地 Domain Event 同步进入 Spring `ApplicationEventPublisher`。需要 persist 或未来 schedule 的事件通过可选 `ReliableDomainEventProvider` 保存；未安装 provider 时在调用点失败。本地 event interceptor 与可靠 event message/publisher infrastructure 分属各自能力所有者。

事件类型不得通过 package/classpath scan 获取。transport 使用显式 `EventTypeCatalog`；生成的 `@EventListener` handler 签名或业务提供的 catalog 是合法注册来源。

## Generator 与 Bootstrap

Aggregate Factory 始终生成，不再有 factory 开关。Aggregate Specification runtime、planner 和 template 不存在。根据数据库 unique constraint 自动生成 Unique Query、Handler、Validator 的主线能力、DSL、planner、template、fixture、测试与文档承诺全部删除；physical unique metadata 与 owned-one cardinality inference 保留。本轮不创建 Unique addon。

通用 Bootstrap API、slot、managed region、conflict policy、外部/override template bundle 继续存在。生产资源中不提供官方默认项目内置 preset；测试可以使用 test-only bundle 验证通用能力。

只配置项目坐标和模块路径、没有 schema/design/manifest 输入时，`cap4kGenerateSources` 必须成功 no-op；已配置模块的 `compileKotlin` 仍依赖该任务。加入输入后继续按输入合同生成 application-side 或 database-side ID，不由模板硬编码实体 ID。

## 删除面

旧聚合 starter、旧 Mediator API、runtime enable 开关、event scan package、Reentrant annotation/aspect、Aggregate Specification 和生成主线 Unique 能力都不存在。公共文档、capability matrix、分析文档和 contributor-facing skills 不得继续把这些内容描述为可用能力。

## 验收

- Core、JPA 与每个可选 starter 的生产依赖边界可由 Gradle dependency/compile/test 证明。
- 关键旧 starter runtime tests 已迁入真实能力所有者并通过，不能通过把所有 optional 实现重新聚合进测试 production classpath 来通过。
- Generator/renderer/Gradle plugin tests 证明 Factory 默认生成、Unique/Specification 删除、Bootstrap 外部模板和无输入编译 no-op。
- 全仓 `check` 通过；已有配置 skip 必须如实报告，任何新增失败必须复现并修复。
