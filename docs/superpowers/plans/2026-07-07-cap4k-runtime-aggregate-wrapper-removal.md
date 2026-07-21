# Cap4k Runtime Aggregate Wrapper Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the obsolete runtime aggregate wrapper API while preserving aggregate factories, behavior files, entity repositories, UoW, and domain events.

**Architecture:** This is a layered deletion: remove the wrapper API from `ddd-core`, then remove the JPA wrapper implementation and compatibility branches, then update starter wiring and peripheral bridges. The active model remains entity-based repositories plus `AggregateFactorySupervisor` creation through `Mediator.factories`.

**Tech Stack:** Kotlin, Gradle multi-module build, Spring Boot auto-configuration, JPA, MockK/JUnit tests, cap4k compiler and pipeline plugins.

---

## Scope Check

This is one dependency chain, not independent subsystem work. `ddd-core` owns the public wrapper API; `ddd-domain-repo-jpa` depends on it for wrapper predicates and UoW compatibility; starter, Querydsl, and the compiler plugin expose smaller dependent surfaces.

Compilation, test execution, and commits are listed for handoff. Under this workspace rule, the agent must not run compile/test/install or modify the git index unless the user explicitly authorizes it.

## File Structure

Core API and facade:

- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Aggregate.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregatePredicate.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisorSupport.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Id.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/X.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisorTest.kt`

JPA runtime:

- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicate.kt`
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupport.kt`
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Delete wrapper-only JPA tests under `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/**`
- Modify JPA tests for UoW, `JpaPredicate`, and `DefaultRepositorySupervisor`

Starter and peripheral bridges:

- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Modify starter initialization tests that reference `AggregateSupervisor`
- Modify: `ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/QuerydslPredicate.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`

`Id.kt` is included because static search shows it is only referenced from the wrapper supervisor path. Do not delete `AggregateFactory.kt`, `AggregatePayload.kt`, `AggregateElement.kt`, `AggregateFactorySupervisor.kt`, `AggregateFactorySupervisorSupport.kt`, or `DefaultAggregateFactorySupervisor.kt`.

---

### Task 1: Remove Core Wrapper API And Mediator Facade

**Files:**
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Aggregate.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregatePredicate.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisorSupport.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Id.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/X.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisorTest.kt`

- [ ] **Step 1: Update core event tests away from wrapper aggregates**

In `DefaultDomainEventSupervisorTest.kt`, remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
```

Delete the test named `should handle aggregate root entities`. Delete this helper class:

```kotlin
class TestAggregate(private val id: String) : Aggregate.Default<TestEntity>()
```

Keep the direct entity helper:

```kotlin
data class TestEntity(val id: String)
```

- [ ] **Step 2: Remove wrapper unwrapping from `DefaultDomainEventSupervisor`**

Remove the `Aggregate` import and delete:

```kotlin
private fun unwrapEntity(entity: Any): Any = (entity as? Aggregate<*>)?._unwrap() ?: entity
```

Use direct entity identity in all attachment paths:

```kotlin
EventRuntimeContext.currentOrCreateAmbient()
    .attachDomain(entity, EventAttachment.eager(domainEventPayload, schedule))
domainEventInterceptorManager.orderedDomainEventInterceptors
    .forEach { interceptor -> interceptor.onAttach(domainEventPayload, entity, schedule) }
```

```kotlin
EventRuntimeContext.currentOrCreateAmbient()
    .attachDomain(entity, EventAttachment.lazy(schedule, domainEventPayloadSupplier))
```

```kotlin
val eventPayloads = domainAttachments[entity] ?: return
```

```kotlin
domainEventInterceptorManager.orderedDomainEventInterceptors
    .forEach { interceptor -> interceptor.onDetach(domainEventPayload, entity) }
```

```kotlin
attachments.addAll(popEvents(entity))
```

- [ ] **Step 3: Remove wrapper facade from `Mediator.kt` and `X.kt`**

`Mediator.kt` must no longer import or extend `AggregateSupervisor`. Remove `aggregateSupervisor`, `aggregates`, and `agg`. Keep this factory surface:

```kotlin
interface Mediator : AggregateFactorySupervisor,
    RepositorySupervisor,
    DomainServiceSupervisor,
    UnitOfWork,
    IntegrationEventSupervisor,
    RequestSupervisor {

    val aggregateFactorySupervisor: AggregateFactorySupervisor
        get() = AggregateFactorySupervisor.instance

    companion object {
        @JvmStatic
        val factories: AggregateFactorySupervisor by lazy { AggregateFactorySupervisor.instance }

        @JvmStatic
        val fac: AggregateFactorySupervisor by lazy { factories }
    }
}
```

`X.kt` must no longer import `AggregateSupervisor`. Remove `aggregates` and `agg`. Keep:

```kotlin
val factories: AggregateFactorySupervisor by lazy { Mediator.factories }
val fac: AggregateFactorySupervisor by lazy { Mediator.fac }
```

- [ ] **Step 4: Remove aggregate supervisor delegation from `DefaultMediator`**

Replace:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.*
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
```

Delete the full section starting at:

```kotlin
// AggregateSupervisor methods
```

Keep:

```kotlin
override fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY =
    AggregateFactorySupervisor.instance.create(entityPayload)
```

- [ ] **Step 5: Delete obsolete core wrapper files**

```powershell
Remove-Item -LiteralPath "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Aggregate.kt"
Remove-Item -LiteralPath "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregatePredicate.kt"
Remove-Item -LiteralPath "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt"
Remove-Item -LiteralPath "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisorSupport.kt"
Remove-Item -LiteralPath "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Id.kt"
```

- [ ] **Step 6: Static-check core references**

```powershell
rg -n "AggregateSupervisor|AggregatePredicate|Aggregate\.Default|_wrap|_unwrap|\bId<" ddd-core/src/main ddd-core/src/test
rg -n "AggregateFactory|AggregatePayload|AggregateElement|Mediator\.factories|Mediator\.fac" ddd-core/src/main ddd-core/src/test
```

Expected: first command has no matches; second command keeps active factory/payload matches.

- [ ] **Step 7: User/CI core test command**

```powershell
./gradlew :ddd-core:test
```

Expected when run by user/CI: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit checkpoint if commits are authorized**

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/X.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisorTest.kt
git add -u ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate
git commit -m "refactor(core): remove runtime aggregate wrapper api"
```
### Task 2: Remove JPA Wrapper Predicate And UoW Compatibility

**Files:**
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicate.kt`
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupport.kt`
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Delete: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateTest.kt`
- Delete: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupportTest.kt`
- Delete: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`

- [ ] **Step 1: Replace `JpaPredicate.kt` with entity-only predicates**

Replace `JpaPredicate.kt` with:

```kotlin
package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import org.springframework.data.jpa.domain.Specification

class JpaPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val spec: Specification<ENTITY>? = null,
    val ids: Iterable<Any>? = null,
    val valueObject: ValueObject<*>? = null
) : Predicate<ENTITY> {

    companion object {
        @JvmStatic
        fun <ENTITY : Any> byId(entityClass: Class<ENTITY>, id: Any): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = listOf(id))

        @JvmStatic
        fun <ENTITY : Any> byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = ids)

        @JvmStatic
        fun <VALUE_OBJECT : ValueObject<*>> byValueObject(valueObject: VALUE_OBJECT): JpaPredicate<VALUE_OBJECT> =
            JpaPredicate(valueObject.javaClass, ids = listOf(valueObject.hash()), valueObject = valueObject)

        @JvmStatic
        fun <ENTITY : Any> bySpecification(
            entityClass: Class<ENTITY>,
            specification: Specification<ENTITY>
        ): JpaPredicate<ENTITY> = JpaPredicate(entityClass, specification)
    }
}
```

- [ ] **Step 2: Simplify repository predicate class resolution**

In `DefaultRepositorySupervisor.kt`, remove imports of `JpaAggregatePredicate` and `JpaAggregatePredicateSupport`. Replace:

```kotlin
val predicateClass = when (predicate) {
    is JpaAggregatePredicate<*, *> -> JpaAggregatePredicateSupport.getPredicate(predicate).javaClass
    else -> predicate.javaClass
}
```

with:

```kotlin
val predicateClass = predicate.javaClass
```

- [ ] **Step 3: Remove wrapper state from `JpaUnitOfWork`**

Remove:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
```

Delete:

```kotlin
private val wrapperMapThreadLocal = ThreadLocal.withInitial { HashMap<Any, Aggregate<*>>() }
```

Remove this line from `reset()`:

```kotlin
wrapperMapThreadLocal.remove()
```

Delete `unwrapEntity` and `updateWrappedEntity`. Replace the public entity collection methods with:

```kotlin
override fun persist(entity: Any) {
    if (isValueObjectAndExists(entity)) return
    persistEntitiesThreadLocal.get().add(entity)
}

override fun persistIfNotExist(entity: Any): Boolean {
    if (isExists(entity)) return false
    return persistEntitiesThreadLocal.get().add(entity)
}

override fun remove(entity: Any) {
    removeEntitiesThreadLocal.get().add(entity)
}
```

Replace merge calls that update wrappers:

```kotlin
entityManager.merge(entity).also { merged -> updateWrappedEntity(entity, merged) }
```

with:

```kotlin
entityManager.merge(entity)
```

Replace the delete merge block with:

```kotlin
entityManager.merge(entity).also { merged ->
    entityManager.remove(merged)
}
```

- [ ] **Step 4: Delete JPA wrapper implementation files**

```powershell
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicate.kt"
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupport.kt"
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt"
```

- [ ] **Step 5: Delete wrapper-only JPA tests**

```powershell
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateTest.kt"
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupportTest.kt"
Remove-Item -LiteralPath "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt"
```

- [ ] **Step 6: Remove wrapper cases from JPA tests**

In `JpaUnitOfWorkTest.kt`, remove the `Aggregate` import, delete `testPersistAggregateUnwrapping`, delete `testAggregateWrappingUnwrapping`, and delete the helper class `TestAggregate`. Keep direct entity, value-object, application-side ID, transaction propagation, and listener tests.

In `JpaPredicateTest.kt`, remove imports for wrapper aggregate types and delete tests whose names mention `toAggregatePredicate`. Keep tests for:

```kotlin
JpaPredicate.byId(TestEntity::class.java, id)
JpaPredicate.byIds(TestEntity::class.java, ids)
JpaPredicate.byValueObject(valueObject)
JpaPredicate.bySpecification(TestEntity::class.java, specification)
```

In `DefaultRepositorySupervisorTest.kt`, remove imports of `JpaAggregatePredicate` and `JpaAggregatePredicateSupport`, then delete the test with display name `应该正确处理JpaAggregatePredicate`.

- [ ] **Step 7: Static-check JPA wrapper references**

```powershell
rg -n "JpaAggregatePredicate|DefaultAggregateSupervisor|AggregatePredicate|AggregateSupervisor|Aggregate\.Default|_wrap|_unwrap|wrapperMapThreadLocal|unwrapEntity|updateWrappedEntity|toAggregatePredicate" ddd-domain-repo-jpa/src/main ddd-domain-repo-jpa/src/test
rg -n "JpaUnitOfWork|DefaultRepositorySupervisor|JpaPredicate|ValueObject" ddd-domain-repo-jpa/src/main ddd-domain-repo-jpa/src/test
```

Expected: first command has no matches; second command keeps active UoW, repository, predicate, and value-object matches.

- [ ] **Step 8: User/CI JPA test command**

```powershell
./gradlew :ddd-domain-repo-jpa:test
```

Expected when run by user/CI: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit checkpoint if commits are authorized**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateTest.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt
git add -u ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate
git commit -m "refactor(jpa): remove aggregate wrapper repository support"
```
### Task 3: Remove Starter Aggregate Supervisor Wiring

**Files:**
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/LazyInitializationFixTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CoreInitializationTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/ComprehensiveInitializationTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanDependencyTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanLifecycleTest.kt`

- [ ] **Step 1: Replace aggregate imports in auto-configuration**

In `JpaRepositoryAutoConfiguration.kt`, replace:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.*
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import com.only4.cap4k.ddd.core.domain.aggregate.SpecificationManager
```

Remove:

```kotlin
import com.only4.cap4k.ddd.domain.aggregate.impl.DefaultAggregateSupervisor
```

- [ ] **Step 2: Delete the old aggregate supervisor bean**

Delete this bean:

```kotlin
@Bean
fun defaultAggregateSupervisor(
    repositorySupervisor: DefaultRepositorySupervisor,
    jpaUnitOfWork: JpaUnitOfWork,
): DefaultAggregateSupervisor = DefaultAggregateSupervisor(
    repositorySupervisor,
    jpaUnitOfWork
).also {
    AggregateSupervisorSupport.configure(it)
}
```

Keep this factory supervisor bean:

```kotlin
@Bean
fun defaultAggregateFactorySupervisor(
    factories: List<AggregateFactory<*, *>>,
    jpaUnitOfWork: JpaUnitOfWork,
): DefaultAggregateFactorySupervisor = DefaultAggregateFactorySupervisor(
    factories,
    jpaUnitOfWork
).apply {
    init()
    AggregateFactorySupervisorSupport.configure(this)
}
```

- [ ] **Step 3: Update starter tests to protect factory supervisor instead of wrapper supervisor**

In `LazyInitializationFixTest.kt`, replace `AggregateSupervisor` import with:

```kotlin
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
```

Replace the aggregate supervisor lazy-init test with a factory supervisor version:

```kotlin
@Test
@DisplayName("验证AggregateFactorySupervisor延迟初始化正常工作")
fun testAggregateFactorySupervisorLazyInitialization() {
    try {
        val supervisor = AggregateFactorySupervisor.instance
        assertNotNull(supervisor, "AggregateFactorySupervisor instance should not be null")
    } catch (e: kotlin.UninitializedPropertyAccessException) {
        if (e.message?.contains("AggregateFactorySupervisorSupport") == true ||
            e.stackTrace.any { it.className.contains("AggregateFactorySupervisorSupport") }
        ) {
            println("Factory supervisor lazy initialization works but support is not configured: ${e.message}")
        } else {
            throw AssertionError("延迟初始化修复失败 - 仍然存在其他UninitializedPropertyAccessException", e)
        }
    } catch (e: Exception) {
        println("Factory supervisor lazy initialization works but support is not configured: ${e.message}")
    }
}
```

In `CoreInitializationTest.kt`, replace the import with `AggregateFactorySupervisor` and replace:

```kotlin
val aggregateSupervisor = AggregateSupervisor.instance
val repositorySupervisor = RepositorySupervisor.instance
```

with:

```kotlin
val aggregateFactorySupervisor = AggregateFactorySupervisor.instance
val repositorySupervisor = RepositorySupervisor.instance
```

In `ComprehensiveInitializationTest.kt`, replace the import with `AggregateFactorySupervisor` and replace:

```kotlin
val aggregateSupervisor = AggregateSupervisor.instance
println("✓ AggregateSupervisor延迟初始化工作正常")
```

with:

```kotlin
val aggregateFactorySupervisor = AggregateFactorySupervisor.instance
println("Factory supervisor lazy initialization works")
```

- [ ] **Step 4: Update bean-name lists**

In `BeanDependencyTest.kt`, replace:

```kotlin
"defaultAggregateSupervisor",
```

with:

```kotlin
"defaultAggregateFactorySupervisor",
```

In `BeanLifecycleTest.kt`, replace:

```kotlin
val beanNames = listOf("defaultMediator", "defaultRepositorySupervisor", "defaultAggregateSupervisor")
```

with:

```kotlin
val beanNames = listOf("defaultMediator", "defaultRepositorySupervisor", "defaultAggregateFactorySupervisor")
```

- [ ] **Step 5: Static-check starter wrapper wiring**

```powershell
rg -n "DefaultAggregateSupervisor|AggregateSupervisor|AggregateSupervisorSupport|defaultAggregateSupervisor" cap4k-ddd-starter/src/main cap4k-ddd-starter/src/test
rg -n "DefaultAggregateFactorySupervisor|AggregateFactorySupervisor|defaultAggregateFactorySupervisor" cap4k-ddd-starter/src/main cap4k-ddd-starter/src/test
```

Expected: first command has no matches; second command keeps active factory supervisor matches.

- [ ] **Step 6: User/CI starter test command**

```powershell
./gradlew :cap4k-ddd-starter:test
```

Expected when run by user/CI: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit checkpoint if commits are authorized**

```powershell
git add cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/LazyInitializationFixTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/CoreInitializationTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/ComprehensiveInitializationTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanDependencyTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/BeanLifecycleTest.kt
git commit -m "refactor(starter): stop wiring aggregate wrapper supervisor"
```
### Task 4: Remove Querydsl Aggregate Predicate Bridge

**Files:**
- Modify: `ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/QuerydslPredicate.kt`

- [ ] **Step 1: Replace `QuerydslPredicate.kt` without the aggregate bridge**

Replace the file with:

```kotlin
package com.only4.cap4k.ddd.domain.repo.querydsl

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Predicate as DomainPredicate

class QuerydslPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val predicate: BooleanBuilder = BooleanBuilder()
) : DomainPredicate<ENTITY> {

    val orderSpecifiers: MutableList<OrderSpecifier<*>> = mutableListOf()

    fun where(filter: Predicate): QuerydslPredicate<ENTITY> = apply {
        predicate.and(filter)
    }

    fun orWhere(filter: Predicate): QuerydslPredicate<ENTITY> = apply {
        predicate.or(filter)
    }

    fun orderBy(vararg orderSpecifiers: OrderSpecifier<*>): QuerydslPredicate<ENTITY> = apply {
        this.orderSpecifiers.addAll(orderSpecifiers)
    }

    companion object {
        @JvmStatic
        fun <ENTITY : Any> of(entityClass: Class<ENTITY>): QuerydslPredicate<ENTITY> =
            QuerydslPredicate(entityClass)

        @JvmStatic
        fun <ENTITY : Any> byPredicate(entityClass: Class<ENTITY>, predicate: Predicate): QuerydslPredicate<ENTITY> =
            QuerydslPredicate(entityClass, BooleanBuilder(predicate))
    }
}
```

- [ ] **Step 2: Static-check Querydsl wrapper references**

```powershell
rg -n "Aggregate|AggregatePredicate|JpaAggregatePredicate|toAggregatePredicate|_wrap|_unwrap" ddd-domain-repo-jpa-querydsl/src/main ddd-domain-repo-jpa-querydsl/src/test
```

Expected: no matches.

- [ ] **Step 3: User/CI Querydsl test command**

```powershell
./gradlew :ddd-domain-repo-jpa-querydsl:test
```

Expected when run by user/CI: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit checkpoint if commits are authorized**

```powershell
git add ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/QuerydslPredicate.kt
git commit -m "refactor(querydsl): remove aggregate predicate bridge"
```

---

### Task 5: Remove Compiler Plugin AggregatePredicate Resolution

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`

- [ ] **Step 1: Remove the aggregate predicate FQ name**

Delete:

```kotlin
private val aggregatePredicateFq = FqName("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate")
```

Keep:

```kotlin
private val predicateFq = FqName("com.only4.cap4k.ddd.core.domain.repo.Predicate")
```

- [ ] **Step 2: Replace predicate type resolution with entity-predicate-only logic**

Replace `resolveAggregateFromPredicateType` with:

```kotlin
private fun resolveAggregateFromPredicateType(type: IrType): String? {
    val simple = type as? IrSimpleType ?: return null
    val cls = simple.classifier?.owner as? IrClass ?: return null
    val fq = cls.fqNameWhenAvailable?.asString()
    return when {
        fq == predicateFq.asString() -> {
            val arg = simple.arguments.getOrNull(0) as? org.jetbrains.kotlin.ir.types.IrTypeProjection
            arg?.type?.let { resolveAggregateRootFromType(it) }
        }
        cls.isOrImplements(predicateFq) -> {
            val directArg = (simple.arguments.getOrNull(0) as? org.jetbrains.kotlin.ir.types.IrTypeProjection)?.type
            val superArg = cls.findSuperTypeArgument(predicateFq, 0)
            (directArg ?: superArg)?.let { resolveAggregateRootFromType(it) }
        }
        else -> null
    }
}
```

- [ ] **Step 3: Static-check compiler plugin wrapper references**

```powershell
rg -n "AggregatePredicate|aggregatePredicateFq" cap4k-plugin-code-analysis-compiler/src/main cap4k-plugin-code-analysis-compiler/src/test
```

Expected: no matches.

- [ ] **Step 4: User/CI compiler plugin test command**

```powershell
./gradlew :cap4k-plugin-code-analysis-compiler:test
```

Expected when run by user/CI: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit checkpoint if commits are authorized**

```powershell
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt
git commit -m "refactor(code-analysis): remove aggregate predicate resolution"
```

---

### Task 6: Final Static Sweep And Reference-Project Guard

**Files:**
- Inspect: all modified modules
- Inspect: `cap4k-reference-content-studio/**`
- Modify only if needed: active docs outside `docs/superpowers/**`

- [ ] **Step 1: Confirm no Kotlin wrapper references remain**

From workspace root `C:\Users\LD_moxeii\Documents\code\only-workspace`:

```powershell
rg -n "AggregateSupervisor|AggregatePredicate|JpaAggregatePredicate|DefaultAggregateSupervisor|AggregateSupervisorSupport|JpaAggregatePredicateSupport|Aggregate\.Default|_wrap|_unwrap|wrapperMapThreadLocal|unwrapEntity|updateWrappedEntity|toAggregatePredicate|Mediator\.aggregates|Mediator\.agg|X\.aggregates|X\.agg|\bId<" cap4k -g "*.kt"
```

Expected: no Kotlin matches.

- [ ] **Step 2: Confirm required factory surfaces remain**

```powershell
rg -n "AggregateFactory|AggregatePayload|AggregateElement|AggregateFactorySupervisor|DefaultAggregateFactorySupervisor|Mediator\.factories|Mediator\.fac" cap4k -g "*.kt"
```

Expected: active matches remain in `ddd-core`, starter wiring, generator/template tests, and reference-style usage.

- [ ] **Step 3: Confirm reference project still uses the current model**

```powershell
rg -n "Mediator\.factories|Mediator\.repositories|Mediator\.uow|AggregateFactory|AggregatePayload|AggregateElement|Behavior" cap4k-reference-content-studio -g "*.kt"
rg -n "Mediator\.aggregates|Mediator\.agg|AggregatePredicate|Aggregate\.Default|_wrap|_unwrap|toAggregatePredicate" cap4k-reference-content-studio -g "*.kt"
```

Expected: first command has active matches; second command has no matches.

- [ ] **Step 4: Confirm active docs do not advertise wrapper as current API**

```powershell
rg -n "Mediator\.aggregates|Mediator\.agg|AggregatePredicate|AggregateSupervisor|Aggregate\.Default|_wrap|_unwrap|toAggregatePredicate" cap4k/docs -g "*.md" -g "!superpowers/specs/**" -g "!superpowers/plans/**"
```

Expected: no active-doc matches. Historical specs and plans under `docs/superpowers/**` can mention wrapper as removal context.

- [ ] **Step 5: Inspect diff scope**

```powershell
git diff --stat
git diff --name-only
```

Expected changed paths are limited to files listed in this plan, plus directly required active-doc edits if Step 4 found active documentation drift.

- [ ] **Step 6: User/CI affected-module verification**

```powershell
./gradlew :ddd-core:test
./gradlew :ddd-domain-repo-jpa:test
./gradlew :cap4k-ddd-starter:test
./gradlew :ddd-domain-repo-jpa-querydsl:test
./gradlew :cap4k-plugin-code-analysis-compiler:test
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected when run by user/CI: all listed tasks report `BUILD SUCCESSFUL`.

- [ ] **Step 7: Final commit if commits are authorized**

```powershell
git status --short
git add docs/superpowers/specs/2026-07-07-cap4k-runtime-aggregate-wrapper-removal-design.md docs/superpowers/plans/2026-07-07-cap4k-runtime-aggregate-wrapper-removal.md
git commit -m "docs: plan runtime aggregate wrapper removal"
```

If implementation commits were already made task-by-task, use this final commit only for the spec and plan documents.

## Completion Criteria

This plan is complete when:

- production Kotlin source no longer references the old wrapper runtime API
- tests no longer import or assert the deleted wrapper API
- `AggregateFactory`, `AggregatePayload`, `AggregateElement`, `AggregateFactorySupervisor`, `DefaultAggregateFactorySupervisor`, `Mediator.factories`, and `Mediator.fac` remain present
- `DefaultDomainEventSupervisor` uses direct entity identity
- `JpaUnitOfWork` persists and removes direct entity arguments without wrapper maps
- starter wiring registers `defaultAggregateFactorySupervisor` but not `defaultAggregateSupervisor`
- Querydsl no longer exposes `toAggregatePredicate`
- the compiler plugin resolves repository `Predicate<ENTITY>` but not aggregate-wrapper predicates
- static sweep commands match the expected results
- user/CI verification commands pass when run outside the static-only agent constraint