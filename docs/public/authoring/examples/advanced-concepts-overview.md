# 概念选择实践示例总览

## Scenario

本页不是再讲一遍 [内容草稿到发布主链路](content-draft-to-publish.md)、[媒体处理 callback 主路径](media-processing-callback.md) 或 [媒体处理 polling 备用路径](media-processing-polling.md)，而是给审阅者和作者一张对照表：当共享参考项目已经固定为 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径与 polling fallback 路径时，什么时候默认路径已经足够，什么时候需要引入更具体的建模概念。

这里的默认前提不变：`ApproveContentReviewCmd` 完成并登记领域事实后，由领域事件订阅器发出 `StartMediaProcessingCmd`，创建或加载 `MediaProcessingTask`，再通过 `TriggerMediaProcessingCli` 启动外部处理；外部结果优先经外部事实入口回来，内部触发入口只在 callback 不可用或不可靠时作为补位入口。只要这个主干还能自然表达业务，作者就不应该为了“更像 DDD”而提前引入额外抽象。

## Why this layer / concept

这层总览存在，是因为这些概念最容易被误用成“默认配置升级包”。在这个参考项目里，很多边界其实已经由默认路径讲清楚了：`Content` 只负责内容生命周期，`MediaProcessingTask` 只负责媒体处理生命周期，`TriggerMediaProcessingCli` 只负责外部调用边界，外部事实入口是结果回传主路径，内部触发入口是 fallback。若问题只是这些责任还没写清、命令链还没切清、外部回传还没统一收敛成内部命令，那么继续把默认路径讲完整，通常比先引入额外概念更对。

只有当默认路径已经被正确表达之后，仍然出现“值语义说不清”“身份容易串位”“领域判断无处安放”“流程已经跨时间等待与补偿”的剩余问题，才需要选择对应概念。因此本页要回答的不是“Value Object / Strong ID / Domain Service / Saga 是什么”，而是“相对于这个固定参考项目，它们分别解决哪一种具体问题”。

## Recommended shape

先看默认路径是否仍然够用，再决定是否引入其他概念。对这个参考项目，最快的分诊方式可以先压成下面几类：

- 默认路径：问题仍然是 `ApproveContentReviewCmd -> StartMediaProcessingCmd` 的交接缝没讲清、外部事实入口和内部触发入口还没收敛成同一组内部命令、或 `PublishContentCmd` 还在越层回写 `MediaProcessingTask`。这说明要先把现有命令链和聚合边界写对，不是缺额外概念。
- Value Object：问题信号是同一个业务值必须被统一校验、归一化、比较，而且外部事实入口与内部触发入口都应该构造成同一种内部值表达；这时才接近 [content-publication-value-object.md](content-publication-value-object.md)。
- Strong ID：问题信号是 `ContentId`、`AuthorId`、`MediaProcessingTaskId` 或外部任务号容易串位；这时才接近 [content-publication-strong-id.md](content-publication-strong-id.md)。
- Domain Service：问题信号是这段判断明显属于领域，但放进 `Content`、`MediaProcessingTask` 或某个 Value Object 都别扭；这时才接近 [content-publication-domain-service.md](content-publication-domain-service.md)。
- Saga：问题信号是流程已经跨时间等待、恢复、超时或补偿，外部事实入口与内部触发入口只是把外部事实送回同一条长期协调流程；这时才接近 [content-publication-saga.md](content-publication-saga.md)。

如果你面对的是 `Content` 审核、发布资格检查、`MediaProcessingTask` 启动与推进、外部事实入口回写、内部触发入口补位这些已经能被现有命令链与聚合边界表达清楚的事情，那么默认路径通常已经足够。典型信号是：你只是需要把 `ApproveContentReviewCmd -> StartMediaProcessingCmd` 的交接缝讲清，把外部事实入口与内部触发入口都收敛成同一组内部命令语义，或明确 `PublishContentCmd` 只消费只读事实而不顺手回写 `MediaProcessingTask`。这类问题本质上还是默认路径落地质量问题，不是某个概念缺席。

当问题开始落在“同一个业务值必须被统一校验、归一化、比较，且外部事实入口与内部触发入口都必须构造成一致的内部值表达”时，才接近 [content-publication-value-object.md](content-publication-value-object.md) 的适用面。这里的对照点不是“有没有字符串字段”，而是 `Content` 或 `MediaProcessingTask` 内是否已经出现一个不能再靠原始类型或松散字段组合表达的业务值。例如外部媒体任务标识、处理结果摘要、某组发布前判定值如果需要统一规则，Value Object 有理由；如果只是状态枚举、简单字段、或单纯想防止 ID 混用，默认路径或更轻表达通常仍够。

