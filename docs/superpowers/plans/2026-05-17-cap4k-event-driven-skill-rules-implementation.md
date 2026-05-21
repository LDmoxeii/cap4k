# Cap4k Event-Driven Skill Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the source cap4k skills so they consistently enforce design-driven generation, read/write repository boundaries, zero-trust commands, and event-driven orchestration.

**Architecture:** Keep the existing skill decomposition. Add sharper rules to the smallest relevant files under `skills/`, then wire those rules into workflows and gotchas so normal task routes activate them.

**Tech Stack:** Markdown skills in the `cap4k` repository, PowerShell, ripgrep, git.

---

## File Map

- Modify: `skills/cap4k-modeling/rules/tactical-modeling.md`
- Modify: `skills/cap4k-modeling/workflows/define-cross-boundary-events.md`
- Modify: `skills/cap4k-implementation/rules/mediator-and-uow.md`
- Modify: `skills/cap4k-implementation/workflows/implement-command-slice.md`
- Modify: `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`
- Modify: `skills/cap4k-implementation/references/gotchas.md`
- Modify: `skills/cap4k-service-integration/rules/service-boundaries.md`
- Modify: `skills/cap4k-service-integration/rules/integration-events.md`
- Modify: `skills/cap4k-service-integration/references/gotchas.md`
- Modify: `skills/cap4k-generation/rules/output-ownership.md`
- Modify: `skills/cap4k-generation/workflows/generate-from-design.md`
- Modify: `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md`
- Modify: `skills/cap4k-verification/references/gotchas.md`

## Implementation Notes

- Work in the `cap4k` repository, not in installed skill copies outside the repository.
- Do not introduce a new skill or a new code layer.
- Keep `SKILL.md` routing files concise. Prefer rule/workflow/reference files for durable content.
- Preserve generated subscriber shell guidance: do not tell users to delete generated empty subscribers just because they are empty.
- Use the exact snippets below as source material, but adapt headings only when needed to match the surrounding file structure.

---

### Task 1: Confirm Branch, Source Tree, And Current Wording

**Files:**
- Read: cap4k skill Markdown files under `skills/cap4k-*/`

- [ ] **Step 1: Confirm git branch is not `master`**

Run:

```powershell
git status --short --branch
```

Expected: output starts with a non-master branch name such as `## docs/event-driven-skill-rules-spec`.

- [ ] **Step 2: Confirm source skill directories exist**

Run:

```powershell
Get-ChildItem -Force -LiteralPath 'skills' | Select-Object -ExpandProperty Name
```

Expected: output includes:

```text
cap4k-authoring
cap4k-generated-output-review
cap4k-generation
cap4k-implementation
cap4k-modeling
cap4k-service-integration
cap4k-verification
```

- [ ] **Step 3: Search for stale repository-boundary wording**

Run:

```powershell
rg -n "only.*command|command only|只能.*cmd|只能.*command|repository.*只能|仓储.*只能|repo.*command" skills -g "skills/cap4k-*/**/*.md"
```

Expected: any hits are reviewed and either updated in later tasks or kept only if they clearly refer to write boundaries.

- [ ] **Step 4: Search for orchestration wording that may need tightening**

Run:

```powershell
rg -n "cmd.*cmd|command.*command|commands.send|Mediator\\.commands|orchestrat|编排|subscriber|listener|@EventListener|Saga|saga" skills -g "skills/cap4k-*/**/*.md"
```

Expected: locate the files listed in this plan and any adjacent wording that contradicts this plan.

---

### Task 2: Update Modeling Rules For Domain Events And Child Identity Boundaries

**Files:**
- Modify: `skills/cap4k-modeling/rules/tactical-modeling.md`
- Modify: `skills/cap4k-modeling/workflows/define-cross-boundary-events.md`

- [ ] **Step 1: Add domain event payload boundary to tactical modeling**

Open `skills/cap4k-modeling/rules/tactical-modeling.md` and add this section near the domain event guidance:

