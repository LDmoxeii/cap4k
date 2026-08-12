# Outcome

建立一套覆盖 cap4k 产品能力和知识投影的长期防漂移治理，使 Runtime、Generator、Analyzer 的共享契约以及 AgentFacts、Public Docs、Skill 的下游投影在同一次变更传播中得到完整检查。一次完成能力契约图、传播闭包、代码事实投影、Parent/Child Issue 治理、PR 语义约束、静态 CI、可选 Agent Review、AGENTS 贡献规则和当前态内容清理。

# Scope

- 明确 Runtime、Generator、Analyzer 是产品能力面；AgentFacts、Public Docs、Skill 是这些能力针对机器、人类用户与业务项目 Agent 的知识投影和操作入口。`Canonical capability contract -> Runtime / Generator / Analyzer -> AgentFacts / Public Docs / Skill` 构成受治理的能力契约图。
- 定义公开面权威链：Canonical Spec 管长期语义与所有权，生产代码中的 contract / descriptor / registry / task registration / artifact catalog 管可枚举事实，AgentFacts 从代码和当前项目状态生成，Public Docs 解释当前行为，Skill 只保留路由、稳定操作约束和按需读取规则。
- 建立由代码权威源导出的确定性 capability facts，使校验器可以比较 Runtime contract、Generator carrier/artifact、Analyzer observation、Agent section、公开 Gradle task、provider/capability identity 与其他被公开面引用的高风险闭集。`docs/superpowers/capability-matrix.md` 等人工矩阵不得继续充当独立 current truth source。
- 整理 `README.md`、`docs/public/**` 和 `skills/cap4k-authoring/**`：只保留当前支持状态、当前用法和当前边界；删除或改写迭代日志式、旧能力对照式、Issue/历史 Spec 依赖式内容。
- 保持 Public Docs 面向人类、Skill 面向业务项目 Agent、AgentFacts 面向机器读取；三者允许针对受众采用不同表达，但不得维护相互冲突的事实闭集。
- 保留并扩展现有 Skill 基础检查，同时提供仓库级 capability contract 校验入口；它必须检查结构、薄路由预算、链接、退休术语以及来自代码权威源的语义对齐。
- 将仓库级 capability contract 校验接入 GitHub required `check`，包括 docs-only PR；docs-only 可以继续跳过完整 Gradle `check`，但不能跳过能力契约和公开面校验。
- 在 `AGENTS.md` 写入简短贡献约束和校验路由：任何契约变化都必须沿能力契约图检查所有直接和传递依赖面，并逐面记录“已修改”“已验证无需修改”或“明确不适用及理由”；不复制 task、provider、section 或 output 清单。
- 扩展 `.agents/skills/issue-governance`、cap4k Issue forms、PR 模板及正文校验，建立 Parent Issue 总体意图、Child Issue 独立实现切片、PR 交付证据和最终 `master` 组合验收的闭环。`Closes` 只能指向 Child/Standalone Issue，不得由实现 PR 自动关闭 Parent。
- 为事实投影、传播闭包、校验器、Issue/PR 治理和 CI 失败场景补充足够的自动化测试与可读诊断。
- 在不削弱确定性门禁、不引入不可接受的凭据、成本或 fork PR 安全风险的前提下，尝试接入 Agent Review 作为动态增强；无法可靠接入时记录原因，不阻塞本 change 完成。

# Non-goals

- 不改变 Runtime、Generator 或 Analyzer 的业务能力、执行语义、产物语义和公开 API。
- 不重新设计 Runtime、Generator、Analyzer、AgentFacts 的业务模型或 Skill 路由模型，除非为表达和验证能力契约依赖所必需。
- 不把 Git 历史、Issue、Comet change、历史 spec/plan 或迁移叙事复制进 Public Docs、Skill 或 AgentFacts。
- 不把所有 Public Docs 自动生成；概念、示例、用户工作流和解释性正文继续人工维护。
- 不让 Canonical Spec 复制高频变化的 task、provider、字段和文件枚举；这些事实必须由代码权威源产生。
- 不依靠宽泛关键词扫描宣称已经解决语义漂移；文本扫描只能作为辅助 guard。
- 不继续、修改或推进 `framework-capability-audit` change，也不接触该 worktree 中其他 Agent 的未提交内容。
- 不把本次交付拆成多个阶段、后续批次或待续治理项。
- 不要求 Agent Review 成为 required approval 或唯一审计依据；动态审计不能替代确定性校验和人类判断。
- 不强制所有外部 bug/docs/release Issue 建立 Parent；Parent/Child 只用于有总体设计和多个可独立合并切片的工作。

