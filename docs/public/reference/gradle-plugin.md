# Gradle Plugin

pipeline plugin id：

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}
```

如果项目通过 version catalog 管理 plugin，也可以用别名；公开合同仍是 plugin id `io.github.ldmoxeii.cap4k.pipeline`。

## 公开任务

| Task | 主要输入 | 主要输出 |
| --- | --- | --- |
| `cap4kBootstrapPlan` | `cap4k { bootstrap { ... } }` | `build/cap4k/bootstrap-plan.json` |
| `cap4kBootstrap` | 已审查的 bootstrap configuration | 写出 bootstrap project structure |
| `cap4kPlan` | DB/schema、`design-json`、`enum-manifest`、`value-object-manifest`、Gradle extension | `build/cap4k/plan.json` |
| `cap4kGenerate` | source-generation plan | 写出 source-generation plan 中的文件 |
| `cap4kGenerateSources` | generated source task config | `<module>/build/generated/cap4k/main/kotlin` |
| `cap4kAnalysisPlan` | `sources.irAnalysis.inputDirs` | `build/cap4k/analysis-plan.json` |
| `cap4kAnalysisGenerate` | analysis plan | 导出 analysis artifacts，尤其是 flow 和 drawing-board |

`build/cap4k/*` 是 `build/` 下的本地 generated evidence，不是 committed source truth。

## 最小 Source Generation 入口

```kotlin
cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    types {
        enumManifest { files.from("design/enums.json") }
        valueObjectManifest { files.from("design/value-objects.json") }
    }
    sources {
        designJson { files.from("design/design.json") }
        db {
            enabled.set(true)
            url.set("jdbc:...")
            schema.set("PUBLIC")
        }
    }
}
```

## 最小 Bootstrap 入口

```kotlin
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.IN_PLACE)
        projectName.set("demo")
        basePackage.set("com.acme.demo")
        modules {
            domainModuleName.set("demo-domain")
            applicationModuleName.set("demo-application")
            adapterModuleName.set("demo-adapter")
            startModuleName.set("demo-start")
        }
        templates {
            preset.set("ddd-default-bootstrap")
        }
        conflictPolicy.set("SKIP")
    }
}
```

bootstrap 合同归入本页、[Generator DSL](generator-dsl.md)、[Plan JSON](plan-json.md) 和 [Common Mistakes](common-mistakes.md)。本章没有单独的 `reference/bootstrap.md`。

## 任务边界

| Boundary | 说明 |
| --- | --- |
| `cap4kPlan` / `cap4kGenerate` | ordinary source generation，读取 DB/schema、design JSON 和 type manifests。 |
| `cap4kGenerateSources` | 只输出 `GENERATED_SOURCE`，root 在 `<module>/build/generated/cap4k/main/kotlin`。 |
| `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` | analysis/observation path，使用 source id `ir-analysis` 和 generator ids `flow`、`drawing-board`。 |
| `cap4kBootstrapPlan` / `cap4kBootstrap` | project structure bootstrap，不替代业务建模、schema、design JSON 或 type manifests。 |