```markdown
### Domain Event Payload Boundary

- Domain events describe business facts, not technical continuation steps. Do not create "command completed" events merely to continue a process.
- Generated domain events may carry the aggregate snapshot. Do not fight that generator contract in business projects.
- Add event fields only when the aggregate snapshot cannot express the fact clearly: added child items, removed child items, deltas, before/after values, or computed fact results.
- Do not expose non-aggregate-root technical or persistence IDs as standalone public identities in domain events, outbound integration events, or open host write contracts.
- Read models may expose aggregate-scoped child keys for UI display, diffing, and selection.
- Commands that target child elements must include the aggregate root identity plus a child key, then validate child membership inside the command.
```

- [ ] **Step 2: Add event-driven default shape to tactical modeling**

In the same file, add this section near aggregate behavior or domain event rules:

```markdown
### Event-Driven Continuation

- Prefer fact-driven continuation: command mutates an aggregate, aggregate behavior records a meaningful domain fact, and independent listeners react to that fact.
- Each listener routes writes into zero-trust commands. The command must re-load the write target and validate its own preconditions.
- Repeated delivery should converge through idempotent command behavior and explicit no-op results.
- Use Saga only for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.
```

- [ ] **Step 3: Tighten cross-boundary event workflow**

Open `skills/cap4k-modeling/workflows/define-cross-boundary-events.md` and add this checklist before finalizing event payloads:

```markdown
## Payload Boundary Check

- [ ] The event name states a business fact, not a technical step.
- [ ] Extra fields are limited to fact data not clearly expressed by the aggregate snapshot.
- [ ] Added or removed child information is represented as aggregate-scoped child keys or fact deltas, not standalone child technical IDs.
- [ ] External consumers receive published language, not internal persistence identifiers.
- [ ] Any write command triggered from this event will re-validate aggregate identity, child membership, status, and invariants.
```

- [ ] **Step 4: Verify modeling wording**

Run:

```powershell
rg -n "Domain Event Payload Boundary|Event-Driven Continuation|Payload Boundary Check|command completed|child technical" skills/cap4k-modeling -g "*.md"
```

Expected: the new sections appear, and any "command completed" wording is negative guidance only.

---

### Task 3: Update Implementation Rules For Repository Boundaries, UoW, And Zero-Trust Commands

**Files:**
- Modify: `skills/cap4k-implementation/rules/mediator-and-uow.md`
- Modify: `skills/cap4k-implementation/workflows/implement-command-slice.md`

- [ ] **Step 1: Replace command-only repository language with read/write boundary language**

Open `skills/cap4k-implementation/rules/mediator-and-uow.md` and make the repository section say:

```markdown
### Repository Access Boundary

- Repository access is governed by read/write boundaries, not by a blanket "commands only" rule.
- Command handlers are aggregate write boundaries. They may load and write aggregate roots through repositories.
- Query handlers are read boundaries. They may use repositories, JPA, projections, or read-model infrastructure in read-only mode.
- Domain event listeners, external fact entries, open host service entries, controllers, jobs, client handlers, and Saga coordinators must not directly mutate aggregates or call write repositories.
- Flow-routing reads outside a command should normally go through a query instead of ad hoc repository access.
- If a non-command component appears to need write repository access, route the write through a command or explicitly document why the component is itself a write boundary.
```

- [ ] **Step 2: Tighten UoW persistence wording**

In the same file, update the UoW rule to include:

```markdown
### UoW Persistence Boundary

- `Mediator.uow.save(...)` belongs inside an explicit write boundary.
- Persist aggregate roots only. Child entities, value objects, inline values, and JSON-backed values are persisted through their aggregate root.
- Do not save child entities independently to bypass aggregate invariants.
- Do not call `Mediator.uow.save(...)` from listeners, jobs, controllers, open host service entries, external fact entries, or client handlers unless that component has been deliberately modeled as the write boundary.
```

- [ ] **Step 3: Add zero-trust command checklist**

Open `skills/cap4k-implementation/workflows/implement-command-slice.md` and add this section before implementation steps that mutate aggregates:

```markdown
## Zero-Trust Command Boundary

Every command must treat all callers as untrusted routing hints:

- [ ] Load the aggregate root or write target inside the command.
- [ ] Validate target existence, ownership, aggregate status, child membership, and business invariants before mutating.
- [ ] Treat query results, listener filters, job checks, Saga state, another command, and external entry validation as insufficient for writes.
- [ ] Return an explicit no-op result for expected non-ready or already-applied states.
- [ ] Throw a domain/application error for missing targets, invalid identities, wrong ownership, invalid child keys, and invariant violations.
- [ ] Persist only aggregate roots through `Mediator.uow.save(...)`.
```

- [ ] **Step 4: Add command-to-command exception rule**

In `skills/cap4k-implementation/workflows/implement-command-slice.md`, add this section near Mediator command usage:

```markdown
## Command-To-Command Calls

- Default stance: commands are write boundaries, not process coordinators.
- Allowed exception: a command may call another command only as local reuse inside the same synchronous write use case.
- Suspicious shape: a command reads state, branches, and sends multiple follow-up commands. Prefer domain event fan-out, external fact entry, job, or Saga for that flow.
- If the follow-up is driven by a business fact, record a domain event and let independent listeners react.
- If command-to-command remains, document why the called command is local synchronous reuse and why event-driven continuation would be worse.
```

- [ ] **Step 5: Verify implementation wording**

Run:

```powershell
rg -n "Repository Access Boundary|UoW Persistence Boundary|Zero-Trust Command Boundary|Command-To-Command Calls|commands are write boundaries" skills/cap4k-implementation -g "*.md"
```

Expected: all four rule blocks are present.

---

### Task 4: Update Subscriber And Job Workflow For Event Fan-Out And Listener Organization

**Files:**
- Modify: `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`
- Modify: `skills/cap4k-implementation/references/gotchas.md`

- [ ] **Step 1: Add listener organization rules**

Open `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md` and add this section near subscriber implementation guidance:

```markdown
## Listener Organization

- Cap4k does not guarantee ordering between multiple listeners or listener methods for the same event.
- Represent independent reactions as independent `@EventListener` methods.
- Strongly discourage a public `on(event)` method that manually dispatches to multiple business reaction methods.
- Give each listener method a business-semantic name and one reaction.
- Use private helpers only for shared technical concerns, not for hiding a business dispatch table.
- Listener-side checks are routing filters only. The called command must still validate every write precondition.
- Do not directly write repositories or mutate aggregates from listeners or jobs. Route writes through commands.
```

- [ ] **Step 2: Add approved listener example**

In the same file, add this example after the listener organization rules:

```markdown
Approved shape: independent listener methods.
```

```kotlin
@Service
class CustomerCoinGivenDomainEventSubscriber {

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun transferCoin(event: CustomerCoinGivenDomainEvent) {
        val action = event.entity
        Mediator.commands.send(
            TransferCoinCmd.Request(
                senderUserId = action.customerId,
                receiverUserId = action.videoOwnerId,
                amount = action.actionCount
            )
        )
    }

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun applyVideoCoinCountDelta(event: CustomerCoinGivenDomainEvent) {
        val action = event.entity
        Mediator.commands.send(
            ApplyVideoCoinCountDeltaCmd.Request(
                videoId = action.videoId,
                delta = action.actionCount
            )
        )
    }
}
```

- [ ] **Step 3: Add discouraged listener example**

In the same file, add this immediately after the approved example:

```markdown
Discouraged shape: public `on(event)` manual dispatch.
```

```kotlin
@Service
class CustomerCoinGivenDomainEventSubscriber {

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun on(event: CustomerCoinGivenDomainEvent) {
        transferCoin(event)
        applyVideoCoinCountDelta(event)
    }

    private fun transferCoin(event: CustomerCoinGivenDomainEvent) {
        // Hidden business reaction.
    }

    private fun applyVideoCoinCountDelta(event: CustomerCoinGivenDomainEvent) {
        // Hidden business reaction.
    }
}
```

Add the sentence below the discouraged example:

```markdown
This shape hides multiple business reactions behind one listener method and can mislead reviewers into assuming ordering or transactional coupling.
```

- [ ] **Step 4: Record listener gotcha**

Open `skills/cap4k-implementation/references/gotchas.md` and add:

