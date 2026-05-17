# Cap4k Authoring Boundary Language Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework cap4k authoring docs and skills so they use boundary concepts instead of implementation names, split service interaction from one-time framework dependency setup, and make command/UoW write boundaries unambiguous.

**Architecture:** Authoring docs become the conceptual source of truth: Open Host Service, Published Language, external capability client, external fact entry, and internal fact publication. Skills route task execution through focused boundaries: modeling explains domain shape, service-integration explains business service interaction, implementation enforces command/UoW write rules, and generation stores one-time framework database scripts.

**Tech Stack:** Markdown docs, Codex skill directories, PowerShell validation script, Git worktree branch `skills/rebuild-cap4k-authoring`.

---

## Current State

The PR worktree is `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/skills-rebuild-cap4k-authoring`.

There is already one uncommitted change in `docs/public/authoring/tactical-model.md`. Task 1 starts by reviewing and finalizing that partial edit rather than discarding it.

Do not edit `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k` on `master`.

## File Structure

- Modify `docs/public/authoring/tactical-model.md`: define boundary vocabulary and default command/UoW semantics.
- Modify `docs/public/authoring/default-happy-path.md`: promote hard rules from implementation-name examples to boundary concepts.
- Modify `docs/public/authoring/default-happy-path.en.md`: keep English default rule table aligned with the Chinese canonical page.
- Modify `docs/public/authoring/application.md`: state that command handlers own write-use-case orchestration, including client calls when the write use case depends on external capability results.
- Modify `docs/public/authoring/adapter.md`: classify controller/RPC/gRPC/HTTP API as Open Host Service implementations, callback/listener/inbound event subscribers as external fact entry implementations, and job/scheduler as internal trigger implementations.
- Modify `docs/public/authoring/domain.md`, `docs/public/authoring/getting-started.md`, `docs/public/authoring/index.md`, `docs/public/authoring/index.en.md`, `docs/public/authoring/framework-positioning.md`, and `docs/public/authoring/framework-positioning.en.md`: remove controller-centered summaries and align audit cues.
- Modify `docs/public/authoring/examples/media-processing-callback.md`, `docs/public/authoring/examples/media-processing-polling.md`, and `docs/public/authoring/examples/content-draft-to-publish.md`: use Open Host Service and external fact entry terminology in examples.
- Modify `skills/cap4k-modeling/**`: remove generation-capability details from modeling routes and use `value object` wording.
- Create `skills/cap4k-service-integration/**`: replace the old runtime-integration skill with service interaction rules.
- Delete `skills/cap4k-runtime-integration/**`: remove the mixed runtime/dependency abstraction after the new skill is in place.
- Modify `skills/cap4k-authoring/SKILL.md`, `skills/cap4k-authoring/routing.yaml`, and `skills/cap4k-authoring/references/route-map.md`: route service interaction tasks to `cap4k-service-integration`.
- Modify `skills/cap4k-implementation/**`: enforce command-owned write orchestration and aggregate-root-only UoW.
- Modify `skills/cap4k-generation/**`: store framework table scripts under generation references and remove one-off gotchas from the main path.
- Create `skills/cap4k-generation/references/framework-database-scripts.md`: index the framework SQL assets and explain when to read them.
- Create `skills/cap4k-generation/references/sql/request.sql`, `event.sql`, `event-http-subscriber.sql`, `saga.sql`, and `locker.sql`: copy the corresponding module resource scripts.
- Modify `skills/scripts/validate-cap4k-skills.ps1`: keep validation aligned with the renamed skill and add stale-word checks.

---

### Task 1: Finalize Authoring Boundary Vocabulary

**Files:**
- Modify: `docs/public/authoring/tactical-model.md`
- Modify: `docs/public/authoring/default-happy-path.md`
- Modify: `docs/public/authoring/default-happy-path.en.md`

- [ ] **Step 1: Inspect the existing partial edit**

Run:

```powershell
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/skills-rebuild-cap4k-authoring diff -- docs/public/authoring/tactical-model.md
```

Expected: diff only in `tactical-model.md`; no unrelated files.

- [ ] **Step 2: Finalize `tactical-model.md` boundary definitions**

Use `apply_patch` to ensure the page contains this exact conceptual block once:

