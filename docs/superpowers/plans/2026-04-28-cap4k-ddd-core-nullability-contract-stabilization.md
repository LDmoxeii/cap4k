# ddd-core Nullability Contract Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize ddd-core Kotlin nullability contracts by removing Java `Optional<T>` from cap4k public lookup APIs, making invariant failures fail fast, and preserving nullable APIs only where absence is real state.

**Architecture:** Keep Java `Optional` at Spring Data / QueryDSL boundaries, adapt it inside repository implementations, and expose Kotlin `T?` from cap4k lookup APIs. Tighten domain service and saga sub-process execution APIs to non-null return types with contextual `IllegalStateException` for broken framework state.

**Tech Stack:** Kotlin, Gradle, JUnit 5, MockK, Spring Data JPA, QueryDSL.

---

## Current-Master Scope

- Spec: `docs/superpowers/specs/2026-04-27-cap4k-ddd-core-nullability-contract-stabilization-design.md`
- Historical analysis: `docs/design/ddd-core-nullability/analysis.md`
- Do not touch `cap4k-plugin-codegen`.
- Do not mix in `Any` serialization-boundary cleanup.
- Keep Spring Data repository `Optional` boundaries in request/event/saga/http persistence modules.
- Use small patches; avoid long Windows command lines.

## File Map

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/service/DomainServiceSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/service/impl/DefaultDomainServiceSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaProcessSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaHandler.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/service/impl/DefaultDomainServiceSupervisorTest.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaQueryUtils.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepositoryTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaQueryUtilsTest.kt`
- Modify: `ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/AbstractQuerydslRepository.kt`
- Modify: `ddd-domain-repo-jpa-querydsl/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/AbstractQuerydslRepositoryTest.kt`

### Task 1: ddd-core Fail-Fast APIs

**Files:**
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/service/impl/DefaultDomainServiceSupervisorTest.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisorTest.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/service/DomainServiceSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/service/impl/DefaultDomainServiceSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaProcessSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/SagaHandler.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`

- [x] **Step 1: Write failing domain service tests**

Update `DefaultDomainServiceSupervisorTest` so missing beans, Spring lookup failures, and resolved beans without implementation-class/superclass `@DomainService` expect `IllegalStateException`.

```kotlin
val ex = assertThrows<IllegalStateException> {
    supervisor.getService(NonExistentService::class.java)
}
assertEquals("Domain service not found: ${NonExistentService::class.java.name}", ex.message)
```

For the interface-only annotation case, keep the current rule:

```kotlin
val ex = assertThrows<IllegalStateException> {
    supervisor.getService(TestServiceImplementationWithoutAnnotation::class.java)
}
assertEquals("Bean is not a domain service: ${TestServiceImplementationWithoutAnnotation::class.java.name}", ex.message)
```

- [x] **Step 2: Write failing saga process tests**

Add a sub-request and saga handler test path in `DefaultSagaSupervisorTest`:

```kotlin
data class TestSubRequest(val data: String) : RequestParam<String>

@Test
fun `send returns cached saga process result when process already executed`() {
    val handler = object : SagaHandler<TestSagaParam, String> {
        override fun exec(request: TestSagaParam): String = execProcess("sub", TestSubRequest("cached"))
    }
    val testSupervisor = newSupervisor(handler)
    every { mockSagaRecord.isSagaProcessExecuted("sub") } returns true
    every { mockSagaRecord.getSagaProcessResult<String>("sub") } returns "cached-result"

    val result = testSupervisor.send(testParam)

    assertEquals("cached-result", result)
}
```

Add the missing cached result case:

```kotlin
@Test
fun `send throws when executed saga process cached result is missing`() {
    val handler = object : SagaHandler<TestSagaParam, String> {
        override fun exec(request: TestSagaParam): String = execProcess("sub", TestSubRequest("missing"))
    }
    val testSupervisor = newSupervisor(handler)
    every { mockSagaRecord.isSagaProcessExecuted("sub") } returns true
    every { mockSagaRecord.getSagaProcessResult<String>("sub") } returns null

    val ex = assertThrows<IllegalStateException> {
        testSupervisor.send(testParam)
    }

    assertEquals("Saga process result missing: sub", ex.message)
}
```

