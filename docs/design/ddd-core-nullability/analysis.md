# ddd-core Nullability & Optional Audit

> Date: 2026-04-15
> Status: Reference material
> Scope: **Full project scan** — all modules under `cap4k/`, not just `ddd-core`
> Purpose: Inventory of nullability issues and tradeoff analysis. This document does not prescribe execution order or priority — those decisions belong to whoever picks up this work.

---

## Context

The ddd-core module was designed during the author's first exposure to Kotlin. Some interfaces use `java.util.Optional` (a Java idiom) instead of Kotlin's native `T?` nullable types. Additionally, some nullable return types have ambiguous semantics.

**Scan scope**: Full `grep` of `Optional` across all modules. Confirmed: the `Optional` usage originates in `ddd-core` interfaces and propagates outward through implementations and tests. No independent `Optional` usages exist in other modules.

---

## Finding 1: `Optional` Usage (Anti-pattern in Kotlin)

Kotlin has native null-safety (`T?`, `?.`, `?:`, `!!`). `java.util.Optional` in Kotlin is redundant:
- Cannot participate in `?.` / `?:` safe-call chains
- Adds unnecessary boxing/unboxing
- Kotlin community consensus: `Optional` should not appear in Kotlin-first APIs

### Affected Interfaces

#### `Repository<ENTITY>` (`domain/repo/Repository.kt`)

```kotlin
// Current
fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): Optional<ENTITY>
fun findFirst(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): Optional<ENTITY>
fun findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): Optional<ENTITY>

// Kotlin-idiomatic equivalent
fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): ENTITY?
fun findFirst(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): ENTITY?
fun findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): ENTITY?
```

#### `RepositorySupervisor` (`domain/repo/RepositorySupervisor.kt`)

```kotlin
// Current
fun <ENTITY: Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): Optional<ENTITY>
fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): Optional<ENTITY>
fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): Optional<ENTITY>

// Kotlin-idiomatic equivalent
fun <ENTITY: Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): ENTITY?
fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): ENTITY?
fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): ENTITY?
```

#### `AggregateSupervisor` (`domain/aggregate/AggregateSupervisor.kt`)

```kotlin
// Current
fun <AGGREGATE: Aggregate<*>> findOne(predicate: AggregatePredicate<AGGREGATE, *>, persist: Boolean = true): Optional<AGGREGATE>
fun <AGGREGATE: Aggregate<*>> findFirst(predicate: AggregatePredicate<AGGREGATE, *>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): Optional<AGGREGATE>
fun <AGGREGATE: Aggregate<ENTITY>, ENTITY: Any> findFirst(predicate: AggregatePredicate<AGGREGATE, ENTITY>, vararg orders: OrderInfo): Optional<AGGREGATE>

// Kotlin-idiomatic equivalent
fun <AGGREGATE: Aggregate<*>> findOne(predicate: AggregatePredicate<AGGREGATE, *>, persist: Boolean = true): AGGREGATE?
fun <AGGREGATE: Aggregate<*>> findFirst(predicate: AggregatePredicate<AGGREGATE, *>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): AGGREGATE?
fun <AGGREGATE: Aggregate<ENTITY>, ENTITY: Any> findFirst(predicate: AggregatePredicate<AGGREGATE, ENTITY>, vararg orders: OrderInfo): AGGREGATE?
```

### Call-site Migration Reference

If this change is made, downstream call-sites would need:

| Before | After |
|--------|-------|
| `.get()` | `!!` |
| `.orElse(default)` | `?: default` |
| `.orElseThrow { ... }` | `?: throw ...` |
| `.isPresent` | `!= null` |
| `.ifPresent { ... }` | `?.let { ... }` |
| `.map { ... }` | `?.let { ... }` |
| `.flatMap { ... }` | `?.let { ... }` |

Also: `import java.util.*` can be removed from affected files.

### Blast Radius (project-wide)

**Interface definitions (ddd-core) — 3 files:**

| File | Methods |
|------|---------|
| `ddd-core/.../domain/repo/Repository.kt` | `findOne()`, `findFirst()` ×2 |
| `ddd-core/.../domain/repo/RepositorySupervisor.kt` | `findOne()`, `findFirst()` ×2 |
| `ddd-core/.../domain/aggregate/AggregateSupervisor.kt` | `findOne()`, `findFirst()` ×2 |

**Implementation classes — 5 files:**

| Module | File | Notes |
|--------|------|-------|
| `ddd-core` | `impl/DefaultMediator.kt` | Delegates only |
| `ddd-domain-repo-jpa` | `AbstractJpaRepository.kt` | `findOne()`, `findFirst()` |
| `ddd-domain-repo-jpa` | `impl/DefaultRepositorySupervisor.kt` | `findOne()`, `findFirst()` |
| `ddd-domain-repo-jpa` | `impl/DefaultAggregateSupervisor.kt` | `Optional.of()`/`Optional.empty()` wrapping |
| `ddd-domain-repo-jpa-querydsl` | `AbstractQuerydslRepository.kt` | `Optional.of()`/`Optional.empty()` wrapping |

