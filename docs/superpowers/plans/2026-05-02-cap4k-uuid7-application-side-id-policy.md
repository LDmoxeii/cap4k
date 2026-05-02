# Cap4k UUID7 Application-Side ID Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the aggregate ID-generation path with a single framework-level application-side ID policy whose default strategy is UUID7 and whose explicit Long strategy is `snowflake-long`.

**Architecture:** Runtime ID contracts live in `ddd-core`; built-in strategy wiring lives in `cap4k-ddd-starter`; JPA persistence integration lives in `ddd-domain-repo-jpa`. The pipeline resolves a concrete ID policy for each aggregate entity from Gradle DSL, validates it against the generated ID type, and renders `@ApplicationSideId` instead of Hibernate custom generator annotations for application-side IDs.

**Tech Stack:** Kotlin, Gradle plugin extension APIs, Spring Boot auto-configuration, JPA/Hibernate, Pebble templates, JUnit 5, MockK, Gradle TestKit.

---

## File Structure

Runtime core contracts:

- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt`: runtime annotation stored on generated ID fields.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt`: `APPLICATION_SIDE` / `DATABASE_SIDE` enum.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt`: strategy metadata and allocation contract.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt`: strategy lookup and map-backed registry.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt`: public allocation API.
- Test `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt`.

Built-in strategies and Spring wiring:

- Create `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt`: UUID7 allocator backed by `UuidCreator.getTimeOrderedEpoch()`.
- Create `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt`: Long allocator backed by `SnowflakeIdGenerator`.
- Create `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`: `IdStrategyRegistry` and `IdAllocator` beans.
- Modify `cap4k-ddd-starter/build.gradle.kts`: add `uuid-creator` implementation dependency if it is not already visible to the starter module.
- Test `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`.

JPA unit-of-work integration:

- Create `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`: reflection helper for ID assignment, graph traversal, and application-side new/existing classification.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`: assign IDs before interceptors and use application-side existence checks before `persist` / `merge`.
- Modify `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`: inject `IdStrategyRegistry` into `JpaUnitOfWork`.
- Test `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`.
- Test `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`.
- Test `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt`.

Pipeline API, DSL, and canonical model:

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`: add `AggregateIdPolicyConfig`.
- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`: replace `AggregateIdGeneratorControl` with `AggregateIdPolicyControl`.
- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`: add `generators.aggregate.idPolicy`.
- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`: copy DSL values into `ProjectConfig.aggregateIdPolicy`.
- Delete or stop using `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`.
- Create `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt`: strategy resolution and type validation.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`: call the new resolver.

Aggregate generator and renderer:

- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`: produce `applicationSideIdStrategy`, remove custom generator fields, and force ID default sentinels.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`: render `@ApplicationSideId` for application-side IDs and `@GeneratedValue(strategy = GenerationType.IDENTITY)` for database identity only.
- Update related tests in `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`.
- Update related tests in `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`.
- Update related tests in `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`.
- Update related tests in `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`.

Legacy DB-comment removal:

- Modify `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`: fail on `@IdGenerator` / `@IG`.
- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`: remove `DbTableSnapshot.entityIdGenerator`.
- Update parser, source, canonical, and plugin tests that currently assert `@IdGenerator=snowflakeIdGenerator`.

---

### Task 1: Add Runtime ID Core Contracts

**Files:**
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt`
- Test: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt`

- [ ] **Step 1: Write failing core tests**

Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt` with these tests:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class IdPolicyCoreTest {

    @Test
    fun `map backed registry returns registered strategy`() {
        val strategy = FixedUuidStrategy()
        val registry = MapBackedIdStrategyRegistry(listOf(strategy))

        assertSame(strategy, registry.get("fixed-uuid"))
    }

    @Test
    fun `registry rejects duplicate strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy(), FixedUuidStrategy()))
        }

        assertEquals("duplicate ID strategy: fixed-uuid", error.message)
    }

    @Test
    fun `allocator returns typed strategy value`() {
        val allocator = DefaultIdAllocator(MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy())))

        assertEquals(UUID(1L, 2L), allocator.next("fixed-uuid", UUID::class))
    }

    @Test
    fun `allocator rejects output type mismatch`() {
        val allocator = DefaultIdAllocator(MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy())))

        val error = assertThrows(IllegalArgumentException::class.java) {
            allocator.next("fixed-uuid", Long::class)
        }

        assertEquals("ID strategy fixed-uuid produces java.util.UUID, not kotlin.Long", error.message)
    }

    @Test
    fun `application side annotation exposes strategy name`() {
        val annotation = AnnotatedEntity::class.java.getDeclaredField("id")
            .getAnnotation(ApplicationSideId::class.java)

        assertEquals("fixed-uuid", annotation.strategy)
    }

    private class FixedUuidStrategy : IdStrategy {
        override val name: String = "fixed-uuid"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class AnnotatedEntity {
        @field:ApplicationSideId(strategy = "fixed-uuid")
        var id: UUID = UUID(0L, 0L)
    }
}
```

- [ ] **Step 2: Run failing core tests**

Run:

```powershell
.\gradlew :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.IdPolicyCoreTest"
```

Expected: compilation fails because `ApplicationSideId`, `IdStrategy`, `IdGenerationKind`, `MapBackedIdStrategyRegistry`, and `DefaultIdAllocator` do not exist.

- [ ] **Step 3: Add core runtime contracts**

Create `ApplicationSideId.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationSideId(
    val strategy: String
)
```

Create `IdGenerationKind.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

enum class IdGenerationKind {
    APPLICATION_SIDE,
    DATABASE_SIDE,
}
```

Create `IdStrategy.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdStrategy {
    val name: String
    val kind: IdGenerationKind
    val outputType: KClass<*>
    val preassignable: Boolean
    fun isDefaultValue(value: Any?): Boolean
    fun next(): Any
}
```

Create `IdStrategyRegistry.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

interface IdStrategyRegistry {
    fun get(name: String): IdStrategy
}

class MapBackedIdStrategyRegistry(
    strategies: Iterable<IdStrategy>
) : IdStrategyRegistry {
    private val strategiesByName: Map<String, IdStrategy> = strategies
        .fold(linkedMapOf<String, IdStrategy>()) { acc, strategy ->
            require(strategy.name.isNotBlank()) { "ID strategy name must not be blank" }
            require(strategy.name !in acc) { "duplicate ID strategy: ${strategy.name}" }
            acc[strategy.name] = strategy
            acc
        }

    override fun get(name: String): IdStrategy =
        strategiesByName[name] ?: throw IllegalArgumentException("unknown ID strategy: $name")
}
```

Create `IdAllocator.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaObjectType

interface IdAllocator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T
}

class DefaultIdAllocator(
    private val strategyRegistry: IdStrategyRegistry
) : IdAllocator {
    override fun <T : Any> next(strategy: String, type: KClass<T>): T {
        val resolved = strategyRegistry.get(strategy)
        require(resolved.kind == IdGenerationKind.APPLICATION_SIDE) {
            "ID strategy $strategy is not application-side"
        }
        require(type.javaObjectType.isAssignableFrom(resolved.outputType.javaObjectType)) {
            "ID strategy $strategy produces ${resolved.outputType.javaObjectType.name}, not ${type.qualifiedName}"
        }

        @Suppress("UNCHECKED_CAST")
        return resolved.next() as T
    }
}
```

- [ ] **Step 4: Run core tests**

Run:

```powershell
.\gradlew :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.IdPolicyCoreTest"
```

Expected: PASS.

- [ ] **Step 5: Commit runtime core contracts**

Run:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt
git commit -m "feat: add application-side id core contract"
```

### Task 2: Add Built-In UUID7 and Snowflake ID Strategies

**Files:**
- Modify: `cap4k-ddd-starter/build.gradle.kts`
- Create: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt`
- Create: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt`
- Create: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`
- Test: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`

- [ ] **Step 1: Write failing starter tests**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID

class IdPolicyAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)

    @Test
    fun `registry exposes uuid7 by default`() {
        contextRunner.run { context ->
            val registry = context.getBean(IdStrategyRegistry::class.java)
            val strategy = registry.get("uuid7")

            assertEquals(IdGenerationKind.APPLICATION_SIDE, strategy.kind)
            assertEquals(UUID::class, strategy.outputType)
            assertTrue(strategy.preassignable)
            assertTrue(strategy.isDefaultValue(UUID(0L, 0L)))
            assertFalse(strategy.isDefaultValue(strategy.next()))
        }
    }

    @Test
    fun `allocator returns uuid7 values without snowflake bean`() {
        contextRunner.run { context ->
            val allocator = context.getBean(IdAllocator::class.java)
            val first = allocator.next("uuid7", UUID::class)
            val second = allocator.next("uuid7", UUID::class)

            assertEquals(7, first.version())
            assertEquals(7, second.version())
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `registry exposes snowflake long when snowflake generator bean exists`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java) { SnowflakeIdGenerator(1L, 1L) }
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val allocator = context.getBean(IdAllocator::class.java)
                val id = allocator.next("snowflake-long", Long::class)

                assertTrue(id > 0L)
            }
    }
}
```

- [ ] **Step 2: Run failing starter tests**

Run:

```powershell
.\gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest"
```

Expected: compilation fails because the strategy and auto-configuration classes do not exist.

- [ ] **Step 3: Add built-in strategy classes**

Modify `cap4k-ddd-starter/build.gradle.kts` by adding:

```kotlin
implementation("com.github.f4b6a3:uuid-creator:6.1.1")
```

Create `Uuid7IdStrategy.kt`:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import java.util.UUID

class Uuid7IdStrategy : IdStrategy {
    override val name: String = "uuid7"
    override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
    override val outputType = UUID::class
    override val preassignable: Boolean = true

    override fun isDefaultValue(value: Any?): Boolean =
        value == null || value == UUID(0L, 0L)

    override fun next(): Any = UuidCreator.getTimeOrderedEpoch()
}
```

Create `SnowflakeLongIdStrategy.kt`:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator

class SnowflakeLongIdStrategy(
    private val snowflakeIdGenerator: SnowflakeIdGenerator
) : IdStrategy {
    override val name: String = "snowflake-long"
    override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
    override val outputType = Long::class
    override val preassignable: Boolean = true

    override fun isDefaultValue(value: Any?): Boolean =
        value == null || value == 0L

    override fun next(): Any = snowflakeIdGenerator.nextId()
}
```

Create `IdPolicyAutoConfiguration.kt`:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.DefaultIdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class IdPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun idStrategyRegistry(
        snowflakeIdGenerator: ObjectProvider<SnowflakeIdGenerator>
    ): IdStrategyRegistry {
        val strategies = mutableListOf<IdStrategy>(Uuid7IdStrategy())
        snowflakeIdGenerator.ifAvailable { strategies += SnowflakeLongIdStrategy(it) }
        return MapBackedIdStrategyRegistry(strategies)
    }

    @Bean
    @ConditionalOnMissingBean
    fun idAllocator(idStrategyRegistry: IdStrategyRegistry): IdAllocator =
        DefaultIdAllocator(idStrategyRegistry)
}
```

- [ ] **Step 4: Register auto-configuration if the module uses imports**

Check whether `cap4k-ddd-starter` currently has an auto-configuration import file:

```powershell
Get-ChildItem -Recurse cap4k-ddd-starter/src/main/resources
```

If `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` exists, add:

```text
com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfiguration
```

If the project still uses `spring.factories`, add the same class under `org.springframework.boot.autoconfigure.EnableAutoConfiguration`.

- [ ] **Step 5: Run starter tests**

Run:

```powershell
.\gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest"
```

Expected: PASS.

- [ ] **Step 6: Commit built-in strategies**

Run:

```powershell
git add cap4k-ddd-starter/build.gradle.kts cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt
git commit -m "feat: wire built-in id strategies"
```

### Task 3: Add JPA Application-Side ID Support

**Files:**
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`

- [ ] **Step 1: Write failing reflection support tests**

Create `JpaApplicationSideIdSupportTest.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class JpaApplicationSideIdSupportTest {

    private val support = JpaApplicationSideIdSupport(
        MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy()))
    )

    @Test
    fun `assigns default root id`() {
        val root = RootEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.id)
    }

    @Test
    fun `keeps preassigned root id`() {
        val assigned = UUID(9L, 9L)
        val root = RootEntity(id = assigned)

        support.assignMissingIds(root)

        assertEquals(assigned, root.id)
    }

    @Test
    fun `traverses owned one to many children`() {
        val root = RootEntity()
        root.children += ChildEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.children.single().id)
    }

    @Test
    fun `does not traverse many to one reverse navigation`() {
        val child = ChildWithParent()
        child.parent = RootEntity()

        support.assignMissingIds(child)

        assertEquals(UUID(1L, 2L), child.id)
        assertEquals(UUID(0L, 0L), child.parent!!.id)
    }

    @Test
    fun `rejects application side id combined with generated value`() {
        val error = assertThrows(IllegalStateException::class.java) {
            support.assignMissingIds(InvalidGeneratedEntity())
        }

        assertEquals(
            "Application-side ID field com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$InvalidGeneratedEntity.id must not also use @GeneratedValue",
            error.message
        )
    }

    @Test
    fun `finds no application side id on provider generated entity`() {
        assertNull(support.findApplicationSideId(ProviderGeneratedEntity()))
    }

    private class FixedUuidStrategy : IdStrategy {
        override val name: String = "uuid7"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class RootEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        val children: MutableList<ChildEntity> = mutableListOf()
    }

    private class ChildEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    )

    private class ChildWithParent(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @ManyToOne
        var parent: RootEntity? = null
    }

    private class ProviderGeneratedEntity {
        @Id
        @GeneratedValue
        var id: Long = 0L
    }

    private class InvalidGeneratedEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        @field:GeneratedValue
        var id: UUID = UUID(0L, 0L)
    }
}
```

- [ ] **Step 2: Run failing helper tests**

Run:

```powershell
.\gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest"
```

Expected: compilation fails because `JpaApplicationSideIdSupport` does not exist.

- [ ] **Step 3: Implement bounded application-side ID support**

Create `JpaApplicationSideIdSupport.kt` with this public shape:

```kotlin
package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import java.lang.reflect.Field