当问题落在“身份底层类型相同，但业务上绝不能互传”时，才接近 [content-publication-strong-id.md](content-publication-strong-id.md) 的适用面。对照来看，`Content.id` 应该是 `ContentId`，同上下文引用 `MediaProcessingTask` 应该落到 `MediaProcessingTaskId`，外部账号概念进入内容上下文时应该按本地语言落到 `AuthorId`。这不是要求把所有字段都包装起来，而是让生成默认路径先守住聚合根和引用身份边界。

当问题落在“这段判断明显属于领域，但硬塞进 `Content`、`MediaProcessingTask` 或某个 Value Object 都别扭”时，才接近 [content-publication-domain-service.md](content-publication-domain-service.md) 的适用面。对照来看，如果你只是需要在 `PublishContentCmd` 前检查 `Content` 是否审核通过、`MediaProcessingTask` 是否已经完成，而且这些事实在当前时刻都已可用，那么依旧是默认路径加清晰领域判断的问题，不必因为涉及两个领域对象就自动升级。只有当这条判断确实是领域真相、又不自然属于任一单个聚合或值对象时，Domain Service 才是合理归位；它看的是已经内部化后的事实，而不是外部事实入口 payload 或内部触发入口 DTO。

当问题已经不是“当前这一刻如何判断”，而是“流程必须跨时间等待、恢复、超时、补偿，且外部事实入口与内部触发入口都只是把外部事实送回同一条长期协调流程”时，才接近 [content-publication-saga.md](content-publication-saga.md) 的适用面。相反，如果外部媒体结果回来后，只需要被翻译成内部命令来推进 `MediaProcessingTask`，再由后续命令决定 `Content` 是否可发布，这仍然是默认命令链，不该过早引入 Saga。Saga 只在你必须把等待中的流程状态持久化下来、稍后恢复推进、并明确处理失败补偿时才成立，而不是因为项目里已经出现 callback 或 polling 就自动成立。

## Non-example / misuse

不是所有“读起来有点复杂”的地方都需要引入额外概念。把 `Content.approve()` 内直接发 `TriggerMediaProcessingCli`、把外部事实入口直接改 `MediaProcessingTask`、把内部触发入口写成另一套发布编排，这些都不是“需要新概念”，而是默认路径本身被写坏了。此时先修默认边界，不要用概念名给越层实现找名义。

把每个字符串都包装成值对象、把每个外部身份都直接沿用上游上下文命名、把每段不想放进聚合的逻辑都扔进 `*DomainService`、把任何跨两个步骤的流程都抬成 Saga，也都属于误用。对这个参考项目来说，更常见的错误是：Value Object 还没解决统一值语义，就先制造一堆命名碎片；Strong ID 还没守住本地语言，就把 `UserId` 直接塞进 `Content`；Domain Service 还没表达明确领域判断，就先接管 application orchestration；Saga 还没出现等待点与补偿点，就先吞掉 `Content` 和 `MediaProcessingTask` 的职责。这样的“概念堆叠”会让外部事实入口主路径与内部触发入口 fallback 路径各长出一套说法，反而更难审阅。

另一个常见误区是把本页当作后续页面的摘要替身。本页不负责重讲 `content-publication-value-object`、`content-publication-strong-id`、`content-publication-domain-service`、`content-publication-saga` 各自的完整示例；它只负责先把它们与默认路径的关系讲清，让作者知道该跳到哪一页，审阅者知道该按哪一种问题去质询实现。

## Audit cues

- 看作者是否先把 `Content`、`MediaProcessingTask`、`TriggerMediaProcessingCli`、callback 主路径、polling fallback 这五个固定元素说清，再讨论其他概念；如果没有，优先追回默认路径解释。
- 看问题究竟是“默认命令链没讲清”还是“默认命令链讲清后仍需要另一个概念”。前者不该引入额外概念，后者才需要进入对应概念页。
- 看 [content-publication-value-object.md](content-publication-value-object.md) 是否被用来解决统一值语义，而不是泛化包装 primitive。
- 看 [content-publication-strong-id.md](content-publication-strong-id.md) 是否只解决身份防混淆与本地语言边界，而不是替代聚合、命令和命名规则。
- 看 [content-publication-domain-service.md](content-publication-domain-service.md) 是否只承载不自然落在单个聚合中的领域判断，而没有接管 `TriggerMediaProcessingCli` 调用、外部事实入口编排或内部触发入口调度。
- 看 [content-publication-saga.md](content-publication-saga.md) 是否只在存在跨时间等待、恢复和补偿要求时出现，而不是因为流程里有外部系统就被默认引入。
- 看 callback 与 polling 是否始终被描述为同一内部命令语义的两个入口，其中 callback 为主、polling 为备；任何概念都不应颠倒这条主次关系。
