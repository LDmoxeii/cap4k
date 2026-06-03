# Cap4k PR 101 Review Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address PR #101 public documentation review comments without changing the PR branch scope, runtime code, generator code, tests, Gradle files, or internal analysis maps.

**Architecture:** This is a documentation-only follow-up on the existing `spec/documentation-system-redesign` branch. The core correction is to separate Repository read/access from Unit of Work persistence/commit, tighten Specification to cap4k's UoW pre-save validation semantics, clarify inbound Integration Event framework/runtime versus user-authored application subscriber boundaries, and remove wording that implies business users implement framework capabilities.

**Tech Stack:** Markdown public docs, Git/GitHub PR #101, static text verification with `rg` and `git diff --check`. Do not run build, tests, Gradle, app, HTTP, install, or runtime commands.

---

## Scope Decisions

- Keep PR #101 as the current documentation-system branch. Do not split, rebase, cherry-pick, or create a replacement PR for this follow-up.
- Modify only public documentation files listed in this plan and this plan file.
- Do not modify Kotlin runtime, generator implementation, Gradle build files, tests, skills, issue-governance files, or `docs/superpowers/analysis/**`.
- Existing local draft edits may be present from the interrupted review session. Treat them as scratch only. Verify each hunk against this plan before keeping it.
- Do not resolve GitHub review threads unless the user explicitly asks.
- Current implementation worker scope stops after Task 1-5 edits and static text checks. Do not commit or push in this worker session.

## Evidence Anchors

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`: Repository exposes read/access operations such as `find`, `findOne`, `findFirst`, `findPage`, `count`, and `exists`. It does not expose `save`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`: Unit of Work exposes `persist`, `persistIfNotExist`, `remove`, and `save`.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`: JPA UoW collects persist/remove intent and performs actual `EntityManager.persist`, `merge`, `remove`, `flush`, and interceptor phases inside `save`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/SpecificationUnitOfWorkInterceptor.kt`: Specification checks run in `beforeTransaction` and `preInTransaction` for persist aggregates.
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`: default `designIntegrationEvent` and `designIntegrationEventSubscriber` package roots are `application.subscribers.integration`.
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventSubscriberArtifactPlannerTest.kt`: inbound integration subscriber default output path is under the application module.
- `ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt`: framework runtime adapter handles scan/register/consume/parse/dispatch for HTTP integration event consumption.

## File Map

- Modify: `docs/public/concepts/modeling-building-blocks/aggregate.md`
  - Clarify Aggregate concept/type versus runtime Root instance.
  - Add the category tree/sibling order decision boundary.
  - Replace Repository persistence wording with Repository read/access plus Unit of Work persistence/commit.
  - Update IMAGE_PROMPT wording to avoid implying Repository saves aggregates.
- Modify: `docs/public/concepts/modeling-building-blocks/domain-service.md`
  - Add category tree boundary guidance from the Domain Service angle.
  - Replace generic Specification wording with cap4k UoW pre-save validation semantics.
  - Remove any phrasing that says Domain Service saves through Repository.
- Modify: `docs/public/concepts/modeling-building-blocks/integration-event.md`
  - Replace the vague "adapter/application layer converts" wording with two-stage responsibility: framework transport adapter/runtime consumes and dispatches typed event; application inbound subscriber interprets it and delegates command/application behavior.
- Modify: `docs/public/architecture/adapter-layer.md`
  - Remove wording that says business-authored adapter logic owns inbound listener conversion or Integration Event consumption entries.
  - State that cap4k integration-event transport adapter/runtime consumes HTTP/message protocol, parses/registers/dispatches typed integration events, and application inbound subscriber interprets typed facts.
- Modify: `docs/public/concepts/modeling-building-blocks/factory.md`
  - Remove the sentence that says Repository saves the created Aggregate.
- Modify: `docs/public/concepts/execution-and-ownership/repository.md`
  - Rewrite Repository as Aggregate read/access boundary.
  - Explicitly state cap4k Repository does not expose save; Unit of Work owns persistence intent and commit.
