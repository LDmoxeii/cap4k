# cap4k ddd-core Nullability Contract Stabilization Design

> Date: 2026-04-27
> Status: Current-master reviewed; approved for implementation planning
> Current-master review: 2026-04-28
> Scope: ddd-core public nullability contract, repository/aggregate lookup return types, domain service lookup, saga sub-process result semantics
> Out of scope: serialization-boundary generic typing, `Any` payload/result refactors, Spring Data repository API changes

## Background

The existing `docs/design/ddd-core-nullability/analysis.md` documents a full scan of nullability and `java.util.Optional` usage across the project.

That analysis was created because the original ddd-core APIs were designed before the framework had a stable Kotlin nullability policy.
It is historical reference material, not the current implementation plan. In current `master`, the contract-first query contract has already removed the old `ListQuery` / `PageQuery` API family and left `Query<PARAM, RESULT>` plus `PageRequest(pageNum, pageSize)`. Those historical query-family inventory entries do not belong to this stabilization slice.

There are two related problems:

- Some Kotlin-first cap4k APIs expose Java `Optional<T>`.
- Some APIs return `T?` even when `null` means configuration error or broken framework state rather than a normal absence result.

The goal is not to blindly convert every nullable return into non-null, nor to blindly convert every `Optional<T>` into `T?`.

The goal is to stabilize the public nullability contract:

```text
Normal absence -> nullable
Framework/configuration invariant failure -> non-null API + exception
Synchronous request result -> non-null
Java framework boundary -> may keep Java Optional internally
cap4k Kotlin public API -> no Optional exposure
```

## Design Principles

### 1. Normal Absence Uses `T?`

If absence is a normal, expected outcome, the Kotlin API should return nullable.

Examples:

- repository lookup finds no matching entity
- aggregate lookup finds no matching aggregate
- asynchronous request result is not ready yet
- saga result is not completed yet

Kotlin callers should use:

```kotlin
val entity = repository.findOne(predicate)
if (entity != null) {
    ...
}
```

not:

```kotlin
val entity = repository.findOne(predicate)
if (entity.isPresent) {
    ...
}
```

### 2. Broken Framework State Throws

If `null` would mean a configuration error or broken invariant, the public API should be non-null and the implementation should fail fast.

Examples:

- domain service class is not registered as a Spring bean
- registered bean is not a valid `@DomainService`
- saga sub-process was recorded as executed but no cached result exists

These are not normal business absence cases. Returning `null` hides the real error and forces defensive handling at every call site.

### 3. Synchronous Request Results Are Non-Null

`RequestParam<RESULT : Any>` already encodes that synchronous request execution returns a non-null result.

This should remain unchanged.

Therefore any API that executes a request synchronously should return `RESULT`, not `RESULT?`, unless the method explicitly models "result not ready yet".

### 4. Java Boundaries May Keep Optional Internally

External Java APIs such as Spring Data may still return `Optional<T>`.

This is acceptable at the boundary.

cap4k implementations may consume those `Optional<T>` values internally, but cap4k Kotlin public APIs should convert them to Kotlin nullable or throw according to the rules above.

## Required Contract Changes

### Repository Lookup

Current:

```kotlin
fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): Optional<ENTITY>

fun findFirst(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): Optional<ENTITY>

fun findFirst(
    predicate: Predicate<ENTITY>,
    vararg orders: OrderInfo
): Optional<ENTITY>
```

Target:

```kotlin
fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): ENTITY?

fun findFirst(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): ENTITY?

fun findFirst(
    predicate: Predicate<ENTITY>,
    vararg orders: OrderInfo
): ENTITY?
```

Reason:

- "no matching entity" is normal absence
- Kotlin nullable is the idiomatic representation
- `Optional<T>` should not appear in cap4k Kotlin public repository APIs

### RepositorySupervisor Lookup

Current:

```kotlin
fun <ENTITY : Any> findOne(
    predicate: Predicate<ENTITY>,
    persist: Boolean = true
): Optional<ENTITY>

fun <ENTITY : Any> findFirst(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): Optional<ENTITY>

fun <ENTITY : Any> findFirst(
    predicate: Predicate<ENTITY>,
    vararg orders: OrderInfo
): Optional<ENTITY>
```

Target:

```kotlin
fun <ENTITY : Any> findOne(
    predicate: Predicate<ENTITY>,
    persist: Boolean = true
): ENTITY?

fun <ENTITY : Any> findFirst(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): ENTITY?

fun <ENTITY : Any> findFirst(
    predicate: Predicate<ENTITY>,
    vararg orders: OrderInfo
): ENTITY?
```

