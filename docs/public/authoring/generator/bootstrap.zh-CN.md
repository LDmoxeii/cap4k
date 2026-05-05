# Bootstrap Guide

> bootstrap 解决的是“把受管宿主根和模块骨架准备好”，不是“把业务代码生成完”。

## 什么时候使用 bootstrap

- 新项目还没有 cap4k 受管宿主根时，先用 bootstrap 建立根与模块骨架。
- 你要演示或预览默认多模块布局，但还不想写进真实宿主根时，用 `PREVIEW_SUBTREE`。
- 你已经有一个 cap4k 受管项目，只是在更新 `design.json`、数据库 schema、KSP 元数据或 aggregate 配置时，不要把 bootstrap 当默认起手式，直接进入 `cap4kPlan`。

当前作者视角下，bootstrap 只是一条很窄的骨架任务族：

- `cap4kBootstrapPlan`：写 `build/cap4k/bootstrap-plan.json`，先预览根和模块骨架。
- `cap4kBootstrap`：按 bootstrap 配置真正落骨架。

## bootstrap 生成什么

- 根 `settings.gradle.kts`
- 根 `build.gradle.kts` 中的 bootstrap managed section
- `domain` / `application` / `adapter` / `start` 模块的 `build.gradle.kts`
- `start` 模块里的 `StartApplication.kt`
- `slots { }` 指向的固定槽位内容

模式差异只有一件事：

- `IN_PLACE`：写向当前受管宿主根
- `PREVIEW_SUBTREE`：写向 `previewDir/` 下面的预览子树

## bootstrap 不负责什么

- 不负责读取 `sources { }` 里的设计、数据库、KSP 元数据或 IR 分析输入。
- 不负责生成命令、查询、聚合、handler、subscriber 这些业务源码。
- 不会替你自动跑 `cap4kPlan` 或 `cap4kGenerate`。
- 不会把任意旧 Gradle 根静默接管成受管宿主根；`IN_PLACE` 只支持受管 root-host。
- 不会替你决定真实项目的业务边界、目录命名或手写编排逻辑。

## bootstrap 之后作者下一步做什么

1. 确认宿主根与四个模块名符合项目现实。
2. 在根工程补全 `cap4k { project { } sources { } generators { } }`。
3. 准备真实输入：`design.json`、数据库 schema、KSP metadata，或显式要做 analysis 时的 IR 输入。
4. 跑 `cap4kPlan`，读取 `build/cap4k/plan.json`。
5. 只有在计划符合预期后，才跑 `cap4kGenerate`。

## 常见反例

- 用 bootstrap 代替 `cap4kPlan` / `cap4kGenerate`，期待它直接产出业务源码。
- 为了看目录布局，把真实项目反复在 `IN_PLACE` 模式下试跑，而不是先用 `PREVIEW_SUBTREE`。
- 把 bootstrap 当成旧项目自动迁移器，期待它接管任意未受管 Gradle 根。
- bootstrap 之后不看源码生成计划，直接手改根模块与业务模块结构，造成后续 plan / generate 认知错位。

## 最低验证

- 先跑 `./gradlew cap4kBootstrapPlan`，确认 `build/cap4k/bootstrap-plan.json` 已写出。
- 检查计划里的根与模块落点是否符合当前模式：`IN_PLACE` 还是 `PREVIEW_SUBTREE`。
- 再跑 `./gradlew cap4kBootstrap`。
- 确认根 `build.gradle.kts` / `settings.gradle.kts` 的 managed section 与模块骨架已出现，且宿主根外的内容没有被 bootstrap 当成业务代码接管。