- Modify: `docs/public/concepts/execution-and-ownership/command.md`
  - Replace "save through Repository and Unit of Work" with "read through Repository, persist/commit through Unit of Work".
- Modify: `docs/public/concepts/execution-and-ownership/command-query-separation.md`
  - Align command write path with Repository read and UoW commit.
- Modify: `docs/public/concepts/execution-and-ownership/subscriber.md`
  - Remove "Repository save boundary" wording.
  - Use Aggregate behavior plus Unit of Work commit boundary.
- Modify: `docs/public/concepts/execution-and-ownership/unit-of-work.md`
  - Remove "Repository save" wording from review checklist.
- Modify: `docs/public/architecture/application-layer.md`
  - Replace "Application layer is responsible for UoW/Mediator" with "Application layer organizes use cases and uses UoW/Mediator framework capabilities".
- Modify: `docs/public/architecture/clean-architecture.md`
  - Separate Repository read/access, Unit of Work persistence/commit, and Mediator routing/facade wording.
  - Replace inbound integration listener consumption wording with the two-stage framework runtime adapter plus application subscriber boundary.
- Modify: `docs/public/architecture/dependency-rules.md`
  - Replace inbound integration listener conversion wording with the two-stage framework runtime adapter plus application subscriber boundary.
- Modify: `docs/public/architecture/index.md`
  - Replace Application Layer responsibility wording so it organizes use cases and uses framework UoW/Mediator.
  - Align adapter overview with the integration-event runtime adapter plus application subscriber boundary.
- Modify: `docs/public/architecture/testing-by-layer.md`
  - Stop presenting inbound integration listener as a business-authored adapter test surface.
  - Split test guidance between framework/runtime smoke or adapter module wiring for transport consume/parse/register/dispatch and application-layer subscriber tests for typed inbound Integration Event behavior.
- Modify: `docs/public/authoring/technical-design.md`
  - Align layer summary, write path, and persistence expectations with Repository read and UoW persistence/commit.
  - Remove inbound integration listener and callback consumption adapter from user-authored adapter building block/module placement wording.
- Modify: `docs/public/examples/reference-content-studio.md`
  - Replace ambiguous module-map wording that says the adapter module contains an integration event consume path.
  - State that adapter module contains HTTP/query/client/persistence mapping and framework transport wiring where applicable, while runtime transport handles consume/parse/register/dispatch and application subscriber handles typed fact behavior.
- Modify: `docs/superpowers/plans/2026-06-04-cap4k-pr-101-review-follow-up.md`
  - Keep this implementation plan as the compression-resistant handoff.

---

### Task 1: Normalize The Existing Draft Against Review Decisions

**Files:**
- Read: every file listed in File Map
- Modify: only files listed in File Map

- [ ] **Step 1: Inspect current local draft**

Run:

```powershell
git status --short -uall
git diff -- docs/public/concepts/modeling-building-blocks/aggregate.md docs/public/concepts/modeling-building-blocks/domain-service.md docs/public/concepts/modeling-building-blocks/integration-event.md docs/public/concepts/modeling-building-blocks/factory.md docs/public/concepts/execution-and-ownership/repository.md docs/public/concepts/execution-and-ownership/command.md docs/public/concepts/execution-and-ownership/command-query-separation.md docs/public/concepts/execution-and-ownership/subscriber.md docs/public/concepts/execution-and-ownership/unit-of-work.md docs/public/architecture/application-layer.md docs/public/authoring/technical-design.md
```

Expected:

```text
Only public docs files listed in this plan are modified.
The diff may include scratch edits from the interrupted review session.
```

- [ ] **Step 2: Reject any scratch hunk outside the decisions**

Apply this decision table while reading the diff:

| Topic | Keep | Remove or rewrite |
| --- | --- | --- |
| Repository | "Repository reads/loads/accesses Aggregate" | "Repository saves/persists Aggregate" |
| Unit of Work | "UoW records persistence intent and commits" | "Repository and UoW jointly save" |
| Specification | "UoW pre-save constraint check through interceptor" | "Domain Service adjacent generic composable rule library" |
| Integration Event | "framework runtime adapter consumes/dispatches, application subscriber interprets" | "adapter/application layer converts" |
| Application layer | "organizes use cases and uses framework capabilities" | "is responsible for implementing UoW/Mediator" |
| Aggregate | "concept/type versus root instance distinction" | "single class/object ambiguity left unexplained" |

