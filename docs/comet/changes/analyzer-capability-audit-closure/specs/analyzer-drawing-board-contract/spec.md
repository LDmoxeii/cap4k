# Analyzer Drawing Board 合同

## 目标

Analyzer 的 Drawing Board output 必须表达从生成 skeleton 代码证据中恢复的规范化战术设计。它服务于人类和 Agent 的结构审阅，也可以在用户明确选择后作为普通 Design JSON 输入再次生成；它不承担任意代码结构浏览、领域正确性证明或运行时行为追踪。

## 产品边界

- Drawing Board 的 canonical model 是 `AnalyzerSnapshot.designProjection`，最小事实是一条规范化 design block。
- 每个 design block 对应一个当前受支持的 Design JSON tactical entry，保留已确认的 tag、package、name、description、aggregate ownership、event semantics、artifact selection、fields、resultFields、type identity、nullability、default semantics 和声明顺序。
- Drawing Board 恢复的是规范化战术设计，不是任意 Kotlin class、方法体、Repository adapter、Entity Method 或 Aggregate carrier 的完整代码结构。
- Analyzer observation 不自动反馈 Generator。只有明确的人类或 Agent 操作，才能把 Drawing Board design block 注册为普通 `design-json` source。
- `drawing_board_aggregate_elements.json` 不属于 Drawing Board design blocks，也不参与 Design JSON round-trip 等价比较；它遵循独立的 Aggregate Structure contract。

## 往返合同

### 规范化等价

当用户明确把 Drawing Board design blocks 作为 Design JSON 输入时，以下规范化战术语义必须保持等价：

- tag、package、name、description 和 aggregate ownership；
- fields、resultFields 及嵌套 DTO 的声明顺序；
- 解析后的 canonical type identity、container 结构和 nullability；
- artifact family、variant 和 primary/secondary selection；
- Domain Event、Integration Event 的方向、`persist`、`eventName` 和受支持的 default expression 语义；
- 生成 skeleton 所需的框架声明、annotation metadata、wiring contract 和其他框架拥有的结构 carrier。

以下物理差异可以被 normalization 忽略：

- 文件名、文件数量和物理目录分区；
- JSON 格式、entry/artifact/file 顺序；
- 可选空数组的省略；
- 默认值的省略与同一有效默认值的显式表达；
- 能解析到同一 canonical FQN 的类型拼写差异。

Normalization 不得吞掉实际战术语义，不得把缺失字段、错误 artifact、丢失 event name 或不同 type identity 当作等价。

### 证据范围

- Design projection 只读取生成 metadata 和实际 Analyzer 证据，不从方法体推断 operation contract，不从文件名或包名猜测缺失设计。
- Entity Method、Repository implementation、AggregateElement carrier、SQL table、JPA mapping、Query predicate 等不属于 design block，除非它们以已确认的 Design JSON 字段或 metadata 语义被明确投影。
- 结构证据可以与 design projection 同时来自一次 compiler observation，但必须由不同的 canonical owner 表达；消费者不得把另一个分区的字段复制进 Drawing Board model。

## 完整性与失败边界

- 请求 Drawing Board 时，受影响 analysis input directory 的 design metadata、raw evidence 或必要 source identity 不完整，必须失败或返回明确不可用状态。
- 一个完整 input directory 不得掩盖另一个存在候选节点但缺少恢复证据的 directory；不得生成看似完整的 partial board。
- 合法的空 design projection、未配置、不可用、部分和无效必须使用可区分状态，不能把 Aggregate Structure 的非空记录当成 design block 以制造伪输出。
- Drawing Board 只报告静态恢复证据的覆盖和新鲜度，不报告领域模型正确、代码行为正确或运行时已经执行。

## 输出与身份

- 保留现有公开 generator/output identity：`pipeline.generator.drawing-board` 和 `drawing-board`。
- `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 仍是 observation lane；Drawing Board 不是普通 source-generation task 的隐式输入。
- `analysis.json`、AgentFacts、Public Docs 和 Skill 只能在代码事实与治理传播闭包实现后投影当前状态；本合同不改变现有 wire schema。

## 验证证据

- 代码与 focused tests：`DesignElementSnapshot`、`DrawingBoardArtifactPlanner`、`IrAnalysisSourceProvider` 和其测试证明字段、artifact selection、完整性和 output identity 的当前事实。
- 真实 round-trip gate：项目 A 生成并编译 skeleton，在真实 compiler Analyzer 上输出 Drawing Board；项目 B 禁用原 Design JSON，仅显式导入 A 的 Drawing Board，再生成并编译，分别比较规范化 design projection 和框架 skeleton。
- 自动化 source/test 证据与真实项目 round-trip 证据必须分开记录。spec-only Archive 不得被当作 round-trip 实现通过。

## 未实施边界

- 本合同确认目标产品语义；本 Change 不实现完整 `designProjection` canonical refactor、新 wire schema、per-partition 完整性状态或双项目 round-trip gate。
- 当前 Build 只因 Aggregate Structure 闭环而移除 `DrawingBoardModel` 对 `aggregateElements` 的重复 canonical ownership；现有 design block 输出、公开 `drawing-board` identity 和 Design JSON 行为不得被该结构调整改变。
- 未实现目标不得同步为 Public Docs、AgentFacts 或 Skill 的当前支持能力。
- 未纳入本 Change Build 的后续实现必须从届时最新 `origin/master` 创建独立 Comet Change 和短期分支，并通过独立 PR 合入 `master`。

## 非目标

- 将 Drawing Board 变成任意代码浏览器、运行时 trace 或数据库 schema viewer。
- 将 `drawing_board_aggregate_elements.json` 当作 Design JSON，或把 Aggregate Structure 自动注册为 Generator 输入。
- 让 Analyzer 输出自动成为 Generator 输入。
- 在 Shape 阶段修改产品代码、Public Docs、AgentFacts、Skill 或 validator；Build 只能修改 Shape 已纳入当前验收范围的能力及其必需投影。