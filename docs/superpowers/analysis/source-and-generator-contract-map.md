# Source And Generator Contract Map

## Purpose

本 map 是 cap4k pipeline 输入源、生成器 ID、design-json 交互标签和关键选择规则的当前事实索引。目标读者是维护 agent 和少量人类维护者：先用它定位代码锚点，再以源码、测试和 Gradle task 为准修正分析。

## Current Facts

- 已确认的 `SourceProvider` / `SourceSnapshot` ID 包括 `db`、`design-json`、`enum-manifest`、`value-object-manifest`、`ir-analysis`。`db`、`design-json`、`enum-manifest`、`value-object-manifest` 属于 source generation 路径；`ir-analysis` 属于 analysis generation 路径。
- source generation 和 analysis generation 是不同职责。`cap4kPlan` / `cap4kGenerate` 使用 `sourceTaskConfig`，筛选 `db`、`design-json`、`enum-manifest`、`value-object-manifest`。`cap4kGenerateSources` 使用 `generatedSourceTaskConfig`，只筛选 `db`、`enum-manifest`。`cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 使用 `analysisTaskConfig`，只筛选 `ir-analysis`。`flow` 和 `drawing-board` 只属于 analysis task。
- Gradle task 另行筛选 source-generation generator IDs；`cap4kGenerateSources` 只保留 `aggregate`、`aggregate-projection`。
- 已从源码确认的 source-generation generator IDs 包括 `aggregate`、`aggregate-projection`、`enum`、`types-value-object`、`command`、`query`、`query-handler`、`client`、`client-handler`、`api-payload`、`domain-event`、`domain-subscriber`、`integration-event`、`integration-subscriber`、`domain-service`、`saga`。
- 已从源码确认的 analysis generator IDs 是 `flow` 和 `drawing-board`。
- `DesignJsonSourceProvider` 当前支持的 normal design-json `tag` 是 `command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga`。这些 tag 也被 `DefaultCanonicalAssembler` 的 `SupportedDesignBlockTags` 和 drawing-board 支持列表复用。
- `validator` 不是当前 normal design-json tag；`DesignJsonSourceProviderTest` 明确验证 `validator` 会抛出 `Unsupported design tag: validator`。`value_object` 也不在当前 supported tag set；当前 value object 走 `value-object-manifest` source 和 `types-value-object` generator。
- `client` 是 design tag 和 generator family；默认 artifact selection 会同时选择 `client` 与 `client-handler`。当前术语是 `client` / `client-handler`，不要把 handler 写成旧式混合术语。
- `integration_event` 是 design-json tag；`integration-event` 是 artifact/generator family，variant 只能是 `inbound` 或 `outbound`。`DesignJsonSourceProvider` 要求 `integration_event` 必须声明 `eventName`、至少在生成 event contract 时有 `fields`，并禁止 `resultFields`。
- `integration_event` 的默认 artifact selection 是 `integration-event:outbound`。如果要生成 inbound event contract，design block 必须显式选择 `integration-event` 且 variant 为 `inbound`。如果要生成 subscriber，必须显式选择 `integration-subscriber`，且同一 block 必须选择 `integration-event:inbound`，否则 assembler / planner 会失败。
- `domain_event` 可省略 `package`，它的包会从目标 aggregate root 包推导；`domain_event` 字段名 `entity` 是保留字段，由 `aggregates[0]` 派生。
- `query`、`client`、`api_payload` 可以声明 `resultFields`；其他 tag 不能声明 `resultFields`，`integration_event` 有专门错误信息。
- design-json 读取 UTF-8，支持 `files` 列表或 `manifestFile` + `projectDir`。manifest entry 会 canonicalize，禁止逃出 `projectDir`，禁止重复和空 entry。

## Source Anchors

- `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`
- `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`

## Contracts

- Source ID contract: config key、provider `id`、snapshot `id` 必须一致。新增 source 时必须同时检查 provider 注册、Gradle config filtering、snapshot model 和 task input snapshot。
- Generator ID contract: config key、planner `id`、`ArtifactPlanItem.generatorId` 必须一致。新增 generator 时必须检查 `DefaultPipelineRunner` 是否要求显式配置、Gradle source / analysis filtering、plan JSON 和 compile wiring。
- Design tag contract: normal design-json tag 只能使用当前 supported tag set。任何新增 tag 必须同时更新 `DesignJsonSourceProvider`、`DefaultCanonicalAssembler`、drawing-board supported tags、generator tests 和 public fixture expectations。
- Artifact selection contract: `artifacts = null` 表示使用 default artifact selection；`artifacts = []` 表示显式空选择。`DefaultCanonicalAssembler` 会校验 family / variant 组合，例如 `query` 允许 `query:page`，`api-payload` 允许 `api-payload:page`，`integration-event` 只允许 `inbound` / `outbound`。
- `integration_event` contract: event shape、inbound/outbound 行为和 subscriber 选择是三层规则。event shape 由 `eventName`、`fields`、`resultFields` 限制决定；inbound/outbound 由 `integration-event` variant 决定；subscriber 由显式 `integration-subscriber` selection 决定，且只允许 inbound。
- Source generation contract: `cap4kPlan` / `cap4kGenerate` 处理 checked-in skeleton 和普通生成计划；`cap4kGenerateSources` 只输出 `GENERATED_SOURCE`。不要把 analysis-only `flow` / `drawing-board` 加入普通 source generation task。
- Analysis generation contract: `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 只从 `ir-analysis` 输入构建 `flow` 和 `drawing-board` 输出；它们是观察 / 分析产物，不是 source skeleton 的替代入口。

