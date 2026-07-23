# Cap4k Mediator Identifier Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose generic identifier generation through `Mediator.identifiers` while replacing the old single-output `IdAllocator` runtime with strategy-family `Identifier*` contracts.

**Architecture:** `ddd-core` owns the public identifier facade and strategy contracts. `cap4k-ddd-starter` wires built-in and external strategies as Spring beans, while `ddd-domain-repo-jpa` consumes only the registry and applies `ENTITY_ID_PREASSIGNMENT` capability checks for save-time `@ApplicationSideId` compatibility.

**Tech Stack:** Kotlin/JVM 2.2.20, Spring Boot 3.5.6 auto-configuration, JUnit 5, Spring `ApplicationContextRunner`, Gradle Kotlin DSL, `uuid-creator`, existing `ddd-distributed-snowflake` `SnowflakeIdGenerator`.

## Global Constraints

- Public application entry is `mediator.identifiers.next(...)`, not `mediator.nextId(...)`.
- Companion shortcut is `Mediator.identifiers.next(...)`.
- Built-in constants must be `BuiltInIdentifierStrategies.UUID7 = "uuid7"` and `BuiltInIdentifierStrategies.SNOWFLAKE = "snowflake"`.
- `IdentifierGenerator` must expose `fun <T : Any> next(strategy: String, type: KClass<T>): T`.
- `IdentifierGenerator` must expose `fun <T : Any> next(strategy: String, type: Class<T>): T` for Java callers.
- `uuid7` must support `String` and `UUID`.
- `uuid7` must fail fast for `Long`, arbitrary numeric types, and Strong ID wrapper classes.
- `snowflake` must support `Long` and decimal `String`.
- `snowflake` must fail fast for `UUID`, arbitrary object types, and Strong ID wrapper classes.
- The public `snowflake-long` strategy name is removed; do not preserve a compatibility alias.
- Identifier generation has no UoW side effect and must not call `persist(...)`, `remove(...)`, or `save(...)`.
- Database-side entity ID generation is not a runtime `IdentifierStrategy`.
- JPA entity ID preassignment must require `IdentifierCapability.ENTITY_ID_PREASSIGNMENT`.
- External business-code strategies can be used through `mediator.identifiers` without `ENTITY_ID_PREASSIGNMENT`.
- Phase 3 does not generate Strong ID wrapper values.
- Phase 3 does not implement generated Strong ID constructor/factory injection.
- Phase 3 does not change UoW `PersistIntent.CREATE/UPDATE` or Phase 2's planned `CREATE/EXISTING`.
- Do not add CosId or any new dependency.
- Keep old historical plans under `docs/superpowers/plans/**` unchanged unless a step explicitly targets the new plan file.

---

## Current Evidence

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt` currently defines `IdAllocator` and `DefaultIdAllocator`.
- `DefaultIdAllocator` currently rejects strategies whose `kind` is not `IdGenerationKind.APPLICATION_SIDE`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt` currently defines a one-output strategy with `outputType`, `preassignable`, and `next(): Any`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt` currently defines `IdStrategyRegistry` and `MapBackedIdStrategyRegistry`.
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt` currently registers `uuid7` as `UUID` only.
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt` currently registers `snowflake-long` as `Long` only.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt` currently has no `identifiers` property.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt` currently forwards UoW, repository, factory, service, event, and request operations only.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt` currently checks `IdGenerationKind.APPLICATION_SIDE` and single `outputType`.
- `ddd-distributed-snowflake/src/main/kotlin/com/only4/cap4k/ddd/domain/distributed/SnowflakeIdentifierGenerator.kt` is a Hibernate `org.hibernate.id.IdentifierGenerator`; do not confuse it with the new core `IdentifierGenerator`.

## File Structure