```md
## 边界入口与对外交互

cap4k authoring 不以 controller、RPC endpoint、callback handler、message listener 这些实现形态作为架构中心。它们首先属于边界入口。

- 开放服务入口（Open Host Service）表示外部系统或前端以同步方式消费本系统能力。HTTP controller、RPC endpoint、gRPC service 都只是实现形态。开放服务入口使用发布语言（Published Language）表达稳定请求、响应、错误与状态语义。
- 外部能力端口 / client 防腐层表示内部主动消费外部能力，例如对象存储、支付、短信、媒体处理、权限资料或兄弟服务查询。client contract 应使用内部业务语言，外部协议、SDK、bucket、objectKey、第三方状态码留在 adapter handler 内。
- 外部事实入口表示外部已经发生的事实进入内部。callback controller、message listener、inbound integration event subscriber 都只是实现形态。外部事实入口不直接修改聚合，而是翻译成内部 command、query 或明确 process step。
- 内部事实发布表示本系统把内部已经发生的跨边界事实发布给外部。outbound integration event 是常见实现形态，通常由领域事实或 application process 派生，不建议聚合根直接承担跨服务发布协议。

前端-facing HTTP API 也按开放服务入口规则审计。严格 DDD 语义中的 Open Host Service 更偏跨系统公开契约，但 cap4k 默认规则要求所有同步入口都遵守同一条边界：入口只翻译请求，写操作进入 command，读操作进入 query。
```

- [ ] **Step 3: Finalize `tactical-model.md` write boundary language**

Use `apply_patch` to ensure the page states:

```md
命令处理器可以使用 repository、factory、domain service、client 请求和 UoW。只有当写用例确实依赖外部能力返回时，才应在命令处理器中调用 client；普通写入决策不要随意穿过外部边界。入口层不编排“先调 client，再补 command”的写流程。

UoW 只保存聚合根。子实体、值对象、inline value、JSON-backed value 都通过聚合根持久化，不作为独立 UoW 保存目标。
```

- [ ] **Step 4: Update `default-happy-path.md` hard rule table**

Use `apply_patch` to replace implementation-centered rule rows with:

```md
| 状态变更收敛到命令处理路径 | `Must` | 开放服务入口、外部事实入口、内部触发入口都不直接改聚合 |
| 聚合根是唯一写入主面 | `Must` | UoW 只保存聚合根，子实体和值对象通过聚合根持久化 |
| 外部能力端口是防腐边界，不是主流程真相源 | `Must` | 外部能力调用必须先穿过 client 防腐层 |
```

- [ ] **Step 5: Update `default-happy-path.md` command section**

Use `apply_patch` to replace controller/job/subscriber wording with:

```md
默认路径要求所有状态变更都能追溯到明确的命令处理。示例项目里，无论是开放服务入口、外部事实入口还是内部触发入口，最终都要转换为 `CreateContentDraftCmd`、`SubmitContentForReviewCmd`、`StartMediaProcessingCmd`、`PublishContentCmd` 这类内部命令，再由 handler 驱动聚合行为。
```

- [ ] **Step 6: Update `default-happy-path.md` integration boundary section**

Use `apply_patch` to rename `cli 是防腐边界` to:

```md
### 外部能力端口是防腐边界
```

Then ensure the audit cues use `client` instead of `cli`:

```md
- client 是否只承担防腐和能力边界职责，而不是自己保存业务真相
```

- [ ] **Step 7: Align `default-happy-path.md` English summary**

Use `apply_patch` to update the English rule table rows to:

```md
| mutation converges into command handling | `Must` | Open Host Service entries, external fact entries, and internal triggers do not directly mutate aggregates |
| aggregate root is the only write surface | `Must` | UoW saves aggregate roots only; child entities and values persist through the aggregate root |
| client is an anti-corruption boundary rather than process truth | `Must` | external capabilities must cross a client boundary first |
```

- [ ] **Step 8: Verify Task 1 language**

Run:

```powershell
rg -n "controller / job / subscriber|cli 是防腐边界|controller surfaces|job surfaces|subscriber surfaces" docs/public/authoring/tactical-model.md docs/public/authoring/default-happy-path.md docs/public/authoring/default-happy-path.en.md
```

Expected: no matches.

---

### Task 2: Rework Layer Guides Around Boundary Concepts

