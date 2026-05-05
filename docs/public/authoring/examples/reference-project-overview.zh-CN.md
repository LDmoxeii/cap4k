# 内容发布与处理示例项目总览

> 本页是作者指南示例附录的统一参考底图。阅读其他示例前，先用它校准“这套文档到底在教哪个项目”。

相关规则：

- [Default Happy Path](../default-happy-path.zh-CN.md)
- [示例合同](../example-contract.zh-CN.md)
- [Domain Guide](../domain.zh-CN.md)
- [Application Guide](../application.zh-CN.md)
- [Adapter Guide](../adapter.zh-CN.md)

## Scenario

统一教学项目是一个单限界上下文的“内容发布与媒体处理”系统。写模型固定围绕两个聚合根展开：

- `Content`：负责草稿、送审、审核、发布。
- `MediaProcessingTask`：负责媒体处理发起、处理中、成功、失败、重试。

默认主链路固定为：

1. 通过 `CreateContentDraftCmd` 创建内容草稿。
2. 通过 `SubmitContentForReviewCmd` 送审。
3. 审核通过后，通过 `ApproveContentCmd` 让 `Content` 进入可发布前状态。
4. 通过 `MediaProcessingCli` 向外部媒体平台发起处理，内部推进 `MediaProcessingTask`。
5. 外部结果返回优先走 callback 主路径；callback 不可用或不可靠时，才用 polling 备用路径补位。
6. 当内容审核条件和媒体处理条件都满足后，通过 `PublishContentCmd` 发布。

本页不是额外讲一个新规则，而是说明为什么所有层指南、高级概念页、生成边界页都反复回到同一个项目。没有这张统一底图，作者很容易在不同页面里脑补出不同样例，最后把“层规则”“高级概念”“生成 ownership”分别套在不同项目上，导致文档能读懂，但项目写不出来。

## Why this layer / concept

这不是某一层的专属示例，而是整个作者指南体系的统一样板。它存在的原因有三个：

- 给所有页面一个稳定的业务语境。`Content`、`MediaProcessingTask`、`MediaProcessingCli`、callback 主路径、polling 备用路径，在每一页都代表同一批对象，不需要读者反复切换心智模型。
- 给“默认路径”和“高级偏离”一个共同对照物。比如 [Saga](../advanced/saga.zh-CN.md) 页说默认不用 Saga，前提就是你已经能把这个项目的 callback 主路径和 polling 备用路径按默认命令链讲清楚。
- 给审阅者一条统一审计线。看到 `CreateContentDraftCmd`、`ApproveContentCmd`、`PublishContentCmd`、`MediaProcessingCli` 时，团队应该立刻知道它们在这个参考项目里各自负责哪一段，而不是每次 review 都重新猜。

如果你在某一页里发现一个建议无法自然落回这条统一链路，优先怀疑的是例子或归位出了问题，而不是先把规则推翻。

## Recommended shape

推荐把整个参考项目理解成三条彼此衔接、但责任清晰分开的线：

1. 内容生命周期线  
   `CreateContentDraftCmd -> SubmitContentForReviewCmd -> ApproveContentCmd -> PublishContentCmd`

2. 媒体处理生命周期线  
   `MediaProcessingCli` 发起外部处理 -> 内部持有外部任务标识 -> `MediaProcessingTask` 进入处理中 -> 结果回写成功 / 失败 / 重试

3. 结果回传入口线  
   callback 主路径：外部回调 / 集成事件 -> `IntegrationEventSubscriber` 或 callback bridge -> 内部命令推进  
   polling 备用路径：外部任务 ID -> 定时 job 轮询 -> 内部命令推进

推荐目录和阅读顺序也应围绕这三条线组织：

- 先读 [内容草稿到发布主链路](content-draft-to-publish.zh-CN.md)，看 `Content` 生命周期如何被命令推进。
- 再读 [媒体处理 callback 主路径](media-processing-callback.zh-CN.md)，看推荐返回路径如何进入内部。
- 最后读 [媒体处理 polling 备用路径](media-processing-polling.zh-CN.md)，确认备用路径只是兼容入口，不是另一套真相。

这套参考项目的关键形状不是“对象很多”，而是“每个对象只承担自己该承担的那一段”：

- `Content` 不直接理解外部 callback payload。
- `MediaProcessingTask` 不直接决定内容是否发布。
- `MediaProcessingCli` 不自己编排发布流程。
- callback 和 polling 虽然入口不同，但进入内部后都要收敛为同一批命令语义。

## Non-example / misuse

下面这些写法会直接破坏“统一参考项目”的作用：

- 每一页各用一个不同业务例子，导致 domain 页在讲订单、application 页在讲视频、adapter 页又在讲消息网关，读者无法建立统一边界。
- 把参考项目写成“一个大对象负责全部流程”，让 `Content` 同时承担审核、媒体处理、发布编排，失去双聚合教学价值。
- 在 overview 里把 callback 和 polling 讲成并列主路径，弱化“callback 主、polling 备”的默认规则。
- 把 `MediaProcessingCli` 当成业务真相源，在 overview 里就暗示“外部平台说完成就代表系统发布完成”。
- 让 overview 只讲抽象概念，不点名 `CreateContentDraftCmd`、`PublishContentCmd`、`MediaProcessingTask` 这些贯穿全文的对象，结果其他页面无法回链。

如果 overview 自己都无法回答“这个统一示例为什么值得在所有页复用”，后续各页的示例就会散掉。

## Audit cues

- 看本页是否明确固定了 `Content`、`MediaProcessingTask`、`MediaProcessingCli`、callback 主路径、polling 备用路径这五个核心元素。
- 看本页是否把内容生命周期线、媒体处理生命周期线、结果回传入口线区分开，而不是揉成一团。
- 看其他页面是否都能自然链接回本页，并且继续沿用同一组对象与命令名。
- 看 callback 是否始终被描述为首选返回路径，polling 是否始终被描述为 fallback。
- 看 overview 是否足够具体，让审阅者能据此检查 [content-draft-to-publish](content-draft-to-publish.zh-CN.md)、[media-processing-callback](media-processing-callback.zh-CN.md)、[media-processing-polling](media-processing-polling.zh-CN.md) 三个附录没有各讲各的项目。
