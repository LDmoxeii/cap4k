# Outcome

把 cap4k 当前认可的应用边界与最佳实践，统一表达在下游 authoring Skill 与 cap4k Public Docs 中，使写作者能够：

- 把一个有明确业务意义的 Command 作为完整本地用例，必要时协调多个相关 Aggregate；
- 保持聚合不变量和状态行为归属于 Aggregate/Domain Service，不把规则搬到 Handler；
- 把“单聚合 Command”作为默认推荐起点，而不是 Runtime 可证明的绝对硬约束；
- 保持 Command 与 Query 平级，Command 内不直接调用 Query；
- 正确理解外层 Command/UoW 的本地事务边界，以及 Capability 外部副作用不受本地回滚保护。

本 change 不新增 Runtime/UoW 能力，不替换或移除现有跨聚合 Command 方式。

# Scope

## Source coverage

- S1：GitHub Issue #221（2026-09-05 已完整读取正文和空评论），作为触发背景；其公共局部 UoW 方案在上一轮 Shape 中已被用户否定为本轮不推进，不作为本 change 的目标来源。
- S2：上一轮 `query-uow-composition-assessment` Shape 决定（当前工作区 `/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/query-uow-composition-assessment/`），完整可读；保留结论：不为扩展灵活性新增 Runtime/UoW，保留方案一与 Command→Query 禁止，问题转为 Skill/Docs 默认约定。
- S3：用户在 2026-09-05 明确的范围决定，complete：下游示例暂不处理；Skill 与 cap4k Public Docs 必须同时考虑相邻影响。

## 变更面

- Skill：优先修改现有 `skills/cap4k-authoring/references/tactical-carriers.md`、`references/runtime-analysis-boundaries.md`；根据路由调查决定是否仅扩展已有 `routing.yaml` trigger_examples；保持 `SKILL.md` 薄壳与当前 route 数，除非验证证明必须变更。
- Public Docs：核对并按需修改 `docs/public/concepts/execution-and-ownership/command.md`、`query.md`、`command-query-separation.md`、`unit-of-work.md`、`docs/public/architecture/application-layer.md`、`docs/public/concepts/modeling-building-blocks/aggregate.md`、`docs/public/reference/common-mistakes.md`、相关 Capability/authoring 页面。只写当前支持的正向合同，不引用 Issue 历史或未采用的局部 UoW。
- README：默认 `verified-no-change`，除非现有文字把单聚合 happy path 写成硬限制或与 Public Docs 形成冲突。
- 下游示例：本 change 不修改 `cap4k-reference-payment` 或其他 consumer 项目。

## 目标语义（候选，待最终确认）

1. Command 默认表达一个完整、可命名的业务用例；一个用例可以协调多个相关 Aggregate。多聚合本身不是错误，多个不相关生命周期/业务意图被塞进同一 Command 才是设计警告。
2. Aggregate/Domain Service 负责不变量、状态转换和生命周期；Command Handler 负责应用层顺序、输入转换、调用聚合行为和失败传播，不重写领域规则。
3. 只有具有独立业务意义、生命周期、复用或重试边界时才拆分 Command；不为形式上的“单聚合”制造无独立语义的包装步骤。
4. Command 不直接调用 Query。需要业务判断时，通过 Repository 加载 Aggregate 或调用领域能力；应用入口先 Query 再 Command 时，不暗示共享未提交状态或同一写 UoW。
5. 外层 Command 仍是当前本地写 UoW 边界；应用代码不手工创建、定位、flush 或提交 UoW。
6. Domain Event 表达已经发生的领域事实，不仅为了获得共同事务而使用事件隐藏流程；Capability 的外部副作用不因处于 Command 而获得本地回滚保证。
7. Analyzer 只能表达观察到的调用/关系证据，不能仅凭引用多个 Aggregate 自动判定 Command 设计错误。

# Non-goals

- 不新增或修改公共局部 UoW、事务传播、Query 参与写 UoW、rollback-only 或 InvocationPolicy Runtime 能力。
- 不移除或降级跨聚合业务 Command；不强制所有 Command 单聚合或单实例。
- 不创建新的 Workflow/Saga 类型、Skill route、reference 文件或下游示例。
- 不把 Issue #221 的 Q/U/X 验收清单直接变成本 change 验收。
- 不修改 Generator、Analyzer detector/evidence、AgentFacts、生产 descriptor、registry、模板或输出合同。
- 不将未实现的架构分析能力写成当前支持，不引用历史讨论作为现行规范。

# Acceptance examples

- A1：Skill 的载体与运行时边界说明中，Command 的完整用例、多相关聚合、聚合不变量归属、不要机械拆分均有明确且不互相矛盾的表述。
- A2：Skill 与 Public Docs 一致表达 Command→Query 禁止；同时不误伤 Event Handler 可调用 Query 的现行合同。
- A3：Public Docs 正向说明“单聚合是默认推荐起点而非绝对 Runtime 限制”，并保留“多个不相关生命周期塞入一个 Command”作为反模式。
- A4：Public Docs 与 Skill 明确区分本地 UoW 原子性、外部 Capability 副作用、跨时间可靠执行和补偿边界。
- A5：未修改下游示例；Runtime、Generator、Analyzer、AgentFacts 的生产合同无变化，并有能力闭包证据。
- A6：Skill 结构、route coverage、链接、current-only 内容、Public Docs 和能力合同校验通过；README 若未修改则有 verified-no-change 理由。

# Constraints and invariants

- 只在独立 `docs/cap4k-authoring-boundary-guidance` worktree 工作，不能在 master 修改。
- Skill 继续遵守 `SKILL.md` 薄壳、`routing.yaml` 单一真相和 progressive disclosure；优先复用现有 references/routes，新增结构必须有实际压力证据。
- Public Docs/Skill 只描述当前生产能力与边界；不把最佳实践偏好伪装成 Runtime 硬保证。
- 必须检查相邻影响：Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill 逐项记录 modified/verified-no-change/not-applicable。
- 下游示例已明确暂不处理，不因文档示例缺失扩大范围。

# Decisions

- 已确认：Issue #221 的 Runtime/UoW 扩展不推进；保留方案一的跨聚合业务 Command 方式。
- 已确认：Command 内禁止直接调用 Query 作为平级边界继续保留。
- 已确认：本 change 同时处理 Skill 与 cap4k Public Docs；下游示例不纳入。
- 已确认：默认推荐“单聚合、单一职责起点”，但不是绝对 Runtime 硬约束；完整业务 Command 可协调多个相关 Aggregate。
- 已确认：优先不新增 reference 文件和 route；先复用现有 Skill 结构，实际修改范围由审计和校验决定。
- 待确认：最终需要修改哪些 Public Docs 页面，以及 `routing.yaml` trigger_examples 是否需要扩展。

# Open questions

无。用户已于 2026-09-05 确认 Skill + Public Docs 目标、非目标、验收示例和“下游示例不纳入”范围；后续按 Runtime continuation 进入 Build/Verify。

# Verification expectations

- Build 前读取当前 Skill references、Public Docs 相关页面、Skill 校验器与能力合同治理规则。
- Build 后运行 `skills/scripts/validate-cap4k-skills.ps1`、`scripts/validate-capability-contract.ps1`、`scripts/test-capability-contract.ps1`，并按实际范围执行文档/链接/route/current-only 检查。
- 对每个能力面记录 modified/verified-no-change/not-applicable 及具体证据；不运行下游项目测试，除非后续范围明确加入。