**Files:**
- Modify: `docs/public/authoring/application.md`
- Modify: `docs/public/authoring/adapter.md`
- Modify: `docs/public/authoring/domain.md`
- Modify: `docs/public/authoring/getting-started.md`
- Modify: `docs/public/authoring/index.md`
- Modify: `docs/public/authoring/index.en.md`
- Modify: `docs/public/authoring/framework-positioning.md`
- Modify: `docs/public/authoring/framework-positioning.en.md`

- [ ] **Step 1: Update `application.md` responsibilities**

Use `apply_patch` to include this sentence in the responsibility section:

```md
写用例中的外部能力调用属于 command handler 的编排责任；入口层不应该先调用 client 再补一个 command。
```

- [ ] **Step 2: Update `application.md` allowed code**

Use `apply_patch` to replace the command handler bullet with:

```md
- 手写 command handler 或其他 application 完成面：加载一个聚合根、在写用例需要外部能力返回时调用 client、调用一个主行为、保存该聚合根，并在需要时发起后续协作。只要文件不在 recurring plan item 里，它就是更安全的作者面。
```

- [ ] **Step 3: Update `application.md` non-examples**

Use `apply_patch` to add this non-example:

```md
- 开放服务入口先调用 `MediaProcessingCli` 或 `ResourceStorageClient`，再调用 command 补写状态，把写用例拆散在入口层。
```

- [ ] **Step 4: Update `adapter.md` introduction**

Use `apply_patch` to add this paragraph near the top:

```md
adapter 文档不再把 controller 当作架构中心。controller、RPC endpoint、gRPC service 属于开放服务入口实现；callback controller、message listener、inbound integration event subscriber 属于外部事实入口实现；job、scheduler 属于内部触发入口实现。
```

- [ ] **Step 5: Update `adapter.md` responsibility bullets**

Use `apply_patch` to replace the first adapter responsibility bullet with:

```md
- 提供开放服务入口实现，把外部同步请求转换成内部命令或查询。HTTP controller、RPC endpoint、gRPC service 都只是实现形态。
```

Add this bullet immediately after it:

```md
- 提供外部事实入口实现，把 callback、message listener、inbound integration event 转换成内部命令、查询或明确 process step。
```

- [ ] **Step 6: Update `adapter.md` non-examples**

Use `apply_patch` to replace controller-specific anti-patterns with:

```md
- 开放服务入口直接加载聚合并改状态，跳过内部命令和 handler。
- 外部事实入口收到 callback 后直接把 `MediaProcessingTask` 标成成功或失败，而不是转换成内部命令。
- 外部能力端口除了做协议调用，还顺便决定“媒体完成后立即发布内容”，把流程编排塞进边界实现。
```

- [ ] **Step 7: Update `domain.md` boundary references**

Use `apply_patch` to replace mentions of Web/callback/message/polling as direct concepts with:

```md
它不关心这个动作来自开放服务入口、外部事实入口，还是内部触发入口；它只关心当 `Content` 或 `MediaProcessingTask` 收到一个内部动作时，状态迁移是否合法。
```

- [ ] **Step 8: Update overview docs**

Use `apply_patch` to update summary bullets:

```md
- 写入行为是否收敛在命令处理路径，而不是散落在开放服务入口、外部事实入口或内部触发入口中
```

Apply this wording to `docs/public/authoring/index.md` and the English equivalent to `docs/public/authoring/index.en.md`:

```md
- whether write behavior stays in command handling instead of Open Host Service entries, external fact entries, or internal trigger glue
```

- [ ] **Step 9: Update framework positioning**

Use `apply_patch` to replace `cli` wording with:

```md
- client 是外部能力防腐层边界，不是主流程真相源
```

For the English page:

```md
- client is the anti-corruption boundary for external capabilities, not the truth source of the process
```

- [ ] **Step 10: Verify Task 2 language**

Run:

```powershell
rg -n "controller.*业务真相|controller.*流程|controller.*写入主面|cli 是|`cli` 是|RPC、message listener" docs/public/authoring/application.md docs/public/authoring/adapter.md docs/public/authoring/domain.md docs/public/authoring/index.md docs/public/authoring/index.en.md docs/public/authoring/framework-positioning.md docs/public/authoring/framework-positioning.en.md
```