Reason:

- same semantics as `Repository`
- supervisor should not rewrap nullable semantics in Java `Optional`

### AggregateSupervisor Lookup

Current:

```kotlin
fun <AGGREGATE : Aggregate<*>> findOne(
    predicate: AggregatePredicate<AGGREGATE, *>,
    persist: Boolean = true
): Optional<AGGREGATE>

fun <AGGREGATE : Aggregate<*>> findFirst(
    predicate: AggregatePredicate<AGGREGATE, *>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): Optional<AGGREGATE>

fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
    predicate: AggregatePredicate<AGGREGATE, ENTITY>,
    vararg orders: OrderInfo
): Optional<AGGREGATE>
```

Target:

```kotlin
fun <AGGREGATE : Aggregate<*>> findOne(
    predicate: AggregatePredicate<AGGREGATE, *>,
    persist: Boolean = true
): AGGREGATE?

fun <AGGREGATE : Aggregate<*>> findFirst(
    predicate: AggregatePredicate<AGGREGATE, *>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true
): AGGREGATE?

fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
    predicate: AggregatePredicate<AGGREGATE, ENTITY>,
    vararg orders: OrderInfo
): AGGREGATE?
```

Reason:

- "no matching aggregate" is normal absence
- aggregate supervisor should follow Kotlin lookup semantics
- implementations should stop wrapping aggregate instances in `Optional.of(...)`

### JpaQueryUtils.queryFirst

Current:

```kotlin
fun <R, F> queryFirst(...): Optional<R>
```

Target:

```kotlin
fun <R, F> queryFirst(...): R?
```

Reason:

- `JpaQueryUtils` is cap4k Kotlin utility code, not a Spring Data public API
- "no first result" is normal absence
- leaving this method as `Optional<R>` would preserve an unnecessary Optional island inside cap4k-owned Kotlin APIs

### DomainServiceSupervisor.getService

Current:

```kotlin
fun <DOMAIN_SERVICE> getService(
    domainServiceClass: Class<DOMAIN_SERVICE>
): DOMAIN_SERVICE?
```

Target:

```kotlin
fun <DOMAIN_SERVICE : Any> getService(
    domainServiceClass: Class<DOMAIN_SERVICE>
): DOMAIN_SERVICE
```

Reason:

- missing domain service is not a normal business result
- a missing bean means misconfiguration
- a bean without `@DomainService` means invalid registration
- returning `null` hides framework configuration errors

Implementation behavior should change from "catch and return null" to fail-fast.

Expected failure cases:

- Spring bean is missing
- resolved bean does not carry `@DomainService` through its class hierarchy
- Spring lookup throws due to ambiguous or invalid bean state
- interface-only `@DomainService` remains insufficient; the resolved implementation class or one of its superclasses must carry the annotation

Expected behavior:

```kotlin
val service = domainServiceSupervisor.getService(MyDomainService::class.java)
```

If service resolution fails, throw an exception with enough context to diagnose:

```text
Domain service not found: com.acme.MyDomainService
```

or:

```text
Bean is not a domain service: com.acme.MyService
```

Exact exception type can be decided during implementation. `IllegalStateException` is acceptable unless a project-specific domain exception is more appropriate.

Implementation decision for this slice:

- the public generic bound should be `DOMAIN_SERVICE : Any`, so Kotlin callers cannot ask for a nullable service type
- missing bean or Spring lookup failure should throw `IllegalStateException("Domain service not found: <fqcn>", cause)`
- resolved bean without implementation-class or superclass `@DomainService` should throw `IllegalStateException("Bean is not a domain service: <fqcn>")`
- valid inherited class annotations and Spring proxy instances should keep working

### SagaProcessSupervisor.sendProcess

Current:

```kotlin
fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendProcess(
    processCode: String,
    request: REQUEST
): RESPONSE?
```

Target:

```kotlin
fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendProcess(
    processCode: String,
    request: REQUEST
): RESPONSE
```

Reason:

- `RequestParam<RESPONSE>` already requires `RESPONSE : Any`
- synchronous request execution returns a non-null result
- "sub-process already executed" should return cached result
- "sub-process already executed but cached result missing" is broken saga state, not normal absence

Current implementation behavior:

```kotlin
if (sagaRecord.isSagaProcessExecuted(processCode)) {
    return sagaRecord.getSagaProcessResult(processCode)
}
```

Target behavior:

```kotlin
if (sagaRecord.isSagaProcessExecuted(processCode)) {
    return sagaRecord.getSagaProcessResult<RESPONSE>(processCode)
        ?: throw IllegalStateException("Saga process result missing: $processCode")
}
```

Exact exception message can be refined during implementation.

### SagaHandler.execProcess

Current:

```kotlin
fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execProcess(
    subCode: String,
    request: SUB_REQUEST
): SUB_RESPONSE?
```

Target:

```kotlin
fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execProcess(
    subCode: String,
    request: SUB_REQUEST
): SUB_RESPONSE
```

Reason:

- this delegates directly to `SagaProcessSupervisor.sendProcess`
- saga handler authors should not be forced to handle null for synchronous sub-process results
- missing cached results should fail fast inside the supervisor

## Stable Nullable APIs

The following nullable return types are correct and should remain nullable.

### Async Request Result

```kotlin
RequestSupervisor.result(requestId): R?
RequestRecord.getResult(): R?
```

Reason:

- asynchronous request may not be completed
- persisted request record may not contain a result yet

### Saga Result

```kotlin
SagaSupervisor.result(id): R?
SagaRecord.getResult(): R?
SagaRecord.getSagaProcessResult(processCode): R?
```

Reason:

- saga may not be completed
- saga sub-process may not have executed
- record-level accessors expose persisted state, not synchronous execution guarantees

Important distinction:

```kotlin
SagaRecord.getSagaProcessResult(processCode): R?
```

can remain nullable because it is a low-level record accessor.

```kotlin
SagaProcessSupervisor.sendProcess(...): R
```

should be non-null because it is a synchronous execution API with request result semantics.

### Aggregate Lookup By Id

Current:

```kotlin
AggregateSupervisor.getById(id): AGGREGATE?
AggregateSupervisor.removeById(id): AGGREGATE?
```

These may remain nullable in this stabilization slice because "id does not exist" is normal absence.

However, the names are not fully honest:

- `getById` sounds like it should return a value or throw
- nullable lookup is usually better named `findById`

This spec does not require renaming in the first implementation slice.

Recommended future direction:

```kotlin
fun findById(id: Id<AGGREGATE, *>): AGGREGATE?
fun getRequiredById(id: Id<AGGREGATE, *>): AGGREGATE
```

For this slice:

- keep current names if needed to reduce scope
- keep nullable return type
- document the semantics clearly

## Java Optional Boundary

Spring Data and other Java framework APIs may continue using `Optional<T>`.

Examples that should not be force-converted at the external API boundary:

```kotlin
springDataRepository.findById(id): Optional<T>
querydslPredicateExecutor.findOne(predicate): Optional<T>
```

cap4k implementation classes may call:

```kotlin
optional.orElse(null)
optional.isPresent
optional.get()
```

internally when adapting Java APIs.

But cap4k Kotlin public APIs should not expose Java `Optional<T>`.

Current-master boundary note:

- `ddd-application-request-jpa`, `ddd-domain-event-jpa`, `ddd-distributed-saga-jpa`, and `ddd-integration-event-http-jpa` use Spring Data `Optional` at repository boundaries; those usages are intentionally retained.
- `ddd-integration-event-http-jpa` may keep its Spring Data `findOne()` Optional handling because it is not implementing the cap4k `Repository` lookup contract.
- `ddd-domain-repo-jpa` and `ddd-domain-repo-jpa-querydsl` must adapt Spring Data / QueryDSL Optional values before returning through cap4k repository APIs.

## Call-Site Migration Rules

Repository and aggregate lookup call sites should move from Java Optional style to Kotlin nullable style.

Before:

```kotlin
val entity = repository.findOne(predicate)
if (entity.isPresent) {
    use(entity.get())
}
```

After:

```kotlin
val entity = repository.findOne(predicate)
if (entity != null) {
    use(entity)
}
```

Before:

```kotlin
val entity = repository.findFirst(predicate).orElse(defaultEntity)
```

After:

```kotlin
val entity = repository.findFirst(predicate) ?: defaultEntity
```

Before:

```kotlin
val entity = repository.findOne(predicate).orElseThrow { DomainException("not found") }
```

After:

```kotlin
val entity = repository.findOne(predicate) ?: throw DomainException("not found")
```

Before:

```kotlin
repository.findOne(predicate).ifPresent { entity ->
    use(entity)
}
```

After:

```kotlin
repository.findOne(predicate)?.let { entity ->
    use(entity)
}
```

## Out Of Scope

The following are explicitly deferred:

- `Aggregate.Default(payload: Any?)` constructor typing
- `RequestRecord.endRequest(result: Any)` generic typing
- `SagaRecord.endSagaProcess(result: Any)` generic typing
- `SagaRecord.endSaga(result: Any)` generic typing
- `EventRecord.init(payload: Any, ...)` generic typing
- serializer boundary redesign
- `RequestParam<RESULT : Any>` change
- contract-first query contract changes
- old `ListQuery` / `PageQuery` historical cleanup
- renaming `AggregateSupervisor.getById` / `removeById`
- adding `findById` / `getRequiredById`
- changing Spring Data repository method signatures
- legacy `cap4k-plugin-codegen` cleanup

## Affected Areas

Expected implementation impact:

- `ddd-core`
  - `Repository`
  - `RepositorySupervisor`
  - `AggregateSupervisor`
  - `DomainServiceSupervisor`
  - `SagaProcessSupervisor`
  - `SagaHandler`
  - `DefaultMediator`
  - `DefaultDomainServiceSupervisor`
  - `DefaultSagaSupervisor`

- `ddd-domain-repo-jpa`
  - `AbstractJpaRepository`
  - `DefaultRepositorySupervisor`
  - `DefaultAggregateSupervisor`
  - `JpaQueryUtils`
  - related tests

- `ddd-domain-repo-jpa-querydsl`
  - `AbstractQuerydslRepository`
  - related tests

- current-master Spring Data boundary modules
  - no public contract changes required
  - compile/test only if implementation changes expose incompatible signatures

- tests
  - update Optional assertions
  - update domain service null-return tests into exception tests
  - update saga process tests if present or add coverage for cached missing result failure

## Acceptance Criteria

Public API:

- `Repository.findOne/findFirst` return nullable entity types, not `Optional`.
- `RepositorySupervisor.findOne/findFirst` return nullable entity types, not `Optional`.
- `AggregateSupervisor.findOne/findFirst` return nullable aggregate types, not `Optional`.
- `JpaQueryUtils.queryFirst` returns nullable result type, not `Optional`.
- `DomainServiceSupervisor.getService` returns non-null service type.
- `SagaProcessSupervisor.sendProcess` returns non-null response type.
- `SagaHandler.execProcess` returns non-null sub-response type.
- `RequestParam<RESULT : Any>` remains unchanged.

Implementation:

- Spring Data / QueryDSL Optional values are adapted inside implementation classes.
- Missing domain service fails fast with contextual exception.
- Bean without `@DomainService` fails fast with contextual exception.
- Already-executed saga process returns cached result.
- Already-executed saga process with missing cached result throws.

Tests:

- repository tests assert `null` / non-null directly.
- aggregate supervisor tests assert `null` / non-null directly.
- `JpaQueryUtils` tests assert `null` / non-null directly.
- domain service supervisor tests expect exceptions for invalid lookup.
- saga process behavior is covered for cached result and missing cached result.

Verification:

- `:ddd-core:test` passes.
- `:ddd-domain-repo-jpa:test` passes.
- `:ddd-domain-repo-jpa-querydsl:test` passes.
- If downstream Spring Data boundary modules are touched by compilation fallout, run their targeted tests as well. Otherwise, leave their Optional boundary behavior unchanged.

## Risks

### Risk: Breaking Existing Call Sites

This is a breaking API cleanup. Java Optional call sites must migrate to Kotlin nullable handling.

This is accepted because the goal is framework contract stabilization, not compatibility preservation.

### Risk: Domain Service Lookup Throws More Often

Some tests or callers may currently rely on `null` to mean "not a domain service".

This should be treated as a bug in the caller or test. Domain service lookup is not a feature probe. It is a runtime service retrieval API.

### Risk: Saga Cached Result Semantics Expose Corrupt Records

Changing `sendProcess` to non-null may expose persisted saga records where a process is marked executed but has no result.

This is desirable. The old nullable API hid corrupt state.

### Risk: `getById` Naming Remains Imperfect

`AggregateSupervisor.getById(id): AGGREGATE?` remains a naming smell.

This spec records the smell but defers renaming to avoid mixing nullability stabilization with public method rename design.

## Final Decision

ddd-core public APIs should follow Kotlin nullability semantics consistently.

The first stabilization slice will:

- remove Java `Optional<T>` from cap4k Kotlin public lookup APIs
- represent normal lookup absence with `T?`
- make domain service lookup non-null and fail-fast
- make saga sub-process execution non-null and fail-fast on broken cached state
- preserve nullable result accessors where "not ready" or "not executed" is a valid state

This makes the framework stricter where it should be strict, and nullable only where absence is a real part of the domain semantics.
