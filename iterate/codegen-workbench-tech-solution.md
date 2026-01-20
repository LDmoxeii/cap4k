# cap4k codegen 工作台化重构技术方案

## 一、需求背景
当前 `cap4k-plugin-codegen` 与 `cap4k-plugin-codegen-ksp` 的代码生成链路由多个 Gradle Task 直接编排，流程逻辑分散在 Task 内部，扩展或替换生成步骤需要修改任务实现。希望参考 LiteFlow 的工作台模式，将流程组织为“全局上下文 + 无状态节点 + 链路编排”，以提升可维护性与可扩展性。

## 二、需求说明
1. 构建统一的 Codegen 工作台执行模型，所有节点通过共享上下文协作。
2. 生成链路可配置（arch/design/aggregate），新增节点无需改 Task 代码。
3. 默认链路输出与现有版本一致，支持回退到旧实现。
4. 增强可观测性（节点耗时/执行轨迹）与可控性（dryRun、跳过节点）。

## 三、可行性分析
1. 现有 `ContextBuilder` 与 `*TemplateGenerator` 已具备节点化边界，迁移成本低。
2. KSP 元数据已落盘为 JSON，可作为工作台上下文输入，无需更改格式。
3. LiteFlow 只需借鉴链路编排思想即可，避免引入复杂依赖。
4. 通过配置开关保留旧链路，回退与比对成本可控。

## 四、联调接口
无新增对外接口。仅新增 Gradle 插件配置项与可选链路文件。

## 五、详细设计

### 5.1 总体架构
工作台由三部分组成：
1. WorkbenchContext：全局上下文，保存 baseMap、模板索引、设计/聚合数据、运行参数与执行轨迹。
2. WorkbenchNode：无状态/弱状态节点，只读取或写入上下文。
3. FlowExecutor：读取链路定义并调度节点执行。

执行链路示例（支持并行组 WHEN，细粒度节点）：
```
arch=THEN(initContext,initPebble,loadArchTemplate,renderArch)

design.context=THEN(
  WHEN(design.loadDesignFiles,design.loadKspMetadata),
  design.typeMapping,
  design.unifiedDesign
)
design.generate=THEN(
  WHEN(design.gen.command,design.gen.query,design.gen.client,design.gen.domainEvent,design.gen.validator,design.gen.apiPayload),
  WHEN(design.gen.domainEventHandler,design.gen.queryHandler,design.gen.clientHandler)
)
design=THEN(arch,design.context,design.generate)

aggregate.context=THEN(
  agg.table,
  WHEN(agg.entityType,agg.annotation,agg.module,agg.relation,agg.uniqueConstraint,agg.enum),
  agg.aggregate,
  agg.package
)
aggregate.generate=THEN(
  WHEN(agg.gen.schemaBase,agg.gen.enum),
  WHEN(agg.gen.enumTranslation,agg.gen.entity,agg.gen.uniqueQuery,agg.gen.uniqueQueryHandler,agg.gen.uniqueValidator),
  WHEN(agg.gen.specification,agg.gen.factory,agg.gen.domainEvent,agg.gen.domainEventHandler,agg.gen.repository),
  agg.gen.aggregate,
  agg.gen.schema
)
aggregate=THEN(arch,aggregate.context,aggregate.generate)
```

### 5.2 核心类设计

#### 5.2.1 WorkbenchContext
统一上下文建议实现为单一类，兼容 `MutableDesignContext` 与 `MutableAggregateContext`：
```kotlin
class CodegenWorkbenchContext(
    val base: BaseContext,
    val log: LoggerAdapter,
    val fileWriter: FileWriter,
    val options: WorkbenchOptions
) : BaseContext by base,
    MutableDesignContext,
    MutableAggregateContext {

    val attributes: MutableMap<String, Any?> = mutableMapOf()
    val trace: ExecutionTrace = ExecutionTrace()
    val outputRegistry: OutputRegistry = OutputRegistry()

    override val designElementMap = mutableMapOf<String, MutableList<DesignElement>>()
    override val aggregateMap = mutableMapOf<String, AggregateInfo>()
    override val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

    override val tableMap = mutableMapOf<String, Map<String, Any?>>()
    override val columnsMap = mutableMapOf<String, List<Map<String, Any?>>>()
    override val relationsMap = mutableMapOf<String, Map<String, String>>()
    override val tablePackageMap = mutableMapOf<String, String>()
    override val entityTypeMap = mutableMapOf<String, String>()
    override val tableModuleMap = mutableMapOf<String, String>()
    override val tableAggregateMap = mutableMapOf<String, String>()
    override val annotationsMap = mutableMapOf<String, Map<String, String>>()
    override val enumConfigMap = mutableMapOf<String, Map<Int, Array<String>>>()
    override val enumPackageMap = mutableMapOf<String, String>()
    override val uniqueConstraintsMap = mutableMapOf<String, List<Map<String, Any?>>>()
}
```