Expected: no stale controller-centered or cli-centered summary remains.

---

### Task 3: Align Examples With New Boundary Vocabulary

**Files:**
- Modify: `docs/public/authoring/examples/media-processing-callback.md`
- Modify: `docs/public/authoring/examples/media-processing-polling.md`
- Modify: `docs/public/authoring/examples/content-draft-to-publish.md`
- Modify: `docs/public/authoring/examples/advanced-concepts-overview.md`

- [ ] **Step 1: Update callback example role list**

Use `apply_patch` in `media-processing-callback.md` to replace role bullets with:

```md
- `MediaProcessingCli` 负责外部能力端口，不负责决定内部状态机。
- 外部事实入口负责把外部处理结果接进来；callback controller、event bridge、`IntegrationEventSubscriber` 都只是实现形态。
- 进入内部后，真正推进状态的仍然是 application 命令链和 `MediaProcessingTask` 聚合行为。
```

- [ ] **Step 2: Update callback recommended flow**

Use `apply_patch` to keep the flow shape but relabel the entry:

```md
external callback / integration event
  -> external fact entry implementation
  -> IntegrationEventSubscriber or equivalent application entry point
  -> CompleteMediaProcessingCmd or FailMediaProcessingCmd
```

- [ ] **Step 3: Update callback non-examples**

Use `apply_patch` to replace the direct callback controller anti-pattern with:

```md
- 外部事实入口收到结果后直接调用仓储，把 `MediaProcessingTask.status` 改成成功或失败。
```

- [ ] **Step 4: Update polling example**

Use `apply_patch` in `media-processing-polling.md` to define polling as internal trigger:

```md
polling 是内部触发入口的一种实现。它可以定时观察外部任务状态，但只负责把观察结果翻译成内部命令，不取得聚合写入特权。
```

- [ ] **Step 5: Update content draft example**

Use `apply_patch` in `content-draft-to-publish.md` to replace controller non-example with:

```md
- 开放服务入口直接调用仓储修改 `Content.status`，绕过 `Content` 聚合行为。
```

- [ ] **Step 6: Update advanced concepts overview**

Use `apply_patch` to replace "controller 编排" wording with:

```md
开放服务入口编排
```

and replace callback/job-specific wording when it is explaining architecture rather than example implementation:

```md
外部事实入口编排
内部触发入口调度
```

- [ ] **Step 7: Verify Task 3 examples**

Run:

```powershell
rg -n "callback controller 收到结果后直接|controller 直接调用仓储|controller 编排|job 写成主流程真相源" docs/public/authoring/examples
```

Expected: no stale wording in conceptual statements. Concrete implementation examples may still mention `callback controller` only when explicitly listing implementation forms.

---

### Task 4: Replace Runtime Integration Skill With Service Integration Skill

**Files:**
- Create: `skills/cap4k-service-integration/SKILL.md`
- Create: `skills/cap4k-service-integration/rules/service-boundaries.md`
- Create: `skills/cap4k-service-integration/rules/integration-events.md`
- Create: `skills/cap4k-service-integration/workflows/design-open-host-service.md`
- Create: `skills/cap4k-service-integration/workflows/handle-external-fact.md`
- Create: `skills/cap4k-service-integration/workflows/consume-external-capability.md`
- Create: `skills/cap4k-service-integration/references/gotchas.md`
- Delete: `skills/cap4k-runtime-integration/SKILL.md`
- Delete: `skills/cap4k-runtime-integration/rules/request-and-event-runtime.md`
- Delete: `skills/cap4k-runtime-integration/rules/integration-event-adapters.md`
- Delete: `skills/cap4k-runtime-integration/workflows/configure-http-integration-events.md`
- Delete: `skills/cap4k-runtime-integration/workflows/handle-external-callback.md`
- Delete: `skills/cap4k-runtime-integration/references/framework-tables.md`
- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify: `skills/cap4k-authoring/routing.yaml`
- Modify: `skills/cap4k-authoring/references/route-map.md`

- [ ] **Step 1: Add `cap4k-service-integration/SKILL.md`**

Use `apply_patch` to add:

