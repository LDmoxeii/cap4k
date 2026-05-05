# Adapter Authoring Guide

本页面向项目作者，说明在 Default Happy Path 下，哪些代码应该落在 `adapter`。统一教学场景仍然是“内容发布与处理示例项目”：外部世界通过 Web 请求、回调、消息、轮询 job、持久化设施接触系统；内部世界只接受稳定的命令、查询和聚合行为。callback 是媒体处理结果返回的主路径，polling 是兼容性备用路径，但它们都只是入口形态，不是业务真相本身。

## 这一层负责什么

- 提供 controller、RPC、message listener 等外部入口，把请求转换成内部命令或查询。
- 提供 persistence adapter，把 `Content` 与 `MediaProcessingTask` 的持久化实现接到内部仓储边界上。
- 提供 domain subscriber / integration subscriber 的运行时入口壳层，把外部事件或内部事件分发到应用层推进点。
- 提供 polling job 入口，作为媒体处理结果回传的备用触发方式。
- 提供 `MediaProcessingCli` 这类外部能力边界实现，负责协议转换、异常转换、请求发送与结果读取。
- 把“外面怎么说”翻译成“系统内部如何理解”，同时把系统内部结果翻译回外部表面。

适配器层的关键词是“边界”和“转换”。它是系统与环境接触的地方，不是系统业务真相驻留的地方。

## 这一层可以写什么

- Web 请求转内部命令 / 查询，例如把“创建内容草稿”的 HTTP 请求转换成 `CreateContentDraftCmd`。
- 外部回调或集成事件转内部命令，例如把媒体平台回调转换成 `CompleteMediaProcessingCmd` 或 `FailMediaProcessingCmd`。
- polling job 调度，例如定时读取外部媒体处理状态，再转换成内部同步命令。
- `Content`、`MediaProcessingTask` 对应的持久化适配实现，例如数据库行与聚合对象之间的映射。
- `MediaProcessingCli` 的实现，用来封装发起处理、查询处理状态、统一第三方错误。
- 输入输出层面的校验、字段映射、协议兼容处理，让应用层只看到稳定的内部契约。

作者在这一层最重要的工作，是把所有外部差异收敛住。无论外部媒体平台的字段名、回调格式、轮询响应如何变化，进入应用层之前都应该已经被翻译成项目内部能稳定理解的命令或查询。

## 这一层不能写什么

- `Content` 或 `MediaProcessingTask` 的业务真相，不在 controller、job、repository adapter、`cli` 实现里决定状态机规则。
- 聚合生命周期规则，例如“什么时候允许发布”“什么时候允许处理失败后重试”。
- 跨层偷塞 application / domain 逻辑，例如 controller 里串完整业务流程，或 `cli` 里替聚合决定下一状态。
- 把外部 DTO、数据库结构、第三方状态码直接当成领域对象传播到内部。
- 让 polling job 成为主流程真相源，绕开 callback 主路径对应的内部命令边界。

如果一段代码已经在解释“业务上为什么允许这样变”，它就不该继续留在适配器层。适配器层只说明“这段外部输入如何进入内部模型”。

## 典型目录与文件骨架

```text
adapter/
  portal/
    web/
      ContentController.kt
      ContentQueryController.kt
  application/
    persistence/
      content/
        ContentRepositoryAdapter.kt
      media_processing_task/
        MediaProcessingTaskRepositoryAdapter.kt
  integration/
    media_processing/
      MediaProcessingCliHttpAdapter.kt
      MediaProcessingCallbackController.kt
      MediaProcessingPollingJob.kt
      MediaProcessingEventListener.kt
```

- `portal/` 放对用户或上游系统暴露的入口，例如 Web controller。
- `application/persistence/` 放把内部仓储边界接到数据库或其他存储设施的适配实现。
- `integration/` 放外部系统交互相关入口与 `cli` 实现，例如媒体处理平台回调、轮询、SDK 或 HTTP 适配。
- callback 与 polling 可以各有自己的入口文件，但进入应用层之后应该汇合到同一套内部命令语义。

这份骨架的目的，是让作者一眼看出某个文件到底是在“接外面”，还是在“改里面”。如果一个文件两件事都在做，通常就需要拆层。

## 常见反例

- controller 直接加载聚合并改状态，跳过内部命令和 handler。
- polling job 直接把 `MediaProcessingTask` 标成成功或失败，而不是转换成内部同步命令。
- 把第三方回调 DTO 当成领域对象一直往里传，直到聚合层也开始识别外部字段名。
- `MediaProcessingCli` 除了做协议调用，还顺便决定“媒体完成后立即发布内容”，把流程编排塞进边界实现。
- persistence adapter 为了省事直接返回查询投影或表结构对象，导致写模型边界和读模型边界混在一起。

这些反例会让系统越来越依赖外部技术细节，最终回调、轮询、Web 入口、数据库结构都会开始抢业务真相。

## 最低验证与审计检查点

- 所有外部输入是否都先被转换成内部命令 / 查询，再进入应用层推进。
- adapter 是否只承担边界与适配职责，而没有自己决定 `Content` 或 `MediaProcessingTask` 的业务状态机。
- callback 主路径和 polling 备用路径是否都落到一致的内部命令边界，没有长出两套事实来源。
- `MediaProcessingCli`、controller、job、repository adapter 里是否没有重复编写领域规则。
- 外部 DTO、数据库结构、第三方状态码是否都被收敛在适配器层，没有直接污染内部领域模型与应用契约。
