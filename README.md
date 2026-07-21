# cap4k

[![CI](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml/badge.svg)](https://github.com/LDmoxeii/cap4k/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ldmoxeii/ddd-core)](https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core)
[![GitHub Release](https://img.shields.io/github/v/release/LDmoxeii/cap4k)](https://github.com/LDmoxeii/cap4k/releases)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/LDmoxeii/cap4k/blob/master/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/LDmoxeii/cap4k)

cap4k 是面向后端业务系统的 DDD 战术框架与生成器协作工具，用 Clean Architecture、明确的生成/手写边界和可审阅证据帮助团队把业务模型落成代码。

<!-- IMAGE_PROMPT:
Purpose: 帮助首次读者快速理解 cap4k 的心智模型：业务建模、分层实现、生成器、人工审阅和证据输出如何协作。
Type: concept map
Prompt: Create a clean concept map for cap4k as a backend DDD tactical framework. Show a central cap4k node connected to business model, Clean Architecture layers, generator-backed authoring, handwritten business logic, reviewable plan output, analysis evidence, and the cap4k-reference-content-studio example project. Use a restrained technical documentation style.
Must show: business model, aggregate/command/query/events, domain/application/adapter/start layers, generator plan, generated skeletons, handwritten logic, analysis evidence, cap4k-reference-content-studio
Must avoid: 不要暗示 cap4k 会自动写业务决策；不要把每个 callback 都画成 Saga；不要把生成器画成会替代业务建模和 ownership review；不要暗示当前核心能力超出后端 DDD authoring 范围；不要画出违反 Clean Architecture 依赖方向的箭头
Alt text after insertion: cap4k 心智模型图，展示业务模型、分层架构、生成器、手写逻辑、审阅计划、分析证据和参考项目之间的关系。
-->

## 它解决什么问题

很多后端项目知道自己需要 DDD、分层架构和可维护边界，但真正落地时容易退回到“一个 service 包办写入、读取、外部调用和事务”的结构。cap4k 解决的是这个落差：让聚合、命令、查询、事件、Saga、仓储、Unit of Work、Mediator、外部能力防腐层等战术概念在项目结构、生成结果和审阅证据中保持可见。

cap4k 的生成器不替代业务判断。它把已建模的业务意图、设计输入和类型清单投影成稳定代码槽位，并通过 `plan.json`、生成输出和分析证据帮助团队审阅“哪些由生成器管理，哪些必须由人写”。

## 核心亮点

- DDD 战术建模：围绕 Aggregate、Entity、Value Object、Command、Query、Domain Event、Integration Event、Saga 等概念组织业务代码。
- Clean Architecture 分层：把 domain、application、adapter、start 的责任和依赖方向拆清楚。
- 生成器支撑作者流程：用 `design.json`、`types.valueObjectManifest`、`types.enumManifest`、Gradle DSL 和源代码分析输入生成可审阅骨架。
- 明确所有权边界：通过 `cap4kPlan` 和生成计划先审阅输出、冲突策略、模板和 managed sections，再执行生成。
- 分析证据：通过 `cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate` 输出流程、依赖和 drawing-board 证据，辅助实现后的复盘。
- 参考项目锚点：`cap4k-reference-content-studio` 提供四层多模块项目、发布流程、Value Object 类型输入和 `PaidPublicationSaga` 示例。

## 两条最短路径

Learning cap4k:

```text
mental model -> docs/public/index.md -> concepts -> architecture -> cap4k-reference-content-studio
```

先用本页建立心智模型，再进入 [Public Documentation](docs/public/index.md)，按 [concepts](docs/public/concepts/index.md) 和 [architecture](docs/public/architecture/index.md) 建立词汇与分层理解，最后用 `cap4k-reference-content-studio` 对照真实项目。

Creating new project:

```text
architecture -> docs/public/generator/bootstrap-project-structure.md -> authoring -> generator inputs/plan/generation
```

先确认 [architecture](docs/public/architecture/index.md) 的分层边界，再看 [bootstrap-project-structure](docs/public/generator/bootstrap-project-structure.md) 建立新项目结构，随后进入 [authoring](docs/public/authoring/index.md)，按 generator inputs、plan review、generation 的顺序推进。

## 文档地图

- [docs/public/index.md](docs/public/index.md)：按读者目标进入文档。
- [concepts](docs/public/concepts/index.md)：DDD 战术概念和执行所有权。
- [architecture](docs/public/architecture/index.md)：Clean Architecture 分层、依赖规则和按层测试。
- [examples](docs/public/examples/index.md)：以 `cap4k-reference-content-studio` 为主的阅读与运行锚点。
- [authoring](docs/public/authoring/index.md)：从业务意图、技术设计、生成输入到实现和验证的作者循环。
- [generator](docs/public/generator/index.md)：bootstrap、输入来源、计划审阅、生成任务和分析证据。
- [reference](docs/public/reference/index.md)：Gradle 插件、DSL、JSON 输入、计划、输出、分析和运行时 SQL 的精确查表。

## 什么时候适合

- 你在做后端业务系统，且需要把业务规则、事务边界、读写分离和外部能力边界写清楚。
- 你希望生成器先产出可审阅骨架和计划，再由人补全关键业务逻辑。
- 你愿意把 Command、Query、Domain Event、Integration Event、Saga 等概念作为代码结构的一部分。
- 你需要把实现结果和分析证据交给团队或 AI 助手复查。

## 什么时候不适合

- 你只需要一次性产物，而不需要长期维护的领域边界、ownership evidence 和手写业务逻辑。
- 你需要的主要能力不在后端业务建模、分层实现和生成证据范围内。
- 你希望工具自动决定业务边界、业务规则、补偿策略或跨系统语义。
- 你的项目不准备采用 DDD 战术概念或 Clean Architecture 分层。

## 当前 Gradle 入口

Gradle plugin id:

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}
```

常用任务：

- `cap4kBootstrapPlan`：生成新项目结构的 bootstrap 计划，用于先审阅。
- `cap4kBootstrap`：按 bootstrap 配置创建或更新项目结构。
- `cap4kPlan`：根据当前输入生成 `build/cap4k/plan.json`，用于审阅生成输出和所有权。
- `cap4kGenerate`：按计划生成 checked-in source 或 output artifact。
- `cap4kGenerateSources`：生成参与源码集的 generated source。
- `cap4kAnalysisPlan`：生成 `build/cap4k/analysis-plan.json`。
- `cap4kAnalysisGenerate`：生成代码分析、流程和 drawing-board 等证据。

精确字段、输出路径和任务关系见 [Gradle plugin reference](docs/public/reference/gradle-plugin.md)。

## License / links

- License: [MIT](LICENSE)
- English README: [README.en.md](README.en.md)
- Public docs: [docs/public/index.md](docs/public/index.md)
- Reference project: sibling repository `cap4k-reference-content-studio`; usage details: [reference-content-studio](docs/public/examples/reference-content-studio.md)
