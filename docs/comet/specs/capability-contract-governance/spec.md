# Capability Contract Governance

## 目标

cap4k 必须以一套可验证的能力契约图维护产品能力及其知识投影。Runtime、Generator、Analyzer 的语义、authoring projection 与 observation projection，以及 AgentFacts、Public Docs、Skill 的机器/人类/Agent 投影，必须在同一次契约变化中完成直接和传递依赖闭包检查。

本契约覆盖：

- `README.md` 与 `docs/public/**`；
- `skills/cap4k-authoring/**` 及其验证脚本；
- `build/cap4k/agent/**` AgentFacts；
- 支撑上述表面的代码事实投影、仓库校验、CI 与贡献规则。

## 能力契约图

Canonical capability contract 是语义根。Runtime、Generator、Analyzer 是产品能力节点；AgentFacts、Public Docs、Skill 是知识和操作投影节点。节点之间的边表示某一表面消费、表达、观察或解释另一表面的契约。

任何节点变化都必须计算影响传播闭包。对闭包中的每个节点和边，变更记录必须给出三种结论之一：

- 已修改，并给出实现与验证证据；
- 已验证无需修改，并给出保持兼容的证据；
- 明确不适用，并说明契约关系为何不存在。

传播闭包不得由修改目录、固定的单向三段规则或提交者记忆替代。Runtime 变化通常需要检查 Generator、Analyzer、AgentFacts、Public Docs 和 Skill；实际是否修改由契约边和证据决定。

## 能力面与知识面

### 产品能力面

cap4k 的产品能力按职责划分为：

- Runtime：执行请求、事件、持久化与 provider composition 等运行时语义；
- Generator：读取受支持输入，经 canonical model 规划和渲染受管产物；
- Analyzer：从代码、IR 和生成证据中提取结构观察与可视化产物。

### 知识与交互面

以下表面是产品能力的投影，不是与 Runtime、Generator、Analyzer 并列的业务实现：

- AgentFacts：机器可读的已安装版本和当前项目事实；
- Public Docs：人类用户理解和使用当前 cap4k 的说明；
- Skill：业务项目 Agent 选择操作、读取事实和遵守稳定边界的薄路由。

不同表面可以根据受众采用不同措辞和粒度，但不得维护相互冲突的事实闭集。

## 权威链

### Canonical Spec

Canonical Spec 必须定义长期稳定的能力职责、用户可见语义、输入输出所有权、公开面受众以及验证义务。

Canonical Spec 不得复制会随代码频繁变化的 task 名、provider/capability 完整列表、Agent section 完整列表、JSON 字段枚举或 Analyzer 输出完整列表。此类枚举必须来自生产代码的结构化事实源。

### 代码事实

当前版本的可枚举事实必须由生产代码中的结构化 contract、descriptor、registry、task registration、planner 或 artifact catalog 持有，并由可执行测试保护。

如果说明材料与生产代码冲突，当前运行行为先以生产代码和测试证据为准；代码若违反已确认的 Canonical Spec，必须修复代码；说明材料若违反代码与 Canonical Spec，必须修复说明材料。不得通过新增另一份手写完整清单来解决冲突。

### AgentFacts

AgentFacts 必须从生产 contract、provider descriptor、extension contribution 和当前项目状态确定性生成。

AgentFacts 必须：

- 先提供稳定 manifest，再提供按 section 分区的事实；
- 区分 installed/supported capability 与当前 project effective state；
- 区分计划证据、可用产物和 freshness；
- 对敏感配置脱敏；
- 保持稳定排序、schema identity 和 snapshot identity；
- 只描述当前版本和当前项目，不描述框架演进历史。

AgentFacts 不得从 Public Docs、Skill、手写 committed snapshot 或历史 spec/plan 反向读取能力事实。

### Public Docs

Public Docs 必须面向当前框架用户，以自包含方式解释 cap4k 当前是什么、当前使用路径、Runtime/Generator/Analyzer 协作方式，以及输入、生成、手写逻辑、测试和分析证据的边界。

Public Docs 必须采用正向当前态表达。普通 reference、concept、authoring 和 example 页面不得用迭代日志、Issue 背景、旧能力对照或历史 spec 解释当前行为。

必须提供升级信息时，应放入 Changelog、Release notes 或明确的 migration surface；它们不得成为理解 current reference 的前置条件。

解释性正文和示例继续人工维护。精确闭集、高风险枚举和表格必须由代码事实生成，或由自动化校验与代码事实做精确对照。