```md
---
name: cap4k-service-integration
description: >
  Use for cap4k business service interaction design and implementation:
  Open Host Service entries, Published Language, external capability clients,
  external fact entries, inbound/outbound integration events, callbacks,
  message listeners, and command/query routing across service boundaries.
---

# Cap4k Service Integration

Use this when a cap4k business project needs to consume external capabilities, expose synchronous capabilities, receive external facts, or publish internal facts across boundaries.

## Always Read

1. `rules/service-boundaries.md`
2. `rules/integration-events.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Internal code consumes an external capability | `references/gotchas.md` | `workflows/consume-external-capability.md` |
| External systems or frontend consume internal capability synchronously | `references/gotchas.md` | `workflows/design-open-host-service.md` |
| External callback, message, or inbound event enters the project | `references/gotchas.md` | `workflows/handle-external-fact.md` |

## Stop Conditions

Stop when an entry implementation is about to write repository or aggregate state directly, when an external protocol leaks into domain/application contracts, or when a write flow calls client first and command second.
```

- [ ] **Step 2: Add `rules/service-boundaries.md`**

Use `apply_patch` to add:

```md
# Service Boundary Rules

- Open Host Service means external systems or frontend synchronously consume internal capability; HTTP controller, RPC endpoint, and gRPC service are implementation forms.
- Published Language is the stable request, response, status, and error language exposed by an Open Host Service.
- External capability client means internal code consumes an outside capability through an anti-corruption port.
- External fact entry means an outside fact enters the system through callback, message listener, or inbound integration event.
- Internal fact publication means an internal fact is published outward, commonly as an outbound integration event.
- Read-oriented Open Host Service routes to query.
- Write-oriented Open Host Service routes to command.
- External fact entries that advance state route to command.
- Entry implementations never write repositories or aggregates directly.
- Client handlers translate external capability protocols and must not decide aggregate state machines.
```

- [ ] **Step 3: Add `rules/integration-events.md`**

Use `apply_patch` to add:

```md
# Integration Event Rules

- Inbound integration events are external facts entering the project.
- Outbound integration events are internal facts published outside the project.
- HTTP, RabbitMQ, and RocketMQ are adapter transports, not modeling categories.
- Inbound events translate into command, query, or an explicit application entry point before touching internal state.
- Outbound events should be derived from domain facts or application process results.
- Do not model external callbacks as domain events.
- Do not let aggregate roots own cross-service protocol details.
- Event contract stability depends on event name, payload schema, subscriber identity, and serialization behavior.
```

- [ ] **Step 4: Add workflows**

Use `apply_patch` to add these three workflow files.

`workflows/consume-external-capability.md`:

```md
# Consume External Capability

1. Name the internal capability in business language, not provider language.
2. Define the client request/response contract in application terms.
3. Keep provider SDK types, object keys, buckets, status codes, and transport DTOs in adapter handlers.
4. If the capability is part of a write use case, call the client inside the command handler.
5. After the client returns, update aggregate state through aggregate behavior and save the aggregate root through UoW.
6. Define idempotency, retry, and cleanup at the command use-case boundary.
```

`workflows/design-open-host-service.md`:

```md
# Design Open Host Service

1. Identify who consumes the synchronous capability.
2. Define the Published Language: request fields, response fields, status meanings, and error meanings.
3. Route reads to query.
4. Route writes to command.
5. Keep controller, RPC endpoint, or gRPC service code as input/output translation only.
6. Do not access repository or aggregate state directly from the entry implementation.
```

`workflows/handle-external-fact.md`:

```md
# Handle External Fact

1. Classify the input as an external fact, not a domain event.
2. Translate the external payload into internal language at the adapter/application boundary.
3. Route state changes to command.
4. Route observations to query or an explicit application entry point.
5. Keep callback payloads, message protocol headers, and third-party status codes out of domain events.
6. Ensure callback, message listener, and inbound integration event paths converge to the same internal command semantics.
```

- [ ] **Step 5: Add service integration gotchas**

Use `apply_patch` to add `references/gotchas.md`:

```md
# Service Integration Gotchas

