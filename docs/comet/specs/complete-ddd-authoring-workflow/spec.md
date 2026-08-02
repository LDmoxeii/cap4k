# Cap4k Agent Enablement

## Purpose

让具备通用软件工程能力和基础 DDD 推理能力的 agent 准确、低成本地操作 cap4k。系统向 agent 暴露 cap4k-specific 的能力、输入、计划、生成、ownership、运行时和分析边界，但不接管人的战略 DDD、组织协作或领域决策。

## Requirements

### Responsibility boundary

- 人与组织负责领域调研、Ubiquitous Language、Subdomain/Bounded Context/Context Map 判断、业务优先级和最终领域决策。
- Agent 可以使用自身 DDD 能力与人讨论目标、事实、不变量、边界和权衡；这些讨论不要求使用 cap4k-specific strategic artifacts。
- Cap4k agent enablement 只负责把已经形成的实现意图映射到当前支持的 generator inputs、tactical carriers、runtime/provider semantics、generated/handwritten ownership 和 verification evidence。
- 系统不得要求 `strategic-seed.yaml`、`design/ddd/`、Iteration Capsule、Projection Ledger、Strategic Workspace、Strategic Change Proposal 或自定义 approval state machine 才能使用 cap4k。
- 正常代码 review、需求确认和工程验收由项目原有协作方式负责，不由 cap4k skill 重复治理。

### Thin skill role

- Skill 若存在，必须是薄的 agent-facing router/field guide，而不是完整 DDD 流程引擎。
- Skill 只保存 agent 无法从通用 DDD/编程知识可靠推导的 cap4k facts：tactical affordances、input surfaces、task sequence、output ownership、runtime/provider boundaries、analyzer authority 和 high-risk failure interpretation。
- Skill 不得通过大量阶段文件强制业务发现、战略建模、审批或迭代生命周期。
- `routing.yaml` 若保留，是唯一 skill route source，但 route 只针对 cap4k operations，例如 project inspection、carrier/input selection、plan/generate、implementation ownership、analysis 和 verification。
- Always-read surface 必须最小化；详细 carrier/input/task facts 只在相关操作时按需加载。
- 当前无外部用户，可以删除或重组全部旧 skill routes，不提供 compatibility aliases、legacy wording 或 migration workflow。
- 当前 change 只维护 cap4k repository 内的 repo-local thin skill canonical source，不生成正式 distribution artifact，不实现 installer、自动同步或跨版本 compatibility mechanism。
- 用户将先亲自使用并迭代 repo-local skill；只有实际体验证明结构稳定且分发收益足够时，才重新评估安装/复制/版本化体系。
- Thin skill 的目标是降低 cap4k-specific 认知与操作成本，不承诺让完全不学习 DDD/cap4k 的用户无知识投入地完成领域设计。用户仍需理解基础 DDD、官方四层项目结构、generator inputs、plan review、generated/handwritten ownership 与最终业务决策责任。
- 当前 change 不修改独立官方 `LDmoxeii/cap4k-template`。Template 可以作为未来 repo-local agent entry 的自然载体，但实际集成必须等 skill 经用户体验稳定后，在 Template 仓库通过独立 change 实施。

### Machine-readable Cap4k surface

