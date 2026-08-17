---
generated_from_state_version: 8
---

# Verification

## Current result

- Result: **Passed**
- Assurance: **skill-coordinated**
- Goal cycle: 1
- Iteration: 1
- Verifier attempt: 1
- Completed: 2026-08-17T12:40:43.152Z
- Summary: 通过。唯一生产变化是 FlowGraphSupport 的 Mermaid node label assembly/escaping；JSON、projection、root、slug、index、edge rendering 与 Pebble raw-pass-through 均保持不变。完整目标 Spec 仅补充 Mermaid 语法合同和职责边界；A1-A104 全部通过。

## Acceptance

| ID | Result | Source | Criterion | Reason |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | A1: 名称为 `payment.attempt.start [POST /api/payments/{paymentId}/attempts]` 的节点生成 `N1["payment.attempt.start [POST /api/payments/{paymentId}/attempts]"]`，不再生成嵌套未引用方括号形式。 | FlowGraphSupport 统一生成 N["..."]，focused test 精确断言 Endpoint 风格节点不再产生未引用嵌套方括号。 |
| A2 | passed | brief.md | A2: 节点名称包含双引号、反斜杠、换行或 HTML-sensitive 字符时，生成结果保持单个合法 quoted label，标签内容不会逃逸 Mermaid 节点声明。 | 节点标签对 &、<、>、反斜杠、双引号与 CR/LF 做确定性转义；focused test 覆盖完整特殊字符组合。 |
| A3 | passed | brief.md | A3: Endpoint HTTP functional fixture 实际执行 analysis plan/generate 后，`.mmd` 包含 quoted Endpoint label，JSON 的 entry identity、节点/边数量、relationship 和唯一 Flow 语义保持不变。 | Endpoint HTTP functional fixture 实际执行 analysis plan/generate，并核对 quoted label、entry identity、2 节点、1 边、relationship 与 flowCount=1。 |
| A4 | passed | brief.md | A4: `pipeline-causal-flow-contract` 明确 `.mmd` 的语法有效性、quoted node label、确定性转义和 Kotlin/Pebble 职责边界，Archive 后 canonical spec 与完整目标一致。 | 完整目标 Spec 相对 canonical baseline 仅增加 .mmd 语法有效性、quoted label、确定性转义及 Kotlin/Pebble 职责澄清。 |
| A5 | passed | brief.md | A5: Flow generator focused tests、Endpoint HTTP Gradle functional test 与完整 Gradle `check` 通过，且 `git diff --check` 通过。 | Runtime 记录的完整 Gradle check、focused coverage 和 git diff --check 均通过。 |
| A6 | passed | brief.md | A6: Runtime、Analyzer、AgentFacts、Public Docs 与 Skill 均完成 capability propagation 审查；Generator 与 canonical Flow artifact contract 为 modified，其他 surface 只有必要变化或 verified-no-change。 | 实际 diff 仅修改 Flow Generator、相关测试和 Native artifacts；Analyzer、Runtime、AgentFacts、Public Docs、Skill 无不必要变更，治理检查通过。 |
| A7 | passed | specs/pipeline-causal-flow-contract/spec.md | Pipeline `flow` 是面向业务因果链的默认静态投影。它从一个有生产代码或明确 relationship evidence 的实际入口出发，沿可验证的 Command/Event 因果关系生成入口中心的最小有向子图，帮助人类和 Agent 阅读代码结构。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A8 | passed | specs/pipeline-causal-flow-contract/spec.md | Flow 只陈述静态连接与可达性，不证明业务正确性、运行时执行顺序、事务边界、消息必达、重试结果或最终业务结果。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A9 | passed | specs/pipeline-causal-flow-contract/spec.md | 一张 Flow 对应一个具体真实入口及其沿默认因果关系可达的最小投影。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A10 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认产品不提供自动或独立 process projection，不把多个真实入口拼成一个业务过程。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A11 | passed | specs/pipeline-causal-flow-contract/spec.md | Pipeline generator `pipeline.generator.flow`、generator id `flow` 和 output id `flow` 是唯一公开 Flow 产品身份。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A12 | passed | specs/pipeline-causal-flow-contract/spec.md | `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 是 Flow planning 与 generation 的公开 task lane。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A13 | passed | specs/pipeline-causal-flow-contract/spec.md | 已退役的 `cap4k-plugin-code-analysis-flow-export`、`cap4kFlow*` tasks、plugin id、alias 和第二套 output contract 不得恢复。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A14 | passed | specs/pipeline-causal-flow-contract/spec.md | Flow 只消费 `AnalyzerSnapshot.graph` 及其 completeness、freshness、source identity 和 diagnostics。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A15 | passed | specs/pipeline-causal-flow-contract/spec.md | Drawing Board Design Projection 与 Aggregate Structure 不得补造 Flow 节点、边、入口或 process 关系。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A16 | passed | specs/pipeline-causal-flow-contract/spec.md | raw Graph 可以包含默认 Flow 不展示的技术事实，例如 Query、Capability、普通 `validator` observation 和 read-side dependency。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A17 | passed | specs/pipeline-causal-flow-contract/spec.md | `unique-validator` 等已退役 Aggregate Structure 类型不得因 Flow 重新出现。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A18 | passed | specs/pipeline-causal-flow-contract/spec.md | Graph 缺失、不可解析 relationship、identity 冲突或会使投影不可信的完整性问题必须产生可操作诊断；合法零 Flow 与分析不完整必须可区分。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A19 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认可见节点： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A20 | passed | specs/pipeline-causal-flow-contract/spec.md | 具备入口证据的实际代码节点； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A21 | passed | specs/pipeline-causal-flow-contract/spec.md | Command； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A22 | passed | specs/pipeline-causal-flow-contract/spec.md | Domain Event； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A23 | passed | specs/pipeline-causal-flow-contract/spec.md | Integration Event。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A24 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认隐藏但保留在 raw Graph 中的节点： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A25 | passed | specs/pipeline-causal-flow-contract/spec.md | Command Handler； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A26 | passed | specs/pipeline-causal-flow-contract/spec.md | Domain Event Handler； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A27 | passed | specs/pipeline-causal-flow-contract/spec.md | Integration Event Handler； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A28 | passed | specs/pipeline-causal-flow-contract/spec.md | Entity Method。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A29 | passed | specs/pipeline-causal-flow-contract/spec.md | Query、Capability、普通 Validator、read-side dependency 和非 Command/Event 技术关系不进入默认业务因果链。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A30 | passed | specs/pipeline-causal-flow-contract/spec.md | 入口节点不是新增的抽象 `Entry` 节点。默认业务因果入口按触发来源解释为 Actor、Event、Time 三类：Actor 当前由annotated Spring HTTP Controller method、typed Spring MVC `EndpointMvcBinding` Provider registration与typed `EndpointRpcProviderBinding` Provider registration提供生产 evidence；Event当前由无上游Inbound Integration Event提供生产 evidence；Time当前由Spring `@Scheduled` method提供Temporal Trigger evidence。GraphQL、CLI、Admin、workflow、webhook、CDC、其他消息adapter和其他scheduler provider可以归入这些概念家族，但只有生产Analyzer提供真实节点和明确relationship evidence后才成为当前支持入口。入口类别不是Public Docs、Skill或永久手写封闭taxonomy，也不新增`entryFamily` wire。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A31 | passed | specs/pipeline-causal-flow-contract/spec.md | Typed Endpoint HTTP Actor root MUST use the concrete `endpointhttpbinding` node derived from binding kind plus operation identity. Typed Endpoint RPC Actor root MUST use `endpointrpcproviderbinding` derived from binding kind、serviceId与operation identity。Contract declaration、Provider Handler单独存在、Consumer remote Handler/client artifact和local Endpoint dispatch不是entry。Command-oriented Provider binding具备Flow资格；Query-oriented binding保留raw Graph evidence但不得增加默认Flow数量。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A32 | passed | specs/pipeline-causal-flow-contract/spec.md | Temporal Trigger 使用实际 scheduled method identity，node type 为 `temporaltriggermethod`，到 Command 的明确 relationship 为 `TemporalTriggerMethodToCommand`。独立周期 Job 可以成为 Time root；由已有业务链登记的 scheduled/delayed Command、可靠 worker 到期唤醒或延迟 Event delivery 是已有因果 continuation 或执行策略，不创建抽象 Temporal root。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A33 | passed | specs/pipeline-causal-flow-contract/spec.md | `commandsendermethod` 与 `CommandSenderMethodToCommand` 完全删除。普通未分类方法调用 Command 不再形成默认 Flow 入口，也不保留 raw fallback、alias 或迁移桥；未来真实入口必须新增对应 Analyzer detector。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A34 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认 Flow 只处理生产 Analyzer 明确识别的 causal relationship，以及从实际入口节点到 Command 的可验证显式 `*ToCommand` trigger relationship。当前显式入口关系包括 `ControllerMethodToCommand`、`EndpointHttpBindingToCommand`、`EndpointRpcProviderBindingToCommand` 与 `TemporalTriggerMethodToCommand`；Inbound Integration Event 通过 `IntegrationEventToHandler` / `IntegrationEventHandlerToCommand` 进入因果链。`EndpointHttpBindingToQuery`与`EndpointRpcProviderBindingToQuery`保留在raw Graph但不是causal Flow edge。relationship名称以`ToCommand`结尾不是入口资格的充分条件；source node role必须与该relationship匹配，generic sender fallback不存在。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A35 | passed | specs/pipeline-causal-flow-contract/spec.md | 当两个可见节点之间存在 raw causal path，且内部节点全部属于隐藏集合时，建立一条直接投影边。隐藏路径长度不设固定上限。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A36 | passed | specs/pipeline-causal-flow-contract/spec.md | 例如： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A37 | passed | specs/pipeline-causal-flow-contract/spec.md | 投影为： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A38 | passed | specs/pipeline-causal-flow-contract/spec.md | 以及： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A39 | passed | specs/pipeline-causal-flow-contract/spec.md | 完整链投影为： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A40 | passed | specs/pipeline-causal-flow-contract/spec.md | Command 和 Event 是可见边界，因此路径在这些节点处分段压缩，不把整条链压成入口到最终 Command 的单边。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A41 | passed | specs/pipeline-causal-flow-contract/spec.md | 路径压缩只穿过已知隐藏因果角色。遇到既不可见、也不属于隐藏集合的未知 Graph 节点时，不把它自动视为透明中间节点。新增可压缩技术角色属于 Analyzer contract 变化，必须由生产代码、测试和 capability propagation 明确声明。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A42 | passed | specs/pipeline-causal-flow-contract/spec.md | 分叉、汇合和共享后缀必须保留。raw edge 与 projected edge 使用稳定 identity 去重，不依赖输入目录或遍历顺序。投影实现保留至少一条对应 raw path evidence 供诊断和测试使用；是否扩展为多路径 evidence 或公开 wire 字段属于独立合同变化，不在本合同中隐式增加。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A43 | passed | specs/pipeline-causal-flow-contract/spec.md | 只有同时满足以下条件的节点才生成默认 Flow root： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A44 | passed | specs/pipeline-causal-flow-contract/spec.md | 生产 Analyzer evidence 能证明节点属于当前支持的 Actor、Event 或 Time trigger，并具备入口资格； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A45 | passed | specs/pipeline-causal-flow-contract/spec.md | 完成可见投影和隐藏路径压缩后，该节点没有上游因果边； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A46 | passed | specs/pipeline-causal-flow-contract/spec.md | 该节点在最终投影中存在下游因果边。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A47 | passed | specs/pipeline-causal-flow-contract/spec.md | 入度为零只是必要条件，不是入口资格的充分条件。孤立 Command/Event、因 metadata 缺失而失去上游的节点、仅因名称或包路径像入口的节点，都不得自动升级为 root。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A48 | passed | specs/pipeline-causal-flow-contract/spec.md | 有上游因果边的中间 Integration Event、Command、Temporal Trigger method 或其他节点不得制造重复 Flow。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A49 | passed | specs/pipeline-causal-flow-contract/spec.md | 两个各自具有真实入口证据、最终投影后均为零入度的入口必须分别生成两张 Flow，即使它们共享下游后缀。共享 Command、Event、节点名称或静态可达性不是自动 stitching 的充分证据。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A50 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认 Flow 不强制为 DAG，不拒绝合法循环，不折叠强连通分量。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A51 | passed | specs/pipeline-causal-flow-contract/spec.md | 遍历使用稳定 visited node/edge 语义有限结束。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A52 | passed | specs/pipeline-causal-flow-contract/spec.md | 入口可达的 visible cycle 必须保留，不能静默截断或伪装成 DAG。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A53 | passed | specs/pipeline-causal-flow-contract/spec.md | 没有可证明入口的纯循环仍属于 raw Graph 事实，但不发明默认 Flow root。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A54 | passed | specs/pipeline-causal-flow-contract/spec.md | 节点、边、entry 和 index 使用稳定排序；重复 raw edge、projected edge 和重复 entry identity 的结果必须确定。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A55 | passed | specs/pipeline-causal-flow-contract/spec.md | 每个真实 root 生成： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A56 | passed | specs/pipeline-causal-flow-contract/spec.md | 并统一生成： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A57 | passed | specs/pipeline-causal-flow-contract/spec.md | Entry JSON 至少包含： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A58 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryId`； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A59 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryType`； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A60 | passed | specs/pipeline-causal-flow-contract/spec.md | 不新增 `entryFamily`；Actor / Event / Time 是合同解释，不是新的 wire 字段； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A61 | passed | specs/pipeline-causal-flow-contract/spec.md | `nodeCount`； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A62 | passed | specs/pipeline-causal-flow-contract/spec.md | `edgeCount`； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A63 | passed | specs/pipeline-causal-flow-contract/spec.md | projected nodes； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A64 | passed | specs/pipeline-causal-flow-contract/spec.md | projected edges。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A65 | passed | specs/pipeline-causal-flow-contract/spec.md | Index 至少包含 input identities、entry type summary、node/edge summary、`flowCount` 和每张 Flow 的 JSON/Mermaid reference。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A66 | passed | specs/pipeline-causal-flow-contract/spec.md | 每个 `flows/<entry-slug>.mmd` MUST 是语法有效、可由 Mermaid flowchart parser 读取的文本。所有节点统一使用 quoted label 形式 `N["..."]`；节点名称中的方括号、花括号、路径分隔符、引号、反斜杠、换行和 HTML-sensitive 字符不得逃逸节点声明。Kotlin generator MUST 对 label 内容执行确定性转义，且不得通过修改 Analyzer display name、Flow JSON identity 或图拓扑来规避呈现语法。 本合同不改变既有 JSON、Mermaid、index wire shape、output root、slug 规则或 `flow` identity。连续链修正与回归证据不得通过增加 process artifact、process index 或第二个 generator id 实现。 | Spec 明确 parser-readable quoted labels；生产实现与 focused test 覆盖方括号、花括号、路径、引号、反斜杠、换行及 HTML-sensitive 字符。 |
| A67 | passed | specs/pipeline-causal-flow-contract/spec.md | Issue #55 观察到旧项目中“外部事实到达 follow-up Command 后，后续业务链出现在另一张 Flow”的现象。当前目标按以下规则判断： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A68 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果 raw Graph evidence 连续，完整的 `Inbound Integration Event -> Command -> Domain Event -> follow-up Command` 必须在同一张 entry-centered Flow 中出现，不需要 process stitching。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A69 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果拆分来自中间节点被错误提升为 root、隐藏路径未正确收缩或 relationship 丢失，应修复或以 regression fixture 防止回归。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A70 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果两个节点各自具有独立入口证据且最终投影后均为零入度，两张 Flow 是正确结果。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A71 | passed | specs/pipeline-causal-flow-contract/spec.md | 多张真实入口 Flow 通过稳定 entry identity、index 和共享可见节点关联阅读；默认产品不自动推断它们属于同一个业务过程。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A72 | passed | specs/pipeline-causal-flow-contract/spec.md | 本次不使用长期未更新的 `cap4k-reference-content-studio` 作为验证门槛，使用仓库内等价 focused 与 Gradle functional fixture 提供自动化证据，不声明该下游项目兼容性。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A73 | passed | specs/pipeline-causal-flow-contract/spec.md | causal edge 选择、节点角色、入口资格、路径压缩、root、循环、去重、Mermaid graph topology、node label assembly 与转义由 Kotlin 生产代码拥有。 | Kotlin 生产代码拥有 topology、label assembly 与转义；Pebble entry.mmd.peb 仍仅 raw 透传 mermaidText。 |
| A74 | passed | specs/pipeline-causal-flow-contract/spec.md | Generator planner 把已计算结果规划为 JSON、Mermaid 和 index artifacts。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A75 | passed | specs/pipeline-causal-flow-contract/spec.md | Pebble 模板只负责写出 planner 提供的内容，不决定节点、边、入口、路径压缩或 Flow 数量。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A76 | passed | specs/pipeline-causal-flow-contract/spec.md | 项目用户可以启用或禁用 `flow`、配置 output root，并按模板合同调整呈现；不能注入自定义遍历、root 或 stitching 算法。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A77 | passed | specs/pipeline-causal-flow-contract/spec.md | 本合同不新增 topology DSL、arbitrary user projection 或 process profile。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A78 | passed | specs/pipeline-causal-flow-contract/spec.md | 需要不同图的高级消费者可以读取 raw Graph 并在 cap4k 产品边界之外生成自有视图。若未来多个项目形成稳定共同需求，应创建独立 Change，设计由插件拥有、命名明确、合同固定的 projection profile，而不是在模板中实现图语义。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A79 | passed | specs/pipeline-causal-flow-contract/spec.md | 保留 `surface.analyzer`、`pipeline.generator.flow` 和 output `flow` 的当前 identity。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A80 | passed | specs/pipeline-causal-flow-contract/spec.md | Analyzer Graph partition 的 Flow consumer 仍只有 `pipeline.generator.flow`，不得新增未实现的 process consumer 或 output。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A81 | passed | specs/pipeline-causal-flow-contract/spec.md | `CapabilityContractFacts`、AgentFacts、Public Docs 和 Skill 必须表达 Actor / Event / Time trigger、annotated Controller、typed Endpoint HTTP与typed Endpoint RPC Provider等当前实际detectors、Command-oriented Endpoint root / Query non-root、Provider-only RPC entry、默认 entry-centered Flow、隐藏路径压缩、root-after-projection、cycle preservation 和唯一产品入口。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A82 | passed | specs/pipeline-causal-flow-contract/spec.md | Public Docs 与 Skill 必须说明连续 graph evidence 生成一张 Flow、两个真实 root 生成两张 Flow，以及共享后缀不触发自动 stitching。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A83 | passed | specs/pipeline-causal-flow-contract/spec.md | Public Docs、Skill 和 AgentFacts 不得宣称 process projection、process output 或用户自定义拓扑算法已经可用。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A84 | passed | specs/pipeline-causal-flow-contract/spec.md | Runtime 对本合同为 `verified-no-change` 或 `not-applicable`，除非实际实现修改了 Runtime contract。 | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A85 | passed | specs/pipeline-causal-flow-contract/spec.md | Focused tests 必须覆盖： | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A86 | passed | specs/pipeline-causal-flow-contract/spec.md | 完整入站 Integration Event 到 follow-up Command 的单 fixture，并断言一张 Flow； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A87 | passed | specs/pipeline-causal-flow-contract/spec.md | Spring `@Scheduled` method 到 Command 的 Temporal Trigger fixture，并断言实际 method identity、`temporaltriggermethod`、`TemporalTriggerMethodToCommand` 和唯一 root； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A88 | passed | specs/pipeline-causal-flow-contract/spec.md | typed Spring MVC Endpoint binding到Command的fixture，断言operation-based `endpointhttpbinding` identity、`EndpointHttpBindingToCommand`与唯一root；对应Query fixture只保留`EndpointHttpBindingToQuery` Graph evidence且不增加flowCount； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A89 | passed | specs/pipeline-causal-flow-contract/spec.md | typed Endpoint RPC Provider binding到Command的fixture，断言`endpoint-rpc:<serviceId>:<operationName>` identity、`endpointrpcproviderbinding`、`EndpointRpcProviderBindingToCommand`与唯一root；对应Query fixture只保留`EndpointRpcProviderBindingToQuery` Graph evidence且不增加flowCount； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A90 | passed | specs/pipeline-causal-flow-contract/spec.md | 同一operation的HTTP与RPC Provider bindings产生两个真实entry并共享下游；Consumer proxy不增加第三个root； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A91 | passed | specs/pipeline-causal-flow-contract/spec.md | Endpoint contract-only、Handler-only、Consumer client、local dispatch和缺失registration fixtures均为零Endpoint Actor Flow；WebMvc.fn binding不与annotated Controller detector重复root，ordinary Controller fixture保持既有行为； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A92 | passed | specs/pipeline-causal-flow-contract/spec.md | 普通未分类 method 直接发送 Command 不生成 `commandsendermethod`、`CommandSenderMethodToCommand` 或默认 Flow； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A93 | passed | specs/pipeline-causal-flow-contract/spec.md | Handler 与任意长度 Entity Method 路径隐藏和收缩； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A94 | passed | specs/pipeline-causal-flow-contract/spec.md | 有上游节点不制造重复 root； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A95 | passed | specs/pipeline-causal-flow-contract/spec.md | 两个真实入口和共享下游保持两张 Flow； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A96 | passed | specs/pipeline-causal-flow-contract/spec.md | fan-out、merge、visible cycle、纯循环、稳定去重和 source evidence； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A97 | passed | specs/pipeline-causal-flow-contract/spec.md | 合法零 Flow 与无效 Graph evidence 的区别； | 该完整目标条款对应的既有 Flow 合同与生产行为未被本 diff 改变；独立 Verifier 抽查相关实现/fixture，完整 Gradle check 继续通过。 |
| A98 | passed | specs/pipeline-causal-flow-contract/spec.md | 含 `operation [METHOD /path/{id}]`、引号、反斜杠、换行与 HTML-sensitive 字符的节点名称生成单个语法有效的 quoted Mermaid label，并保持 JSON identity、节点/边数量和 relationship 不变。 | 新增特殊字符 fixture 精确断言单个 quoted label；JSON 原始名称、拓扑、计数与 relationship 路径保持不变。 |
| A99 | passed | specs/pipeline-causal-flow-contract/spec.md | Gradle functional fixture 必须实际执行 `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`，并核对： | EndpointHttpBindingFlowFunctionalTest 明确实际调用 cap4kAnalysisPlan 与 cap4kAnalysisGenerate。 |
| A100 | passed | specs/pipeline-causal-flow-contract/spec.md | entry JSON、Mermaid、index 均实际生成； | Functional test 核对 entry JSON、Mermaid 与 index 三类文件均实际生成。 |
| A101 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryId`、entry type、Controller/Endpoint HTTP/Endpoint RPC Provider/Temporal Trigger relationship、节点/边计数和 `flowCount` 一致； | Functional test 核对 entryId、entryType、EndpointHttpBindingToCommand、节点/边计数与 flowCount；其他入口 fixture 随完整 check 通过。 |
| A102 | passed | specs/pipeline-causal-flow-contract/spec.md | Handler 与 Entity Method 不进入公开 Flow； | Functional test 断言 Mermaid 不含 EndpointHandler；既有压缩测试覆盖 Handler/Entity Method 不进入公开 Flow。 |
| A103 | passed | specs/pipeline-causal-flow-contract/spec.md | 不生成 process artifact、第二套 Flow output 或已退役 task。 | Functional test 断言不生成 process artifacts；diff 未新增第二 output、generator id 或退役 task。 |
| A104 | passed | specs/pipeline-causal-flow-contract/spec.md | Change Verify 还必须运行完整 Gradle `check`、capability facts export/validate/test、Skill validator、current Runtime facts validator、PR workflow tests 和 `git diff --check`。自动化证据与真实项目证据分别记录；未运行长期漂移的 downstream 项目必须明确声明，不得形成兼容性结论。 | Runtime checks 记录完整 Gradle check、capability export/validate/test、Skill、Runtime facts、PR workflow guards 与 git diff --check 全部通过。 |

