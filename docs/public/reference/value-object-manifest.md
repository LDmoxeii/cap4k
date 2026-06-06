# Value Object Manifest

Value Object 输入通过 `types.valueObjectManifest` 配置，不通过 normal `design.json` tag。

```kotlin
cap4k {
    types {
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
    }
}
```

参考项目示例文件是 `design/value-objects.json`。

manifest root 必须是 JSON array；array 中每一项声明一个 Value Object type。

## Entry 结构

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `name` | string | yes | Value Object type name。 |
| `package` | string | yes | generated class package。 |
| `aggregates` | string array | no | 省略或为空表示 shared；non-empty list 表示 aggregate-owned，当前最多一个 owner。 |
| `storage` | string | no | 当前支持 `json`；省略时按 `json` 处理。 |
| `description` | string | no | description metadata。 |
| `fields` | field array | no | Value Object fields。 |

field item：

| Key | Type | Required |
| --- | --- | --- |
| `name` | string | yes |
| `type` | string | yes |
| `nullable` | boolean | no |
| `defaultValue` | string | no |

## Shared 形态

```json
[
  {
    "name": "MoneyAmount",
    "package": "com.acme.demo.domain.values",
    "storage": "json",
    "fields": [
      { "name": "amount", "type": "java.math.BigDecimal" },
      { "name": "currency", "type": "String" }
    ]
  }
]
```

`aggregates` 省略或为 `[]` 时表示 shared；manifest 会按 shared name 检查重复。

## Aggregate-Owned 形态

```json
[
  {
    "name": "MediaProcessingResultSnapshot",
    "aggregates": ["MediaProcessingTask"],
    "package": "com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.values",
    "storage": "json",
    "description": "media processing result snapshot",
    "fields": [
      { "name": "mediaProcessingTaskId", "type": "MediaProcessingTaskId" },
      { "name": "contentId", "type": "ContentId" },
      { "name": "externalTaskId", "type": "String" },
      { "name": "assetLocation", "type": "String" }
    ]
  }
]
```

`MediaProcessingResultSnapshot` 是 JSON-backed Value Object。参考项目中它持久化在 `media_processing_task.result_snapshot`，converter 生成在 value object class 中。

## 归属、存储方式与已移除字段

- `aggregates` 可省略。
- 省略 `aggregates` 或写成 `aggregates: []` 表示 shared。
- 当前 `aggregates` 最多只能声明一个 owner。
- `storage` 当前只支持 `json`；省略 `storage` 时按 `json` 处理。
- shared Value Object name 不能重复。
- 同一个 owner 下的 Value Object name 不能重复。
- `scope` 和 `aggregate` 已移除；使用 `aggregates`。

## 生成输出说明

| Output | 说明 |
| --- | --- |
| Value Object class | checked-in source，通常进入 domain package。 |
| JSON converter | `storage = "json"` 时生成，用于 persistence conversion。 |
| Building block metadata | generated class 可包含 `@BuildingBlock(tag = "value_object", family = "value-object")`。 |

`@BuildingBlock(tag = "value_object", family = "value-object")` 是生成结果上的 metadata。不要在 normal `design.json` 中写 `tag = "value_object"`。

## 常见检查

- 配置 value-object manifest 时，`types.valueObjectManifest.files` 不能为空。
- 同一个 shared Value Object name 不能重复。
- 同一个 aggregate owner 下的 Value Object name 不能重复。
- `aggregates` 当前最多声明一个 owner。
- `storage` 当前只使用 `json`。
- 不要添加已移除的 `scope` 或 `aggregate` 字段；使用 `aggregates`。