```markdown
## Hidden Listener Dispatch

Strongly discouraged: one public `on(event)` listener method manually dispatches to several private business reaction methods. Use multiple independent `@EventListener` methods with business-semantic names. Cap4k does not guarantee ordering between multiple listeners, so commands triggered by listeners must be idempotent and zero-trust.
```

- [ ] **Step 5: Verify subscriber wording**

Run:

```powershell
rg -n "Approved shape|Discouraged shape|Listener Organization|Strongly discourage|Hidden Listener Dispatch|does not guarantee ordering|business dispatch table" skills/cap4k-implementation -g "*.md"
```

Expected: the workflow and gotcha both include the stronger listener guidance.

---

### Task 5: Update Service Integration Rules For Open Host Service, External Fact Entry, And External Capability Clients

**Files:**
- Modify: `skills/cap4k-service-integration/rules/service-boundaries.md`
- Modify: `skills/cap4k-service-integration/rules/integration-events.md`
- Modify: `skills/cap4k-service-integration/references/gotchas.md`

- [ ] **Step 1: Add two-direction external interaction model**

Open `skills/cap4k-service-integration/rules/service-boundaries.md` and add this section near boundary terminology:

```markdown
### Two Directions Of External Interaction

- Internal consumes external: model the dependency as an external capability client behind internal domain language. Examples: resource storage, media storage, payment gateway, moderation service.
- External consumes internal: model the entry as open host service, external fact entry, published language, inbound message, callback, or integration event.
- Do not collapse these directions into one transport term. Avoid using "RPC" as the domain-facing name for the concept.
```

- [ ] **Step 2: Clarify open host service entry**

In the same file, add:

```markdown
### Open Host Service Entry

- Open host service entries expose internal use cases to external consumers through published language.
- They are protocol translation and routing boundaries, not aggregate write boundaries by default.
- Write operations from open host service entries must route into commands.
- Read operations from open host service entries should route into queries or read-model APIs.
- Do not pass transport payloads directly into aggregate behavior.
```

- [ ] **Step 3: Clarify external fact entry**

In the same file, add:

```markdown
### External Fact Entry

- External fact entries represent facts observed from outside the bounded context: callbacks, inbound messages, polling results, webhooks, or inbound integration events.
- Translate the external payload into internal published language before routing.
- Every write path from an external fact entry must enter a command.
- If one external fact appears to require multiple internal reactions, first consider command -> domain event fan-out.
- Multiple routes from one external fact entry are allowed for now, but treat that as a boundary-review signal.
```

- [ ] **Step 4: Clarify client invocation timing**

In the same file, add:

```markdown
### External Capability Client Invocation

- A command may call an external capability client when the external side effect is part of the same write use case and the command will update aggregate state based on the result.
- Prefer domain language names such as resource storage or media storage over provider names.
- Keep provider-specific terms out of aggregate behavior unless they are part of the business published language.
- Technical storage locator terms such as `objectKey` may appear inside infrastructure-facing contracts or media processing paths, but should not become aggregate identity or public business language by default.
```

- [ ] **Step 5: Align integration event wording**

Open `skills/cap4k-service-integration/rules/integration-events.md` and add:

```markdown
### Integration Event Payload Boundary

- Outbound integration events publish stable business language, not internal persistence structure.
- Do not expose non-aggregate-root technical IDs as standalone resource identities.
- If consumers need child-level information, prefer aggregate-scoped child keys, deltas, or read-model links.
- External callbacks and inbound messages are external facts. Route writes from them into commands.
```

- [ ] **Step 6: Align service-integration gotcha wording**

Open `skills/cap4k-service-integration/references/gotchas.md` and update the storage naming gotcha to:

```markdown
- Application/business contracts should say resource storage or media storage, not provider terms such as OSS bucket. Technical locators such as `objectKey` may appear only in infrastructure-facing contracts or media processing paths and should not become aggregate identity or public business language by default.
```

- [ ] **Step 7: Verify service integration wording**

Run:

```powershell
rg -n "Two Directions Of External Interaction|Open Host Service Entry|External Fact Entry|External Capability Client Invocation|Integration Event Payload Boundary|resource storage|media storage|objectKey|provider terms" skills/cap4k-service-integration -g "*.md"
```

