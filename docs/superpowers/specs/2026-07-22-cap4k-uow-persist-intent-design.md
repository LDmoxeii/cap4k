# cap4k UoW Persist Intent Design

Date: 2026-07-22

## Reader Contract

This spec is the phase 1 runtime contract for cap4k identity work. A newcomer should be able to implement it without knowing the chat history.

Read this file together with the current code files listed below. Do not depend on older chats or older drafts to fill in missing behavior. If this document is unclear, the document needs to be fixed before implementation starts.

The only behavior in scope here is Unit of Work write-intent classification and the minimum cleanup needed around it. Identity generation, Strong ID shape, and later create-time ID injection belong to later phases.

## Context

The identity work plan makes application-side IDs available before persistence. That exposes a runtime ambiguity in `JpaUnitOfWork`: an entity with a non-default ID is not enough to decide whether the current command intends to create a new row or update an existing row.

The issue is broader than SQL shape. It also affects audit and persistence listeners. A new aggregate must be classified as `CREATE` so create audit can run without update audit. A loaded aggregate that the command explicitly persists must be classified as `UPDATE`. A loaded aggregate used only for cross-aggregate validation must not enter the write set at all.

This spec stands on its own. It defines the Unit of Work runtime contract needed to make create/update/remove intent explicit, and it deliberately stops before later identity-generation phases.

## Scope Boundary

### In Scope

- `UnitOfWork` public contract in `ddd-core`
- `DefaultMediator` forwarding
- `DefaultAggregateFactorySupervisor`
- `DefaultRepositorySupervisor`
- `JpaUnitOfWork`
- `PersistType` listener classification

### Out Of Scope

- Strong ID generation shape
- entity or factory constructor shape
- `IdAllocator` and `IdStrategyRegistry` policy design
- database schema or generator input changes
- dirty checking or no-op detection
- repository read API redesign
- transaction propagation redesign

## Code Map

Start with these files:

- [UnitOfWork.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt>)
- [DefaultAggregateFactorySupervisor.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt>)
- [DefaultRepositorySupervisor.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt>)
- [DefaultMediator.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt>)
- [JpaUnitOfWork.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt>)

Expected production change surface:

- add `PersistIntent` in the core application/UoW API area;
- update `UnitOfWork` and `Mediator` to remove `persistIfNotExist(...)` and expose `persist(entity, intent)`;
- update `DefaultMediator` forwarding;
- update `DefaultAggregateFactorySupervisor` to register factory-created aggregates as `CREATE`;
- keep `DefaultRepositorySupervisor` update registration explicit for `persist=true` reads, so the UoW receives `PersistIntent.UPDATE` rather than inferring write intent from implicit newness;
- refactor `JpaUnitOfWork` pending state and save operation selection around pending intent;
- update or remove tests and docs that still describe implicit newness or `persistIfNotExist(...)`.

## Current Evidence

Current cap4k evidence:

- `UnitOfWork` exposes `persist(entity)`, `persistIfNotExist(entity)`, `remove(entity)`, and `save(propagation)`.
- `JpaUnitOfWork` stores pending persistence and deletion as separate `ThreadLocal<LinkedHashSet<Any>>` collections.
- `DefaultAggregateFactorySupervisor.create(...)` creates an aggregate and immediately calls `unitOfWork.persist(instance)`.
- `DefaultRepositorySupervisor` registers repository reads into `UnitOfWork.persist(...)` when read APIs use `persist=true`.
- Repository read APIs default `persist=false`, and the JPA repository implementation detaches loaded entities when `persist=false`.
- `JpaUnitOfWork.save(...)` currently decides created versus updated from a mix of application-side ID metadata, `EntityInformation.isNew(entity)`, `EntityManager.contains(entity)`, and existence queries.
- `JpaUnitOfWork` still contains a persistence-context scanning helper and commented-out code that no longer represents the intended write boundary.
- `PersistType.CREATE`, `PersistType.UPDATE`, and `PersistType.DELETE` are result/listener classifications after flush, not the source-of-truth write intent before flush.

