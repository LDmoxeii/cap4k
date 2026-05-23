# 内容发布示例：Strong ID

相关规则：

- [Strong ID](../advanced/strong-id.md)
- [生成输入源](../generator/input-sources.md)
- [内容发布与处理示例项目总览](reference-project-overview.md)

## Scenario

本页沿用统一参考项目，不新增业务样板：`Content` 负责内容生命周期，`MediaProcessingTask` 负责媒体处理生命周期，外部平台用户在内容上下文里被称为作者。

Strong ID 的目标是防止身份串位。`Content.id`、`MediaProcessingTask.id`、`Content.authorId` 和外部媒体任务号不应该因为底层都能落到字符串或 UUID 存储，就在代码里变成同一种值。

## DB input

`content` 表可以这样表达聚合根身份、当前上下文身份和同上下文聚合引用：

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

## Recommended shape

- `content.id` 是聚合根 ID，Strong ID 1.0 默认生成 `ContentId`。
- `content.author_id` 用 `@RefId=AuthorId`，表示内容上下文里的作者身份。即使外部账号上下文把同一个人叫 user，`Content` 内也不要直接建模跨上下文的 `UserId`。
- `content.media_processing_task_id` 用 `@RefAggregate=MediaProcessingTask`，表示同一上下文内对 `MediaProcessingTask` 聚合根的引用，因此字段类型跟随 `MediaProcessingTaskId`。
- 新聚合根进入 `Mediator.uow.save()` 之前已经带有自己的 Strong ID。不要把 ID 生成推迟到 UoW 保存时、持久化监听器或反射补值路径。

## Non-example / misuse

- 把 `Content.id` 继续暴露为裸 `UUID` 或 `Long`，再靠命名习惯避免和 `MediaProcessingTask.id` 混用。
- 在内容上下文里直接保存 `UserId`，但业务语言和命令都把这个身份称为作者。
- 把同上下文聚合引用写成普通 primitive foreign key，导致 `mediaProcessingTaskId` 失去 `MediaProcessingTaskId` 类型边界。
- 为了统一格式，把内部聚合 ID、作者身份和外部媒体任务号包成同一个 wrapper。

## Audit cues

- 看 `Content.id` 和 `MediaProcessingTask.id` 是否都是各自聚合的 Strong ID 类型。
- 看 `@RefAggregate` 是否只用于同一上下文内的聚合引用。
- 看 `@RefId` 是否表达当前上下文自己的身份语言，而不是泄漏外部上下文命名。
- 看创建路径是否已经得到 Strong ID，而不是在 UoW 保存时临时生成。
- 看 callback 与 polling 入口是否在进入内部命令前把外部任务标识和内部聚合身份区分清楚。