internal class JpaApplicationSideIdSupport(
    private val idStrategyRegistry: IdStrategyRegistry
) {

    fun assignMissingIds(root: Any) {
        assignMissingIds(root, linkedSetOf())
    }

    fun findApplicationSideId(entity: Any): ApplicationSideIdMember? =
        persistentFields(Hibernate.getClass(entity))
            .firstNotNullOfOrNull { field ->
                val annotation = field.getAnnotation(ApplicationSideId::class.java)
                    ?: findGetterAnnotation(Hibernate.getClass(entity), field.name)
                    ?: return@firstNotNullOfOrNull null
                validateNoGeneratedValue(field, entity)
                ApplicationSideIdMember(field, annotation)
            }

    fun isDefaultId(member: ApplicationSideIdMember, entity: Any): Boolean {
        val strategy = idStrategyRegistry.get(member.annotation.strategy)
        require(strategy.kind == IdGenerationKind.APPLICATION_SIDE) {
            "ID strategy ${strategy.name} is not application-side"
        }
        return strategy.isDefaultValue(member.get(entity))
    }

    private fun assignMissingIds(entity: Any, visited: MutableSet<Int>) {
        if (!visited.add(System.identityHashCode(entity))) return

        findApplicationSideId(entity)?.let { member ->
            val strategy = idStrategyRegistry.get(member.annotation.strategy)
            require(strategy.kind == IdGenerationKind.APPLICATION_SIDE) {
                "ID strategy ${strategy.name} is not application-side"
            }
            if (strategy.isDefaultValue(member.get(entity))) {
                member.set(entity, strategy.next())
            }
        }

        ownedRelationValues(entity).forEach { related ->
            when (related) {
                is Iterable<*> -> related.filterNotNull().forEach { assignMissingIds(it, visited) }
                else -> assignMissingIds(related, visited)
            }
        }
    }

    private fun ownedRelationValues(entity: Any): List<Any> =
        persistentFields(Hibernate.getClass(entity)).mapNotNull { field ->
            if (field.getAnnotation(ManyToOne::class.java) != null) return@mapNotNull null
            val oneToMany = field.getAnnotation(OneToMany::class.java)
            val oneToOne = field.getAnnotation(OneToOne::class.java)
            val cascades = oneToMany?.cascade?.toSet() ?: oneToOne?.cascade?.toSet() ?: return@mapNotNull null
            if (CascadeType.ALL !in cascades && CascadeType.PERSIST !in cascades && CascadeType.MERGE !in cascades) {
                return@mapNotNull null
            }
            field.isAccessible = true
            field.get(entity)
        }

    private fun validateNoGeneratedValue(field: Field, entity: Any) {
        val owner = Hibernate.getClass(entity)
        val getterGeneratedValue = getter(owner, field.name)?.getAnnotation(GeneratedValue::class.java)
        check(field.getAnnotation(GeneratedValue::class.java) == null && getterGeneratedValue == null) {
            "Application-side ID field ${owner.name}.${field.name} must not also use @GeneratedValue"
        }
    }

    private fun findGetterAnnotation(type: Class<*>, fieldName: String): ApplicationSideId? =
        getter(type, fieldName)?.getAnnotation(ApplicationSideId::class.java)

    private fun getter(type: Class<*>, fieldName: String) =
        type.methods.firstOrNull { it.name == "get${fieldName.replaceFirstChar(Char::uppercaseChar)}" && it.parameterCount == 0 }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current ->
            current.superclass?.takeIf { it != Any::class.java }
        }.flatMap { current -> current.declaredFields.asSequence() }
}

internal data class ApplicationSideIdMember(
    val field: Field,
    val annotation: ApplicationSideId
) {
    fun get(entity: Any): Any? {
        field.isAccessible = true
        return field.get(entity)
    }

    fun set(entity: Any, value: Any) {
        field.isAccessible = true
        field.set(entity, value)
    }
}
```

- [ ] **Step 4: Run helper tests**

Run:

```powershell
.\gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest"
```

Expected: PASS.

- [ ] **Step 5: Commit helper**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt
git commit -m "feat: add jpa application-side id support"
```

### Task 4: Route Application-Side IDs Correctly in JpaUnitOfWork

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`

- [ ] **Step 1: Add failing unit-of-work tests**

Modify `JpaUnitOfWorkTest.TestableJpaUnitOfWork` to accept an `IdStrategyRegistry`, and update the test setup to pass `MapBackedIdStrategyRegistry(listOf(FixedLongStrategy()))`.

Add these tests to `JpaUnitOfWorkTest`:

```kotlin
@Test
@DisplayName("preassigned application-side id should persist when database row is missing")
fun preassignedApplicationSideIdShouldPersistWhenDatabaseRowIsMissing() {
    val entity = ApplicationSideLongEntity(id = 100L, name = "new")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 100L
    every { entityManager.find(ApplicationSideLongEntity::class.java, 100L) } returns null

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.persist(entity) }
    verify(exactly = 0) { entityManager.merge(entity) }
}

@Test
@DisplayName("application-side id should be assigned before beforeTransaction interceptors")
fun applicationSideIdShouldBeAssignedBeforeBeforeTransactionInterceptors() {
    val entity = ApplicationSideLongEntity(id = 0L, name = "allocated")
    every { mockEntityInfo.isNew(entity) } returns true

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify {
        interceptor1.beforeTransaction(
            match<Set<Any>> { persisted -> (persisted.single() as ApplicationSideLongEntity).id == 42L },
            any()
        )
    }
}

@Test
@DisplayName("existing application-side id should merge when database row exists")
fun existingApplicationSideIdShouldMergeWhenDatabaseRowExists() {
    val entity = ApplicationSideLongEntity(id = 100L, name = "existing")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 100L
    every { entityManager.find(ApplicationSideLongEntity::class.java, 100L) } returns ApplicationSideLongEntity(100L)

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.merge(entity) }
    verify(exactly = 0) { entityManager.persist(entity) }
}

private class FixedLongStrategy : IdStrategy {
    override val name: String = "snowflake-long"
    override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
    override val outputType = Long::class
    override val preassignable: Boolean = true
    override fun isDefaultValue(value: Any?): Boolean = value == null || value == 0L
    override fun next(): Any = 42L
}

private class ApplicationSideLongEntity(
    @field:Id
    @field:ApplicationSideId(strategy = "snowflake-long")
    var id: Long = 0L,
    var name: String = ""
)
```

- [ ] **Step 2: Run failing unit-of-work tests**

Run:

```powershell
.\gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected: the new tests fail because `JpaUnitOfWork` still uses `JpaEntityInformation.isNew(entity)` for assigned-ID routing and does not assign application-side IDs before interceptors.

- [ ] **Step 3: Inject ID support into JpaUnitOfWork**

Modify the constructor:

```kotlin
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    private val supportValueObjectExistsCheckOnSave: Boolean,
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
) : UnitOfWork {
    private val applicationSideIdSupport = JpaApplicationSideIdSupport(idStrategyRegistry)
}
```

Update imports:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
```

- [ ] **Step 4: Assign IDs before interceptors**

In `JpaUnitOfWork.save(propagation)`, after `persistEntitySet` and `deleteEntitySet` are built and before `uowInterceptors.forEach { it.beforeTransaction(...) }`, add:

```kotlin
persistEntitySet.forEach(applicationSideIdSupport::assignMissingIds)
```

The sequence must be:

```kotlin
persistEntitySet.forEach(applicationSideIdSupport::assignMissingIds)
uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }
```

- [ ] **Step 5: Route application-side entities before default isNew logic**

Replace the normal entity branch inside `persistEntities.forEach` with this order:

```kotlin
val applicationSideIdMember = applicationSideIdSupport.findApplicationSideId(entity)
when {
    supportValueObjectExistsCheckOnSave && entity is ValueObject<*> -> {
        if (!isExists(entity)) {
            entityManager.persist(entity)
            results.created.add(entity)
        }
    }
    applicationSideIdMember != null -> {
        check(!applicationSideIdSupport.isDefaultId(applicationSideIdMember, entity)) {
            "Application-side ID remains default after assignment: ${entity.javaClass.name}.${applicationSideIdMember.field.name}"
        }
        val id = applicationSideIdMember.get(entity)
        when {
            entityManager.contains(entity) -> results.updated.add(entity)
            entityManager.find(entity.javaClass, id) == null -> {
                entityManager.persist(entity)
                results.created.add(entity)
            }
            else -> {
                entityManager.merge(entity).also { merged -> updateWrappedEntity(entity, merged) }
                results.updated.add(entity)
            }
        }
    }
    getEntityInformation(entity.javaClass).isNew(entity) -> {
        if (!entityManager.contains(entity)) {
            entityManager.persist(entity)
        }
        results.refreshList = (results.refreshList ?: mutableListOf()).apply { add(entity) }
        results.created.add(entity)
    }
    else -> {
        if (!entityManager.contains(entity)) {
            entityManager.merge(entity).also { merged -> updateWrappedEntity(entity, merged) }
        }
        results.updated.add(entity)
    }
}
```

- [ ] **Step 6: Wire starter auto-configuration**

Modify `JpaRepositoryAutoConfiguration.jpaUnitOfWork(...)` to accept and pass `IdStrategyRegistry`:

```kotlin
fun jpaUnitOfWork(
    unitOfWorkInterceptors: List<UnitOfWorkInterceptor>,
    persistListenerManager: PersistListenerManager,
    jpaUnitOfWorkProperties: JpaUnitOfWorkProperties,
    idStrategyRegistry: IdStrategyRegistry,
): JpaUnitOfWork = JpaUnitOfWork(
    unitOfWorkInterceptors,
    persistListenerManager,
    jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
    jpaUnitOfWorkProperties.supportValueObjectExistsCheckOnSave,
    idStrategyRegistry,
).also {
    UnitOfWorkSupport.configure(it)
    JpaQueryUtils.configure(it, jpaUnitOfWorkProperties.retrieveCountWarnThreshold)
    Md5HashIdentifierGenerator.configure(jpaUnitOfWorkProperties.generalIdFieldName)
}
```

- [ ] **Step 7: Run unit-of-work tests**

Run:

```powershell
.\gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected: PASS.

