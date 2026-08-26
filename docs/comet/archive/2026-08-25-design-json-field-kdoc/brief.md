# Brief

## Scope

本 change 负责让 `design.json` 中 `fields` 与 `resultFields` 的字段级 `description` 贯穿 cap4k 的 Design JSON source、canonical semantic model、设计生成器 render model 和默认 Pebble 模板，最终生成 Kotlin 字段 KDoc。

## Source coverage

- 用户需求：`design.json` 中每个 field 的 `description` 最终生成 Kotlin 字段 KDoc。
- 已确认流程：先结束 reference-payment 的格式 change，再独立修改 cap4k，最后在 reference-payment 重新绑定并重跑最新 cap4k。
- 已确认非目标：Analyzer 不解析 KDoc；本 change 不把普通源码注释反向变成 Analyzer 输入，也不改变 `@DesignBlockMetadata` 的读取契约。

## Decisions

- 字段级 `description` 是可选字符串；缺失或空白时不输出字段 KDoc。
- `fields` 和 `resultFields` 均支持字段 KDoc；嵌套设计值字段也沿用同一规则。
- KDoc 文本复用现有危险字符清洗规则，至少保证 `*/` 不会破坏生成源码。
- 只改变描述投影，不改变字段名称、类型、默认值、顺序、导入和 Analyzer 语义。
- 旧有不带字段 `description` 的 design JSON 保持兼容，生成结果不额外出现空 KDoc。

## Non-goals

- 不要求 Analyzer 解析 KDoc 或普通源码注释。
- 不修改 DB schema comment 的既有解析和 aggregate schema 注释路径。
- 不改变 entry-level `description`、`@DesignBlockMetadata` 或现有类型级 KDoc 行为。

## Open questions

无。[resolved]
