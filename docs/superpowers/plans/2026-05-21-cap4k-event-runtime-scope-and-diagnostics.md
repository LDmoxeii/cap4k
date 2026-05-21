# Cap4k Event Runtime Scope And Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cap4k event runtime attach-only for business integration events, scope-aware for domain and integration event attachments, reliable for integration events derived from persisted domain event listeners, and diagnosable for multi-listener and listener-request failures.

**Architecture:** Introduce a shared ThreadLocal event runtime scope stack used by domain event, integration event, request, and listener dispatch paths. Remove hidden Auto* routing and public payload publish APIs. Domain dispatch owns a child scope: successful dispatch releases attached integration events before marking the domain event delivered; failed dispatch discards the child scope and leaves persisted domain events retryable.

**Tech Stack:** Kotlin, Spring Framework event listener extension points, Spring Boot autoconfiguration, Gradle, JUnit 5, MockK, cap4k `ddd-core`, `cap4k-ddd-starter`, authoring docs, source skills.

---

## Working Branch

Use the isolated worktree:

```powershell
C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\event-runtime-scope-diagnostics
```

Use branch:

```text
feature/event-runtime-scope-diagnostics
```

Do not implement or commit from `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k` because that checkout is `master`.

## Source Spec

Use the approved spec as the implementation contract:

- `docs/superpowers/specs/2026-05-21-cap4k-event-runtime-scope-and-diagnostics-design.md`

## File Structure

Create:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeContext.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeScope.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeScopeType.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventAttachment.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventDispatchException.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/RequestDispatchException.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventListenerFactory.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kApplicationListenerMethodAdapter.kt`
- tests under existing `ddd-core/src/test/kotlin/.../impl/` packages for runtime scope, derived integration release, discard, diagnostics, and removed APIs.

Modify:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventManager.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/IntegrationEventUnitOfWorkInterceptor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/DomainEventManager.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DomainEventUnitOfWorkInterceptor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventPublisher.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventSubscriberManager.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultPersistListenerManager.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/IntegrationEventAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/web/ClearDomainContextInterceptor.kt`
- existing tests affected by removed publish and Auto* behavior.
- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`
- `docs/public/authoring/tactical-model.md`
- `docs/public/authoring/domain.md`
- `docs/public/authoring/application.md`
- source skill files under `skills/`.

Delete:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/annotation/AutoAttach.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/annotation/AutoRequest.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/annotation/AutoRelease.kt`
- Auto* test fixtures and assertions that only validate removed behavior.

Do not modify:

- checked out `master` worktree.
- installed `.agents/skills/**` copies unless a separate deployment task is explicitly requested.
- transport adapter behavior beyond compile fixes from API changes.

## Implementation Order

The order matters. First lock expected behavior with tests where practical, then introduce the scope model, then wire success/failure semantics, then delete public APIs and docs drift.

## Task 1: Preserve Branch Isolation And Baseline

**Files:**

- Read only unless baseline reveals an immediate setup issue.

- [ ] **Step 1: Confirm active worktree and branch**

Run:

```powershell
git status --short --branch
git worktree list
```

Expected:

```text
## feature/event-runtime-scope-diagnostics
?? docs/superpowers/specs/2026-05-21-cap4k-event-runtime-scope-and-diagnostics-design.md
```

The `master` checkout should not contain pending changes.

- [ ] **Step 2: Confirm Gradle baseline can load**

Run:

```powershell
.\gradlew.bat -q projects
```

Expected: command exits with code 0 and lists all cap4k modules.

- [ ] **Step 3: Commit the approved spec before runtime work**

Run:

```powershell
git add docs/superpowers/specs/2026-05-21-cap4k-event-runtime-scope-and-diagnostics-design.md
git commit -m "docs: specify event runtime scope diagnostics"
```

Rationale: keep the accepted design recoverable before larger runtime edits.

## Task 2: Add Runtime Scope Tests First

**Files:**

- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeContextTest.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisorTest.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisorTest.kt`

- [ ] **Step 1: Add scope stack tests**

Cover:

- nested scopes isolate attachments;
- popping inner scope restores outer scope;
- discarding inner scope does not discard outer attachments;
- resetting clears every scope;
- equal data-class payloads attached twice stay as two attachment entries.

Use tests like:

```kotlin
@Test
fun `nested scopes keep outer integration attachments isolated`() {
    val outer = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
    EventRuntimeContext.current().attachIntegration(EventAttachment.eager(TestIntegrationEvent("outer")))

    val inner = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
    EventRuntimeContext.current().attachIntegration(EventAttachment.eager(TestIntegrationEvent("inner")))

    EventRuntimeContext.discard(inner)
    EventRuntimeContext.pop(inner)

    assertThat(outer.integrationAttachments).hasSize(1)
    assertThat(outer.integrationAttachments.single().resolve()).isEqualTo(TestIntegrationEvent("outer"))
}
```

- [ ] **Step 2: Add supplier attachment tests**

Cover both domain and integration supervisors:

- supplier object itself is never validated as event payload;
- supplier is evaluated during release;
- produced payload annotation is validated at release;
- supplier is not evaluated if scope is discarded before release.

- [ ] **Step 3: Add non-deduplication tests**

Add two equal data-class integration events and release them.

Expected: two `EventRecord` rows are saved or two publish markers are emitted depending on test seam. This prevents regressions to `MutableSet<Any>` storage.

- [ ] **Step 4: Run focused tests and observe failures**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*EventRuntimeContextTest" --tests "*DefaultIntegrationEventSupervisorTest" --tests "*DefaultDomainEventSupervisorTest"
```

Expected before implementation: new tests fail to compile or fail behaviorally. Do not weaken the tests to match old behavior.

## Task 3: Introduce Event Runtime Scope Model

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeContext.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeScope.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventRuntimeScopeType.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventAttachment.kt`

- [ ] **Step 1: Add scope type enum**

Use explicit scope names:

```kotlin
enum class EventRuntimeScopeType {
    REQUEST,
    DOMAIN_DISPATCH,
    AMBIENT
}
```

- [ ] **Step 2: Add attachment model**

Use lazy/eager attachment entries. Do not key schedules by payload.

```kotlin
class EventAttachment<out EVENT : Any> private constructor(
    private val payload: EVENT?,
    private val supplier: (() -> EVENT)?,
    val schedule: LocalDateTime,
) {
    fun resolve(): EVENT = payload ?: supplier!!.invoke()

    fun matches(candidate: Any): Boolean =
        payload === candidate || payload == candidate

    companion object {
        fun <EVENT : Any> eager(payload: EVENT, schedule: LocalDateTime = LocalDateTime.now()) =
            EventAttachment(payload, null, schedule)

        fun <EVENT : Any> lazy(schedule: LocalDateTime = LocalDateTime.now(), supplier: () -> EVENT) =
            EventAttachment(null, supplier, schedule)
    }
}
```

Implementation can refine visibility and null-safety, but it must preserve lazy validation and attachment order.

- [ ] **Step 3: Add runtime scope model**

Keep domain attachments entity-bound and integration attachments scope-bound:

```kotlin
class EventRuntimeScope(
    val type: EventRuntimeScopeType,
    val diagnostic: EventDiagnosticContext? = null,
) {
    val domainAttachments: MutableMap<Any, MutableList<EventAttachment<Any>>> = linkedMapOf()
    val integrationAttachments: MutableList<EventAttachment<Any>> = mutableListOf()
}
```

Use identity-aware entity keys if current entity equality can collapse distinct persisted instances. If using `MutableMap<Any, ...>`, add a test that reflects the current expected entity identity behavior.

- [ ] **Step 4: Add ThreadLocal stack operations**

Required operations:

- `push(type, diagnostic)`
- `pop(scope)`
- `currentOrCreateAmbient()`
- `currentOrNull()`
- `discard(scope)`
- `reset()`
- diagnostic read helpers.

Do not expose this as a public business API. Keep it in `impl`.

- [ ] **Step 5: Move reset responsibility into shared context**

`DefaultDomainEventSupervisor.reset()` and `DefaultIntegrationEventSupervisor.reset()` should delegate to `EventRuntimeContext.reset()` so `ClearDomainContextInterceptor` has one consistent cleanup path.

- [ ] **Step 6: Run scope tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*EventRuntimeContextTest"
```

Expected: scope model tests pass.

