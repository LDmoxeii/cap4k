# 命名与目录规范

## 何时阅读本页

- 当你已经接受 Default Happy Path，需要把规则落成目录、文件名和角色归位时，读本页。
- 如果还没有先确认默认规则，请先回到 [Default Happy Path](default-happy-path.zh-CN.md)。

本页给 Default Happy Path 提供可执行的落地约束。以下示例继续使用“内容发布与处理示例项目”，因此目录和命名会围绕 `Content`、`MediaProcessingTask`、`MediaProcessingCli` 展开。

## 推荐目录结构

- `domain/aggregates/content/...`
- `domain/aggregates/media_processing_task/...`
- `application/commands/content/...`
- `application/commands/media_processing_task/...`
- `application/queries/content/...`
- `application/queries/media_processing_task/...`
- `application/subscribers/domain/...`
- `application/subscribers/integration/...`
- `adapter/domain/repositories/...`
- `adapter/application/queries/...`
- `adapter/application/distributed/clients/...`
- `adapter/portal/...`
- `adapter/integration/...`

推荐理解方式：

- `domain/aggregates/...` 放业务真相与状态迁移
- `application/commands/...` 放命令契约与应用层写路径入口；具体 handler 是否也落在这里，要以当前 family layout 和 `plan.json` 为准
- `application/queries/...` 放查询契约；当前 query handler family 默认常落在 `adapter.application.queries/...`
- `application/subscribers/...` 放事件进入 application 的推进点
- `adapter/domain/repositories/...` 放当前 Spring + JPA 默认仓储 family
- `adapter/application/...` 放当前 query / client handler 等 adapter-module application family
- `adapter/...` 其余部分放 Web、外部协议、job 等边界适配

## 命名规范

- 聚合根：业务名词，例如 `Content`、`MediaProcessingTask`
- 实体：业务名词，且只在所属聚合语义里出现
- 值对象：业务概念值名，例如 `ContentStatus`
- 状态 / 生命周期值：优先使用 `*Status`、`*Phase`
- 命令：`*Cmd`
- 查询：`*Qry`
- 外部边界：`*Cli`
- 领域事件：`*DomainEvent`
- 命令处理器：`*CommandHandler`
- 查询处理器：优先按当前 family 使用 `*QryHandler`
- 外部客户端处理器：优先按当前 family 使用 `*CliHandler`
- 领域订阅器：`*DomainEventSubscriber`
- 集成订阅器：`*IntegrationEventSubscriber`
- 仓储：`*Repository`
- 读模型：优先显式表达用途，例如 `*Detail`、`*ListItem`、`*Projection`

## 文件归位规则

- 每类对象必须出现在它的责任目录，不用“离谁近”决定放在哪里。
- 不能为了方便把跨层对象放进相邻目录，例如把外部 DTO 塞进 `application`，或把查询投影塞进 `domain`。
- 命令、查询、事件、订阅器、适配器实现都要保持角色可反推，文件名和目录必须同时说明它是谁、属于哪一层。
- 生成主面和手写主面都要按同一责任目录归位，不能因为生成器产物存在就破坏层边界。
- 当前 layout 下，“逻辑属于 application”与“物理文件落在 application module 还是 adapter module”不是一回事。像 `*QryHandler.kt`、`*CliHandler.kt` 这类 family 即使落在 `adapter.application.*`，承担的仍然是 application 级调度责任。

## 审计检查点

- 名称是否能直接反推出角色，而不是读完实现才知道用途
- 目录是否和责任一致，而不是“看起来差不多”就放在附近
- 同一个概念在不同层是否保持同一词汇，不出现命名漂移
- `Content`、`MediaProcessingTask`、`MediaProcessingCli` 这些主对象是否一眼就能看出各自属于哪一层
- `*QryHandler.kt`、`*CliHandler.kt`、`*Repository.kt` 这类当前 family 的命名与目录，是否和 `application` / `adapter` 指南里的现实落点一致，而不是继续沿用旧的 `*QueryHandler` 或错误目录想象