- [x] **Step 3: Run ddd-core targeted tests and verify RED**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.service.impl.DefaultDomainServiceSupervisorTest" --tests "com.only4.cap4k.ddd.core.application.saga.impl.DefaultSagaSupervisorTest" --rerun-tasks
```

Expected: FAIL before implementation because `getService()` still returns null and saga `execProcess()` / `sendProcess()` still return nullable values.

- [x] **Step 4: Implement fail-fast signatures and behavior**

Change signatures:

```kotlin
fun <DOMAIN_SERVICE : Any> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE
fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendProcess(processCode: String, request: REQUEST): RESPONSE
fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execProcess(subCode: String, request: SUB_REQUEST): SUB_RESPONSE
```

Implement `DefaultDomainServiceSupervisor.getService()`:

```kotlin
val domainService = try {
    applicationContext.getBean(domainServiceClass)
} catch (e: Exception) {
    throw IllegalStateException("Domain service not found: ${domainServiceClass.name}", e)
}

if (!hasAnnotationRecursively(domainService.javaClass, DomainService::class.java)) {
    throw IllegalStateException("Bean is not a domain service: ${domainService.javaClass.name}")
}
return domainService
```

Implement cached saga process failure:

```kotlin
if (sagaRecord.isSagaProcessExecuted(processCode)) {
    return sagaRecord.getSagaProcessResult<RESPONSE>(processCode)
        ?: throw IllegalStateException("Saga process result missing: $processCode")
}
```

- [x] **Step 5: Run ddd-core targeted tests and verify GREEN**

Run the same targeted command. Expected: PASS.

### Task 2: Repository and Aggregate Lookup Nullability

**Files:**
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepositoryTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaQueryUtilsTest.kt`
- Modify: `ddd-domain-repo-jpa-querydsl/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/AbstractQuerydslRepositoryTest.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaQueryUtils.kt`
- Modify: `ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/AbstractQuerydslRepository.kt`

- [x] **Step 1: Update repository tests to nullable expectations**

Replace cap4k Optional assertions:

```kotlin
assertTrue(result.isPresent)
assertEquals(entity, result.get())
```

with:

```kotlin
assertEquals(entity, result)
```

Replace empty Optional assertions:

```kotlin
assertFalse(result.isPresent)
```

with:

```kotlin
assertNull(result)
```

Mocks of cap4k `Repository.findOne/findFirst` should return `entity` or `null`, not `Optional.of(...)` / `Optional.empty()`. Keep mocks of Spring Data `findById` and QueryDSL `findOne` returning `Optional`, because those are Java framework boundaries.

- [x] **Step 2: Update JpaQueryUtils tests to nullable expectations**

Change display names and assertions from Optional language to nullable language:

```kotlin
assertEquals(expectedEntities[0], result)
assertNull(result)
```

- [x] **Step 3: Run JPA/querydsl targeted tests and verify RED**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.AbstractJpaRepositoryTest" --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --tests "com.only4.cap4k.ddd.domain.aggregate.impl.DefaultAggregateSupervisorTest" --tests "com.only4.cap4k.ddd.domain.repo.JpaQueryUtilsTest" --rerun-tasks
.\gradlew.bat :ddd-domain-repo-jpa-querydsl:test --tests "com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepositoryTest" --rerun-tasks
```

Expected: FAIL before implementation because public lookup APIs still return `Optional`.

- [x] **Step 4: Change public lookup signatures**

Change cap4k public lookup APIs:

```kotlin
fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): ENTITY?
fun findFirst(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo> = emptyList(), persist: Boolean = true): ENTITY?
fun findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): ENTITY?
```

Apply equivalent changes to `RepositorySupervisor`, `AggregateSupervisor`, and `DefaultMediator`. Remove `java.util.*` imports where they are only needed for `Optional`.

- [x] **Step 5: Adapt JPA and QueryDSL implementations**

Use nullable values at the cap4k implementation boundary:

```kotlin
val entity = jpaRepository.findById(id).orElse(null)
if (!persist && entity != null) {
    entityManager.detach(entity)
}
return entity
```

For first-result queries:

```kotlin
val entity = page.content.firstOrNull()
```

For supervisors:

```kotlin
return repo(...).findOne(predicate, persist)
    ?.also { if (persist) unitOfWork.persist(it) }