## Task 4: Refactor Integration Event Supervisor To Scope Attach Only

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventManager.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/IntegrationEventUnitOfWorkInterceptor.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/web/ClearDomainContextInterceptor.kt`
- Modify: tests in `DefaultIntegrationEventSupervisorTest.kt`

- [ ] **Step 1: Remove public payload publish API**

Delete these public business-facing methods:

```kotlin
fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime = LocalDateTime.now())
fun <EVENT : Any> publish(eventPayload: EVENT, delay: Duration)
```

Keep `EventPublisher.publish(EventRecord)` unchanged because that is persisted-record delivery.

- [ ] **Step 2: Store attach entries in current scope**

`attach(eventPayload, schedule)` should:

- validate `@IntegrationEvent` on eager payload;
- add `EventAttachment.eager(...)` to `EventRuntimeContext.currentOrCreateAmbient().integrationAttachments`;
- preserve duplicate equal payloads.

`attach(schedule) { ... }` should:

- add a lazy attachment without validating the lambda class;
- validate the produced payload only when release resolves the attachment.

- [ ] **Step 3: Detach only matching current-scope entries**

`detach(payload)` should remove matching eager payload entries from the current scope only.

Do not search and mutate outer scopes, because inner scopes must not unexpectedly alter outer work.

- [ ] **Step 4: Release current scope attachments**

`IntegrationEventManager.release()` should release only the current scope's integration attachments.

Add an internal overload on `DefaultIntegrationEventSupervisor` if necessary:

```kotlin
internal fun release(scope: EventRuntimeScope)
```

This overload is for runtime collaborators, not for business code.

- [ ] **Step 5: Preserve existing transactional publish marker behavior**

Current release persists records and publishes committed markers after transaction commit when synchronization is active. Keep that behavior.

Do not turn `release()` into direct transport publish.

- [ ] **Step 6: Remove immediate publish tests**

Delete or rewrite tests named like:

- `should immediately publish integration event`
- `should use default schedule time when publishing`

Replace with tests proving that public payload publish does not exist and attach-release is the supported path.

- [ ] **Step 7: Run integration supervisor tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultIntegrationEventSupervisorTest"
```

Expected: tests pass with scope-backed storage.

## Task 5: Refactor Domain Event Supervisor To Scope Attachments

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/DomainEventManager.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DomainEventUnitOfWorkInterceptor.kt`
- Modify: tests in `DefaultDomainEventSupervisorTest.kt`
- Modify: tests in `DomainEventUnitOfWorkInterceptorTest.kt`

- [ ] **Step 1: Store domain attachments in current scope**

Replace supervisor-local `ThreadLocal` event sets and schedule maps with `EventRuntimeContext.currentOrCreateAmbient().domainAttachments`.

Entity binding stays required:

```kotlin
scope.domainAttachments.getOrPut(entity) { mutableListOf() }
    .add(EventAttachment.eager(domainEventPayload, schedule))
```

- [ ] **Step 2: Preserve release-by-entities behavior**

`DomainEventManager.release(entities)` should release domain attachments in the current scope for the supplied entities.

Keep UoW as the release boundary:

- `DomainEventUnitOfWorkInterceptor.postEntitiesPersisted(...)` releases domain events;
- request completion does not auto-release domain events.

- [ ] **Step 3: Fix supplier semantics**

Supplier overload should store lazy attachments. It should not attach the lambda object as a domain event.

At release:

- resolve supplier;
- validate produced payload `@DomainEvent`;
- use attachment schedule;
- create event record.

- [ ] **Step 4: Keep transient and persisted domain event behavior**

Preserve:

- transient immediate dispatch for `@DomainEvent(persist = false)` and no delay;
- persisted EventRecord flow for `persist = true` or delayed domain events;
- event interceptor behavior.

- [ ] **Step 5: Run domain supervisor tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultDomainEventSupervisorTest" --tests "*DomainEventUnitOfWorkInterceptorTest"
```

Expected: tests pass and no reflection assertions depend on old ThreadLocal fields.

