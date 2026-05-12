# Value Object

对应示例：

- [内容发布示例：Value Object](../examples/content-publication-value-object.zh-CN.md)

## 什么时候需要它

- `Content` 或 `MediaProcessingTask` 里有一段值语义，已经不适合继续裸用 `String`、`Int` 或松散字段组合表达。
- 这段值需要在创建时统一校验、归一化或比较，例如内容标题、外部媒体任务号、处理结果摘要。
- 你需要让作者和审阅者一眼看出“这不是随便一个字符串”，但它又不是新的聚合根。

在共享教学项目里，`ContentTitle`、`MediaAssetFingerprint`、`MediaProcessingResultSnapshot` 这类都可能是值对象候选；而 `ContentStatus`、`ReviewStatus`、`MediaProcessingStatus` 这类状态值，默认更接近枚举。

## 为什么默认路径不够

默认路径鼓励先用最轻的表达：

- 状态枚举就先用 enum。
- 单一简单标量就先用 primitive 或薄包装。
- 只有当多个字段共同组成一个业务值，并且需要一起校验、比较、传递时，才升级成复合值对象。

如果把所有字段都提前升成值对象，`Content` 和 `MediaProcessingTask` 会很快被“名词碎片”淹没，作者反而看不出主业务动作。反过来，如果明明已经需要统一值语义，却继续裸用原始类型，callback 主路径和 polling 备用路径就容易各自拼装出不同的内部值解释。

还要特别区分“值对象定义”和“持久化承载方式”。例如把 `MediaProcessingResultSnapshot` 存到一个 JSON 字段里，只说明数据库用 JSON 承载它；JSON 字段本身不是值对象定义，值对象的定义仍然来自领域语义、构造约束和等值规则。

## 持久化承载方式

当前作者应先把值对象分成“领域值定义”和“数据库承载形态”两件事：

- inline column：少量字段可直接展开到父聚合表，字段规则仍由手写值对象或聚合行为保证。
- JSON column：适合 `MediaProcessingResultSnapshot` 这类聚合内部快照；DDL 用 `@T=MediaProcessingResultSnapshot`，`types.registryFile` 指向手写类型与 converter，生成器只把字段映射回聚合。
- separate table / `@VO`：更重的 table-backed 形态，只在确实需要独立行、外键绑定或可定位持久化值时考虑，不是默认 Value Object 路径。

不要把 `ValueObject<ID>.hash()` 当成所有值对象的统一合同。它更接近 table-backed / 可持久化定位值对象的能力；普通业务值对象可以只是手写 Kotlin 类型，靠构造、校验、归一化和 equals 表达值语义。

## 推荐形态

- 先分三类看：
  - enum：表达固定离散状态，例如 `MediaProcessingStatus`。
  - primitive value：表达单一但有明确业务语义的值，例如 `ContentTitle`、`ExternalMediaTaskId`。
  - composite value object：表达多个字段共同组成的不可分业务值，例如 `MediaProcessingResultSnapshot`，里面可以包含转码输出、加密结果、失败原因摘要等。
- 对 `ExternalMediaTaskId` 这类例子，先用一个明确分界：
  - 如果你关心的是值语义更丰富，例如格式校验、归一化、从不同 payload 统一解析、或多个字段共同决定等值关系，这是 value-object 思路。
  - 如果你关心的核心只是别把 `ContentId`、`MediaProcessingTaskId`、`ExternalMediaTaskId` 混用，那优先看 [Strong ID](strong-id.zh-CN.md)。
- 让值对象服务于聚合行为，而不是替代聚合行为。比如 `Content` 负责“是否允许发布”，值对象只负责把输入值保持成合法、可比较、可复用的业务值。
- callback 主路径和 polling 备用路径都先在 adapter / application 层完成外部协议转换，再统一构造同一种值对象进入 `MediaProcessingTask`。
- 如果数据库需要 JSON、嵌入字段或列展开，那是持久化层决定如何承载；领域层仍然只关心这个值对象长什么业务样。当前 JSON-backed 形态通常由 `@T` + `types.registryFile` + converter 接入，值对象类本身仍是作者手写主面。

## 常见误用

- 只是因为“类型多一点更高级”，就把每个 `String` 都包装成值对象，结果作者阅读成本暴涨。
- 本该是枚举的生命周期状态，硬写成复合值对象，掩盖了 `Content` 或 `MediaProcessingTask` 的状态机。
- 把一段 JSON 列结构直接等同于值对象定义，导致一改存储结构就误以为业务概念也变了。
- callback payload 用一种字段组合，polling payload 用另一种字段组合，分别直写聚合，最后同一个“处理结果”在内部没有统一值语义。
- 在查询层临时拼出一个“像值对象的 DTO”，再反向拿它当写模型真相。

## 审计检查点

- 当前对象到底是 enum、primitive value，还是 composite value object，团队能否说清楚，而不是统称“值对象”。
- 这段值语义是否真的被多个业务动作共享，或者真的需要独立校验与等值规则。
- `Content` 与 `MediaProcessingTask` 的聚合行为是否仍然清晰，没有被值对象碎片替代。
- JSON 字段是否只被当作 persistence carrier，而不是领域定义本身。
- `@VO` 是否只在确实需要 table-backed 值对象时使用，而不是被当成“用了值对象就必须建表”的默认动作。
- 如果作者把 `ExternalMediaTaskId` 归到值对象，审阅者是否能看到明确的值语义、归一化或等值规则，而不只是 ID 防混淆诉求。
- callback 主路径与 polling 备用路径进入内部后，是否构造成同一种值表达。
