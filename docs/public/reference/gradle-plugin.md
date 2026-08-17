# Gradle Plugin

pipeline plugin id：

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}
```

如果项目通过 version catalog 管理 plugin，也可以用别名；公开合同仍是 plugin id `io.github.ldmoxeii.cap4k.pipeline`。

<!-- CAPABILITY_CONTRACT:PUBLIC_TASKS -->
## 公开任务

| Task | Mutation boundary | Live external input | 主要输入 | 主要输出 |
| --- | --- | --- | --- | --- |
| `cap4kAgentSnapshot` | `build_evidence_only` | `false` | 已解析 Gradle configuration、本地 inputs 与既有 evidence | `build/cap4k/agent/` manifest-first snapshot |
| `cap4kPlan` | `build_evidence_only` | `true` | DB/schema、`design-json`、`enum-manifest`、`value-object-manifest`、Gradle extension | `build/cap4k/plan.json` |
| `cap4kGenerate` | `managed_outputs` | `true` | source-generation plan | 写出 source-generation plan 中的文件 |
| `cap4kGenerateSources` | `managed_outputs` | `true` | generated source/resource task config | `<module>/build/generated/cap4k/main/kotlin` 与 `<module>/build/generated/cap4k/main/resources` |
| `cap4kAnalysisPlan` | `build_evidence_only` | `false` | `sources.irAnalysis.inputDirs` | `build/cap4k/analysis-plan.json` |
| `cap4kAnalysisGenerate` | `managed_outputs` | `false` | analysis plan | 导出 analysis artifacts，尤其是 flow 和 drawing-board |
<!-- /CAPABILITY_CONTRACT:PUBLIC_TASKS -->

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

## 任务边界

| Boundary | 说明 |
| --- | --- |
| `cap4kAgentSnapshot` | read-only project/capability inspection；默认不连接 live external source。 |
| `cap4kPlan` / `cap4kGenerate` | ordinary source generation，读取 DB/schema、design JSON 和 type manifests。 |
| `cap4kGenerateSources` | 输出 `GENERATED_SOURCE` 与 `GENERATED_RESOURCE`，roots 分别在 `<module>/build/generated/cap4k/main/kotlin` 与 `<module>/build/generated/cap4k/main/resources`。 |
| `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` | analysis/observation path，使用 source id `ir-analysis` 和 generator ids `flow`、`drawing-board`。 |

新项目结构由官方 GitHub Template 或团队自己的模板/人工流程建立；pipeline plugin 不提供项目初始化 task 或 DSL。
