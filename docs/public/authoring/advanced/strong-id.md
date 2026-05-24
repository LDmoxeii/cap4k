# Strong ID

## 什么时候需要它

- 你的项目已经出现多个长得很像但不能混用的 ID，例如 `ContentId`、`MediaProcessingTaskId`、`ExternalMediaTaskId`。
- 作者和审阅者已经频繁依赖命名习惯而不是类型系统，导致传错 ID 的风险开始真实存在。
- 你想做的是工程强化：让类型系统更早拦住 ID 混用，而不是重写 DDD 基础教学。

在共享教学项目里，最常见的风险是把 `Content` 的内部 ID、`MediaProcessingTask` 的内部 ID，以及外部媒体平台回传的任务 ID 混成同一种字符串。callback 主路径和 polling 备用路径尤其容易把外部任务号一路带进内部，最后谁是谁靠变量名猜。

具体语境见 [示例总览](../examples/index.md) 和 [内容发布示例：Strong ID](../examples/content-publication-strong-id.md)：这里的 Strong ID 讨论只是在同一内容发布与媒体处理项目里强化 ID 边界，不另起样例。

## 默认生成路径

Strong ID 1.0 的生成默认路径已经把聚合根 ID 生成为 Strong ID 类型。`Content` 这类聚合根的主键不是继续作为裸 `UUID` 或 `Long` 暴露给作者，而是进入 `ContentId` 这样的类型边界；同一上下文内引用另一个聚合时，也应落到目标聚合的 ID 类型上。

这不表示所有值都应该升级成 ID wrapper，也不表示 Strong ID 变成 DDD 核心 building block。默认命令边界、聚合边界和命名规则仍然是主规则；Strong ID 只是让生成出的身份边界更早被类型系统拦住。

## 推荐形态

- 把 Strong ID 视为类型安全增强，而不是领域建模主角。
- 生成的聚合根 ID 默认使用 Strong ID 类型，例如 `Content.id: ContentId`。
- 同一限界上下文内引用另一个聚合时，用 `@RefAggregate=<AggregateName>`，例如 `Content.mediaProcessingTaskId: MediaProcessingTaskId?`。
- 当前上下文需要保存外部概念的本地身份时，用 `@RefId=<TypeName>`，例如内容上下文把创作者称为作者时写 `AuthorId`，不要在 `Content` 内直接建模跨上下文的 `UserId`。
- 优先在容易混淆的边界引入，例如 `ContentId`、`MediaProcessingTaskId`、`AuthorId`、`ExternalMediaTaskId` 的命令参数、查询参数和导航字段。
- 对 `ExternalMediaTaskId` 这类例子，先用一个明确分界：
  - 如果主要问题是防止不同 ID 类型传错、混用、串位，那它属于 Strong ID。
  - 如果主要问题是这个值本身还有更丰富的语义，例如格式标准化、复合组成、业务等值规则，那它更接近 [Value Object](value-object.md)。
- 保持 ID 的业务归属清楚：内部聚合 ID 还是内部聚合 ID，外部系统任务号还是外部系统任务号，不要因为都包了一层就混成同类。
- 当前阶段可以先用薄包装实现类型约束，但后续设计应与 wrapper 脱钩。重点是“类型语义更强”，不是“必须用某种包装手法”。
- callback 和 polling 进入系统时，都先把外部任务标识规范化成统一的内部表达，再交给 `MediaProcessingTask` 相关命令。
- JPA 生成形态走 embedded Strong ID 的单列承载；不要把作者引回 primitive ID 默认策略、value class converter 路线或 UoW 保存时补 ID 的路径。

## DB 输入示例

内容发布参考项目里，`content` 表可以这样表达聚合根 ID、当前上下文身份和同上下文聚合引用：

```sql
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
```

生成后的领域形状应读作：

```kotlin
class Content(
    val id: ContentId,
    val authorId: AuthorId,
    val mediaProcessingTaskId: MediaProcessingTaskId?,
)
```

这里的 `ContentId` 来自聚合根默认 Strong ID；`AuthorId` 是当前内容上下文对外部创作者概念的本地语言；`MediaProcessingTaskId` 来自同一上下文内 `MediaProcessingTask` 聚合根的 ID 类型。

## 常见误用

- 把 Strong ID 讲成 DDD 的核心积木，好像不用它就不算领域建模。
- 所有字段一律包一层 ID wrapper，连不会混淆的局部值也一起复杂化。
- 内部聚合 ID 和外部任务号都复用同一种 wrapper，只因为底层都是字符串。
- 把 wrapper 机制本身绑成长期架构约束，后续一旦换实现就牵动大量无关代码。
- callback 路径用了强类型 ID，polling 路径还是裸字符串，造成同一边界两套约束强度。
- 在内容上下文里明明使用 `AuthorId` 作为本地语言，却直接把跨上下文的 `UserId` 塞进 `Content`。
- 让作者在 `Mediator.uow.save()` 或持久化监听器里给新聚合补 ID；聚合根 ID 应在创建路径上已经是 Strong ID。

## 审计检查点

- 当前 Strong ID 是否解决了真实的 ID 混用风险，而不是为了“看起来更强类型”。
- 团队能否清楚区分内部聚合 ID 与外部系统 ID，而不是只看底层存储类型。
- 生成的聚合根 ID 是否默认是 `ContentId` / `MediaProcessingTaskId` 这类 Strong ID，而不是 primitive ID 策略。
- 同上下文聚合引用是否使用 `@RefAggregate`，当前上下文外部概念身份是否使用 `@RefId`。
- 本地语言是否保持一致，例如内容上下文用 `AuthorId` 时没有直接暴露跨上下文 `UserId`。
- Strong ID 是否仍然是工程强化，不会盖过聚合、命令和命名这些默认主规则。
- 如果作者把 `ExternalMediaTaskId` 归到 Strong ID，审阅者是否能确认核心诉求是 ID 防混淆，而不是其实在承载更丰富的值语义。
- 现有设计是否保留了与 wrapper 脱钩的空间，而不是把某种封装技法写死成框架核心。
- callback 主路径和 polling 备用路径在 ID 进入内部时，是否遵守同一套类型表达。