Expected: service integration rules distinguish dependency clients from external entry points.

---

### Task 6: Update Generation Rules For Design-Driven Surface Ownership

**Files:**
- Modify: `skills/cap4k-generation/rules/output-ownership.md`
- Modify: `skills/cap4k-generation/workflows/generate-from-design.md`

- [ ] **Step 1: Add generated-capable surface rule**

Open `skills/cap4k-generation/rules/output-ownership.md` and add:

```markdown
### Generated-Capable Surfaces

- Before adding event, subscriber, command, query, client, validator, or API payload surfaces, decide whether `design.json` can generate that surface.
- If the generator supports the surface, update `design.json` first and regenerate.
- Do not quietly handwrite generator-supported surfaces.
- If a surface cannot be generated, state the reason in review notes or final notes.
- Do not delete generated subscriber shells simply because they are empty. Implement business logic inside the generated boundary when the behavior is ready.
```

- [ ] **Step 2: Add pre-generation decision gate**

Open `skills/cap4k-generation/workflows/generate-from-design.md` and add this checklist before running generation:

```markdown
## Surface Ownership Gate

- [ ] New command surfaces are represented in `design.json` when generation supports them.
- [ ] New query surfaces are represented in `design.json` when generation supports them.
- [ ] New domain event or subscriber surfaces are represented in `design.json` when generation supports them.
- [ ] New client, validator, and API payload surfaces are represented in `design.json` when generation supports them.
- [ ] Any handwritten surface has a stated reason why generation is not available for that surface.
```

- [ ] **Step 3: Verify generation wording**

Run:

```powershell
rg -n "Generated-Capable Surfaces|Surface Ownership Gate|Do not quietly handwrite|generated subscriber shells" skills/cap4k-generation -g "*.md"
```

Expected: both generation files contain the design-driven surface ownership rule.

---

### Task 7: Update Verification Workflow And Gotchas

**Files:**
- Modify: `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md`
- Modify: `skills/cap4k-verification/references/gotchas.md`

- [ ] **Step 1: Add flow review checks**

Open `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md` and add:

```markdown
## Flow-Orchestration Review

- [ ] Commands are not acting as process coordinators by reading state, branching, and sending multiple follow-up commands.
- [ ] Command-to-command calls, if present, are local reuse inside one synchronous write use case.
- [ ] Fact-driven continuation uses domain events, external fact entries, jobs, or Saga instead of technical "command completed" events.
- [ ] Multiple listeners for the same event do not assume ordering.
- [ ] Listener-triggered commands are idempotent and zero-trust.
- [ ] External fact entries route writes into commands and do not mutate aggregates directly.
```

- [ ] **Step 2: Add generated ownership checks**

In the same file, add:

```markdown
## Generated Ownership Review

- [ ] Newly added command, query, event, subscriber, client, validator, and API payload surfaces were checked against `design.json` generation support.
- [ ] Generator-supported surfaces were added through `design.json` and regeneration.
- [ ] Any handwritten surface includes the reason it could not be generated.
- [ ] Generated subscriber shells were not deleted merely because they were empty.
```

- [ ] **Step 3: Add verification gotchas**

Open `skills/cap4k-verification/references/gotchas.md` and add:

```markdown
## Coordinator Command Smell

A command that reads state, branches, and sends multiple follow-up commands may be hiding process coordination. Review whether the flow should instead be driven by a domain event, external fact entry, job, or Saga. Command-to-command calls should remain local synchronous write-use-case reuse.

## Split Flow Output

If analysis flow output splits one business process across command and subscriber diagrams, do not automatically treat it as a code bug. Record whether the split is an expected projection of event-driven flow. If the output hides causality, reference cap4k issue #55 for investigation.

## Multi-Listener Failure Diagnostics

When multiple listeners react to one event, cap4k does not guarantee listener order. Review idempotency, zero-trust command validation, and error messages. Reference cap4k issue #56 when diagnostics make failures hard to identify.
```

- [ ] **Step 4: Verify verification wording**

Run:

```powershell
rg -n "Flow-Orchestration Review|Generated Ownership Review|Coordinator Command Smell|Split Flow Output|Multi-Listener Failure Diagnostics|issue #55|issue #56" skills/cap4k-verification -g "*.md"
```