- [ ] **Step 8: Run starter auto-configuration tests**

Run:

```powershell
.\gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.AutoConfigurationContextTest"
```

Expected: PASS. If this test does not cover `IdStrategyRegistry`, add an assertion that the context contains `IdStrategyRegistry` and `IdAllocator`.

- [ ] **Step 9: Commit unit-of-work integration**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd
git commit -m "fix: persist preassigned application-side ids"
```

### Task 5: Convert Runtime Characterization to Supported Behavior

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt`

- [ ] **Step 1: Add failing runtime test for UUID root and child allocation**

Create `ApplicationSideIdJpaRuntimeTest.kt`:

```kotlin
package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@SpringBootTest(classes = [ApplicationSideIdJpaRuntimeTest.RuntimeTestApplication::class])
class ApplicationSideIdJpaRuntimeTest(
    @Qualifier("jpaUnitOfWork")
    private val unitOfWork: UnitOfWork,
    private val rootRepository: RuntimeUuidRootRepository,
    private val jdbcTemplate: JdbcTemplate
) {

    @Test
    fun `default uuid root and child ids are assigned before persist`() {
        val root = RuntimeUuidRoot(name = "uuid-root")
        root.children += RuntimeUuidChild(name = "uuid-child")

        unitOfWork.persist(root)
        unitOfWork.save()

        assertNotEquals(UUID(0L, 0L), root.id)
        assertNotEquals(UUID(0L, 0L), root.children.single().id)
        assertEquals(1, countRows("select count(*) from runtime_uuid_root"))
        assertEquals(1, countRows("select count(*) from runtime_uuid_child"))
    }

    @Test
    fun `preassigned uuid root id is preserved and inserted`() {
        val id = UUID.fromString("018f38c1-1111-7000-8000-000000000001")
        val root = RuntimeUuidRoot(id = id, name = "preassigned")

        unitOfWork.persist(root)
        unitOfWork.save()

        assertEquals(id, rootRepository.findById(id).orElseThrow().id)
    }

    private fun countRows(sql: String): Int =
        requireNotNull(jdbcTemplate.queryForObject(sql, Int::class.java))

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @SpringBootApplication(scanBasePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackageClasses = [RuntimeUuidRoot::class])
    @EnableJpaRepositories(basePackageClasses = [RuntimeUuidRootRepository::class])
    class RuntimeTestApplication
}

@Entity
@Table(name = "runtime_uuid_root")
open class RuntimeUuidRoot(
    @field:Id
    @field:ApplicationSideId(strategy = "uuid7")
    @field:Column(name = "id", nullable = false, updatable = false)
    open var id: UUID = UUID(0L, 0L),
    @field:Column(name = "name", nullable = false)
    open var name: String = ""
) {
    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "root_id", nullable = false)
    open var children: MutableList<RuntimeUuidChild> = mutableListOf()
}

@Entity
@Table(name = "runtime_uuid_child")
open class RuntimeUuidChild(
    @field:Id
    @field:ApplicationSideId(strategy = "uuid7")
    @field:Column(name = "id", nullable = false, updatable = false)
    open var id: UUID = UUID(0L, 0L),
    @field:Column(name = "name", nullable = false)
    open var name: String = ""
)

interface RuntimeUuidRootRepository : JpaRepository<RuntimeUuidRoot, UUID>
```

- [ ] **Step 2: Run failing runtime test**

Run with a long timeout:

```powershell
.\gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.ApplicationSideIdJpaRuntimeTest" --no-daemon
```

Expected before Task 4 is complete: fails because IDs are not assigned or the preassigned root is routed incorrectly. Expected after Task 4: PASS.

- [ ] **Step 3: Update existing characterization status**

In `AggregateJpaRuntimeDefectReproductionTest.preassignedApplicationSideIdIsPreservedForNewRoot`, change the final assertion from known-defect classification to supported classification:

```kotlin
assertSupported(classification)
```

Keep the test body focused on preserving a preassigned ID. If the old fixture still uses Hibernate `@GeneratedValue(generator = ...)`, convert only this preassigned-ID fixture to `@ApplicationSideId(strategy = "snowflake-long")` so it tests the new contract.

- [ ] **Step 4: Run runtime tests**

Run with a long timeout:

```powershell
.\gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.*" --no-daemon
```

Expected: PASS. If Testcontainers startup dominates runtime, keep this command running until the existing Gradle 25-minute timeout is reached before treating it as failed.

- [ ] **Step 5: Commit runtime behavior**

Run:

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt
git commit -m "test: support application-side id runtime behavior"
```

### Task 6: Add Gradle DSL and Pipeline API ID Policy Config

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing Gradle DSL functional test**

Add a test to `PipelinePluginFunctionalTest` that writes this Gradle configuration into the fixture build file:

```kotlin
cap4k {
    project {
        basePackage.set("com.example.demo")
        domainModulePath.set("demo-domain")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
            username.set("sa")
            password.set("")
        }
    }
    generators {
        aggregate {
            enabled.set(true)
            idPolicy {
                defaultStrategy.set("snowflake-long")
                aggregate("message.UserMessage", "uuid7")
                entity("message.UserMessageAttachment", "snowflake-long")
            }
        }
    }
}
```

Assert that `cap4kPlan` can parse the DSL and that the plan JSON contains these fragments:

```kotlin
assertTrue(planJson.contains("\"defaultStrategy\": \"snowflake-long\""))
assertTrue(planJson.contains("\"message.UserMessage\": \"uuid7\""))
assertTrue(planJson.contains("\"message.UserMessageAttachment\": \"snowflake-long\""))
```

- [ ] **Step 2: Run failing DSL test**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: compilation or configuration fails because `idPolicy`, `aggregate(...)`, and `entity(...)` are not defined.

- [ ] **Step 3: Add API config classes**

Modify `ProjectConfig.kt`:

```kotlin
data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    val modules: Map<String, String>,
    val typeRegistry: Map<String, TypeRegistryEntry> = emptyMap(),
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
    val artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    val aggregateIdPolicy: AggregateIdPolicyConfig = AggregateIdPolicyConfig(),
)

data class AggregateIdPolicyConfig(
    val defaultStrategy: String = "uuid7",
    val aggregateStrategies: Map<String, String> = emptyMap(),
    val entityStrategies: Map<String, String> = emptyMap(),
)
```

Modify `PipelineModels.kt` by replacing `AggregateIdGeneratorControl` with:

```kotlin
enum class AggregateIdPolicyKind {
    APPLICATION_SIDE,
    DATABASE_SIDE,
}

data class AggregateIdPolicyControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val idFieldName: String,
    val idFieldType: String,
    val strategy: String,
    val kind: AggregateIdPolicyKind,
)
```

Modify `CanonicalModel`:

```kotlin
val aggregateIdPolicyControls: List<AggregateIdPolicyControl> = emptyList(),
```

Remove `aggregateIdGeneratorControls` from `CanonicalModel`.

- [ ] **Step 4: Add Gradle extension**

Modify `AggregateGeneratorExtension` in `Cap4kExtension.kt`:

```kotlin
open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unsupportedTablePolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
    val artifacts: AggregateGeneratorArtifactsExtension =
        objects.newInstance(AggregateGeneratorArtifactsExtension::class.java)
    val idPolicy: AggregateIdPolicyExtension =
        objects.newInstance(AggregateIdPolicyExtension::class.java)

    fun artifacts(block: AggregateGeneratorArtifactsExtension.() -> Unit) {
        artifacts.block()
    }

    fun idPolicy(block: AggregateIdPolicyExtension.() -> Unit) {
        idPolicy.block()
    }
}

