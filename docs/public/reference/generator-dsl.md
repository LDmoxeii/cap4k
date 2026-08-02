# Generator DSL

`cap4k {}` 是 pipeline plugin 的公开 Gradle extension。字段说明以公开 block 为准；业务建模解释见 [Generator Input Projection](../authoring/generator-input-projection.md)。

## 顶层 Blocks

```kotlin
cap4k {
    project { }
    types { }
    sources { }
    generators { }
    managedFields { }
    templates { }
    layout { }
    pipelineExtensions { }
}
```

| Block | 用途 |
| --- | --- |
| `project` | base package 与 module path。 |
| `types` | type registry、enum manifest、value-object manifest。 |
| `sources` | design JSON、DB/schema、IR analysis input。 |
| `generators` | aggregate、aggregate projection、flow、drawing-board generator block。 |
| `managedFields` | 项目级 managed-field 精确策略默认值。 |
| `templates` | source generation template preset、override dirs、conflict policies。 |
| `layout` | package layout 与 analysis output root。 |
| `pipelineExtensions` | Pipeline Extension provider 与 contribution 级选项。 |

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
| `aggregate` | `unsupportedTablePolicy` | DB/schema driven aggregate family。 |
| `aggregateProjection` | block presence | aggregate projection generator configuration marker。 |
| `flow` | none | analysis output generator id `flow`。 |
| `drawingBoard` | none | analysis output generator id `drawing-board`。 |

```kotlin
generators {
    aggregate {
        unsupportedTablePolicy.set("FAIL")
    }
    flow { }
    drawingBoard { }
}
```

## `managedFields { }`

Managed-field defaults select exact policy keys. An explicit database column annotation takes precedence over an exact column-name default, which takes precedence over the identifier default.

| Field | 说明 |
| --- | --- |
| `identifierDefaultPolicy` | 未显式标注的单列物理主键所使用的精确 `identifier.*` policy key；默认 `identifier.uuid7`。 |
| `columnPolicyDefaults` | exact column name 到 exact managed policy key 的映射。 |

```kotlin
managedFields {
    identifierDefaultPolicy.set("identifier.uuid7")
    columnPolicyDefaults.put("created_at", "enrichment.audit-time.created-at")
    columnPolicyDefaults.put("updated_at", "enrichment.audit-time.updated-at")
    columnPolicyDefaults.put("version", "version")
    columnPolicyDefaults.put("deleted", "soft-delete")
}
```

策略键区分大小写，并使用小写 kebab-case 点分段。自定义策略必须由已安装的 Pipeline Extension 提供定义；DB source 只保留键，Canonical Model 阶段负责解析。

## `templates { }`

| Field | 说明 |
| --- | --- |
| `preset` | source generation template preset，默认 `ddd-default`。 |
| `overrideDirs` | template override dirs，按配置顺序查找。 |
| `conflictPolicy` | 非 checked-in source-generation output 的默认 conflict policy；`CHECKED_IN_SOURCE` 固定为 `SKIP`。 |
| `templateConflictPolicies` | 按 `templateId` 覆盖非 checked-in output 的 conflict policy；不能让 checked-in source 被覆盖或失败。 |

```kotlin
templates {
    preset.set("ddd-default")
    overrideDirs.from("codegen/templates")
    conflictPolicy.set("SKIP")
    templateConflictPolicies.put("types/value_object_json_converter.kt.peb", "OVERWRITE")
}
```

addon template override 与 built-in template override 共用 `templates.overrideDirs` 和 `templates.templateConflictPolicies`。

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
    designCapability { packageRoot.set("application.capabilities") }
    designCapabilityHandler { packageRoot.set("adapter.application.capabilities") }
    designApiPayload { packageRoot.set("adapter.portal.api.payload") }
    flow { outputRoot.set("analysis/flows") }
    drawingBoard { outputRoot.set("analysis/drawing-board") }
}
```

公开 layout blocks 包括 `aggregate`, `aggregateSchema`, `aggregateRepository`, `aggregateSharedEnum`, `designCommand`, `designQuery`, `designCapability`, `designQueryHandler`, `designCapabilityHandler`, `designApiPayload`, `designDomainEvent`, `designDomainEventHandler`, `designIntegrationEvent`, `designIntegrationEventSubscriber`, `flow`, `drawingBoard`。

## `pipelineExtensions { }`

构建期扩展安装使用 Gradle configuration `cap4kPipelineExtension`。配置按 extension provider ID 和 contribution ID 两级寻址；选项只对对应 contribution 可见。

```kotlin
dependencies {
    cap4kPipelineExtension("com.only4:engine-cap4k-pipeline-extension:1.0.0")

    // 使用扩展定义的运行时 policy 时，运行时依赖需要显式声明。
    implementation("com.only4:engine-cap4k-managed-runtime:1.0.0")
}

cap4k {
    pipelineExtensions {
        provider("only-engine") {
            contribution("enum-translation") {
                option("mode", "project-default")
            }
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

Pipeline Extension 是唯一的构建期安装根。当前允许的 contribution 类型是 Managed Field Policy 和 Artifact Addon；未知 contribution 类型会失败，扩展不能插入或重排 pipeline stage。

Artifact Addon artifacts 会列入 `cap4kPlan`，并使用和 built-in artifacts 相同的 ownership fields：`generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy`。现有 addon template namespace `addons/<addonId>/...` 保持不变。