- Create or move to `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierGenerator.kt`: public generation facade and default implementation.
- Create or move to `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategy.kt`: strategy-family contract.
- Create or move to `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategyRegistry.kt`: name-based registry and map-backed implementation.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierCapability.kt`: narrow capability enum.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/BuiltInIdentifierStrategies.kt`: built-in strategy name constants.
- Keep `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt`: annotation remains the save-time compatibility marker.
- Remove `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt` after JPA capability migration removes the final production and test references.
- Move `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt` to `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdentifierStrategy.kt`.
- Move `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt` to `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt`.
- Modify `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`: register built-in strategy beans, collect external `IdentifierStrategy` beans, expose `IdentifierStrategyRegistry`, expose `IdentifierGenerator`.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`: add instance and companion `identifiers`.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/MediatorSupport.kt`: store configured `IdentifierGenerator`.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`: delegate `identifiers` to constructor-injected generator.
- Modify `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt`: inject and configure `IdentifierGenerator`.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`: use capability and output-type-aware strategy API.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`: constructor type changes only; do not change UoW intent semantics.
- Modify `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`: inject `IdentifierStrategyRegistry`.
- Modify tests in `IdPolicyCoreTest`, `IdPolicyAutoConfigurationTest`, `DefaultMediatorTest`, `JpaApplicationSideIdSupportTest`, `JpaUnitOfWorkTest`, and `AutoConfigurationContextTest`.

## Segmented Commit Policy

- Commit after Task 1: core API is green.
- Commit after Task 2: starter built-in strategies and registry wiring are green.
- Commit after Task 3: Mediator facade tests are green.
- Commit after Task 4: JPA capability migration tests are green.
- Commit after Task 5: cleanup, static checks, and focused Gradle verification are done.
- If a task grows beyond one coherent diff, split inside the task as test commit then implementation commit, but never leave the branch with failing tests except at a deliberate TDD checkpoint before the matching implementation commit.

### Task 1: Core Identifier API And Strategy-Family Model

**Files:**
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierCapability.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/BuiltInIdentifierStrategies.kt`
- Move/Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt` -> `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierGenerator.kt`
- Move/Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt` -> `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategy.kt`
- Move/Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt` -> `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategyRegistry.kt`
- Test: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt`

**Interfaces:**
- Produces: `enum class IdentifierCapability { ENTITY_ID_PREASSIGNMENT }`
- Produces: `object BuiltInIdentifierStrategies { const val UUID7 = "uuid7"; const val SNOWFLAKE = "snowflake" }`
- Produces: `interface IdentifierStrategy`
- Produces: `interface IdentifierStrategyRegistry`
- Produces: `class MapBackedIdentifierStrategyRegistry(strategies: Iterable<IdentifierStrategy>)`
- Produces: `interface IdentifierGenerator`
- Produces: `class DefaultIdentifierGenerator(private val strategyRegistry: IdentifierStrategyRegistry)`
- Consumes: existing `ApplicationSideId` annotation remains unchanged.

- [ ] **Step 1: Replace the core policy tests with failing tests for the new API**

Replace `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.reflect.KClass

class IdPolicyCoreTest {

    @Test
    fun `built in identifier strategy constants are stable`() {
        assertEquals("uuid7", BuiltInIdentifierStrategies.UUID7)
        assertEquals("snowflake", BuiltInIdentifierStrategies.SNOWFLAKE)
    }

    @Test
    fun `map backed registry returns registered strategy`() {
        val strategy = FixedStringStrategy()
        val registry = MapBackedIdentifierStrategyRegistry(listOf(strategy))

        assertSame(strategy, registry.get("fixed-string"))
    }

    @Test
    fun `registry rejects blank strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdentifierStrategyRegistry(listOf(BlankNameStrategy()))
        }

        assertEquals("identifier strategy name must not be blank", error.message)
    }

    @Test
    fun `registry rejects duplicate strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy(), FixedStringStrategy()))
        }

        assertEquals("duplicate identifier strategy: fixed-string", error.message)
    }

    @Test
    fun `registry rejects unknown strategy names`() {
        val registry = MapBackedIdentifierStrategyRegistry(emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.get("missing")
        }

        assertEquals("unknown identifier strategy: missing", error.message)
    }

    @Test
    fun `generator returns typed strategy value using KClass`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        assertEquals("ORD-1", generator.next("fixed-string", String::class))
    }

    @Test
    fun `generator returns typed strategy value using Java Class`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        assertEquals("ORD-1", generator.next("fixed-string", String::class.java))
    }

    @Test
    fun `generator rejects unsupported output type`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            generator.next("fixed-string", Long::class)
        }

        assertEquals("identifier strategy fixed-string does not support output type kotlin.Long", error.message)
    }

    @Test
    fun `business code strategy without preassignment capability remains allocatable`() {
        val strategy = FixedStringStrategy()
        val generator = DefaultIdentifierGenerator(MapBackedIdentifierStrategyRegistry(listOf(strategy)))

        assertFalse(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities)
        assertEquals("ORD-1", generator.next("fixed-string", String::class))
    }

    @Test
    fun `application side annotation still exposes strategy name`() {
        val annotation = AnnotatedEntity::class.java.getDeclaredField("id")
            .getAnnotation(ApplicationSideId::class.java)

        assertEquals("fixed-uuid", annotation.strategy)
    }

    private class FixedStringStrategy : IdentifierStrategy {
        override val name: String = "fixed-string"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "ORD-1" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == ""
    }

    private class BlankNameStrategy : IdentifierStrategy {
        override val name: String = " "
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = false
        override fun <T : Any> next(type: KClass<T>): T = error("not used")
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null
    }

    private class AnnotatedEntity {
        @field:ApplicationSideId(strategy = "fixed-uuid")
        var id: UUID = UUID(0L, 0L)
    }
}
```

