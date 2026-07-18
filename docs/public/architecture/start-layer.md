# Start Layer

Start layer 是 cap4k 项目的 Spring Boot runtime assembly 层。它把 domain、application 和 adapter 模块装配成可启动应用，提供 local startup、runtime config、bean wiring 和 smoke path，但它不是 business truth layer。

## 负责

Start layer 负责 Spring Boot application entry、runtime assembly、local startup、runtime config、profile wiring、bean scanning、application smoke path 和运行时集成检查。它回答“这个项目如何启动、如何装配、哪些模块被放进运行时”。

在参考项目中，start layer 让 `cap4k-reference-content-studio-domain`、`cap4k-reference-content-studio-application` 和 `cap4k-reference-content-studio-adapter` 能在同一个 Spring Boot runtime 中协作。它可以承载 smoke tests，证明发布、媒体处理 callback、paid publication saga 或 design contract 在运行时 wiring 下可被触达。

## 不负责

Start layer 不负责业务真相。它不应该决定内容是否可发布、Aggregate 如何改变状态、Command 是否成功、Query 如何定义业务含义、Controller payload 如何映射或 external capability 如何解释业务结果。

runtime config 可以影响环境、profile、endpoint wiring 或 fake/external adapter 选择，但不应该替代 domain/application 的业务判断。若配置项开始决定业务不变量，说明业务规则已经放错层。

## 生成骨架

cap4k generation 可以为 start module 提供 project wiring、Spring Boot entry、configuration shape、module assembly 或 smoke-test-friendly entry。生成骨架让 runtime assembly 可被重复定位，也让 docs 和 reference project 能用同一套模块名解释启动路径。

生成骨架不负责创造业务流程。它可以帮助应用启动和装配 bean，但具体发布规则、媒体处理状态推进、paid publication recovery 或 callback 语义仍由 domain/application/adapter 的 handwritten logic 表达。

## 手写逻辑

Start layer 的手写逻辑应该保持轻量，主要是 runtime config、profile-specific wiring、local startup 支持、fake/external adapter selection 和 smoke path glue。它可以提供 smoke tests 验证 wiring，但 smoke tests 不应替代 domain behavior tests 或 application orchestration tests。

参考测试锚点包括 `StartApplicationSmokeTest`、`ContentStudioHappyPathHttpSmokeTest`、`ContentStudioPaidPublicationSagaSmokeTest`、`ContentStudioDesignContractTest`、`PublishContentCommandContractTest` 和 `MediaProcessingCallbackIntegrationEventSmokeTest`。

## 依赖方向

Start layer 可以依赖 domain、application 和 adapter 来完成 assembly。domain、application 和 adapter 不应反向依赖 start。adapter 也不应通过 start layer 取得业务决策；start 只负责把实现接到 runtime。

这个依赖方向允许 start 选择运行时配置和 bean 组合，但不允许 start 成为跨层共享业务服务。需要业务判断时，应回到 domain/application；需要协议转换时，应回到 adapter。

## 参考项目

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读 `cap4k-reference-content-studio-start` 时，优先定位这些锚点：

- `StartApplicationSmokeTest`
- `ContentStudioHappyPathHttpSmokeTest`
- `ContentStudioPaidPublicationSagaSmokeTest`
- `ContentStudioDesignContractTest`
- `PublishContentCommandContractTest`
- `MediaProcessingCallbackIntegrationEventSmokeTest`

这些文件能展示 start layer 如何证明 runtime wiring 和 smoke path 可用，同时不把业务规则写进启动层。

## 审核

审核 start layer 时，先看它是否只做 Spring Boot runtime assembly、local startup 和 runtime config。再看 smoke path 是否覆盖关键 wiring，但没有把所有业务正确性都压到端到端测试。最后确认 domain、application、adapter 没有依赖 start，start 中也没有新增业务真相。