- Controller is not the architecture concept; Open Host Service is the boundary concept.
- Callback controller is an external fact entry implementation, not a normal write surface.
- RPC is a transport name. Use Open Host Service when describing the business boundary.
- OSS, S3, payment, SMS, and media processing are external capabilities behind client ports.
- Application contracts should say resource storage or media storage, not OSS bucket or objectKey.
- A write use case should not be split into entry calls client first and command later.
- External facts may enter through HTTP callback, MQ listener, or integration event subscriber, but all state advancement still converges to command.
```

- [ ] **Step 6: Delete old runtime integration files**

Use `apply_patch` with delete hunks for every file under `skills/cap4k-runtime-integration`.

- [ ] **Step 7: Update authoring router**

Use `apply_patch` in `skills/cap4k-authoring/routing.yaml`:

```yaml
  - task: service-integration
    skill: cap4k-service-integration
    triggers:
      - "open host service"
      - "external capability client"
      - "external callback"
      - "integration event"
      - "服务交互"
      - "开放服务入口"
      - "外部事实入口"
      - "集成事件"
```

- [ ] **Step 8: Update `skills/cap4k-authoring/SKILL.md` and route map**

Use `apply_patch` to replace `cap4k-runtime-integration` with `cap4k-service-integration` and route wording:

```md
| Design or implement service-boundary interaction | `cap4k-service-integration` |
```

- [ ] **Step 9: Verify Task 4 routing**

Run:

```powershell
rg -n "cap4k-runtime-integration|runtime-integration|request runtime|framework persistence tables" skills
```

Expected: no matches.

---

### Task 5: Clean Modeling and Implementation Skills

**Files:**
- Modify: `skills/cap4k-modeling/SKILL.md`
- Modify: `skills/cap4k-modeling/rules/tactical-modeling.md`
- Modify: `skills/cap4k-modeling/workflows/derive-ddd-model.md`
- Modify: `skills/cap4k-modeling/workflows/define-cross-boundary-events.md`
- Modify: `skills/cap4k-modeling/references/gotchas.md`
- Modify: `skills/cap4k-implementation/rules/layering.md`
- Modify: `skills/cap4k-implementation/rules/mediator-and-uow.md`
- Modify: `skills/cap4k-implementation/workflows/implement-command-slice.md`
- Modify: `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`
- Modify: `skills/cap4k-implementation/references/gotchas.md`

- [ ] **Step 1: Update modeling description**

Use `apply_patch` in `skills/cap4k-modeling/SKILL.md`:

```md
description: >
  Use for cap4k business modeling before code: business intent, DDD boundaries,
  aggregates, entities, value objects, domain events, service interaction
  boundaries, domain services, specifications, and technical方案 before
  generation or implementation.
```

Remove the hard boundary about unsupported `value_object` and `domain_service` generation from this file.

- [ ] **Step 2: Rewrite modeling tactical rules**

Use `apply_patch` in `rules/tactical-modeling.md` so it contains:

```md
# Tactical Modeling Rules

- Aggregates own write invariants and state transitions.
- Entities belong inside aggregate consistency boundaries.
- Value objects express business value semantics before persistence carrier choices.
- Domain services should only be modeled when a domain decision crosses aggregate boundaries or does not naturally belong to one aggregate.
- Specifications model validation policies only when the project intentionally demonstrates or enforces that concept.
- Domain events express meaningful domain facts from domain behavior; synchronous or asynchronous handling is a delivery/runtime choice.
- External interaction must be classified as external capability client, Open Host Service, external fact entry, or internal fact publication before generation.
- Do not model external callbacks as domain events.
```

- [ ] **Step 3: Update `derive-ddd-model.md`**

Use `apply_patch` to replace the numbered list with:

```md
# Derive DDD Model

1. Start from agreed business vocabulary.
2. Propose aggregate roots and explain each consistency boundary.
3. Classify child entities and value objects.
4. Identify invariants and the command that enforces each invariant.
5. Identify domain events as meaningful domain facts; record whether handling must be synchronous or can be asynchronous.
6. Identify domain services only for domain decisions that cross aggregate boundaries.
7. Classify service interaction boundaries before choosing transport or generation details.
8. Produce DDL/design JSON recommendations only after the tactical model is coherent.
```

- [ ] **Step 4: Update cross-boundary workflow**

Use `apply_patch` in `define-cross-boundary-events.md` to classify all four directions:

```md
# Define Cross-Boundary Interaction

