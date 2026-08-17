# Agent API

`cap4kAgentSnapshot` 是 cap4k 的只读、Gradle-first agent inspection surface。它不替代 `cap4kPlan`、generation 或 analysis mutation tasks，也不连接数据库等 live external source。

## Output

默认输出目录是 `build/cap4k/agent/`：

<!-- CAPABILITY_CONTRACT:AGENT_SECTIONS -->
| File | Purpose |
| --- | --- |
| `manifest.json` | 小型入口，包含 contract/cap4k version、snapshot identity、project summary、section status/hash/count 和推荐读取分区。 |
| `project.json` | repository、module、base package 与 public task shape。 |
| `capabilities.json` | provider descriptor 派生的 supported catalog 与当前 project effective view。 |
| `inputs.json` | configured input、local path/readability、external-I/O safety、脱敏 identity 与显式 plan task。 |
| `ownership.json` | 既有 plan 中的 output kind、conflict policy、managed roots 与 freshness。 |
| `runtime.json` | v3 静态 Runtime capability/provider facts、Event Handler contract、runtime/provider boundaries 与已加载 extension contribution 概要。 |
| `analysis.json` | v2 Analyzer 分区事实：`graph`、`designProjection`、`aggregateStructure` 各自的 requested/status/count/source/freshness/output/diagnostic。 |
| `diagnostics.json` | 稳定 diagnostic identity、level、stage、path、message 与 actionable hint。 |
<!-- /CAPABILITY_CONTRACT:AGENT_SECTIONS -->

读取方先读 `manifest.json`，再按当前操作选择分区。一次 task execution 产生的所有文件共享同一个 snapshot identity；不要跨两次执行拼接分区。

## Status

<!-- CAPABILITY_CONTRACT:AGENT_SNAPSHOT_STATUS -->
- `ok`：分区完整且当前检查未发现阻塞。
- `partial`：project 有效，但非必需分区不适用、不可用，或 extension 仅提供 identity-level metadata；task 可以成功。
- `invalid`：cap4k configuration、必需 input 或 provider validation 无效；task 尽量写完可获得分区与 diagnostics，然后非零退出。
- `unavailable`：必需 project state 无法取得；task 尽量留下原因与 diagnostics，然后非零退出。
<!-- /CAPABILITY_CONTRACT:AGENT_SNAPSHOT_STATUS -->

只有 Gradle 在 task 注册或启动前已经灾难性失败时，才可能完全没有 snapshot。此时读取普通 Gradle failure，不要声称已读取 Agent API。

## Capability Views

<!-- CAPABILITY_CONTRACT:AGENT_CAPABILITY_VIEWS -->
`capabilities.json` 的 `supported` 描述当前 plugin 版本内建 provider 和本次 inspection 成功加载的 extension contribution。`effective` 描述同一 stable capability identity 在当前 project 中的状态。
<!-- /CAPABILITY_CONTRACT:AGENT_CAPABILITY_VIEWS -->

<!-- CAPABILITY_CONTRACT:AGENT_EFFECTIVE_STATUS -->
`effective` 状态闭集是 `configured`、`ready`、`blocked`、`not_applicable`。未配置不等于当前版本不支持；已支持也不等于当前项目立即可运行。
<!-- /CAPABILITY_CONTRACT:AGENT_EFFECTIVE_STATUS -->

Capability category、input requirement、相关 Gradle tasks、output ownership、tactical carrier 与 runtime/provider/analyzer boundary 来自 provider descriptor。Gradle adapter 不维护第二套 provider catalog。

Supported capability 还会披露 activation：`explicit_configuration` 表示必须由 project 显式启用，`input_driven` 表示相应 input 满足后自动参与 planning，`installed` 表示已加载的 extension contribution 会参与当前 pipeline。运行器与 Agent effective view 共用这项 descriptor metadata；不要根据 provider ID 猜测 activation。

## Evidence Freshness

<!-- CAPABILITY_CONTRACT:AGENT_EVIDENCE_FRESHNESS -->
Agent task 可以读取既有 `build/cap4k/plan.json` 和 `build/cap4k/analysis-plan.json`，但不会主动刷新它们。只有 evidence 中的 configuration/local-input identity 与当前 snapshot 一致，且不依赖无法证明当前状态的 live external input 时，才可标为 `fresh`。其余状态是 `stale`、`unknown` 或 `missing`，并建议显式运行对应 plan task。
<!-- /CAPABILITY_CONTRACT:AGENT_EVIDENCE_FRESHNESS -->

