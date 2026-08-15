# Design JSON

`design/design.json` 是 ordinary source generation 的 building-block 输入。它声明结构锚点，不承载业务规则实现。

## 文档结构与字段规则

- 根节点必须是 JSON array。
- array 中的每一项必须是 object。
- `tag` 和 `name` 必须是非空 string。
- 除 `domain_event` 外，`package` 必填。
- 公开输入字段为 `tag`、`name`、`package`、`description`、`aggregates`、`fields`、`resultFields`、`operationName`、`eventName`、`persist` 和 `artifacts`。
- field 的 `type` 必须写 formal Kotlin-style type expression，不能写 `self`；nullability 属于 type expression，不再使用独立 `nullable`。
- `domain_event.fields` 是生成事件的完整 payload；省略或留空时生成无 payload 的 marker event。
- `domain_event.aggregates` 只表达归属和放置，不会隐式生成 Aggregate、Entity、Strong ID 或 snapshot 字段。
- Domain Event field 的 resolved semantic type graph 不得直接或嵌套包含 cap4k 已知的 Aggregate/Entity；应显式使用标量、Strong ID、Value Object、enum 或专用不可变 snapshot。
- 当前 Drawing Board generator 的普通 tag 输出满足这些规则，可以由人或 Agent 通过 `sources.designJson.files` 显式注册为普通 design JSON 输入；Aggregate element 结构文件不满足 Design JSON contract。cap4k 不会自动注册或回灌 analysis output。

## 支持的 Normal Tags

| `tag` | 主要用途 | 常见输出方向 |
| --- | --- | --- |
| `command` | 状态变更的 application intent | Command skeleton |
| `query` | read-side observation intent | Query contract / handler surface |
| `capability` | external capability contract | Capability call / handler surface |
| `api_payload` | adapter-facing payload/result shape | payload classes |
| `endpoint` | transport-neutral published Actor operation | contract Request / Response |
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
| `resultFields` | field array | 允许用于 `command`、`query`、`capability`、`api_payload` 和 `endpoint` 的 result shape；在 `command` 上表达 command outcome，在 `endpoint` 上表达 published Response。 |
| `operationName` | string | 只允许用于 `endpoint`，且必须是非空稳定 published operation identity。它不从 Kotlin type、HTTP route 或 RPC service name 推导。 |
| `eventName` | string | 只允许用于 `domain_event` 和 `integration_event`；`integration_event` 以及 `persist: true` 的 `domain_event` 必填。 |
| `persist` | boolean | 只允许用于 `domain_event`。 |
| `artifacts` | artifact array | 部分 tag 用来表达 output family / variant metadata。 |

field item 常见 shape：

```json
{ "name": "snapshots", "type": "List<ContentSnapshot?>?" }
```

`type` 会在 source assembly 之后编译为 canonical structured type tree。支持 builtin、named type、`List<T>`、`Set<T>`、`Map<K, V>`、`Array<T>` 和递归 `?`；不支持 mutable collection、`Collection`、`Iterable`、`Sequence`、primitive array、tuple 或任意 generic type。Primitive array 会在最终 canonical identity 解析后拒绝，因此 `kotlin.IntArray`、指向它的 alias、short-name evidence 及递归容器位置都不能绕过校验；`Array<Int>` 仍受支持，业务类型 `com.acme.IntArray` 不会仅因 simple name 被拒绝。Domain Event 的递归 Entity 检查同样遍历 `Array<T>` element。`nullable` 只能由 type expression 的 `?` 表达，schema 不接受独立 nullability 字段。

`PageData<Item>` 是 query / API result 专用的 page envelope，不属于通用 generic type algebra，也不能用于普通 request/value field。`query` 或 `api-payload` 的 `page` variant 会派生 `pageNum: Int = 1` 和 `pageSize: Int = 10`；作者不能声明根路径为 `pageNum` / `pageSize` 的 field，因此 `pageNum.value`、`pageSize[].value` 同样非法，`filter.pageNum`、`filters[].pageSize` 不冲突。非 page block 可以把这些根字段当作普通业务字段。

## Artifact Selection

省略 `artifacts` 会展开为当前 tag 的默认集合。显式声明时，列表必须非空、包含 primary carrier，并且只能使用当前 tag 支持的 family/variant；secondary artifact 不能脱离 primary：

| Tag | Primary | Optional secondary |
| --- | --- | --- |
| `command` | `command` | none |
| `query` | `query`（可用 `page` variant） | `query-handler` |
| `capability` | `capability` | `capability-handler` |
| `api_payload` | `api-payload`（可用 `page` variant） | none |
| `endpoint` | `endpoint` | none |
| `domain_event` | `domain-event` | `domain-subscriber` |
| `integration_event` | `integration-event`（`inbound` / `outbound`） | `integration-subscriber`，仅 inbound |
| `domain_service` | `domain-service` | none |

