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

## Entry Schema

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `name` | string | yes | enum type name。 |
| `package` | string | yes | generated enum package。 |
| `items` | item array | yes | enum constants。 |

item shape：

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `value` | int | yes | persisted / serialized numeric value。 |
| `name` | string | yes | enum constant name。 |
| `desc` | string | yes | human-readable description。 |

## Minimal Example

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

`ReleasePolicy` remains in the Content aggregate local enum package。Manifest 没有单独的 `shared` / `local` switch；不要从不存在的额外 flag 推断 ownership。

## Generation Notes

| Rule | 说明 |
| --- | --- |
| Configure location | 使用 `types.enumManifest`，不是 `sources.enumManifest`。 |
| Type registry | enum manifest entries 不需要 matching `types.registryFile` entries。 |
| Schema binding | DB `@T=<EnumName>` 可以把 schema fields 绑定到 enum manifest types。 |
| Business boundary | enum 让有限选项保持类型化；complex policy 仍属于 domain/application logic。 |
| Enum translation | translation output 是 addon-owned，不是 core aggregate DSL toggle。 |

## Common Checks

- `items` 必须明确并保持稳定。
- 数据存在后，不应随意改变 `value`。
- `name` 应保持 domain language constant，而不是 transport label。
- 不要向 enum manifest 添加已移除的 translation flags；addon-owned translation artifacts 通过 addon 安装。