# Acceptance examples

- A1：当 Runtime、Generator 或 Analyzer 的能力契约发生变化时，PR 和校验必须沿已声明的直接及传递依赖边覆盖 Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill；每一面都具有“已修改”“已验证无需修改”或“明确不适用及理由”的可审计结论。
- A2：当生产代码新增、删除或重命名 Agent snapshot section，而 Public Docs 的文件表或 Skill route 仍引用旧集合时，capability contract 校验以明确的 expected/actual 差异失败。
- A3：当公开 Gradle task、provider/capability identity、Runtime contract 或 Analyzer artifact/output 的代码事实发生变化，而受管生成器、分析器或公开投影没有同步时，CI 在合并前失败；未被声明为完整闭集的普通示例不会被误判。
- A4：当仅修改 `README.md`、`docs/public/**` 或 `skills/**` 时，GitHub required `check` 仍运行结构、链接、当前态内容和代码契约对齐校验，不因 docs-only 分类而跳过。
- A5：`validate-cap4k-skills.ps1` 的职责和边界清晰可见：它能发现 Skill 结构、体积、链接、退休术语及 Skill 对代码契约的漂移，但不会被描述为 Public Docs 与 AgentFacts 全局一致性的唯一入口。
- A6：Public Docs 不要求读者理解 Bootstrap 等已退休能力、旧 task/DSL、Issue 背景或框架迭代过程；必须保留的升级信息位于 Changelog、Release 或明确的 migration surface，而不是 current reference 正文。
- A7：Skill 通过 `routing.yaml` 和 Agent snapshot 渐进读取当前项目能力，不复制完整 provider/capability catalog；路由引用的 Agent section 与生产 Agent contract 对齐。
- A8：AgentFacts 继续由生产 contract、provider descriptor 和当前项目状态确定性生成，不从 Public Docs、Skill 或 committed 手写 JSON 反向读取事实。
- A9：Canonical Spec、AGENTS、Public Docs 和 Skill 各自只承载其职责内的信息；自动化测试证明权威链和失败诊断，仓库没有新增第二套手写机器事实目录。
- A10：给定一个总体设计 Parent Issue，所有实现 Child Issue 使用原生 sub-issue 或显式 fallback 关系回链；Child PR 只关闭 Child/Standalone，Parent 只有在所有必需 Child 已完成且组合证据位于同一 `origin/master` lineage 后才允许关闭。
- A11：PR 模板和校验要求声明 Parent/Child、Acceptance IDs、受影响能力面、共享契约、传播闭包结果、组合证据、兄弟切片责任和审计重点；空标题或无理由的 `N/A` 不能通过。
- A12：本 change 的实现、测试和文档全部位于 `feature/public-surface-contract` 独立 worktree，`framework-capability-audit` 的状态和文件保持不变。

# Constraints and invariants

- 以创建本 change 时的 `origin/master` 提交 `f42dadf363071c76435d7a7ee0933e12f03ba80d` 为实现基线；若实现期间 `origin/master` 变化，先报告并按 Comet/仓库规则重新核对，不静默改基线。
- 生产代码和可执行测试是当前行为的最终证据；Canonical Spec 约束其应维持的稳定语义，但不替代代码中的可枚举事实源。能力变更的影响面按契约图传播闭包计算，而不是按修改目录或预设的单向固定列表猜测。
- Public Docs 面向框架用户，Skill 面向在业务项目中执行 cap4k authoring 的 Agent，AgentFacts 面向机器；不得为了复用文本而破坏受众边界。
- AgentFacts 是生成物和当前项目观察，不是 committed source truth，也不承载版本历史。
- `routing.yaml` 仍是 Skill 的唯一 route table；`SKILL.md` 保持薄入口。
- docs-only PR 可以跳过完整 Gradle test suite，但能力契约和公开面检查必须有足够的代码事实证据，不能退化为只检查 Markdown 拼写或链接。
- Parent/Child 生命周期状态由 GitHub Issue 和原生 sub-issue 关系管理；Canonical Spec 保存总体设计，Comet change 保存单个设计或实现单元的目标、验收和证据。Comet 不被伪装成多 PR 实时依赖图。
- 校验应产生稳定、可修复的错误信息，至少指出事实类别、权威来源、声明位置、expected 与 actual。
- 不手工修改 `comet-state.yaml`、verification 报告或 `.comet/runtime/native/**`。