## Change Impact

- 修改 design-json tag、字段或 artifact selection 会影响 source parse、canonical assembly、design generator、drawing-board 回写和 fixture compile tests。
- 修改 generator ID 会影响 Gradle extension config、task filtering、plan JSON、template conflict policy override、addon namespace 校验和用户文档中的 generator key。
- 修改 `integration_event` 默认 selection 或 subscriber 规则，会直接影响 inbound contract、outbound event、subscriber skeleton 和 flow/drawing-board 的交互可视化。
- 把 `ir-analysis` 混入 source generation 会让 analysis outputs 进入普通 plan/generate 语义，破坏 `cap4kPlan` / `cap4kGenerate` 忽略 `flow` 和 `drawing-board` 的现有测试。

## Verification

在 PowerShell 中，`cap4k-plugin-pipeline-*` 作为 `rg` 路径参数可能被原样传给 ripgrep 并触发 `os error 123`。使用显式目录列表更稳定：

```powershell
rg -n "command|query|client|api_payload|domain_event|integration_event|domain_service|saga|validator|value_object" cap4k-plugin-pipeline-source-design-json cap4k-plugin-pipeline-generator-design
```

```powershell
rg -n "client-handler|client[/]cli|aggregate-projection|integration-subscriber" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-source-design-json cap4k-plugin-pipeline-source-ir-analysis cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-flow cap4k-plugin-pipeline-generator-drawing-board
```

```powershell
rg -n 'override val id: String = "[^"]+"' cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-generator-flow cap4k-plugin-pipeline-generator-drawing-board cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-source-design-json cap4k-plugin-pipeline-source-enum-manifest cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-source-ir-analysis
```

## Drift Watch

- 如果代码重新加入 `validator` normal design-json tag，必须同时更新 supported tag list、设计生成器、artifact families 和测试；在此之前，`validator` 只应作为 aggregate unique validator 或 addon/provider 相关事实出现。
- 如果 value object 入口从 `value-object-manifest` 改为 design-json tag，必须重写本 map 的 source ID、tag、generator 和 ownership 描述。
- 如果 `integration_event` 默认从 outbound 改为 inbound，或者 subscriber 不再要求 inbound，必须同步修改 flow、drawing-board 和 design generator tests。
- 如果 Gradle task filtering 改变，必须重新确认 `SOURCE_TASK_SOURCE_IDS`、`GENERATED_SOURCE_TASK_SOURCE_IDS`、`ANALYSIS_TASK_SOURCE_IDS` 以及对应 generator ID set。

## Not Covered

- 不覆盖 template 内容的逐行渲染规则。
- 不覆盖 DB schema inference、aggregate relation inference 和 special field policy 的详细规则。
- 不覆盖 addon provider 的业务语义，只记录 addon namespace 和 provider ID 校验会受 generator contract 影响。