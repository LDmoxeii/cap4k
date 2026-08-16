# Outcome

从 cap4k 当前能力面一次性完整退役 framework-level `api_payload`：Endpoint Request/Response 成为 published Actor API 的唯一结构契约，adapter 特殊协议形态通过 typed binding mapper/policy 或 private handwritten implementation type 处理，不再由 Design JSON、canonical model、Generator、Analyzer、Drawing Board、AgentFacts 或 Public Docs 发布第二套 API Payload schema。

# Scope

- 从 Design JSON 语言删除 `api_payload` tag、`api-payload` artifact family 与 page variant；输入使用这些值时直接失败。
- 从 Pipeline API、semantic roles、artifact layout/resolver、canonical assembler、source descriptor、planner registry、Gradle DSL/config projection 与 Drawing Board supported carriers 删除 API Payload surface。
- 删除 `DesignApiPayloadArtifactPlanner`、专属 render-model entry、`api_payload.kt.peb`、planner/template tests 与 API Payload functional fixtures。
- 删除 Analyzer 对 API Payload 的专属 node/recovery、unannotated `Request`/`Response` wrapper heuristic 与 round-trip projection。
- 更新混合 fixtures/tests，使当前七个 Design JSON tags、Query page 与 Endpoint Request/Response 继续获得完整 generation/compile/Analyzer/round-trip evidence。
- 更新 canonical Specs、code-derived AgentFacts/snapshots、Public Docs 和当前 authoring surface；历史 archive、historical plans/specs 和 Git provenance 不批量改写。
- 该 change 作为 `actor-endpoint-http-binding`（#193）的 prerequisite；合入 `master` 后从新基线 refresh #193 Shape。

# Non-goals

- 实现 Spring MVC ActorEndpoint Provider binding、HTTP runtime、OpenAPI、Feign/RPC 或 Consumer proxy。
- 删除项目作者可能手写的普通 private/internal DTO；这类类型不属于 cap4k capability contract。
- 为 `api_payload` 提供 deprecated alias、自动迁移、warning period、compatibility parser、no-op planner、旧 Gradle DSL bridge 或 migration document。
- 把 API Payload page variant 转移成另一套新 payload family；Query page 保持现状，published Endpoint 的分页语义由 Endpoint Request/Response 显式表达。
- 修改历史 `docs/comet/archive/**`、`docs/superpowers/**` 或 Git 历史中的事实记录。

# Acceptance examples

- A1：Design JSON descriptor 不再列出 `api_payload`/API Payload；`tag = api_payload`、family `api-payload` 或其 `page` variant 以 unsupported diagnostic 失败且无 alias/fallback。
- A2：Pipeline API 与 canonical model 不再包含 API Payload semantic roles、`designApiPayload` layout/resolver、default artifact 或 tactical carrier；其他 semantic value roles 保持可编译和稳定。
- A3：Generator/renderer/Gradle plugin 不再注册 `api-payload` planner、template、known generator id 或 DSL/config projection，专属生产文件和 fixture 目录被删除。
- A4：Drawing Board 与 Analyzer 不再产生、恢复或猜测 `api_payload` block/node；普通未注解的顶层 `Request`/`Response` wrapper 不再被启发式识别为 API Payload。
- A5：Design round-trip 的当前七个 tags 全部通过真实 generation → compile → Analyzer → Drawing Board → regeneration；Query page、Endpoint `operationName`、Request/Response 与 `EndpointRequest<Response>` 语义不回归。
- A6：当前生产代码、非历史 tests/fixtures、canonical Specs、Public Docs、AgentFacts 和 Skill projection 对 `api_payload`、`api-payload`、`API Payload`、`designApiPayload` 与专属 planner/template surface 达到零活跃引用。
- A7：普通 published API 的结构事实源只剩 Endpoint Request/Response；特殊 adapter mapping 可使用 private handwritten intermediate type，但该类型不进入 Design JSON、canonical model、Generator、Analyzer、Drawing Board 或 AgentFacts。
- A8：Agent Snapshot/manifest 由 production descriptors 重新导出，不再包含 API Payload Generator 或 Design JSON API Payload tactical carrier，hash 与 focused expectations 同步更新。
- A9：Public Docs 只描述当前七个 Design JSON tags 和 Endpoint/API binding 边界，不新增退役兼容说明；历史资产保留为 provenance。
- A10：capability contract validation、Runtime/Skill guards、Analyzer/round-trip focused suites、Pipeline functional fixtures与完整 Gradle `check` 通过。

