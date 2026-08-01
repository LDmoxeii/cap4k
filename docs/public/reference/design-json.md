# Design JSON

`design/design.json` 是 ordinary source generation 的 building-block 输入。它声明结构锚点，不承载业务规则实现。

## 文档结构与字段规则

- 根节点必须是 JSON array。
- array 中的每一项必须是 object。
- `tag` 和 `name` 必须是非空 string。
- 除 `domain_event` 外，`package` 必填。
- 公开输入字段为 `tag`、`name`、`package`、`description`、`aggregates`、`fields`、`resultFields`、`eventName`、`persist` 和 `artifacts`。
- field 的 `type` 必须写 formal Kotlin-style type expression，不能写 `self`；nullability 属于 type expression，不再使用独立 `nullable`。
- `domain_event.fields` 是生成事件的完整 payload；省略或留空时生成无 payload 的 marker event。
- `domain_event.aggregates` 只表达归属和放置，不会隐式生成 Aggregate、Entity、Strong ID 或 snapshot 字段。
- Domain Event field 的 resolved semantic type graph 不得直接或嵌套包含 cap4k 已知的 Aggregate/Entity；应显式使用标量、Strong ID、Value Object、enum 或专用不可变 snapshot。
- flow 或 drawing-board 片段只有满足这些规则后，才能通过 `sources.designJson.files` 作为普通 design JSON 输入。

## 支持的 Normal Tags

| `tag` | 主要用途 | 常见输出方向 |
| --- | --- | --- |
| `command` | 状态变更的 application intent | Command skeleton |
| `query` | read-side observation intent | Query contract / handler surface |
| `capability` | external capability contract | Capability call / handler surface |
| `api_payload` | adapter-facing payload/result shape | payload classes |
| `domain_event` | domain fact contract | domain event / subscriber or handler shell |
| `integration_event` | published language event contract | integration event / inbound subscriber shell |
| `domain_service` | domain decision anchor | domain service skeleton |

## 常用 Keys

| Key | Type | 说明 |
| --- | --- | --- |
| `tag` | string | 必须是 supported normal tag。 |
| `package` | string | design package segment；除 `domain_event` 外必填；最终 package 还受 layout block 影响。 |
| `name` | string | building block name。 |
| `description` | string | 可读 description。 |
| `aggregates` | string array | 关联的 aggregate names；普通 tag 可用空数组表示不绑定具体 aggregate。`domain_event` 必须且只能声明一个 owner aggregate；该值只表达归属，不贡献 payload。 |
| `fields` | field array | input fields。 |
| `resultFields` | field array | 允许用于 `command`、`query`、`capability` 和 `api_payload` 的 result shape；在 `command` 上表达 command outcome。 |
| `eventName` | string | 只允许用于 `domain_event` 和 `integration_event`；`integration_event` 必填。 |
| `persist` | boolean | 只允许用于 `domain_event`。 |
| `artifacts` | artifact array | 部分 tag 用来表达 output family / variant metadata。 |

field item 常见 shape：

```json
{ "name": "snapshots", "type": "List<ContentSnapshot?>?" }
```

`type` 会在 source assembly 之后编译为 canonical structured type tree。支持 builtin、named type、`List<T>`、`Set<T>`、`Map<K, V>`、`Array<T>` 和递归 `?`；不支持 mutable collection、`Collection`、`Iterable`、`Sequence`、`IntArray` / `LongArray` 等 primitive array alias、tuple 或任意 generic type。Domain Event 的递归 Entity 检查同样遍历 `Array<T>` element。旧 `nullable` 字段已移除。

`PageData<Item>` 是 query / API result 专用的 page envelope，不属于通用 generic type algebra，也不能用于普通 request/value field。

## 最小 Command

```json
[
  {
    "tag": "command",
    "package": "content.workflow",
    "name": "SubmitContentForReview",
    "description": "submit content draft for review",
    "aggregates": ["Content"],
    "fields": [
      { "name": "contentId", "type": "ContentId" }
    ]
  }
]
```

