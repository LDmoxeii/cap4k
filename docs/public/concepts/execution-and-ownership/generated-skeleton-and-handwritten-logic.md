# Generated Skeleton And Handwritten Logic

cap4k 的生成结果应被理解为“稳定骨架 + 手写业务逻辑”的协作关系。生成骨架帮助项目保持命名、目录、入口、接口和 wiring 的一致性；手写逻辑负责表达业务含义、状态判断、异常处理、幂等、恢复和外部能力语义。generator 不会自动替团队做业务决策。

public docs 中提到的 skeleton 有两类 ownership。第一类是 checked-in skeletons，它们可以作为仓库里的可见入口长期维护，例如 Command、Query、Subscriber、client、client-handler、Saga 或 API payload 相关结构。第二类是 build-owned generated source，它们属于构建期间由 generator 维护的输出，不应被当作手写业务逻辑的主要落点。

当一个用例需要实现 Command handler、Query handler、Subscriber reaction、Scheduled Reaction、external capability adapter 或 Saga step 时，应先找到生成骨架给出的稳定位置，再在约定的 handwritten logic location 中补业务代码。这样代码审查能快速分辨哪些结构来自 design tags，哪些判断来自团队手写。

在 cap4k 中，`command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga` 等 design tags 可以驱动不同类型的 skeleton。生成部分提供的是 shape；字段语义、行为调用、事件条件、read model 选择、协议转换、补偿动作和恢复边界仍由手写逻辑负责。对 public docs 读者来说，关键是把 generator 当成组织工具，而不是业务作者。

如果团队确实需要绕过某个 skeleton，fallback rule 是：必须在手写代码中保留同等清晰的用例边界、命名、层级方向和 ownership 说明。绕过 skeleton 不能成为把 Command、Query、adapter protocol、domain behavior 混在一起的理由；也不能把 build-owned generated source 改成长期手写区。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。可以把 `PublishContentCmd`、`GetContentDetailQry`、`ContentPublicationReadyDomainEventSubscriber`、`MediaProcessingPollingFallbackJob`、`TriggerMediaProcessingCli`、`GetMediaProcessingStatusCli`、`*CliHandler`、`FakeMediaProcessingCli` 和 `PaidPublicationSaga` 放在一起阅读，观察 skeleton 如何提供入口，而业务判断如何落在手写实现中。

Generated Skeleton And Handwritten Logic 的设计边界是 ownership。常见误用包括在 build-owned generated source 中写业务规则，把 skeleton 当成已经完成的业务实现，绕过 skeleton 后丢失命名边界，或者把 generator 输出和手写代码反复覆盖。审查时可以看业务逻辑是否位于可维护位置，生成文件是否没有被长期手改，fallback 是否仍保持 application/domain/adapter/start 模块边界，以及读者是否能从文件名判断职责。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 cap4k 生成骨架、构建期生成源和手写业务逻辑的 ownership 边界。
Type: architecture diagram
Prompt: Draw a cap4k generated skeleton ownership architecture diagram. Show design tags creating checked-in skeletons and build-owned generated source, then handwritten logic filling Command handler, Query handler, Subscriber, client-handler, and Saga steps. Use Chinese labels and preserve English identifiers.
Must show: checked-in skeletons, build-owned generated source, handwritten logic locations, design tags, fallback rule when bypassing skeletons, generator as organization tool
Must avoid: implying generator writes business decisions automatically, showing build-owned generated source as a handwritten business area, arrows that mix adapter protocol into domain model
Alt text after insertion: cap4k 生成与手写 ownership 图，展示 design tags、checked-in skeletons、build-owned generated source、手写逻辑位置和绕过 skeleton 时的 fallback rule。
-->