`domain_service` 是 metadata-only anchor，非空 `fields` 或 `resultFields` 都会失败。Domain Service 的业务操作由作者实现，Analyzer 不从方法体推断设计输入。

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

`command.fields` 表达 Command payload，`command.resultFields` 表达 Command outcome payload。Command、Query、Capability、API Payload、Endpoint、Domain Event 和 Integration Event 的 structured fields 都进入同一 canonical semantic-value compiler，但各自 role 和输出 family 仍保持独立。省略或声明空 `command.resultFields` 时仍保持无结果 response 形态。

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

## 最小 Endpoint

```json
[
  {
    "tag": "endpoint",
    "package": "booking",
    "name": "CreateBooking",
    "operationName": "booking.create",
    "description": "create a booking",
    "fields": [
      { "name": "customerId", "type": "String" },
      { "name": "startTime", "type": "java.time.Instant" }
    ],
    "resultFields": [
      { "name": "bookingId", "type": "String" }
    ]
  }
]
```

一个 `endpoint` 表达一个 transport-neutral published operation。generator 在 `project.contractModulePath` 指向的依赖叶子模块生成一个 operation object；其中 `Request` 实现轻量 `EndpointRequest<Response>`，`Response` 与 Request 同属该 object，并通过 `OPERATION_NAME` 保留显式 `operationName`。Endpoint contract 不实现 Command、Query 或 Capability marker，也不包含 HTTP/RPC route、client、provider、retry 或 discovery 配置。

本阶段只提供 contract 与 `Mediator.endpoints` dispatch family。Provider 的本地实现和未来 Consumer RPC proxy 都以本进程的 `EndpointHandler<Request, Response>` 接入；业务代码通过 `Mediator.endpoints.send/sendAsync` 调用，不直接调用 Handler/proxy。HTTP/RPC binding 属于后续能力，不由 `endpoint` Design JSON 自动生成。

## 最小 Domain Event

```json
[
  {
    "tag": "domain_event",
    "name": "ContentApproved",
    "description": "content review was approved",
    "aggregates": ["Content"],
    "persist": true,
    "eventName": "content.approved",
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

`integration_event` 需要清晰的 `eventName` 和 fields。`family = "integration-event"` 配合 `variant = "inbound"` / `"outbound"` 表达 artifact variant。事件 payload 生成到 `project.contractModulePath` 指向的 contract module；inbound subscriber shell 仍生成到 application module，只负责把外部事实导向内部 command semantics。generator 不依据 direction 绑定 HTTP、RabbitMQ 或 RocketMQ topology；transport/provider 仍由运行时 Spring YAML 配置按 `eventName` 选择和装配。

## Tag 约束

| Tag | 约束 |
| --- | --- |
| `command` | 不应作为 read shortcut；状态变化放在 command path。 |
| `query` | 不应 mutate aggregate 或修复状态。 |
| `capability` | 表达 application-facing external capability，不放 adapter protocol details。 |
| `api_payload` | 表达 payload shape，不替代 command/query 边界。 |
| `endpoint` | 表达一个显式 published operation；必须声明 `operationName`，不得携带 transport binding 或复用内部 Command/Query/Capability marker。 |
| `domain_event` | 表达业务事实，不表达技术 continuation step；`persist: true` 时必须声明非空 `eventName`，`persist` 只允许在这里使用；`fields` 是完整 payload，`aggregates` 仅表达归属，resolved field graph 不得包含 Aggregate/Entity。 |
| `integration_event` | 表达 service boundary published language；必须声明 `eventName`。 |
| `domain_service` | 用于跨对象领域判断，不放 HTTP、message、database protocol。 |

## Analysis 片段边界

Drawing Board JSON 是 analysis evidence。当前 Drawing Board generator 的普通 `drawing_board_<tag>.json` 文件满足本页字段集合、tag 约束、field shape 和 artifact selection；人或 Agent 可以把选定的普通 tag 文件显式加入 `sources.designJson.files`。这不会自动发生，也不代表任意 analysis JSON 都是合法输入。

`drawing_board_aggregate_elements.json` 是独立的 Aggregate element 结构证据，不带 Design JSON `tag`，不能加入 `sources.designJson.files`。不要为了回灌该文件而把 `repository` 添加为 normal tag；`repository` 不在受支持的 Normal Tags 中。

跨上下文复用 Integration Event 时，可以复制 outbound published-language contract，并显式把 artifact variant 改为 inbound。这个修改是消费上下文的新设计决策；Analyzer 和 Generator 都不会自动改变 event direction。

Value Object 和 enum 使用 type manifests 输入。数据库唯一约束保留为 schema/canonical metadata，用于存储完整性和已支持的 owned relation 基数推断；aggregate generator 不生成唯一性 Query、Handler 或 Validator。
