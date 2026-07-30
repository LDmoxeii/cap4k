# Bootstrap Project Structure

官方默认项目通过独立 GitHub Template 分发。Bootstrap 不是官方项目初始化路径，而是面向需要自定义项目结构、模板和 slots 的高级工具。第一次理解 cap4k 时，仍应先读 [concepts](../concepts/index.md)、[architecture](../architecture/index.md) 和 [Reference Content Studio](../examples/reference-content-studio.md)。只有当团队明确需要维护自己的结构模板时，再阅读本页。

`cap4kBootstrapPlan` 与 `cap4kBootstrap` 服务于结构落位。前者写出本地 `build/cap4k/bootstrap-plan.json`，让作者先读 bootstrap 将创建或保护的 root、module、package 和 template output；后者应用已审查的结构输出。

## What Bootstrap Provides

bootstrap 提供的是通用的项目骨架规划和受控写入能力：

- root project 和 public pipeline plugin wiring。
- 可配置的 domain、application、adapter、start 四模块结构 planner。
- base package、module path 和默认 source root。
- 用户通过显式 template override 和 slots 提供的具体项目文件。
- 可审查的 `bootstrap-plan.json` evidence。

cap4k 制品不再内置一份“官方默认项目”Bootstrap 模板。执行 Bootstrap 前必须显式提供团队自己的 template override；slots、managed block 和 conflict policy 继续保护这些输出。Bootstrap 不会替代业务建模，之后仍然需要作者完成业务意图、Aggregate、Command、Query、Event、Capability、持久化编排边界、schema、`design/design.json` 和 type manifests。

## Equivalent Manual Layout

官方默认项目直接使用 GitHub Template。团队也可以手工建立项目，或用自定义 Bootstrap 模板建立等价结构。典型 cap4k 项目仍应保持四层多模块：

- `*-domain`：Aggregate、Entity、Value Object、Domain Event、Factory、Domain Service、Repository contract。
- `*-application`：Command、Query、Capability、Subscriber、Scheduled Reaction 和 application orchestration。
- `*-adapter`：Controller、payload mapping、query adapter、capability-handler、persistence adapter、外部协议形状映射。
- `*-start`：Spring Boot runtime assembly、configuration、database schema 和 application entry wiring。

bootstrap 的价值是保护团队自定义结构的命名、目录和 managed section；它不是官方默认文件的第二份来源。无论通过 GitHub Template、自定义 bootstrap 还是手工创建，后续 plan review 都要确认 module placement 和 Clean Architecture 依赖方向。

## What Bootstrap Does Not Replace

bootstrap 不替代这些作者输入：

- 不替代业务意图和通用语言。
- 不替代 Aggregate、Value Object、Event、policy、external capability 和持久化编排建模。
- 不替代 DB/schema 或 DDL。
- 不替代 `design/design.json`。
- 不替代 `types.valueObjectManifest` 或 `types.enumManifest`。
- 不替代 plan review、handwritten implementation、verification evidence。

如果 bootstrap 后缺少 schema、design JSON 或 type manifests，source generation 仍然没有足够事实生成业务相关 skeleton。正确反馈路径是回到 [Generator Input Projection](../authoring/generator-input-projection.md) 和 [Inputs And Sources](inputs-and-sources.md)，而不是把结构目录当成业务模型。

## Bootstrap Review

阅读 `build/cap4k/bootstrap-plan.json` 时，重点看：

- project root 和 module path 是否符合目标项目命名。
- domain/application/adapter/start 是否清楚分层。
- base package 是否和团队约定一致。
- bootstrap 输出是否会覆盖已有手写结构。
- 后续 `cap4kPlan` 能否在这些 module 中正确落位 source generation output。

`bootstrap-plan.json` 是本地 generated evidence，不是 committed source truth。它适合在执行 bootstrap 前审查结构意图；项目真正的 source truth 仍然是提交后的 settings、Gradle files、source directories、schema、design inputs 和手写代码。

## Relation To Authoring

创建新项目时，可以这样理解顺序：

1. 用 [Architecture](../architecture/index.md) 确认四层职责。
2. 用官方 GitHub Template 建立默认项目，或在确有自定义结构需求时使用 bootstrap。
3. 回到 [authoring](../authoring/index.md) 写业务意图、模型和技术设计。
4. 用 schema、`design/design.json`、`types.valueObjectManifest`、`types.enumManifest` 形成 generator inputs。
5. 用 `cap4kPlan` / `cap4kGenerate` 进入 source generation。

bootstrap 是可选的自定义结构工具，不是官方初始化入口，也不是 authoring loop 的替代品。
