# Generator Input Projection

generator input projection 是把已经形成的业务意图、模型和技术设计投影成 cap4k generator 能消费的事实。它不是让 generator 替作者做业务判断，而是让作者把已决定的边界、类型、入口、模块和 persistence surface 写成可审查输入。

在 [Reference Content Studio](../examples/reference-content-studio.md) 中，主要输入是 schema、`design/design.json`、`design/value-objects.json`、`design/enums.json` 和 root `build.gradle.kts` 中的 cap4k extension configuration。它们共同决定生成骨架的 shape；真正的状态推进、补偿语义、幂等和异常处理仍在 handwritten logic 中表达。

## Projection Surfaces

输入投影要覆盖这些 surface：

- DB schema：Aggregate table、ID、引用、enum type marker、Value Object type marker、unique constraint 和外键。
- `design/design.json`：Command、Query、API Payload、client、Domain Event、Integration Event、Domain Service、Saga、Subscriber、job 等 building blocks。
- `design/value-objects.json`：通过 `types.valueObjectManifest` 管理 Value Object。
- `design/enums.json`：通过 `types.enumManifest` 管理 Business Enum。
- module layout：domain、application、adapter、start 的物理模块位置。
- Gradle extension configuration：声明 project、templates、types、sources、generators 和 analysis output layout。

这些输入应该来自前面几轮 authoring 的设计结论。不要通过“先生成看看”来绕过业务意图，也不要把 plan output 反向当成业务模型来源。

## Schema Projection

schema 表达 persistence surface，不是业务模型的全部。参考项目中：

- `content` 表未声明 `@Parent` 或 `@Ignore`，承载 `Content` 聚合根。
- `media_processing_task` 表未声明 `@Parent` 或 `@Ignore`，承载 `MediaProcessingTask` 聚合根。
- `paid_publication_task` 表未声明 `@Parent` 或 `@Ignore`，承载 paid publication 的持久化协调状态。
- `content.release_policy` 使用 `@Type=ReleasePolicy;`，连接 enum manifest。
- `media_processing_task.result_snapshot` 使用 `@Type=MediaProcessingResultSnapshot;`，连接 JSON-backed Value Object。
- `media_processing_task.content_id` 和 `paid_publication_task.content_id` 使用 `@RefAggregate=Content;`，表达跨聚合引用事实。

schema 可以帮助 generator 理解字段、类型和 persistence mapping，但不能替代 Aggregate 行为。表之间存在外键，也不表示一个 Aggregate 可以直接修改另一个 Aggregate。

## design/design.json

`design/design.json` 是主要 building-block 输入。它把 technical design 中的 application entry、event、payload、client 和 Saga 锚点写成结构化事实。

参考项目里可以看到这些类别：

- `command`：例如 `CreateContentDraft`、`ApproveContentReview`、`StartMediaProcessing`、`MarkMediaProcessingSucceeded`、`RecordContentMediaReady`、`PublishContent`、paid publication commands。
- `query`：例如 `GetContentDetail`、`GetMediaProcessingStatus`、`GetPaidPublicationStatus`、`ListSubmittedMediaProcessingTasksForPolling`。
- `api_payload`：HTTP payload 和 result shape。
- `client`：例如 `TriggerMediaProcessing`、`GetMediaProcessingStatus`、paid publication external capabilities。
- `domain_event`：例如 `ContentPublicationReady`、`MediaProcessingSucceeded`。
- `integration_event`：例如 inbound `MediaProcessingCallback` 和 outbound `ContentPublished`。
- `saga`：例如 `PaidPublicationSaga`。

这些 entries 说明“有哪些结构锚点应该存在”。它们不说明 `Content` 何时可以发布，也不说明 Saga 每一步如何补偿；这些属于 handwritten logic 和 focused evidence。

## Type Manifests

`types.enumManifest` 和 `types.valueObjectManifest` 是相邻类型输入路径。

`design/enums.json` 通过 `types.enumManifest` 管理 Business Enum。参考项目中，`ReleasePolicy` 包含 `IMMEDIATE` 和 `PAID`，`MediaProcessingResultStatus` 包含 `SUCCEEDED` 和 `FAILED`。这些 enum 帮助作者避免裸值漂移，并让 schema marker、domain type 和 generated output 保持一致。

`design/value-objects.json` 通过 `types.valueObjectManifest` 管理 Value Object。参考项目中，`MediaProcessingResultSnapshot` 属于 `MediaProcessingTask` 聚合语境，使用 `storage = "json"`，并包含 `mediaProcessingTaskId`、`contentId`、`externalTaskId`、`resultStatus`、`assetSha256`、`assetLocation`、`completedAt` 等字段。

type manifest 的目标是表达类型边界。不要把 adapter payload 直接投影成 Value Object，也不要把 enum 当作业务 policy 的全部。

## Module Layout

module layout 要和 [Architecture](../architecture/index.md) 对齐。参考项目的 Gradle extension 中，`domainModulePath`、`applicationModulePath` 和 `adapterModulePath` 分别指向：

- `cap4k-reference-content-studio-domain`
- `cap4k-reference-content-studio-application`
- `cap4k-reference-content-studio-adapter`

start module 由 bootstrap configuration 记录为 `cap4k-reference-content-studio-start`。这些路径决定 generated output 和 checked-in skeleton 应该落在哪里，也帮助 plan review 判断 module placement 是否正确。

如果 plan output 显示 Query handler、client-handler 或 persistence adapter 的物理位置和作者预期不同，先回到 module layout 和 generator input projection 检查，不要直接移动生成文件。

## Gradle Extension Configuration

Gradle extension 把输入面连接到 generator。参考项目 root `build.gradle.kts` 中的关键配置包括：

```kotlin
cap4k {
    project {
        basePackage.set("com.only4.cap4k.reference.contentstudio")
        domainModulePath.set("cap4k-reference-content-studio-domain")
        applicationModulePath.set("cap4k-reference-content-studio-application")
        adapterModulePath.set("cap4k-reference-content-studio-adapter")
    }
    types {
        enumManifest {
            files.from("design/enums.json")
        }
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
    }
    sources {
        designJson {
            files.from("design/design.json")
        }
        db {
            enabled.set(true)
        }
    }
}
```

这段配置的 public authoring 含义是：作者把 schema、design JSON 和 type manifests 声明为输入；generator 消费这些输入并输出计划与骨架。它不是业务规则位置，也不是让 Gradle 配置承载领域判断。

## Feedback Signals

这些信号说明输入投影需要回到前面的 authoring 圈：

- schema 中的 Aggregate table 和模型边界不一致。
- `design/design.json` 中的 `command` 实际只是读取，或 `query` 实际会写入。
- `types.valueObjectManifest` 中的类型没有值语义。
- `types.enumManifest` 中的 enum 被用来隐藏复杂 policy。
- module path 导致 generated output 落到错误层。
- Gradle extension 配置里声明了输入，但业务叙述和 tests 无法解释这些输入。

输入投影的目标是让 generator 看见正确事实，而不是让 generator 修正不清晰的设计。