1. Decide whether the system consumes external capability, exposes synchronous capability, receives external fact, or publishes internal fact.
2. For external capability, define a client contract in internal business language.
3. For Open Host Service, define the Published Language and route reads to query and writes to command.
4. For external fact entry, translate callback/message/event payload into internal command, query, or application entry point.
5. For internal fact publication, derive outbound integration events from domain facts or application process results.
6. Keep transport details out of domain events and aggregate behavior.
```

- [ ] **Step 5: Update implementation layering rule**

Use `apply_patch` in `skills/cap4k-implementation/rules/layering.md`:

```md
# Layering Rules

- Domain owns aggregates, entities, value objects, invariants, domain events, domain services, factories, and specifications.
- Application owns commands, command handlers, validators, subscribers, internal triggers, and process orchestration.
- Adapter owns Open Host Service implementations, external fact entry implementations, persistence adapters, query handlers, client handlers, and external protocol mapping.
- Query handlers and client handlers are adapter-side physical handlers by default.
- Open Host Service entries, external fact entries, and internal triggers must not become direct write-persistence surfaces.
```

- [ ] **Step 6: Update UoW rule**

Use `apply_patch` in `skills/cap4k-implementation/rules/mediator-and-uow.md`:

```md
# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate-root loading in write flows.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.requests` for external capability clients.
- Use `Mediator.cmd` and `Mediator.qry` for command/query boundaries.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- UoW saves aggregate roots only.
- Persist child entities, value objects, inline values, and JSON-backed values through the aggregate root.
- Do not inject handlers directly to bypass the request supervisor path.
- Do not call client first from an entry implementation and then command second to patch internal state.
```

- [ ] **Step 7: Update command implementation workflow**

Use `apply_patch` in `implement-command-slice.md` to ensure the steps include:

```md
4. If the write use case depends on an external capability result, call the client inside the command handler.
5. Call aggregate behavior to record the internal state change.
6. Save the aggregate root through `Mediator.uow.save()`.
```

- [ ] **Step 8: Update subscriber/job implementation workflow**

Use `apply_patch` in `implement-subscriber-or-job.md`:

```md
# Implement Subscriber Or Internal Trigger

1. Classify the entry as domain-event subscriber, external fact entry, or internal trigger.
2. Use subscribers or jobs as routing points, not hidden aggregate persistence layers.
3. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
4. Route state changes to command.
5. Route reads to query.
6. Keep external protocol payloads out of aggregate behavior and domain events.
```

- [ ] **Step 9: Verify Task 5 wording**

Run:

```powershell
rg -n "value concepts|first-class `value_object`|domain_service.*unsupported|client/cli|Controllers and jobs|Do not call `Mediator.uow.persist\\(valueObject\\)`" skills/cap4k-modeling skills/cap4k-implementation
```

Expected: no matches.

---

### Task 6: Add Generation Framework Database Script Reference

**Files:**
- Modify: `skills/cap4k-generation/SKILL.md`
- Modify: `skills/cap4k-generation/references/gotchas.md`
- Create: `skills/cap4k-generation/references/framework-database-scripts.md`
- Create: `skills/cap4k-generation/references/sql/request.sql`
- Create: `skills/cap4k-generation/references/sql/event.sql`
- Create: `skills/cap4k-generation/references/sql/event-http-subscriber.sql`
- Create: `skills/cap4k-generation/references/sql/saga.sql`
- Create: `skills/cap4k-generation/references/sql/locker.sql`

- [ ] **Step 1: Update generation SKILL route**

Use `apply_patch` in `skills/cap4k-generation/SKILL.md` to add:

```md
| Bootstrap framework database tables | `references/framework-database-scripts.md` | `workflows/bootstrap-project.md` |
```

- [ ] **Step 2: Add framework database script index**

Use `apply_patch` to create `references/framework-database-scripts.md`:

```md
# Framework Database Scripts

These scripts are one-time setup references for cap4k framework modules. Read this file during bootstrap or dependency setup, not during normal business modeling.

| Capability | Tables | Script |
|---|---|---|
| request records | `__request`, `__archived_request` | `references/sql/request.sql` |
| domain/integration event records | `__event`, `__archived_event` | `references/sql/event.sql` |
| HTTP integration event subscriber registry | `__event_http_subscriber` | `references/sql/event-http-subscriber.sql` |
| saga records | `__saga`, `__saga_process`, `__archived_saga`, `__archived_saga_process` | `references/sql/saga.sql` |
| distributed locker records | `__locker` | `references/sql/locker.sql` |