Expected:

```text
Every kept hunk maps to one row in the table.
No hunk changes PR scope or non-public documentation.
```

### Task 2: Correct Repository And Unit Of Work Semantics

**Files:**
- Modify: `docs/public/concepts/modeling-building-blocks/aggregate.md`
- Modify: `docs/public/concepts/modeling-building-blocks/factory.md`
- Modify: `docs/public/concepts/execution-and-ownership/repository.md`
- Modify: `docs/public/concepts/execution-and-ownership/command.md`
- Modify: `docs/public/concepts/execution-and-ownership/command-query-separation.md`
- Modify: `docs/public/concepts/execution-and-ownership/subscriber.md`
- Modify: `docs/public/concepts/execution-and-ownership/unit-of-work.md`
- Modify: `docs/public/authoring/technical-design.md`

- [ ] **Step 1: Rewrite Repository page around read/access only**

In `docs/public/concepts/execution-and-ownership/repository.md`, make the opening statements express this exact contract:

```markdown
Repository 是 Aggregate 的读取和访问边界。它让 application layer 以聚合为单位加载业务对象，而不是直接暴露底层持久化细节。cap4k 的 Repository 接口不提供保存能力；持久化意图、删除意图和提交由 Unit of Work 承担。
```

Expected:

```text
The page no longer says Repository saves, persists, or owns save timing.
```

- [ ] **Step 2: Rewrite command write path pages**

Apply these target meanings:

```markdown
Command handler 通过 Repository 读取所需聚合，调用 Aggregate 行为方法，并把持久化意图和提交交给 Unit of Work。
```

```markdown
Command handler 可以通过 Repository 加载 Aggregate，调用 domain layer 暴露的行为，再通过 Unit of Work 记录持久化意图并完成提交。
```

Expected:

```text
`command.md` and `command-query-separation.md` both use Repository for loading and UoW for commit.
```

- [ ] **Step 3: Fix related concept pages**

Apply these target meanings:

```markdown
Factory 不保存 Aggregate；创建完成后的 Aggregate 由 application flow 交给 Unit of Work 持久化。
```

```markdown
Subscriber 不绕过 Aggregate 行为或 Unit of Work 提交边界直接修改业务对象。
```

```markdown
审查 Unit of Work 时，检查待持久化对象是否通过 Unit of Work 收集和提交。
```

Expected:

```text
`factory.md`, `subscriber.md`, and `unit-of-work.md` do not contain "Repository save" or equivalent Chinese wording.
```

- [ ] **Step 4: Fix aggregate and authoring wording**

Apply these target meanings:

```markdown
Repository 提供聚合级读取入口，Unit of Work 管理持久化意图和提交时机。
```

```markdown
persistence design 要服务 Aggregate ownership。Repository 负责按聚合读取，Unit of Work 负责持久化意图和提交。
```

Expected:

```text
`aggregate.md` and `technical-design.md` consistently describe Repository read plus UoW commit.
```

### Task 3: Clarify Aggregate Instance Boundary And Domain Service Decisions

**Files:**
- Modify: `docs/public/concepts/modeling-building-blocks/aggregate.md`
- Modify: `docs/public/concepts/modeling-building-blocks/domain-service.md`

- [ ] **Step 1: Add Aggregate type versus instance explanation**

Add a paragraph with this content meaning to `aggregate.md` after the opening paragraph:

```markdown
文档中说 Aggregate 时，可能指建模概念、聚合类型，也可能指运行时的某个 Aggregate Root instance。真正保护事务一致性的，是一次命令处理中被加载出来的 Root instance 及它拥有的内部对象集合。
```

Expected:

```text
Readers can distinguish Aggregate as concept/type from Aggregate Root instance as transaction boundary.
```

- [ ] **Step 2: Add category tree boundary guidance**

Add this decision meaning to `aggregate.md` or `domain-service.md`:

```markdown
如果同一父节点下的 sibling order 必须在一次事务里保持一致，优先考虑让 CategoryTree、CategoryParent 或类似 Root 拥有这组子节点顺序；插入节点并调整相邻兄弟位置就是这个聚合的行为。如果每个分类节点都是独立 Aggregate，则同一次调整多个兄弟节点已经是跨聚合协作，应由 application orchestration 或 Domain Service decision 明确组织。
```

Expected:

```text
The category example answers the PR review question without pretending every tree operation belongs to one Category node aggregate.
```

- [ ] **Step 3: Correct Specification semantics in Domain Service**

Replace the old generic Specification paragraph with this meaning:

```markdown
在 cap4k 中，Specification 更接近聚合持久化前的约束检查，而不是 Domain Service 可以随意组合调用的通用规则库。运行时通过 SpecificationUnitOfWorkInterceptor 在 Unit of Work 的 beforeTransaction 和 preInTransaction 阶段检查待持久化实体，并在规格不通过时拒绝提交。
```

Expected:

```text
`domain-service.md` no longer teaches Specification as a generic Domain Service-adjacent composable pattern.
```

### Task 4: Clarify Inbound Integration Event Runtime And Subscriber Boundaries

**Files:**
- Modify: `docs/public/concepts/modeling-building-blocks/integration-event.md`
- Modify: `docs/public/architecture/adapter-layer.md`
- Modify: `docs/public/architecture/dependency-rules.md`
- Modify: `docs/public/architecture/testing-by-layer.md`
- Modify: `docs/public/authoring/technical-design.md`
- Modify: `docs/public/examples/reference-content-studio.md`
- Review: `docs/public/concepts/execution-and-ownership/subscriber.md`

- [ ] **Step 1: Replace vague inbound conversion wording**

In `integration-event.md`, replace the sentence that says inbound events are converted by "adapter/application layer" with this meaning:

```markdown
Inbound Integration Event 进入系统后，要区分两段责任：cap4k 的 integration-event transport adapter/runtime 消费 HTTP/message 等外部协议并分发 typed integration event；业务项目中的 application-layer inbound integration subscriber 再接收这个外部事实，做幂等、语义转换，并在需要改变状态时委托 Command 或 application behavior。
```

Expected:

```text
The page makes clear that transport consume/parse/register is framework-provided runtime behavior, not default user-authored adapter code.
```

- [ ] **Step 2: Check related pages for contradiction**

Run:

```powershell
rg -n "adapter/application 层|用户.*实现.*adapter|手写.*consume|inbound listener|inbound integration listener|callback consumption adapter|transport consumption/listener|integration event consume path|Integration Event consumption entry|inbound event consumption references|inbound integration listener 把 callback 或 message 转换成内部事实入口" docs/public/concepts docs/public/architecture docs/public/authoring docs/public/examples
```

Expected:

```text
No line says business users must handwrite framework transport consume/register behavior.
Lines that mention inbound listener describe application subscriber semantics or framework runtime adapter semantics clearly.
```

### Task 5: Correct Application Layer Capability Wording

**Files:**
- Modify: `docs/public/architecture/application-layer.md`
- Modify: `docs/public/authoring/technical-design.md`
- Modify: `docs/public/architecture/clean-architecture.md`
- Modify: `docs/public/architecture/index.md`
- Modify: `docs/public/architecture/dependency-rules.md`

- [ ] **Step 1: Replace "responsible for UoW/Mediator" wording**

In `application-layer.md`, use this meaning:

```markdown
Application layer 组织 Command、Query、Subscriber、Saga、Scheduled Reaction 和 external capability requests。它在写入提交和入口路由中使用 Unit of Work 与 Mediator 这些框架能力；业务项目通常是在 handler 中调用它们，而不是重新实现它们。
```

Expected:

```text
The page no longer implies business project authors implement UoW or Mediator.
```

- [ ] **Step 2: Align technical design layer summary**

In `technical-design.md`, use this meaning:

```markdown
application layer：Command、Query、Subscriber、Saga、Scheduled Reaction 和 external capability requests；写入提交和入口路由使用 Unit of Work、Mediator 等框架能力。
```