- [ ] **Step 2: Run the new core tests and confirm they fail for missing new API**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.IdPolicyCoreTest"
```

Expected: FAIL with unresolved references such as `BuiltInIdentifierStrategies`, `IdentifierStrategy`, `MapBackedIdentifierStrategyRegistry`, and `DefaultIdentifierGenerator`.

- [ ] **Step 3: Move old core files to the new public names**

Run:

```powershell
git mv ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierGenerator.kt
git mv ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategy.kt
git mv ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategyRegistry.kt
```

Expected: Git records three renames. Do not remove `IdGenerationKind.kt` in this task because JPA code still references it until Task 4.

- [ ] **Step 4: Add the narrow capability enum**

Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierCapability.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

enum class IdentifierCapability {
    ENTITY_ID_PREASSIGNMENT,
}
```

- [ ] **Step 5: Add the built-in strategy constants**

Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/BuiltInIdentifierStrategies.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

object BuiltInIdentifierStrategies {
    const val UUID7 = "uuid7"
    const val SNOWFLAKE = "snowflake"
}
```

- [ ] **Step 6: Replace the strategy contract with the strategy-family API**

Replace `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategy.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdentifierStrategy {
    val name: String
    val capabilities: Set<IdentifierCapability>

    fun supports(type: KClass<*>): Boolean
    fun <T : Any> next(type: KClass<T>): T
    fun isDefaultValue(value: Any?, type: KClass<*>): Boolean
}
```

- [ ] **Step 7: Replace the registry with the identifier registry**

Replace `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierStrategyRegistry.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

interface IdentifierStrategyRegistry {
    fun get(name: String): IdentifierStrategy
}

class MapBackedIdentifierStrategyRegistry(
    strategies: Iterable<IdentifierStrategy>
) : IdentifierStrategyRegistry {
    private val strategiesByName: Map<String, IdentifierStrategy> = strategies
        .fold(linkedMapOf<String, IdentifierStrategy>()) { acc, strategy ->
            require(strategy.name.isNotBlank()) { "identifier strategy name must not be blank" }
            require(strategy.name !in acc) { "duplicate identifier strategy: ${strategy.name}" }
            acc[strategy.name] = strategy
            acc
        }

    override fun get(name: String): IdentifierStrategy =
        strategiesByName[name] ?: throw IllegalArgumentException("unknown identifier strategy: $name")
}
```

- [ ] **Step 8: Replace the allocator with the identifier generator**

Replace `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierGenerator.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdentifierGenerator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T

    fun <T : Any> next(strategy: String, type: Class<T>): T =
        next(strategy, type.kotlin)
}

class DefaultIdentifierGenerator(
    private val strategyRegistry: IdentifierStrategyRegistry
) : IdentifierGenerator {
    override fun <T : Any> next(strategy: String, type: KClass<T>): T {
        val resolved = strategyRegistry.get(strategy)
        require(resolved.supports(type)) {
            "identifier strategy $strategy does not support output type ${type.qualifiedName}"
        }
        return resolved.next(type)
    }
}
```

- [ ] **Step 9: Run the core tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.IdPolicyCoreTest"
```

