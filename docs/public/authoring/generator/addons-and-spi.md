# Addon 与 SPI 使用

本页面向业务项目作者说明如何使用 generator addon。它不是 addon 作者实现指南。

如果需要判断 addon 产物是否进入默认作者面，示例语境仍回到 [示例总览](../examples/index.md) 的内容发布与媒体处理项目。

## 业务项目什么时候用 addon

当项目需要内置生成族之外的产物，且对应能力由独立 addon 提供时，可以通过 addon 扩展生成计划。

业务项目作者只需要关心：

- 如何添加 addon 依赖；
- addon 会产出哪些 artifact；
- 如何在 `cap4kPlan` 中审阅 addon 计划；
- 如何覆盖 addon 模板；
- 如何设置 addon 模板冲突策略。

实现 `ArtifactAddonProvider`、打包模板资源、注册 `ServiceLoader` 属于 addon 作者指南，不在本页展开。

## cap4kAddon 依赖

Gradle 插件提供 `cap4kAddon` configuration。业务项目通过它把 addon jar 加入生成器 classpath：

```kotlin
dependencies {
    cap4kAddon("com.example:example-cap4k-addon:1.0.0")
}
```

addon jar 中的 provider 会通过 `ServiceLoader` 加载。重复 provider id 会被拒绝。

如果 addon 还提供自己的 Gradle extension 或项目配置项，应按该 addon 文档配置；cap4k 只提供 addon 加载、计划归一化、模板解析和导出机制。

## addon artifact 与内置 artifact

addon artifact 在业务项目作者视角下应像内置 artifact 一样处理：

- 同样出现在 `cap4kPlan` 的计划项中；
- 同样有 `generatorId`、`templateId`、`outputPath`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy`；
- 同样通过 renderer 渲染；
- 同样遵循 generated source / checked-in source 的 ownership 判断。

因此，使用 addon 后仍要先跑：

```powershell
./gradlew cap4kPlan
```

再审阅 `build/cap4k/plan.json`。不要因为 artifact 来自 addon，就跳过计划审阅。

## 覆盖 addon 模板

addon 模板通过类似下面的 `templateId` 定位：

```text
addons/<addon-id>/<family>/<template>.peb
```

addon jar 内部资源路径通常是：

```text
cap4k/addons/<addon-id>/<family>/<template>.peb
```

项目 `templates.overrideDirs` 会先于 addon 资源被检查，所以业务项目可以用相同相对路径覆盖 addon 模板。

示例：

```text
<override-dir>/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

模板覆盖会带来升级漂移和审计成本。只有当默认 addon 模板确实不满足项目需要时，才应覆盖。

## templateConflictPolicies

`templateConflictPolicies` 对 addon template ID 同样生效：

```kotlin
cap4k {
    templates {
        templateConflictPolicies.put(
            "addons/<addon-id>/flow/entry.json.peb",
            "OVERWRITE"
        )
    }
}
```

key 必须匹配计划中的真实 `templateId`。作者应先通过 `cap4kPlan` 找到 addon artifact 的 template ID，再配置策略。

常见判断：

- `GENERATED_SOURCE` 通常是 build-owned，可覆盖；
- `CHECKED_IN_SOURCE` 如果要成为作者维护骨架，通常应使用 `SKIP`；
- 无法确认 ownership 时，先不要用 `OVERWRITE` 覆盖已有作者代码。

## enum translation

enum translation 已从核心 aggregate DSL 中移除。当前方向是由 addon 拥有：

- cap4k 提供 addon SPI 和模板流水线；
- only-engine 或其他 addon 提供 enum translation artifact；
- 业务项目通过 `cap4kAddon` 依赖该 addon；
- 项目仍通过 `cap4kPlan`、`templates.overrideDirs`、`templateConflictPolicies` 审阅和控制产物。

因此，不要在核心 aggregate DSL 中寻找 enum translation 开关。应按 addon 文档启用，并把它当成普通 addon artifact 审阅。

## addon 作者指南是另一件事

业务项目使用 addon，不等于正在编写 addon。

| 角色 | 需要知道 |
| --- | --- |
| 业务项目作者 | 添加 `cap4kAddon` 依赖、配置 addon、审阅计划、覆盖模板、设置冲突策略 |
| addon 作者 | 实现 provider、产出 plan item、打包模板、注册 service loader |
| cap4k 框架作者 | 维护 SPI 兼容、计划归一化、renderer/exporter 行为 |

本页只覆盖第一行。需要编写 addon 时，应使用单独的 addon 作者指南。