Expected:

```text
The authoring guide matches the architecture page.
```

### Task 6: Static Verification Only For This Worker

**Files:**
- Verify: all modified files from File Map

- [ ] **Step 1: Run forbidden phrase scan**

Run:

```powershell
rg -n "integration event consume path|inbound integration listener|callback consumption adapter|用户.*实现.*adapter|手写.*consume|transport consumption/listener|Application Layer.*负责.*Unit of Work|Application layer.*负责.*Unit Of Work|负责.*Unit of Work|负责.*Mediator|Unit of Work、Repository|Unit of Work、Repository 和 Mediator 帮助 application layer 管理提交边界|inbound integration listener 消费外部事实|Integration Event consumption entry|inbound event consumption references|inbound integration listener 把 callback 或 message 转换成内部事实入口|Repository save|repository save|Repository 是否以聚合为单位保存|Repository 和 Unit of Work 负责按聚合保存|Repository 拥有聚合级持久化入口|Repository 只负责保存|借助 Repository 保存|通过 Repository 和 Unit of Work 完成保存|通过 Repository 持久化|保存交给 Repository|adapter/application 层转换|Specification 是 Domain Service 附近|不应保存 Repository|Application layer 负责 \\[Command\\].*Unit Of Work" docs/public
```

Expected:

```text
No matches. Exit code 1 is acceptable for rg when there are no matches.
```

- [ ] **Step 2: Run targeted source-of-truth reminder scan**

Run:

```powershell
rg -n "fun save|fun persist|interface Repository|interface UnitOfWork|SpecificationUnitOfWorkInterceptor|designIntegrationEventSubscriber|HttpIntegrationEventSubscriberAdapter" ddd-core ddd-domain-repo-jpa cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-design ddd-integration-event-http
```

Expected:

```text
Output includes Repository without save, UnitOfWork with persist/remove/save, SpecificationUnitOfWorkInterceptor, designIntegrationEventSubscriber layout, and HttpIntegrationEventSubscriberAdapter.
```

- [ ] **Step 3: Run diff whitespace check**

Run:

```powershell
git diff --check -- docs/public/concepts/modeling-building-blocks/aggregate.md docs/public/concepts/modeling-building-blocks/domain-service.md docs/public/concepts/modeling-building-blocks/integration-event.md docs/public/concepts/modeling-building-blocks/factory.md docs/public/concepts/execution-and-ownership/repository.md docs/public/concepts/execution-and-ownership/command.md docs/public/concepts/execution-and-ownership/command-query-separation.md docs/public/concepts/execution-and-ownership/subscriber.md docs/public/concepts/execution-and-ownership/unit-of-work.md docs/public/architecture/application-layer.md docs/public/architecture/adapter-layer.md docs/public/architecture/clean-architecture.md docs/public/architecture/dependency-rules.md docs/public/architecture/index.md docs/public/architecture/testing-by-layer.md docs/public/authoring/technical-design.md docs/public/examples/reference-content-studio.md docs/superpowers/plans/2026-06-04-cap4k-pr-101-review-follow-up.md
```

Expected:

```text
Exit code 0. LF/CRLF warnings are acceptable in this Windows worktree. No whitespace errors are acceptable.
```

- [ ] **Step 4: Confirm changed file scope**

Run:

```powershell
git status --short -uall
```

Expected:

```text
Only files listed in File Map are modified or added.
```

- [ ] **Step 5: Stop before commit or push**

For the current worker session, do not run `git add`, `git commit`, or `git push`. Handoff should report changed files and static check results only.

Expected:

```text
No commit or push is created by this worker.
```

---

## Self-Review

- Spec coverage: all 9 PR review comments map to Task 2, Task 3, Task 4, or Task 5. The PR scope decision maps to Scope Decisions.
- Placeholder scan: this plan contains none of the banned placeholder patterns listed by `superpowers:writing-plans`.
- Type and term consistency: Repository means read/access only; Unit of Work means persistence intent and commit; framework integration-event runtime means transport consume/parse/register/dispatch; application inbound subscriber means typed event interpretation and delegation.