<!-- CAPABILITY_CONTRACT:AGENT_VALIDATION_STATUS -->
Agent validation 的状态闭集是 `verified`、`unknown`、`failed`。它只表达当前 snapshot 中相应 validation evidence 的结论。
<!-- /CAPABILITY_CONTRACT:AGENT_VALIDATION_STATUS -->

<!-- CAPABILITY_CONTRACT:AGENT_DIAGNOSTIC_LEVEL -->
`diagnostics.json` 的 level 闭集是 `error`、`warning`、`info`。
<!-- /CAPABILITY_CONTRACT:AGENT_DIAGNOSTIC_LEVEL -->

配置了 DB source 时，Agent task 只报告 source 已配置、所需 task 与脱敏 identity；它不连接数据库，也不声称 live schema 已验证为最新。

`analysis.json` 使用 `cap4k.agent.analysis.v2`，固定公开 `graph`、`designProjection`、`aggregateStructure` 三个分区。每个分区独立报告 `requested`、`status`、`counts`、`sources`、`freshness`、`plannedOutputPaths`、`availableOutputPaths`、`diagnosticIds`、`nextAction` 与 `reason`。`sources` 的稳定 identity 和本地路径用于解释跨模块合并与冲突证据；读取方不得用另一个分区的成功掩盖当前分区的不完整。

section 顶层 `status` 只聚合本次 generator 配置实际请求的分区：`flow` 请求 `graph`；`drawing-board` 请求 `designProjection` 与 `aggregateStructure`。未请求分区仍保留当前观察事实和诊断，但不会把 section 顶层状态降级。没有任何分区被请求时，section 为 `unavailable`。

每个分区分别区分 `plannedOutputPaths` 与 `availableOutputPaths`。只运行 `cap4kAnalysisPlan` 时，fresh plan 可以证明将生成什么，但不能证明 artifacts 已存在；相应 requested 分区为 `partial` 并给出 `cap4kAnalysisGenerate`。只有该分区的计划输出都存在且不早于当前 plan 时，才把 outputs 报告为 available。

Pipeline Extension discovery 只读取本地 resolved classpath metadata。Extension SPI 要求 provider 构造、descriptor 和 contribution discovery 保持确定、无副作用，不得连接网络、数据库或其他 live source，也不得修改文件或启动进程；真正的 contribution work 只能在显式 pipeline operation 中执行。若 extension inspection 失败，`runtime.externalIoSafe` 为 `false`，snapshot 同时以 structured diagnostic 报告失败，不能对该次检查作安全性声明。

`runtime.json` 的 `eventHandler` 是当前 cap4k 版本的静态 Runtime 作者契约，不是对业务代码做 classpath scan 的观察结果。它说明方法级 `@EventListener` 是唯一作者入口，Domain Event 与 Integration Event 共用同步、串行、fail-fast 执行模型，方法级 `@Order` 只为不同数值建立顺序、相同值不承诺次序，以及 Handler 返回时如何等待当前 scope 的 `askAsync()` / `callAsync()`。`forbidden` 列表用于在写代码前避开启动时会被 Runtime 拒绝的 Handler shape。

## Runtime Facts

Agent contract version 4 保留 `runtime.json` 的 `cap4k.agent.runtime.v3`。该文件仍是静态、只读的框架事实，不启动 Spring application，不检查 classpath 是否装配 provider，也不读取 broker 或 live registry。

<!-- CAPABILITY_CONTRACT:RUNTIME_CAPABILITIES -->
当前 Runtime capability catalog 固定为：