## Goals

1. Keep `persist(...)` as the public write registration verb.
2. Add explicit create/update intent to `persist(...)`.
3. Make `persist(entity)` default to update intent.
4. Make factory-created aggregates register create intent internally.
5. Make repository `persist=true` register update intent.
6. Remove `persistIfNotExist(...)` from the new UoW contract.
7. Keep `remove(entity)` as the public delete registration method.
8. Internally represent create, update, and remove through one pending change model.
9. Stop using existence queries to turn the factory create path into update.
10. Keep validation-only repository reads out of the UoW write set.
11. Align persist listener and audit classification with explicit UoW intent.
12. Clean up `JpaUnitOfWork` dead code and commented-out persistence-context scan logic as part of this runtime slice.

## Non Goals

- Do not change the DB identity strategy contract.
- Do not change generated Strong ID shape.
- Do not change entity templates or factory templates in this spec.
- Do not redesign transaction propagation wrappers.
- Do not redesign `UnitOfWorkInterceptor` lifecycle shape.
- Do not introduce cap4k-level dirty checking.
- Do not make repository read tracking imply update unless `persist=true` is explicitly selected.
- Do not add a new public `create(...)`, `update(...)`, `stageNew(...)`, or `stageChanged(...)` UoW verb.
- Do not keep `persistIfNotExist(...)` as a preferred or compatibility runtime path.

## Implementation Guardrails

These rules prevent the implementation from drifting into later phases:

- Do not solve create/update classification by adding another database existence check to the factory-created path.
- Do not keep `persistIfNotExist(...)` as a hidden compatibility fallback unless this spec is revised first.
- Do not introduce dirty/no-op checking to avoid update audit. If a caller registers update intent, update audit is acceptable.
- Do not change repository methods so that every load is automatically tracked for update.
- Do not change entity templates or factory payload templates to satisfy this runtime slice.
- Do not make `PersistType` the pre-flush source of truth. `PersistType` remains a listener/result classification after the UoW applies the pending change.
- Do not redesign transaction propagation wrappers while extracting create/update/remove helpers.
- If `UnitOfWorkInterceptor` needs separate create/update sets before flush, stop and revise this spec instead of adding a side-channel.

## Terms

### Persist Intent

Persist intent is the command-side write intent passed to `UnitOfWork.persist(...)` before `save()`.

Supported public persist intents:

```kotlin
enum class PersistIntent {
    CREATE,
    UPDATE,
}
```

`CREATE` means the entity is new in this Unit of Work and must be inserted.

`UPDATE` means the entity is existing and the current command intends to submit an update.

### Pending Change

Pending change is the internal UoW representation of all write intentions.

```kotlin
private enum class UnitOfWorkIntent {
    CREATE,
    UPDATE,
    REMOVE,
}

private data class PendingChange(
    val entity: Any,
    val intent: UnitOfWorkIntent,
)
```

`REMOVE` is internal because `persist(entity, REMOVE)` is not a readable public API. Public deletion remains `remove(entity)`.

### Persist Type

`PersistType` remains the post-flush listener classification:

- `PersistType.CREATE`
- `PersistType.UPDATE`
- `PersistType.DELETE`

Persist intent and persist type must remain separate concepts. Intent is the requested operation before persistence. Type is the observed classification sent to listeners after persistence.

## Public UoW Contract

The new public contract is:

```kotlin
interface UnitOfWork {
    fun persist(entity: Any, intent: PersistIntent = PersistIntent.UPDATE)
    fun remove(entity: Any)
    fun save(propagation: Propagation = Propagation.REQUIRED)
}
```

Rules:

- `persist(entity)` means `persist(entity, PersistIntent.UPDATE)`.
- `persist(entity, PersistIntent.CREATE)` means the entity must be inserted.
- `persist(entity, PersistIntent.UPDATE)` means the entity must be updated or merged as an existing entity.
- `remove(entity)` means the entity must be deleted.
- `save()` commits the pending changes.
- `persistIfNotExist(entity)` is removed from the new contract.