`command` 表达写入意图。读取其他 aggregate 或 external fact 可以用于 zero-trust validation，但写入 ownership 仍应收敛到目标 aggregate 和 application command boundary。

`command.fields` 表达 Command payload，`command.resultFields` 表达 Command outcome payload。Command、Query、Capability、API Payload、Domain Event 和 Integration Event 的 structured fields 都进入同一 canonical semantic-value compiler，但各自 role 和输出 family 仍保持独立。省略或声明空 `command.resultFields` 时仍保持无结果 response 形态。

## 最小 Query

```json
[
  {
    "tag": "query",
    "package": "content.read",
    "name": "GetContentDetail",
    "description": "get content detail",
    "aggregates": ["Content"],
    "fields": [
      { "name": "contentId", "type": "ContentId" }
    ],
    "resultFields": [
      { "name": "title", "type": "String" },
      { "name": "reviewStatus", "type": "String" }
    ]
  }
]
```

`query` 只观察。它不修复 write model，不推进状态。

## 最小 Domain Event

```json
[
  {
    "tag": "domain_event",
    "name": "ContentApproved",
    "description": "content review was approved",
    "aggregates": ["Content"],
    "persist": true,
    "fields": [
      { "name": "contentId", "type": "ContentId" },
      { "name": "approvedAt", "type": "Instant" }
    ]
  }
]
```

`aggregates: ["Content"]` 让 generator 确定事件归属和 package，但不会生成 `Content` 字段。`contentId`、`approvedAt` 是作者显式声明的历史事实。需要更多上下文时继续声明不可变值；不要把 `Content`、owned Entity 或 persistence proxy 放进 payload。没有历史值需要携带时可以省略 `fields`，生成 marker event。

## 最小 Integration Event

```json
[
  {
    "tag": "integration_event",
    "package": "media.processing",
    "name": "MediaProcessingCallback",
    "description": "media processing callback",
    "aggregates": ["MediaProcessingTask"],
    "eventName": "cap4k.reference.contentstudio.media-processing.completed",
    "fields": [
      { "name": "externalTaskId", "type": "String" },
      { "name": "assetLocation", "type": "String" }
    ],
    "artifacts": [
      { "family": "integration-event", "variant": "inbound" }
    ]
  }
]
```

`integration_event` 需要清晰的 `eventName` 和 fields。`family = "integration-event"` 配合 `variant = "inbound"` / `"outbound"` 表达 artifact variant；inbound subscriber shell 仍只负责把外部事实导向内部 command semantics。

## Tag 约束

| Tag | 约束 |
| --- | --- |
| `command` | 不应作为 read shortcut；状态变化放在 command path。 |
| `query` | 不应 mutate aggregate 或修复状态。 |
| `capability` | 表达 application-facing external capability，不放 adapter protocol details。 |
| `api_payload` | 表达 payload shape，不替代 command/query 边界。 |
| `domain_event` | 表达业务事实，不表达技术 continuation step；`eventName` 可用于 published name，`persist` 只允许在这里使用；`fields` 是完整 payload，`aggregates` 仅表达归属，resolved field graph 不得包含 Aggregate/Entity。 |
| `integration_event` | 表达 service boundary published language；必须声明 `eventName`。 |
| `domain_service` | 用于跨对象领域判断，不放 HTTP、message、database protocol。 |

## Analysis 片段边界

drawing-board JSON 是 analysis evidence。只有内容满足本页字段集合、tag 约束、field shape 和 artifact selection 规则时，才可以通过 `sources.designJson.files` 作为普通 design JSON 输入。

Value Object 和 enum 使用 type manifests 输入。数据库唯一约束保留为 schema/canonical metadata，用于存储完整性和已支持的 owned relation 基数推断；aggregate generator 不生成唯一性 Query、Handler 或 Validator。