- 当前 change 必须新增最小 read-only Cap4k Agent API，覆盖 project inspection、capability/input discovery、validation 与 plan/diagnostic normalization。其主要目标是移除 thin skill 中高容量、高漂移的静态 framework facts。
- Agent 必须能发现并使用最终保留的 public Gradle tasks：`cap4kPlan`、`cap4kGenerate`、`cap4kGenerateSources`、`cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`。Agent API supported catalog 不得再列出 bootstrap capability 或 bootstrap tasks。
- `build/cap4k/plan.json`、`build/cap4k/analysis-plan.json`、pipeline diagnostics、generated-source managed-root state、flow 和 drawing-board outputs 是当前 machine evidence；agent guidance 必须复用其实际 schema/semantics。
- Machine contract 应让 agent 判断 project/module shape、enabled sources/generators、supported carrier/input mapping、planned artifacts、conflict policy、output ownership、diagnostics、analysis availability 和 unsupported/provider boundaries。
- `capabilities.json` 必须同时提供 supported catalog 与 effective project view，并通过稳定 capability identity 关联。
- Supported catalog 描述当前 cap4k plugin 版本内建能力及本次 project inspection 已成功加载的 extension contributions，至少披露 capability category、provenance、required inputs/configuration、相关 Gradle tasks、output ownership 以及 runtime/provider/analyzer boundary。
- Effective project view 描述同一能力在当前 project 中是否 configured、ready、blocked 或 not-applicable，并给出缺失输入、阻塞 diagnostics 与可执行的下一步提示。
- “当前版本/extension 支持”不得被表达为“当前 project 已可立即使用”；反之，未配置的能力仍应可从 supported catalog 被 agent 发现，避免将版本能力目录重新静态复制进 skill。
- 任何新增 Agent API 必须以当前 ProjectConfig、pipeline planning、diagnostics 和 analyzer output 为权威，不得在 adapter 中维护第二套 capability truth。
- Structured result 必须提供稳定 identity、level/status、artifact/input path、message、actionable hint 和可证明范围，使 agent 无需解析易漂移的人类日志。
- 新 API 首轮保持 read-only，不替代最终保留的 `cap4kGenerate`、`cap4kGenerateSources` 或 `cap4kAnalysisGenerate` 等有副作用任务；agent 仍通过现有任务执行 mutation。
- Agent task 默认必须保持 external-I/O-safe。它可以读取 Gradle resolved configuration、本地 project-owned inputs 与现有 machine evidence，但不得在 snapshot 生成期间主动连接数据库或其他 external live source。
- 对配置了 live source 的项目，Agent API 必须报告该 source、现有 plan evidence 的可证明 freshness，以及需要显式执行的 plan task；只有 project configuration/input identity 足以证明现有 evidence 与当前状态一致时才能标记为 fresh，否则必须明确标记 stale、unknown 或 missing。没有显式运行 `cap4kPlan` 等相关 task 时，不得声称已验证最新 live schema。
- Agent API snapshot、manifest、分区和 diagnostics 必须默认执行 credential redaction：不得序列化 password、token、private key、原始 connection string 或内嵌 credential；需要表达配置存在性或来源时，只能输出类型、存在性和脱敏后的稳定 identity。
- 没有 MCP 时，agent 仍必须能够通过 Gradle/CLI machine contract 完成核心工作。
- Agent API 的首个正式 delivery surface 是 project-local Gradle task + versioned JSON contract。Task 随 consumer project 使用的 cap4k plugin 版本提供，并读取 Gradle 已解析的真实 project configuration。
- Inspection/capability core model 必须 adapter-neutral；Gradle task 只是首个 adapter，未来 CLI/MCP 必须复用同一 core，不得复制判断逻辑。
- Agent API 采用 manifest-first progressive loading。一次 Gradle task 执行生成完整分区快照，避免 agent 为读取不同详情重复承担 Gradle 启动成本；agent 默认只读取小型 `manifest.json`，再按当前任务加载所需分区。
- 默认输出位于 `build/cap4k/agent/`，至少包含 `manifest.json`、`project.json`、`capabilities.json`、`inputs.json`、`ownership.json`、`runtime.json`、`analysis.json` 与 `diagnostics.json`。具体文件内部结构由 versioned contract 定义。
- Manifest 必须包含 contract/cap4k version、project summary、各分区的路径、内容 hash、状态与数量摘要、diagnostic counts，以及足以帮助 agent 选择下一批详情的推荐分区；它不得复制全部分区正文。
- Progressive loading 只改变读取方式，不允许不同分区各自计算并漂移出不一致的 project state；同一次执行产物共同表示一个有明确 identity 的快照。
- 典型读取应保持最小：carrier/input 选择读取 manifest 加 capabilities/inputs/ownership，生成失败读取 manifest 加 diagnostics，分析任务读取 manifest 加 analysis。
- Manifest 与分区必须支持 `ok`、`partial`、`invalid`、`unavailable` 等明确状态。分区不可用时必须记录稳定原因，而不是省略文件后要求 agent 猜测。
- 当 cap4k configuration 或 generator input 无效，但 Agent task 已能启动时，task 必须尽可能写出 manifest、仍可获得的分区和 `diagnostics.json`；manifest 标记整体无效，相关分区标记不可用及原因，task 最终以非零退出码结束。
- 当 Agent API 无法取得必需 project state 时，必须尽可能留下 `unavailable` 状态和 diagnostics，并以非零退出码结束。
- 只有发生在 Agent task 注册或启动之前的 Gradle configuration/evaluation 灾难性失败，才允许完全没有 Agent API snapshot；skill 必须在这种情况下退回普通 Gradle failure evidence，不得声称读取到了结构化快照。
- 当 project 与必需 cap4k configuration 有效，但非必需分区不适用或不可用时，Agent task 必须成功退出；manifest 标记整体 `partial`，对应分区标记 `unavailable` 并给出稳定原因。
- 例如未配置 `sources.irAnalysis.inputDirs` 时，没有 analysis 详情属于可披露的非必需能力缺失，不得把原本有效的 project 判为 Agent API failure。

