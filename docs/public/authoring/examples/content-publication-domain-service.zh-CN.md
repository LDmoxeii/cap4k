# 内容发布示例：Domain Service

## Scenario

在共享的 `content-publication / media-processing` 参考项目里，`PublishContentCmd` 不是“收到命令就立刻发布”的简单动作。进入发布判断前，系统通常已经经历过两条外部收敛路径：

- 主路径：媒体处理平台通过 `callback` 回推处理结果。
- 备用路径：平台没有及时回推时，由 `polling` 补拉处理结果。

这两条路径先被统一写回 `MediaProcessingTask`，把“外部世界说了什么”收敛成领域内部已经承认的事实，例如“处理成功”“处理失败”“输出资产已齐”。与此同时，`Content` 也已经承认自己的内部事实，例如“审核通过”“未下线”“未撤回”。

到了真正执行 `PublishContentCmd` 的时刻，应用层手里拿到的是两个已经内部化的领域对象：`Content` 和 `MediaProcessingTask`。此时要回答的问题不是“怎么调用外部系统”，而是“基于当前已知事实，这篇内容现在是否具备发布资格”。

这就是一个适合 Domain Service 的正例：判断明显属于领域，但又不自然只挂在 `Content` 或 `MediaProcessingTask` 任一侧。

## Why default path is not enough

默认路径优先要求行为落在聚合自身，因为真正改状态的动作仍然应该由聚合行为表达。例如：

- `Content` 自己表达“送审”“审核通过”“进入已发布”。
- `MediaProcessingTask` 自己表达“开始处理”“处理成功”“处理失败”。

问题在于，“发布资格判断”不是只看 `Content` 就能下结论，也不是只看 `MediaProcessingTask` 就能下结论。它需要把两个聚合已经承认的事实放在一起，形成一个领域判断：

- `Content` 是否审核通过。
- `Content` 当前是否仍允许进入发布。
- `MediaProcessingTask` 是否已经把 callback / polling 的结果统一为可用事实。
- `MediaProcessingTask` 的结果是否满足发布所需媒体条件。

如果硬把这段判断塞进 `Content`，`Content` 就必须知道过多媒体处理侧事实；如果硬塞进 `MediaProcessingTask`，媒体任务又会反向承担内容发布语义。这两边都别扭。

但它也还不是 Saga。原因是这里没有“跨时间等待中的流程状态机”问题。`callback` 和 `polling` 的不确定性，应该先在媒体处理链路中被吸收并规范化；等到执行领域判断时，所需事实已经可用。此时只是“事实输入 -> 资格结论输出”的同步领域判断，不是“等待未来某个时刻再继续”的跨时间编排。

## Recommended shape

推荐形态很简单：Domain Service 只消费已经内部化的领域事实，只返回领域结论，不直接承担状态写入。

一个合适的正例是“发布资格评估”：

- 输入：`Content`、`MediaProcessingTask`。
- 前提：二者都已经是领域对象，而不是 callback payload、polling DTO 或 application 层拼出来的临时结构。
- 输出：一个明确结论，例如“允许发布”“媒体未就绪”“内容状态不允许发布”。

支持性片段只需要表达 facts-in / conclusion-out 形状：

```ts
const eligibility = publicationEligibility.evaluate(content, mediaProcessingTask)
```

这里的 `publicationEligibility` 不负责调用仓储、不负责触发 callback、不负责补轮询，也不直接把 `Content` 改成已发布。它只回答领域问题。真正的状态变更仍然由后续聚合行为完成，例如应用层在拿到“允许发布”的结论后，再向 `Content` 发起发布命令。

这样做的价值在于：

- 领域判断仍留在领域层，而不是掉进 application handler。
- 聚合边界没有被伪装打穿，因为写入动作没有偷偷转移到 service。
- callback 与 polling 的入口差异已经在判断前被抹平，领域规则只面对稳定事实。

## Non-example / misuse

非例子 1：编排垃圾桶式“领域服务”。

- 一个 `ContentPublicationDomainService` 里同时做查仓储、发命令、调媒体平台、发消息、记日志、兜底重试。
- 这种东西本质上是 application orchestration，只是借了领域服务的名字。

非例子 2：伪装成领域服务的跨聚合写 loophole。

- service 一边读 `Content`，一边改 `MediaProcessingTask`，最后再顺手把 `Content` 改成已发布。
- 这会让“领域判断”变成跨聚合写入口，绕开原本应由聚合命令保护的边界。

非例子 3：把跨时间等待问题错建模成领域服务。

- “如果还没收到 callback，就每隔十分钟再看一次 polling 结果，直到条件满足再继续发布。”
- 这不是 Domain Service，而是典型的 [Saga](../advanced/saga.zh-CN.md) 问题，因为核心难点是跨时间推进、等待与恢复，而不是当前时刻的领域判断。

## Usage boundary

使用边界可以压成一句话：当 `Content` 与 `MediaProcessingTask` 的相关事实都已经在当前时刻可用时，可以用 Domain Service 做发布资格判断；当问题变成“等待未来事实出现后再继续”，就应该升级为 Saga。

因此，`PublishContentCmd` 所对应的领域判断边界应该是：

- 先把 callback 主路径和 polling 备用路径统一进 `MediaProcessingTask`。
- 再让 Domain Service 基于 `Content` 与 `MediaProcessingTask` 做资格结论。
- 最后由聚合命令或聚合行为执行真正状态变更。

只要 service 仍然是“领域判断器”而不是“流程驾驶员”，这个边界就是清楚的。

## Audit cues

- 审阅时先问：这里解决的是不是领域判断，而不是 callback 编排、polling 调度或仓储 orchestration。
- 再问：判断所需事实是否已经存在于 `Content` 与 `MediaProcessingTask` 内部，而不是直接消费外部 DTO。
- 如果作者声称这是发布资格逻辑，审阅者应能指出具体资格事实，例如 `Content` 已审核通过、媒体任务结果已规范化且满足发布条件。
- 如果 service 自己执行跨聚合写入，或者自己决定“等下次 polling 再说”，那它已经越过 Usage boundary。
- 如果把这段逻辑搬回某个单一聚合会更自然，那它就不该被提升为领域服务。