#### 5.2.2 WorkbenchNode
节点统一接口，执行过程中只能通过 context 共享数据：
```kotlin
interface WorkbenchNode {
    val id: String
    val order: Int
    val writeScopes: Set<String> get() = emptySet()
    fun supports(ctx: CodegenWorkbenchContext): Boolean = true
    fun execute(ctx: CodegenWorkbenchContext)
}
```
说明：同一 WHEN 并行组内的节点若写域有交集，则执行器应降级为串行执行或加互斥锁。

#### 5.2.3 FlowDefinition 与执行器
```kotlin
sealed interface FlowNode
data class Then(val nodes: List<FlowNode>) : FlowNode
data class When(val nodes: List<FlowNode>) : FlowNode
data class Ref(val id: String) : FlowNode

data class FlowDefinition(
    val name: String,
    val root: FlowNode
)

class FlowExecutor(
    private val registry: NodeRegistry,
    private val options: WorkbenchOptions
) {
    fun execute(flow: FlowDefinition, ctx: CodegenWorkbenchContext) { /* ... */ }
}
```

#### 5.2.4 NodeRegistry
支持手工注册或 ServiceLoader 发现：
```kotlin
class NodeRegistry {
    private val nodes = mutableMapOf<String, WorkbenchNode>()
    fun register(node: WorkbenchNode) { nodes[node.id] = node }
    fun get(id: String): WorkbenchNode = nodes[id] ?: error("Node not found: $id")
}
```

#### 5.2.5 OutputRegistry
用于并行生成时的输出占位与去重：
```kotlin
class OutputRegistry {
    private val registry = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun claim(key: String): Boolean = registry.add(key)
}
```

### 5.3 节点列表与职责
基础节点（顺序执行）：
| 节点 ID | 责任 | 现有类映射 | writeScopes |
| --- | --- | --- | --- |
| initContext | 初始化上下文、清空模板索引 | `AbstractCodegenTask` 初始化逻辑 | `context` |
| initPebble | 初始化 Pebble 引擎 | `PebbleInitializer` | `engine` |
| loadArchTemplate | 读取 archTemplate 并 resolve | `GenArchTask.loadTemplate` | `template` |
| renderArch | 渲染目录与模板索引 | `AbstractCodegenTask.render` | `templateIndex` |

Design 上下文节点（允许并行组）：
| 节点 ID | order | 责任 | 现有类映射 | writeScopes |
| --- | --- | --- | --- | --- |
| design.loadDesignFiles | 10 | 读取 design.json 并解析 | `DesignContextBuilder` | `designElementMap` |
| design.loadKspMetadata | 15 | 读取 KSP 聚合元数据 | `KspMetadataContextBuilder` | `aggregateMap` |
| design.typeMapping | 18 | 构建 typeMapping | `TypeMappingBuilder` | `typeMapping` |
| design.unifiedDesign | 20 | 构建 designMap | `UnifiedDesignBuilder` | `designMap` |

Design 生成节点（order 同组可并行）：
| 节点 ID | order | 责任 | 现有类映射 | writeScopes |
| --- | --- | --- | --- | --- |
| design.gen.command | 10 | 生成 Command | `CommandGenerator` | `fs,typeMapping` |
| design.gen.query | 10 | 生成 Query | `QueryGenerator` | `fs,typeMapping` |
| design.gen.client | 10 | 生成 Client | `ClientGenerator` | `fs,typeMapping` |
| design.gen.domainEvent | 10 | 生成 DomainEvent | `DomainEventGenerator` | `fs,typeMapping` |
| design.gen.validator | 10 | 生成 Validator | `ValidatorGenerator` | `fs,typeMapping` |
| design.gen.apiPayload | 10 | 生成 ApiPayload | `ApiPayloadGenerator` | `fs,typeMapping` |
| design.gen.domainEventHandler | 20 | 生成 DomainEventHandler | `DomainEventHandlerGenerator` | `fs` |
| design.gen.queryHandler | 20 | 生成 QueryHandler | `QueryHandlerGenerator` | `fs` |
| design.gen.clientHandler | 20 | 生成 ClientHandler | `ClientHandlerGenerator` | `fs` |