### Bootstrap retirement

- 当前 bootstrap 是独立子系统，包含 `cap4kBootstrapPlan`、`cap4kBootstrap`、`cap4k.bootstrap` DSL、bootstrap API/contracts、preset/slot planner、renderer、root-state guard、managed-section merger、filesystem exporter、fixtures 与文档。
- 当前 in-place repeatability 依赖 `// [cap4k-bootstrap:managed-*]` root managed markers、严格 root guard、slot bindings 与 managed-section merge；这不是普通 generator checked-in skeleton 的 handwritten ownership，也不是 managed-field handler slot。
- 当前 change 必须完整删除 bootstrap capability：删除 public Gradle tasks `cap4kBootstrapPlan`、`cap4kBootstrap`，删除 `cap4k.bootstrap` DSL/config factory，删除 bootstrap API/contracts/models、planner、runner、renderer、root-state guard、managed-section merger、filesystem exporter、bootstrap module 及其依赖。
- 删除所有 bootstrap-specific presets、slot bindings、`// [cap4k-bootstrap:managed-*]` markers、fixtures、tests、sample configuration、README/public docs、skill routes/references 和 Agent API capability entries。
- 不提供一次性初始化替代实现，不保留 no-op stub、compatibility task/DSL、deprecated alias、migration path 或 legacy detection；引用旧入口应像引用不存在的 capability 一样失败。
- 官方 GitHub Template 继续作为默认项目初始化路径；团队自定义 Template 或人工建立项目结构不属于 cap4k core bootstrap capability，当前 change 不修改独立 `LDmoxeii/cap4k-template` 仓库。
- Bootstrap retirement 不得删除或弱化普通 generator 的 checked-in skeleton ownership、aggregate behavior scaffold、handwritten logic slots、managed-field handler slots、`ConflictPolicy.SKIP` 或其他非 bootstrap 的冲突保护行为。
- 重构后的 thin skill 不承载 bootstrap authoring workflow；Agent API supported catalog 与 effective project view 只报告删除后实际存在的 framework capability。

### MCP boundary

- MCP 可以作为 machine-readable Cap4k Agent API 的可选适配器，提供 project inspection、capability discovery、input/schema/example lookup、plan、generate、analysis 和 diagnostics tools。
- MCP 不得只是公共文档搜索器，也不得复制 generator/runtime/analyzer 规则。
- MCP 不得成为 cap4k build、CI 或普通 consumer project 的强制运行依赖。
- MCP 必须调用或读取底层权威 contract；CLI/Gradle 与 MCP 对同一 project state 必须返回语义一致的结果。
- 当前 change 不实现 MCP adapter。它必须先完成并验证 adapter-neutral Agent API core 与 Gradle/versioned-JSON contract；MCP 作为独立后续 change，且只能包装经验证的同一 core/contract。

### Tactical assistance

