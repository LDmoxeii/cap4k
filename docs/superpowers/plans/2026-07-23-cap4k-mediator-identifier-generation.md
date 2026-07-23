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

    private open class OrderNoStrategy : IdentifierStrategy {
        override open val name: String = "order-no"
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

### Task 3: Mediator Identifiers Facade

**Files:**
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/MediatorSupport.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt`
- Test: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`

**Interfaces:**
- Consumes: `IdentifierGenerator` from Task 1 and Spring bean from Task 2.
- Produces: `Mediator.identifiers: IdentifierGenerator` instance property.
- Produces: `Mediator.identifiers` companion shortcut.
- Produces: `DefaultMediator(override val identifiers: IdentifierGenerator = MediatorSupport.identifiers)`.
- Produces: `MediatorSupport.configure(identifierGenerator: IdentifierGenerator)`.

- [ ] **Step 1: Replace mediator forwarding tests with identifier facade coverage**

Replace `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import kotlin.reflect.KClass

class DefaultMediatorTest {

    @Test
    fun `persist forwards entity and intent to configured unit of work`() {
        val unitOfWork = RecordingUnitOfWork()
        UnitOfWorkSupport.configure(unitOfWork)
        val entity = Any()

        DefaultMediator(RecordingIdentifierGenerator()).persist(entity, PersistIntent.CREATE)

        assertSame(entity, unitOfWork.persistedEntity)
        assertEquals(PersistIntent.CREATE, unitOfWork.persistedIntent)
    }

    @Test
    fun `identifiers property delegates to configured generator`() {
        val generator = RecordingIdentifierGenerator()
        val mediator = DefaultMediator(generator)

        val id = mediator.identifiers.next("order-no", String::class)

        assertEquals("ID-1", id)
        assertEquals("order-no", generator.strategy)
        assertEquals(String::class, generator.type)
    }

    @Test
    fun `companion identifiers shortcut delegates to configured generator`() {
        val generator = RecordingIdentifierGenerator()
        MediatorSupport.configure(generator)

        val id = Mediator.identifiers.next("order-no", String::class.java)

        assertEquals("ID-1", id)
        assertEquals("order-no", generator.strategy)
        assertEquals(String::class, generator.type)
    }

    @Test
    fun `identifier generation does not touch unit of work`() {
        val unitOfWork = RecordingUnitOfWork()
        UnitOfWorkSupport.configure(unitOfWork)

        DefaultMediator(RecordingIdentifierGenerator()).identifiers.next("order-no", String::class)

        assertEquals(0, unitOfWork.persistCalls)
        assertEquals(0, unitOfWork.removeCalls)
        assertEquals(0, unitOfWork.saveCalls)
    }

    private class RecordingIdentifierGenerator : IdentifierGenerator {
        var strategy: String? = null
        var type: KClass<*>? = null

        override fun <T : Any> next(strategy: String, type: KClass<T>): T {
            this.strategy = strategy
            this.type = type
            @Suppress("UNCHECKED_CAST")
            return "ID-1" as T
        }
    }

    private class RecordingUnitOfWork : UnitOfWork {
        var persistedEntity: Any? = null
        var persistedIntent: PersistIntent? = null
        var persistCalls: Int = 0
        var removeCalls: Int = 0
        var saveCalls: Int = 0

        override fun persist(entity: Any, intent: PersistIntent) {
            persistCalls++
            persistedEntity = entity
            persistedIntent = intent
        }

        override fun remove(entity: Any) {
            removeCalls++
        }

        override fun save(propagation: Propagation) {
            saveCalls++
        }
    }
}
```

- [ ] **Step 2: Run mediator tests and confirm they fail for missing facade**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest"
```

Expected: FAIL with unresolved `identifiers` references or missing `DefaultMediator(IdentifierGenerator)` constructor.

- [ ] **Step 3: Add identifier generator storage to MediatorSupport**

Update `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/MediatorSupport.kt` to:

```kotlin
package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import org.springframework.context.ApplicationContext

/**
 * 中介者配置
 *
 * @author LD_moxeii
 * @date 2025/07/22
 */
object MediatorSupport {
    lateinit var instance: Mediator
    lateinit var ioc: ApplicationContext
    lateinit var identifiers: IdentifierGenerator

    fun configure(mediator: Mediator) {
        instance = mediator
    }

    fun configure(applicationContext: ApplicationContext) {
        ioc = applicationContext
    }

    fun configure(identifierGenerator: IdentifierGenerator) {
        identifiers = identifierGenerator
    }
}
```

- [ ] **Step 4: Expose identifiers from Mediator**

Update `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt` by adding the import:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
```

Add this instance property inside `interface Mediator`:

```kotlin
    val identifiers: IdentifierGenerator
        get() = MediatorSupport.identifiers
```

Add this companion shortcut after `ioc`:

```kotlin
        @JvmStatic
        val identifiers: IdentifierGenerator
            get() = MediatorSupport.identifiers
```

- [ ] **Step 5: Inject identifiers into DefaultMediator**

Change the class declaration in `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt` from:

```kotlin
class DefaultMediator : Mediator {
```

to:

```kotlin
class DefaultMediator(
    override val identifiers: IdentifierGenerator = MediatorSupport.identifiers
) : Mediator {
```

Add imports:

```kotlin
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
```

Keep all existing forwarding methods unchanged.

- [ ] **Step 6: Configure DefaultMediator with the generator bean**

Replace `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt` with:

```kotlin
package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.impl.DefaultMediator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CQS自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
class MediatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Mediator::class)
    fun defaultMediator(
        applicationContext: ApplicationContext,
        identifierGenerator: IdentifierGenerator,
    ): DefaultMediator =
        DefaultMediator(identifierGenerator).also {
            MediatorSupport.configure(it)
            MediatorSupport.configure(applicationContext)
            MediatorSupport.configure(identifierGenerator)
        }
}
```

- [ ] **Step 7: Run mediator tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest"
```

Expected: PASS.

- [ ] **Step 8: Run starter context smoke for mediator wiring**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.AutoConfigurationContextTest"
```

Expected at this point: may fail only where tests still assert old `IdAllocator`/`IdStrategyRegistry` bean types. Do not fix those assertions until Task 5 unless the failure blocks context startup.

- [ ] **Step 9: Commit Task 3**

Run:

```powershell
git status --short
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/MediatorSupport.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt
git commit -m "feat: expose mediator identifier generator"
```

Expected: Commit contains only Mediator facade changes and tests.

### Task 4: JPA ApplicationSideId Capability Migration

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `IdentifierStrategyRegistry` and `IdentifierCapability.ENTITY_ID_PREASSIGNMENT`.
- Produces: JPA-only capability enforcement for entity ID preassignment.
- Preserves: `ApplicationSideIdMember` shape and existing owned relation traversal.
- Preserves: `JpaUnitOfWork` `PersistIntent.CREATE/UPDATE` behavior.

- [ ] **Step 1: Update JPA support test imports and strategy helpers**

In `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`, replace old ID imports:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import kotlin.reflect.KClass
```

Replace every `MapBackedIdStrategyRegistry(...)` call with `MapBackedIdentifierStrategyRegistry(...)`.

Replace the helper strategy classes with:

```kotlin
    private class FixedUuidStrategy : IdentifierStrategy {
        override val name: String = "uuid7"
        override val capabilities: Set<IdentifierCapability> =
            setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(1L, 2L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
            value == null || value == UUID(0L, 0L)
    }

    private class NonPreassigningUuidStrategy : IdentifierStrategy {
        override val name: String = "uuid7"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(1L, 2L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
            value == null || value == UUID(0L, 0L)
    }

    private class StringIdStrategy : IdentifierStrategy {
        override val name: String = "string-id"
        override val capabilities: Set<IdentifierCapability> =
            setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "not-a-uuid" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
            value == null || value == ""
    }

    private class DefaultUuidStrategy : IdentifierStrategy {
        override val name: String = "default-uuid"
        override val capabilities: Set<IdentifierCapability> =
            setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(0L, 0L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
            value == null || value == UUID(0L, 0L)
    }
```

Replace `nullUuidStrategy()` with:

```kotlin
    private fun nullUuidStrategy(): IdentifierStrategy =
        Proxy.newProxyInstance(
            IdentifierStrategy::class.java.classLoader,
            arrayOf(IdentifierStrategy::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getName" -> "null-uuid"
                "getCapabilities" -> setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
                "supports" -> args?.single() == UUID::class
                "isDefaultValue" -> args?.get(0) == null || args?.get(0) == UUID(0L, 0L)
                "next" -> null
                else -> error("unexpected method: ${method.name}")
            }
        } as IdentifierStrategy
```

- [ ] **Step 2: Replace the database-side rejection test with capability rejection**

Replace the existing `rejects database side strategy` test in `JpaApplicationSideIdSupportTest` with:

```kotlin
    @Test
    fun `rejects strategy without entity id preassignment capability`() {
        val nonPreassigningSupport = JpaApplicationSideIdSupport(
            MapBackedIdentifierStrategyRegistry(listOf(NonPreassigningUuidStrategy()))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            nonPreassigningSupport.assignMissingIds(RootEntity())
        }

        assertEquals("identifier strategy uuid7 does not support entity ID preassignment", error.message)
    }
```

- [ ] **Step 3: Update the output type mismatch assertion**

Replace the expected message in `rejects strategy output type mismatch before assignment` with:

```kotlin
        assertEquals(
            "identifier strategy string-id does not support output type java.util.UUID for field " +
                "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$StringStrategyEntity.id",
            error.message
        )
```

- [ ] **Step 4: Run JPA support tests and confirm they fail on production imports**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest"
```

Expected: FAIL because production code still imports `IdGenerationKind`, `IdStrategy`, and `IdStrategyRegistry`.

- [ ] **Step 5: Migrate JpaApplicationSideIdSupport to the new registry and capability**

In `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`, replace imports:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import kotlin.reflect.KClass
```

Change the constructor parameter to:

```kotlin
    private val idStrategyRegistry: IdentifierStrategyRegistry
```

Replace `isDefaultId(...)` with:

```kotlin
    fun isDefaultId(member: ApplicationSideIdMember, entity: Any): Boolean {
        val strategy = idStrategyRegistry.get(member.annotation.strategy)
        requireEntityIdPreassignment(strategy)
        return strategy.isDefaultValue(member.get(entity), member.field.type.kotlin)
    }
```

Replace the private `requireApplicationSide(...)`, `validateOutputType(...)`, and `nextValue(...)` helpers with:

```kotlin
    private fun requireEntityIdPreassignment(strategy: IdentifierStrategy) {
        require(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities) {
            "identifier strategy ${strategy.name} does not support entity ID preassignment"
        }
    }

    private fun validateOutputType(strategy: IdentifierStrategy, member: ApplicationSideIdMember) {
        val fieldType = member.field.type.kotlin
        require(strategy.supports(fieldType)) {
            "identifier strategy ${strategy.name} does not support output type ${member.field.type.name} for field " +
                "${member.field.declaringClass.name}.${member.field.name}"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun nextValue(strategy: IdentifierStrategy, member: ApplicationSideIdMember): Any? {
        val fieldType = member.field.type.kotlin as KClass<Any>
        return strategy.next(fieldType)
    }
```

In `assignMissingIds(...)`, replace:

```kotlin
            requireApplicationSide(strategy)
```

with:

```kotlin
            requireEntityIdPreassignment(strategy)
```

Replace:

```kotlin
                val generated = nextValue(strategy)
```

with:

```kotlin
                val generated = nextValue(strategy, member)
```

Replace:

```kotlin
                check(!strategy.isDefaultValue(generated)) {
```

with:

```kotlin
                check(!strategy.isDefaultValue(generated, member.field.type.kotlin)) {
```

- [ ] **Step 6: Update JpaUnitOfWork constructor types**

In `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`, replace imports:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
```

Replace constructor defaults:

```kotlin
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
```

with:

```kotlin
    idStrategyRegistry: IdentifierStrategyRegistry = MapBackedIdentifierStrategyRegistry(emptyList()),
```

Replace every remaining `MapBackedIdStrategyRegistry(emptyList())` in the file with `MapBackedIdentifierStrategyRegistry(emptyList())`.

- [ ] **Step 7: Update JPA repository auto-configuration injection type**

In `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`, replace:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
```

Change the `jpaUnitOfWork(...)` parameter:

```kotlin
        idStrategyRegistry: IdentifierStrategyRegistry,
```

- [ ] **Step 8: Update JpaUnitOfWorkTest strategy fixture**

In `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`, replace old imports with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import kotlin.reflect.KClass
```

Replace `MapBackedIdStrategyRegistry(...)` with `MapBackedIdentifierStrategyRegistry(...)`.

Replace the `FixedLongStrategy` helper with:

```kotlin
    private class FixedLongStrategy : IdentifierStrategy {
        override val name: String = BuiltInIdentifierStrategies.SNOWFLAKE
        override val capabilities: Set<IdentifierCapability> =
            setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == Long::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return 1001L as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
            value == null || value == 0L
    }
```

Change any test fixture annotation from:

```kotlin
    @ApplicationSideId(strategy = "snowflake-long")
```

to:

```kotlin
    @ApplicationSideId(strategy = "snowflake")
```

- [ ] **Step 9: Run JPA support and UoW tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected: PASS.

- [ ] **Step 10: Commit Task 4**

Run:

```powershell
git status --short
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: enforce identifier preassignment capability in jpa"
```

Expected: Commit contains only JPA capability migration and related tests.

### Task 5: Global Cleanup And Verification

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationContextTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/test/runtime/appsideid/ApplicationSideIdRuntimeFixtures.kt` only if it imports old core types.
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt` after no references remain.
- Search and update: remaining production/test references in `ddd-core`, `ddd-domain-repo-jpa`, and `cap4k-ddd-starter`.

**Interfaces:**
- Consumes: all interfaces from Tasks 1-4.
- Produces: no new API.
- Produces: repository state with no old public ID runtime names in production or current tests.

- [ ] **Step 1: Update starter context bean assertions**

In `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/AutoConfigurationContextTest.kt`, replace imports:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
```

with:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
```

Replace bean assertions:

```kotlin
            assertNotNull(context.getBean(IdStrategyRegistry::class.java))
            assertNotNull(context.getBean(IdAllocator::class.java))
```

with:

```kotlin
            assertNotNull(context.getBean(IdentifierStrategyRegistry::class.java))
            assertNotNull(context.getBean(IdentifierGenerator::class.java))
```

- [ ] **Step 2: Update snowflake-long runtime fixtures**

Run:

```powershell
rg -n "\"snowflake-long\"" cap4k-ddd-starter/src/test ddd-domain-repo-jpa/src/test ddd-core/src/test --glob "*.kt"
```

Expected before cleanup: matches only in active tests or fixtures, not in production after Tasks 1-4.

For every active test fixture match in current test sources, replace:

```kotlin
@ApplicationSideId(strategy = "snowflake-long")
```

with:

```kotlin
@ApplicationSideId(strategy = "snowflake")
```

Do not edit old historical plan files under `docs/superpowers/plans/**`.

- [ ] **Step 3: Delete IdGenerationKind after reference cleanup**

Run:

```powershell
rg -n "IdGenerationKind" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter --glob "*.kt"
```

Expected before deletion: no matches except `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt`.

Delete the file:

```powershell
git rm ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdGenerationKind.kt
```

- [ ] **Step 4: Run static old API search**

Run:

```powershell
rg -n "IdAllocator|DefaultIdAllocator|IdStrategy\\b|IdStrategyRegistry|MapBackedIdStrategyRegistry|IdGenerationKind|SnowflakeLongIdStrategy" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter --glob "*.kt"
```

Expected: no matches.

Run:

```powershell
rg -n "\"snowflake-long\"" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter --glob "*.kt"
```

Expected: no matches.

Run:

```powershell
rg -n "IdentifierGenerator" ddd-distributed-snowflake/src/main/kotlin --glob "*.kt"
```

Expected: matches may include `org.hibernate.id.IdentifierGenerator` in `SnowflakeIdentifierGenerator.kt`; do not edit that file unless a compiler error proves an import conflict.

- [ ] **Step 5: Run focused Gradle verification**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.IdPolicyCoreTest" --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest"
```

Expected: PASS.

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected: PASS.

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest" --tests "com.only4.cap4k.ddd.AutoConfigurationContextTest"
```

Expected: PASS.

- [ ] **Step 6: Run broader affected module tests if focused verification is green**

Run:

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test
```

Expected: PASS. If this is too slow or environment-limited, record the exact skipped command and the focused evidence from Step 5 in the final implementation report.

- [ ] **Step 7: Commit Task 5**

Run:

```powershell
git status --short
git add ddd-core ddd-domain-repo-jpa cap4k-ddd-starter
git commit -m "chore: remove legacy id allocation runtime names"
```

Expected: Commit contains cleanup and verification-related fixture changes only.

## Coverage Checklist

- `BuiltInIdentifierStrategies.UUID7 = "uuid7"`: Task 1 test and implementation.
- `BuiltInIdentifierStrategies.SNOWFLAKE = "snowflake"`: Task 1 test and implementation.
- `IdentifierGenerator` KClass overload: Task 1.
- `IdentifierGenerator` Java `Class<T>` overload: Task 1 and Task 3 companion shortcut test.
- `uuid7` supports `String` and `UUID`: Task 2.
- `uuid7` rejects `Long`: Task 2.
- `snowflake` supports `Long` and decimal `String`: Task 2.
- `snowflake` rejects `UUID`: Task 2.
- `snowflake-long` removed with no alias: Task 2 and Task 5.
- External strategy beans collected: Task 2.
- Duplicate strategy names fail fast: Task 1 and Task 2.
- Unknown strategy names fail fast: Task 1 and Task 2.
- `mediator.identifiers`: Task 3.
- `Mediator.identifiers`: Task 3.
- Identifier generation has no UoW side effect: Task 3.
- JPA entity ID assignment requires `ENTITY_ID_PREASSIGNMENT`: Task 4.
- Business-code strategy without `ENTITY_ID_PREASSIGNMENT` remains usable: Task 1 and Task 2.
- Strong ID wrapper generation remains out of scope: Global Constraints and no task creates wrapper generation.
- UoW `PersistIntent.CREATE/UPDATE` remains unchanged: Task 4 explicitly preserves UoW semantics.

## Self-Review

Spec coverage: The plan maps every Phase 3 public API, strategy matrix, registry rule, Mediator facade rule, JPA capability boundary, and verification evidence item to at least one task. Phase 2 all-entity Strong ID work and Phase 4 create-time Strong ID injection remain out of scope.

Placeholder scan: The plan contains no unresolved placeholder language. Each code-changing step includes the exact code snippet or replacement command needed for that step.

Type consistency: The plan uses `IdentifierGenerator`, `IdentifierStrategy`, `IdentifierStrategyRegistry`, `MapBackedIdentifierStrategyRegistry`, `IdentifierCapability.ENTITY_ID_PREASSIGNMENT`, and `BuiltInIdentifierStrategies` consistently across tasks. The public strategy constants are always `uuid7` and `snowflake`.

Risk notes: `SnowflakeIdentifierGenerator` in `ddd-distributed-snowflake` is a Hibernate generator and is intentionally not renamed in this plan. `IdPolicyAutoConfiguration` remains named as-is to avoid unrelated Spring auto-configuration import churn; it wires the new identifier runtime internally.

## Execution Handoff

Plan complete when this file is committed. Use one of these execution modes:

1. **Subagent-Driven (recommended)**: dispatch a fresh subagent per task, review after each task, and commit at every task boundary.
2. **Inline Execution**: execute tasks in this session using `superpowers:executing-plans`, with a checkpoint after each task commit.