Expected: PASS.

- [ ] **Step 10: Commit Task 1**

Run:

```powershell
git status --short
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt
git commit -m "feat: introduce identifier strategy family core"
```

Expected: Commit includes only core identifier API files and `IdPolicyCoreTest`.

### Task 2: Starter Built-In Strategies And Bean Registration

**Files:**
- Move/Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt` -> `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdentifierStrategy.kt`
- Move/Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt` -> `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`
- Test: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`

**Interfaces:**
- Consumes: `IdentifierStrategy`, `IdentifierCapability`, `IdentifierStrategyRegistry`, `IdentifierGenerator`, and `BuiltInIdentifierStrategies` from Task 1.
- Produces: `class Uuid7IdentifierStrategy : IdentifierStrategy`
- Produces: `class SnowflakeIdentifierStrategy(private val snowflakeIdGenerator: SnowflakeIdGenerator) : IdentifierStrategy`
- Produces: Spring beans for `uuid7IdentifierStrategy`, optional `snowflakeIdentifierStrategy`, `IdentifierStrategyRegistry`, and `IdentifierGenerator`.

- [ ] **Step 1: Replace starter auto-configuration tests with the target behavior**

Replace `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt` with:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID
import kotlin.reflect.KClass

class IdPolicyAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)

    @Test
    fun `registry exposes uuid7 by default`() {
        contextRunner.run { context ->
            val registry = context.getBean(IdentifierStrategyRegistry::class.java)
            val strategy = registry.get(BuiltInIdentifierStrategies.UUID7)

            assertTrue(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities)
            assertTrue(strategy.supports(UUID::class))
            assertTrue(strategy.supports(String::class))
            assertFalse(strategy.supports(Long::class))
            assertTrue(strategy.isDefaultValue(UUID(0L, 0L), UUID::class))
            assertTrue(strategy.isDefaultValue("", String::class))
        }
    }

    @Test
    fun `generator returns uuid7 UUID and String values without snowflake bean`() {
        contextRunner.run { context ->
            val generator = context.getBean(IdentifierGenerator::class.java)
            val first = generator.next(BuiltInIdentifierStrategies.UUID7, UUID::class)
            val second = generator.next(BuiltInIdentifierStrategies.UUID7, String::class)

            assertEquals(7, first.version())
            assertTrue(UUID.fromString(second).version() == 7)
            assertNotEquals(first.toString(), second)
        }
    }

    @Test
    fun `uuid7 rejects unsupported output type`() {
        contextRunner.run { context ->
            val generator = context.getBean(IdentifierGenerator::class.java)

            val error = assertThrows(IllegalArgumentException::class.java) {
                generator.next(BuiltInIdentifierStrategies.UUID7, Long::class)
            }

            assertEquals("identifier strategy uuid7 does not support output type kotlin.Long", error.message)
        }
    }

    @Test
    fun `registry exposes snowflake family when snowflake generator bean exists`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)
                val longId = generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, Long::class)
                val stringId = generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, String::class)

                assertTrue(longId > 0L)
                assertTrue(stringId.toLong() > 0L)
            }
    }

    @Test
    fun `snowflake rejects unsupported output type`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)

                val error = assertThrows(IllegalArgumentException::class.java) {
                    generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, UUID::class)
                }

                assertEquals("identifier strategy snowflake does not support output type java.util.UUID", error.message)
            }
    }

    @Test
    fun `snowflake long legacy name is not registered`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val registry = context.getBean(IdentifierStrategyRegistry::class.java)

                val error = assertThrows(IllegalArgumentException::class.java) {
                    registry.get("snowflake-long")
                }

                assertEquals("unknown identifier strategy: snowflake-long", error.message)
            }
    }

    @Test
    fun `application provided strategy bean is collected`() {
        ApplicationContextRunner()
            .withBean("orderNoStrategy", IdentifierStrategy::class.java, { OrderNoStrategy() })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)

                assertEquals("ORD-1", generator.next("order-no", String::class))
            }
    }

    @Test
    fun `duplicate strategy names fail fast`() {
        ApplicationContextRunner()
            .withBean("duplicateUuid7Strategy", IdentifierStrategy::class.java, { DuplicateUuid7Strategy() })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                assertNotNull(context.startupFailure)
                assertTrue(context.startupFailure!!.message!!.contains("duplicate identifier strategy: uuid7"))
            }
    }

    private class OrderNoStrategy : IdentifierStrategy {
        override val name: String = "order-no"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "ORD-1" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == ""
    }

    private class DuplicateUuid7Strategy : OrderNoStrategy() {
        override val name: String = "uuid7"
    }
}
```