Aggregate 上下文节点（允许并行组）：
| 节点 ID | order | 责任 | 现有类映射 | writeScopes |
| --- | --- | --- | --- | --- |
| agg.table | 10 | 表/列基础信息 | `TableContextBuilder` | `tableMap,columnsMap` |
| agg.entityType | 20 | 实体类型 | `EntityTypeContextBuilder` | `entityTypeMap` |
| agg.annotation | 20 | 注释/注解信息 | `AnnotationContextBuilder` | `annotationsMap` |
| agg.module | 20 | 模块信息 | `ModuleContextBuilder` | `tableModuleMap` |
| agg.relation | 20 | 关联关系 | `RelationContextBuilder` | `relationsMap` |
| agg.uniqueConstraint | 20 | 唯一约束 | `UniqueConstraintContextBuilder` | `uniqueConstraintsMap` |
| agg.enum | 20 | 枚举信息 | `EnumContextBuilder` | `enumConfigMap,enumPackageMap` |
| agg.aggregate | 30 | 聚合信息 | `AggregateContextBuilder` | `tableAggregateMap` |
| agg.package | 40 | 包信息 | `TablePackageContextBuilder` | `tablePackageMap` |

Aggregate 生成节点（order 同组可并行）：
| 节点 ID | order | 责任 | 现有类映射 | writeScopes |
| --- | --- | --- | --- | --- |
| agg.gen.schemaBase | 10 | Schema 基类 | `SchemaBaseGenerator` | `fs,typeMapping` |
| agg.gen.enum | 10 | 枚举 | `EnumGenerator` | `fs,typeMapping` |
| agg.gen.enumTranslation | 20 | 枚举翻译 | `EnumTranslationGenerator` | `fs` |
| agg.gen.entity | 20 | 实体 | `EntityGenerator` | `fs,typeMapping` |
| agg.gen.uniqueQuery | 20 | 唯一约束查询 | `UniqueQueryGenerator` | `fs` |
| agg.gen.uniqueQueryHandler | 20 | 唯一约束查询处理器 | `UniqueQueryHandlerGenerator` | `fs` |
| agg.gen.uniqueValidator | 20 | 唯一约束校验器 | `UniqueValidatorGenerator` | `fs` |
| agg.gen.specification | 30 | 规范 | `SpecificationGenerator` | `fs` |
| agg.gen.factory | 30 | 工厂 | `FactoryGenerator` | `fs` |
| agg.gen.domainEvent | 30 | 领域事件 | `DomainEventGenerator` | `fs,typeMapping` |
| agg.gen.domainEventHandler | 30 | 领域事件处理器 | `DomainEventHandlerGenerator` | `fs` |
| agg.gen.repository | 30 | Repository | `RepositoryGenerator` | `fs` |
| agg.gen.aggregate | 40 | 聚合封装 | `AggregateGenerator` | `fs` |
| agg.gen.schema | 50 | Schema 类 | `SchemaGenerator` | `fs` |

兼容节点（Composite，可映射到子链路）：
| 节点 ID | 责任 | 子链路 |
| --- | --- | --- |
| buildDesignContext | 旧入口，便于回退 | design.context |
| generateDesign | 旧入口，便于回退 | design.generate |
| buildAggregateContext | 旧入口，便于回退 | aggregate.context |
| generateAggregate | 旧入口，便于回退 | aggregate.generate |

说明：
1. `renderArch` 需兼容 `renderFileSwitch` 与 `dryRun`，支持只构建模板索引不落盘。
2. `loadArchTemplate` 在 context 中保存 Template 实例供后续节点使用。
3. `fs` 表示文件系统输出域；同一 WHEN 并行组内若输出路径可能冲突，需使用输出注册表或互斥控制。
4. order 相同的节点可视为并行候选组，执行器可按 order 自动拆分 WHEN 组。

### 5.4 模板渲染与索引复用
将 `AbstractCodegenTask.render` 抽取为无状态 `TemplateRenderer`：
```kotlin
class TemplateRenderer(
    private val writer: FileWriter,
    private val log: LoggerAdapter
) {
    fun render(ctx: CodegenWorkbenchContext, template: Template, rootPath: String) { /* ... */ }
}
```
渲染过程中，需要复用以下逻辑：
1. `templatePackage[tag]` 与 `templateParentPath[tag]` 计算规则保持一致。
2. `templateNodeMap` 的 tag 归一化规则沿用 `TagAliasResolver`。

