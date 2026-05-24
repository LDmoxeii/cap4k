# 内容发布示例：Value Object

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.md)
- [Value Object](../advanced/value-object.md)
- [Strong ID](../advanced/strong-id.md)
- [Domain Guide](../domain.md)

## Scenario

本页仍然停留在统一教学项目里，不换例子、不换命令世界：`Content` 负责草稿、送审、审核、发布，`MediaProcessingTask` 负责媒体处理发起、处理中、成功、失败、重试；`ApproveContentReviewCmd` 之后由领域事件订阅器发出 `StartMediaProcessingCmd`，外部结果再通过 callback 主路径或 polling 备用路径回到内部。

在这套参考项目里，Value Object 不是为了“让模型看起来更领域化”，而是为了把已经重复出现、并且需要统一解释的业务值收拢起来。最直接的正例有两个：

- `ContentTitle`：内容创建、编辑、送审前检查时都会反复出现，同一个标题值需要统一校验、归一化和比较。
- `MediaProcessingResultSnapshot`：callback 主路径和 polling 备用路径都会拿回“处理结果”这组事实，内部需要把它们收敛成同一种值表达，再交给 `MediaProcessingTask` 理解。

## Why this concept fits

默认路径会先鼓励作者用最轻的表达：单一标量先裸用 primitive，固定状态先用 enum，只有当值语义已经开始独立成形时才选择 Value Object。这个默认建议本身没有错，但在当前参考项目里，以下两类值已经需要更明确的值语义：

- `ContentTitle` 如果继续只是一个裸 `String`，创建草稿、更新标题、送审校验时就容易各自复制不同的长度规则、空白裁剪规则、显示值规则。这样 `CreateContentDraftCmd` 和后续命令看到的“标题合法”并不是同一种定义。
- `MediaProcessingResultSnapshot` 如果继续拆成几段零散字段，callback adapter 和 polling job 很容易分别拼出不同的内部结构。最后同样是“处理成功并产出结果”，`MediaProcessingTask` 却会因为入口不同而接收到不同形状的内部事实。

也就是说，选择 Value Object 的原因不是形式感，而是这里已经出现了跨命令、跨入口、跨适配器的统一值语义需求。没有这层收拢，`Content` 和 `MediaProcessingTask` 周围会不断长出重复校验和重复解释。

## Recommended shape

推荐把这两个例子分别放在不同力度的值语义层级上理解：

- `ContentTitle` 更接近单值但有业务语义的 Value Object。它的重点不是字段多，而是“标题”这个值在进入 `Content` 前就应该已经被统一整理好。
- `MediaProcessingResultSnapshot` 更接近复合 Value Object。它代表的是一次媒体处理结果的内部快照，可能同时包含输出摘要、错误摘要、时间点或外部回传后需要保留的解释信息，但这些字段一起才构成一个完整业务值。

作者在文档里可以把推荐形状理解成下面这种“进入聚合前先统一值解释”的样子：

```text
external callback / polling payload
  -> adapter / application translation
  -> MediaProcessingResultSnapshot
  -> MarkMediaProcessingSucceededCmd
  -> MediaProcessingTask
```

这个片段的重点不是代码形式，而是边界顺序：先把外部 payload 翻译成内部值，再让命令和聚合消费它。对 `ContentTitle` 也是同样思路：先形成统一标题值，再进入 `CreateContentDraftCmd`、编辑标题命令或送审前检查。

在 `cap4k-reference-content-studio` 当前落地里，这个例子故意走轻量 JSON-backed 形态：

- schema 中 `media_processing_task.result_snapshot` 通过 `@T=MediaProcessingResultSnapshot` 绑定到自定义类型。
- `design/types.json` 把短名绑定到手写 `MediaProcessingResultSnapshot` 和 `MediaProcessingResultSnapshotConverter`。
- 生成器只让 `MediaProcessingTask` 获得 `resultSnapshot` 字段与 converter 映射；值对象构造、校验、归一化和 converter 都是作者手写主面。
- 聚合行为把 snapshot 收进 `MediaProcessingTask`，命令 handler 只保存聚合根，不单独 `Mediator.uow.persist(snapshot)`。

## Non-example / misuse

- `ContentStatus`、`ReviewStatus`、`MediaProcessingStatus` 这类生命周期状态值，不会因为名字里带“值”就自动变成 Value Object。它们默认更接近 enum，核心问题是状态机分支，不是复杂值语义。
- 只是想避免把 `ContentId`、`MediaProcessingTaskId`、`ExternalMediaTaskId` 混用，这首先是 `Strong ID` 议题，不应在本页伪装成 Value Object 例子。ID 防混淆本身不等于已经有了丰富的值语义。
- 因为数据库把 `MediaProcessingResultSnapshot` 放进一个 `JSON` 列，就直接把那段持久化结构当成领域定义，或者误以为生成器已经支持 first-class `value_object`。这会把 storage shape 和 domain shape 混在一起。`JSON` 只是 persistence carrier，不是业务值本身。
- callback 主路径收到一套结果字段，polling 备用路径收到另一套结果字段，然后两个入口各自直接修改 `MediaProcessingTask`。这说明内部没有统一值表达，正是缺 Value Object 的信号。

## Usage boundary

Value Object 在这里的使用边界很明确：

- 它用来表达“已经需要统一解释的业务值”，例如 `ContentTitle`、`MediaProcessingResultSnapshot`。
- 它不用来替代 `Content` 或 `MediaProcessingTask` 的生命周期决策。是否允许发布、是否允许重试，仍然属于聚合行为。
- 它不负责解决纯 ID anti-mixing。那一层边界请优先放到 `Strong ID`。
- 它不负责把状态机包装得更花。状态推进仍然优先用 enum / status 概念表达，再由聚合解释迁移规则。
- 它也不由 `JSON`、嵌入列或 DTO 字段形状来定义。持久化和传输可以变化，但值语义边界应保持稳定。
- JSON-backed 值对象的 class、converter、构造与校验当前都应由作者维护；生成器消费的是字段类型绑定，不是完整生成值对象本体。

如果作者无法说明“这个对象除了防混淆或存储方便之外，还统一了什么业务值解释”，那它大概率还不该在本项目里建模为 Value Object。

## Audit cues

- 看 `ContentTitle` 是否把标题相关的校验、归一化、比较语义收拢成一个稳定值，而不是散落在多个命令 handler 里。
- 看 callback 主路径与 polling 备用路径进入内部前，是否都先收敛成同一种 `MediaProcessingResultSnapshot`，再交给 `MediaProcessingTask`。
- 看团队是否把 `ContentStatus` 这类生命周期状态直接当成 Value Object 使用；如果是，通常说明 enum / status 边界被写糊了。
- 看文档是否把 `Strong ID` 和 Value Object 分清：前者解决 ID 防混淆，后者解决业务值解释。
- 看 `JSON` 是否只被描述为持久化承载方式，而没有被当成领域定义本身。
- 看命令 handler 是否只保存 `MediaProcessingTask` 聚合根，而没有把聚合内 JSON-backed 值对象单独加入工作单元。
- 看值对象是否真的服务于统一命令 / 事件世界，而不是仅仅因为“包装后更像 DDD”就被引入。