## Checks

| Check | Command | Working directory | Status | Exit | Duration |
| --- | --- | --- | --- | ---: | ---: |
| Full Gradle check | -NoProfile -Command .\gradlew.bat check --no-parallel --console=plain | . | passed | 0 | 484952 ms |
| Capability, Skill, Runtime, and PR governance checks | -NoProfile -Command $ErrorActionPreference='Stop'; & .\scripts\export-capability-contract-facts.ps1; & .\scripts\validate-capability-contract.ps1; & .\scripts\test-capability-contract.ps1; & .\skills\scripts\validate-cap4k-skills.ps1; & .\scripts\validate-current-runtime-facts.ps1; & .\scripts\test-pr-workflow.ps1 | . | passed | 0 | 176298 ms |
| Git diff whitespace check | diff --check | . | passed | 0 | 105 ms |

## Blockers

_None._

## Risks and skipped work

- 未引入或实际运行第三方 Mermaid parser；语法有效性依据统一 quoted-label 结构、确定性转义、精确 focused test、真实 Gradle functional fixture 与完整 check 核验，属于已接受非阻断限制。
- 新增特殊字符 focused test 未在同一测试中逐字段断言 topology；不变性由仅修改 renderMermaid 的 diff 与 Endpoint HTTP functional fixture 的 identity/count/relationship 断言共同覆盖。

## Previous iterations

| Goal cycle | Iteration | Attempt | Outcome | Unresolved | Summary | Completed |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | pass | — | 通过。唯一生产变化是 FlowGraphSupport 的 Mermaid node label assembly/escaping；JSON、projection、root、slug、index、edge rendering 与 Pebble raw-pass-through 均保持不变。完整目标 Spec 仅补充 Mermaid 语法合同和职责边界；A1-A104 全部通过。 | 2026-08-17T12:40:43.152Z |

## Conclusion

通过。唯一生产变化是 FlowGraphSupport 的 Mermaid node label assembly/escaping；JSON、projection、root、slug、index、edge rendering 与 Pebble raw-pass-through 均保持不变。完整目标 Spec 仅补充 Mermaid 语法合同和职责边界；A1-A104 全部通过。