- [ ] **Step 2: Run the starter ID tests and confirm they fail for missing new wiring**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest"
```

Expected: FAIL with unresolved references to `Identifier*` types in starter or missing `IdentifierGenerator` bean.

- [ ] **Step 3: Move starter strategy files to family names**

Run:

```powershell
git mv cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdentifierStrategy.kt
git mv cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt
```

Expected: Git records two renames.

- [ ] **Step 4: Implement the uuid7 strategy family**

Replace `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdentifierStrategy.kt` with:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import java.util.UUID
import kotlin.reflect.KClass

class Uuid7IdentifierStrategy : IdentifierStrategy {
    override val name: String = BuiltInIdentifierStrategies.UUID7
    override val capabilities: Set<IdentifierCapability> =
        setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)

    override fun supports(type: KClass<*>): Boolean =
        type == UUID::class || type == String::class

    override fun <T : Any> next(type: KClass<T>): T {
        require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
        val uuid = UuidCreator.getTimeOrderedEpoch()
        val value: Any = when (type) {
            UUID::class -> uuid
            String::class -> uuid.toString()
            else -> error("unreachable unsupported output type: ${type.qualifiedName}")
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
        when (type) {
            UUID::class -> value == null || value == UUID(0L, 0L)
            String::class -> value == null || value == "" || value == UUID(0L, 0L).toString()
            else -> value == null
        }
}
```

- [ ] **Step 5: Implement the snowflake strategy family**

Replace `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt` with:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import kotlin.reflect.KClass

class SnowflakeIdentifierStrategy(
    private val snowflakeIdGenerator: SnowflakeIdGenerator
) : IdentifierStrategy {
    override val name: String = BuiltInIdentifierStrategies.SNOWFLAKE
    override val capabilities: Set<IdentifierCapability> =
        setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)

    override fun supports(type: KClass<*>): Boolean =
        type == Long::class || type == String::class

    override fun <T : Any> next(type: KClass<T>): T {
        require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
        val id = snowflakeIdGenerator.nextId()
        val value: Any = when (type) {
            Long::class -> id
            String::class -> id.toString()
            else -> error("unreachable unsupported output type: ${type.qualifiedName}")
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
        when (type) {
            Long::class -> value == null || value == 0L
            String::class -> value == null || value == "" || value == "0"
            else -> value == null
        }
}
```

- [ ] **Step 6: Wire strategy beans, registry, and generator**

Replace `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt` with:

```kotlin
package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.DefaultIdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class IdPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["uuid7IdentifierStrategy"])
    fun uuid7IdentifierStrategy(): IdentifierStrategy =
        Uuid7IdentifierStrategy()

    @Bean
    @ConditionalOnBean(SnowflakeIdGenerator::class)
    @ConditionalOnMissingBean(name = ["snowflakeIdentifierStrategy"])
    fun snowflakeIdentifierStrategy(snowflakeIdGenerator: SnowflakeIdGenerator): IdentifierStrategy =
        SnowflakeIdentifierStrategy(snowflakeIdGenerator)

    @Bean
    @ConditionalOnMissingBean
    fun identifierStrategyRegistry(strategies: List<IdentifierStrategy>): IdentifierStrategyRegistry =
        MapBackedIdentifierStrategyRegistry(strategies)

    @Bean
    @ConditionalOnMissingBean
    fun identifierGenerator(identifierStrategyRegistry: IdentifierStrategyRegistry): IdentifierGenerator =
        DefaultIdentifierGenerator(identifierStrategyRegistry)
}
```

- [ ] **Step 7: Run the starter ID tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest"
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

Run:

```powershell
git status --short
git add cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt
git commit -m "feat: wire identifier strategy families"
```

Expected: Commit includes starter strategy renames, auto-configuration, and `IdPolicyAutoConfigurationTest`.
