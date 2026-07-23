# cap4k Mediator Identifier Generation Design

Date: 2026-07-23

Status as of 2026-07-23: design approved for spec authoring after expanding the scope from entity-ID-specific runtime support to generic identifier generation. Implementation has not been dispatched and no Phase 3 implementation PR has been merged into `master`.

## Reader Contract

This is the phase 3 design spec for the cap4k identity roadmap. It is written for an implementation agent who has no chat history.

Read this spec together with:

- [2026-07-22-cap4k-identity-roadmap-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md>)
- [2026-07-22-cap4k-uow-persist-intent-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-22-cap4k-uow-persist-intent-design.md>)
- [2026-07-22-cap4k-all-entity-strong-id-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md>)

This spec is not an implementation plan. It fixes the design boundary for exposing generic identifier generation through `Mediator`, expanding built-in strategy output types, and keeping external identifier strategy registration open.

## businessIntent

Application handlers sometimes need an identifier before persistence or outside persistence entirely:

- a command response must return the new ID before `save()`;
- a domain event or integration event must carry the ID before persistence;
- an external call or idempotency key must reference a new entity;
- user-authored construction intentionally passes an ID into a payload or constructor;
- application code needs a distributed globally unique business key or technical key that is not an entity primary key;
- application code needs a business code such as `BIZ` + date + sequence through the same extension mechanism;
- application code needs the same identifier generation surface as generated/framework code without depending on JPA or low-level runtime beans.

The desired application-facing shape is:

```kotlin
val id = mediator.identifiers.next(BuiltInIdentifierStrategies.UUID7, String::class)
val snowflake = mediator.identifiers.next(BuiltInIdentifierStrategies.SNOWFLAKE, Long::class)
val orderNo = mediator.identifiers.next("order-no", String::class)
```

Phase 3 is about manual primitive/runtime identifier generation. It is not the generated Strong ID create-time injection phase.

## Current Evidence

Current master evidence:

- `IdAllocator` exists in `ddd-core` and exposes `next(strategy: String, type: KClass<T>)`.
- Production code does not currently call `IdAllocator` from `JpaUnitOfWork`; it is exposed as a bean and covered by tests.
- `JpaUnitOfWork` uses `IdStrategyRegistry` directly through JPA application-side ID support.
- `DefaultIdAllocator` delegates to `IdStrategyRegistry`, rejects non-application-side strategies, and checks output type assignability.
- `IdStrategy` currently has one `outputType` per strategy and carries persistence-oriented metadata.
- `Uuid7IdStrategy` is registered by default with name `uuid7`, output type `UUID`, and `preassignable=true`.
- `SnowflakeLongIdStrategy` exists with name `snowflake-long`, output type `Long`, and `preassignable=true`.
- `IdPolicyAutoConfiguration` currently builds a registry from `Uuid7IdStrategy()` plus `SnowflakeLongIdStrategy(...)` when a `SnowflakeIdGenerator` bean exists.
- `Mediator` does not expose `IdAllocator`.
- `StrongIds.newUuidV7String()` already generates canonical UUIDv7 strings for generated Strong IDs, but it is not surfaced through `IdAllocator`.

Phase 3 intentionally changes public ID strategy semantics. There are no external users, so current `IdAllocator`/`IdStrategy` names may be renamed instead of wrapped for compatibility. No compatibility alias is required for `snowflake-long`.

## External Design Notes

CosId was reviewed only as API inspiration. Phase 3 does not add CosId as a dependency.

Useful ideas:

- named generators/providers keep business code away from concrete ID generator implementations;
- generation and string conversion should be separate concerns;
- Snowflake IDs should be available as strings for front-end and JSON boundaries where 64-bit numeric precision is unsafe.

Relevant references:

- [CosId repository](https://github.com/Ahoo-Wang/CosId)
- [CosId SnowflakeId guide](https://cosid.ahoo.me/guide/snowflake.html)
- [CosId IdConverter guide](https://cosid.ahoo.me/guide/id-converter.html)

## ubiquitousLanguage

- **Identifier generation**: asking cap4k for a new identifier value without creating, persisting, or registering an entity.
- **Entity ID preassignment**: assigning a primary-key value before persistence. This is a JPA/entity-ID capability check, not the whole purpose of the generator API.
- **Business code**: an application-defined identifier such as an order number, invite code, or external correlation key. It can use the same generation facade without being an entity ID.
- **Strategy name**: the public string key such as `uuid7` or `snowflake`.
- **Strategy family**: one public strategy that may support multiple output types.
- **Output type**: requested runtime representation such as `String`, `UUID`, or `Long`.
- **Built-in strategy constant**: a core constant for framework-provided strategy names.
- **External strategy**: an application-provided `IdentifierStrategy` bean registered under its own name.
- **Strong ID wrapper**: generated type such as `OrderId`; not allocated by Phase 3.

## cap4kCarriers

This phase changes runtime/facade carriers, not business tactical carriers.

- **Mediator**: exposes a discoverable identifier generation entry.
- **IdentifierGenerator**: becomes the public generation facade. It supersedes `IdAllocator`.
- **BuiltInIdentifierStrategies**: provides stable constants for built-in strategy names.
- **IdentifierStrategy**: becomes a strategy-family interface that can support multiple output types.
- **IdentifierStrategyRegistry**: registers built-in and external strategies by name.
- **IdentifierCapability**: records narrow capabilities such as entity ID preassignment without adding persistence-kind semantics to the generic generator.
- **Starter auto-configuration**: wires built-in strategies and collects application strategies.

## cleanArchitecturePlacement

- `ddd-core` owns `IdentifierGenerator`, `IdentifierStrategy`, `IdentifierStrategyRegistry`, `BuiltInIdentifierStrategies`, `IdentifierCapability`, and the Mediator-facing facade contract.
- `cap4k-ddd-starter` owns Spring bean discovery and default strategy wiring.
- `ddd-distributed-snowflake` and starter modules may provide the current Snowflake generator implementation.
- JPA modules must not be required to call `mediator.identifiers`.
- JPA application-side ID support may consume `IdentifierStrategyRegistry`, but it must apply entity-ID-specific capability checks there.
- Generator modules are not required for this phase.

## Public API

### Built-In Strategy Constants

Add constants in `ddd-core`:

```kotlin
object BuiltInIdentifierStrategies {
    const val UUID7 = "uuid7"
    const val SNOWFLAKE = "snowflake"
}
```

Do not use an enum or sealed class for strategy names. Built-ins should be discoverable, but external strategy names must remain open.

### IdentifierGenerator

Keep the strategy-plus-output-type shape:

```kotlin
interface IdentifierGenerator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T
    fun <T : Any> next(strategy: String, type: Class<T>): T
}
```

The `Class<T>` overload is required for Java callers. It may delegate to the `KClass<T>` overload.

Do not add `mediator.nextId(...)` in Phase 3. The chosen application surface is:

```kotlin
mediator.identifiers.next(BuiltInIdentifierStrategies.UUID7, String::class)
mediator.identifiers.next("order-no", String::class)
```

### Mediator

Expose generation through a property:

```kotlin
interface Mediator {
    val identifiers: IdentifierGenerator
}
```

The companion object should expose the same facility consistently with existing shortcuts:

```kotlin
Mediator.identifiers.next(BuiltInIdentifierStrategies.UUID7, String::class.java)
```

Recommended shortcut names:

- `Mediator.identifiers`
- instance property `mediator.identifiers`

Avoid adding multiple names such as `id`, `ids`, `allocator`, `generator`, and `nextId` at once. A small facade is easier to keep stable.

## Strategy Model

Phase 3 replaces the current one-output strategy model with a strategy-family model:

```kotlin
enum class IdentifierCapability {
    ENTITY_ID_PREASSIGNMENT
}

interface IdentifierStrategy {
    val name: String
    val capabilities: Set<IdentifierCapability>

    fun supports(type: KClass<*>): Boolean
    fun <T : Any> next(type: KClass<T>): T
    fun isDefaultValue(value: Any?, type: KClass<*>): Boolean
}
```

Rules:

- `name` is the public strategy-family name.
- `IdGenerationKind` must not be part of this generic API.
- `capabilities` expresses narrow optional uses. In Phase 3 the only required built-in capability is `ENTITY_ID_PREASSIGNMENT`.
- A registered strategy is generally allocatable through `IdentifierGenerator`; it does not need a `GENERAL_ALLOCATION` capability.
- Built-in `uuid7` and `snowflake` must declare `ENTITY_ID_PREASSIGNMENT`.
- Business-code strategies such as `order-no` normally omit `ENTITY_ID_PREASSIGNMENT` unless the application intentionally allows that strategy to be used as an entity primary-key source.
- `supports(type)` defines the allowed output matrix.
- `next(type)` must fail fast when `type` is unsupported.
- `isDefaultValue(value, type)` must be output-type aware.

`DefaultIdentifierGenerator` should no longer validate against a single `outputType` and should not require an application-side persistence kind. It should resolve the strategy, require `supports(type)`, and call `next(type)`.

JPA application-side ID support is the only Phase 3 path that cares whether a strategy can be used to fill entity primary keys. It must require `IdentifierCapability.ENTITY_ID_PREASSIGNMENT` before assigning an ID to an entity. Database-side generation remains a persistence mapping concern and must not appear as a generic `IdentifierStrategy` kind.

## Built-In Strategy Matrix

Required built-in behavior:

| Strategy | `String` | `UUID` | `Long` |
|---|---|---|---|
| `BuiltInIdentifierStrategies.UUID7` / `uuid7` | supported | supported | fail fast |
| `BuiltInIdentifierStrategies.SNOWFLAKE` / `snowflake` | supported | fail fast | supported |

### UUID7

`uuid7` must support:

- `String`: canonical lowercase hyphenated UUIDv7 string.
- `UUID`: `java.util.UUID` with version 7 and non-zero value.

`uuid7` must reject:

- `Long`
- arbitrary numeric types
- Strong ID wrapper classes

String output should be equivalent in format to `StrongIds.newUuidV7String()`.

### Snowflake

`snowflake` must support:

- `Long`: the raw Snowflake ID.
- `String`: decimal base-10 string representation of the same generated `Long`.

`snowflake` must reject:

- `UUID`
- arbitrary object types
- Strong ID wrapper classes

The default string representation must be decimal `long.toString()`, not radix62, radix36, friendly format, or prefixed format. Rich converters are future distributed-ID work, not Phase 3.

The public `snowflake-long` strategy name is removed. Do not preserve it as a compatibility alias.

## Registry And Extension

`IdentifierStrategyRegistry` should remain name-based:

```kotlin
interface IdentifierStrategyRegistry {
    fun get(name: String): IdentifierStrategy
}
```

Starter auto-configuration should collect available `IdentifierStrategy` beans:

- built-in `uuid7` strategy bean;
- built-in `snowflake` strategy bean when a `SnowflakeIdGenerator` bean exists;
- application-provided `IdentifierStrategy` beans.

Registration rules:

- duplicate strategy names fail fast;
- blank strategy names fail fast;
- unknown strategy names fail fast;
- external strategies use the same `IdentifierStrategy` interface as built-ins;
- replacing the entire `IdentifierStrategyRegistry` bean remains possible through Spring override conventions, but should not be required for normal extension.

Example extension:

```kotlin
@Bean
fun orderNoStrategy(): IdentifierStrategy = OrderNoStrategy()

val orderNo = mediator.identifiers.next("order-no", String::class)
```

## Behavior Rules

- Identifier generation has no UoW side effect.
- Identifier generation must not call `persist(...)`, `remove(...)`, or `save(...)`.
- Identifier generation does not classify an entity as create or existing.
- Identifier generation does not inspect JPA metadata.
- Database-side entity ID generation cannot be invoked through this API because it is not a runtime `IdentifierStrategy`.
- Entity ID preassignment must require `IdentifierCapability.ENTITY_ID_PREASSIGNMENT` in JPA-specific support code.
- Unsupported output type fails fast with a diagnostic that includes strategy name and requested type.
- Unknown strategy fails fast with a diagnostic that includes the strategy name.
- Generated Strong ID wrapper generation is not supported in Phase 3.
- Generation returns a new value each call unless the external strategy intentionally documents deterministic behavior.

## Java Usage

Java callers should be able to use:

```java
String id = mediator.getIdentifiers().next(BuiltInIdentifierStrategies.UUID7, String.class);
Long snowflake = Mediator.getIdentifierGenerator().next(BuiltInIdentifierStrategies.SNOWFLAKE, Long.class);
```

Kotlin keeps `Mediator.identifiers` for both instance and companion usage. Java uses `mediator.getIdentifiers()` for the instance property and `Mediator.getIdentifierGenerator()` for the companion property. The static accessor cannot also be named `getIdentifiers()` because the JVM interface would then contain conflicting instance and static methods with the same name and signature. The public Java interop test uses these compiled names.

## Relationship To Other Phases

Phase 1:

- Already merged to `master`.
- Phase 3 must not change UoW `CREATE/UPDATE` or Phase 2's planned `CREATE/EXISTING` design.

Phase 2:

- May be implemented in parallel.
- Phase 3 does not depend on all-entity Strong ID metadata.
- If Phase 2 changes ID strategy metadata names, Phase 3 must keep runtime built-in constants stable as `uuid7` and `snowflake`.
- If Phase 2 keeps JPA-specific ID policy metadata, Phase 3 must not force that metadata into the generic identifier generation API.

Phase 4:

- Strong ID wrapper generation such as `mediator.identifiers.next(OrderId::class)` is out of scope.
- Generated Strong ID create-time injection may later use `IdentifierGenerator` internally, but that is not required here.

Future distributed ID work:

- may replace or redesign the current Snowflake implementation;
- may add converters such as radix62, safe-JavaScript numeric IDs, friendly Snowflake strings, or named generator providers;
- must preserve the Phase 3 facade shape unless a later spec explicitly supersedes it.

## Code Map

Start implementation research from these current files. Several names are expected to change during implementation:

- [IdAllocator.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt>)
- [IdStrategy.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt>)
- [IdStrategyRegistry.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategyRegistry.kt>)
- [StrongIds.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt>)
- [Mediator.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt>)
- [DefaultMediator.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt>)
- [MediatorAutoConfiguration.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt>)
- [IdPolicyAutoConfiguration.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt>)
- [Uuid7IdStrategy.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdStrategy.kt>)
- [SnowflakeLongIdStrategy.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeLongIdStrategy.kt>)
- [SnowflakeAutoConfiguration.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/distributed/SnowflakeAutoConfiguration.kt>)
- [IdPolicyAutoConfigurationTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt>)
- [IdPolicyCoreTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt>)

Expected target renames:

- `IdAllocator` -> `IdentifierGenerator`
- `DefaultIdAllocator` -> `DefaultIdentifierGenerator`
- `IdStrategy` -> `IdentifierStrategy`
- `IdStrategyRegistry` -> `IdentifierStrategyRegistry`
- `BuiltInIdStrategies` -> `BuiltInIdentifierStrategies`
- `SnowflakeLongIdStrategy` -> a family strategy such as `SnowflakeIdentifierStrategy`

Keep `IdGenerationKind` only if a separate JPA/entity-ID policy type still needs it. It must not remain on `IdentifierStrategy`.

## Implementation Boundary

Likely implementation areas:

- `ddd-core` identifier API and support object;
- `Mediator` and `DefaultMediator` facade exposure;
- `cap4k-ddd-starter` built-in strategy beans and registry wiring;
- existing ID policy tests;
- new mediator identifier generation tests.

Out of scope:

- Strong ID wrapper generation;
- generated Strong ID constructor/factory injection;
- generator canonical model changes;
- UoW persistence intent changes;
- JPA save-time ID assignment behavior;
- replacing the Snowflake algorithm implementation;
- rich ID conversion/format configuration.

## Verification Evidence

Static and focused runtime evidence should prove:

- `BuiltInIdentifierStrategies.UUID7` is `uuid7`.
- `BuiltInIdentifierStrategies.SNOWFLAKE` is `snowflake`.
- `IdentifierGenerator.next("uuid7", String::class)` returns canonical UUIDv7 string values.
- `IdentifierGenerator.next("uuid7", UUID::class)` returns UUID version 7 values.
- `IdentifierGenerator.next("uuid7", Long::class)` fails fast.
- `IdentifierGenerator.next("snowflake", Long::class)` returns positive Snowflake IDs when a `SnowflakeIdGenerator` bean exists.
- `IdentifierGenerator.next("snowflake", String::class)` returns decimal strings that parse back to generated Snowflake longs.
- `IdentifierGenerator.next("snowflake", UUID::class)` fails fast.
- `snowflake-long` is not registered.
- duplicate external strategy names fail fast.
- application-provided `IdentifierStrategy` beans are collected without replacing the whole registry.
- `mediator.identifiers` delegates to the configured `IdentifierGenerator`.
- `Mediator.identifiers` exposes the same generator for Java/static-style usage.
- identifier generation does not register UoW changes.
- JPA save-time entity ID assignment rejects strategies that do not declare `ENTITY_ID_PREASSIGNMENT`.
- a business-code external strategy without `ENTITY_ID_PREASSIGNMENT` is still usable through `mediator.identifiers`.

Recommended test classes to update or add:

- `IdPolicyCoreTest`
- `IdPolicyAutoConfigurationTest`
- `DefaultMediatorTest`
- a Java interop test if the project already has Java-facing API tests in the relevant module

## Risks

- If `IdentifierStrategy` is introduced without updating JPA application-side ID support, save-time ID assignment can break. Implementation must update every production caller of `IdStrategy.outputType`, `IdStrategy.kind`, and `IdStrategy.next()`.
- If `snowflake` string output uses a non-decimal format in Phase 3, downstream schema and API expectations will be harder to stabilize. Keep decimal string now.
- If `Mediator` exposes both `idAllocator`, `identifiers`, and multiple shortcut methods, the API surface will fragment. Keep one primary property and one companion shortcut.
