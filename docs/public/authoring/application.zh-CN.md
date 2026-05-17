# Application Authoring Guide

本页面向项目作者，说明在 Default Happy Path 下，哪些代码应该落在 `application`。统一教学场景仍然是“内容发布与处理示例项目”：`Content` 与 `MediaProcessingTask` 是写模型主角，外部媒体处理系统通过 `MediaProcessingCli` 进入内部；结果回传优先走 callback 主路径，polling 作为备用路径，但两条路径都必须收敛为内部命令和查询。

当前 cap4k 的另一个关键现实是：application 层的“责任”与“文件 ownership”不是一回事。`*Cmd.kt`、`*Qry.kt` 这类请求契约经常是 plan-managed 产物；`*QryHandler.kt`、`*CliHandler.kt`，甚至某些 `*DomainEventSubscriber.kt` family 也可能是计划写出的 checked-in 文件。作者在改这些文件之前，必须先看 `build/cap4k/plan.json`，不要因为它们位于 `src/main/kotlin` 就自动把它们当普通手写文件。

命令处理器、流程编排、订阅器和 `Mediator.cmd` / `Mediator.qry` / `Mediator.requests` 的默认协作方式见 [公开战术模型](tactical-model.zh-CN.md)。

## 这一层负责什么

- 定义 Command / Query 契约，给外部入口和内部流程一个稳定的应用层表面。
- 编写 handler，把一个主动作推进到对应聚合根，而不是直接在入口层里改状态。
- 调度 domain、repository、client、读模型或查询依赖，让“下一步做什么”在这里明确。
- 承接领域事件或集成返回之后的流程推进，例如在媒体处理结果返回后触发内部命令更新 `MediaProcessingTask`。
- 维护“一个入口推进一个主动作”的默认写法，让送审、启动处理、发布、查询详情都各自有清晰责任边界。
- 在当前默认 layout 下，区分“逻辑属于 application”与“物理文件在 application module 还是 adapter module”：契约多在 application module，部分 handler family 会落到 adapter module，但它们承担的仍是 application 级调度责任。

应用层的职责不是拥有业务真相，而是把各种入口意图整理成领域层能执行的内部动作，并把结果继续推进到下一个明确阶段。
写用例中的外部能力调用属于 command handler 的编排责任；入口层不应该先调用 client 再补一个 command。

## 这一层可以写什么

- 写命令契约，例如 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd`。但编辑前先确认它们是不是本次计划产物。
- 与媒体处理任务相关的写命令，例如 `StartMediaProcessingCmd`、`CompleteMediaProcessingCmd`、`FailMediaProcessingCmd`、`SyncMediaProcessingProgressCmd`。同样先看 ownership，再决定是改契约还是改手写完成面。
- 读查询契约，例如 `GetContentDetailQry`、`GetMediaProcessingProgressQry`；这类 `*Qry.kt` 通常是 request-contract surface，不是塞项目特有编排的地方。
- 手写 command handler 或其他 application 完成面：加载一个聚合根、在写用例需要外部能力返回时调用 client、调用一个主行为、保存该聚合根，并在需要时发起后续协作。只要文件不在 recurring plan item 里，它就是更安全的作者面。
- 用于推进后续动作的订阅器或应用层订阅逻辑，例如收到“媒体处理已完成”的内部事实后，触发下一个明确命令；如果某个 `*DomainEventSubscriber.kt` 是计划产物，就把项目特有逻辑继续下沉到你自己维护的手写 collaborator，而不是把一切都塞进生成壳里。
- 为发布决策准备的只读判断，例如读取“媒体已完成”的投影或查询结果，再决定是否允许 `PublishContentCmd` 继续推进 `Content`。

作者在这一层最常见的写法，是把一个入口意图翻译成“加载哪个聚合、调用哪个行为、保存哪个结果、是否触发下一个内部动作”。如果你写的代码主要在做这四件事，它通常就属于应用层。

当前可直接执行的 ownership 判断是：

- `*Cmd.kt`、`*Qry.kt`：默认先视为 request-contract 计划产物，先看 `plan.json`，再决定是否可改。
- `*QryHandler.kt`、`*CliHandler.kt`：当前默认 family 常落在 adapter module 的 `adapter.application.*` 下；它们虽承担 application 责任，但也要先看 `plan.json`。
- `*CommandHandler.kt` 或作者自己补的 orchestration file：如果它不是计划持续生成的文件，通常才是更稳定的作者完成面。

## 这一层不能写什么

- 直接修改聚合内部字段，绕开 `Content` 或 `MediaProcessingTask` 的行为方法。
- 把 adapter 侧的请求 DTO、回调 payload、第三方状态码对象直接带进应用层当业务真相使用。
- 让一个入口同时推进多个主动作，例如在一次 handler 中既改 `Content` 又改 `MediaProcessingTask`。
- 用查询流程偷偷承载写逻辑，例如在 `GetContentDetailQry` 里顺手修复状态。
- 没有明确边界理由就直接依赖 `MediaProcessingCli`，把外部系统细节散落在各个 handler 中。
- 把“callback 是主路径、polling 是备用路径”写成两套完全不同的业务规则。它们可以有不同入口，但不能有不同的内部真相。
- 不看 `plan.json` 就直接改 `*Cmd.kt`、`*Qry.kt`、`*QryHandler.kt`、`*CliHandler.kt`、`*DomainEventSubscriber.kt`，把计划产物误当成长久手写家。

应用层也不能变成“什么都先塞进来再说”的垃圾桶。凡是已经在表达业务不变量的代码，应该回到领域层；凡是还在转换外部协议的代码，应该留在适配器层。

## 典型目录与文件骨架

```text
<application-module>/
  src/main/kotlin/.../application/
    commands/
      content/
        CreateContentDraftCmd.kt
        SubmitContentForReviewCmd.kt
        PublishContentCmd.kt
        PublishContentCommandHandler.kt
      media_processing_task/
        StartMediaProcessingCmd.kt
        CompleteMediaProcessingCmd.kt
        SyncMediaProcessingProgressCmd.kt
        SyncMediaProcessingProgressCommandHandler.kt
    queries/
      content/
        GetContentDetailQry.kt
      media_processing_task/
        GetMediaProcessingProgressQry.kt
    subscribers/
      domain/
        MediaProcessingCompletedDomainEventSubscriber.kt
