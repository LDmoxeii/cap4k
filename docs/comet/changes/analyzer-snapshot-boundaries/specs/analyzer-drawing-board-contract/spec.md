# Analyzer Drawing Board 合同

## 目标

Analyzer 的 Drawing Board output 表达从生成 skeleton 代码证据中恢复的规范化战术设计。它服务于人类和 Agent 的结构审阅，也可以在用户明确选择后作为普通 Design JSON source 再次生成；它不承担任意代码结构浏览、领域正确性证明或运行时行为追踪。

本合同在现有 compiler-backed 自动化往返能力上增加 Analyzer 三分区完整性接入。仓内 fixture 使用真实 compiler Analyzer 和两个隔离项目提供可重复回归；长期未更新的下游参考项目不是本合同的硬门禁，未执行时必须明确记录且不得伪装为通过。

## 产品边界

- Drawing Board 的唯一 Analyzer source 是 `AnalyzerSnapshot.designProjection`。
- 每个 design block 对应一个当前支持的 Design JSON tactical entry，保留 tag、package、name、description、aggregate ownership、event semantics、artifact selection、fields、resultFields、type identity、nullability、default semantics 和声明顺序。
- Drawing Board 恢复规范化战术设计，不恢复任意 Kotlin class、方法体、Repository adapter、Entity Method、Aggregate carrier、SQL table 或 JPA mapping。
- Analyzer observation 不自动反馈 Generator。只有明确的人类、Agent 或测试配置才能把 Drawing Board design files 注册为普通 `design-json` source。
- `drawing_board_aggregate_elements.json` 不属于 Drawing Board design blocks，不参与往返等价比较；它遵循 Aggregate Structure 分区合同。

## 往返合同

### 规范化等价

用户明确把 Drawing Board design files 作为 Design JSON 输入时，以下语义必须保持等价：

- tag、package、name、description 和 aggregate ownership；
- fields、resultFields 及嵌套 DTO 的声明顺序；
- 解析后的 canonical type identity、container 结构和 nullability；
- artifact family、variant 和 primary/secondary selection；
- Domain Event、Integration Event 的方向、`persist`、`eventName` 和支持的 default expression；
- 生成 skeleton 所需的框架声明、annotation metadata、wiring contract 和框架拥有的结构 carrier。

以下物理差异可以被 normalization 忽略：

- 文件名、文件数量和物理目录分区；
- JSON 格式和 entry/artifact/file 顺序；
- 可选空数组的省略；
- 默认值的省略与同一有效默认值的显式表达；
- 能解析到同一 canonical FQN 的类型拼写差异。

Normalization 不得吞掉实际战术语义，不得把缺失字段、错误 artifact、丢失 event name 或不同 type identity 当作等价。

### 证据范围

- Design Projection 只读取生成 metadata 和真实 Analyzer evidence，不从方法体、文件名或包名猜测缺失设计。
- Entity Method、Repository implementation、AggregateElement carrier、SQL/JPA mapping、Query predicate 等不属于 design block，除非它们是已确认 Design JSON 字段或 metadata 语义。
- 结构证据可以与 Design Projection 来自同一次 compiler observation，但必须由不同 partition 和 canonical owner 表达。

## 完整性与失败边界

- Drawing Board 只消费 `AnalyzerSnapshot.designProjection` 及其 sources、status 和 diagnostics。
- 没有 design candidate 的 input directory 可以产生合法空 projection；未配置、不可用、partial、invalid 和合法空必须可区分。
- 存在 design candidate 时，缺少 `design-elements.json`、缺少必要 metadata、JSON 无效或跨模块语义冲突必须使该 partition invalid。
- 一个完整 input directory 不能掩盖另一个不完整来源；请求 Drawing Board 时不得输出外观完整的 partial board。
- Aggregate Structure 非空不能替代 Design Projection，也不能把 `drawing_board_aggregate_elements.json` 注册为 Design JSON。
- Drawing Board 只报告静态恢复证据覆盖和 artifact freshness，不报告领域模型正确、代码行为正确或 runtime 已执行。

## 输出与身份

- 保留公开 generator/output identity：`pipeline.generator.drawing-board` 和 `drawing-board`。
- 保留按 tag 输出的 `drawing_board_<tag>.json` 以及现有 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` observation lane。
- Drawing Board design files 继续使用普通 Design JSON array shape，因此可以由 `design-json` source 显式注册；不会自动回灌。
- Agent `analysis.json` 只在 evidence model wire 实现后投影 Design Projection partition 的 status、counts、sources、freshness、outputs 和 diagnostics。

## 自动化往返门禁

仓内 compiler-backed 自动化必须建立两个隔离 project：

1. Project A 读取规范化 Design JSON，生成并编译 skeleton，通过真实 compiler Analyzer 输出 Drawing Board design files；
2. Project B 禁用或移除原 Design JSON，只显式注册 Project A 的 Drawing Board design files，再生成并编译；
3. 比较 Project A 原始 canonical design 与 Project B recovered canonical design；
4. 比较两次生成的 framework-owned skeleton 和关键 runtime annotations/carriers；
5. 覆盖所有支持的 tactical tag、artifact variants、event semantics、复杂字段、type identity、nullability、default expression 和声明顺序。

现有 `DesignRoundTripFunctionalTest` 是该自动化门禁的基础，迁移 AnalyzerSnapshot 后必须继续通过，不能用简化 mock 替代真实 compiler Analyzer。

## 下游项目证据边界

- `cap4k-reference-content-studio` 等 sibling repository 可以提供额外集成证据，但不属于本合同的 CI、Verify 或 Archive 硬门禁。
- 下游项目长期未更新、需要先迁移已退役输入时，可以停止本次验证；verification 必须记录未执行原因和已观察到的 project drift，不能写成已通过。
- 下游验证缺失不得用于放宽 Analyzer 分区、Drawing Board 往返或 metadata 完整性合同；后续完成项目现代化后可以追加独立集成证据。

## Capability propagation

- 当前支持事实由生产 code 和 tests 派生后，才更新 AgentFacts、Public Docs 和 Skill。
- Design Projection 的直接 consumer 是 Drawing Board；Aggregate Structure 和 Graph 不得作为替代 input。
- Public Docs 只能声明已经由生产代码和仓内 compiler-backed 自动化证明的 round-trip 范围，不得把 canonical target contract 或未执行的下游验证当作实现证据。

## 非目标

- 将 Drawing Board 变成任意代码浏览器、runtime trace 或 database schema viewer；
- 自动注册 Analyzer output 为 Generator input；
- 将 `drawing_board_aggregate_elements.json` 当 Design JSON；
- 修改默认 Flow、增加 process projection 或恢复独立 flow exporter；
- 升级或修改下游参考项目的输入、领域行为和工程结构；
- 以单一真实项目覆盖所有可能的第三方 template override 和自定义代码形态。
