# Design JSON

`design/design.json` 是 ordinary source generation 的 building-block 输入。它声明结构锚点，不承载业务规则实现。

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

Normal `design.json` 不支持 `validator` tag，也不支持 `value_object` tag。Value Object 和 enum 通过 type manifests 输入。

## 常用 Keys

| Key | Type | 说明 |
| --- | --- | --- |
| `tag` | string | 必须是 supported normal tag。 |
| `package` | string | design package segment；最终 package 还受 layout block 影响。 |
| `name` | string | building block name。 |
| `description` | string | 可读 description。 |
| `aggregates` | string array | 关联的 aggregate names；空数组表示不绑定具体 aggregate。 |
| `fields` | field array | input fields。 |
| `resultFields` | field array | read queries、API payloads、clients 等可使用的 result shape。 |
| `eventName` | string | event-related entries 的 published name，尤其是 `integration_event`。 |
| `artifacts` | artifact array | 部分 tag 用来表达 output family / variant metadata。 |

field item 常见 shape：

```json
{ "name": "contentId", "type": "ContentId", "nullable": false }
```

`nullable` 可省略；不同 tag 会有各自的附加字段。

## 最小 Command

```json
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
```

`command` 表达写入意图。读取其他 aggregate 或 external fact 可以用于 zero-trust validation，但写入 ownership 仍应收敛到目标 aggregate 和 application command boundary。

## 最小 Query

```json
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
```

`query` 只观察。它不修复 write model，不推进状态。

## 最小 Integration Event

```json
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
```

`integration_event` 需要清晰的 `eventName` 和 fields。`family = "integration-event"` 配合 `variant = "inbound"` / `"outbound"` 表达 artifact variant；inbound subscriber shell 仍只负责把外部事实导向内部 command semantics。

## Tag 约束

| Tag | 约束 |
| --- | --- |
| `command` | 不应作为 read shortcut；状态变化放在 command path。 |
| `query` | 不应 mutate aggregate 或修复状态。 |
| `client` | 表达 application-facing external capability，不放 adapter protocol details。 |
| `api_payload` | 表达 payload shape，不替代 command/query 边界。 |
| `domain_event` | 表达业务事实，不表达技术 continuation step。 |
| `integration_event` | 表达 service boundary published language；`eventName` 是跨边界名称。 |
| `domain_service` | 用于跨对象领域判断，不放 HTTP、message、database protocol。 |
| `saga` | 用于可恢复、可补偿或长事务协调；不是每个 callback 的默认形态。 |

## 排除的 Normal Tags

| 不支持的 normal tag | 正确入口 |
| --- | --- |
| `value_object` | 通过 [Value Object Manifest](value-object-manifest.md) 和 `types.valueObjectManifest` 输入 |
| `validator` | aggregate unique helper 或 addon artifact；不是 normal `design.json` tag |

生成出的 Value Object class 可能包含 `@BuildingBlock(tag = "value_object", family = "value-object")`，这是输出元数据，不是 normal design input。