### 5.4.1 并行执行策略
1. WHEN 并行组使用有界线程池，线程数由 `flow.parallelism` 控制。
2. 执行器对并行组内节点的 `writeScopes` 做冲突判定，冲突则串行或加锁。
3. 生成节点使用 `OutputRegistry` 进行输出路径与类名的占位登记，避免并行重复生成与覆盖。
4. `typeMapping` 更新在并行组内使用互斥或延迟合并，保证幂等与一致性。

### 5.5 链路定义格式
采用 LiteFlow 风格 DSL，最小支持 THEN + WHEN：
- THEN：顺序执行
- WHEN：并行执行（同组并发，需 writeScopes 无冲突）

示例（细粒度链路 + 子链路引用）：
```
design.context=THEN(WHEN(design.loadDesignFiles,design.loadKspMetadata),design.typeMapping,design.unifiedDesign)
design.generate=THEN(WHEN(design.gen.command,design.gen.query,design.gen.client,design.gen.domainEvent,design.gen.validator,design.gen.apiPayload),
                      WHEN(design.gen.domainEventHandler,design.gen.queryHandler,design.gen.clientHandler))
design=THEN(arch,design.context,design.generate)
```

解析器需支持 THEN/WHEN 以及子链路引用（Ref）：
```kotlin
class FlowParser {
    fun parse(expr: String): FlowDefinition { /* ... */ }
}
```

### 5.6 配置设计（Gradle）
在 `CodegenExtension` 中新增 `flow` 配置，复杂链路建议放在 `chainFile`，`chainExpr` 只指向顶层链路：
```kotlin
cap4kCodegen {
    flow {
        enabled.set(true)
        chainExpr.set("THEN(arch,design.context,design.generate)")
        chainName.set("design")
        chainFile.set("codegen-flow.properties")
        metadataPath.set(".../build/generated/ksp/main/resources/metadata")
        dryRun.set(false)
        parallelism.set(4)
        parallelByOrder.set(true)
        enableNodes.set(listOf("design.loadDesignFiles","design.loadKspMetadata","design.typeMapping","design.unifiedDesign"))
        disableNodes.set(listOf("design.gen.validator"))
    }
}
```
配置说明：
1. `parallelism` 控制线程池大小。
2. `parallelByOrder` 开启后，Composite 节点会按 order 自动切分 WHEN 组。

### 5.7 执行轨迹与日志
新增 `ExecutionTrace`：
```kotlin
data class TraceItem(val nodeId: String, val groupId: String, val threadName: String, val startMs: Long, val endMs: Long, val status: String)
class ExecutionTrace { val items: MutableList<TraceItem> = mutableListOf() }
```
输出建议：
1. 每个节点输出耗时与结果（success/skip/fail）。
2. WHEN 并行组输出组级别耗时与并发度。
3. `dryRun` 模式下日志明确标记“未落盘”。

### 5.8 Import 扩展与用户自定义
目标：解决导入管理器写死、用户无法扩展的问题。

设计要点：
1. 引入 ImportPipeline，将内置 ImportManager 与用户规则/扩展拼接。
2. 用户可通过 Gradle 配置或设计文件 metadata 提供额外 imports。
3. 允许按 tag/生成类名/模板名进行条件匹配。

核心接口示例：
```kotlin
interface ImportContributor {
    val id: String
    fun supports(tag: String, ctx: Map<String, Any?>): Boolean = true
    fun contribute(tag: String, ctx: Map<String, Any?>, imports: MutableSet<String>)
}

class ImportPipeline(private val contributors: List<ImportContributor>) {
    fun resolve(tag: String, ctx: Map<String, Any?>, base: Set<String>): List<String> { /* ... */ }
}
```

Gradle 配置示例：
```kotlin
cap4kCodegen {
    imports {
        rule {
            tag.set("api_payload")
            namePattern.set(".*Page.*")
            add.set(listOf("com.foo.PageResult"))
            remove.set(listOf("com.only4.cap4k.ddd.core.share.PageParam"))
        }
    }
}
```

设计文件扩展示例（design.json）：
```json
{
  "tag": "payload",
  "name": "SubmitVideoPage",
  "imports": ["com.foo.PageResult"]
}
```
说明：`DesignContextBuilder` 已会收集未知字段进入 metadata，可直接读取 `imports` 并注入 Pipeline。

### 5.9 生成计划与细粒度生成单元
目标：解决 shouldGenerate 繁琐、依赖关系难维护，以及生成粒度不匹配的问题。

核心思路：
1. Generator 不再只面向 table/design，而是先产出 GenerationUnit 列表。
2. 生成计划阶段建立依赖图，按 order + deps 拓扑排序执行。
3. 每个 Unit 显式声明输出 key 与依赖 key，避免 while + shouldGenerate。

