# Adapter Authoring Guide

本页面向项目作者，说明在 Default Happy Path 下，哪些代码应该落在 `adapter`。统一教学场景仍然是“内容发布与处理示例项目”：外部世界通过 Web 请求、回调、消息、轮询 job、持久化设施接触系统；内部世界只接受稳定的命令、查询和聚合行为。callback 是媒体处理结果返回的主路径，polling 是兼容性备用路径，但它们都只是入口形态，不是业务真相本身。

当前 Spring + JPA 现实下，adapter 的默认物理落点有几条是比较固定的：仓储 family 在 `adapter.domain.repositories`，query / client handler family 常在 `adapter.application.*`，payload family 常在 `adapter.portal.api.payload`。这些文件即使落在 `src/main/kotlin`，也仍然可能是 plan-managed 产物；改之前先看 `build/cap4k/plan.json`，不要把目录位置误判成 ownership。

query handler、client/cli handler 和 adapter 边界的责任说明见 [公开战术模型](tactical-model.zh-CN.md)。当前默认要点是：query/client/cli handler 物理上通常在 adapter 侧，但实现的是 application 请求契约。

## 这一层负责什么

- 提供 controller、RPC、message listener 等外部入口，把请求转换成内部命令或查询。
- 提供 persistence adapter，把 `Content` 与 `MediaProcessingTask` 的持久化实现接到内部仓储边界上。
- 提供 domain subscriber / integration subscriber 的运行时入口壳层，把外部事件或内部事件分发到应用层推进点。
- 提供 polling job 入口，作为媒体处理结果回传的备用触发方式。
- 提供 `MediaProcessingCli` 这类外部能力边界实现，负责协议转换、异常转换、请求发送与结果读取。
- 把“外面怎么说”翻译成“系统内部如何理解”，同时把系统内部结果翻译回外部表面。
- 在当前默认模板里，为 Spring Boot / JPA 提供真实可扫描的 repository 落点，例如 `@EnableJpaRepositories` 默认关注的 `adapter.domain.repositories`。

适配器层的关键词是“边界”和“转换”。它是系统与环境接触的地方，不是系统业务真相驻留的地方。

## 这一层可以写什么

- Web 请求转内部命令 / 查询，例如把“创建内容草稿”的 HTTP 请求转换成 `CreateContentDraftCmd`。
- 外部回调或集成事件转内部命令，例如把媒体平台回调转换成 `CompleteMediaProcessingCmd` 或 `FailMediaProcessingCmd`。
- polling job 调度，例如定时读取外部媒体处理状态，再转换成内部同步命令。
- `Content`、`MediaProcessingTask` 对应的持久化适配实现，例如 `adapter.domain.repositories` 下的 Spring Data repository 与 `*JpaRepositoryAdapter`。
- `MediaProcessingCli` 的实现，用来封装发起处理、查询处理状态、统一第三方错误。
- 输入输出层面的校验、字段映射、协议兼容处理，让应用层只看到稳定的内部契约。
- 只要 `plan.json` 没有把它声明成 recurring plan item，作者自己维护的 controller、callback bridge、polling job、integration listener 就是正常的 adapter 手写面。

作者在这一层最重要的工作，是把所有外部差异收敛住。无论外部媒体平台的字段名、回调格式、轮询响应如何变化，进入应用层之前都应该已经被翻译成项目内部能稳定理解的命令或查询。

## 这一层不能写什么

- `Content` 或 `MediaProcessingTask` 的业务真相，不在 controller、job、repository adapter、`cli` 实现里决定状态机规则。
- 聚合生命周期规则，例如“什么时候允许发布”“什么时候允许处理失败后重试”。
- 跨层偷塞 application / domain 逻辑，例如 controller 里串完整业务流程，或 `cli` 里替聚合决定下一状态。
- 把外部 DTO、数据库结构、第三方状态码直接当成领域对象传播到内部。
- 让 polling job 成为主流程真相源，绕开 callback 主路径对应的内部命令边界。
- 不看 `plan.json`，就直接改 `adapter.domain.repositories/*Repository.kt`、`adapter.application/*Handler.kt`、`adapter.portal/api/payload/*` 这类当前常见的 plan-managed family。

如果一段代码已经在解释“业务上为什么允许这样变”，它就不该继续留在适配器层。适配器层只说明“这段外部输入如何进入内部模型”。

## 典型目录与文件骨架