Expected: flow review and gotcha coverage are both present.

---

### Task 8: Run Cross-Skill Consistency Review

**Files:**
- Read: cap4k skill Markdown files under `skills/cap4k-*/`
- Read: `docs/superpowers/specs/2026-05-17-cap4k-event-driven-skill-rules-design.md`

- [ ] **Step 1: Search for forbidden repository simplification**

Run:

```powershell
rg -n "only.*command|command only|只能.*cmd|只能.*command|仓储.*只能|repository.*只能" skills -g "skills/cap4k-*/**/*.md"
```

Expected: no hit states a blanket repository command-only rule. Any remaining hit must explicitly mean write repository or write boundary.

- [ ] **Step 2: Search for old external naming**

Run:

```powershell
rg -n "RPC|runtime integration|cap4k-runtime-integration|OSS|object storage" skills -g "skills/cap4k-*/**/*.md"
```

Expected: no new domain-facing concept is named `RPC`, `runtime integration`, or provider-specific storage. Any `objectKey` mention is scoped to technical locator language.

- [ ] **Step 3: Search for weak listener wording**

Run:

```powershell
rg -n "on\\(event\\)|dispatch|分发|多个监听|listener order|guarantee ordering|Strongly discourage" skills/cap4k-implementation skills/cap4k-verification -g "*.md"
```

Expected: public manual dispatch is described as strongly discouraged, and listener ordering is not assumed.

- [ ] **Step 4: Search for generation ownership coverage**

Run:

```powershell
rg -n "design\\.json|generated-capable|generator-supported|handwrite|手写|regenerate|generated subscriber" skills/cap4k-generation skills/cap4k-generated-output-review skills/cap4k-verification -g "*.md"
```

Expected: generation ownership is explicit before implementing new surfaces.

- [ ] **Step 5: Search for placeholders without literal placeholder tokens**

Run:

```powershell
$placeholderPattern = ('TB' + 'D|TO' + 'DO|FIX' + 'ME|fill' + ' in|implement' + ' later|Similar' + ' to Task')
rg -n $placeholderPattern skills docs/superpowers/specs/2026-05-17-cap4k-event-driven-skill-rules-design.md -g "skills/cap4k-*/**/*.md" -g "*.md"
```

Expected: no new placeholder text introduced by this change.

---

### Task 9: Verify Diff, Whitespace, And Git State

**Files:**
- Verify: all modified files

- [ ] **Step 1: Check for references to installed skill copies**

Run:

```powershell
$installedCopyPattern = ('\\.' + 'agents|only-workspace\\\\\\.' + 'agents|Documents\\\\code\\\\only-workspace\\\\\\.' + 'agents')
rg -n $installedCopyPattern docs/superpowers/specs/2026-05-17-cap4k-event-driven-skill-rules-design.md docs/superpowers/plans/2026-05-17-cap4k-event-driven-skill-rules-implementation.md skills -g "*.md"
```

Expected: no hits.

- [ ] **Step 2: Check Markdown diff**

Run:

```powershell
git diff -- docs/superpowers/specs/2026-05-17-cap4k-event-driven-skill-rules-design.md docs/superpowers/plans/2026-05-17-cap4k-event-driven-skill-rules-implementation.md skills
```

Expected: diff contains only the intended spec, plan, and skill documentation changes.

- [ ] **Step 3: Check whitespace**

Run:

```powershell
git diff --check
```

Expected:

```text
```

No output.

- [ ] **Step 4: Check final status**

Run:

```powershell
git status --short --branch
```

Expected: branch is not `master`; modified files are limited to this plan's file map plus the spec and plan documents.

- [ ] **Step 5: Commit after approval and successful verification**

Run after the implementation is approved:

```powershell
git add docs/superpowers/specs/2026-05-17-cap4k-event-driven-skill-rules-design.md docs/superpowers/plans/2026-05-17-cap4k-event-driven-skill-rules-implementation.md skills
git commit -m "docs: refine cap4k event-driven skill rules"
```

Expected: commit succeeds on the feature branch. Do not push directly to `master`.