核心结构示例：
```kotlin
data class GenerationUnit(
    val id: String,
    val tag: String,
    val order: Int,
    val deps: List<String> = emptyList(),
    val writeScopes: Set<String> = emptySet(),
    val templateNodes: List<TemplateNode>,
    val context: Map<String, Any?>
)

interface UnitGenerator {
    val tag: String
    fun collect(ctx: CodegenWorkbenchContext): List<GenerationUnit>
    fun render(unit: GenerationUnit, ctx: CodegenWorkbenchContext)
}
```

细粒度示例：
1. EnumGenerator: 产出 `EnumUnit(table, column, enumType)`，每列一个枚举。
2. DomainEventGenerator: 产出 `DomainEventUnit(table, eventName)`。
3. DomainEventHandlerGenerator: 产出 `DomainEventHandlerUnit(table, eventName)`，依赖对应 DomainEventUnit。

依赖与类型登记：
1. Unit 执行前注册 `exportTypes`（或使用 OutputRegistry + TypeRegistry）。
2. Handler Unit 依赖 `DomainEvent:<name>` 的 key，而非依赖 typeMapping。
3. typeMapping 由 Plan 扫描阶段统一写入，避免并行写冲突。

执行器行为：
1. `collect` 阶段构建全量 Unit 列表与依赖图。
2. 对可并行的 Unit 分组执行（writeScopes 无冲突）。
3. 生成结束后按 Unit 的 exportTypes 更新 typeMapping。

## 六、权限与配置
无新增权限。新增 Gradle 配置 `cap4kCodegen.flow`，用于链路选择与节点开关。

## 七、改动范围
新增：
1. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/workbench/*`
2. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/workbench/nodes/*`
3. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/workbench/flow/*`
4. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/imports/pipeline/*`
5. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/*`
6. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/workbench/plan/*`

修改：
1. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenArchTask.kt`
2. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenDesignTask.kt`
3. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenAggregateTask.kt`
4. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/extension/CodegenExtension.kt`
5. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/CodegenPlugin.kt`
6. `cap4k-plugin-codegen/build.gradle.kts`
7. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/imports/*`
8. `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/*`

可选：
1. `cap4k-plugin-codegen-ksp/README.md` 增加工作台说明

## 八、影响范围
1. 生成逻辑入口不变，保持 `cap4kGenArch` / `cap4kGenAggregate` / `cap4kGenDesign`。
2. 默认链路输出一致，支持配置回退旧实现。
3. 多模块下的 metadataPath 需显式配置或保持默认路径推断。

## 九、测试点
1. 默认链路输出与旧版对比一致（arch/design/aggregate）。
2. `dryRun` 模式不写文件，仅输出日志与模板索引。
3. 节点开关有效：禁用节点后不执行对应步骤。
4. metadataPath 不存在时设计链路安全退出。
5. WHEN 并行组开启/关闭下输出一致，避免重复生成或覆盖。
6. ImportPipeline 规则生效（add/remove/condition）。
7. GenerationUnit 依赖图可正确排序与并行执行。

## 十、风险与兼容
1. 模板索引构建顺序变化可能影响 package 解析，需保留旧逻辑。
2. 链路配置缺失关键节点会导致生成不完整，需在解析阶段校验。
3. 依赖新增或链路格式引入错误，需要提供回退开关。
4. 并行执行引入共享数据竞争（typeMapping 或输出覆盖），需在执行器或节点内部加互斥与输出注册表。
5. Unit 级别生成引入输出 key 冲突，需要在 OutputRegistry 中统一校验。

## 十一、实施步骤
1. 引入 workbench 核心抽象、节点注册表与 Flow 解析器。
2. 拆分 buildDesignContext/generateDesign/buildAggregateContext/generateAggregate 为细粒度节点。
3. 引入 ImportPipeline 与可配置规则。
4. 引入并行执行器与 `OutputRegistry`，并让 writeScopes 生效。
5. 引入 GenerationUnit/Plan，逐步替换 shouldGenerate + while 模式。
6. 添加 flow 配置并在 Task 中按开关切换新旧实现。
7. 使用现有示例设计文件和数据库结构做输出对比。
8. 完成日志与 trace 验证后，再逐步默认启用。

## 说明/假设
1. 初期支持 THEN + WHEN，并可通过 `parallelByOrder` 自动切分并行组。
2. 生成器将逐步迁移为 UnitGenerator，但模板内容保持不变。
3. 保留原有任务实现作为回退路径。
