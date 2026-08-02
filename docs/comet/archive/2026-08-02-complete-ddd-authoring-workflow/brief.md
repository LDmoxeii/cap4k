# Outcome

重新聚焦 #100：让一个略懂 DDD 的人借助 agent 使用 cap4k 时，agent 能准确理解并操作 cap4k 的运行时、生成器、分析器和项目约定，显著降低上手成本。Cap4k 提供的是战术框架与工程能力；agent 可以协助人讨论领域问题，但 cap4k 不规定组织如何完成战略 DDD，也不治理人的领域决策过程。

# Scope

- 将现有 57 个文件、约 155 KB 的 skill system 重构为轻量的 **cap4k agent enablement layer**，重点保存 agent 无法仅靠通用 DDD 知识可靠推导的 cap4k-specific facts、操作顺序、ownership 边界和 failure interpretation。
- 让 agent 能发现现有项目形态、识别可用 generator/runtime/analyzer 能力、选择正确 generator input、运行并理解 `cap4kPlan`、`cap4kGenerate`、`cap4kGenerateSources`、`cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate`。
- 让 agent 能读取结构化 `build/cap4k/plan.json`、`build/cap4k/analysis-plan.json` 和 pipeline diagnostics，理解 generated-source、checked-in scaffold 与 handwritten logic ownership，并依据当前代码而不是陈旧 prose 做决策。
- 保留足够的 DDD-to-cap4k affordance guidance，帮助 agent 把人已经讨论出的业务概念映射到 Aggregate、Value Object、Command、Query、Domain Event、Integration Event、Capability、Domain Service、Repository 等当前载体；该 guidance 是建议和解释，不是领域治理状态机。
- 新增最小 read-only、Gradle-first、manifest-first 的 machine-readable Cap4k Agent API；MCP 只保留为未来可选适配器，不在当前 change 实现。
- 完整删除 bootstrap capability，包括 `cap4kBootstrapPlan`、`cap4kBootstrap`、`cap4k.bootstrap`、bootstrap modules/API/core/renderer、root protection、slot/managed-block merge、fixtures、tests、文档和 skill 引用。
- 允许无兼容层地删除、合并、改名和重排当前 skill routes、references、workflows 和 validation。

# Non-goals

- 不规定或接管 Domain Landscape、Subdomain Portfolio、Context Map、组织边界、团队协作、投资优先级或战略 DDD 调研过程。
- 不要求业务项目建立 `strategic-seed.yaml`、`design/ddd/`、Iteration Capsule、Projection Ledger、Strategic Workspace 或 cap4k-specific approval state machine。
- 不把正常的人机讨论、代码 review 和工程验收重新包装成 cap4k 强制治理流程。
- 不假设战略设计可以通过更严格的 artifact schema 自动变好；领域质量主要来自人的知识、沟通和判断。
- 不让 MCP 复制一套静态文档或成为新的权威来源；没有稳定 machine contract 时，MCP 不能掩盖底层漂移。
- 不保留简化的一次性 bootstrap、兼容 task/DSL、deprecated alias 或迁移桥接；官方 GitHub Template 继续承担默认项目初始化。
- 不以“用户完全不需要学习 DDD/cap4k”为目标；本 change 降低框架事实、任务选择和 ownership 判断成本，但保留用户对基础 DDD、四层结构、generator input、plan review 与业务决策的最低理解责任。
- 不在本 change 中关闭 #27 的真实 runnable reference-project proof gate，也不虚构 analyzer 尚不能证明的业务语义。

# Acceptance examples