**Utility class — 1 file:**

| Module | File | Notes |
|--------|------|-------|
| `ddd-domain-repo-jpa` | `JpaQueryUtils.kt` | `queryFirst()` returns `Optional<R>` — independent helper |

**Test files — cap4k interface tests (5 files, ~31 usages):**

| Module | Test File |
|--------|-----------|
| `ddd-domain-repo-jpa` | `AbstractJpaRepositoryTest.kt` (~10 usages) |
| `ddd-domain-repo-jpa` | `DefaultRepositorySupervisorTest.kt` (~5 usages) |
| `ddd-domain-repo-jpa` | `DefaultAggregateSupervisorTest.kt` (~12 usages) |
| `ddd-domain-repo-jpa` | `JpaQueryUtilsTest.kt` (~2 usages) |
| `ddd-domain-repo-jpa-querydsl` | `AbstractQuerydslRepositoryTest.kt` (~2 usages) |

**Test files — Spring Data boundary (4 files, no change needed):**

| Module | Test File | Reason |
|--------|-----------|--------|
| `ddd-application-request-jpa` | `JpaRequestRecordRepositoryTest.kt` | Spring Data `findById` natively returns `Optional` |
| `ddd-domain-event-jpa` | `JpaEventRecordRepositoryTest.kt` | Same |
| `ddd-distributed-saga-jpa` | `JpaSagaRecordRepositoryTest.kt` | Same |
| `ddd-integration-event-http-jpa` | `JpaHttpIntegrationEventSubscriberRegisterTest.kt` + `EventHttpSubscriberJpaRepositoryTest.kt` | Same |

**Summary: 14 files, ~62 changes. This is a breaking API change.**

### Migration Tradeoffs

| Approach | Pros | Cons |
|----------|------|------|
| Big bang (change all, bump major version) | Clean cut, no legacy baggage | All downstream users must update at once |
| Deprecate + bridge (add `findOneOrNull()` alongside, deprecate old) | Gradual migration path | Temporary API surface bloat, two ways to do the same thing |
| Extension functions as bridge | Non-breaking, additive | `Optional` signatures remain in core interfaces until removed |

---

## Finding 2: Nullable Returns with Ambiguous Semantics

### Correctly Nullable (no action needed)

| Interface | Method | Returns | Reason |
|-----------|--------|---------|--------|
| `RequestSupervisor` | `result(requestId)` | `R?` | Async result may not be ready yet |
| `SagaSupervisor` | `result(id)` | `R?` | Same — saga may not be complete |
| `SagaRecord` | `getResult()` | `R?` | Saga may not be complete |
| `SagaRecord` | `getSagaProcessResult(processCode)` | `R?` | Sub-process may not have executed |
| `RequestRecord` | `getResult()` | `R?` | Request may not be complete |
| `AggregateSupervisor` | `getById(id)` | `AGGREGATE?` | ID may not exist |
| `AggregateSupervisor` | `removeById(id)` | `AGGREGATE?` | ID may not exist |

### Questionable Nullability

#### `DomainServiceSupervisor.getService()` → `DOMAIN_SERVICE?`

```kotlin
fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE?
```

**Observation**: "Service not found" is a configuration error (missing `@DomainService` annotation or component scan misconfiguration), not a normal runtime condition. Returning `null` forces every call-site to handle a case that shouldn't happen in a correctly wired application.

**Tradeoff**: Changing to non-null + throw simplifies call-sites, but means misconfiguration throws at use-time rather than returning null. Whether that's desirable depends on the desired error-handling philosophy.

#### `SagaProcessSupervisor.sendProcess()` → `RESPONSE?`

```kotlin
fun <REQUEST: RequestParam<RESPONSE>, RESPONSE: Any> sendProcess(processCode: String, request: REQUEST): RESPONSE?
```

**Observation**: The method executes a saga sub-process — it either succeeds (returns result) or fails (throws exception). Examining the implementation: `null` is returned when the sub-process was already executed (cache hit from `isSagaProcessExecuted`), and `getSagaProcessResult` provides the cached result. But the caller (`SagaHandler.execProcess`) passes through the `null` without distinguishing "not executed" from "executed with null result".

**Tradeoff**:
- Option A: Always return a result (fetch from cache if already executed) → simpler caller code, but changes semantics
- Option B: Keep nullable but document what `null` means explicitly → no breaking change, but callers must still handle it

---

## Finding 3: `Any` Parameter Where Generics Would Be Better

### `Aggregate.Default` Constructor

```kotlin
open class Default<ENTITY: Any>(payload: Any? = null) : Aggregate<ENTITY> {
    init {
        if (payload != null) {
            require(payload is AggregatePayload<*>) { "payload must be AggregatePayload" }
            @Suppress("UNCHECKED_CAST")
            val root = Mediator.factories.create(payload as AggregatePayload<ENTITY>)
            _wrap(root)
        }
    }
}
```

