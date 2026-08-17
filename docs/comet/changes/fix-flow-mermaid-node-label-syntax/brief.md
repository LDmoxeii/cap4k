# Outcome

修复 Pipeline Flow Mermaid 输出对含方括号、花括号、路径和引号等标点的节点名称生成非法语法的问题，使现有 `flows/*.mmd` 可以被 Mermaid 解析；同时把“Mermaid artifact 必须语法有效、节点使用 quoted label”补入 Pipeline Causal Flow canonical contract，并保持 Flow JSON、拓扑、identity、slug、index 和模板边界不变。

# Scope

- 修改 Flow generator 的 Mermaid 节点渲染：所有节点标签使用 Mermaid quoted label 形式。
- 对 quoted label 内的双引号、反斜杠、换行和现有 HTML-sensitive 字符执行确定性转义，避免标签内容逃逸节点语法。
- 更新 `pipeline-causal-flow-contract`，明确 `.mmd` 必须是可解析的 Mermaid flowchart，节点 label assembly 与转义由 Kotlin generator 拥有。
- 添加 focused generator 回归测试，覆盖 Endpoint HTTP 名称 `operation [METHOD /path/{id}]` 以及引号等特殊字符。
- 更新 Endpoint HTTP Gradle functional fixture，断言实际生成的 `.mmd` 使用合法 quoted label，同时既有 JSON、关系与 Flow 数量语义保持不变。
- 运行 Flow generator 与 Pipeline Gradle focused tests，并按仓库治理核对 capability propagation。

# Non-goals

- 不修改 Analyzer 产生的 Endpoint HTTP display name。
- 不修改 Flow JSON、index wire shape、节点或边 identity、slug、root、路径压缩和图拓扑。
- 不引入 Mermaid parser/runtime dependency，也不改变 Pebble 模板的 raw-pass-through 职责。
- 不生成 Endpoint Handler 或 HTTP/RPC binding。
- 不处理 Payment reference 项目自身的业务模型、模块结构或代码风格。
- 不在本 change 修复 repository PR finish provider 对 `spec_changes: []` 的兼容缺陷；本 change 有真实 canonical spec clarification。

# Acceptance examples

- A1: 名称为 `payment.attempt.start [POST /api/payments/{paymentId}/attempts]` 的节点生成 `N1["payment.attempt.start [POST /api/payments/{paymentId}/attempts]"]`，不再生成嵌套未引用方括号形式。
- A2: 节点名称包含双引号、反斜杠、换行或 HTML-sensitive 字符时，生成结果保持单个合法 quoted label，标签内容不会逃逸 Mermaid 节点声明。
- A3: Endpoint HTTP functional fixture 实际执行 analysis plan/generate 后，`.mmd` 包含 quoted Endpoint label，JSON 的 entry identity、节点/边数量、relationship 和唯一 Flow 语义保持不变。
- A4: `pipeline-causal-flow-contract` 明确 `.mmd` 的语法有效性、quoted node label、确定性转义和 Kotlin/Pebble 职责边界，Archive 后 canonical spec 与完整目标一致。
- A5: Flow generator focused tests、Endpoint HTTP Gradle functional test 与完整 Gradle `check` 通过，且 `git diff --check` 通过。
- A6: Runtime、Analyzer、AgentFacts、Public Docs 与 Skill 均完成 capability propagation 审查；Generator 与 canonical Flow artifact contract 为 modified，其他 surface 只有必要变化或 verified-no-change。

# Constraints and invariants

- Kotlin production code继续拥有 Mermaid graph topology、node label assembly 与转义；Pebble 只透传 `mermaidText`。
- 转义结果必须确定，不依赖输入遍历顺序或平台换行。
- 现有 edge label、JSON 和 index 输出不得因本修复发生无关变化。
- canonical spec clarification 不新增 output、task、generator identity、wire 字段或用户可配置拓扑。
- 变更在独立 worktree 与合法 `fix/*` 短期分支完成，不修改 `master`。

# Decisions

- 采用 Mermaid quoted label `N["..."]` 修复节点标签，而不是修改 Analyzer display name 或要求下游展示层特殊处理。
- 所有节点统一使用 quoted label，避免按字符分类产生两套输出规则。
- 将语法有效性与 quoted-label 要求写入 `pipeline-causal-flow-contract`，作为既有公开 `.mmd` artifact 的合同澄清，不新增能力身份。
- 不引入第三方 Mermaid parser；focused 精确输出、真实 Gradle functional fixture和完整 Gradle check作为回归证据。
- 已验证但分支不符合 repository PR policy 的旧 change 以 `keep` 归档；本 change 从当前 `origin/master` 在合法 `fix/*` 分支重建并重新 Verify，旧证据只作线索、不替代本 change 验收。

# Open questions

- None.

# Verification expectations

- 运行 `:cap4k-plugin-pipeline-generator-flow:test` 的 focused 测试。
- 运行 `:cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.EndpointHttpBindingFlowFunctionalTest"`。
- 运行完整 `./gradlew check`。
- 运行 capability facts export/validate/test、Skill validator、current Runtime facts validator及 PR workflow guard；按传播闭包记录 modified / verified-no-change / not-applicable。
- 运行 `git diff --check` 并检查工作区只包含本 change 文件。