- 用户只描述业务目标时，agent 可以用通用 DDD 能力与用户讨论边界、事实、不变量和语言；它不会先要求创建 cap4k strategic workspace 或固定设计文件。
- 当讨论进入实现时，agent 能检查当前 cap4k project、generator inputs、modules、source ownership 和已有代码，再解释哪些结构可以生成、哪些必须手写、哪些由 runtime/provider 执行、哪些只能通过 analyzer/test 观察。
- 面对 Aggregate、Command、Domain Event、Integration Event、Capability 或 Value Object，agent 能给出符合当前代码的 input surface、plan expectation、generated artifact、handwritten slot、runtime boundary 和 verification path。
- Agent 在生成前运行并审查 plan；不会为了通过编译手写一个 generator 已支持的平行 skeleton，也不会把 build-owned generated source 当作长期业务逻辑位置。
- Agent 能把 analyzer output 当作实现观察证据，指出它能证明的结构/因果关系和不能证明的业务意图、事务提交、交付重试或战略正确性。
- 当 cap4k 不支持 Saga、Event Sourcing、完整 CQRS、semantic module enforcement 或其他能力时，agent 明确说明 provider/extension/unsupported boundary，而不是把 DDD 术语硬映射成不存在的 framework type。
- 一个 bounded dry run 能证明：略懂 DDD 的用户只需表达业务目标，agent 即可发现项目、选择 cap4k 载体、准备输入、审查 plan、生成、完成手写逻辑并验证结果，而不引入额外领域治理制度。
- Consumer project 不再暴露 `cap4kBootstrapPlan`、`cap4kBootstrap` 或 `cap4k.bootstrap`；repository 中不再存在 bootstrap module、root guard、managed markers、slot/merge 实现或面向 agent 的 bootstrap 能力声明。
- Bootstrap 删除不会影响普通 generator 的 checked-in skeleton ownership、handwritten logic slots、managed-field handler slots 或 `ConflictPolicy.SKIP` 行为。
- Agent API snapshot 不泄露 password、token、private key、原始 connection string 或内嵌 credential；它只披露完成判断所需的类型、存在性和脱敏 identity。

# Constraints and invariants

- `routing.yaml` 若继续存在，只能路由 cap4k-specific work；不得成为完整 DDD 方法论或组织流程的 source of truth。
- Skill 内容必须 self-contained、progressively loaded、code-grounded，并把高漂移事实尽量交给机器可检查的 capability/input/plan contract。
- Runtime historical-fact boundary 保持不变；PR #152 后 generator 使用显式不可变 Domain Event fact fields。
- Generator plan、diagnostics 和 analyzer artifacts 是 cap4k 当前最有价值的机器接口；任何新 agent interface 必须复用这些权威结果，而不是重新实现一套推断逻辑。
- Agent API 生成的 manifest、分区和 diagnostics 属于可持久化报告，必须默认脱敏，不得序列化 password、token、private key、原始 connection string 或其他 credential。
- 对既有 plan evidence，只有 project configuration/input identity 足以证明与当前状态一致时才能标记为 fresh；无法证明时必须明确标为 stale、unknown 或 missing，并建议显式运行对应 plan task。
- Analyzer 是 observation/feedback，不是业务事实权威或 generator input。
- 当前没有外部用户；允许 breaking redesign，不保留 legacy routes、aliases、deprecated artifacts 或双轨执行。
- #100 负责 agent-facing authoring system；#27 与 analyzer issues 继续提供独立真实项目和观察能力证据。

# Decisions