open class AggregateIdPolicyExtension @Inject constructor(objects: ObjectFactory) {
    val defaultStrategy: Property<String> = objects.property(String::class.java).convention("uuid7")
    internal val aggregateStrategies = objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    internal val entityStrategies = objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())

    fun aggregate(name: String, strategy: String) {
        aggregateStrategies.put(name, strategy)
    }

    fun entity(name: String, strategy: String) {
        entityStrategies.put(name, strategy)
    }
}
```

- [ ] **Step 5: Copy DSL config into ProjectConfig**

Modify `Cap4kProjectConfigFactory.build(...)`:

```kotlin
return ProjectConfig(
    basePackage = basePackage,
    layout = ProjectLayout.MULTI_MODULE,
    modules = modules,
    typeRegistry = typeRegistry,
    sources = sources,
    generators = generators,
    templates = TemplateConfig(
        preset = extension.templates.preset.normalized().ifEmpty { "ddd-default" },
        overrideDirs = resolveTemplateOverrideDirs(project, extension),
        conflictPolicy = ConflictPolicy.valueOf(
            extension.templates.conflictPolicy.normalized().ifEmpty { "SKIP" }
        ),
    ),
    artifactLayout = artifactLayout,
    aggregateIdPolicy = buildAggregateIdPolicy(extension),
)
```

Add:

```kotlin
private fun buildAggregateIdPolicy(extension: Cap4kExtension): AggregateIdPolicyConfig {
    val idPolicy = extension.generators.aggregate.idPolicy
    return AggregateIdPolicyConfig(
        defaultStrategy = idPolicy.defaultStrategy.normalized().ifEmpty { "uuid7" },
        aggregateStrategies = idPolicy.aggregateStrategies.get().mapKeys { it.key.trim() }.mapValues { it.value.trim() },
        entityStrategies = idPolicy.entityStrategies.get().mapKeys { it.key.trim() }.mapValues { it.value.trim() },
    )
}
```

Update imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyConfig
```

- [ ] **Step 6: Include ID policy in plan report**

If `BootstrapPlanReport` is not involved, update the regular plan report model used by `Cap4kPlanTask` so `ProjectConfig.aggregateIdPolicy` or resolved `aggregateIdPolicyControls` appears in `build/cap4k/plan.json`. Use resolved controls if the report already serializes canonical model data.

Expected JSON fragment after `cap4kPlan`:

```json
"aggregateIdPolicyControls": [
  {
    "entityName": "UserMessage",
    "strategy": "uuid7",
    "kind": "APPLICATION_SIDE"
  }
]
```

- [ ] **Step 7: Run DSL test**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: the new DSL test passes.

