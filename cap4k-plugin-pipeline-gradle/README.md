# cap4k-plugin-pipeline

> Cap4k DDD 代码生成流水线 —— 从"设计描述/数据库/IR 分析图"到"可编译工程骨架"的固定阶段流水线。
>
> Gradle 插件 ID：`com.only4.cap4k.plugin.pipeline`
>
> 本 README 覆盖：**使用**（如何在你的工程里开箱跑起来）+ **架构**（阶段/契约/模型）+ **二开**（如何新增 Source / Generator / Renderer / Template Preset / Bootstrap Preset）。

---

## 目录

- [1. 它是什么 / 不是什么](#1-它是什么--不是什么)
- [2. 为什么要这样设计](#2-为什么要这样设计)
- [3. 整体架构](#3-整体架构)
- [4. 模块全景](#4-模块全景)
- [5. 快速开始](#5-快速开始)
- [6. Gradle DSL 完整参考](#6-gradle-dsl-完整参考)
- [7. Gradle 任务参考](#7-gradle-任务参考)
- [8. Source Provider 详解](#8-source-provider-详解)
- [9. Generator Provider 详解](#9-generator-provider-详解)
- [10. Canonical Model 规范](#10-canonical-model-规范)
- [11. 模板系统](#11-模板系统)
- [12. Bootstrap 脚手架](#12-bootstrap-脚手架)
- [13. 二次开发指南](#13-二次开发指南)
- [14. 诊断与排错](#14-诊断与排错)
- [15. 测试 Fixture 索引](#15-测试-fixture-索引)
- [16. 治理与边界](#16-治理与边界)

---

## 1. 它是什么 / 不是什么

### 1.1 它是什么

`cap4k-plugin-pipeline` 是一组协作的 Gradle / Kotlin 模块，**把多类输入（设计 JSON、数据库 schema、KSP 元数据、IR 分析图、共享枚举清单）折叠成一个规范模型（`CanonicalModel`），然后让一组 Generator 把该模型投射到具体的工程产物（Kotlin 源文件、Flow 图、Drawing Board 文档等）**。

- 以 **固定阶段流水线** 的方式运作，阶段顺序 **不可由用户重排**。
- 用户面向的唯一 DSL 入口是 `cap4k { ... }`（见第 6 节）。
- 每个阶段的契约以 Kotlin 接口 + 数据类形式暴露在 `cap4k-plugin-pipeline-api` 中。**注意**：当前 provider 列表在 `PipelinePlugin.buildRunner()` 中**硬编码注册**，没有 ServiceLoader / DI 自动发现机制；新增 Source / Generator 必须修改 `cap4k-plugin-pipeline-gradle` 模块本身（见 § 13）。
- 渲染层与架构层解耦：默认渲染器使用 Pebble，但 `ArtifactRenderer` 是可替换接口。
- 与 `cap4k-ddd-*` 家族生成的代码对齐：输出直接落到 DDD 典型目录（`domain/aggregates/{agg}/...`、`application/commands/...`、`adapter/domain/repositories/...` 等）。

### 1.2 它不是什么

- **不是** 通用代码生成器。Canonical Model 针对 DDD 场景建模（Aggregate / Request / DomainEvent / Schema / Repository 等），其他领域需要自行裁剪。
- **不是** 可由 Groovy / Kotlin 闭包注入任意逻辑的脚本引擎。用户只能 `enabled = true/false` 和传参。
- **不是** 旧版 `cap4k-plugin-codegen`（monolithic）的一个小改。两者并存但契约完全不同；新工程请优先使用 pipeline。

---

## 2. 为什么要这样设计

1. **阶段固定** 让错误能在更早阶段被拦住：Source 校验→Canonical 校验→Generator 校验→文件冲突检查；无需推理"生成器之间是否有顺序依赖"。
2. **Canonical Model 作为唯一真理源**：Generator 只面对模型，不面对原始输入。新增 Source 如果能复用现有 `CanonicalModel` 字段则不波及 Generator；需要新增切片时只需 Generator 消费新字段即可。
3. **Render 阶段只做字符串产出**：类型解析、短名消歧、导入收集在 Canonical 阶段或 Planner 阶段完成；模板保持薄且可被覆盖。
4. **ConflictPolicy 控制落盘**：SKIP / OVERWRITE / FAIL 在导出阶段统一决定，不穿透到模板。
5. **严格分层的模块化**：Source / Generator / Renderer 是独立 Gradle 子模块，api 层零依赖，单元测试只依赖 `api` 和 `core`。**注意**：这是为了代码组织和测试隔离，不是外部可插拔点 —— provider 列表在 `cap4k-plugin-pipeline-gradle` 中硬编码组合（见 § 13）。

---

## 3. 整体架构

### 3.1 阶段序列

```
           ┌─────────────────────────────────────────────────────────────┐
           │                        ProjectConfig                        │
           │   (由 Cap4kProjectConfigFactory 从 Gradle Extension 构建)   │
           └─────────────────────────────────────────────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                                     ▼
┌──────────────────────────────┐           ┌──────────────────────────────┐
│   SourceProvider[]           │           │ Cap4kProjectConfigFactory    │
│   - design-json              │           │ - validateProjectRules       │
│   - ksp-metadata             │  collect  │ - buildModules               │
│   - db                       │ ────────▶ │ - buildSources               │
│   - enum-manifest            │           │ - buildGenerators            │
│   - ir-analysis              │           │ - validateGeneratorDeps      │
└──────────────────────────────┘           └──────────────────────────────┘
                    │
                    ▼ SourceSnapshot[]
┌──────────────────────────────────────────────────────────────────────┐
│              CanonicalAssembler (DefaultCanonicalAssembler)          │
│  将 SourceSnapshot[] 折叠成 CanonicalModel + PipelineDiagnostics     │
└──────────────────────────────────────────────────────────────────────┘
                    │
                    ▼ CanonicalModel
┌──────────────────────────────────────────────────────────────────────┐
│                    GeneratorProvider[].plan()                        │
│  - design / design-query-handler / design-client / design-client-    │
│    handler / design-validator / design-api-payload / design-domain-  │
│    event / design-domain-event-handler                               │
│  - aggregate (内部委派 12 个 family planner)                         │
│  - flow / drawing-board                                              │
└──────────────────────────────────────────────────────────────────────┘
                    │
                    ▼ ArtifactPlanItem[]   ← cap4kPlan 在这里写 plan.json 后停止
┌──────────────────────────────────────────────────────────────────────┐
│                  ArtifactRenderer (PebbleArtifactRenderer)           │
│    TemplateResolver → 模板文本 → 两阶段渲染（design 模板）           │
└──────────────────────────────────────────────────────────────────────┘
                    │
                    ▼ RenderedArtifact[]
┌──────────────────────────────────────────────────────────────────────┐
│    ArtifactExporter (FilesystemArtifactExporter / NoopExporter)      │
│    根据 ConflictPolicy (SKIP/OVERWRITE/FAIL) 决定是否写盘           │
└──────────────────────────────────────────────────────────────────────┘
                    │
                    ▼ PipelineResult (writtenPaths + diagnostics)
```

### 3.2 核心契约（`cap4k-plugin-pipeline-api`）

```kotlin
interface SourceProvider {
    val id: String
    fun collect(config: ProjectConfig): SourceSnapshot
}

interface GeneratorProvider {
    val id: String
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
```

`SourceSnapshot` 是 **sealed interface**，所有快照类型均封闭在 `PipelineModels.kt` 中：`DbSchemaSnapshot` / `DesignSpecSnapshot` / `KspMetadataSnapshot` / `IrAnalysisSnapshot` / `EnumManifestSnapshot`。新增 snapshot 子类型必须在 api 模块内完成 —— Kotlin 的 sealed 约束不允许外部模块声明子类。

`ArtifactPlanItem` 是计划单元：

```kotlin
data class ArtifactPlanItem(
    val generatorId: String,
    val moduleRole: String,     // "domain" | "application" | "adapter" | "project"
    val templateId: String,     // 相对于 preset 根或 overrideDirs
    val outputPath: String,     // 相对工程根的产物路径
    val context: Map<String, Any?>,
    val conflictPolicy: ConflictPolicy,
)
```

### 3.3 渲染契约（`cap4k-plugin-pipeline-renderer-api`）

```kotlin
interface ArtifactRenderer {
    fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact>
}

interface TemplateResolver {
    fun resolve(templateId: String): String
}

interface BootstrapRenderer {
    fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact>
}
```

---

## 4. 模块全景

流水线由 15 个独立 Gradle 子模块组成，严格单向依赖（`api` 层不依赖任何其他模块）：

| 模块 | 职责 | 关键符号 |
|------|------|---------|
| `cap4k-plugin-pipeline-api` | 纯契约 + 数据模型 | `SourceProvider`, `GeneratorProvider`, `PipelineRunner`, `CanonicalModel`, `ArtifactPlanItem`, `ProjectConfig`, `ConflictPolicy`, `BootstrapPresetProvider` |
| `cap4k-plugin-pipeline-core` | 默认实现装配 | `DefaultPipelineRunner`, `DefaultCanonicalAssembler`, `DefaultBootstrapRunner`, `FilesystemArtifactExporter`, `NoopArtifactExporter`, `AggregateNaming`, `AggregateRelationInference`, `AggregateJpaControlInference` |
| `cap4k-plugin-pipeline-gradle` | Gradle 插件、DSL、Task | `PipelinePlugin`, `Cap4kExtension`, `Cap4kPlanTask`, `Cap4kGenerateTask`, `Cap4kBootstrapPlanTask`, `Cap4kBootstrapTask`, `Cap4kProjectConfigFactory`, `Cap4kBootstrapConfigFactory` |
| `cap4k-plugin-pipeline-renderer-api` | 渲染契约 | `ArtifactRenderer`, `TemplateResolver`, `BootstrapRenderer` |
| `cap4k-plugin-pipeline-renderer-pebble` | Pebble 默认实现 + preset 模板 | `PebbleArtifactRenderer`, `PebbleBootstrapRenderer`, `PresetTemplateResolver`, `PipelinePebbleExtension` |
| `cap4k-plugin-pipeline-bootstrap` | Bootstrap preset 提供者 | `DddMultiModuleBootstrapPresetProvider` |
| `cap4k-plugin-pipeline-source-design-json` | 读取设计 JSON | `DesignJsonSourceProvider` |
| `cap4k-plugin-pipeline-source-db` | 读取 JDBC schema | `DbSchemaSourceProvider`, `JdbcTypeMapper`, `DbColumnAnnotationParser`, `DbRelationAnnotationParser` |
| `cap4k-plugin-pipeline-source-ksp-metadata` | 读取 KSP 聚合元数据 | `KspMetadataSourceProvider` |
| `cap4k-plugin-pipeline-source-ir-analysis` | 读取 IR 节点/边/设计元素 | `IrAnalysisSourceProvider` |
| `cap4k-plugin-pipeline-source-enum-manifest` | 读取共享枚举清单 | `EnumManifestSourceProvider` |
| `cap4k-plugin-pipeline-generator-design` | 设计族（command/query/client/validator/api_payload/domain_event/...） | `DesignArtifactPlanner`, `DesignQueryHandlerArtifactPlanner`, `DesignClientArtifactPlanner`, `DesignClientHandlerArtifactPlanner`, `DesignValidatorArtifactPlanner`, `DesignApiPayloadArtifactPlanner`, `DesignDomainEventArtifactPlanner`, `DesignDomainEventHandlerArtifactPlanner` |
| `cap4k-plugin-pipeline-generator-aggregate` | 聚合族（entity/schema/repository/factory/specification/wrapper/enum/...） | `AggregateArtifactPlanner` 及 12 个 family planner |
| `cap4k-plugin-pipeline-generator-flow` | 流图导出 | `FlowArtifactPlanner` |
| `cap4k-plugin-pipeline-generator-drawing-board` | 画板文档 | `DrawingBoardArtifactPlanner` |

**依赖方向**：
```
gradle ──┬──► bootstrap ──► renderer-pebble ──► renderer-api ──► api
         ├──► generator-* ─────────────────────────────────────► api
         ├──► source-* ──────────────────────────────────────► api
         └──► core ──► renderer-api ──► api
```

---

## 5. 快速开始

### 5.1 先决条件

- Gradle 8.x，Kotlin DSL
- JDK 17+
- `settings.gradle.kts` 里已声明 `cap4k` 多模块分层（`domain` / `application` / `adapter`），参考项目 `settings.gradle.kts`
- 如果从 Maven 仓库拉取插件，在根 `settings.gradle.kts` 里配好阿里云 Cap4k 仓库（参考 `cap4k/settings.gradle.kts`）

### 5.2 最小化配置（design 族，单聚合）

```kotlin
// build.gradle.kts（工程根）
plugins {
    id("com.only4.cap4k.plugin.pipeline") version "x.y.z"
}

cap4k {
    project {
        basePackage = "com.example.demo"
        applicationModulePath = "demo-application"
        domainModulePath = "demo-domain"
        adapterModulePath = "demo-adapter"
    }
    sources {
        designJson {
            enabled = true
            files.from("design/design.json")
        }
        kspMetadata {
            enabled = true
            inputDir = "demo-domain/build/generated/ksp/main/resources/metadata"
        }
    }
    generators {
        design { enabled = true }
        designDomainEvent { enabled = true }
        designDomainEventHandler { enabled = true }
    }
    // 可选：模板覆盖 + 冲突策略
    templates {
        overrideDirs.from("codegen/templates")   // 任意子路径可覆盖 preset
        conflictPolicy = "SKIP"                  // SKIP / OVERWRITE / FAIL
    }
}
```

```bash
# 先预览计划（不写盘）
./gradlew cap4kPlan
cat build/cap4k/plan.json

# 确认没问题再落盘
./gradlew cap4kGenerate
```

### 5.3 典型的 `design.json`

每个条目一个 design 元素，`tag` 决定它最终进入哪个生成家族：

```json
[
  {
    "tag": "cmd",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "提交订单命令",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" },
      { "name": "submittedAt", "type": "java.time.LocalDateTime" }
    ],
    "responseFields": [
      { "name": "accepted", "type": "Boolean" }
    ]
  },
  {
    "tag": "domain_event",
    "package": "order",
    "name": "OrderCreated",
    "desc": "订单创建事件",
    "aggregates": ["Order"],
    "persist": true,
    "requestFields": [
      { "name": "reason", "type": "String" }
    ],
    "responseFields": []
  }
]
```

**支持的 `tag`**（大小写不敏感）：

| tag | 家族 | 映射 Kind |
|-----|------|----------|
| `cmd`, `command` | design | COMMAND（类名 `{Name}Cmd`） |
| `qry`, `query` | design | QUERY（类名 `{Name}Qry`） |
| `cli`, `client`, `clients` | design-client | CLIENT（类名 `{Name}Cli`） |
| `validator` | design-validator | 类名 `{UpperCamel}`（规范化驼峰） |
| `api_payload` | design-api-payload | 类名 `{UpperCamel}` |
| `domain_event` | design-domain-event | 类名不以 `Evt`/`Event` 结尾时自动追加 `DomainEvent` |

---

## 6. Gradle DSL 完整参考

所有 DSL 均定义在 `Cap4kExtension.kt`。插件 apply 后可直接用 `cap4k { ... }` 入口。

### 6.1 `project { }`

```kotlin
cap4k {
    project {
        basePackage = "com.example.demo"          // 必填；其它生成路径的 namespace 根
        applicationModulePath = "demo-application" // 当启用 application 类生成时必填
        domainModulePath = "demo-domain"           // 当启用 domain 类生成时必填
        adapterModulePath = "demo-adapter"         // 当启用 adapter 类生成时必填
    }
}
```

**校验规则**（见 `Cap4kProjectConfigFactory.validateProjectRules`）：

| 启用的 Generator | 必填字段 |
|-------------------|---------|
| `design` | `applicationModulePath` |
| `designQueryHandler` | `adapterModulePath` |
| `designClient` | `applicationModulePath` |
| `designClientHandler` | `adapterModulePath` |
| `designValidator` | `applicationModulePath` |
| `designApiPayload` | `adapterModulePath` |
| `designDomainEvent` | `domainModulePath` |
| `designDomainEventHandler` | `applicationModulePath` |
| `aggregate` | 同时要求 `domainModulePath`, `applicationModulePath`, `adapterModulePath` |

### 6.2 `types { }` — 项目级类型注册表

当 design JSON 中使用短名类型（如 `"type": "UUID"`）时，框架默认只认识 Kotlin 基本类型。可以通过外部 JSON 注册额外的 SimpleName→FQN 映射：

```kotlin
cap4k {
    types {
        registryFile = "codegen/type-registry.json"
    }
}
```

```json
// codegen/type-registry.json
{
  "UUID": "java.util.UUID",
  "LocalDateTime": "java.time.LocalDateTime",
  "Status": "com.example.common.Status"
}
```

**约束**：
- 键必须是简单名（不可含 `.`）
- 值必须是合法 FQN（多段、每段合法 Java 标识符）
- 不可覆盖内置 Kotlin 类型（`String`, `Int`, `Long`, `Boolean`, `List`, `Map`, ... 见 `reservedTypeNames`）
- 重复键会直接抛错

### 6.3 `sources { }`

```kotlin
cap4k {
    sources {
        designJson {
            enabled = true
            // 方式 1：直接列出文件
            files.from("design/a.json", "design/b.json")
            // 方式 2：使用清单文件
            manifestFile = "design/manifest.json"  // 清单内容：["a.json","b.json"]，路径相对 projectDir
        }
        kspMetadata {
            enabled = true
            inputDir = "domain/build/generated/ksp/main/resources/metadata"
        }
        db {
            enabled = true
            url = "jdbc:mysql://localhost:3306/demo"
            username = "root"
            password = "root"
            schema = "demo"          // 可选
            includeTables.addAll(listOf("orders", "items"))
            excludeTables.addAll(listOf("audit_log"))
        }
        enumManifest {
            enabled = true
            files.from("design/enums.json")
        }
        irAnalysis {
            enabled = true
            inputDirs.from("analysis/app/build/cap4k-code-analysis")
        }
    }
}
```

**各 source 规则**：

- **`designJson`**：`files` 与 `manifestFile` 二选一；启用时必须至少一个条目；清单模式下 projectDir 自动注入
- **`kspMetadata`**：`inputDir` 必须指向已存在目录，目录内文件必须命名为 `aggregate-*.json`
- **`db`**：自动尝试加载 `h2`/`mysql`/`mariadb`/`postgresql` 驱动（按 URL 前缀判定）；其他驱动需自行确保 classpath 可用
- **`enumManifest`**：`files` 非空；JSON 结构见 `SharedEnumDefinition`
- **`irAnalysis`**：目录内需包含 `nodes.json`, `rels.json`，可选 `design-elements.json`

### 6.4 `generators { }`

所有生成器默认 `enabled = false`。完整列表：

```kotlin
cap4k {
    generators {
        design { enabled = true }
        designQueryHandler { enabled = true }
        designClient { enabled = true }
        designClientHandler { enabled = true }
        designValidator { enabled = true }
        designApiPayload { enabled = true }
        designDomainEvent { enabled = true }
        designDomainEventHandler { enabled = true }
        aggregate {
            enabled = true
            unsupportedTablePolicy = "FAIL"   // FAIL / SKIP
        }
        flow {
            enabled = true
            outputDir = "docs/flow"           // 相对工程根
        }
        drawingBoard {
            enabled = true
            outputDir = "docs/drawing-board"
        }
    }
}
```

**Generator 之间的依赖**（见 `Cap4kProjectConfigFactory.validateGeneratorDependencies`，启用时强制）：

| Generator | 需要启用 |
|-----------|---------|
| `design` | `designJson` source |
| `designQueryHandler` | `design` generator |
| `designClient` | `designJson` source |
| `designClientHandler` | `designClient` generator |
| `designValidator` | `designJson` source |
| `designApiPayload` | `designJson` source |
| `designDomainEvent` | `designJson` + `kspMetadata` sources |
| `designDomainEventHandler` | `designDomainEvent` generator |
| `aggregate` | `db` source |
| `flow` | `irAnalysis` source |
| `drawingBoard` | `irAnalysis` source |

### 6.5 `templates { }`

```kotlin
cap4k {
    templates {
        preset = "ddd-default"                // 默认 preset id
        overrideDirs.from("codegen/templates") // 覆盖目录列表（按顺序查找）
        conflictPolicy = "SKIP"               // SKIP / OVERWRITE / FAIL
    }
}
```

- `preset`：classpath resource 根（`presets/{preset}/`）
- `overrideDirs`：按目录顺序查找；首个命中的同 id 文件即为最终模板文本
- `conflictPolicy` 作用于 **导出** 阶段，决定目标文件已存在时的行为

### 6.6 `bootstrap { }`

用于生成 Bootstrap 骨架（多模块 Gradle 根 + 各模块源目录，外加可选 slot 扩展）。

Kotlin DSL 中这些属性当前是 Gradle `Property` / `ListProperty` 风格，示例请按 `.set(...)` / `.from(...)` 使用。

**官方默认模式**是 `IN_PLACE`：写向当前受管宿主根，不再隐式创建 `{projectName}/...` 子树。

```kotlin
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.IN_PLACE) // 默认值；不写也会落到 IN_PLACE
        projectName.set("demo")
        basePackage.set("com.example.demo")
        modules {
            domainModuleName.set("demo-domain")
            applicationModuleName.set("demo-application")
            adapterModuleName.set("demo-adapter")
            startModuleName.set("demo-start")
        }
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("bootstrap-templates")
        }
        slots {
            root.from("bootstrap/slots/root")
            modulePackage("domain").from("bootstrap/slots/domain-package")
            modulePackage("start").from("bootstrap/slots/start-package")
            moduleResources("start").from("bootstrap/slots/start-resources")
        }
        conflictPolicy.set("FAIL")
    }
}
```

如果你需要一个显式的教学 / 演示输出子树，请切到 `PREVIEW_SUBTREE` 并提供 `previewDir`：

```kotlin
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("bootstrap-preview")
        projectName.set("demo")
        basePackage.set("com.example.demo")
        modules {
            domainModuleName.set("demo-domain")
            applicationModuleName.set("demo-application")
            adapterModuleName.set("demo-adapter")
            startModuleName.set("demo-start")
        }
    }
}
```

- `mode`：默认 `IN_PLACE`；`PREVIEW_SUBTREE` 是显式预览模式
- `previewDir`：仅在 `mode = PREVIEW_SUBTREE` 时允许，且必须是安全的相对路径
- `projectName`：控制生成的项目标识（如 `rootProject.name` 与模块命名上下文），**不再**隐式决定输出根前缀
- `conflictPolicy`：对非根目录输出仍按常规规则生效，默认仍是 `FAIL`；`IN_PLACE` 下的 `build.gradle.kts` / `settings.gradle.kts` 通过 bootstrap-managed section merge 可重复执行，不等于整文件接管
- `IN_PLACE`：当前只支持作用于**受管 root-host 宿主根**（根文件必须带 cap4k bootstrap managed section）；如果只是想看产物布局或做教程演示，请使用 `PREVIEW_SUBTREE`

详见第 12 节。

---

## 7. Gradle 任务参考

插件注册在 `PipelinePlugin.apply()` 中：

| 任务名 | 作用 | 产物 | 是否写盘 |
|--------|------|------|---------|
| `cap4kPlan` | 规划生成计划 | `build/cap4k/plan.json`（含 `items` 与 `diagnostics`） | 否 |
| `cap4kGenerate` | 执行完整流水线 | 生成到 `outputPath` 指向的位置 | 是 |
| `cap4kBootstrapPlan` | 规划 Bootstrap 骨架 | `build/cap4k/bootstrap-plan.json` | 否 |
| `cap4kBootstrap` | 生成 Bootstrap 骨架 | 按 `bootstrap.mode` 写入：`IN_PLACE` 写向当前受管宿主根；`PREVIEW_SUBTREE` 写到 `previewDir/` | 是 |

**自动任务依赖推断**（`inferDependencies`）：

- 如果启用了 `design` 或 `design-domain-event` 生成器，并且 `ksp-metadata` 源的 `inputDir` 落在某个子工程的 `build/` 下 → 自动 `dependsOn(":{proj}:kspKotlin")`
- 如果启用了 `flow` 或 `drawing-board`，且 `ir-analysis.inputDirs` 落在某个子工程的 `build/` 下 → 自动 `dependsOn(":{proj}:compileKotlin")`

此外，启用 `aggregate` 时，会在域模块自动添加 `jakarta.persistence-api:3.1.0` 依赖（`ensureAggregateDomainJpaDependency`）。

---

## 8. Source Provider 详解

### 8.1 `design-json` — 设计描述

- 实现：`DesignJsonSourceProvider`
- 输入格式：JSON 数组，每项为 `DesignSpecEntry`

```ts
type DesignSpecEntry = {
    tag: string;              // 见"支持的 tag"
    package: string;          // 用作子包名（如 order.submit → order/submit/...）
    name: string;             // 未规范化名，生成器会加后缀/规范化
    desc?: string;
    aggregates?: string[];    // 对应 KSP 元数据里的 aggregateName
    persist?: boolean;        // 仅 domain_event 使用
    requestFields?: FieldModel[];
    responseFields?: FieldModel[];
}

type FieldModel = {
    name: string;             // 可用 "address.city" 来表达嵌套字段
    type?: string;            // 默认 "kotlin.String"；支持 FQN 或 simpleName（经 typeRegistry）
    nullable?: boolean;
    defaultValue?: string;    // Kotlin 合法源码片段
}
```

- **manifestFile 模式**：清单必须是字符串数组，条目相对于 `projectDir`；不允许 `../` 逃逸，不允许重复，不允许指向不存在文件
- 字段 `type` 如果是短名，会经过 `typeRegistry` 解析；未解析的短名保持原样（模板侧可能因此产出错误 import）

### 8.2 `ksp-metadata` — 聚合元数据

- 实现：`KspMetadataSourceProvider`
- 文件名约定：`aggregate-{AggName}.json`
- 结构：

```json
{
  "aggregateName": "Order",
  "aggregateRoot": {
    "className": "Order",
    "qualifiedName": "com.example.demo.domain.aggregates.order.Order",
    "packageName": "com.example.demo.domain.aggregates.order"
  }
}
```

- 用途：`domain_event` 需要查找 `aggregates[0]` 对应的聚合包名；`design` 的 CMD/QRY 亦会尝试用之标注 `aggregatePackageName`

### 8.3 `db` — JDBC Schema

- 实现：`DbSchemaSourceProvider`
- 读取：`DatabaseMetaData.getTables/getColumns/getPrimaryKeys/getIndexInfo`
- **注解语法**：统一写在 JDBC 注释（表/列的 `REMARKS`）中，形式 `@KEY[=value];`（末尾分号可省略，但多个注解之间建议用 `;` 分隔）。key 大小写不敏感。

#### 列注解（`DbColumnAnnotationParser`）

| 注解 | 别名 | 形式 | 作用 |
|------|------|------|------|
| `@TYPE` | `@T` | `@T=com.example.Status;` | 显式指定列的 Kotlin 类型（`typeBinding`） |
| `@ENUM` | `@E` | `@E=0:OFF:禁用\|1:ON:启用;` | 本地枚举条目（`value:name:description`，用 `\|` 分隔多项）；**必须与 `@T` 同时出现**，否则校验失败 |

#### 表/列关系注解（`DbRelationAnnotationParser`）

**表级**（写在表注释上）：

| 注解 | 别名 | 形式 | 作用 |
|------|------|------|------|
| `@PARENT` | `@P` | `@P=parent_table;` | 声明父表，构成父子聚合结构 |
| `@AGGREGATEROOT` | `@ROOT`, `@R` | `@R=true;` / `@R=false;` | 显式聚合根布尔；不写时默认由 `@P` 是否存在推断 |
| `@VALUEOBJECT` | `@VO` | `@VO` | 值对象标记（presence-only，**不允许写 `=value`**） |

注意：`@P` 与 `@R=true` 互斥，同时出现会抛 "conflicting table relation annotations"。

**列级**（写在列注释上）：

| 注解 | 别名 | 形式 | 作用 |
|------|------|------|------|
| `@REFERENCE` | `@REF` | `@REF=target_table;` | 外键引用目标表（下列 relation / lazy / count 的前提） |
| `@RELATION` | `@REL` | `@REL=MANY_TO_ONE;` | 显式关系类型；**当前仅支持** `MANY_TO_ONE`、`ONE_TO_ONE`（别名：`*:1`/`1:1`/`MANYTOONE`/`ONETOONE`）。**不支持** `ONE_TO_MANY` |
| `@LAZY` | `@L` | `@L=true;` / `@L=false;` | 是否懒加载（布尔） |
| `@COUNT` | `@C` | `@C=size;` | 计数提示（任意字符串值） |

注意：`@REL` / `@L` / `@C` 必须与 `@REF` 在同一列上，否则校验失败（`@Relation/Lazy/Count requires @Reference on the same column comment`）。

#### 示例

```sql
-- 表注释：标记为 order 的子表（值对象）
CREATE TABLE order_line (
  id BIGINT PRIMARY KEY COMMENT '@T=java.util.UUID;',
  order_id BIGINT COMMENT '订单外键 @REF=orders;@REL=MANY_TO_ONE;@L=true;',
  status TINYINT COMMENT '@T=com.example.LineStatus;@E=0:NEW:新建|1:DONE:完成;'
) COMMENT='订单行 @P=orders;@VO;';
```

- `includeTables`/`excludeTables` 支持精确匹配与大小写不敏感的归一匹配；匹配歧义（多个候选）时按未匹配处理
- Primary key 为空或复合 → 走 `UnsupportedTablePolicy`

### 8.4 `enum-manifest` — 共享枚举

- 实现：`EnumManifestSourceProvider`
- 结构：每个定义一个 `SharedEnumDefinition { typeName, packageName, generateTranslation, items[] }`
- 归属：仅喂给 `model.sharedEnums`，由 `aggregate` 生成器的 `SharedEnumArtifactPlanner` 产出（模板 `aggregate/enum.kt.peb`）；当 `generateTranslation = true` 时额外由 `EnumTranslationArtifactPlanner` 产出翻译器（模板 `aggregate/enum_translation.kt.peb`）
- **与本地枚举的分工**：`db` source 在列注释中的 `@T + @E` 组合会进入 `model.entities[].fields[].{typeBinding, enumItems}`，由独立的 `LocalEnumArtifactPlanner` 产出。两条路径互不合并，各自负责自己的 Canonical 字段
  - 共享枚举（跨聚合复用） → `enum-manifest` → `sharedEnums` → `SharedEnumArtifactPlanner`
  - 本地枚举（单列就地声明） → `db` 注解 → `entities.fields.enumItems` → `LocalEnumArtifactPlanner`
  - 翻译器 → `EnumTranslationArtifactPlanner`（仅处理 `sharedEnums` 中 `generateTranslation = true` 的条目）

### 8.5 `ir-analysis` — IR 分析图

- 实现：`IrAnalysisSourceProvider`
- 文件：`nodes.json`, `rels.json`, 可选 `design-elements.json`
- 由配套的 `cap4k-plugin-code-analysis-*` 工具链产出（`./gradlew compileKotlin` 后写到 `build/cap4k-code-analysis/`）
- 用途：
  - `flow` 把节点-边导出为每个 controllermethod 入口的 mermaid + JSON
  - `drawing-board` 把 `design-elements.json` 折叠成画板文档（按 `tag|package|name` 去重）

---

## 9. Generator Provider 详解

Generator 统一约束：
- `override val id: String` 与 `ProjectConfig.generators` 的键一一对应
- `plan(config, model)` 返回 `List<ArtifactPlanItem>`，每项指定 `templateId` + `outputPath` + `context`
- **不在生成器里直接写文件**（交给 exporter）

### 9.1 `design` — 命令/查询请求类

- id：`design`
- 模块：application
- 产物：`{application}/src/main/kotlin/{basePackage}/application/{commands|queries}/{package}/{TypeName}.kt`
- 模板：`design/command.kt.peb`, `design/query.kt.peb`, `design/query_list.kt.peb`, `design/query_page.kt.peb`（`DesignQueryVariantResolver` 按响应字段形态选择）
- 类名规则：`{Name}Cmd` / `{Name}Qry`

### 9.2 `design-query-handler`

- id：`design-query-handler`
- 模块：adapter
- 与 `design` 1:1 配套
- 模板：`design/query_handler.kt.peb`, `design/query_list_handler.kt.peb`, `design/query_page_handler.kt.peb`

### 9.3 `design-client` / `design-client-handler`

- 分别在 application 与 adapter 生成 CLIENT 请求与其处理器
- 模板：`design/client.kt.peb`, `design/client_handler.kt.peb`
- 类名：`{Name}Cli` / `{Name}CliHandler`

### 9.4 `design-validator`

- id：`design-validator`
- 模块：application
- 模板：`design/validator.kt.peb`
- 规范化：`UpperCamel`（下划线/空格拆分再大写首字符）

### 9.5 `design-api-payload`

- id：`design-api-payload`
- 模块：adapter
- 模板：`design/api_payload.kt.peb`
- 可同时生成 Request/Response 嵌套类（当字段名含 `.` 时视为嵌套结构）

### 9.6 `design-domain-event`

- id：`design-domain-event`
- 模块：domain
- 输出路径：`{domain}/src/main/kotlin/{basePackage}/domain/{entry.package}/events/{TypeName}.kt`
- 模板：`design/domain_event.kt.peb`
- 类名：若未以 `Evt`/`Event` 结尾则自动追加 `DomainEvent`
- Canonical 约束：`aggregates` 必须恰好 1 个；且该聚合必须在 `kspMetadata` 中找得到

### 9.7 `design-domain-event-handler`

- id：`design-domain-event-handler`
- 模块：application
- 模板：`design/domain_event_handler.kt.peb`
- 默认产出一个带 `@EventListener` 的 Subscriber 类

### 9.8 `aggregate`

- id：`aggregate`
- 模块：domain + application + adapter
- 是一个**复合生成器**，内部依次委派 12 个 family planner：
  - `SchemaArtifactPlanner` → `aggregate/schema.kt.peb`
  - `EntityArtifactPlanner` → `aggregate/entity.kt.peb`
  - `RepositoryArtifactPlanner` → `aggregate/repository.kt.peb`
  - `FactoryArtifactPlanner` → `aggregate/factory.kt.peb`
  - `SpecificationArtifactPlanner` → `aggregate/specification.kt.peb`
  - `AggregateWrapperArtifactPlanner` → `aggregate/wrapper.kt.peb`
  - `UniqueQueryArtifactPlanner` / `UniqueQueryHandlerArtifactPlanner` / `UniqueValidatorArtifactPlanner` → `aggregate/unique_*.kt.peb`
  - `SharedEnumArtifactPlanner` / `LocalEnumArtifactPlanner` / `EnumTranslationArtifactPlanner` → `aggregate/enum*.kt.peb`
- 目录约定：
  - `{domain}/src/main/kotlin/{basePackage}/domain/aggregates/{segment}/...`
  - `{domain}/src/main/kotlin/{basePackage}/domain/_share/meta/{segment}/{Schema}.kt`
  - `{adapter}/src/main/kotlin/{basePackage}/adapter/domain/repositories/{Repo}.kt`
- 表名 → segment 规则见 `AggregateNaming`

### 9.9 `flow`

- id：`flow`
- 模块：project（写到工程根）
- `outputDir` 必填（相对路径，禁止 `..` 逃逸）
- 每个 "controllermethod" 产出 `{slug}.json` 和 `{slug}.mmd`，另外有一个 `index.json`
- 模板：`flow/entry.json.peb`, `flow/entry.mmd.peb`, `flow/index.json.peb`

### 9.10 `drawing-board`

- id：`drawing-board`
- 模块：project
- 支持的 tag：`cli`, `cmd`, `qry`, `payload`, `de`（其他会被忽略）
- 模板：`drawing-board/document.json.peb`

---

## 10. Canonical Model 规范

位于 `PipelineModels.kt`。**所有 Generator 只应依赖这里定义的类型**；不能回读 Source Snapshot。

核心切片：

```kotlin
data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),           // 来自 design-json 的 cmd/qry/cli
    val validators: List<ValidatorModel> = emptyList(),       // tag=validator
    val domainEvents: List<DomainEventModel> = emptyList(),   // tag=domain_event
    val schemas: List<SchemaModel> = emptyList(),             // 来自 db
    val entities: List<EntityModel> = emptyList(),            // 来自 db
    val repositories: List<RepositoryModel> = emptyList(),    // 来自 db
    val analysisGraph: AnalysisGraphModel? = null,            // 来自 ir-analysis
    val drawingBoard: DrawingBoardModel? = null,              // 来自 ir-analysis.design-elements
    val apiPayloads: List<ApiPayloadModel> = emptyList(),     // tag=api_payload
    val sharedEnums: List<SharedEnumDefinition> = emptyList(), // 来自 enum-manifest
    val aggregateRelations: List<AggregateRelationModel> = emptyList(),
    val aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
)
```

**诊断**通过 `PipelineDiagnostics` 传出：
- `aggregate.discoveredTables/includedTables/excludedTables/supportedTables/unsupportedTables`
- 在 `cap4kPlan` 阶段以 JSON 形式写入 `plan.json`
- 当 `unsupportedTablePolicy = FAIL` 且出现不支持表时抛 `PipelineDiagnosticsException`

---

## 11. 模板系统

### 11.1 Preset 目录结构

所有内置模板位于 `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/`：

```
presets/
├── ddd-default/              # 生产模板
│   ├── design/
│   │   ├── command.kt.peb
│   │   ├── query.kt.peb
│   │   ├── query_list.kt.peb
│   │   ├── query_page.kt.peb
│   │   ├── query_handler.kt.peb
│   │   ├── query_list_handler.kt.peb
│   │   ├── query_page_handler.kt.peb
│   │   ├── client.kt.peb
│   │   ├── client_handler.kt.peb
│   │   ├── validator.kt.peb
│   │   ├── api_payload.kt.peb
│   │   ├── domain_event.kt.peb
│   │   └── domain_event_handler.kt.peb
│   ├── aggregate/
│   │   ├── entity.kt.peb, schema.kt.peb, repository.kt.peb
│   │   ├── factory.kt.peb, specification.kt.peb, wrapper.kt.peb
│   │   ├── unique_query.kt.peb, unique_query_handler.kt.peb, unique_validator.kt.peb
│   │   └── enum.kt.peb, enum_translation.kt.peb
│   ├── drawing-board/document.json.peb
│   └── flow/entry.json.peb, entry.mmd.peb, index.json.peb
└── ddd-default-bootstrap/    # Bootstrap 模板
    └── bootstrap/
        ├── root/build.gradle.kts.peb, settings.gradle.kts.peb
        └── module/{domain,application,adapter,start}-build.gradle.kts.peb, start-application.kt.peb
```

### 11.2 模板查找顺序（`PresetTemplateResolver`）

1. 如果 `templateId` 是存在的绝对路径 → 直接读取
2. 按顺序遍历 `templates.overrideDirs`，首个存在的 `{dir}/{templateId}` 胜出
3. classpath 资源：`presets/{preset}/{templateId}`
4. 全部失败 → 抛 `Template not found: presets/{preset}/{templateId}`

### 11.3 Pebble 引擎

有两个 `PebbleEngine` 实例：

- **designEngine**：`design/` 前缀的 templateId 走此引擎，启用 `use()` 助手
- **regularEngine**：用于 aggregate/flow/drawing-board/bootstrap

所有引擎共用这些扩展（`PipelinePebbleExtension`）：

| 名称 | 类型 | 作用 |
|------|------|------|
| `json` | filter | 将对象 `toJson` |
| `type(field)` | function | 取 `field.renderedType`（`String` 或 POJO 的 getter） |
| `imports(list)` | function | 返回规范化 + 去重的 import 列表，design 模板下还会合并 `use()` 注册 |
| `use(fqn)` | function | **仅 design 模板可用**。两阶段中仅在 COLLECTING 阶段写入收集器，RENDERING 阶段返回空串 |

### 11.4 两阶段渲染（仅 design 模板）

```
COLLECTING 阶段
    评估模板；use() 注册 FQN 到 ExplicitImportCollector
        ↓
RENDERING 阶段
    把收集器与 context.imports 合并后写入 context
    再次评估模板；此时 imports() 返回合并后的完整列表
```

冲突检测：同一简单名对应不同 FQN → 直接抛 `use() import conflict: {simpleName} is already bound to {A}, cannot also import {B}`。

### 11.5 典型模板片段

**Design 模板（`domain_event.kt.peb`）**：

```peb
package {{ packageName }}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate") -}}
{{ use("com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent") -}}
{{ use(aggregateType) -}}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@DomainEvent(persist = {{ persist }})
@Aggregate(
    aggregate = "{{ aggregateName }}",
    name = "{{ typeName }}",
    type = Aggregate.TYPE_DOMAIN_EVENT,
    description = {{ descriptionKotlinStringLiteral | raw }}
)
class {{ typeName }}(
    val entity: {{ aggregateName }}{% if fields.size > 0 %},{% endif %}
{% for field in fields %}
    val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
)
```

**非 design 模板**：只能用 `imports(imports)` 和 `type(...)`，不能用 `use()`。

### 11.6 Override 实战

假设你要把 `domain_event_handler` 的默认处理器改成 `@RetryableEventListener`：

```
codegen/templates/
└── design/
    └── domain_event_handler.kt.peb   ← 内容自定义
```

```kotlin
cap4k {
    templates {
        overrideDirs.from("codegen/templates")
    }
}
```

`PresetTemplateResolver` 会先在 `codegen/templates/design/domain_event_handler.kt.peb` 找到命中文件，其他模板仍走 preset。

### 11.7 可用的 Context 字段（每个模板）

| 模板 | context 关键字段 |
|------|------------------|
| `design/command.kt.peb` / `query*.kt.peb` | `packageName`, `typeName`, `description*`, `imports`, `requestFields`, `requestNestedTypes`, `responseFields`, `responseNestedTypes` |
| `design/*_handler.kt.peb` | 同上 + `requestTypeName`, `aggregateName` 等（见 `DesignQueryHandlerRenderModels`） |
| `design/validator.kt.peb` | `packageName`, `typeName`, `description*`, `valueType`, `imports` |
| `design/api_payload.kt.peb` | `packageName`, `typeName`, `description*`, `imports`, `requestFields/nestedTypes`, `responseFields/nestedTypes` |
| `design/domain_event.kt.peb` | `packageName`, `typeName`, `descriptionCommentText`, `descriptionKotlinStringLiteral`, `aggregateName`, `aggregateType`, `persist`, `imports`, `fields`, `nestedTypes` |
| `design/domain_event_handler.kt.peb` | 同上 + `domainEventTypeName` |
| `aggregate/entity.kt.peb` 等 | 详见 `cap4k-plugin-pipeline-generator-aggregate` 中各 planner |
| `flow/entry.json.peb` / `entry.mmd.peb` | `jsonContent` / `mermaidText` |
| `flow/index.json.peb` | `jsonContent` |
| `drawing-board/document.json.peb` | `jsonContent` |

---

## 12. Bootstrap 脚手架

Bootstrap 与常规 pipeline 相互独立，只产生初始目录/脚本（不写业务代码）。

当前唯一实现的 preset 是 `ddd-multi-module`（`DddMultiModuleBootstrapPresetProvider`），对应模板包 `ddd-default-bootstrap`。

### 12.1 `IN_PLACE`：官方默认模式

默认 `bootstrap.mode = IN_PLACE`。这时产物直接落到**当前受管宿主根**，不会再隐式创建 `{projectName}/...` 前缀目录。

`projectName` 在这里仍然有意义，但它只负责项目标识，例如：

- `settings.gradle.kts` 的 `rootProject.name`
- bootstrap 生成的固定模块命名与模板上下文

示意布局：

```
<managed root-host>/
├── build.gradle.kts          ← 仅 bootstrap-owned `cap4k { bootstrap { ... } }` section 受管并可重复 merge
├── settings.gradle.kts       ← 仅 `rootProject.name` + bootstrap-owned fixed-module `include(...)` sections 受管并可重复 merge
├── {domainModuleName}/
│   └── build.gradle.kts      ← bootstrap/module/domain-build.gradle.kts.peb
├── {applicationModuleName}/
│   └── build.gradle.kts      ← bootstrap/module/application-build.gradle.kts.peb
├── {adapterModuleName}/
│   └── build.gradle.kts      ← bootstrap/module/adapter-build.gradle.kts.peb
└── {startModuleName}/
    ├── build.gradle.kts      ← bootstrap/module/start-build.gradle.kts.peb
    └── src/main/kotlin/{basePackage}/StartApplication.kt
```

边界要点：

- `IN_PLACE` 当前**只**支持受管 `root-host` 宿主根；不会把任意已有 Gradle 根自动接管成 bootstrap 根
- 根文件的可重跑性来自 managed-section merge，不是整文件重写
- `build-logic/` 不是 preset 默认固定产物；只有配置了 `slots.buildLogic.from(...)` 才会出现
- 当前 bootstrap-managed ownership 很窄：
  - `build.gradle.kts`：bootstrap-owned `cap4k { bootstrap { ... } }` block
  - `settings.gradle.kts`：`rootProject.name` 与 bootstrap-owned fixed-module `include(...)`
- 宿主根里其他内容仍归用户所有，rerun 时应保持不动

受管根片段示例（来自 `bootstrap-sample` fixture）：

```kotlin
modules {
    domainModuleName.set("only-danmuku-domain")
    applicationModuleName.set("only-danmuku-application")
    adapterModuleName.set("only-danmuku-adapter")
    startModuleName.set("only-danmuku-start")
}
slots {
    root.from("codegen/bootstrap-slots/root")
    modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
    modulePackage("start").from("codegen/bootstrap-slots/start-package")
    moduleResources("start").from("codegen/bootstrap-slots/start-resources")
}
```

### 12.2 `PREVIEW_SUBTREE`：显式预览 / 教学模式

如果你需要一个独立子树来查看结构、写教程或做演示，请显式设置：

```kotlin
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

cap4k {
    bootstrap {
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("bootstrap-preview")
    }
}
```

示意布局：

```
bootstrap-preview/
├── build.gradle.kts
├── settings.gradle.kts
├── {domainModuleName}/
│   └── build.gradle.kts
├── {applicationModuleName}/
│   └── build.gradle.kts
├── {adapterModuleName}/
│   └── build.gradle.kts
└── {startModuleName}/
    ├── build.gradle.kts
    └── src/main/kotlin/{basePackage}/StartApplication.kt
```

说明：

- `PREVIEW_SUBTREE` 仍然是当前 slice 的教学 / demo escape hatch
- 这个模式下 bootstrap 拥有整个生成子树，因此 root 文件可以直接完整生成
- `build-logic/` 同样只会在配置了 `slots.buildLogic.from(...)` 时出现
- 仓库中的验证重点是通过 TestKit + plugin classpath 校验这些预览根可被生成和执行；更广义的“已发布插件独立分发体验”不在本 slice 覆盖范围内

### 12.3 Slot 扩展

Slot 是预留的"由用户提供目录、在 Bootstrap 上下文中渲染后落盘"的目录绑定：

| Slot 种类 | DSL | 落点 |
|----------|-----|------|
| ROOT | `slots.root.from(...)` | 根目录 |
| BUILD_LOGIC | `slots.buildLogic.from(...)` | `build-logic/` |
| MODULE_ROOT | `slots.moduleRoot("domain").from(...)` | `{moduleName}/` 根 |
| MODULE_PACKAGE | `slots.modulePackage("domain").from(...)` | `{moduleName}/src/main/kotlin/{rolePackageRoot}/...`（`domain|application|adapter` 对应 `{basePackage}/{role}`，`start` 对应 `{basePackage}`） |
| MODULE_RESOURCES | `slots.moduleResources("start").from(...)` | `{moduleName}/src/main/resources/` |

Slot 内容与固定模板都走同一渲染器：`sourcePath`/`templateId` 被解析为模板文本后按 Pebble 上下文渲染。
当前没有 raw-copy slot mode；每个 slot 目录也没有多目标路由（单一 `kind + role` 绑定落到单一路径族）。

Slot 落点会随模式重定位：

- `IN_PLACE`：相对当前受管宿主根落盘
- `PREVIEW_SUBTREE`：相对 `previewDir/` 落盘

### 12.4 任务

- `cap4kBootstrapPlan`：规划并写到 `build/cap4k/bootstrap-plan.json`（不写盘）
- `cap4kBootstrap`：按 `bootstrap.mode` 落盘
  - `IN_PLACE`：写向当前受管宿主根；根 `build.gradle.kts` / `settings.gradle.kts` 通过 managed-section merge 处理，非根产物仍遵循 `conflictPolicy`（默认 `FAIL`）
  - `PREVIEW_SUBTREE`：写到 `previewDir/`；该子树下的根 `build.gradle.kts` / `settings.gradle.kts` 同样通过 managed-section merge 处理，其余产物仍遵循普通导出语义与 `conflictPolicy`

---

## 13. 二次开发指南

**请先阅读**：本小节面向的是**仓库内贡献者 / fork 维护者**，不是插件外部使用者。除 § 13.1 模板覆盖外，所有扩展都要求**修改本仓库、编译并发布新版插件**，而不是在外部 Gradle 工程里动态注入。

原因：`SourceSnapshot` 是 sealed interface；`CanonicalModel` 是固定字段 data class；provider 列表在 `PipelinePlugin.buildRunner()` 里硬编码组合；没有 ServiceLoader / DI 自动发现。这是有意为之的边界（见 § 16）。

扩展点按改动范围从小到大：

### 13.1 新增 / 覆盖模板（零代码）

见 § 11.6。只需要把 `.peb` 文件放到 `templates.overrideDirs` 里即可 —— 这是**唯一**不需要改仓库代码的扩展点。

### 13.2 新增 Source Provider

#### 先考虑替代方案

由于 `SourceSnapshot` 是 sealed interface（见 § 3.2），**不能在独立模块里定义新的 snapshot 子类型**。动手前先评估：

- **替代方案 A（推荐）——扩展现有 provider**：直接在现有的 `SourceProvider` 里扩展采集逻辑，或在 api 模块里给现有 snapshot 补字段。例如新的设计元数据字段，直接加到 `DesignSpecEntry`，并在 `DesignJsonSourceProvider.parseFile` 里读进去。
- **替代方案 B（条件性可行）——新写一个 provider 返回现有 snapshot**：此路径**只对 `DefaultCanonicalAssembler` 合并消费的 snapshot 类型成立**。当前合并策略按类型而定：
  - `EnumManifestSnapshot`：`flatMap` 所有 definitions —— 多 provider 可共存
  - `KspMetadataSnapshot`：`flatMap` aggregates 后 `associateBy(aggregateName)` —— 多 provider 可共存，但同名聚合后出现的会被静默覆盖
  - `DesignSpecSnapshot` / `DbSchemaSnapshot` / `IrAnalysisSnapshot`：**`firstOrNull()`** —— 只会取第一个命中，后面的 provider 结果被完全丢弃。这种情况必须改 assembler 的合并策略，或退回到方案 A / C

  实现此路径前务必**先读 `DefaultCanonicalAssembler.assemble()`** 确认你的目标 snapshot 类型在合并分支还是 `firstOrNull` 分支。

- **方案 C（全新 snapshot）**：确实需要一个全新的 snapshot 类型。此时必须跨模块改动（api + 新 source 模块 + core 的 assembler + gradle 模块本身），按下面完整步骤执行。

#### 方案 C 完整步骤

1. **改 api 模块**：在 `cap4k-plugin-pipeline-api/.../PipelineModels.kt` 里追加 snapshot 子类型：

   ```kotlin
   data class XxxSnapshot(
       override val id: String = "xxx",
       // 你的字段
   ) : SourceSnapshot
   ```

   必须在此处，因为 `SourceSnapshot` 是 sealed。

2. **新建源模块** `cap4k-plugin-pipeline-source-{xxx}`，其 `build.gradle.kts`：

   ```kotlin
   plugins { id("buildsrc.convention.kotlin-jvm") }
   dependencies {
       implementation(project(":cap4k-plugin-pipeline-api"))
   }
   ```

3. **实现 SourceProvider**：

   ```kotlin
   class XxxSourceProvider : SourceProvider {
       override val id: String = "xxx"
       override fun collect(config: ProjectConfig): XxxSnapshot {
           val opts = config.sources[id]?.options ?: emptyMap()
           // 读取文件 / 远端 / 数据库 ...
           return XxxSnapshot(/* ... */)
       }
   }
   ```

4. **让 gradle 模块依赖新源模块**（否则下一步 import 不到）：在 `cap4k-plugin-pipeline-gradle/build.gradle.kts` 的 `dependencies` 块里追加：

   ```kotlin
   implementation(project(":cap4k-plugin-pipeline-source-xxx"))
   ```

5. **注册到 settings**：在根 `settings.gradle.kts` 的 pipeline `include(...)` 里加入 `cap4k-plugin-pipeline-source-xxx`。

6. **扩展 DSL**：`Cap4kSourcesExtension` 里追加 `xxx { enabled; ... }`，在 `Cap4kExtension.sources` 暴露 builder。

7. **翻译到 SourceConfig**：`Cap4kProjectConfigFactory.buildSources` 里把 DSL 状态翻译为 `SourceConfig(enabled, options)`；在 `validateGeneratorDependencies` 里补充相关依赖校验（例如"generator X 要求 source xxx 启用"）。

8. **注册 provider**：在 `PipelinePlugin.buildRunner()` 的 `sources = listOf(...)` 里加入 `XxxSourceProvider()`。

9. **消费 snapshot**：扩展 `DefaultCanonicalAssembler`，识别 `XxxSnapshot` 并把数据折叠进 `CanonicalModel` 的既有或新增切片。**新增切片意味着要改 api 模块的 `CanonicalModel` data class**，成本随之上升。

10. **写测试**：在 `cap4k-plugin-pipeline-gradle/src/test/resources/functional/` 下加 fixture，在 `PipelinePluginFunctionalTest` 添加对应用例。

**最小化参考**：`cap4k-plugin-pipeline-source-enum-manifest` 是单文件实现，是最精简的 Source 参考。

---

### 13.3 新增 Generator Provider

`GeneratorProvider` 不是 sealed，但 `CanonicalModel` 是一个固定字段的 data class —— 如果 generator 需要读取**新的**模型切片，同样要改 api 模块。

#### 步骤

1. **新建 generator 模块** `cap4k-plugin-pipeline-generator-{xxx}`，其 `build.gradle.kts`：

   ```kotlin
   plugins { id("buildsrc.convention.kotlin-jvm") }
   dependencies {
       implementation(project(":cap4k-plugin-pipeline-api"))
   }
   ```

2. **实现 planner**：

   ```kotlin
   class XxxArtifactPlanner : GeneratorProvider {
       override val id: String = "xxx"
       override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
           val moduleRoot = config.modules["application"] ?: error("application module required")
           return model.someSlice.map { item ->
               ArtifactPlanItem(
                   generatorId = id,
                   moduleRole = "application",
                   templateId = "xxx/my.kt.peb",
                   outputPath = "$moduleRoot/src/main/kotlin/.../${item.name}.kt",
                   context = mapOf("name" to item.name, "imports" to emptyList<String>()),
                   conflictPolicy = config.templates.conflictPolicy,
               )
           }
       }
   }
   ```

3. **让 gradle 模块依赖新模块**：`cap4k-plugin-pipeline-gradle/build.gradle.kts` 追加 `implementation(project(":cap4k-plugin-pipeline-generator-xxx"))`。

4. **注册到 settings**：根 `settings.gradle.kts` 里 `include("cap4k-plugin-pipeline-generator-xxx")`。

5. **放模板**：在 `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/xxx/my.kt.peb` 放默认模板（用户仍可用 `overrideDirs` 覆盖）。

6. **扩展 DSL**：`Cap4kGeneratorsExtension` 追加 `xxx { enabled }`。

7. **配置工厂**：`Cap4kProjectConfigFactory` 里：
   - `buildModules`：新增模块角色映射
   - `buildGenerators`：`GeneratorConfig` 填充
   - `validateProjectRules` / `validateGeneratorDependencies`：依赖校验

8. **注册 provider**：`PipelinePlugin.buildRunner()` 的 `generators = listOf(...)` 里加入 `XxxArtifactPlanner()`。

9. **写 fixture 和 functional test**。

**重要约束**：如果 generator 需要读取 source 原始快照中尚未建模的数据，**先**在 api 模块的 `CanonicalModel` 里加字段、**再**在 `DefaultCanonicalAssembler` 里填充，**最后**由 generator 消费。不要让 generator 直接读 `SourceSnapshot`。

---

### 13.4 替换 / 增强 Renderer

`ArtifactRenderer` 不是 sealed，可以从外部模块实现，但仍需 `cap4k-plugin-pipeline-gradle` 对它建立依赖并在 `buildRunner()` 里替换。

**完全替换 Pebble**：

```kotlin
class MyArtifactRenderer(
    private val resolver: TemplateResolver,
) : ArtifactRenderer {
    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
        return planItems.map { /* 用 handlebars / velocity / Kotlin DSL 渲染 */ }
    }
}
```

需要改动：
1. 新建模块并被 `cap4k-plugin-pipeline-gradle` `implementation`
2. 在 `PipelinePlugin.buildRunner()` 里把 `renderer = ...` 换成你的实现

**仅给 Pebble 加函数/过滤器**（推荐的轻量路线）：

1. 实现一个 `AbstractExtension`（参考 `PipelinePebbleExtension`），覆写 `getFunctions()` / `getFilters()`
2. 改 `PebbleArtifactRenderer.newEngine()`，在 `.extension(...)` 链里把你的 extension 追加上
3. 重新打包 `cap4k-plugin-pipeline-renderer-pebble`

---

### 13.5 新增 Template Preset

1. 在 `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/my-preset/` 下放齐全部模板（建议从 `ddd-default/` 拷贝后裁剪，避免 `Template not found` 运行时错误）
2. 用户 `cap4k { templates { preset = "my-preset" } }` 切换

**注意**：保持 templateId 与内置 preset 一致 —— generator 的 `plan()` 方法里写死了 templateId 字符串，新 preset 缺文件会在渲染阶段抛 `Template not found`。

---

### 13.6 新增 Bootstrap Preset

`BootstrapPresetProvider` 不是 sealed，可以在独立模块实现。但仍需 `cap4k-plugin-pipeline-gradle` 建立依赖并改 `buildBootstrapRunner()`。

1. 实现 `BootstrapPresetProvider`：

   ```kotlin
   class MyBootstrapPresetProvider : BootstrapPresetProvider {
       override val presetId: String = "my-preset"
       override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> = buildList {
           add(BootstrapPlanItem(
               presetId = presetId,
               outputPath = "build.gradle.kts",
               conflictPolicy = config.conflictPolicy,
               templateId = "bootstrap/root/build.gradle.kts.peb",
               context = mapOf("projectName" to config.projectName),
           ))
           // ...
       }
   }
   ```

2. 让 `cap4k-plugin-pipeline-gradle/build.gradle.kts` `implementation` 包含此 provider 的模块（放进 `cap4k-plugin-pipeline-bootstrap` 也可以）
3. 在 `PipelinePlugin.buildBootstrapRunner()` 的 `providers = listOf(...)` 里加入
4. 把相应模板放到 `presets/my-bootstrap-preset/bootstrap/...`
5. 用户 `cap4k { bootstrap { preset = "my-preset" } }` 切换

---

### 13.7 自定义 ArtifactExporter

`ArtifactExporter` 不是 sealed，可以外部实现。默认 `FilesystemArtifactExporter` 已处理：根目录沙盒、相对路径校验、ConflictPolicy、父目录自动创建 —— 除非你有"输出到 jar/远程存储/内存缓冲"这种硬需求，否则不建议替换。

```kotlin
interface ArtifactExporter {
    fun export(artifacts: List<RenderedArtifact>): List<String>
}
```

步骤：实现接口 → 让 `cap4k-plugin-pipeline-gradle` 依赖到含此类的模块 → 在 `PipelinePlugin.buildRunner()` 里替换 `exporter = ...`。

---

## 14. 诊断与排错

### 14.1 常见错误与对策

| 错误信息 | 原因 | 对策 |
|----------|------|------|
| `project.basePackage is required.` | DSL 没填 `basePackage` | 在 `project { basePackage = "..." }` 补上 |
| `project.domainModulePath is required when designDomainEvent is enabled.` | 启用了 domain 类生成但没配模块路径 | 参见 6.1 校验表 |
| `design generator requires enabled designJson source.` | Generator 依赖被违反 | 参见 6.4 依赖表 |
| `sources.designJson.files must not be empty when designJson is enabled.` | 启用但列表为空 | 用 `files.from(...)` 或 `manifestFile` |
| `design manifest entry escapes projectDir: ../secret.json` | manifest 路径逃逸 | 路径必须在工程目录内 |
| `ksp-metadata inputDir does not exist: ...` | 上游 `kspKotlin` 没跑 | 先 `./gradlew :domain:kspKotlin` 或依赖自动推断应当生效 |
| `domain_event X must declare exactly one aggregate` | design-json 条目 `aggregates` 字段不是单元素数组 | 每个 domain_event 只能绑一个聚合 |
| `domain_event X references missing aggregate metadata: Y` | KSP 元数据里找不到 `Y` | 确认 `aggregate-Y.json` 文件在 `kspMetadata.inputDir` 下 |
| `db table X is unsupported for aggregate generation: missing_primary_key / composite_primary_key` | 聚合要求单列主键 | 改表结构，或把 `aggregate.unsupportedTablePolicy = "SKIP"` 跳过 |
| `use() import conflict: X is already bound to A, cannot also import B` | 同一 SimpleName 导入歧义 | 检查 type-registry / 字段类型，避免同名不同 FQN |
| `Template not found: presets/ddd-default/design/X.peb` | preset 缺失或 override 未命中 | 确认 `templateId`、`overrideDirs` 顺序、文件存在 |
| `Target already exists: /path/to/file` | conflictPolicy=FAIL 撞文件 | 改 SKIP/OVERWRITE 或删除目标 |
| `Artifact output path must be relative` | Planner 产生绝对路径 | 插件内错误，检查 planner 实现 |
| `enabled generators have no registered providers: xxx` | Gradle 配置启用了未注册的 generator id | 确认 `buildRunner` 中已注册，或 DSL 值有误 |

### 14.2 `plan.json` 阅读建议

运行 `./gradlew cap4kPlan` 后查看 `build/cap4k/plan.json`：

- `items[]` 每项代表一个待生成文件
- `diagnostics.aggregate.unsupportedTables` 暴露表结构问题（即使 policy=SKIP 也会记录）
- 当 plan 中某个期望的 item 缺失 → 八成是 Canonical 侧过滤掉了（tag 未匹配、aggregates 不合法等）；去 `DefaultCanonicalAssembler` 对应分支排查

### 14.3 调试渲染

把某个 planner 的输出打印到 stdout：

```kotlin
override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
    val items = /* ... */
    items.forEach { println("[xxx] ${it.outputPath} ctx=${it.context.keys}") }
    return items
}
```

或在 `DefaultPipelineRunner.run` 处打 breakpoint，观察 `planItems` 与 `renderedArtifacts`。

---

## 15. 测试 Fixture 索引

`cap4k-plugin-pipeline-gradle/src/test/resources/functional/` 包含可直接运行的参考工程（由 `PipelinePluginFunctionalTest` 驱动）：

| Fixture | 用途 |
|---------|------|
| `design-sample` | 全量 design 族（cmd/qry/cli + handlers）+ override 模板 |
| `design-validator-sample` | validator 家族最小样例 |
| `design-api-payload-sample` | api_payload + 嵌套字段 |
| `design-domain-event-sample` | domain_event + handler + KSP 元数据 |
| `design-default-value-invalid-sample` | 默认值非法时的 fail-fast |
| `design-manifest-sample` | `manifestFile` 模式 |
| `design-type-registry-sample` | `types.registryFile` 短名解析 |
| `aggregate-sample` | 基本聚合（单表） |
| `aggregate-policy-sample` | `unsupportedTablePolicy` 行为 |
| `flow-sample` | IR 分析图 → flow |
| `flow-compile-sample` | `cap4kPlan` 对 `compileKotlin` 的依赖推断 |
| `drawing-board-sample` | drawing-board 输出 |

参考写法：

```kotlin
val projectDir = Files.createTempDirectory("my-fixture")
copyFixture(projectDir, "design-domain-event-sample")
val result = GradleRunner.create()
    .withProjectDir(projectDir.toFile())
    .withPluginClasspath()
    .withArguments("cap4kGenerate")
    .build()
assertTrue(result.output.contains("BUILD SUCCESSFUL"))
```

---

## 16. 治理与边界

（摘自 `AGENTS.md` / `docs/superpowers/mainline-roadmap.md`，改动前请先阅读）

### 16.1 不可重启的边界

- **流水线阶段顺序不可由用户定制**
- **用户只能开/关 source/generator，不能注入运行时逻辑**
- 兄弟 design 条目之间的类型互引不支持
- 短名自动解析保持保守
- FQN + 符号身份是导入的真理源
- `use()` 仅限 design 模板，且只做显式 import
- Bootstrap / arch-template 必须作为独立能力，不得回灌到 design 模板助手层

### 16.2 三条工作轨道（不要混）

1. **主线**：design-generator 质量（Phase B 正在进行，domain_event 家族为当前下一 slice）
2. **真实项目集成边界**：仅在 `specs/*-integration-*-design.md` 列表内推进
3. **Bootstrap / arch-template 迁移**：独立 slice，不混入 design

### 16.3 参考文档

- [AGENTS.md](../AGENTS.md)
- [mainline-roadmap.md](../docs/superpowers/mainline-roadmap.md)
- [原始重设计 spec](../docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- [docs/superpowers/specs/](../docs/superpowers/specs/) 下所有具体 slice spec

---

## 附录 A：Generator id → 模块角色 / 模板 快查表

| Generator id | 模块 role | 主模板 | 输出位置模板 |
|--------------|-----------|--------|-------------|
| `design` | application | `design/command.kt.peb` 等 | `{application}/src/main/kotlin/{pkg}/application/{commands|queries}/{sub}/{Name}{Cmd|Qry}.kt` |
| `design-query-handler` | adapter | `design/query*_handler.kt.peb` | `{adapter}/src/main/kotlin/{pkg}/adapter/application/query/{sub}/{Name}QryHandler.kt` |
| `design-client` | application | `design/client.kt.peb` | `{application}/src/main/kotlin/{pkg}/application/clients/{sub}/{Name}Cli.kt` |
| `design-client-handler` | adapter | `design/client_handler.kt.peb` | `{adapter}/src/main/kotlin/{pkg}/adapter/application/client/{sub}/{Name}CliHandler.kt` |
| `design-validator` | application | `design/validator.kt.peb` | `{application}/src/main/kotlin/{pkg}/application/validators/{sub}/{UpperCamel}.kt` |
| `design-api-payload` | adapter | `design/api_payload.kt.peb` | `{adapter}/src/main/kotlin/{pkg}/adapter/portal/api/{sub}/{UpperCamel}.kt` |
| `design-domain-event` | domain | `design/domain_event.kt.peb` | `{domain}/src/main/kotlin/{pkg}/domain/{sub}/events/{Name}DomainEvent.kt` |
| `design-domain-event-handler` | application | `design/domain_event_handler.kt.peb` | `{application}/src/main/kotlin/{pkg}/application/subscribers/domain/{sub}/{Name}DomainEventSubscriber.kt` |
| `aggregate` | domain+application+adapter | `aggregate/*.kt.peb` | 多处，见 9.8 |
| `flow` | project | `flow/entry.{json|mmd}.peb`, `flow/index.json.peb` | `{generators.flow.outputDir}/...` |
| `drawing-board` | project | `drawing-board/document.json.peb` | `{generators.drawingBoard.outputDir}/...` |

---

## 附录 B：类型解析规则

1. **优先级**：FQN（含 `.`） > type-registry 简单名 > Kotlin 内置类型 > 同包短名（保守解析）
2. Kotlin 内置类型（不可在 type-registry 覆盖）：`Any Array Boolean Byte Char Collection Double Float Int Iterable List Long Map MutableCollection MutableIterable MutableList MutableMap MutableSet Nothing Number Pair Sequence Set Short String Triple Unit`
3. 字段默认值（`defaultValue`）是 **原样写入 Kotlin 源码的片段**；模板侧用 `| raw` 输出
4. 可空性：`nullable = true` 在模板侧渲染为 `T?`

---

## 附录 C：Design Handler 命名

| design tag | request 类名 | handler 类名 |
|-----------|-------------|-------------|
| `cmd` | `{Name}Cmd` | — |
| `qry` | `{Name}Qry` | `{Name}QryHandler`（按响应形态自动选 plain/list/page） |
| `cli` | `{Name}Cli` | `{Name}CliHandler` |
| `validator` | `{UpperCamel}` | — |
| `api_payload` | `{UpperCamel}` | — |
| `domain_event` | `{Name}DomainEvent`（如需） | `{Name}DomainEventSubscriber` |

---

**贡献须知**：修改 DSL 或 Canonical Model 时，请同步更新本 README + `CLAUDE.md` + 影响到的 fixture，并为新的错误分支添加 functional test。