```

For aggregate wrapping:

```kotlin
return repositorySupervisor.findOne(pred, persist)
    ?.let { entity -> newInstance(clazz, entity) }
```

For `JpaQueryUtils.queryFirst()`:

```kotlin
return results.firstOrNull()
```

- [x] **Step 6: Run JPA/querydsl targeted tests and verify GREEN**

Run the same two targeted commands. Expected: PASS.

### Task 3: Cross-Module Compile Fallout and Boundary Scan

**Files:**
- Modify only files required by compile fallout from Task 1 and Task 2.
- Do not modify `cap4k-plugin-codegen`.
- Do not modify Spring Data repository signatures.

- [x] **Step 1: Scan for remaining cap4k public Optional exposure**

Run:

```powershell
rg -n "Optional<|java\.util\.Optional|isPresent|ifPresent|orElse|\.get\(\)" ddd-core ddd-domain-repo-jpa ddd-domain-repo-jpa-querydsl -g "*.kt"
```

Expected after implementation: no `Optional<` in cap4k public lookup APIs. Remaining `Optional` usage should be Spring Data / QueryDSL boundary adaptation or tests that mock those external boundaries.

- [x] **Step 2: Compile affected downstream modules if needed**

If Task 1 or Task 2 changes expose compile errors outside the three target modules, update only direct call sites from Optional style to nullable style:

```kotlin
val entity = repository.findOne(predicate)
if (entity != null) {
    use(entity)
}
```

Do not rewrite independent Spring Data Optional usage such as:

```kotlin
springDataRepository.findById(id).orElseThrow { ... }
```

Result: no additional downstream source modules required edits.

- [x] **Step 3: Run targeted downstream tests only for touched modules**

If JPA request/event/saga/http persistence modules are touched, run the corresponding module tests:

```powershell
.\gradlew.bat :ddd-application-request-jpa:test --rerun-tasks
.\gradlew.bat :ddd-domain-event-jpa:test --rerun-tasks
.\gradlew.bat :ddd-distributed-saga-jpa:test --rerun-tasks
.\gradlew.bat :ddd-integration-event-http-jpa:test --rerun-tasks
```

Expected: PASS for every touched module. Skip these if those modules are not touched.

Result: skipped because no downstream Spring Data boundary modules were touched.

### Task 4: Required Verification

**Files:**
- No planned source edits.

- [x] **Step 1: Run ddd-core full tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 2: Run JPA repository tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 3: Run QueryDSL repository tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa-querydsl:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 4: Final boundary scan**

Run:

```powershell
rg -n "fun .*: Optional<|Optional<.*>" ddd-core ddd-domain-repo-jpa ddd-domain-repo-jpa-querydsl -g "*.kt"
```

Expected: no cap4k public API signatures return `Optional<T>`. Spring Data / QueryDSL boundary test stubs may still mention `Optional`.

- [x] **Step 5: Review diff**

Run:

```powershell
git diff -- ddd-core ddd-domain-repo-jpa ddd-domain-repo-jpa-querydsl docs/superpowers/specs/2026-04-27-cap4k-ddd-core-nullability-contract-stabilization-design.md docs/superpowers/plans/2026-04-28-cap4k-ddd-core-nullability-contract-stabilization.md
```

Expected: diff is limited to the approved nullability contract stabilization scope and docs.
