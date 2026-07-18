# Generator DSL

`cap4k {}` 是 pipeline plugin 的公开 Gradle extension。字段说明以公开 block 为准；业务建模解释见 [Generator Input Projection](../authoring/generator-input-projection.md)。

## 顶层 Blocks

```kotlin
cap4k {
    project { }
    types { }
    sources { }
    generators { }
    templates { }
    bootstrap { }
    layout { }
    addons { }
}
```

| Block | 用途 |
| --- | --- |
| `project` | base package 与 module path。 |
| `types` | type registry、enum manifest、value-object manifest。 |
| `sources` | design JSON、DB/schema、IR analysis input。 |
| `generators` | aggregate、aggregate projection、flow、drawing-board generator block。 |
| `templates` | source generation template preset、override dirs、conflict policies。 |
| `bootstrap` | project structure bootstrap configuration。 |
| `layout` | package layout 与 analysis output root。 |
| `addons` | provider-scoped addon options；addon 安装仍通过 `cap4kAddon` dependency。 |

## `project { }`

| Field | 说明 |
| --- | --- |
| `basePackage` | generated package 的基础包。 |
| `domainModulePath` | domain module path。 |
| `applicationModulePath` | application module path。 |
| `adapterModulePath` | adapter module path。 |

```kotlin
project {
    basePackage.set("com.acme.demo")
    domainModulePath.set("demo-domain")
    applicationModulePath.set("demo-application")
    adapterModulePath.set("demo-adapter")
}
```

## `types { }`

| Field | 说明 |
| --- | --- |
| `registryFile` | 自定义类型 FQN / converter policy 输入。 |
| `enumManifest.files` | `types.enumManifest` files，例如 `design/enums.json`。 |
| `valueObjectManifest.files` | `types.valueObjectManifest` files，例如 `design/value-objects.json`。 |

```kotlin
types {
    registryFile.set("design/types.json")
    enumManifest { files.from("design/enums.json") }
    valueObjectManifest { files.from("design/value-objects.json") }
}
```

enum 与 Value Object manifest entries 不需要再重复写入 `types.registryFile`。

## `sources { }`

| Block | Fields | 服务的任务 |
| --- | --- | --- |
| `designJson` | `files`, `manifestFile` | `cap4kPlan`, `cap4kGenerate` |
| `db` | `enabled`, `url`, `username`, `password`, `schema`, `includeTables`, `excludeTables` | `cap4kPlan`, `cap4kGenerate` |
| `irAnalysis` | `inputDirs` | `cap4kAnalysisPlan`, `cap4kAnalysisGenerate` |

```kotlin
sources {
    designJson { files.from("design/design.json") }
    db {
        enabled.set(true)
        url.set("jdbc:...")
        username.set("sa")
        password.set("secret")
        schema.set("PUBLIC")
        includeTables.set(listOf("content"))
        excludeTables.set(emptyList())
    }
    irAnalysis {
        inputDirs.from("demo-application/build/cap4k-code-analysis")
    }
}
```

`sources.irAnalysis.inputDirs` 是 analysis selection。它不是 ordinary source generation input。

## `generators { }`

| Block | Fields | 说明 |
| --- | --- | --- |
| `aggregate` | `unsupportedTablePolicy`, `specialFields`, `artifacts` | DB/schema driven aggregate family。 |
| `aggregateProjection` | block presence | aggregate projection generator configuration marker。 |
| `flow` | none | analysis output generator id `flow`。 |
| `drawingBoard` | none | analysis output generator id `drawing-board`。 |

```kotlin
generators {
    aggregate {
        unsupportedTablePolicy.set("FAIL")
        specialFields {
            idDefaultStrategy.set("uuid7")
            deletedDefaultColumn.set("")
            versionDefaultColumn.set("")
            managedDefaultColumns.set(emptyList())
        }
        artifacts {
            factory.set(true)
            specification.set(false)
            unique.set(false)
        }
    }
    flow { }
    drawingBoard { }
}
```