**Observation**: `payload: Any?` + runtime `require` check is a Java-style pattern. Kotlin's type system could enforce this at compile time via overloaded constructors or a typed parameter (`AggregatePayload<ENTITY>?`).

### `endRequest(result: Any)` / `endSagaProcess(result: Any)` / `endSaga(result: Any)`

| Interface | Method |
|-----------|--------|
| `RequestRecord` | `endRequest(now: LocalDateTime, result: Any)` |
| `SagaRecord` | `endSagaProcess(now: LocalDateTime, processCode: String, result: Any)` |
| `SagaRecord` | `endSaga(now: LocalDateTime, result: Any)` |
| `EventRecord` | `init(payload: Any, ...)` |

**Observation**: `result: Any` / `payload: Any` lose type information. These sit at framework serialization boundaries where the concrete type is unknown at compile time — the `Any` may be intentionally correct here. Changing these to generics would require refactoring the serialization layer.

---

## Finding 4: `RequestParam<RESULT: Any>` Constraint

`RequestParam<RESULT: Any>` forces RESULT to be non-null. The constraint flows consistently through the entire request chain:

- `Command<PARAM, RESULT: Any>.exec()` → returns `RESULT` (non-null) ✓
- `RequestHandler<REQUEST, RESPONSE: Any>.exec()` → returns `RESPONSE` (non-null) ✓
- `NoneResultCommandParam` uses `Unit` as RESULT ✓
- `RequestSupervisor.send()` → returns `RESPONSE` (non-null, sync always has result) ✓
- `RequestSupervisor.result()` → returns `R?` (nullable, async may not be ready) ✓

**Verdict**: No issues found. The constraint is correct and consistent.

---

## Complete Interface Inventory

For reference, all public interfaces in ddd-core and their nullability status:

### Domain Layer

| Interface | Status |
|-----------|--------|
| `Aggregate<ENTITY>` | Clean |
| `AggregatePayload<ENTITY>` | Clean — marker interface |
| `AggregatePredicate<A, E>` | Clean — marker interface |
| `AggregateFactory<P, E>` | Clean |
| `AggregateFactorySupervisor` | Clean |
| `AggregateSupervisor` | `findOne`/`findFirst` use `Optional` (Finding 1) |
| `Id<A, K>` | Clean |
| `Predicate<ENTITY>` | Clean — marker interface |
| `Repository<ENTITY>` | `findOne`/`findFirst` use `Optional` (Finding 1) |
| `RepositorySupervisor` | `findOne`/`findFirst` use `Optional` (Finding 1) |
| `Specification<Entity>` | Clean |
| `SpecificationManager` | Clean |
| `ValueObject<ID>` | Clean |
| `DomainServiceSupervisor` | `getService()` returns `T?` (Finding 2) |
| `DomainEventSupervisor` | Clean |
| `EventPublisher` | Clean |
| `EventRecord` | Clean (`init(payload: Any)` noted in Finding 3) |
| `EventSubscriber` | Clean |
| `EventSubscriberManager` | Clean |
| `PersistListener` | Clean |
| `PersistListenerManager` | Clean |

### Application Layer

| Interface | Status |
|-----------|--------|
| `RequestParam<RESULT>` | Clean |
| `RequestHandler<REQ, RES>` | Clean |
| `RequestInterceptor<REQ, RES>` | Clean |
| `RequestSupervisor` | Clean — `result()` correctly `R?` |
| `RequestRecord` | Clean (`endRequest(result: Any)` noted in Finding 3) |
| `RequestRecordRepository` | Clean |
| `RequestManager` | Clean |
| `Command<P, R>` | Clean |
| `Query<P, R>` | Clean |
| `ListQuery<P, I>` | Clean |
| `PageQuery<P, I>` | Clean |
| `UnitOfWork` | Clean |
| `Locker` | Clean |
| `IntegrationEventSupervisor` | Clean |
| `SagaParam<R>` | Clean |
| `SagaHandler<REQ, RES>` | `execProcess()` returns `SUB_RESPONSE?` — delegates to `SagaProcessSupervisor` (Finding 2) |
| `SagaProcessSupervisor` | `sendProcess()` returns `RESPONSE?` — unclear null semantics (Finding 2) |
| `SagaSupervisor` | Clean — `result()` correctly `R?` |
| `SagaRecord` | Clean (`endSaga(result: Any)` noted in Finding 3) |
| `SagaRecordRepository` | Clean |
| `SagaManager` | Clean |

### Top-Level

| Interface | Status |
|-----------|--------|
| `Mediator` | Inherits Finding 1 from `RepositorySupervisor`, `AggregateSupervisor` |
| `X` | Same — delegates to `Mediator` |