- 之前拟议的 mandatory Strategic Seed、`design/ddd/` canonical workspace、Active Iteration Capsule、Projection Ledger、custom Decision/Acceptance gates 和 Strategic Change Proposal workflow 不再作为当前目标；这些设计越过了 cap4k 的框架责任边界。
- 战略设计属于组织与人的工作。Agent 可以使用自身 DDD 能力提出问题、解释权衡和协助建模，但 cap4k 只需要让 agent 对 cap4k 的工程能力“了如指掌”。
- Skill 若保留，其价值不是控制用户，而是提供少量、稳定、cap4k-specific 的 semantic guidance 与 tool-routing guidance。
- MCP 不是根本解法。根本解法是稳定的 machine-readable input/capability/plan/diagnostic contract；MCP 可能是其上的可选 agent adapter。
- 当前 Gradle tasks 和结构化 plan/diagnostics 已构成 Agent API 的起点，但尚未形成统一的 project inspection、capability discovery 和 tool schema surface。
- 采用 breaking reset，不为当前重型 skill structure 保留兼容层。
- 用户已确认 Outcome 重置：本 change 正式建设 cap4k agent enablement layer，不再建设完整 DDD workflow/governance system。
- 用户已确认最小 read-only Cap4k Agent API 纳入当前 change。它承担 project inspection、capability/input discovery、validation 和 plan/diagnostic normalization，使 thin skill 不再静态复制大量 framework facts；现有 generation/mutation tasks 暂不被新 API 替代。
- 用户已确认 Agent API 采用 Gradle-first：consumer project 通过与 cap4k plugin 版本一致的 read-only task 获取 versioned JSON；inspection/capability 核心 model 保持 adapter-neutral，未来 CLI/MCP 复用同一实现。
- 用户已确认 Agent API 采用 manifest-first progressive loading：一次 Gradle 执行生成完整分区快照，agent 默认只读取小型 manifest，再按当前任务加载 project、capabilities、inputs、ownership、runtime、analysis 或 diagnostics 详情。
- 用户已确认 Agent API 的无效项目失败语义：只要 Agent task 能启动，即使 cap4k configuration 或 generator input 无效，也要尽可能写出带明确状态、可用分区和结构化 diagnostics 的部分快照，然后以非零退出码报告无效；只有 Gradle 在 task 注册/启动前灾难性失败时才允许没有快照。
- 用户已确认非必需分区的 `partial` 成功语义：project 与必需配置有效时，非必需分区不适用或不可用不会让 Agent task 失败；task 成功退出，manifest 明确标记 `partial`，分区记录 `unavailable` 与原因。
- 用户已确认 capability discovery 同时提供 supported catalog 与 effective project view：前者描述当前 cap4k 版本及已安装 extensions 支持什么，后者描述当前项目实际配置、就绪、阻塞或不适用的能力；两者必须通过稳定 capability identity 关联但不得混淆。
- 用户已确认当前 change 不实现 MCP adapter。当前交付聚焦 thin skill、adapter-neutral Agent API core、Gradle-first/versioned-JSON surface 和有界 agent dry run；MCP 在底层 contract 经验证后作为独立后续 change。
- 用户已确认 Agent task 默认保持 external-I/O-safe：它可以读取 resolved configuration、本地 project-owned inputs 与现有 machine evidence，但不得主动连接数据库等 live source；需要 live source 的 planning 由 agent 显式运行现有 plan task。
- 用户已确认当前只保留 cap4k repository 内的 repo-local thin skill source，不建设正式 distribution artifact、installer、自动同步或版本兼容体系；先由用户亲自体验并迭代 skill，再评估分发投入。
- 用户已确认当前 change 不修改独立官方 `cap4k-template`。Template 与 skill 的最小协作在 repo-local skill 经实际体验稳定后，通过独立 Template change 实施；当前只保留该后续方向。
- 用户已确认完整删除 bootstrap capability：删除 `cap4kBootstrapPlan`、`cap4kBootstrap`、`cap4k.bootstrap`、bootstrap API/core/renderer/module、root protection、slot/managed-block merge、fixtures/tests/docs/skill references；不提供一次性替代版、兼容别名、弃用过渡或迁移路径。官方 GitHub Template 是默认初始化入口，普通 generator ownership 与 handwritten slots 保持不变。
- 用户已确认本轮 shared understanding，并授权按当前 brief/spec 进入 Build。

# Open questions

- 无。

# Verification expectations

- 对照当前 generator/runtime/analyzer 代码和 PR #152 后事实生成或校验 capability map，不依赖历史 issue prose 作为 runtime truth。
- 验证 agent 能从一个真实或有界 fixture 中发现 project shape、选择 input surface、运行 plan/generate/analyze、解释 diagnostics、保护 output ownership 并完成 focused verification。
- 测量重构后的 always-read 文件数、总 skill surface、重复规则和 route overlap，证明上下文负担显著下降。
- 明确区分 thin skill guidance、machine-readable framework facts、可执行 tools 和普通 DDD reasoning 的责任。
- 验证没有 MCP 时 agent 仍可通过 Gradle/versioned JSON machine interface 完成同一核心操作。
- 验证 Agent API 对 invalid/partial/freshness 的状态与退出码语义，并验证所有 snapshot 分区和 diagnostics 的 credential redaction。
- 验证 Gradle task 列表、settings/dependencies、source/test fixtures、public docs、skill routes 与 Agent API supported catalog 中均不存在 bootstrap capability 或陈旧引用。
- 验证普通 generator ownership、checked-in scaffold、handwritten slots、managed-field handler slots 和 `ConflictPolicy.SKIP` 的现有测试仍通过。
