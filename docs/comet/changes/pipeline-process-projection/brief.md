# Outcome

收口 GitHub Issue #55，证明完整的外部事实因果链在默认 entry-centered Pipeline Flow 中保持连续，并明确两个各自具有真实入口证据的入口仍生成两张独立 Flow。

本 Change 不新增 process projection，不改变默认 `flow` 的产品身份、wire 或输出目录。它通过专门的 focused regression fixture、Gradle functional fixture 和跨入口阅读规则，关闭旧项目中“业务过程被拆开”这一未决调查。

# Scope

- 以 `origin/master@d9c22bf25a62699037354a4cabc41c2e26d44812` 为基线，读取 Issue #55 和 canonical `pipeline-causal-flow-contract`。
- 增加完整 focused regression fixture，覆盖 `Inbound Integration Event -> hidden handler -> Command -> hidden command handler/entity method -> Domain Event -> hidden handler -> follow-up Command`，并断言只生成一张 entry-centered Flow。
- 增加等价 Pipeline Gradle functional fixture，通过 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 实际物化 JSON、Mermaid 和 index。
- 验证两个各自具备真实入口证据、最终投影后均为零入度的入口继续生成两张 Flow，即使共享下游后缀。
- 更新 Public Docs 与 Skill，说明连续链、多个真实入口、共享后缀以及多张 Flow 的关联阅读规则。
- 审计 Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill 的传播闭包，不新增 process output、capability identity 或公开 task。

# Non-goals

- 不新增独立或自动的 process projection。
- 不改变默认 `flow` 的 root、可见节点、隐藏路径收缩、循环和去重语义。
- 不把所有入口、服务或 bounded context 拼成一张大图。
- 不从静态可达性推断运行时顺序、事务顺序、消息必达或业务结果。
- 不恢复已退役的 `cap4k-plugin-code-analysis-flow-export`、`cap4kFlow*` tasks、alias 或第二套旧 Flow 入口。
- 不修改或验证长期未更新的 `cap4k-reference-content-studio`，不声明该下游项目兼容性。
- 不引入用户自定义拓扑算法、遍历 DSL 或运行时代码注入；未来若出现稳定需求，另行设计由插件拥有的命名 projection profile。
- 不在本 Change 处理普通 `validator` raw Graph observation 的去留；`unique-validator` 保持退役。

# Acceptance examples

- A1：给定完整的入站 Integration Event 到 follow-up Command 链，默认 `flow` 生成一张 entry JSON、一张 Mermaid，并在 index 中报告 `flowCount = 1`。
- A2：A1 的公开节点只包含实际入口、Command、Domain Event、Integration Event；Handler 和 Entity Method 被隐藏且路径正确收缩。
- A3：给定两个各自具有真实入口证据、最终投影后均为零入度的入口，默认 `flow` 生成两张独立 Flow，即使它们共享下游后缀。
- A4：有上游因果边的中间 Integration Event、Command 或 sender 不被提升为额外 root。
- A5：默认 `flows/<entry>.json`、`flows/<entry>.mmd`、`flows/index.json`、generator id `flow` 和 output id `flow` 保持不变。
- A6：focused tests 以一个完整 fixture 同时覆盖入站事件、Command、Domain Event、follow-up Command、隐藏路径压缩和单 Flow 计数，不只依赖分段组合测试。
- A7：Gradle functional fixture 实际执行 analysis plan/generate，核对 JSON、Mermaid、index 的路径、entry identity、节点/边计数和 `flowCount`。
- A8：Public Docs 与 Skill 明确：连续 graph evidence 产生一张 Flow；两个真实 root 产生两张 Flow；共享后缀不是自动 stitching 的理由。
- A9：代码事实、AgentFacts、Public Docs 和 Skill 中不存在 process generator、process output、process task 或第二套 Flow 产品入口。
- A10：完整 Gradle 检查、capability contract export/validate/test、Skill validator、current Runtime facts validator、PR workflow tests 与 `git diff --check` 全部通过。

# Constraints and invariants

- `AnalyzerSnapshot.graph` 是默认 Flow 的唯一静态事实输入；Drawing Board 与 Aggregate Structure 不得补造因果关系。
- 入口资格来自生产代码或明确 relationship evidence；入度为零不是入口资格的充分条件。
- root 只在完成可见投影和隐藏路径收缩后判断。
- Command、Domain Event、Integration Event 和实际入口保持可见；Command/Event Handler 与 Entity Method 保持隐藏。
- 路径压缩支持任意长度的已知隐藏因果角色，不把未知 Graph 节点自动当成可穿透中间件。
- 默认 Flow 允许循环，以稳定 visited node/edge 语义有限结束，不强制 DAG。
- 拓扑算法继续由 Kotlin 生产代码拥有；Pebble 模板只负责写出 planner 已生成的 JSON 或 Mermaid 内容，不决定节点、边、root 或压缩规则。
- Public Docs、Skill 和 AgentFacts 只描述已落地代码状态，历史和未采用方案保留在 Issue、Git 与 Comet archive。

# Decisions

- 采用方案 1：保持只有 entry-centered Pipeline Flow，不新增 process projection。
- 当前实现已经支持 Integration Event、Command、Domain Event 和 follow-up Command 在同一入口 Flow 中连续投影。
- 两个独立真实入口生成两张 Flow 是正确行为；共享下游不是自动 process stitching 的充分证据。
- Issue #55 的实现缺口是完整专名 regression fixture、Gradle functional evidence 和阅读规则，而不是新的 stitching 算法。
- `cap4k-reference-content-studio` 长期未更新，本 Change 使用仓库内等价 fixture，不对该项目作兼容性声明。
- Flow topology、root 和路径压缩继续写在 Kotlin 中；模板不承担图语义。
- 本 Change 不开放用户自定义图算法。需要不同图的高级用户可以消费 raw Graph；重复出现的产品需求应另行形成插件拥有、合同固定的 projection profile。
- `SagaCoordinator` 不是当前代码事实；普通 `validator` 仅保留为 raw Graph observation 且被默认 Flow 排除，其进一步退役不属于 #55。

# Open questions

- 无。

# Verification expectations

- focused tests 覆盖完整入站事件链、任意长度隐藏路径压缩、单 Flow 计数、两个真实入口、共享后缀、循环和稳定去重。
- Gradle functional fixture 实际执行 analysis plan/generate，并核对 JSON、Mermaid、index 的路径、entry identity 与计数。
- capability facts 必须继续只枚举 `pipeline.generator.flow` 和 output `flow`，且 Analyzer Graph consumer closure 保持一致。
- Public Docs 与 Skill 的当前支持描述必须与测试和生产 descriptor 一致，不出现 process projection 已支持的声明。
- 运行完整 `./gradlew check`，以及 capability facts export/validate/test、Skill、Runtime facts、PR body/workflow 和 `git diff --check`。
- 自动化证据与真实项目证据分开记录；本 Change 不要求运行已长期漂移的 downstream 项目。
