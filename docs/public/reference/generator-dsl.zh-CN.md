# Generator DSL Reference

> 本页只收集 DSL 形状与最小解释。任务顺序、边界和作者决策请回到 [Generator Guide](../authoring/generator/index.zh-CN.md)。

## 顶层 `cap4k { }`

`cap4k` 是项目作者配置 pipeline 的总入口：

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

各块的作者视角职责：

- `project { }`：声明 `basePackage` 与模块路径。
- `types { }`：补充短名类型注册表。
- `sources { }`：声明输入从哪里来。
- `generators { }`：声明哪些 generator family 参与本次运行。
- `templates { }`：控制源码生成任务族的 preset、override 与冲突策略。
- `bootstrap { }`：控制 bootstrap 任务族。
- `layout { }`：控制源码或分析产物的包根、包后缀与输出根。

## `bootstrap { }`

```kotlin
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.IN_PLACE)
        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")
        conflictPolicy.set("FAIL")
        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
            startModuleName.set("only-danmuku-start")
        }
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
            moduleResources("start").from("codegen/bootstrap-slots/start-resources")
        }
    }
}
```

作者最需要记住的字段：

- `enabled`：不启用就不能跑 bootstrap 任务。
- `preset`：当前受支持的 bootstrap preset 是 `ddd-multi-module`。
- `mode`：默认 `IN_PLACE`；显式预览时改成 `PREVIEW_SUBTREE` 并提供 `previewDir`。
- `modules { }`：定义四个模块名。
- `templates { preset / overrideDirs }`：只影响 bootstrap 模板。
- `slots { }`：把额外固定槽位内容并入 bootstrap 骨架。

## `sources { }`

```kotlin
cap4k {
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("domain/build/generated/ksp/main/resources/metadata")
        }
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:build/h2/demo")
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("video_post"))
            excludeTables.set(emptyList())
        }
        enumManifest {
            enabled.set(true)
            files.from("design/enums/*.json")
        }
        irAnalysis {
            enabled.set(true)
            inputDirs.from("analysis/app/build/cap4k-code-analysis")
        }
    }
}
```

输入块与任务族关系：

- `designJson`、`kspMetadata`、`db`、`enumManifest`：服务 `cap4kPlan` / `cap4kGenerate` 这条源码生成链路。
- `irAnalysis`：服务 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`。

## `generators { }`

```kotlin
cap4k {
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
}
```

分组方式：

- design family：命令、查询、client、validator、api payload、domain event 相关 generator。
- aggregate family：基于 aggregate / schema 输入生成聚合骨架及其相关产物。
- analysis family：`flow` 与 `drawingBoard`。

要点：

- 在 DSL 中启用 generator，不代表所有任务都会用到它。
- design / aggregate 仍属于 `cap4kPlan` / `cap4kGenerate`。
- `flow` / `drawingBoard` 仍属于 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`。

## `aggregate { }`

`aggregate` 是 `generators { }` 内的子块：

```kotlin
cap4k {
    generators {
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
                wrapper.set(false)
                unique.set(false)
                enumTranslation.set(false)
            }
        }
    }
}
```

作者需要记住的现实：

- 默认最小 aggregate 族会产出 entity、schema、repository、behavior。
- `artifacts { }` 里的 `factory`、`specification`、`wrapper`、`unique`、`enumTranslation` 默认关闭，按需打开。
- aggregate 族里会同时出现 `GENERATED_SOURCE` 和 `CHECKED_IN_SOURCE`；真实所有权诊断应回到 `plan.json` 的 `outputKind` 与 `resolvedOutputRoot`。

## `preset / overrideDirs`

源码生成任务族与 bootstrap 任务族各自有一套模板配置：

```kotlin
cap4k {
    templates {
        preset.set("ddd-default")
        overrideDirs.from("codegen/templates")
        conflictPolicy.set("SKIP")
    }

    bootstrap {
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }
    }
}
```

当前现实：

- `preset`：选择 classpath 下的默认模板集。
- `overrideDirs`：按顺序查找同路径模板，首个命中覆盖默认模板。
- 当前覆盖方式仍然是项目级路径替换，不是 artifact / family 粒度控制。
- override 有升级漂移代价，默认应先接受官方 preset。

## 常见最小配置示例

### design family 最小示例

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        designCommand { enabled.set(true) }
        designQuery { enabled.set(true) }
        designClient { enabled.set(true) }
        designClientHandler { enabled.set(true) }
    }
}
```

### aggregate family 最小示例

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set(
                "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("video_post"))
            excludeTables.set(emptyList())
        }
    }
    generators {
        aggregate {
            enabled.set(true)
            specialFields {
                idDefaultStrategy.set("snowflake-long")
            }
        }
    }
}
```

### analysis family 最小示例

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
    }
    sources {
        irAnalysis {
            enabled.set(true)
            inputDirs.from("analysis/app/build/cap4k-code-analysis")
        }
    }
    layout {
        flow {
            outputRoot.set("flows")
        }
    }
    generators {
        flow {
            enabled.set(true)
        }
    }
}
```