## Task 6: Release Listener-Derived Integration Events From Domain Dispatch

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventPublisher.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventPublisherTest.kt`
- Create or modify integration-style test in `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/`

- [ ] **Step 1: Inject IntegrationEventManager into DefaultEventPublisher**

Change constructor wiring:

```kotlin
class DefaultEventPublisher(
    private val eventSubscriberManager: EventSubscriberManager,
    private val integrationEventPublishers: List<IntegrationEventPublisher>,
    private val eventRecordRepository: EventRecordRepository,
    private val eventMessageInterceptorManager: EventMessageInterceptorManager,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val integrationEventPublishCallback: IntegrationEventPublisher.PublishCallback,
    private val integrationEventManager: IntegrationEventManager,
    threadPoolSize: Int,
) : EventPublisher
```

Update `DomainEventAutoConfiguration.defaultEventPublisher(...)` to provide the manager.

- [ ] **Step 2: Push domain dispatch scope**

In `internalPublish4DomainEvent(...)`, wrap subscriber dispatch:

```kotlin
val scope = EventRuntimeContext.push(
    EventRuntimeScopeType.DOMAIN_DISPATCH,
    EventDiagnosticContext.forEventRecord(event)
)
try {
    eventSubscriberManager.dispatch(event.payload)
    integrationEventManager.release()
    event.endDelivery(LocalDateTime.now())
    if (event.persist) {
        eventRecordRepository.save(event)
    }
} catch (ex: Throwable) {
    EventRuntimeContext.discard(scope)
    throw ex
} finally {
    EventRuntimeContext.pop(scope)
}
```

Implementation must ensure `integrationEventManager.release()` releases the domain dispatch scope, not an outer request scope.

- [ ] **Step 3: Persist derived integration events before delivery marker**

The order is required:

1. dispatch domain subscribers;
2. persist and schedule derived integration event records;
3. mark source domain event delivered;
4. save source domain event delivery state if persisted.

If step 2 fails, the domain event remains not delivered and can be retried.

- [ ] **Step 4: Add successful derived event test**

Test:

- domain event record is published;
- event listener attaches integration event through `IntegrationEventSupervisor.instance.attach(...)` or `Mediator.events.attach(...)`;
- integration EventRecord is saved and committed marker path is invoked;
- domain EventRecord is marked delivered after derived event persistence.

- [ ] **Step 5: Add failure discard test**

Test:

- one domain listener attaches an integration event;
- another listener fails;
- domain dispatch throws diagnostic exception;
- integration event from the failed dispatch scope is not persisted;
- persisted domain EventRecord is not marked delivered.

- [ ] **Step 6: Run event publisher tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultEventPublisherTest" --tests "*DerivedIntegration*"
```

Expected: success and discard semantics pass.

## Task 7: Add Programmatic Event Subscriber Diagnostics And Remove Auto Routing

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventSubscriberManager.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/EventDispatchException.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventSubscriberManagerTest.kt`

- [ ] **Step 1: Delete AutoRequest and AutoRelease scanning**

Remove:

- `processAutoRequests(...)`;
- `processAutoReleases(...)`;
- imports for `AutoRequest`, `AutoRequests`, `AutoRelease`, `AutoReleases`;
- converter-based auto request sending;
- auto release integration event publishing.

Subscriber registration should only include:

- explicit programmatic `EventSubscriber<T>`;
- Spring bridge subscriber that calls `ApplicationEventPublisher.publishEvent(payload)`.

- [ ] **Step 2: Add diagnostic exception shape**

Add a runtime exception that can hold all failures:

```kotlin
class EventDispatchException(
    val eventPayloadClass: String,
    val eventTypeName: String?,
    val failures: List<EventListenerFailure>,
) : RuntimeException(...)
```

Each `EventListenerFailure` should include:

- subscriber class for programmatic subscriber;
- listener bean, class, and method when Spring listener metadata is available;
- original cause.

- [ ] **Step 3: Keep collect-all behavior unless changed by tests**

If current dispatch policy invokes all subscribers and then throws, keep it.

Required behavior:

- collect every failed subscriber;
- throw one `EventDispatchException`;
- preserve original causes;
- include event payload class and diagnostic context from `EventRuntimeContext`.

- [ ] **Step 4: Add tests**

Cover:

- two programmatic subscribers fail and both failures are present;
- one subscriber succeeds, one fails, and exception still identifies the failing subscriber;
- Spring bridge failure wraps with event payload class even before listener-factory metadata is added.

- [ ] **Step 5: Run subscriber tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultEventSubscriberManagerTest"
```

Expected: explicit subscribers are diagnosable and Auto* no longer appears in this class or tests.

## Task 8: Add Spring @EventListener Method Diagnostics

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventListenerFactory.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kApplicationListenerMethodAdapter.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt`
- Add Spring context tests under `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/` if existing test dependencies allow it.

- [ ] **Step 1: Implement listener factory**

Use Spring event listener extension points:

```kotlin
class Cap4kEventListenerFactory : EventListenerFactory {
    override fun supportsMethod(method: Method): Boolean {
        return AnnotatedElementUtils.hasAnnotation(method, EventListener::class.java) &&
            !AnnotatedElementUtils.hasAnnotation(method, TransactionalEventListener::class.java)
    }

