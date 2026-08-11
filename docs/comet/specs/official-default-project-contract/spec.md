# 官方默认项目合同

## Runtime 模块

cap4k 的官方 Runtime 必须由以下独立 starter 组成：Core、JPA、Command JPA、Domain Event JPA、Integration Event HTTP、RabbitMQ、RocketMQ。每个 starter 只传递其能力所需依赖；旧聚合 starter、Request JPA、Saga JPA、Locker JDBC 与 HTTP JPA 不存在，也没有兼容 alias。

Core starter 提供 IoC、UUIDv7、Identifier strategy registry/generator、generated-own-ID registry、独立 Command/Query/Capability 同步分发、Domain Service、本地同步 Domain Event 和事件类型目录。UUIDv7 是唯一内置的 application-side Strong ID 分配策略；Core/JPA 默认组合不包含可靠 Command/Event 或 transport 实现。数据库分配的 identity 仍是 persistence policy，不是 application-side generator。

JPA starter 提供 Repository、Aggregate Factory、自动完成的 REQUIRED Command UnitOfWork、候选变化识别、审计 enrich、最终变化识别、持久化同步和 JPA 所需配置。它保持 CREATE/EXISTING、generated-own-ID completion、pending owned child reconciliation、root-only final entry、soft-delete 和 provider-assigned identity/version 的已批准行为。应用代码不需要调用 completion-oriented `save()`；高级 `flush()` 只同步数据库，不提交事务或释放 Domain Event。

Command JPA 与 Domain Event JPA 分别拥有可靠记录、内部 execution substrate、claim/lease/token、retry-policy snapshot、redrive 和 retention。它们不依赖公开 Locker provider，状态转换和 fenced acknowledgement 始终由 Runtime 自身拥有。可靠 Command 必须在当前 Command 事务内登记，并且只能在提交成功后唤醒 worker。cap4k 不提供内置 Saga runtime、persistence、starter 或 generator。

HTTP、RabbitMQ 与 RocketMQ starter 分别拥有 transport publisher/subscriber adapter。HTTP 只使用显式静态 route 与固定接收端点，不提供动态 subscriber registry、管理端点或 JPA subscription carrier。transport 需要可靠 event repository 时必须明确失败，不得回退到不可靠发送。

## Mediator 与能力装配

`Mediator` 是纯静态 namespace，只暴露 canonical 名称及 Strong ID 所需 identifiers。`DefaultMediator`、`X`、`Mediator.instance` 和 `cmd/qry/req/repo/fac/svc` 等短别名不存在。

默认 provider 必须能被业务 bean 独立替换。未安装的可选 provider 在真正调用时抛出 `ProviderUnavailableException`，错误指出 provider；同一 provider 存在多个实现时，Spring 装配阶段确定性失败并列出冲突 bean。

## Application 与 Event 边界

Command、Query 与 Capability 是独立 public contract，不存在 generic Request marker、Handler、Supervisor 或 `Mediator.requests`。Command 独占 REQUIRED transaction 和自动 UoW completion；嵌套 Command 加入当前物理事务和 UoW。Query 不创建 write UoW。Capability 不创建、挂起或提交本地事务。enqueue/schedule/result 等可靠语义只属于 Command，并通过可选 `ReliableCommandSupervisor` 提供。

本地 Domain Event 与入站 Integration Event 统一通过方法级 Spring `@EventListener` Handler 同步、串行、fail-fast 分发。不同方法级 `@Order` 值按数值从小到大执行；相同值不承诺次序。Handler 必须返回 `Unit/void`；`@Async`、`suspend`、`@TransactionalEventListener`、`defaultExecution=false`、多事件声明和多态订阅在启动发现阶段确定性失败。Handler 可以同步发送 Command、查询 Query、调用 Capability，也可以并行启动 `askAsync()` / `callAsync()`；Runtime 在方法返回后等待当前 Handler scope 登记的所有此类任务，任一失败都会使 Handler 失败。需要在当前调用栈之外改变状态的工作使用可靠 Command 的 `enqueue`、`schedule` 或 `delay`。

UoW 以非重入因果 frontier 释放 Domain Event：当前 frontier 执行期间产生的事件进入下一 frontier。需要 persist 或未来 schedule 的事件通过可选 `ReliableDomainEventProvider` 保存；未安装 provider 时在调用点失败。

事件类型不得通过 package/classpath scan 获取。transport 使用显式 `EventTypeCatalog`；生成的 `@EventListener` handler 签名或业务提供的 catalog 是合法注册来源。

## Generator 与 Bootstrap

Aggregate Factory 始终生成，不再有 factory 开关。Aggregate Specification runtime、planner 和 template 不存在。根据数据库 unique constraint 自动生成 Unique Query、Handler、Validator 的主线能力、DSL、planner、template、fixture、测试与文档承诺全部删除；physical unique metadata 与 owned-one cardinality inference 保留。本轮不创建 Unique addon。

通用 Bootstrap API、slot、managed region、conflict policy、外部/override template bundle 继续存在。生产资源中不提供官方默认项目内置 preset；测试可以使用 test-only bundle 验证通用能力。

只配置项目坐标和模块路径、没有 schema/design/manifest 输入时，`cap4kGenerateSources` 必须成功 no-op；已配置模块的 `compileKotlin` 仍依赖该任务。加入输入后继续按输入合同生成 application-side 或 database-side ID，不由模板硬编码实体 ID。

## 删除面

旧聚合 starter、generic Request API、Request JPA、Saga runtime/starter/generator、Locker API/runtime/starter/schema、旧 Mediator API、runtime enable 开关、event scan package、Reentrant annotation/aspect、Aggregate Specification 和生成主线 Unique 能力都不存在。公共文档、capability matrix、分析文档和 contributor-facing skills 不得继续把这些内容描述为可用能力。

## 验收

- Core、JPA 与每个可选 starter 的生产依赖边界可由 Gradle dependency/compile/test 证明。
- 关键旧 starter runtime tests 已迁入真实能力所有者并通过，不能通过把所有 optional 实现重新聚合进测试 production classpath 来通过。
- Generator/renderer/Gradle plugin tests 证明 Factory 默认生成、Unique/Specification 删除、Bootstrap 外部模板和无输入编译 no-op。
- 全仓 `check` 通过；已有配置 skip 必须如实报告，任何新增失败必须复现并修复。
