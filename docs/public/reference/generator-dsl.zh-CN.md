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
    aggregate { enabled.set(true) }
    flow { enabled.set(true) }
    drawingBoard { enabled.set(true) }
}
```

| generator family | 说明 | 主要任务 |
| --- | --- | --- |
| design family | 命令、查询、client、validator、api payload、domain event 相关源码 | `cap4kPlan` / `cap4kGenerate` |
| aggregate | 聚合骨架及相关产物 | `cap4kPlan` / `cap4kGenerate` |
| `flow` | 流程观察材料 | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |
| `drawingBoard` | 设计 / 文档观察材料 | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |

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
        enumTranslation.set(false)
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
| `artifacts.enumTranslation` | 可选枚举翻译产物 |

## `preset / overrideDirs`

源码生成模板：

```kotlin
templates {
    preset.set("ddd-default")
    overrideDirs.from("codegen/templates")
    conflictPolicy.set("SKIP")
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

## 常见最小配置示例

design family：

```kotlin
project { basePackage.set("com.acme.demo"); applicationModulePath.set("demo-application"); adapterModulePath.set("demo-adapter") }
sources { designJson { enabled.set(true); files.from("design/design.json") } }
generators { designCommand { enabled.set(true) } }
```

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
