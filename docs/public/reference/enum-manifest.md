# Enum Manifest

Business Enum 输入通过 `types.enumManifest` 配置。

```kotlin
cap4k {
    types {
        enumManifest {
            files.from("design/enums.json")
        }
    }
}
```

参考项目示例文件是 `design/enums.json`。

manifest root 必须是 JSON array；array 中每一项声明一个 enum type。

## Entry 结构

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `name` | string | yes | enum type name。 |
| `package` | string | yes | generated enum package。 |
| `aggregates` | string array | no | 省略或为空表示 shared；non-empty list 表示 aggregate-owned，最多一个 owner。 |
| `items` | item array | yes | enum constants。 |

item shape：

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `value` | int | yes | persisted / serialized numeric value。 |
| `name` | string | yes | enum constant name。 |
| `desc` | string | yes | human-readable description。 |

## 最小示例

```json
[
  {
    "name": "ReleasePolicy",
    "package": "com.only4.cap4k.reference.contentstudio.domain.aggregates.content.enums",
    "items": [
      { "value": 0, "name": "IMMEDIATE", "desc": "Immediate" },
      { "value": 2, "name": "PAID", "desc": "Paid" }
    ]
  }
]
```

参考项目还定义：

```json
{
  "name": "MediaProcessingResultStatus",
  "package": "com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.enums",
  "items": [
    { "value": 0, "name": "SUCCEEDED", "desc": "Succeeded" },
    { "value": 1, "name": "FAILED", "desc": "Failed" }
  ]
}
```

`ReleasePolicy` 保持在 Content aggregate local enum package。manifest 没有单独的 `shared` / `local` switch；不要从不存在的额外 flag 推断 ownership。

## 归属与重复规则

- `aggregates` 可省略。
- 省略 `aggregates` 或写成 `aggregates: []` 表示 shared。
- `aggregates` 最多只能声明一个 owner。
- shared enum name 不能重复。
- 同一个 owner 下的 enum name 不能重复。

## 生成说明

| Rule | 说明 |
| --- | --- |
| Configure location | 使用 `types.enumManifest`。 |
| Type registry | enum manifest entries 不需要 matching `types.registryFile` entries。 |
| Schema binding | DB `@T=<EnumName>` 可以把 schema fields 绑定到 enum manifest types。 |
| Business boundary | enum 让有限选项保持类型化；complex policy 仍属于 domain/application logic。 |

## 常见检查

- `items` 必须明确并保持稳定。
- 数据存在后，不应随意改变 `value`。
- `name` 应保持 domain language constant，而不是 transport label。
- addon-owned translation artifacts 通过 addon 安装和配置。
