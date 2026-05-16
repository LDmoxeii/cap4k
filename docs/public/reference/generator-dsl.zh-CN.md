# Generator DSL Reference

> 本页按“块名 -> 关键字段 -> 最小片段”记录 DSL。任务顺序、作者决策和边界判断请回到 [Generator Guide](../authoring/generator/index.zh-CN.md)。

## 顶层 `cap4k { }`

```kotlin
cap4k {
    project { }
    types { }
    sources { }
    generators { }
    templates { }
    bootstrap { }
    layout { }
}
```

| 块 | 作用 |
| --- | --- |
| `project { }` | 声明 `basePackage` 与模块路径 |
| `types { }` | 注册额外短名类型 |
| `sources { }` | 声明输入来源 |
| `generators { }` | 启用要参与的生成族 |
| `templates { }` | 配置源码生成模板、覆盖目录、冲突策略 |
| `bootstrap { }` | 配置 bootstrap 任务族 |
| `layout { }` | 调整包根、包后缀与分析产物输出根 |

## `bootstrap { }`

最小形状：

```kotlin
bootstrap {
    enabled.set(true)
    preset.set("ddd-multi-module")
    mode.set(BootstrapMode.IN_PLACE)
    projectName.set("demo")
    basePackage.set("com.acme.demo")
    modules { /* domain / application / adapter / start */ }
    templates { preset.set("ddd-default-bootstrap") }
    slots { /* optional */ }
    conflictPolicy.set("FAIL")
}
```

| 字段 | 含义 |
| --- | --- |
| `enabled` | 不启用就不能跑 bootstrap 任务 |
| `preset` | 当前支持 `ddd-multi-module` |
| `mode` | `IN_PLACE` 或 `PREVIEW_SUBTREE` |
| `previewDir` | 仅 `PREVIEW_SUBTREE` 时需要 |
| `projectName` / `basePackage` | 项目名与基础包名 |
| `modules { }` | 四个模块名 |
| `templates { preset / overrideDirs }` | bootstrap 模板配置 |
| `slots { }` | 附加固定槽位内容 |
| `conflictPolicy` | bootstrap 写盘冲突策略 |

## `sources { }`

最小形状：

```kotlin
sources {
    designJson { enabled.set(true); files.from("design/design.json") }
    kspMetadata { enabled.set(true); inputDir.set("path/to/metadata") }
    db { enabled.set(true); url.set("jdbc:..."); schema.set("PUBLIC") }
    enumManifest { enabled.set(true); files.from("design/enums/*.json") }
    irAnalysis { enabled.set(true); inputDirs.from("path/to/ir-analysis") }
}
```

| source block | 常用字段 | 服务哪条任务链路 |
| --- | --- | --- |
| `designJson` | `enabled`, `files`, `manifestFile` | `cap4kPlan` / `cap4kGenerate` |
| `kspMetadata` | `enabled`, `inputDir` | `cap4kPlan` / `cap4kGenerate` |
| `db` | `enabled`, `url`, `username`, `password`, `schema`, `includeTables`, `excludeTables` | `cap4kPlan` / `cap4kGenerate` |
| `enumManifest` | `enabled`, `files` | `cap4kPlan` / `cap4kGenerate` |
| `irAnalysis` | `enabled`, `inputDirs` | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |

## `generators { }`

最小形状：

```kotlin
generators {
    designCommand { enabled.set(true) }
    designQuery { enabled.set(true) }
    designQueryHandler { enabled.set(true) }
    designClient { enabled.set(true) }
    designClientHandler { enabled.set(true) }
    designValidator { enabled.set(true) }
    designApiPayload { enabled.set(true) }
    designDomainEvent { enabled.set(true) }
    designDomainEventHandler { enabled.set(true) }
    designIntegrationEvent { enabled.set(true) }
    designIntegrationEventSubscriber { enabled.set(true) }
    aggregate { enabled.set(true) }
    flow { enabled.set(true) }
    drawingBoard { enabled.set(true) }
}
```

| generator family | 说明 | 主要任务 |
| --- | --- | --- |
| design family | 命令、查询、client、validator、api payload、domain event、integration event 相关源码 | `cap4kPlan` / `cap4kGenerate` |
| aggregate | 聚合骨架及相关产物 | `cap4kPlan` / `cap4kGenerate` |
| `flow` | 流程观察材料 | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |
| `drawingBoard` | 设计 / 文档观察材料 | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |

`designIntegrationEvent` 生成 `tag = "integration_event"` 的事件契约类，要求启用 `sources.designJson` 且配置 `project.applicationModulePath`。对应 design entry 必须声明 `role`、`eventName`、至少一个 `requestFields` 字段，并保持 `responseFields` 为空。`designIntegrationEventSubscriber` 依赖 `designIntegrationEvent`，只为 `role = "inbound"` 的事件生成 Spring `@EventListener` subscriber；`role = "outbound"` 只生成事件契约，不生成 subscriber。