Use only the subset required by the selected cap4k dependencies. Translate dialect details before applying to non-MySQL databases.
```

- [ ] **Step 3: Add request SQL**

Use `apply_patch` to create `references/sql/request.sql` by copying the exact contents from:

```text
ddd-application-request-jpa/src/main/resources/request.sql
```

- [ ] **Step 4: Add event SQL**

Use `apply_patch` to create `references/sql/event.sql` by copying the exact contents from:

```text
ddd-domain-event-jpa/src/main/resources/event.sql
```

- [ ] **Step 5: Add HTTP subscriber SQL**

Use `apply_patch` to create `references/sql/event-http-subscriber.sql` by copying the exact contents from:

```text
ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql
```

- [ ] **Step 6: Add saga SQL**

Use `apply_patch` to create `references/sql/saga.sql` by copying the exact contents from:

```text
ddd-distributed-saga-jpa/src/main/resources/saga.sql
```

- [ ] **Step 7: Add locker SQL**

Use `apply_patch` to create `references/sql/locker.sql` by copying the exact contents from:

```text
ddd-distributed-locker-jdbc/src/main/resources/locker.sql
```

- [ ] **Step 8: Remove noisy generation gotchas**

Use `apply_patch` in `skills/cap4k-generation/references/gotchas.md` to remove lines about:

```text
src-generated/main/kotlin
designIntegrationEventSubscriber depends on designIntegrationEvent
stale enum translation core DSL
```

Keep gotchas that affect normal business-project generation.

- [ ] **Step 9: Verify Task 6 SQL files**

Run:

```powershell
rg -n "__request|__event|__event_http_subscriber|__saga|__locker" skills/cap4k-generation/references/framework-database-scripts.md skills/cap4k-generation/references/sql
```

Expected: each table family appears in the index and the matching SQL file.

---

### Task 7: Validation and Review Prep

**Files:**
- Modify: `skills/scripts/validate-cap4k-skills.ps1`
- Read: all changed files

- [ ] **Step 1: Update validation forbidden patterns**

Use `apply_patch` to add stale patterns:

```powershell
('cap4k-runtime-integration'),
('value concepts'),
('src-generated/main/kotlin'),
('enum translation core DSL'),
('controller / job / subscriber')
```

Do not ban `@Lazy` globally because public generator input docs still document supported DB annotation syntax. Limit `@Lazy` cleanup to skill files.

- [ ] **Step 2: Run skill validation**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
cap4k skill validation passed for 7 skills.
```

- [ ] **Step 3: Scan stale skill text**

Run:

```powershell
rg -n "cap4k-runtime-integration|runtime-integration|value concepts|src-generated/main/kotlin|enum translation core DSL|client/cli|controller / job / subscriber" skills
```

Expected: no matches.

- [ ] **Step 4: Scan stale authoring concepts**

Run:

```powershell
rg -n "controller / job / subscriber|cli 是防腐边界|RPC、message listener|controller surfaces|job surfaces|subscriber surfaces" docs/public/authoring
```

Expected: no matches.

- [ ] **Step 5: Check markdown links**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected: no broken local markdown links.

- [ ] **Step 6: Inspect git diff**

Run:

```powershell
git diff --stat
git diff --check
```

Expected: changed files match this plan; `git diff --check` reports no whitespace errors.

- [ ] **Step 7: Prepare review summary**

Write a concise summary with these sections:

```md
Changed:
- Boundary language now centers Open Host Service, Published Language, external capability client, external fact entry, and internal fact publication.
- Runtime integration skill replaced by service integration skill.
- Framework database scripts moved under generation references with actual SQL assets.
- Modeling skill no longer carries generation unsupported details.
- Implementation skill now enforces command-owned client calls and aggregate-root-only UoW.

Verified:
- skill validation command and result
- stale scan command and result
- diff check command and result

Not done:
- No master edits.
- No commit unless explicitly approved.
```

---

## Execution Notes

- Execute tasks in order.
- Commit only after the user approves the reviewed diff.
- Use `apply_patch` for manual edits.
- Keep changes inside `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/skills-rebuild-cap4k-authoring`.
- Do not edit the main `master` worktree.
- Do not preserve `cap4k-runtime-integration` for compatibility; replace it.