`aggregate.artifacts.unique` 生成 aggregate unique helper surfaces。

## `templates { }`

| Field | 说明 |
| --- | --- |
| `preset` | source generation template preset，默认 `ddd-default`。 |
| `overrideDirs` | template override dirs，按配置顺序查找。 |
| `conflictPolicy` | 默认 source generation conflict policy，默认 `SKIP`。 |
| `templateConflictPolicies` | 按 `templateId` 覆盖 conflict policy。 |

```kotlin
templates {
    preset.set("ddd-default")
    overrideDirs.from("codegen/templates")
    conflictPolicy.set("SKIP")
    templateConflictPolicies.put("design/api_payload.kt.peb", "OVERWRITE")
}
```

addon template override 与 built-in template override 共用 `templates.overrideDirs` 和 `templates.templateConflictPolicies`。

## `bootstrap { }`

| Field | 说明 |
| --- | --- |
| `enabled` | 是否启用 bootstrap configuration。 |
| `preset` | bootstrap preset，常用 `ddd-multi-module`。 |
| `mode` | `BootstrapMode.IN_PLACE` 或 `BootstrapMode.PREVIEW_SUBTREE`。 |
| `previewDir` | `PREVIEW_SUBTREE` 输出目录。 |
| `projectName` | root project name。 |
| `basePackage` | bootstrap 后项目 base package。 |
| `modules.domainModuleName` | domain module name。 |
| `modules.applicationModuleName` | application module name。 |
| `modules.adapterModuleName` | adapter module name。 |
| `modules.startModuleName` | start module name。 |
| `templates.preset` | bootstrap template preset。 |
| `templates.overrideDirs` | bootstrap template override dirs。 |
| `slots` | `root`, `buildLogic`, `moduleRoot(role)`, `modulePackage(role)`, `moduleResources(role)`。 |
| `conflictPolicy` | bootstrap write conflict policy，默认 `FAIL`。 |

```kotlin
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
```

`cap4kBootstrapPlan` 写出 `build/cap4k/bootstrap-plan.json`；`cap4kBootstrap` 写出 bootstrap project structure。

## `layout { }`

package layout blocks 使用这些字段：

| Field | 说明 |
| --- | --- |
| `packageRoot` | package root segment。 |
| `packageSuffix` | appended suffix。 |
| `defaultPackage` | entry package 为空时的 fallback segment。 |

analysis output root blocks 使用这些字段：

| Field | 说明 |
| --- | --- |
| `outputRoot` | generated analysis artifact root。 |

常用 blocks：

```kotlin
layout {
    designCommand { packageRoot.set("application.commands") }
    designQuery { packageRoot.set("application.queries") }
    designApiPayload { packageRoot.set("adapter.portal.api.payload") }
    flow { outputRoot.set("analysis/flows") }
    drawingBoard { outputRoot.set("analysis/drawing-board") }
}
```

公开 layout blocks 包括 `aggregate`, `aggregateSchema`, `aggregateRepository`, `aggregateSharedEnum`, `aggregateUniqueQuery`, `aggregateUniqueQueryHandler`, `aggregateUniqueValidator`, `designCommand`, `designQuery`, `designClient`, `designQueryHandler`, `designClientHandler`, `designApiPayload`, `designDomainEvent`, `designDomainEventHandler`, `designIntegrationEvent`, `designIntegrationEventSubscriber`, `flow`, `drawingBoard`。

## `addons { }`

addon 安装使用 Gradle configuration `cap4kAddon`。`addons {}` 只承载 provider-scoped options。

```kotlin
dependencies {
    cap4kAddon("com.only4:engine-cap4k-addon:0.1.12-SNAPSHOT")
}

cap4k {
    addons {
        provider("only-engine-enum-translation") {
            option("mode", "project-default")
        }
    }
    templates {
        templateConflictPolicies.put(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            "OVERWRITE"
        )
    }
}
```

addon artifacts 会列入 `cap4kPlan`，并使用和 built-in artifacts 相同的 ownership fields：`generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy`。
