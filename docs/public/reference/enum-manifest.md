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

参考项目示例文件是 `design/enums.json`。manifest root 必须是 JSON array；array 中每一项声明一个 enum type。

## Entry 结构

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `name` | string | yes | enum type name。 |
| `package` | string | yes | enum package。 |
| `aggregates` | string array | no | 省略或为空表示 shared；non-empty list 表示 aggregate-owned，最多一个 owner。 |
| `fields` | field array | no | 有序的自定义 constructor property schema；省略时保持原有基础 enum shape。 |
| `items` | item array | yes | enum constants。 |

每个 `fields` declaration 必须且只能包含：

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `name` | string | yes | 自定义 property name；不得与 `value`、`name`、`description`、`desc`、`Converter`、`valueOfOrNull` 等保留成员冲突。 |
| `type` | string | yes | canonical semantic type expression；可用 `?` 显式声明 nullable。 |

`fields` 的数组顺序就是生成的 constructor property 顺序。它只定义属性名、类型和顺序，**不支持默认值**，也不会从 item JSON 猜测 schema。

每个 item 固定包含：

| Key | Type | Required | 说明 |
| --- | --- | --- | --- |
| `value` | int | yes | persisted / serialized numeric value，必须在 Kotlin `Int` 范围内且在同一 enum 内唯一。 |
| `name` | string | yes | enum constant name，在同一 enum 内唯一。 |
| `desc` | string | yes | human-readable description。 |
| `<field name>` | typed literal | yes | 必须为每个 declared field 显式提供；nullable field 也必须显式写 `null`。 |

item 不能提供未在 `fields` 中声明的自定义属性。缺失、未知或类型不匹配的属性会使输入校验失败。

## 支持的自定义属性类型

首版支持以下 canonical scalar types，并支持其 nullable form：

- `String`
- `Boolean`
- `Byte`
- `Short`
- `Int`
- `Long`
- `Float`
- `Double`
- `BigInteger`
- `BigDecimal`
- canonical Business Enum type；item 值以 JSON string 写入被引用 enum 的精确常量名，canonical compilation 会在 planning 前解析并校验

不支持集合、`Map`、Value Object、任意对象构造或 raw Kotlin expression。enum reference 必须能按 canonical enum identity 明确解析；不能依赖有歧义的短名称猜测。

## 示例

```json
[
  {
    "name": "ReleasePolicy",
    "package": "com.only4.cap4k.reference.contentstudio.domain.aggregates.content.enums",
    "fields": [
      { "name": "group", "type": "String" },
      { "name": "terminal", "type": "Boolean" },
      { "name": "note", "type": "String?" }
    ],
    "items": [
      {
        "value": 0,
        "name": "IMMEDIATE",
        "desc": "Immediate",
        "group": "PUBLIC",
        "terminal": true,
        "note": null
      },
      {
        "value": 2,
        "name": "PAID",
        "desc": "Paid",
        "group": "MONETIZED",
        "terminal": false,
        "note": "Requires entitlement"
      }
    ]
  }
]
```

没有 `fields` 的既有 manifest 仍使用 `value` / `name` / `desc` item shape，并保持生成类型的基础 API。

## 归属与重复规则

- `aggregates` 可省略。
- 省略 `aggregates` 或写成 `aggregates: []` 表示 shared。
- `aggregates` 最多只能声明一个 owner。
- shared enum name 不能重复。
- 同一个 owner 下的 enum name 不能重复。
- manifest 没有单独的 `shared` / `local` switch；不要从不存在的额外 flag 推断 ownership。

## 首次物化与后续演进

manifest-authored shared 和 aggregate-owned Business Enum 都由 `cap4kPlan` / `cap4kGenerate` 作为 ordinary authoring source 处理：

- Kotlin 文件首次物化到 domain module 的 `src/main/kotlin`。
- plan item 使用 `CHECKED_IN_SOURCE` 和 `SKIP`。
- 文件进入版本控制后，项目作者可以在 enum class 中增加领域方法和其他手写逻辑。
- 后续 manifest 变化不会覆盖既有文件；需要重物化时，应显式删除目标文件、重新生成并审查版本控制 diff。
- Business Enum artifact 不属于 build-owned generated source；`cap4kGenerateSources` 可以读取 enum manifest 作为其他 generated artifacts 的 canonical type input，但不会物化或覆盖 enum class。

生成类型继续提供 `value: Int`、`description: String`、`valueOfOrNull(Int?)` 和 nested JPA `Converter` API；自定义 properties 排在 `value` 与 `description` 之后，并按 `fields` 顺序生成。

## 生成说明

| Rule | 说明 |
| --- | --- |
| Configure location | 使用 `types.enumManifest`。 |
| Type registry | enum manifest entries 不需要 matching `types.registryFile` entries。 |
| Schema binding | DB `@Type=<EnumName>` 可以把 schema fields 绑定到 enum manifest types。 |
| Business boundary | enum 让有限选项保持类型化；complex policy 仍属于 domain/application logic。 |

## 常见检查

- `items`、persisted `value` 和 `fields` 顺序必须明确并保持稳定。
- 数据存在后，不应随意改变 `value`。
- `name` 应保持 domain language constant，而不是 transport label。
- nullable 不等于 optional；nullable item value 仍必须显式写 `null`。
- addon-owned translation artifacts 通过 addon 安装和配置。
