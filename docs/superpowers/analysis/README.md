# cap4k 分析导航

`docs/superpowers/analysis/` 面向 cap4k 维护代理和 human maintainers，不面向 public users。本 landing page 是 Phase 1 分析地图的导航入口，路由到目标 map slots；每个 map 只有在后续从 source anchors 创建并验证后，才成为对应主题的事实索引。维护时当分析内容与代码冲突，code wins over analysis。

历史 specs/plans 不是默认阅读材料。需要追溯背景时再单独查阅，不从本导航作为 active maps 进入。

## Phase 1 Target Map Slots

以下链接是 Phase 1 计划创建的目标 active map slots，不表示这些 map 已经存在或已经完成事实校验。

- [architecture-map.md](architecture-map.md) - 模块边界、核心架构和代码定位。
- [pipeline-and-gradle-map.md](pipeline-and-gradle-map.md) - Gradle pipeline、tasks、plugin DSL 和构建入口。
- [source-and-generator-contract-map.md](source-and-generator-contract-map.md) - source IDs、generator IDs、design JSON 和生成契约。
- [artifact-output-and-ownership-map.md](artifact-output-and-ownership-map.md) - artifact 输出、ownership、generated-vs-handwritten 边界。
- [runtime-and-integration-map.md](runtime-and-integration-map.md) - runtime tactical behavior、集成入口和运行期约束。
- [analysis-flow-and-verification-map.md](analysis-flow-and-verification-map.md) - compiler analysis、flow、drawing-board 和验证链路。
- [release-map.md](release-map.md) - Maven Central release 和发布检查入口。
- [documentation-and-skill-drift-map.md](documentation-and-skill-drift-map.md) - downstream docs/public/ 与 skills/ drift 检查入口。

## Quick Reading Path

- module orientation -> [architecture-map.md](architecture-map.md)
- Gradle task or DSL change -> [pipeline-and-gradle-map.md](pipeline-and-gradle-map.md)
- source ID, generator ID, or design JSON change -> [source-and-generator-contract-map.md](source-and-generator-contract-map.md)
- output ownership or generated-vs-handwritten boundary -> [artifact-output-and-ownership-map.md](artifact-output-and-ownership-map.md)
- runtime tactical behavior -> [runtime-and-integration-map.md](runtime-and-integration-map.md)
- compiler analysis, flow, drawing-board -> [analysis-flow-and-verification-map.md](analysis-flow-and-verification-map.md)
- Maven Central release -> [release-map.md](release-map.md)
- public docs or skills drift -> [documentation-and-skill-drift-map.md](documentation-and-skill-drift-map.md)