<adapter-module>/
  src/main/kotlin/.../adapter/application/
    queries/
      content/
        GetContentDetailQryHandler.kt
      media_processing_task/
        GetMediaProcessingProgressQryHandler.kt
    distributed/
      clients/
        media_processing/
          StartMediaProcessingCliHandler.kt
```

- `commands/`、`queries/` 下首先放 request-contract surface；这些文件是否可直接编辑，要先看 `plan.json`。
- `PublishContentCommandHandler.kt`、`SyncMediaProcessingProgressCommandHandler.kt` 这种不在 recurring plan 里的手写完成文件，才是默认更稳定的 application 作者面。
- `subscribers/` 下放事件进入 application 层后的推进点，但订阅器推进的仍然应该是明确的内部命令，不是直接改聚合；如果某个 subscriber family 已由 generator 接管，同样先看 ownership。
- 当前 repo 默认会把部分 query / client handler family 放进 adapter module 的 `adapter.application.*` 路径下。看到它们落在 adapter module，不代表它们就变成了 adapter 业务真相文件；它们仍然是在执行 application 层调度，只是 ownership 依旧要先确认。
- 媒体处理 callback 和 polling 进入 application 层之后，都应该落到 `media_processing_task/` 相关命令族，而不是各自长出一套独立写模型。

这份骨架强调的是角色分工。看到文件名时，作者和审阅者应该能立刻判断它是在定义动作、执行动作，还是只做观察。

## 常见反例

- 一个 `PublishContentCmd` handler 同时加载 `Content` 和 `MediaProcessingTask`，并在同一次事务里一起改状态，试图“一把推完所有事情”。
- 因为想少写一个命令，就让 callback controller 或 polling job 直接改仓储，不经过应用层 handler。
- 开放服务入口先调用 `MediaProcessingCli` 或 `ResourceStorageClient`，再调用 command 补写状态，把写用例拆散在入口层。
- 在查询 handler 中顺手补写状态，例如“查详情时发现媒体已经完成，于是直接把 `Content` 标记成可发布”。
- handler 直接解析第三方媒体平台状态码，并把这些外部枚举到处传递，导致应用层不再稳定。
- callback 路径走 `CompleteMediaProcessingCmd`，polling 路径却直接写另外一套“轮询成功逻辑”，造成同一事实有两套推进方式。
- 因为文件就在 `src/main/kotlin`，就直接去改 `CreateContentDraftCmd.kt`、`GetContentDetailQry.kt` 或 `GetContentDetailQryHandler.kt`，没有先核对它是不是 plan-managed artifact。

这些反例通常会让项目后期越来越难审计，因为写入边界、失败边界和重试边界会一起变模糊。

## 对应示例

- [内容发布与处理示例项目总览](examples/reference-project-overview.zh-CN.md)：先用统一参考项目校准“这条命令链到底在推进哪段主动作”。
- [内容草稿到发布主链路](examples/content-draft-to-publish.zh-CN.md)：对应 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd` 的默认应用层推进方式。
- [媒体处理 callback 主路径](examples/media-processing-callback.zh-CN.md)：对应 `MediaProcessingCli` 发起外部处理后，callback / integration event 如何回到内部命令链。
- [媒体处理 polling 备用路径](examples/media-processing-polling.zh-CN.md)：对应外部任务 ID、定时 job 和 `SyncMediaProcessingProgressCmd` / 完成失败命令的 fallback 收敛方式。

## 最低验证与审计检查点

- 一个 handler 是否只推进一个主动作，并且只让一个聚合根进入持久化写边界。
- 写 handler 是否总是通过聚合行为推进状态，而不是直接改对象内部字段。
- 查询路径是否保持只读，`GetContentDetailQry` 与 `GetMediaProcessingProgressQry` 是否没有偷偷承载写逻辑。
- callback 主路径和 polling 备用路径进入应用层后，是否收敛为同一组内部命令语义，而不是各写各的真相。
- 使用 `MediaProcessingCli` 的地方是否有明确边界理由，而且外部协议细节没有扩散进整个应用层。
- 在编辑 `*Cmd.kt`、`*Qry.kt`、`*CommandHandler.kt`、`*QryHandler.kt`、`*CliHandler.kt`、`*DomainEventSubscriber.kt` 之前，是否先检查了 `build/cap4k/plan.json`，区分 request-contract plan item 与作者完成面。
- 如果某个 application family 是计划产物，项目特有编排是否已经回到作者自己维护的手写完成文件，而不是继续塞进 generator-managed shell。