# Constraints and invariants

- 当前框架没有外部用户，本 change 是直接 breaking cleanup；正确性和单一事实源优先于兼容性。
- 删除必须贯穿 Source → Pipeline API → canonical → Generator/Renderer → Gradle → Analyzer/Drawing Board → AgentFacts/Public Docs 的完整 propagation closure，不能只隐藏入口或删除模板。
- Endpoint、Command、Query、Capability、Domain Event、Integration Event、Domain Service 的已接受 semantic roles 与 ownership 不得因删除共享 API Payload 分支而回归。
- Adapter private DTO 是普通 Kotlin implementation detail，不是新的 tactical tag、generated artifact family 或 published contract。
- Facts 继续从 production descriptors/registries 导出；Public Docs、Skill 或手写 snapshot 不得反向成为能力目录。
- 删除/移动文件时只作用于本 change worktree 内已核对的 API Payload 专属路径，不触碰历史 archive 或无关用户改动。

# Decisions

- 用户确认当前没有外部用户，不要求 backward compatibility，可以直接删除错误或重复能力。
- framework-level API Payload 与 Endpoint Request/Response 构成重复 schema authority并会漂移，因此不保留为 adapter escape hatch。
- 普通外部 API 直接使用 Endpoint Request/Response；path/query/header/body 差异由 typed binding mapper 构造 Endpoint Request，status/header/body/envelope 差异由 response policy 表达。
- 必要的 transport-local intermediate type 仅允许作为 private/internal handwritten adapter implementation detail。
- 采用独立 `retire-design-api-payload` change 先完成 Pipeline capability retirement，不把该跨层删除塞入 #193 的 HTTP Runtime/Analyzer implementation PR。
- API Payload 没有独立 canonical capability spec，因此通过更新其实际归属的 `design-roundtrip-contract`、`generator-contract-surface` 与 `semantic-value-types` 完整目标规格来归档删除。
- 保持单一 change：Source、canonical、planner、Analyzer、facts 与 docs 必须原子收敛，拆分后任一中间状态都会暴露失真的 capability contract。

# Open questions

- [blocking] CONFIRM: 确认本 change 无兼容层地完整删除 framework-level `api_payload` 及其 Source/Pipeline/canonical/Generator/Renderer/Gradle/Analyzer/Drawing Board/AgentFacts/Public Docs surface，保留 Endpoint 与 Query page 等其他能力，并以 A1-A10 验收；完成合入后再 refresh #193。

# Verification expectations

- Source/descriptor/canonical/planner/renderer/Gradle focused tests覆盖 unsupported tag/family、registry/layout absence、其他七个 tags与 Query page 正向行为。
- Analyzer compiler/IR tests覆盖 API Payload node与 heuristic 消失、普通 wrapper 零误报、其他 tactical nodes 不回归。
- 两份 API Payload 专属 functional fixture 删除；rich round-trip 与 integrated fixtures迁移到七 tags并完成 generation/compile/Analyzer/regeneration。
- 运行 code-derived capability facts export、Agent Snapshot tests、Public Docs/Skill/Runtime/PR workflow guards与零活跃引用扫描。
- 运行完整 Gradle `check`；任何因共享 semantic-value renderer/compiler 删除造成的其他 role 回归都视为失败。