    override fun createApplicationListener(beanName: String, type: Class<*>, method: Method): ApplicationListener<*> {
        return Cap4kApplicationListenerMethodAdapter(beanName, type, method)
    }
}
```

Do not steal `@TransactionalEventListener` from Spring's transactional listener factory.

- [ ] **Step 2: Implement method adapter**

Extend `ApplicationListenerMethodAdapter` and wrap the actual method invocation.

Capture:

- bean name;
- listener class;
- listener method;
- event payload class;
- current event record id from `EventRuntimeContext` when available.

Use the Spring method invocation hook that actually surrounds listener method execution. If `doInvoke(...)` is used, verify it catches business method exceptions and preserves original cause.

- [ ] **Step 3: Push listener diagnostic context while invoking**

During listener invocation:

- enrich `EventRuntimeContext` with listener metadata;
- restore the previous diagnostic context in `finally`.

This is required so request failures inside listeners can report the current listener method.

- [ ] **Step 4: Register factory in autoconfiguration**

Add a bean in `DomainEventAutoConfiguration` or another appropriate starter config.

If order is necessary, make the factory order explicit and ensure it does not override transactional listener behavior.

- [ ] **Step 5: Reject or warn on @EventListener return values**

Preferred: fail startup for non-`Unit`/`void` listener return types handled by cap4k factory.

Acceptable fallback if startup rejection is too invasive:

- produce diagnostic warning;
- add verification docs and skill rules that listener methods must return `Unit`;
- add tests that document unsupported return-value publication.

- [ ] **Step 6: Add Spring listener tests**

Cover:

- failing `@EventListener` method reports bean name, class, and method;
- listener return value is rejected or reported as unsupported;
- `@TransactionalEventListener` remains owned by Spring transactional event factory.

- [ ] **Step 7: Run Spring listener diagnostics tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*EventListener*Diagnostics*" --tests "*Cap4kEventListener*"
```

Expected: diagnostics identify the actual listener method.

## Task 9: Add Request Scope And Listener Request Diagnostics

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/RequestDispatchException.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisorTest.kt`
- Add listener-request failure tests if not covered by Task 8.

- [ ] **Step 1: Push request scope around handler execution**

`RequestSupervisor.internalSend(...)` should:

- resolve request handler;
- push `REQUEST` scope with request param class and handler class diagnostics;
- invoke handler;
- pop scope in `finally`.

Do not release events at request completion.

- [ ] **Step 2: Detect unreleased request-scope attachments**

On request scope close:

- if scope still contains domain or integration attachments, discard them;
- report a deterministic warning or exception suitable for tests.

Use exception only if this does not break valid existing flows. Otherwise, log and add a test seam.

- [ ] **Step 3: Wrap request failures with diagnostic context**

When a request fails inside an event listener, exception should include:

- event payload class;
- listener bean/class/method if available;
- request param class;
- request handler class;
- original cause.

- [ ] **Step 4: Add tests**

Cover:

- request handler failure outside event listener includes request param and handler;
- request handler failure inside event listener also includes event and listener context;
- nested request scope does not release or discard outer-scope attachments.

- [ ] **Step 5: Run request tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultRequestSupervisorTest" --tests "*Request*Diagnostics*"
```

Expected: request diagnostics pass and nested scope behavior is isolated.

## Task 10: Remove Auto* Public Annotations And Persist AutoAttach Runtime

**Files:**

- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/annotation/AutoAttach.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/annotation/AutoRequest.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/annotation/AutoRelease.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultPersistListenerManager.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/repo/impl/DefaultPersistListenerManagerTest.kt`

- [ ] **Step 1: Delete Auto annotation files**

Remove all five public annotations:

- `AutoAttach`;
- `AutoRequest`;
- `AutoRequests`;
- `AutoRelease`;
- `AutoReleases`.

They currently live in three files; deleting those files removes all five declarations.

- [ ] **Step 2: Remove AutoAttach scanning**

`DefaultPersistListenerManager` should no longer scan domain event classes for `@AutoAttach`.

Keep only explicit persist listener behavior that remains part of the framework.

- [ ] **Step 3: Remove AutoAttach tests**

Delete test sections that assert automatic domain events from persistence lifecycle.

If the test file becomes mostly obsolete, rewrite it to cover only remaining persist listener behavior.

- [ ] **Step 4: Run compile search**

Run:

```powershell
rg -n "AutoAttach|AutoRequest|AutoRequests|AutoRelease|AutoReleases" ddd-core cap4k-ddd-starter
```

Expected: no source or test usage remains, except release notes/spec/plan references.

- [ ] **Step 5: Run persist listener tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "*DefaultPersistListenerManagerTest"
```