- [ ] **Step 8: Commit API and DSL**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "feat: add aggregate id policy dsl"
```

### Task 7: Resolve Canonical Aggregate ID Policies

**Files:**
- Delete or stop using: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add failing canonical tests**

Add these tests to `DefaultCanonicalAssemblerTest`:

```kotlin
@Test
fun `default uuid7 strategy applies to UUID aggregate id`() {
    val result = assembleAggregate(
        config = projectConfigWithIdPolicy(defaultStrategy = "uuid7"),
        tables = listOf(
            table(
                name = "user_message",
                columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val control = result.model.aggregateIdPolicyControls.single()
    assertEquals("uuid7", control.strategy)
    assertEquals(AggregateIdPolicyKind.APPLICATION_SIDE, control.kind)
    assertEquals("UUID", control.idFieldType)
}

@Test
fun `default uuid7 strategy rejects Long aggregate id`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        assembleAggregate(
            config = projectConfigWithIdPolicy(defaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "video",
                    columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )
    }

    assertTrue(error.message!!.contains("ID strategy uuid7 cannot be applied to aggregate video.Video id field id"))
}

@Test
fun `aggregate override applies to owned child entities`() {
    val result = assembleAggregate(
        config = projectConfigWithIdPolicy(
            defaultStrategy = "uuid7",
            aggregateStrategies = mapOf("video.Video" to "snowflake-long")
        ),
        tables = listOf(
            table(
                name = "video",
                columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            ),
            table(
                name = "video_file",
                columns = listOf(
                    column("id", "BIGINT", "Long", false, primaryKey = true),
                    column("video_id", "BIGINT", "Long", false, referenceTable = "video"),
                ),
                primaryKey = listOf("id"),
                aggregateRoot = false,
                parentTable = "video",
            )
        )
    )

    assertEquals(
        setOf("Video" to "snowflake-long", "VideoFile" to "snowflake-long"),
        result.model.aggregateIdPolicyControls.map { it.entityName to it.strategy }.toSet()
    )
}

@Test
fun `entity override wins over aggregate override`() {
    val result = assembleAggregate(
        config = projectConfigWithIdPolicy(
            defaultStrategy = "uuid7",
            aggregateStrategies = mapOf("video.Video" to "uuid7"),
            entityStrategies = mapOf("video.VideoFile" to "snowflake-long")
        ),
        tables = listOf(
            table(
                name = "video",
                columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            ),
            table(
                name = "video_file",
                columns = listOf(
                    column("id", "BIGINT", "Long", false, primaryKey = true),
                    column("video_id", "UUID", "UUID", false, referenceTable = "video"),
                ),
                primaryKey = listOf("id"),
                aggregateRoot = false,
                parentTable = "video",
            )
        )
    )

    assertEquals("uuid7", result.model.aggregateIdPolicyControls.single { it.entityName == "Video" }.strategy)
    assertEquals("snowflake-long", result.model.aggregateIdPolicyControls.single { it.entityName == "VideoFile" }.strategy)
}

@Test
fun `identity id remains database identity without application side annotation`() {
    val result = assembleAggregate(
        config = projectConfigWithIdPolicy(defaultStrategy = "uuid7"),
        tables = listOf(
            table(
                name = "audit_log",
                columns = listOf(
                    column("id", "BIGINT", "Long", false, primaryKey = true, generatedValueStrategy = "IDENTITY")
                ),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val control = result.model.aggregateIdPolicyControls.single()
    assertEquals("database-identity", control.strategy)
    assertEquals(AggregateIdPolicyKind.DATABASE_SIDE, control.kind)
}
```

If helper functions named `assembleAggregate`, `projectConfigWithIdPolicy`, `table`, or `column` do not exist, add them at the bottom of `DefaultCanonicalAssemblerTest` as local test helpers that construct `ProjectConfig`, `DbTableSnapshot`, and `DbColumnSnapshot` using existing helper style in the file.

- [ ] **Step 2: Run failing canonical tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: compilation fails because `aggregateIdPolicyControls`, `AggregateIdPolicyKind`, and `AggregateIdPolicyResolver` do not exist.

- [ ] **Step 3: Implement resolver**

Create `AggregateIdPolicyResolver.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal object AggregateIdPolicyResolver {
    private const val UUID7 = "uuid7"
    private const val SNOWFLAKE_LONG = "snowflake-long"
    private const val DATABASE_IDENTITY = "database-identity"

    fun resolve(
        config: ProjectConfig,
        entities: List<EntityModel>,
        persistenceFieldControls: List<AggregatePersistenceFieldControl>,
    ): List<AggregateIdPolicyControl> {
        val entitiesByNameAndPackage = entities.associateBy { it.packageName to it.name }
        return entities.map { entity ->
            val idField = entity.idField
            val identity = persistenceFieldControls
                .firstOrNull {
                    it.entityName == entity.name &&
                        it.entityPackageName == entity.packageName &&
                        it.fieldName == idField.name
                }
                ?.generatedValueStrategy == "IDENTITY"

            val explicitEntityStrategy = config.aggregateIdPolicy.entityStrategies[entityKey(config, entity)]
            val aggregateRoot = aggregateRoot(entity, entitiesByNameAndPackage)
            val explicitAggregateStrategy = config.aggregateIdPolicy.aggregateStrategies[entityKey(config, aggregateRoot)]
            val requestedStrategy = explicitEntityStrategy ?: explicitAggregateStrategy ?: config.aggregateIdPolicy.defaultStrategy

            val strategy = if (identity && explicitEntityStrategy == null && explicitAggregateStrategy == null) {
                DATABASE_IDENTITY
            } else {
                requestedStrategy
            }

            if (identity && strategy != DATABASE_IDENTITY) {
                throw IllegalArgumentException(
                    "ID strategy $strategy cannot be applied to identity aggregate ${entityKey(config, entity)} id field ${idField.name}"
                )
            }

            validateType(config, entity, strategy)

            AggregateIdPolicyControl(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                tableName = entity.tableName,
                idFieldName = idField.name,
                idFieldType = idField.type,
                strategy = strategy,
                kind = if (strategy == DATABASE_IDENTITY) {
                    AggregateIdPolicyKind.DATABASE_SIDE
                } else {
                    AggregateIdPolicyKind.APPLICATION_SIDE
                },
            )
        }
    }

    private fun validateType(config: ProjectConfig, entity: EntityModel, strategy: String) {
        val idType = entity.idField.type
        val valid = when (strategy) {
            UUID7 -> idType == "UUID" || idType == "java.util.UUID"
            SNOWFLAKE_LONG -> idType == "Long" || idType == "kotlin.Long"
            DATABASE_IDENTITY -> idType in setOf("Long", "Int", "Short", "java.lang.Long", "java.lang.Integer")
            else -> throw IllegalArgumentException("unknown ID strategy: $strategy")
        }
        require(valid) {
            "ID strategy $strategy cannot be applied to aggregate ${entityKey(config, entity)} id field ${entity.idField.name}: generated ID type is $idType"
        }
    }

    private fun aggregateRoot(
        entity: EntityModel,
        entitiesByNameAndPackage: Map<Pair<String, String>, EntityModel>,
    ): EntityModel {
        var current = entity
        val visited = mutableSetOf<Pair<String, String>>()
        while (current.parentEntityName != null) {
            val key = current.packageName to current.name
            if (!visited.add(key)) return current
            current = entitiesByNameAndPackage[current.packageName to current.parentEntityName] ?: return current
        }
        return current
    }

    private fun entityKey(config: ProjectConfig, entity: EntityModel): String {
        val aggregateRoot = "${config.basePackage}.${config.artifactLayout.aggregate.packageRoot}".trimEnd('.')
        val relativePackage = entity.packageName.removePrefix("$aggregateRoot.").removePrefix(aggregateRoot)
        return listOf(relativePackage, entity.name)
            .filter { it.isNotBlank() }
            .joinToString(".")
    }
}
```

- [ ] **Step 4: Wire resolver into canonical assembly**

Modify `DefaultCanonicalAssembler.kt`:

```kotlin
val aggregateIdPolicyControls = AggregateIdPolicyResolver.resolve(
    config = config,
    entities = entities,
    persistenceFieldControls = aggregatePersistenceFieldControls,
)
```

Use the new property in `CanonicalModel`:

```kotlin
aggregateIdPolicyControls = aggregateIdPolicyControls,
```

Remove the call to `AggregateIdGeneratorInference.infer(...)`.

- [ ] **Step 5: Run canonical tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS after updating old assertions that referenced `aggregateIdGeneratorControls`.

- [ ] **Step 6: Commit canonical ID policy resolution**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git rm --ignore-unmatch cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt
git commit -m "feat: resolve aggregate id policies"
```

### Task 8: Reject Legacy DB Comment ID Generator Input

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add failing parser rejection tests**

Update `DbTableAnnotationParserTest` so these cases fail fast:

```kotlin
@Test
fun `IdGenerator annotation is unsupported`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse("@IdGenerator=snowflakeIdGenerator;")
    }

    assertEquals(
        "unsupported table annotation @IdGenerator: configure aggregate.idPolicy in Gradle DSL instead",
        error.message
    )
}

@Test
fun `IG annotation alias is unsupported`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse("@IG=snowflakeIdGenerator;")
    }

    assertEquals(
        "unsupported table annotation @IG: configure aggregate.idPolicy in Gradle DSL instead",
        error.message
    )
}
```

- [ ] **Step 2: Run failing parser tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest"
```

Expected: tests fail because the parser still accepts `@IdGenerator`.

- [ ] **Step 3: Remove DB snapshot generator field**

Modify `DbTableSnapshot` in `PipelineModels.kt` by deleting:

```kotlin
val entityIdGenerator: String? = null,
```

Update every `DbTableSnapshot(...)` construction that still passes `entityIdGenerator = ...`.

- [ ] **Step 4: Reject legacy annotations in parser**

Modify `DbTableAnnotationParser.parse(...)` so `@IdGenerator` and `@IG` throw:

```kotlin
if (annotationName == "IdGenerator" || annotationName == "IG") {
    throw IllegalArgumentException(
        "unsupported table annotation @$annotationName: configure aggregate.idPolicy in Gradle DSL instead"
    )
}
```

Keep the cleaned comment behavior for supported annotations unchanged.

- [ ] **Step 5: Update tests that still assert custom generator pass-through**

Replace old assertions like:

```kotlin
assertEquals("snowflakeIdGenerator", control.entityIdGenerator)
assertTrue(generatedVideoPost.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
assertTrue(generatedVideoPost.contains("@GenericGenerator(name = \"snowflakeIdGenerator\", strategy = \"snowflakeIdGenerator\")"))
```

with assertions against Gradle DSL policy:

```kotlin
assertEquals("snowflake-long", control.strategy)
assertEquals(AggregateIdPolicyKind.APPLICATION_SIDE, control.kind)
assertTrue(generatedVideoPost.contains("@ApplicationSideId(strategy = \"snowflake-long\")"))
assertFalse(generatedVideoPost.contains("@GenericGenerator"))
assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
```

- [ ] **Step 6: Run parser and compile functional tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest"
.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --no-daemon
```

Expected: PASS after all old `@IdGenerator` fixture material is replaced by `generators.aggregate.idPolicy`.

- [ ] **Step 7: Commit legacy input rejection**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "refactor: reject legacy id generator comments"
```

### Task 9: Render Application-Side ID Fields in Aggregate Entities

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing aggregate planner tests**

Add tests to `AggregateArtifactPlannerTest`:

```kotlin
@Test
fun `application side uuid7 id field renders annotation and sentinel default`() {
    val artifact = planSingleEntityArtifact(
        model = CanonicalModel(
            entities = listOf(uuidEntity("UserMessage")),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(uuidEntity("UserMessage"))),
            aggregateIdPolicyControls = listOf(
                AggregateIdPolicyControl(
                    entityName = "UserMessage",
                    entityPackageName = "com.example.domain.aggregates.message",
                    tableName = "user_message",
                    idFieldName = "id",
                    idFieldType = "UUID",
                    strategy = "uuid7",
                    kind = AggregateIdPolicyKind.APPLICATION_SIDE,
                )
            )
        )
    )

    val idField = artifact.context["scalarFields"].asFieldList().single { it["name"] == "id" }
    assertEquals("uuid7", idField["applicationSideIdStrategy"])
    assertEquals("UUID(0L, 0L)", idField["defaultValue"])
    assertEquals(false, idField["updatable"])
    assertEquals(true, artifact.context["hasApplicationSideIdFields"])
    assertEquals(false, artifact.context["hasGenericGeneratorFields"])
}

@Test
fun `database identity id field still renders identity generated value`() {
    val entity = longEntity("AuditLog")
    val artifact = planSingleEntityArtifact(
        model = CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregatePersistenceFieldControls = listOf(
                AggregatePersistenceFieldControl(
                    entityName = "AuditLog",
                    entityPackageName = "com.example.domain.aggregates.audit",
                    fieldName = "id",
                    columnName = "id",
                    generatedValueStrategy = "IDENTITY",
                )
            ),
            aggregateIdPolicyControls = listOf(
                AggregateIdPolicyControl(
                    entityName = "AuditLog",
                    entityPackageName = "com.example.domain.aggregates.audit",
                    tableName = "audit_log",
                    idFieldName = "id",
                    idFieldType = "Long",
                    strategy = "database-identity",
                    kind = AggregateIdPolicyKind.DATABASE_SIDE,
                )
            )
        )
    )

    val idField = artifact.context["scalarFields"].asFieldList().single { it["name"] == "id" }
    assertEquals(null, idField["applicationSideIdStrategy"])
    assertEquals("IDENTITY", idField["generatedValueStrategy"])
}
```

Add local test helper:

```kotlin
@Suppress("UNCHECKED_CAST")
private fun Any?.asFieldList(): List<Map<String, Any?>> = this as List<Map<String, Any?>>
```

- [ ] **Step 2: Add failing renderer tests**

Add tests to `PebbleArtifactRendererTest` using the `aggregate/entity.kt.peb` template:

```kotlin
@Test
fun `entity template renders application side id without hibernate generic generator`() {
    val content = renderAggregateEntity(
        mapOf(
            "packageName" to "com.example.domain.aggregates.message",
            "typeName" to "UserMessage",
            "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "user_message"),
            "hasApplicationSideIdFields" to true,
            "hasGeneratedValueFields" to false,
            "hasGenericGeneratorFields" to false,
            "hasVersionFields" to false,
            "hasConverterFields" to false,
            "dynamicInsert" to false,
            "dynamicUpdate" to false,
            "softDeleteSql" to null,
            "softDeleteWhereClause" to null,
            "jpaImports" to emptyList<String>(),
            "imports" to listOf("java.util.UUID"),
            "scalarFields" to listOf(
                mapOf(
                    "name" to "id",
                    "type" to "UUID",
                    "nullable" to false,
                    "defaultValue" to "UUID(0L, 0L)",
                    "columnName" to "id",
                    "isId" to true,
                    "applicationSideIdStrategy" to "uuid7",
                    "generatedValueStrategy" to null,
                    "isVersion" to false,
                    "insertable" to null,
                    "updatable" to false,
                    "converterClassRef" to null,
                )
            ),
            "relationFields" to emptyList<Map<String, Any?>>(),
        )
    )

    assertTrue(content.contains("import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId"))
    assertTrue(content.contains("@field:ApplicationSideId(strategy = \"uuid7\")"))
    assertTrue(content.contains("id: UUID = UUID(0L, 0L)"))
    assertFalse(content.contains("@GenericGenerator"))
    assertFalse(content.contains("@GeneratedValue(generator ="))
}
```

- [ ] **Step 3: Run failing aggregate generator and renderer tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: tests fail because the planner and template still use `generatedValueGenerator` / `GenericGenerator`.

- [ ] **Step 4: Update EntityArtifactPlanner render context**

In `EntityArtifactPlanner.kt`, replace `idGeneratorControl` lookup with:

```kotlin
val idPolicyControl = model.aggregateIdPolicyControls.firstOrNull {
    it.entityName == entity.name && it.entityPackageName == entity.packageName
}
```

Replace custom generator field calculations with:

```kotlin
val isIdField = jpa.isId
val isApplicationSideIdField =
    isIdField && idPolicyControl?.idFieldName == field.name &&
        idPolicyControl.kind == AggregateIdPolicyKind.APPLICATION_SIDE
val isDatabaseIdentityField =
    isIdField && idPolicyControl?.idFieldName == field.name &&
        idPolicyControl.strategy == "database-identity"

val generatedValueStrategy = when {
    isApplicationSideIdField -> null
    isDatabaseIdentityField -> "IDENTITY"
    else -> control?.generatedValueStrategy
}
val applicationSideIdStrategy = if (isApplicationSideIdField) idPolicyControl.strategy else null
val projectedDefaultValue = when {
    isApplicationSideIdField && idPolicyControl.strategy == "uuid7" -> "UUID(0L, 0L)"
    isApplicationSideIdField && idPolicyControl.strategy == "snowflake-long" -> "0L"
    else -> defaultProjector.project(
        fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
        fieldType = fieldType,
        nullable = field.nullable,
        rawDefaultValue = field.defaultValue,
        enumItems = planning.resolveEnumItems(entity.packageName, field),
    )
}
```

Use these map entries:

```kotlin
"defaultValue" to projectedDefaultValue,
"applicationSideIdStrategy" to applicationSideIdStrategy,
"generatedValueStrategy" to generatedValueStrategy,
"updatable" to when {
    isApplicationSideIdField -> false
    control?.updatable != null -> control.updatable
    control?.insertable != null -> true
    else -> null
},
```

Set context booleans:

```kotlin
"hasApplicationSideIdFields" to scalarFields.any { it["applicationSideIdStrategy"] != null },
"hasGeneratedValueFields" to scalarFields.any {
    it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
},
"hasGenericGeneratorFields" to false,
```

Add `ApplicationSideId` and `UUID` imports through `imports`:

```kotlin
val applicationSideImports = buildList {
    if (scalarFields.any { it["applicationSideIdStrategy"] != null }) {
        add("com.only4.cap4k.ddd.core.domain.id.ApplicationSideId")
    }
    if (scalarFields.any { it["defaultValue"] == "UUID(0L, 0L)" && it["type"] == "UUID" }) {
        add("java.util.UUID")
    }
}
```

Use:

```kotlin
"imports" to (relationPlan.imports + applicationSideImports).distinct(),
```

- [ ] **Step 5: Update Pebble template**

In `entity.kt.peb`, remove the `hasGenericGeneratorFields` import branch:

```peb
{% if hasGeneratedValueFields -%}
{{ use("jakarta.persistence.GeneratedValue") -}}
{{ use("jakarta.persistence.GenerationType") -}}
{% endif -%}
```

Remove:

```peb
{{ use("org.hibernate.annotations.GenericGenerator") -}}
```

Replace the ID annotation branch with:

```peb
{% if field.isId %}    @Id
{% endif %}{% if field.isId and field.applicationSideIdStrategy is not null %}    @field:ApplicationSideId(strategy = "{{ field.applicationSideIdStrategy }}")
{% elseif field.isId and field.generatedValueStrategy == "IDENTITY" %}    @GeneratedValue(strategy = GenerationType.IDENTITY)
{% endif %}
```

Keep the existing `@Column` rendering path so `updatable = false` is controlled by the render model.

- [ ] **Step 6: Run generator and renderer tests**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS after all old custom-generator assertions are updated.

- [ ] **Step 7: Commit aggregate rendering**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render application-side aggregate ids"
```

### Task 10: Add UUID Type Detection and Plugin Integration Coverage

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add failing JDBC UUID mapping test**

Add to `DbSchemaSourceProviderTest` or a dedicated `JdbcTypeMapperTest`:

```kotlin
@Test
fun `maps native uuid type to kotlin UUID`() {
    assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.OTHER, "UUID"))
    assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.OTHER, "uuid"))
}
```

Import:

```kotlin
import java.sql.Types
```

- [ ] **Step 2: Run failing source DB test**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.*"
```

Expected: fails because `JdbcTypeMapper.toKotlinType` currently accepts only the SQL type integer.

- [ ] **Step 3: Update JDBC type mapper**

Modify `JdbcTypeMapper.kt`:

```kotlin
object JdbcTypeMapper {
    fun toKotlinType(sqlType: Int, typeName: String? = null): String = when {
        sqlType == Types.OTHER && typeName.equals("uuid", ignoreCase = true) -> "UUID"
        sqlType == Types.BINARY && typeName.equals("uuid", ignoreCase = true) -> "UUID"
        else -> when (sqlType) {
            Types.BIGINT -> "Long"
            Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Int"
            Types.BOOLEAN, Types.BIT -> "Boolean"
            Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal"
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.LocalDateTime"
            Types.DATE -> "java.time.LocalDate"
            else -> "String"
        }
    }
}
```

Modify `DbSchemaSourceProvider.kt`:

```kotlin
val typeName = rows.getString("TYPE_NAME")
DbColumnSnapshot(
    name = rows.getString("COLUMN_NAME"),
    dbType = typeName,
    kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE"), typeName),
    ...
)
```

- [ ] **Step 4: Add plugin compile fixture for UUID7**

Add or update a `PipelinePluginCompileFunctionalTest` fixture with H2 UUID columns:

```sql
create table user_message (
    id uuid primary key,
    content varchar(255) not null
);
```

Use Gradle DSL:

```kotlin
generators {
    aggregate {
        enabled.set(true)
        idPolicy {
            defaultStrategy.set("uuid7")
        }
    }
}
```

Assert generated entity contains:

```kotlin
assertTrue(generatedEntity.contains("import java.util.UUID"))
assertTrue(generatedEntity.contains("import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId"))
assertTrue(generatedEntity.contains("@field:ApplicationSideId(strategy = \"uuid7\")"))
assertTrue(generatedEntity.contains("id: UUID = UUID(0L, 0L)"))
assertFalse(generatedEntity.contains("@GenericGenerator"))
assertFalse(generatedEntity.contains("@GeneratedValue(generator ="))
```

- [ ] **Step 5: Add plugin compile fixture for Long override**

Add or update a fixture with `BIGINT` IDs and explicit policy:

```kotlin
generators {
    aggregate {
        enabled.set(true)
        idPolicy {
            defaultStrategy.set("snowflake-long")
        }
    }
}
```

Assert:

```kotlin
assertTrue(generatedVideoPost.contains("@field:ApplicationSideId(strategy = \"snowflake-long\")"))
assertTrue(generatedVideoPost.contains("id: Long = 0L"))
assertFalse(generatedVideoPost.contains("@GenericGenerator"))
assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
```

- [ ] **Step 6: Add plugin failure fixture for invalid default**

Add a `PipelinePluginFunctionalTest` case where a `BIGINT` ID table uses default `uuid7` with no override.

Expected failure fragment:

```text
ID strategy uuid7 cannot be applied to aggregate video.Video id field id: generated ID type is Long
```

- [ ] **Step 7: Run plugin tests**

Run with long timeout:

```powershell
.\gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.*"
.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --no-daemon
.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --no-daemon
```

Expected: PASS. Treat plugin compile fixtures as long-running and allow up to 25 minutes.

- [ ] **Step 8: Commit plugin integration**

Run:

```powershell
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate id policy generation"
```

### Task 11: Remove Remaining Legacy Generator References in New Pipeline

**Files:**
- Modify tests under `cap4k-plugin-pipeline-core/src/test`
- Modify tests under `cap4k-plugin-pipeline-generator-aggregate/src/test`
- Modify tests under `cap4k-plugin-pipeline-renderer-pebble/src/test`
- Modify tests under `cap4k-plugin-pipeline-gradle/src/test`
- Do not modify legacy module `cap4k-plugin-codegen` unless a new-pipeline test imports it.

- [ ] **Step 1: Search for remaining new-pipeline legacy references**

Run:

```powershell
rg -n "AggregateIdGeneratorControl|aggregateIdGeneratorControls|entityIdGenerator|generatedValueGenerator|genericGeneratorName|genericGeneratorStrategy|@IdGenerator|snowflakeIdGenerator|@GenericGenerator|@GeneratedValue\\(generator" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle -g "*.kt" -g "*.peb"
```

Expected after Tasks 6-10: only allowed references are negative assertions such as `assertFalse(content.contains("@GenericGenerator"))` and parser rejection tests.

- [ ] **Step 2: Replace remaining positive legacy assertions**

For each positive match in new-pipeline modules, replace it with one of:

```kotlin
assertTrue(content.contains("@field:ApplicationSideId(strategy = \"snowflake-long\")"))
assertTrue(content.contains("@field:ApplicationSideId(strategy = \"uuid7\")"))
assertFalse(content.contains("@GenericGenerator"))
assertFalse(content.contains("@GeneratedValue(generator ="))
```

For API model references, replace imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
```

- [ ] **Step 3: Run search again**

Run:

```powershell
rg -n "AggregateIdGeneratorControl|aggregateIdGeneratorControls|entityIdGenerator|generatedValueGenerator|genericGeneratorName|genericGeneratorStrategy|@IdGenerator|@GeneratedValue\\(generator" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle -g "*.kt" -g "*.peb"
```

Expected: no positive production references. Parser rejection tests may still contain `@IdGenerator` input strings.

- [ ] **Step 4: Run new pipeline test set**

Run:

```powershell
.\gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --no-daemon
```

Expected: PASS. Allow long-running Gradle plugin tests up to 25 minutes.

- [ ] **Step 5: Commit cleanup**

Run:

```powershell
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle
git commit -m "refactor: remove new-pipeline custom id generator path"
```

### Task 12: Final Verification and Documentation Status

**Files:**
- Modify: `docs/superpowers/specs/2026-05-02-cap4k-uuid7-id-generator-default-policy-design.md`
- Modify: `docs/superpowers/mainline-roadmap.md`
- Modify: `AGENTS.md` only if the active planning queue changes after this slice

- [ ] **Step 1: Run focused runtime and plugin verification**

Run from the `cap4k` repo root:

```powershell
.\gradlew :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test --no-daemon
.\gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --no-daemon
```

Expected: PASS. For `cap4k-plugin-pipeline-gradle`, allow up to 25 minutes before treating a timeout as failure.

- [ ] **Step 2: Run whole-project compile check**

Run:

```powershell
.\gradlew compileKotlin compileTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Verify no new-pipeline legacy generator path remains**

Run:

```powershell
rg -n "AggregateIdGeneratorControl|aggregateIdGeneratorControls|entityIdGenerator|generatedValueGenerator|genericGeneratorName|genericGeneratorStrategy|@GeneratedValue\\(generator|@GenericGenerator" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle -g "*.kt" -g "*.peb"
```

Expected: no production matches. Negative test assertions are acceptable only when they explicitly verify absence.

- [ ] **Step 4: Update spec status**

Change the spec header:

```markdown
> Status: Implemented
```

Add this completion note near the top:

```markdown
> Completion note: Implemented by the UUID7 application-side ID policy slice. The implementation keeps `@GeneratedValue(strategy = GenerationType.IDENTITY)` for database identity IDs and uses `@ApplicationSideId` for application-side strategies.
```

- [ ] **Step 5: Update roadmap status**

In `docs/superpowers/mainline-roadmap.md`, move `UUID7 ID generator and default ID-generation policy` from active queue to completed work, and leave `unit-of-work and repository backend comparison` deferred unless new runtime failures from this slice justify reopening it.

- [ ] **Step 6: Commit final docs**

Run:

```powershell
git add docs/superpowers/specs/2026-05-02-cap4k-uuid7-id-generator-default-policy-design.md docs/superpowers/mainline-roadmap.md AGENTS.md
git commit -m "docs: close uuid7 id policy slice"
```

## Plan Self-Review

Spec coverage:

- UUID7 default strategy: Tasks 2, 6, 7, 9, and 10.
- Explicit `snowflake-long`: Tasks 2, 6, 7, 9, and 10.
- Gradle-only policy configuration: Tasks 6 and 8.
- Field-level runtime contract: Tasks 1 and 9.
- Framework allocator: Tasks 1 and 2.
- JPA save-time ID assignment and preassigned-ID persistence: Tasks 3, 4, and 5.
- Strategy/type validation: Tasks 7 and 10.
- Removal of legacy custom generator path: Tasks 8, 9, and 11.
- Verification and doc status: Task 12.

Forbidden-marker scan:

- The plan contains concrete paths, commands, expected outcomes, and code snippets for every implementation step.
- No intentionally vague implementation markers remain.

Type consistency:

- Runtime enum is `IdGenerationKind`.
- Pipeline enum is `AggregateIdPolicyKind`.
- Runtime annotation is `ApplicationSideId`.
- Pipeline canonical list is `aggregateIdPolicyControls`.
- User DSL is `generators.aggregate.idPolicy`.
