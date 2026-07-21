# Value Object

Value Object 是不可变的值语义对象。它没有独立身份，相等性由值决定，适合表达金额、范围、快照、配置片段、结构化状态和类型安全的组合值。Value Object 的价值不在于减少字段数量，而在于把一组必须一起理解、一起校验、一起持久化的业务值变成一个明确概念。

当字段组合需要一致校验、值相等判断、不可变快照或 JSON-backed 持久化时，应考虑 Value Object。Strong ID 是 specialized value type，也可以按 Value Object style 使用，用来避免裸 `String` / `Long` 在不同业务身份之间漂移。Business Enum 是相邻的 value-type input path，通过 `types.enumManifest` 表达有限业务选项，但它不是 Value Object 实现；精确 enum schema 放在 [enum-manifest.md](../../reference/enum-manifest.md)。

在 cap4k 中，Value Object 位于 domain layer，通常被 Aggregate 或 Entity 持有。`types.valueObjectManifest` 是 Value Object 生成入口，可表达 shared 或 aggregate-owned 的值对象、字段、包路径和 JSON-backed converter。generator 可以生成 data class、converter 和 building-block metadata；值对象的业务命名、字段语义、校验规则、默认值取舍和使用边界仍需要人工设计。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，`MediaProcessingResultSnapshot` 由 `types.valueObjectManifest` 生成，是一个 JSON-backed 值对象，并通过 `media_processing_task.result_snapshot` 持久化。它是阅读“复合结果快照如何成为一个领域值”的推荐锚点。

设计边界是值语义。Value Object 不应承担跨聚合流程，不应持有 Repository，不应调用外部能力，也不应为了复用而包含无关字段。常见误用包括继续在 command 和 entity 中散落裸字符串标识、把可变生命周期对象误建成 Value Object、把 adapter payload 当作领域值直接复用、或把 generated building-block metadata 误认为 normal `design.json` input tag。

判断 Value Object 是否用对时，可以看它是否不可变、是否按值比较、字段是否构成一个业务概念。Strong ID 应消除裸 ID 漂移；Business Enum 应通过 `types.enumManifest` 走相邻类型输入路径；JSON-backed converter 只承担持久化转换，生成代码和手写语义边界保持清晰。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者区分 Value Object、Strong ID、Business Enum 类型输入和 JSON-backed composite value 的关系。
Type: concept map
Prompt: Draw a cap4k value type concept map. Put Value Object in the center, with Composite Value Object and JSON-backed Value Object as implementations. Show Strong ID as a specialized value type / value-object style, and Business Enum as a related type input path through types.enumManifest, not a Value Object implementation. Show Aggregate or Entity holding these values, and types.valueObjectManifest / types.enumManifest as generator inputs. Use Chinese labels and preserve English identifiers.
Must show: immutable value semantics, equality by value, Strong ID typed identifiers, Business Enum as related enum input, JSON-backed converter, MediaProcessingResultSnapshot anchor
Must avoid: implying Value Object has independent lifecycle, implying generated building-block metadata belongs in normal design.json input, implying generated metadata is handwritten business logic
Alt text after insertion: Value Object 概念地图，展示 Strong ID、Business Enum 类型输入、复合值对象和 JSON-backed 值对象的关系。
-->