### Skill

`skills/cap4k-authoring/SKILL.md` 必须保持薄入口，`routing.yaml` 必须保持唯一 route table。

Skill 必须先生成或刷新 Agent snapshot，先读 manifest，再按 route 渐进读取所需 section；根据 supported catalog 与 effective state 选择当前项目可执行操作；只内置无法从 AgentFacts 直接获得、但执行 authoring 必须遵守的稳定规则。

Skill 不得复制完整 provider/capability catalog、公开 task 全表、Agent section schema 或 Analyzer output 全表。Skill 提到的闭集或 identifier 必须可由 capability contract validator 与代码事实对照。

## Current-only 内容规则

`README.md`、`docs/public/**`、`skills/cap4k-authoring/**` 和 AgentFacts 普通输出必须只承载当前态。

以下内容不属于这些 current surfaces：

- 某能力过去如何实现或为何被重写；
- 已关闭 Issue 的调查过程；
- 历史分支、旧 task、旧 DSL 和旧模块的兼容说明；
- 未来计划或尚未确认的能力；
- 维护 Agent 的实现口号和阶段记录。

当前边界允许直接说明“不支持什么”或“应使用什么”，但不得要求读者先理解已退休实现。例如应写“项目初始化使用官方 GitHub Template 或显式手工结构”，而不是把 Bootstrap 的删除过程写进普通用户路径。

## Capability facts

仓库必须提供一份由代码权威源产生的确定性 capability facts 投影，供跨能力校验、AgentFacts、文档、Skill 与 CI 使用。

该投影至少覆盖：

- Runtime contract identity、Generator carrier/artifact identity 与 Analyzer observation/artifact identity；
- 跨能力依赖边及其适用条件；
- Agent snapshot 文件、section identity 与状态 vocabulary；
- 公开 Gradle task identity 和只读/变更边界；
- 被公开引用的 source/generator/provider/capability identity；
- Analyzer 计划输出和公开 artifact identity；
- 其他在 Public Docs 或 Skill 中被声明为完整闭集的高风险事实。

投影实现不得把 Gradle adapter、PowerShell regex、Markdown 表格或 committed 手写 JSON 变成第二权威源。实现可以选择生产 API、focused export task 或测试支持入口，但必须直接消费生产 contract/descriptor/registry，并以测试证明确定性。

## 校验入口

仓库必须有一个统一的 capability contract validation 入口，负责协调：

- Skill 结构和 route 唯一性；
- 薄入口预算；
- Public Docs 与 Skill 的本地链接；
- 已退休 active term 和已知历史污染；
- Runtime / Generator / Analyzer 的共享契约和组合证据；
- Public Docs / Skill 引用与 capability facts 的精确对齐；
- PR 声明的影响传播闭包与变更事实的对齐；
- current-only 表面的结构和内容约束；
- 可读、稳定、可修复的失败诊断。

`skills/scripts/validate-cap4k-skills.ps1` 保留为 Skill 专属检查调度器。它必须检查 Skill 自身的结构、预算、链接、current-only 规则以及 Skill 所引用代码事实的契约对齐，但它不是 Public Docs 与 AgentFacts 全局一致性的唯一入口。

关键词扫描只能捕获明确的退休 identifier 和高置信历史污染，不得作为语义一致性的唯一证据。

## 漂移失败语义

当受管事实不一致时，校验必须非零退出，并至少报告事实类别、代码权威来源或投影 section、声明文件或 route、expected、actual 和推荐修复方向。

校验必须区分“页面声称这是完整闭集”和“页面只举例说明”。普通示例不因为未列出所有 capability 而失败；完整表、route vocabulary、schema 文件表和公开 task reference 必须精确对齐。


## 总体设计与多切片治理

当一项总体设计需要多个可独立实现、验证和合并的切片时，治理使用以下职责分工：

- Canonical Spec 保存系统最终必须成立的总体语义、不变量、能力契约边和总体验收；
- GitHub Parent Issue 保存实时切片、依赖、进度和最终关闭状态，并优先使用原生 sub-issue 关系；
- Child Issue 保存单个可验收实现切片的范围、非目标、依赖和 acceptance IDs；
- 每个 Child 可以使用独立 Comet change 保存正式目标、验收和验证证据；
- PR 是交付单元，只能自动关闭其直接负责的 Child 或 Standalone Issue，不得自动关闭总体 Parent；
- Parent 只有在全部必需 Child 完成、相关 merge commit 均属于同一被接受的 `origin/master` lineage、并在该基线上完成总体组合验收后才允许关闭。