The default `UPDATE` is intentional. The main user-authored write path is loading an existing aggregate with repository `persist=true`, mutating it, and calling `save()`. The main create path is factory-driven and can pass `CREATE` internally without asking the application author to learn a second method.

Manual construction outside the factory is an advanced path. If it is used for creation, the caller must explicitly pass `PersistIntent.CREATE`.

## Runtime Source Rules

### Factory Source

`DefaultAggregateFactorySupervisor.create(payload)` must register the new aggregate as create intent:

```kotlin
unitOfWork.persist(instance, PersistIntent.CREATE)
```

Factory-created entities remain `CREATE` for the whole UoW, even if they are mutated before `save()`.

### Repository Source

Repository reads remain read-only by default.

When a repository read uses `persist=false`, the loaded aggregate is a validation/read object and must not be registered in the UoW write set.

When a repository read uses `persist=true`, `DefaultRepositorySupervisor` must register the loaded aggregate with the default update intent:

```kotlin
unitOfWork.persist(loaded)
```

or equivalently:

```kotlin
unitOfWork.persist(loaded, PersistIntent.UPDATE)
```

This is a write-intent declaration. cap4k does not perform additional dirty/no-op checks. If JPA later optimizes unchanged managed entities, that is an ORM concern, not a cap4k contract.

### Manual Source

Manual `UnitOfWork.persist(entity)` registers update intent. Manual new-entity creation must use:

```kotlin
unitOfWork.persist(entity, PersistIntent.CREATE)
```

There is no automatic "new versus existing" mainline behavior for manual construction.

## Pending Change Model

`JpaUnitOfWork` should replace separate pending sets with one pending change set.

The model should support:

- object-identity based deduplication
- explicit intent merging for repeated registration of the same instance
- remove intent registration
- conversion from public `PersistIntent` to internal `UnitOfWorkIntent`
- extraction of persisted and removed aggregate sets for existing interceptors
- result classification after JPA operations

Object identity must be used for deduplication. Entity `equals` and `hashCode` must not decide UoW registration identity.

Recommended private shape:

```kotlin
private class PendingChangeSet {
    private val entries = LinkedHashMap<ObjectIdentityKey, PendingChange>()

    fun persist(entity: Any, intent: PersistIntent)
    fun remove(entity: Any)
    fun drain(): List<PendingChange>
}
```

`ObjectIdentityKey` can be a private wrapper around reference equality and `System.identityHashCode`.

The final implementation can use another private shape if it preserves these semantics.

## Same-Instance Merge Rules

When the same object instance is registered more than once in one UoW:

| Existing Intent | New Intent | Result |
|---|---|---|
| `CREATE` | `CREATE` | `CREATE` |
| `CREATE` | `UPDATE` | `CREATE` |
| `UPDATE` | `UPDATE` | `UPDATE` |
| `UPDATE` | `CREATE` | fail fast |
| `CREATE` | `REMOVE` | remove pending entry, no-op |
| `UPDATE` | `REMOVE` | `REMOVE` |
| `REMOVE` | `UPDATE` | fail fast |
| `REMOVE` | `CREATE` | fail fast |
| `REMOVE` | `REMOVE` | `REMOVE` |

Rationale:

- A factory-created aggregate remains new even if later code calls default `persist(entity)`.
- A new aggregate removed before flush should not produce insert, delete, or listener events.
- Recreating or updating a removed instance in the same UoW is ambiguous and should fail.
- Turning an existing update into create is also ambiguous and should fail.

## Same-Identity Conflict Rules

When feasible, `JpaUnitOfWork` should detect different object instances that represent the same entity identity and fail before flush.

Examples:

- `Order(id=1)` registered as `CREATE`, and another `Order(id=1)` registered as `UPDATE`
- `Order(id=1)` registered as `CREATE`, and another `Order(id=1)` registered as `CREATE`
- `Order(id=1)` registered as `UPDATE`, and another `Order(id=1)` registered as `REMOVE`