## `aggregate { }`

最小形状：

```kotlin
aggregate {
    enabled.set(true)
    unsupportedTablePolicy.set("FAIL")
    specialFields {
        idDefaultStrategy.set("uuid7")
        deletedDefaultColumn.set("")
        versionDefaultColumn.set("")
        managedDefaultColumns.set(emptyList())
    }
    artifacts {
        factory.set(false)
        specification.set(false)
        unique.set(false)
    }
}
```

| 字段 | 含义 |
| --- | --- |
| `enabled` | 启用 aggregate 族 |
| `unsupportedTablePolicy` | 不支持表结构时的策略 |
| `specialFields.idDefaultStrategy` | 默认 ID 策略 |
| `specialFields.deletedDefaultColumn` | 默认逻辑删除列 |
| `specialFields.versionDefaultColumn` | 默认版本列 |
| `specialFields.managedDefaultColumns` | 默认受管字段 |
| `artifacts.factory` | 可选 factory 产物 |
| `artifacts.specification` | 可选 specification 产物 |
| `artifacts.unique` | 可选 unique 查询 / handler / validator |

枚举翻译不再是 cap4k core aggregate 产物。需要这类产物时，应通过构建期 addon 提供，并由 `cap4kAddon` 依赖加载。

## `templates { }`

源码生成模板：

```kotlin
templates {
    preset.set("ddd-default")
    overrideDirs.from("codegen/templates")
    conflictPolicy.set("SKIP")
    templateConflictPolicies.put("aggregate/factory.kt.peb", "OVERWRITE")
}
```

bootstrap 模板：

```kotlin
bootstrap {
    templates {
        preset.set("ddd-default-bootstrap")
        overrideDirs.from("codegen/bootstrap-templates")
    }
}
```

| 字段 | 含义 |
| --- | --- |
| `preset` | 选择默认模板集 |
| `overrideDirs` | 按顺序查找覆盖模板 |
| `conflictPolicy` | 源码生成写盘冲突策略 |
| `templateConflictPolicies` | 按 `templateId` 覆盖单个产物的写盘冲突策略 |

## Addon 产物

cap4k 原生产物和 addon 产物共用同一套模板覆盖、冲突策略、计划和生成语义。

项目如果安装构建期 addon，例如 only-engine 枚举翻译 addon，addon 贡献的产物会出现在 `cap4kPlan` 中，并通过 `cap4kGenerate` 写入文件。

```kotlin
dependencies {
    cap4kAddon("com.only4:engine-cap4k-addon:0.1.12-SNAPSHOT")
}

cap4k {
    templates {
        overrideDirs.from("codegen/templates")
        templateConflictPolicies.put(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            "OVERWRITE"
        )
    }
}
```

addon 模板可通过 `templates.overrideDirs` 覆盖；addon 模板冲突策略可通过 `templates.templateConflictPolicies` 按 `templateId` 配置。

运行时代码依赖和生成期 addon 依赖是两件事。运行时库由项目通过 `implementation` 等配置声明；生成期 addon 通过 `cap4kAddon` 声明。cap4k 不会扫描项目普通运行时 classpath 来自动发现 addon。

## 常见最小配置示例

design family：

```kotlin
project { basePackage.set("com.acme.demo"); applicationModulePath.set("demo-application"); adapterModulePath.set("demo-adapter") }
sources { designJson { enabled.set(true); files.from("design/design.json") } }
generators { designCommand { enabled.set(true) } }
```

integration event：

```kotlin
project { basePackage.set("com.acme.demo"); applicationModulePath.set("demo-application") }
sources { designJson { enabled.set(true); files.from("design/design.json") } }
generators {
    designIntegrationEvent { enabled.set(true) }
    designIntegrationEventSubscriber { enabled.set(true) }
}
```

默认布局会把事件契约放到 application 层的 `application.subscribers.integration.<role>.<designPackage>` 下，把 inbound subscriber 骨架放到 `application.subscribers.integration` 下。模板覆盖文件名是 `design/integration_event.kt.peb` 和 `design/integration_event_subscriber.kt.peb`。

aggregate family：

```kotlin
project { basePackage.set("com.acme.demo"); domainModulePath.set("demo-domain"); applicationModulePath.set("demo-application"); adapterModulePath.set("demo-adapter") }
sources { db { enabled.set(true); url.set("jdbc:..."); schema.set("PUBLIC") } }
generators { aggregate { enabled.set(true) } }
```

analysis family：

```kotlin
project { basePackage.set("com.acme.demo") }
sources { irAnalysis { enabled.set(true); inputDirs.from("path/to/ir-analysis") } }
generators { flow { enabled.set(true) } }
```