Comet 不承担多个 PR 的实时依赖图。Parent Issue 不复制高频代码事实；它只记录总体意图、切片关系、acceptance coverage 和证据状态。

`.agents/skills/issue-governance` 必须支持 Parent、Child 和 Standalone 三种角色，允许同仓库总体设计拆分为独立 Child，规定原生 sub-issue 优先、fallback backlink、closing keyword、master containment 和 Parent closure audit。

cap4k Issue forms 必须提供维护者可用的总体设计 Parent 与实现切片 Child 入口，同时保留普通外部 bug/docs/release 的 Standalone 路径。

## PR 审计合同

PR 模板必须要求作者提供：

- Parent Issue、直接负责的 Child/Standalone Issue、Canonical Spec 和 acceptance IDs；
- Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill 的受影响面；
- 修改或消费的共享契约；
- 对传播闭包中各面的“已修改 / 已验证无需修改 / 不适用及理由”；
- 跨能力组合验证；
- 本切片责任、兄弟切片责任、已知风险和 reviewer audit focus。

PR body validator 必须校验承重内容，不只校验标题存在。Parent 与 `Closes` 不得指向同一 Issue；存在 Parent 时必须有直接 Child；无理由的空内容或 `N/A` 不得通过。无需总体设计的 Standalone 变更允许明确声明 Parent 为 `N/A`。

## Agent Review

Agent Review 是确定性门禁之外的动态增强，用于发现静态闭集和测试未覆盖的架构遗漏、传播边遗漏和总体设计偏离。

Agent Review 不得替代 capability contract validator、组合测试、PR body validation 或人类审批。实现只有在以下条件同时满足时才应接入自动 Agent Review：

- reviewer 能读取仓库治理指令、Canonical Spec、Parent/Child Issue 和 PR 审计字段；
- workflow 对 fork PR、secret、提示注入、写权限和第三方代码执行有明确安全边界；
- 费用、配额和失败模式可接受；
- Agent 失败不会错误地覆盖确定性检查结论。

若当前 GitHub/凭据环境无法可靠满足上述条件，本 change 可以只交付完整审计上下文和可接入设计，并明确记录未启用原因；这不阻塞 capability contract governance 的完成。

## GitHub CI

GitHub required `check` 必须在所有 pull request 上执行 capability contract validation。

- 非 docs-only PR：capability contract validation 与现有 Gradle checks 一起运行；
- docs-only PR：可以继续跳过完整 Gradle `check`，但必须运行 capability contract validation，并获得足够的代码事实投影来做语义比较；
- validation 失败必须阻止合并；
- CI 不得依赖外部数据库、网络服务、发布凭据或业务项目仓库。

## AGENTS 贡献规则

根 `AGENTS.md` 必须只写简短治理规则和命令路由：

- Public Docs、Skill、AgentFacts 只描述当前版本；
- 历史进入 Git/Issue/Release/Changelog/migration/Comet archive 或内部历史 spec/plan；
- 修改生产 task、contract、descriptor、registry、Agent schema 或 Analyzer artifact 时必须更新受影响的公开解释并运行统一 validator；
- Skill 必须通过 AgentFacts 获取动态 capability/project facts；
- 不在 AGENTS 中复制事实枚举。

## 验证要求

实现必须提供自动化证据，至少证明：

- capability contract facts 从生产权威源确定性产生；
- Agent section 增删或改名会使过时 Public Docs/Skill fixture 失败；
- 公开 task、provider/capability 或 Analyzer artifact 漂移会被捕获；
- Skill 基础结构、薄入口、链接和退休术语检查继续有效；
- docs-only 与非 docs-only CI 路径都执行统一 validator；
- 当前仓库全部 Public Docs、Skill 和 AgentFacts 合同通过；
- 现有 Runtime facts guard 与 Agent snapshot focused tests 不被削弱。

## 非目标

- 本契约不改变 Runtime、Generator、Analyzer 的业务功能或执行语义。
- 本契约不要求所有 Public Docs 自动生成。
- 本契约不把 AgentFacts 变成 committed source truth。
- 本契约不恢复退休能力或保留历史兼容入口。
- 本契约不拆成多个阶段或后续治理 change。
- 本契约不强制自动 Agent Review 成为 required approval；可靠接入不可行时允许仅保留静态门禁和审计上下文。
- 本契约不依赖或修改 `framework-capability-audit`。
