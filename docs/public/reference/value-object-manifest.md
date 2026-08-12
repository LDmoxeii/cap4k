# Value Object Manifest

Value Object 输入通过 `types.valueObjectManifest` 配置。

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
| `aggregates` | string array | no | 省略或为空表示 shared；non-empty list 表示 aggregate-owned，最多一个 owner。 |
| `persistence` | object | no | 省略表示纯 Value Object；当前只支持显式 `{ "kind": "json" }` 投影。 |
| `description` | string | no | description metadata。 |
| `fields` | field array | no | Value Object fields。 |

field item：

| Key | Type | Required |
| --- | --- | --- |
| `name` | string | yes |
| `type` | string | yes；使用 Kotlin-style type expression，nullability 写在类型中的 `?`。 |
| `defaultValue` | string | no |

`type` 会在 canonical compilation 阶段解析为 structured type tree。支持 builtin、named type、`List<T>`、`Set<T>`、`Map<K, V>`，并允许在任意层递归使用 `?`，例如 `List<Money?>?`。不支持 mutable collection、`Collection`、`Iterable`、`Sequence`、array、tuple 或任意 generic type。

## Shared 形态

```json
[
  {
    "name": "MoneyAmount",
    "package": "com.acme.demo.domain.values",
    "fields": [
      { "name": "amount", "type": "java.math.BigDecimal" },
      { "name": "currency", "type": "String" }
    ]
  }
]
```

`aggregates` 省略或为 `[]` 时表示 shared；manifest 会按 shared name 检查重复。这个声明没有 `persistence`，因此只生成纯 Value Object，不生成 persistence converter。

## Aggregate-Owned 形态

```json
[
  {
    "name": "MediaProcessingResultSnapshot",
    "aggregates": ["MediaProcessingTask"],
    "package": "com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.values",
    "persistence": { "kind": "json" },
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

`MediaProcessingResultSnapshot` 显式选择 JSON persistence projection。Value Object 本身仍是纯值；独立的 build-owned `MediaProcessingResultSnapshotJsonAttributeConverter` 负责 persistence conversion。

JSON projection 只把整个 Value Object 映射为一个数据库 attribute。它不会把 `amount`、`currency` 等内部成员展开成独立列，也不会生成可移植的 nested Criteria/Schema path。需要按内部成员稳定查询时，应先显式建模为普通列/关联；relational 或 embedded Value Object projection 不在当前能力内，不能把省略 `persistence` 或选择 JSON 理解为已经获得结构化查询能力。

## 归属与存储方式

- `aggregates` 可省略。
- 省略 `aggregates` 或写成 `aggregates: []` 表示 shared。
- `aggregates` 最多只能声明一个 owner。
- `persistence` 可省略；省略表示不生成 persistence projection。
- 当前唯一支持的 projection 是 `persistence: { "kind": "json" }`。
- persistence projection 只通过 `persistence` 字段声明；schema 不接受 `storage`。
- shared Value Object name 不能重复。
- 同一个 owner 下的 Value Object name 不能重复。
- 归属通过 `aggregates` 表达。

## 生成输出说明

| Output | 说明 |
| --- | --- |
| Value Object class | checked-in pure-value source，通常进入 domain package；existing file 固定 SKIP。 |
| JSON converter | 显式 `persistence.kind = "json"` 时生成独立 `<ValueObjectName>JsonAttributeConverter`，属于 build-owned generated source。 |

## 常见检查

- 配置 value-object manifest 时，`types.valueObjectManifest.files` 不能为空。
- 同一个 shared Value Object name 不能重复。
- 同一个 aggregate owner 下的 Value Object name 不能重复。
- `aggregates` 最多声明一个 owner。
- nullability 必须写在 `type` 中，不能再声明 `nullable`。
- 需要 JSON persistence 时显式声明 `persistence.kind = "json"`；否则省略 `persistence`。
- 显式 default 必须能被 semantic compiler 安全投影；不支持的表达式会按 field path 失败。
