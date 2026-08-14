# Outcome

Pipeline Flow 只从有明确生产触发证据的真实代码节点生成默认业务因果入口，并以 Actor、Event、Time 三类触发来源解释入口语义。Analyzer 增加 Temporal Trigger 生产证据，使实际 scheduled method 能以自身节点身份启动 Command/Event Flow；Flow 不再把“任意方法调用 Command”自动等同于真实入口。

# Scope

- 更新 Analyzer Graph 与 Pipeline Causal Flow 的完整目标合同，明确 Actor / Event / Time 三类入口来源和统一 root 判定。
- 保留实际代码节点 identity，不新增抽象 `Entry` 或抽象 `TemporalTrigger` 节点。
- Analyzer 首期识别 Spring `org.springframework.scheduling.annotation.Scheduled` 方法，生成明确 Temporal Trigger method 节点及其到 Command 的 relationship evidence。
- Flow 消费显式 `*ToCommand` trigger evidence，并继续要求投影后零入度且存在下游因果边。
- 保持 HTTP Controller 与 Inbound Integration Event 的现有入口行为，补齐 Temporal Trigger focused、compiler-backed 与 Gradle functional 自动化证据。
- 审计 Analyzer、Flow、CapabilityContractFacts、AgentFacts、Public Docs 与 Skill 的传播闭包，按实际影响更新。
- 从 NodeType、RelationshipType、Analyzer emission、Flow projection 与测试中彻底删除 `commandsendermethod` / `CommandSenderMethodToCommand`，不保留 raw fallback、alias 或迁移桥。

# Non-goals

- 不实现 scheduler、Job runtime、分布式调度、cron 管理、misfire、retry、控制台或通用 task framework。
- 不生成 Job 或 Scheduled Reaction 骨架。
- 不在本 Change 新增 RPC、GraphQL、CLI、workflow、CDC 或其他 adapter detector；Flow 继续允许未来 Analyzer 以真实节点和明确 `*ToCommand` relationship 扩展。
- 不新增 `entryFamily` wire 字段，不改变现有 Flow JSON、Mermaid、index、output identity 或 task lane。
- 不恢复 process projection、自动 stitching 或用户自定义 topology。
- 不把 Query-only endpoint、Domain Event Handler、Command Handler、Entity Method、outbound Integration Event、scheduled Command worker 或纯技术维护任务提升为默认 Flow root。

# Acceptance examples

- A1：一个 Spring `@Scheduled` 方法直接发送 Command 时，Analyzer Graph 产生真实 method 节点和明确 Temporal Trigger 到 Command 的 relationship，Flow 以该 method 为唯一 root 并生成 JSON、Mermaid 与 index entry。
- A2：`@Scheduled` 方法只执行 Query、Capability 或纯技术逻辑而不发送 Command 时，不生成默认 causal Flow。
- A3：HTTP Controller 到 Command 与无上游 Inbound Integration Event 到 Command 的现有 Flow 保持成立，并分别归入 Actor 与 Event 触发语义。
- A4：普通内部方法即使发送 Command，也不能仅凭 generic sender evidence 自动升级为默认 Flow root。
- A5：由既有业务链登记的 delayed/scheduled Command 不制造抽象 Temporal Trigger root；时间唤醒仍是执行策略或已有因果链 continuation。
- A6：有上游因果边的中间 Event、Command 或 trigger method 不制造重复 Flow；两个独立显式 root 仍生成两张 Flow。
- A7：生产 Node/Relationship 类型、Analyzer compiler observation、Flow projection、focused tests 与 Gradle functional fixture 对 Temporal Trigger 的 identity、edge、root 和输出计数保持确定性。
- A8：Capability facts、AgentFacts、Public Docs、Skill、Runtime/PR/workflow validators 与完整 Gradle `check` 的声明和实际代码一致，不声明未实现的 adapter detector 或 scheduler 能力。

# Constraints and invariants

- 当前没有外部用户，不保留 deprecated alias、兼容 relationship、双写 wire 或迁移桥；需要删除的旧面直接删除。
- 入口资格来自生产代码或明确 relationship evidence，入度为零和命名相似都不是充分条件。
- 入口分类是概念解释，不是永久封闭的 NodeType allowlist；未来入口必须由 Analyzer 增加真实 producer evidence。
- Temporal Trigger 概念对应时间触发来源，Graph 节点对应实际 scheduled method。
- Flow 仍只消费 Analyzer Graph partition，不从 Public Docs、Skill、Drawing Board 或 Aggregate Structure 补造入口。
- Runtime 调度能力与本 Change 分离；Runtime 合同默认为 `verified-no-change` 或 `not-applicable`。

# Decisions

- 默认业务因果 Flow 的基础触发来源固定解释为 Actor / Event / Time。
- HTTP、RPC、CLI 等属于 Actor/Intent Trigger；Inbound Integration Event、webhook/message/stream 等属于 Event/Fact Trigger；scheduled method/job/cron 等属于 Temporal Trigger。具体 adapter 只有在生产 Analyzer 存在明确 evidence 时才成为当前支持入口。
- Domain Event Handler 是既有链的 continuation，不是新入口；Inbound Integration Event 只有在投影后没有上游因果边时才是 Event root。
- 独立周期 Job 是 Temporal root；由已有流程登记的 scheduled/delayed Command 是 continuation，不创建新的 Temporal root。
- 首期 Temporal Trigger detector 使用现有 Spring analysis surface，识别 `@Scheduled` 方法；其他调度 provider 后续按真实项目证据独立扩展。
- 不新增抽象 Entry node、TemporalTrigger node 或 `entryFamily` wire；实际 method node type 与 relationship type承载生产证据。
- commandsendermethod NodeType 与 CommandSenderMethodToCommand RelationshipType、Analyzer emission、Flow 常量和相关测试全部删除；未知普通方法调用 Command 不生成替代入口证据。

# Open questions

- 无。

# Verification expectations

- Analyzer core/compiler focused tests覆盖 `@Scheduled` detection、Temporal Trigger node/relationship、普通 method 隔离，以及 direct Command send 的确定性输出。
- Flow focused tests覆盖 Temporal root、generic sender 非 root、Actor/Event 回归、root-after-projection、共享后缀、循环和稳定去重。
- Gradle functional fixture实际编译带 `@Scheduled` 的 application source并运行 `cap4kAnalysisPlan`、`cap4kAnalysisGenerate`，核对 raw Graph、Flow JSON、Mermaid 与 index。
- 运行相关模块 focused tests、完整 Gradle `check`、`scripts/export-capability-contract-facts.ps1`、`scripts/validate-capability-contract.ps1`、Skill/Runtime/PR-template/workflow validators 与 `git diff --check`。
- Verifier 逐项核对 A1-A8，并确认不存在 generic sender 入口兼容桥、抽象 Temporal node、scheduler runtime 或未声明 wire 变化。