This check should be pragmatic. It may use JPA entity information and application-side ID metadata already available to `JpaUnitOfWork`, but it should not become a broad identity-reflection subsystem. Cases that cannot be recognized safely can remain delegated to JPA or the database.

## JpaUnitOfWork Save Flow

Recommended `JpaUnitOfWork.save(...)` structure:

```kotlin
override fun save(propagation: Propagation) {
    val pendingChanges = pendingChangesThreadLocal.get().drain()
    val persistedEntities = pendingChanges
        .filter { it.intent == CREATE || it.intent == UPDATE }
        .mapTo(LinkedHashSet()) { it.entity }
    val removedEntities = pendingChanges
        .filter { it.intent == REMOVE }
        .mapTo(LinkedHashSet()) { it.entity }

    prepareApplicationSideIds(pendingChanges)
    uowInterceptors.forEach { it.beforeTransaction(persistedEntities, removedEntities) }

    try {
        save(pendingChanges, propagation) { changes ->
            val result = FlushResult()
            uowInterceptors.forEach { it.preInTransaction(persistedEntities, removedEntities) }

            changes.forEach { change ->
                when (change.intent) {
                    CREATE -> applyCreate(change.entity, result)
                    UPDATE -> applyUpdate(change.entity, result)
                    REMOVE -> applyRemove(change.entity, result)
                }
            }

            flushAndNotifyIfNeeded(result)
            notifyPostInterceptors(persistedEntities, removedEntities, changes)
        }

        uowInterceptors.forEach { it.afterTransaction(persistedEntities, removedEntities) }
    } finally {
        clearCurrentProcessingState()
    }
}
```

This example is illustrative. The fixed requirement is that the save flow is driven by `PendingChange.intent`, not by ID presence or existence queries for the mainline create/update decision.

## JPA Operation Rules

### Create

Create intent must call `EntityManager.persist(entity)` for the root entity unless it is already managed.

Rules:

- Assign missing application-side IDs before persistence.
- Validate application-side IDs are not default after assignment.
- Do not query the database to decide whether to merge.
- If the database already contains the same primary key, let JPA/database raise the conflict.
- Add the entity to the created result set.
- Refresh newly persisted entities only when the existing DB-identity path still needs refresh.

Application-side Strong IDs created by generated constructors should already be present before `save()`. The existing application-side ID assignment path remains compatibility support for manually annotated entities.

### Update

Update intent must treat the entity as existing.

Rules:

- The root entity ID must already identify an existing entity.
- Do not assign a new root ID for an update-intent aggregate.
- Missing application-side IDs may still be assigned to new aggregate-owned children before merge.
- If `EntityManager.contains(entity)` is false, call `EntityManager.merge(entity)`.
- If the entity is already managed, no explicit merge is needed.
- Add the entity to the updated result set.
- Do not perform cap4k dirty checking.

### Remove

Remove intent remains a separate public operation.

Rules:

- If `EntityManager.contains(entity)` is true, call `EntityManager.remove(entity)`.
- Otherwise merge first, then remove the merged instance.
- Add the entity to the deleted result set.
- If the same instance was previously registered as `CREATE` in this UoW, the pending create should be canceled and no JPA call should be made.

## Audit And Listener Rules

Persist listener classification must follow the final pending change result:

- `CREATE` intent that is applied -> `PersistType.CREATE`
- `UPDATE` intent that is applied -> `PersistType.UPDATE`
- `REMOVE` intent that is applied -> `PersistType.DELETE`
- `CREATE` canceled by `REMOVE` before flush -> no listener event

This keeps audit policy straightforward:

- create audit runs only for created entities
- update audit runs only for update intent
- delete audit runs only for delete intent
- validation-only reads do not trigger audit

No cap4k dirty tracking is added. A caller that registers update intent is declaring that update audit is acceptable.

## Repository Validation Contract

Command handlers may load multiple aggregates for validation, including aggregates of different types.

Rules:

- Validation-only loads use default `persist=false`.
- Only the aggregate or aggregate list that the command intends to mutate should use `persist=true`.
- `persist=true` means update intent registration, not merely "keep managed if maybe needed".
- If a command loads an aggregate with `persist=false` and later decides to mutate it, it must explicitly call `UnitOfWork.persist(entity)` before `save()`.

## `persistIfNotExist` Removal

`persistIfNotExist(entity)` is removed from the new contract.

Rationale:

- It reintroduces existence-query driven persistence semantics.
- It blurs application business decisions with UoW persistence mechanics.
- It conflicts with factory-created create intent.
- It complicates audit classification.
- There is no external user compatibility requirement for this iteration.

The replacement is explicit application logic:

```kotlin
if (!repository.exists(predicate)) {
    factory.create(payload)
}
```

Factory creation then registers `CREATE` intent automatically.

## JpaUnitOfWork Cleanup Scope

This slice should clean up `JpaUnitOfWork` enough that the code structure reflects the new contract.

Allowed cleanup:

- Remove unused persistence-context scanning helper if it only supported commented-out behavior.
- Remove commented-out persistence-context scan code.
- Remove imports used only by deleted helper code.
- Replace anonymous flush-result object with a private named result structure.
- Replace `save(arrayOf(...))` input packing with a typed private input structure or direct pending-change input.
- Extract small private methods for create/update/remove application.
- Rename local variables so they describe intent rather than generic persistence.

Out of scope cleanup:

- Redesigning transaction propagation method layout.
- Redesigning `UnitOfWorkInterceptor`.
- Redesigning repository read APIs.
- Changing event or specification interceptor behavior beyond the changed input sets caused by explicit intent.

## API Migration

Breaking changes:

- `UnitOfWork.persistIfNotExist(entity)` is removed.
- `UnitOfWork.persist(entity)` no longer means "runtime chooses insert or update".
- `UnitOfWork.persist(entity)` means update intent.
- New entity creation outside factory must call `persist(entity, PersistIntent.CREATE)`.

Runtime call-site changes:

- `DefaultAggregateFactorySupervisor.create(...)` must call `persist(instance, PersistIntent.CREATE)`.
- `DefaultRepositorySupervisor` can keep calling `persist(entity)` for `persist=true` because default intent is update.
- `DefaultMediator` must match the new `UnitOfWork` interface and remove `persistIfNotExist`.

Documentation and tests that describe `persistIfNotExist` or implicit persist newness must be updated or removed.

## Verification Strategy

Required focused tests:

- Factory supervisor registers `PersistIntent.CREATE`.
- Repository supervisor registers update intent when `persist=true`.
- Repository reads with default `persist=false` do not register UoW changes.
- `persist(entity, PersistIntent.CREATE)` calls `EntityManager.persist(entity)` and reports `PersistType.CREATE`.
- Create intent with non-default application-side ID does not query-and-merge existing rows.
- `persist(entity)` on a detached existing entity calls merge and reports `PersistType.UPDATE`.
- Managed update intent reports `PersistType.UPDATE` without explicit merge.
- `remove(entity)` calls remove or merge-then-remove and reports `PersistType.DELETE`.
- Same instance `CREATE` then default `persist(entity)` remains `CREATE`.
- Same instance `CREATE` then `remove(entity)` cancels the pending change and emits no listener event.
- Removed `persistIfNotExist` call sites no longer compile in production code.

Recommended cleanup verification:

- Static search confirms no commented-out persistence-context scan remains in `JpaUnitOfWork`.
- Static search confirms `persistIfNotExist` no longer exists in the public `UnitOfWork` contract.
- Existing transaction propagation behavior remains covered by current `JpaUnitOfWork` tests.

## Rollback

Rollback to this design is required if implementation evidence shows any of these assumptions are false:

- Factory-created application-side ID entities still need existence-query merge behavior.
- Repository `persist=true` cannot safely mean update intent.
- Removing `persistIfNotExist` breaks required runtime behavior that cannot be expressed as explicit application logic.
- Interceptors require pre-flush separation of create and update, not just the existing persisted/remove aggregate sets.

If any of these happen, revise this spec before writing or executing an implementation plan.
