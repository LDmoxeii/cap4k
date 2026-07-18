# Design JSON

`design/design.json` 是 ordinary source generation 的 building-block 输入。它声明结构锚点，不承载业务规则实现。

## 文档结构与字段规则

- 根节点必须是 JSON array。
- array 中的每一项必须是 object。
- `tag` 和 `name` 必须是非空 string。
- 除 `domain_event` 外，`package` 必填。
- 公开输入字段为 `tag`、`name`、`package`、`description`、`aggregates`、`fields`、`resultFields`、`eventName`、`persist` 和 `artifacts`。
- field 的 `type` 必须写明确类型名，不能写 `self`。
- `domain_event.fields` 中的 field name `entity` 是保留名。
- flow 或 drawing-board 片段只有满足这些规则后，才能通过 `sources.designJson.files` 作为普通 design JSON 输入。

## 支持的 Normal Tags

| `tag` | 主要用途 | 常见输出方向 |
| --- | --- | --- |
| `command` | 状态变更的 application intent | Command skeleton |
| `query` | read-side observation intent | Query contract / handler surface |
| `client` | external capability contract | client contract / handler surface |
| `api_payload` | adapter-facing payload/result shape | payload classes |
| `domain_event` | domain fact contract | domain event / subscriber or handler shell |
| `integration_event` | published language event contract | integration event / inbound subscriber shell |
| `domain_service` | domain decision anchor | domain service skeleton |
| `saga` | long-running coordination anchor | Saga skeleton |

## 常用 Keys

| Key | Type | 说明 |
| --- | --- | --- |
| `tag` | string | 必须是 supported normal tag。 |
| `package` | string | design package segment；除 `domain_event` 外必填；最终 package 还受 layout block 影响。 |
| `name` | string | building block name。 |
| `description` | string | 可读 description。 |
| `aggregates` | string array | 关联的 aggregate names；空数组表示不绑定具体 aggregate。 |
| `fields` | field array | input fields。 |
| `resultFields` | field array | 允许用于 `command`、`query`、`client` 和 `api_payload` 的 result shape；在 `command` 上表达 command outcome。 |
| `eventName` | string | 只允许用于 `domain_event` 和 `integration_event`；`integration_event` 必填。 |
| `persist` | boolean | 只允许用于 `domain_event`。 |
| `artifacts` | artifact array | 部分 tag 用来表达 output family / variant metadata。 |

field item 常见 shape：

```json
{ "name": "contentId", "type": "ContentId", "nullable": false }
```

`nullable` 可省略；不同 tag 会有各自的附加字段。`type` 必须是明确类型名，不能写 `self`。

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

`command.fields` 表达 request payload，`command.resultFields` 表达 command outcome payload。`command.resultFields` 与 `query`、`client`、`api_payload` 的 `resultFields` 使用同一 field shape，并应在 design JSON 解析、canonical 保留和模板渲染中保持一致；省略或声明空数组时仍保持无结果 response 形态。

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
| `client` | 表达 application-facing external capability，不放 adapter protocol details。 |
| `api_payload` | 表达 payload shape，不替代 command/query 边界。 |
| `domain_event` | 表达业务事实，不表达技术 continuation step；`eventName` 可用于 published name，`persist` 只允许在这里使用；field name `entity` 保留。 |
| `integration_event` | 表达 service boundary published language；必须声明 `eventName`。 |
| `domain_service` | 用于跨对象领域判断，不放 HTTP、message、database protocol。 |
| `saga` | 用于可恢复、可补偿或长事务协调；不是每个 callback 的默认形态。 |

## Analysis 片段边界

drawing-board JSON 是 analysis evidence。只有内容满足本页字段集合、tag 约束、field shape 和 artifact selection 规则时，才可以通过 `sources.designJson.files` 作为普通 design JSON 输入。

Value Object 和 enum 使用 type manifests 输入；aggregate unique helper 通过 aggregate generator artifact 配置或 addon artifact 表达。