# Decisions

- 2026-08-12：本治理从 `framework-capability-audit` 分离，使用独立 Comet change `public-surface-contract`、分支 `feature/public-surface-contract` 和 worktree `.worktrees/public-surface-contract`。
- 2026-08-12：不采用阶段式交付。本 change 一次完成正式契约、事实投影、Public Docs/Skill 当前态整理、校验实现、CI 接入、AGENTS 规则和验证。
- 2026-08-12：现有 `skills/scripts/validate-cap4k-skills.ps1` 只是四类静态检查的调度器；它当前不足以处理语义漂移。完成后它仍负责 Skill 专属检查，但必须纳入代码契约对齐；仓库级 Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill 闭环由独立 capability contract validation 入口统一编排。
- 2026-08-12：Canonical Spec 保存稳定的语义、受众、所有权和验证责任，不复制易变枚举。代码中的结构化 contract / descriptor / registry / registration / artifact catalog 是可枚举事实权威。
- 2026-08-12：Public Docs、Skill 和 AgentFacts 只描述当前版本；历史仅由 Git、GitHub Issue/Release、Changelog、migration 文档、Comet archive 和内部历史 spec/plan 承载。
- 2026-08-12：治理范围扩大为完整 capability contract governance。内部 change ID 因 Comet Native 不支持安全重命名而保留 `public-surface-contract`；正式 capability 及归档目标使用 `capability-contract-governance`。
- 2026-08-12：影响传播采用契约图闭包，而不是固定的 Runtime→Generator→Analyzer 三段规则。任何能力面变化都检查所有直接和传递依赖面，并逐面记录修改、无需修改或不适用结论。
- 2026-08-12：GitHub Parent Issue 管总体意图和实时切片状态，Child Issue 管独立实现范围，Comet 管每个 change 的正式目标和验收，最终 closure audit 只接受同一 `origin/master` lineage 上的组合证据。`.agents/skills/issue-governance`、Issue forms、PR 模板和校验均纳入本 change。
- 2026-08-12：Agent Review 是动态增强项。优先保证确定性 validator、组合测试和 PR 语义校验；若凭据、费用、fork PR 安全或平台能力使可靠接入代价过高，可以只交付可接入设计和审计上下文，不阻塞验收。

# Open questions

- 无。

# Verification expectations

- 运行新增的仓库级 capability contract validator，并验证成功路径与至少一组受控漂移失败 fixture。
- 运行 `skills/scripts/validate-cap4k-skills.ps1`，确认基础 lint 与 Skill 代码契约对齐检查全部通过。
- 运行现有 `scripts/validate-current-runtime-facts.ps1`，确认新治理没有削弱 Runtime facts guard。
- 运行针对能力事实投影、传播闭包、Runtime/Generator/Analyzer 组合边、Agent contract、Gradle task/public surface 和 Analyzer output catalog 的 focused tests。
- 运行 Issue hierarchy、PR body semantic validation 和 workflow guard 测试，覆盖 Parent/Child 合法关系、错误 closing target、缺失能力影响面和无理由 `N/A`。
- 运行 `./gradlew check`；若环境或既有无关故障阻止全量执行，必须保留 focused test 结果并明确报告阻塞证据。
- 静态审查 `.github/workflows/ci.yml` 的 docs-only 与非 docs-only 两条路径，证明 required `check` 均执行 capability contract validation。
- 扫描最终 `README.md`、`docs/public/**`、`skills/cap4k-authoring/**`，确认没有把历史迭代材料作为当前公开合同，也没有新增对 `framework-capability-audit` 的依赖。