| Capability ID | Contract / implementation owner | Starter owner |
| --- | --- | --- |
| `runtime.core-dispatch` | `ddd-core` | `cap4k-ddd-core-starter` |
| `runtime.endpoint-http-provider` | `ddd-endpoint-http` | `cap4k-ddd-endpoint-http-starter` |
| `runtime.endpoint-rpc-provider` | neutral contract: `ddd-endpoint-rpc`; HTTP/JSON implementation: `ddd-endpoint-rpc-http` | `cap4k-ddd-endpoint-rpc-http-starter` |
| `runtime.endpoint-rpc-consumer` | neutral contract: `ddd-endpoint-rpc`; HTTP/JSON implementation: `ddd-endpoint-rpc-http` | `cap4k-ddd-endpoint-rpc-http-starter` |
| `runtime.identifier-allocation` | `ddd-core` | `cap4k-ddd-core-starter` |
| `runtime.local-domain-event` | `ddd-core` | `cap4k-ddd-core-starter` |
| `runtime.jpa-persistence` | `ddd-domain-repo-jpa` | `cap4k-ddd-jpa-starter` |
| `runtime.reliable-command` | `ddd-application-command-jpa` | `cap4k-ddd-command-jpa-starter` |
| `runtime.reliable-event` | `ddd-domain-event-jpa` | `cap4k-ddd-domain-event-jpa-starter` |
| `runtime.integration-event-transport` | shared contract: `ddd-core` | concrete provider owns implementation/starter |
<!-- /CAPABILITY_CONTRACT:RUNTIME_CAPABILITIES -->

Endpoint RPC 的两个 capability facts 分别表达 Provider ingress 与 Consumer egress，但共享 neutral ABI、HTTP/JSON backend 和 starter ownership。它们只证明当前 framework 支持该能力：静态 snapshot 仍将 `applicationAssembly` 标为 `unknown`，将 `runtimeObservation` 与 `verification` 标为 `not_performed`，不会声称 client artifact、route map、credential、timeout、远端服务或 fixed Provider route 已在当前应用装配或观测。它们没有 Integration Event provider identity，也不会加入下面的 transport provider catalog。

<!-- CAPABILITY_CONTRACT:RUNTIME_PROVIDERS -->
Integration Event transport provider facts 使用与 Runtime registry 注册完全相同的 identity：

- `integration-event-transport.http`；
- `integration-event-transport.rabbitmq`；
- `integration-event-transport.rocketmq`。
<!-- /CAPABILITY_CONTRACT:RUNTIME_PROVIDERS -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_FRAMEWORK_SUPPORT -->
`frameworkSupport` 的状态闭集是 `supported`，只说明当前 cap4k 版本实现了该能力。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_FRAMEWORK_SUPPORT -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_APPLICATION_ASSEMBLY -->
`applicationAssembly` 的状态闭集是 `unknown`，表示 Gradle snapshot 没有启动应用、无法证明当前应用已装配。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_APPLICATION_ASSEMBLY -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_OBSERVATION -->
`runtimeObservation` 的状态闭集是 `not_performed`，表示本次 task 没有执行 live observation。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_OBSERVATION -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_OPERATIONAL_STATE -->
Provider `operationalState` 的状态闭集是 `unknown`，表示静态文件不能证明健康状态。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_OPERATIONAL_STATE -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_VERIFICATION -->
verification 的状态闭集是 `not_performed`，表示本次 snapshot 没有执行该能力的运行验收。类、依赖、配置或说明文字存在都不会把这些字段提升为可用、健康或成功。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_VERIFICATION -->

<!-- CAPABILITY_CONTRACT:AGENT_RUNTIME_LIVE_STATE_SOURCE -->
Provider `liveStateSource` 的闭集是 `runtime_provider_state_registry`。
<!-- /CAPABILITY_CONTRACT:AGENT_RUNTIME_LIVE_STATE_SOURCE -->

Live provider state 的唯一来源是当前应用中的 `RuntimeProviderStateRegistry.snapshot()`；其安全字段为 `providerId`、`state`、`observedAt` 和 `category`。只有实际读取 registry snapshot 才能证明当时的注册与观测状态。`runtime.json` 只声明该 live source，不复制、缓存或推断 registry 内容，也不包含 payload、凭据、URI、broker topology 或异常详情。

## Credential Boundary

Snapshot 不序列化 password、token、private key、原始 connection string、内嵌 credential 或 extension option value。Options 只披露 configured/sensitive key；stable identity 对敏感 value 只编码存在性，密码变化不会产生可用于猜测密码的 value hash。

## Ownership Projection

`ownership.json` 中的 `items` 使用与 `build/cap4k/plan.json` item 相同的 `generatorId`、`templateId`、`outputPath`、`outputKind`、`conflictPolicy` 和 `resolvedOutputRoot` 语义。`outputPath` 始终是完整的 repo-relative target path，不应与 `resolvedOutputRoot` 再次拼接；`resolvedOutputRoot` 是可选的 root metadata，checked-in source 可以为空，也可以由 planner 提供 source root。