- Agent guidance 必须覆盖当前普通 IDDD tactical carriers：Aggregate、Entity、Value Object、Strong ID、Command、Query、Capability、Domain Service、Domain Event、Integration Event、Factory、Repository、Application orchestration、Subscriber 和 Scheduled Reaction。
- Guidance 必须把业务概念解释为候选 carrier，并清楚说明选择条件、反例和不支持边界；它不能假装替用户做出领域真相判断。
- Generic Specification 不得映射为不存在的 generated carrier；应落到 invariant、Domain Service decision、repository predicate、database constraint 或显式 external implementation。
- Domain Event 必须使用显式历史事实 fields，不能持有 Entity/Aggregate；runtime validator 保持严格。
- Saga/Process Manager、Event Sourcing、full CQRS、semantic module enforcement 等非一等能力必须标为 provider/extension/unsupported，不得隐式承诺。

### Operational workflow

- Agent 进入业务项目后先检查 repository/project/module shape、cap4k configuration、generator inputs、existing generated/handwritten ownership 和 relevant source/tests；这是一项 cap4k project inspection，不是战略 DDD admission gate。
- Agent 与用户讨论实现意图后，选择当前支持的 tactical carrier 和 generator input surface。
- 对 generator-supported structure，agent 先准备/修改 input，再运行 `cap4kPlan` 并审查 plan/diagnostics；不得手写平行 skeleton。
- Plan 合理后才运行 generation，并审查实际 output ownership/conflicts。
- Agent 在 checked-in scaffold 或明确 handwritten slot 中实现业务逻辑，不修改 build-owned generated source 承载长期逻辑。
- Agent 运行与风险相称的 compile/test/runtime checks，并在可用时生成 analyzer evidence；analyzer mismatch 是检查线索，不是自动业务修复。
- Agent 向用户提交正常工程 change review，说明实际变更、验证、限制和未证明事项；不增加 cap4k-specific custom approval workflow。

### Drift resistance

- High-risk facts 应由 code-derived capability index、schemas、fixtures 或 validation checks 支撑，而不是只写在 prose 中。
- Skill validation 必须检查 route uniqueness、progressive loading、stale task/input names、output ownership、Domain Event contract、analyzer authority 和 unsupported capability claims。
- PR #152 后的 Domain Event generator/runtime contract 是当前事实，不得回退运行时 historical-fact boundary。
- #27 runnable reference project 和开放 analyzer issues 是独立 evidence/capability work；本能力必须披露其状态，但运行时 skill bundle 不依赖读取 GitHub issue。

### Delivery boundary

- 本 change 当前首先解决 #100 的 agent-facing authoring complexity，不自动扩展为组织级 DDD workflow。
- 用户已确认将 Outcome 重置为 cap4k agent enablement：thin skill 只提供 cap4k-specific guidance，战略 DDD 与组织流程不属于本 change。
- 完成目标可以通过 bounded end-to-end fixture/dry run 证明 agent 对 cap4k 的操作能力；真实 runnable reference-project 四块闭环仍由 #27 负责。
- 用户已确认当前 change 同时实现最小 read-only Agent API，并采用 Gradle-first、versioned JSON、manifest-first progressive loading、“无效输入仍落盘诊断快照并非零退出”、“非必需分区缺失时 `partial` 成功”、supported catalog + effective project view，以及默认 external-I/O-safe；MCP adapter 明确延后为独立 change。
- 用户已确认当前 change 同时完整删除 bootstrap subsystem，不保留简化版或兼容入口；官方 GitHub Template 仍是默认初始化路径，普通 generator ownership/handwritten slots 不受影响。

## Non-goals

- 不构建或治理 Domain Strategic Workspace。
- 不强制领域设计文件、迭代 capsule、projection ledger 或额外审批门。
- 不把 agent 的通用 DDD 推理复制成大量 cap4k rules。
- 不让 analyzer 自动决定业务语义或改写 generator inputs。
- 不把 MCP 作为文档数据库、第二套 capability truth 或强制依赖。
- 不在当前 change 建设通用 skill distribution、自动安装、自动升级或零学习 onboarding 系统。
- 不在当前 change 修改官方 `cap4k-template` 仓库或向其复制尚未稳定的 skill snapshot。
- 不以一次性 bootstrap、外部 bootstrap CLI 或兼容 shim 替代已删除的 capability。
- 不在本 change 中宣称 #27 的真实项目闭环已经完成。