```text
<adapter-module>/
  src/main/kotlin/.../adapter/
    domain/
      repositories/
        ContentRepository.kt
        MediaProcessingTaskRepository.kt
    application/
      queries/
        content/
          GetContentDetailQryHandler.kt
        media_processing_task/
          GetMediaProcessingProgressQryHandler.kt
      distributed/
        clients/
          media_processing/
            StartMediaProcessingCliHandler.kt
    portal/
      api/
        payload/
          content/
            CreateContentPayload.kt
        ContentController.kt
        ContentQueryController.kt
    integration/
      media_processing/
        MediaProcessingCallbackController.kt
        MediaProcessingPollingJob.kt
        MediaProcessingEventListener.kt
```

- `adapter.domain.repositories` 是当前默认 Spring Data JPA 落点。生成文件里通常同时包含 repository interface 与 `*JpaRepositoryAdapter`，所以看到 `*Repository.kt` 时先判断 ownership，不要想当然把它当纯手写类。
- `adapter.application.queries`、`adapter.application.distributed.clients` 是当前默认的 query / client handler family 落点；它们属于 adapter module，但承担的是把外部能力接到 application 表面的责任。
- `adapter.portal.api.payload` 是当前 payload family 的默认落点；payload 是否可直接编辑同样要先看 `plan.json`。
- `portal/`、`integration/` 里像 controller、callback、polling job 这类边界入口通常是作者自己维护的手写面，但如果项目额外开启了对应 generator family，也一样先核对 ownership。
- callback 与 polling 可以各有自己的入口文件，但进入应用层之后应该汇合到同一套内部命令语义。

这份骨架的目的，是让作者一眼看出某个文件到底是在“接外面”，还是在“改里面”。如果一个文件两件事都在做，通常就需要拆层。

## 常见反例

- controller 直接加载聚合并改状态，跳过内部命令和 handler。
- polling job 直接把 `MediaProcessingTask` 标成成功或失败，而不是转换成内部同步命令。
- 把第三方回调 DTO 当成领域对象一直往里传，直到聚合层也开始识别外部字段名。
- `MediaProcessingCli` 除了做协议调用，还顺便决定“媒体完成后立即发布内容”，把流程编排塞进边界实现。
- persistence adapter 为了省事直接返回查询投影或表结构对象，导致写模型边界和读模型边界混在一起。
- 不确认 ownership 就直接改 `ContentRepository.kt`、`GetContentDetailQryHandler.kt`、`CreateContentPayload.kt` 这类计划产物，结果下次生成又把作者补丁冲掉。

这些反例会让系统越来越依赖外部技术细节，最终回调、轮询、Web 入口、数据库结构都会开始抢业务真相。

## 对应示例

- [内容发布与处理示例项目总览](examples/reference-project-overview.zh-CN.md)：先看统一教学项目里有哪些外部入口、内部命令和聚合边界需要被 adapter 接住。
- [内容草稿到发布主链路](examples/content-draft-to-publish.zh-CN.md)：看 Web / UI 请求如何只翻译成 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`ApproveContentCmd`、`PublishContentCmd`，而不直接改 `Content`。
- [媒体处理 callback 主路径](examples/media-processing-callback.zh-CN.md)：看 callback controller、integration listener、`IntegrationEventSubscriber`、`MediaProcessingCli` 各自该停在哪一层。
- [媒体处理 polling 备用路径](examples/media-processing-polling.zh-CN.md)：看 polling job 为什么只该承担外部任务查询和内部命令转换，而不该升级成主流程编排器。

## 最低验证与审计检查点

- 所有外部输入是否都先被转换成内部命令 / 查询，再进入应用层推进。
- adapter 是否只承担边界与适配职责，而没有自己决定 `Content` 或 `MediaProcessingTask` 的业务状态机。
- callback 主路径和 polling 备用路径是否都落到一致的内部命令边界，没有长出两套事实来源。
- `MediaProcessingCli`、controller、job、repository adapter 里是否没有重复编写领域规则。
- 外部 DTO、数据库结构、第三方状态码是否都被收敛在适配器层，没有直接污染内部领域模型与应用契约。
- 在编辑 `adapter.domain.repositories/*Repository.kt`、`adapter.application/**/*.kt`、`adapter.portal/api/payload/**/*.kt` 之前，是否先检查了 `build/cap4k/plan.json`，确认它是作者面还是 plan-managed family。
- 当前 Spring + JPA 仓储落点是否仍然保持在 `adapter.domain.repositories`，而不是继续沿用上一版文档里那个不符合当前模板现实的 `adapter/application/persistence/...` 想象目录。
