# Code Generation Guide

> 这页定义项目作者默认应如何使用 `cap4kPlan` 与 `cap4kGenerate`，以及何时必须读 `build/cap4k/plan.json`。

## 何时先跑 `cap4kPlan`

以下场景先跑 `cap4kPlan`，不要直接 `cap4kGenerate`：

- 新项目第一次准备生成业务源码。
- `project { }`、`sources { }`、`generators { }`、`layout { }`、`templates { }` 改过之后。
- `design.json`、数据库 schema、KSP metadata 或共享枚举清单刚改完，你需要确认新增、删除或迁移的产物。
- 你不确定某个产物会落到哪个模块、哪个输出根，或到底属于 generated source 还是 checked-in source。

`cap4kPlan` 的硬边界很明确：

- 只规划 design / aggregate 这条源码生成链路。
- 会写 `build/cap4k/plan.json`。
- 不落真正源码产物。
- 不替代 `cap4kAnalysisPlan`，也不处理 bootstrap。

## 何时再跑 `cap4kGenerate`

只有在 `build/cap4k/plan.json` 已经回答清楚下面这些问题后，才进入 `cap4kGenerate`：

- 需要的 generator family 已经出现。
- 目标模块与路径符合预期。
- 你理解哪些文件会进入 `src/main/kotlin`，哪些会进入 `build/generated/cap4k/main/kotlin`。
- 没有把 analysis 输出误当成主源码生成。

`cap4kGenerate` 的边界同样要守住：

- 它只执行 `cap4kPlan` 对应的源码生成链路。
- 它不是 analysis 导出任务。
- 它不是 bootstrap 任务。
- 它不是“先跑一次看看再说”的试探入口；试探应该留在 `cap4kPlan` 和 `plan.json` 阅读阶段完成。

## 如何阅读 `plan.json`

最先看的是 `items[]`。每一项至少要回答四个问题：

| 字段 | 你要问的问题 |
| --- | --- |
| `generatorId` | 这是谁生成的？是 design 家族还是 aggregate 家族？ |
| `templateId` | 它属于哪类模板与产物家族？ |
| `outputPath` | 这次会把文件写到哪里？ |
| `conflictPolicy` | 遇到已有文件时，行为是跳过、覆盖还是失败？ |

当你在做“归属诊断”时，再重点看这两个字段：

| 字段 | 用法 |
| --- | --- |
| `items[].outputKind` | 区分 `CHECKED_IN_SOURCE`、`GENERATED_SOURCE`、`OUTPUT_ARTIFACT` |
| `items[].resolvedOutputRoot` | 看这项产物最终归属于哪个输出根，而不是只盯着相对路径猜测 |

作者最常见的读取方式：

- `GENERATED_SOURCE` + `build/generated/cap4k/main/kotlin`：这是每次生成都可能覆盖的构建输出目录，不要靠手改去维持业务真相。
- `CHECKED_IN_SOURCE` + `src/main/kotlin`：这是写进版本库源码目录的计划产物；是否可改取决于它是不是明确留给作者补充的文件，不能只看目录位置。
- `OUTPUT_ARTIFACT`：这是非源码产物，不要把它当成业务代码入口。

一个关键例子是 aggregate 默认行为骨架：`aggregate/behavior.kt.peb` 会以 checked-in source 形式进入 `src/main/kotlin/.../<AggregateRootName>Behavior.kt`。这类文件是明确留给作者补业务行为的文件；而大量 aggregate 主体骨架则会通过 `GENERATED_SOURCE` 进入模块本地 `build/generated/cap4k/main/kotlin`。如果你不读 `outputKind` 与 `resolvedOutputRoot`，很容易把“输出根位置”误判成“作者是否可以直接改”。

## `CHECKED_IN_SOURCE` 落到 `src/main/kotlin` 时怎么判断

短答案：不能因为文件落在 `src/main/kotlin`，就默认把它当成普通手写文件。只要它出现在 `plan.json` 里，就先把它当成计划产物，再按下面这张表判断。

| 情况 | 可以直接编辑吗 | 项目特有逻辑应该放哪里 |
| --- | --- | --- |
| `plan.json` 中出现，且 `outputKind=GENERATED_SOURCE` | 不可以 | 放到手写文件里，例如 application handler、adapter 转换文件，或文档明确留给作者补充的行为文件 |
| `plan.json` 中出现，且 `outputKind=CHECKED_IN_SOURCE`，并且该文件族被文档明确说明是作者补充点，例如 `*Behavior.kt` | 可以，但只补这类文件被设计出来要承载的内容 | 只在该文件里补聚合行为；application 编排、查询组装、外部协议转换仍回到手写的 application / adapter 文件 |
| `plan.json` 中出现，且 `outputKind=CHECKED_IN_SOURCE`，但你看不出它是不是明确留给作者补充的文件 | 先不要改 | 先把项目逻辑放回手写文件；等文档或模板家族明确它是作者补充点后再决定 |
| 文件不在 `plan.json` 中，只是普通 `src/main/kotlin` 文件 | 可以 | 这是正常手写文件，按层次责任放业务逻辑 |

直接规则：

- Do：先看 `outputKind`、`templateId`，再决定文件是不是作者补充点。
- Do：把项目特有编排、查询组装、外部协议转换放在手写的 application / adapter 文件里。
- Don't：因为文件出现在 `src/main/kotlin`，就往计划产物里塞 controller 流程、CLI 调用、查询拼装或跨层逻辑。

什么时候必须读 `plan.json`：

- 第一次跑 `cap4kPlan` 后。
- 任何会改变输出根或模块落点的 DSL 变更后。
- 任何会改变 aggregate 族产物归属的排查场景里。
- 审阅前，你要证明这次生成没有把手写主面和生成主面混淆时。

## 生成文件和手写文件怎么配合

- 先让生成器产出结构骨架，再把项目特有规则补回手写主面。
- application 的编排、adapter 的协议转换、查询组装，不该靠去改那些会被计划再次生成的文件来实现。
- aggregate 家族里，默认会同时存在“可能被重复生成覆盖的文件”和“明确留给作者补行为的文件”。前者不要承载项目特有逻辑，后者也只承载它被设计出来要承载的那一小段责任。
- 当 `plan.json` 里某个文件持续作为计划产物出现时，不要因为它落在 `src/main/kotlin` 就默认把它当成随意手写文件；先回到 [生成 / 手写边界](../generation-boundaries.zh-CN.md) 做判断。

## 常见生成误用

- 不看 `cap4kPlan`，直接跑 `cap4kGenerate`。
- 把 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 当成普通源码生成入口。
- 看见 `src/main/kotlin` 就认定这是纯手写主面，不再检查 `plan.json`。
- 看到 `build/generated/cap4k/main/kotlin` 里的文件后，用手工移动或复制来“修正”所有权。
- 没有默认模板确实不够的证据，就直接进入 `overrideDirs` 覆盖。

## 最低验证

- 跑 `./gradlew cap4kPlan`。
- 打开 `build/cap4k/plan.json`，至少确认目标 family、`outputKind`、`resolvedOutputRoot` 与 `conflictPolicy`。
- 跑 `./gradlew cap4kGenerate`。
- 确认计划中的源码已落到预期根目录，且手写主面没有因为误判所有权而被你塞进会被下次生成覆盖的产物里。
