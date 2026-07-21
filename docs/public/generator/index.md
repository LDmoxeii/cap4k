# Generator

`generator/` 位于 [authoring](../authoring/index.md) 之后。authoring 说明人如何从业务意图、模型、技术设计、生成输入、计划审查、手写实现和验证反馈中循环；generator 章节说明支撑这条 authoring loop 的 mechanics：输入从哪里来、计划如何表达 ownership、哪些任务写 checked-in skeleton，哪些任务只产生 build-owned generated source，analysis evidence 又如何回到验证。

generator 不是独立于 authoring 的学习入口。第一次学习 cap4k 时，仍建议先读 [concepts](../concepts/index.md)、[architecture](../architecture/index.md) 和 [Reference Content Studio](../examples/reference-content-studio.md)。当读者已经知道自己要表达什么业务边界，再进入本章看生成器如何把显式输入转成稳定结构和可审查证据。

精确字段不在本章展开。本章会解释 `generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy` 等字段为什么重要；字段枚举、JSON shape、DSL 选项和输出路径细节请进入 [reference](../reference/index.md) 查询。

## Pages

- [Generator Backed Authoring](generator-backed-authoring.md)：解释 generator 是 architecture control，不是业务判断替代品；它把已明确的设计输入转成稳定 code slots 和 evidence。
- [Bootstrap Project Structure](bootstrap-project-structure.md)：说明 `cap4kBootstrapPlan` / `cap4kBootstrap` 如何作为新项目结构入口，和手工四层多模块布局等价。
- [Inputs And Sources](inputs-and-sources.md)：说明 Gradle extension、DB/schema、`design/design.json`、`types.valueObjectManifest`、`types.enumManifest`、source-analysis input 和 reference project examples。
- [Planning And Ownership Review](planning-and-ownership-review.md)：说明 `cap4kPlan`、`bootstrap-plan.json`、`plan.json`、`conflictPolicy`、generated-vs-handwritten ownership 和 managed sections。
- [Generation Tasks](generation-tasks.md)：说明 `cap4kGenerate`、`cap4kGenerateSources`、checked-in source、generated source、analysis task boundary 和公开 Gradle task sequence。
- [Analysis Evidence](analysis-evidence.md)：说明 `cap4kAnalysisPlan`、`cap4kAnalysisGenerate`、flows、drawing-board 和 verification 中如何使用 analysis evidence。

## Reference Lookup

需要查精确字段时，直接进入这些 reference 页面：

- [Gradle Plugin](../reference/gradle-plugin.md)：plugin id、public Gradle tasks 和任务职责。
- [Generator DSL](../reference/generator-dsl.md)：Gradle extension 中 project、sources、types、generators 和 analysis 相关配置。
- [Design JSON](../reference/design-json.md)：`command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga` 等 tag。
- [Value Object Manifest](../reference/value-object-manifest.md) 与 [Enum Manifest](../reference/enum-manifest.md)：类型输入格式。
- [Plan JSON](../reference/plan-json.md)：`plan.json` item、ownership 字段和 conflict behavior。
- [Outputs](../reference/outputs.md)：`CHECKED_IN_SOURCE`、`GENERATED_SOURCE`、`OUTPUT_ARTIFACT` 和输出根。
- [Analysis Outputs](../reference/analysis-outputs.md)：analysis plan、`analysis/flows`、`analysis/drawing-board` 和 IR analysis input。
- [Common Mistakes](../reference/common-mistakes.md)：常见误用和修正路径。

## Reading Path

创建或改造项目时，可以按这个顺序阅读：

1. 从 [authoring](../authoring/index.md) 明确业务意图、模型和技术设计。
2. 用 [Generator Input Projection](../authoring/generator-input-projection.md) 把设计投影到 schema、`design/design.json`、type manifests 和 Gradle extension。
3. 回到本章的 [Inputs And Sources](inputs-and-sources.md) 确认 generator 会读取哪些事实。
4. 用 [Planning And Ownership Review](planning-and-ownership-review.md) 阅读 plan evidence，先审查 ownership，再生成。
5. 用 [Generation Tasks](generation-tasks.md) 区分 source generation 和 build-owned generated source。
6. 用 [Analysis Evidence](analysis-evidence.md) 把 flow 与 drawing-board evidence 带回 [Verification And Feedback](../authoring/verification-and-feedback.md)。

这个顺序服务于作者判断，不是命令清单。generator mechanics 的价值在于让设计输入、输出 ownership 和验证证据可追踪，而不是替作者选择业务规则。
