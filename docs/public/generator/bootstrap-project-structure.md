# Bootstrap Project Structure

bootstrap 是默认的新项目结构入口，不是默认学习入口。第一次理解 cap4k 时，仍应先读 [concepts](../concepts/index.md)、[architecture](../architecture/index.md) 和 [Reference Content Studio](../examples/reference-content-studio.md)。当团队已经决定要创建一个 cap4k 项目，并希望用 generator 建立标准四层多模块结构时，再阅读本页。

`cap4kBootstrapPlan` 与 `cap4kBootstrap` 服务于结构落位。前者写出本地 `build/cap4k/bootstrap-plan.json`，让作者先读 bootstrap 将创建或保护的 root、module、package 和 template output；后者应用已审查的结构输出。

## What Bootstrap Provides

bootstrap 提供的是项目骨架结构：

- root project 和 public pipeline plugin wiring。
- domain、application、adapter、start 的四层多模块 layout。
- base package、module path 和默认 source root。
- 生成器后续读取输入和落位输出所需的基础目录。
- 可审查的 `bootstrap-plan.json` evidence。

它帮助团队从空项目进入可维护结构，但不会替代业务建模。bootstrap 之后仍然需要作者完成业务意图、Aggregate、Command、Query、Event、external capability、Saga、schema、`design/design.json` 和 type manifests。

## Equivalent Manual Layout

不用 bootstrap 也可以手工建立等价结构。典型 cap4k 项目仍应保持四层多模块：

- `*-domain`：Aggregate、Entity、Value Object、Domain Event、Factory、Specification、Domain Service、Repository contract。
- `*-application`：Command、Query、Subscriber、Saga、Scheduled Reaction、Unit of Work orchestration、external capability contract。
- `*-adapter`：Controller、payload mapping、query adapter、client-handler、persistence adapter、外部协议转换。
- `*-start`：Spring Boot runtime assembly、configuration、database schema 和 application entry wiring。

bootstrap 的价值是减少结构搭建中的命名和目录漂移；它不是唯一合法路径。无论通过 bootstrap 还是手工创建，后续 plan review 都要确认 module placement 和 Clean Architecture 依赖方向。

## What Bootstrap Does Not Replace

bootstrap 不替代这些作者输入：

- 不替代业务意图和通用语言。
- 不替代 Aggregate、Value Object、Event、policy、external capability 和 Saga 建模。
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
2. 用 bootstrap 建立或审查四层多模块结构。
3. 回到 [authoring](../authoring/index.md) 写业务意图、模型和技术设计。
4. 用 schema、`design/design.json`、`types.valueObjectManifest`、`types.enumManifest` 形成 generator inputs。
5. 用 `cap4kPlan` / `cap4kGenerate` 进入 source generation。

bootstrap 是结构入口，不是 authoring loop 的替代品。