Expected: remaining behavior passes.

## Task 11: Remove Mediator.events.publish Forwarding

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: affected tests and samples.

- [ ] **Step 1: Delete publish methods from Mediator event facade**

Remove methods that forward payload publication:

```kotlin
fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime = LocalDateTime.now())
fun <EVENT : Any> publish(eventPayload: EVENT, delay: Duration)
```

Keep:

- `attach(payload, schedule)`;
- `attach(schedule) { payload }`;
- `detach(payload)`.

- [ ] **Step 2: Remove DefaultMediator forwarding**

Delete:

```kotlin
override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime) {
    IntegrationEventSupervisor.instance.publish(eventPayload, schedule)
}
```

- [ ] **Step 3: Compile against removed API**

Run:

```powershell
.\gradlew.bat :ddd-core:compileKotlin
```

Expected: compile passes after all references are removed.

- [ ] **Step 4: Search for forbidden payload publish guidance in source code**

Run:

```powershell
rg -n "events\\.publish|IntegrationEventSupervisor\\.instance\\.publish|IntegrationEventSupervisor\\.publish|\\.publish\\(eventPayload" ddd-core cap4k-ddd-starter
```

Expected: only internal `EventPublisher.publish(EventRecord)` and transport publisher APIs remain.

## Task 12: Update Runtime Wiring And Cleanup

**Files:**

- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/IntegrationEventAutoConfiguration.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/web/ClearDomainContextInterceptor.kt`
- Modify related tests if wiring signatures change.

- [ ] **Step 1: Update integration supervisor bean signatures**

If constructor signatures changed, update `IntegrationEventAutoConfiguration.defaultIntegrationEventSupervisor(...)`.

Ensure `IntegrationEventSupervisorSupport.configure(...)` still configures both supervisor and manager.

- [ ] **Step 2: Update domain event publisher bean signatures**

Inject `IntegrationEventManager` into `DefaultEventPublisher`.

Make sure there is no circular bean dependency. Expected direction:

```text
DefaultEventPublisher -> IntegrationEventManager interface
DefaultDomainEventSupervisor -> EventPublisher
```

If a circular dependency appears, break it with `ObjectProvider<IntegrationEventManager>` or a small internal collaborator rather than moving integration release logic back into subscriber manager.

- [ ] **Step 3: Register cap4k event listener factory**

Register the Spring listener factory once.

Confirm it does not replace Spring transactional event listener handling.

- [ ] **Step 4: Consolidate HTTP context cleanup**

`ClearDomainContextInterceptor` should call the shared runtime context reset path.

Avoid needing separate domain and integration supervisor reset calls.

- [ ] **Step 5: Run starter compile**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:compileKotlin
```

Expected: wiring compiles.

## Task 13: Update Analysis Documentation

**Files:**

- Modify: `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`

- [ ] **Step 1: Remove removed runtime capabilities**

Delete or rewrite mentions of:

- `AutoAttach`;
- `AutoRequest`;
- `AutoRelease`;
- business-facing `IntegrationEventSupervisor.publish`;
- `Mediator.events.attach or publish`.

- [ ] **Step 2: Add scoped runtime behavior**

Document:

- business code uses `Mediator.events.attach(...)` for outbound integration events;
- domain dispatch pushes a scope;
- successful domain dispatch releases derived integration events before marking domain event delivered;
- failed domain dispatch discards derived integration events and leaves persisted domain event retryable.

- [ ] **Step 3: Add diagnostics behavior**

Document:

- event-level delivery remains;
- multi-listener failures are collected;
- diagnostics include event payload, listener metadata, request param, handler, and causes when available;
- Spring `@EventListener` receives payloads through cap4k bridge and method invocation is wrapped by cap4k diagnostics.

- [ ] **Step 4: Verify analysis doc drift**

Run:

```powershell
rg -n "AutoAttach|AutoRequest|AutoRelease|attach or publish|IntegrationEventSupervisor\\.publish|Mediator\\.events\\.publish" docs/superpowers/analysis
```

Expected: only historical spec/plan references remain, not active runtime guidance.

## Task 14: Update Public Authoring Documentation

**Files:**

- Modify: `docs/public/authoring/tactical-model.md`
- Modify: `docs/public/authoring/domain.md`
- Modify: `docs/public/authoring/application.md`
- Modify event-related examples if search finds stale wording.

- [ ] **Step 1: Teach attach-only outbound integration events**

Public authoring docs should say:

- aggregate behavior records domain facts;
- explicit listener methods react;
- writes route to zero-trust commands;
- outbound integration facts use `Mediator.events.attach(...)`;
- listener methods return `Unit`.

- [ ] **Step 2: Remove publish as a business choice**

Do not teach authors to choose between `attach` and `publish`.

If immediate out-of-context publication is mentioned, remove it rather than marking it deprecated.

- [ ] **Step 3: Remove hidden auto routing guidance**

No public authoring page should recommend or imply:

- persistence lifecycle auto domain facts;
- annotation-driven event-to-command routing;
- annotation-driven event-to-integration conversion.

- [ ] **Step 4: Verify public authoring drift**

Run:

```powershell
rg -n "AutoAttach|AutoRequest|AutoRelease|Mediator\\.events\\.publish|IntegrationEventSupervisor\\.publish|attach or publish" docs/public/authoring
```

Expected: no active authoring guidance remains for removed APIs.

## Task 15: Update Source Skills

**Files:**

- Modify: `skills/cap4k-modeling/SKILL.md`
- Modify: `skills/cap4k-modeling/rules/**` as needed.
- Modify: `skills/cap4k-implementation/SKILL.md`
- Modify: `skills/cap4k-implementation/rules/mediator-and-uow.md`
- Modify: `skills/cap4k-service-integration/SKILL.md`
- Modify: `skills/cap4k-service-integration/rules/integration-events.md`
- Modify: `skills/cap4k-generation/SKILL.md`
- Modify: generation rules or workflows that can emit event code.
- Modify: `skills/cap4k-verification/SKILL.md`
- Modify: `skills/cap4k-verification/rules/test-strategy.md`
- Modify: `skills/scripts/validate-cap4k-skills.ps1` if source skill validation exists and can enforce the new contract.

- [ ] **Step 1: Update modeling skill**

Rules must state:

- domain events come from domain behavior;
- domain events are business facts, not persistence lifecycle callbacks;
- no AutoAttach concept exists in stable authoring.

- [ ] **Step 2: Update implementation skill**

Rules must state:

- event listener methods are explicit and named;
- listener methods return `Unit`;
- listener-side writes route through commands;
- outbound integration events use `Mediator.events.attach(...)`;
- no `Mediator.events.publish(payload)` business path exists.

- [ ] **Step 3: Update service-integration skill**

Rules must state:

- outbound integration event path is attach-only;
- integration events derived from domain facts can be attached inside event listeners;
- commands remain zero-trust and idempotent when multiple listeners route broad facts into multiple command paths.

- [ ] **Step 4: Update generation skill**

Rules must prohibit generated code from emitting:

- `AutoAttach`;
- `AutoRequest`;
- `AutoRelease`;
- payload `publish(...)` calls.

- [ ] **Step 5: Update verification skill**

Add scans for removed APIs:

```powershell
rg -n "AutoAttach|AutoRequest|AutoRequests|AutoRelease|AutoReleases|Mediator\\.events\\.publish|IntegrationEventSupervisor\\.publish" .
```

Expected: source/runtime and active docs have no usage except historical specs/plans or explicit removal notes.

- [ ] **Step 6: Run skill validation**

If present:

```powershell
.\skills\scripts\validate-cap4k-skills.ps1
```

If the script cannot run on this platform or does not exist, record that explicitly in the final implementation notes.

## Task 16: Full Runtime Verification

**Files:**

- No edits unless verification exposes failures.

- [ ] **Step 1: Run focused core tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test
```

Expected: all `ddd-core` tests pass.

- [ ] **Step 2: Run starter compile/tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test
```

If `test` has no tests, Gradle should still compile test sources and report success.

- [ ] **Step 3: Run transport adapter compile checks**

Run:

```powershell
.\gradlew.bat :ddd-integration-event-http:compileKotlin :ddd-integration-event-rabbitmq:compileKotlin :ddd-integration-event-rocketmq:compileKotlin
```

Expected: transport adapters still compile because internal `EventPublisher.publish(EventRecord)` remains unchanged.

- [ ] **Step 4: Run broad compile if focused checks pass**

Run:

```powershell
.\gradlew.bat compileKotlin
```

If this is too slow or blocked by unrelated modules, record exact failure and run the maximum focused equivalent.

- [ ] **Step 5: Run forbidden API scans**

Run:

```powershell
rg -n "AutoAttach|AutoRequest|AutoRequests|AutoRelease|AutoReleases" ddd-core cap4k-ddd-starter skills docs/public/authoring docs/superpowers/analysis
rg -n "Mediator\\.events\\.publish|IntegrationEventSupervisor\\.publish|IntegrationEventSupervisor\\.instance\\.publish|attach or publish" ddd-core cap4k-ddd-starter skills docs/public/authoring docs/superpowers/analysis
```

Expected:

- no runtime source or active guidance uses removed APIs;
- spec and plan may mention removed names as historical context or verification targets.

## Task 17: Commit, Issue Lifecycle, And PR

**Files:**

- No source edits unless final issue references are added to docs.

- [ ] **Step 1: Review worktree diff**

Run:

```powershell
git status --short
git diff --stat
git diff -- docs/superpowers/specs docs/superpowers/plans
```

Expected: changes match this plan and implementation scope.

- [ ] **Step 2: Commit implementation**

Use one or more coherent commits. Suggested grouping:

```powershell
git add ddd-core cap4k-ddd-starter
git commit -m "feat: scope event runtime dispatch"

git add docs skills
git commit -m "docs: align event authoring with scoped runtime"
```

If implementation is done in one coherent commit instead, keep message explicit.

- [ ] **Step 3: Update GitHub issue #56**

Add a comment summarizing:

- listener diagnostics implemented;
- tests and commands run;
- PR link.

Keep issue open until PR merges and downstream verification is done, unless issue governance says the issue can close at merge.

- [ ] **Step 4: Update GitHub issue #61**

Add a comment summarizing:

- domain-dispatch-scope release implemented;
- failure discard behavior;
- tests and commands run;
- PR link.

Keep issue open until PR merges and downstream verification is done, unless issue governance says the issue can close at merge.

- [ ] **Step 5: Push branch and open PR**

Run:

```powershell
git push -u origin feature/event-runtime-scope-diagnostics
```

Open PR from `feature/event-runtime-scope-diagnostics` to `master`.

PR body must include:

- linked issues: `Closes #56` only if closure is valid at merge, otherwise `Refs #56`; same for `#61`;
- runtime summary;
- removed API summary;
- docs and skills sync summary;
- verification commands and results.

## Acceptance Checklist

- [ ] Approved spec is committed on the feature branch.
- [ ] Business-facing integration events are attach-only.
- [ ] `Mediator.events.publish(payload, ...)` is removed.
- [ ] `IntegrationEventSupervisor.publish(payload, ...)` is removed.
- [ ] `AutoAttach`, `AutoRequest`, `AutoRequests`, `AutoRelease`, and `AutoReleases` are removed from runtime.
- [ ] Domain event attachments are scope-aware.
- [ ] Integration event attachments are scope-aware.
- [ ] Supplier attach overloads validate produced payloads at release.
- [ ] Equal event payloads attached twice are not silently deduplicated.
- [ ] Domain dispatch releases listener-derived integration events on success.
- [ ] Domain dispatch discards listener-derived integration events on failure.
- [ ] Derived integration event records persist before source domain event delivery marker.
- [ ] Programmatic subscriber failures report event and subscriber metadata.
- [ ] Spring `@EventListener` failures report bean, class, method, event payload, and original cause.
- [ ] Request failures inside event listeners report request param and handler metadata.
- [ ] Listener return-value event publication is rejected or documented as unsupported with verification.
- [ ] `docs/superpowers/analysis/` reflects actual runtime behavior.
- [ ] `docs/public/authoring/` teaches explicit attach-only event flow.
- [ ] `skills/` normal activation path teaches explicit attach-only event flow and forbidden API scans.
- [ ] Focused runtime tests pass.
- [ ] Forbidden API scans pass.
- [ ] Branch is pushed and PR is opened from the worktree branch, not from `master`.
