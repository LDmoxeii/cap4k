# cap4k UoW Owned Entity Lifecycle Classification Design

Date: 2026-07-23
Revision: 2026-07-24 after PR #132 and PR #133 reached master

## Reader Contract

This spec is for an implementation agent with no chat history. After reading it, the agent should be able to answer these questions without asking for historical context:

- What does Unit of Work own, and what do Repository and `OwnedEntityList` not own?
- What did PR #132 and PR #133 already change on `master`?
- Why is owned-child lifecycle derived from an enrolled aggregate root instead of child ID presence?
- Which direct-child misuse must fail in this phase, and which broader direct-child classifier is intentionally deferred?
- Why must implementation not add a new annotation to mark owned children?
- How are generated Strong IDs different from the compatibility `@ApplicationSideId` path?
- When is repository observation baseline captured in current code, and why must the spec not say "before detach"?
- Which files are in scope, which files are forbidden, and what evidence proves the implementation stayed inside the boundary?

This is a design contract, not a full implementation plan. Agents may implement inside the stated boundary, but must stop and revise the spec if current code evidence contradicts any required behavior.

## Status

Remaining-scope hardening draft after PR #132 and PR #133. PR #132 already delivered the generated Strong ID and UoW fallback basics; this spec now constrains the remaining owned-child lifecycle hardening slice.

The original draft was written before these pull requests were merged. Current `master` already contains important pieces that were previously described as future work: `PersistIntent.EXISTING`, repository observation baseline, generated Strong ID completion, provider dirty inspection, and `OwnedEntityList`. This revision turns the spec into a remaining-scope contract instead of repeating already-merged work as if it were absent.

## Current Evidence

Use these files as the current source of truth. Do not rely on older chat discussion.

### Merged PR Evidence

- `36f4059f` merges PR #132, `plan/cap4k-all-entity-strong-id`.
- `f9e5ce16` merges PR #133, `plan/cap4k-default-aggregate-template-structure`.
- `master` currently points at `f9e5ce16`.

### Runtime Contract Evidence

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt` now has only `CREATE` and `EXISTING`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt` defaults `persist(entity)` to `PersistIntent.EXISTING`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt` calls `unitOfWork.persist(instance, PersistIntent.CREATE)` for factory-created aggregate roots.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt` observes every loaded entity and enrolls it as `EXISTING` only when repository APIs are called with `persist=true`.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt` applies `JpaAggregateLoadPlanSupport` before returning. For `persist=false`, it detaches the root before returning.
- Because `DefaultRepositorySupervisor` observes after the repository method returns, current implementation observes the materialized/load-plan-applied graph before user code can mutate it, but not necessarily before detach.

### Baseline And Traversal Evidence

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt` owns pending entries, repository observation baseline, ID completion, same-identity conflict checks, dirty inspection, and listener notification.
- `JpaUnitOfWork.observeRepositoryLoad(...)` records the root and reachable generated owned entities through `JpaGeneratedOwnedRelationTraversal`.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt` records observed objects by root and preserves the first observation for a root instead of absorbing later mutations as the new baseline.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt` currently traverses only initialized `@OneToMany` fields that are non-inverse, have `@JoinColumn`, include both `CascadeType.PERSIST` and `CascadeType.MERGE`, and use `orphanRemoval=true`.
- This traversal is intentionally narrower than arbitrary JPA graph scanning.

### ID Evidence

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt` completes missing generated own Strong IDs for `CREATE`, and for `EXISTING` completes only reachable owned entities that are not repository-observed baseline objects.
- The same file validates that observed existing Strong IDs are present and unchanged.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt` describes `@ApplicationSideId` as compatibility runtime annotation for manually authored application-side IDs.
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt` still implements the compatibility path through `IdentifierStrategyRegistry`. That strategy system must not be deleted by this spec.

### Owned Relation Evidence

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt` is a simple facade over a mutable backing list. It delegates `add`, `remove`, `singleOrNull`, and `replace`; it does not know UoW, Repository, Mediator, EntityManager, ID generation, or persistence intent.
- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ownedentitylistfixture/OwnedEntityListJpaRuntimeFixtures.kt` shows the generated-style shape: private JPA backing collections, transient public `OwnedEntityList` accessors, protected no-arg constructors, and public business constructors.
- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/OwnedEntityListJpaRuntimeTest.kt` proves Hibernate persists, reloads, and orphan-removes through the private backing collections.

### Existing Test Evidence

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt` already covers repository observation of root plus generated owned children, clean existing enrollment without update listener, observed detached existing enrollment, generated Strong ID completion for new owned children, repeated observation not absorbing newly added children, observed identity-change rejection, and application-side ID compatibility.
- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt` covers generated Strong ID UoW runtime behavior and clean/dirty existing listener behavior.
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt` covers repository observation on both default non-persistent reads and persistent reads, and `PersistIntent.EXISTING` enrollment for `persist=true`.

## Problem Statement

When application code persists an aggregate root, UoW must treat owned children as part of the aggregate lifecycle. Application code should not have to declare public persist intent for each owned child.

Plain-language model:

- If the aggregate root is new, the root and the owned children currently inside it are new for this save.
- If the aggregate root came from a repository, the objects already inside it at load time are known existing.
- If business logic later adds a child to that loaded root, that child is new for this save, even if it already has an application-side ID.
- If business logic removes a loaded child from an orphan-removal relation, that child is a delete candidate through the aggregate root.
- Merely loading an existing aggregate does not mean it was updated.
- A child ID by itself is not enough evidence to decide whether the child is new or existing.

This spec solves the remaining lifecycle classification boundary needed for UoW-owned child handling after PR #132 and PR #133.

## Non Goals

This spec does not solve these problems:

- It does not introduce Phase 4 create-time ID injection in `OwnedEntityList.add(...)`.
- It does not make `OwnedEntityList` register UoW changes.
- It does not add annotations to classify direct child entities.
- It does not implement a general direct-child classifier for arbitrary entity instances.
- It does not turn UoW into a general JPA graph persistence engine.
- It does not traverse arbitrary non-owned JPA associations.
- It does not redesign Repository query APIs.
- It does not delete `@ApplicationSideId` or the identifier strategy system.
- It does not require generated Strong ID entities to use `@ApplicationSideId`.
- It does not claim child audit/listener publication is complete unless implementation extends the listener result surface and tests it.

## Terms

### PersistIntent

`PersistIntent` is the public UoW write intent for the public enrollment target. Current `master` has:

```kotlin
enum class PersistIntent {
    CREATE,
    EXISTING,
}
```

`CREATE` means the enrolled public target is expected to be inserted. `EXISTING` means the enrolled public target is expected to represent already existing persistence state. `persist(entity)` means `persist(entity, PersistIntent.EXISTING)`.

### Public Enrollment Target

A public enrollment target is the object application code passes directly to `UnitOfWork.persist(...)` or `UnitOfWork.remove(...)`.

For normal aggregate writes, the public enrollment target should be the aggregate root. Owned children are internal lifecycle participants derived from the root graph, not normal public enrollment targets.

### Generated Own Strong ID

A generated own Strong ID is the generated entity's own identifier type, such as `OrderId` or `OrderLineId`, modeled as a `StrongId` value object and stored through JPA as the entity's own ID field, usually through `@EmbeddedId`.

Generated own Strong IDs use generated constructors and companion `new()` methods. They are the primary path for generated aggregate/entity IDs after PR #132.

### Application-Side ID Compatibility Path

The compatibility path is the runtime support for manually authored entities annotated with `@ApplicationSideId` and backed by `IdentifierStrategyRegistry`.

This path stays because the runtime still supports manually authored application-side IDs. It is not the generated Strong ID path, and generated Strong ID output should not start adding `@ApplicationSideId` to preserve this compatibility path.

### Create-Time Injection

Create-time injection means assigning a missing generated own Strong ID when the child is added to a relation, for example inside `OwnedEntityList.add(entity)`.

That is Phase 4 work. This spec only requires UoW save-time and pre-transaction completion to remain correct, so Phase 4 can later improve ergonomics without becoming the only correctness mechanism.

### Save-Time And Pre-Transaction Completion

Current UoW assigns missing IDs when `persist(...)` is called and repeats completion before transaction work. This protects entities added before `save()` and entities added by UoW interceptors before persistence is applied.

### Repository Observation Baseline

Repository observation baseline is the UoW record of the aggregate graph as repository APIs observed it before user code receives and mutates it.

Current code records the baseline after the repository method has materialized the result and applied the load plan. For `persist=false`, the lower repository may already have detached the root before `DefaultRepositorySupervisor` records the baseline. Therefore this spec must not say the baseline is captured "before detach". The important contract is "before user mutation", not "before detach".

### Observed Root

The observed root is the aggregate root passed to repository observation for one repository result.

### Observed Owned Child

An observed owned child is a non-root entity recorded in the same repository observation baseline as the observed root.

### Reachable Owned Entity

A reachable owned entity is an object currently reachable from an enrolled root through the generated owned relation traversal.

Current traversal is bounded to initialized generated-style owned `@OneToMany` relations with non-inverse `@JoinColumn`, `PERSIST` plus `MERGE` cascades, and `orphanRemoval=true`. Do not silently broaden this to arbitrary JPA associations.

### Repository-Observed Child Guard

The repository-observed child guard is the narrow direct-child misuse check required by this spec: if an object is known from repository observation to be a child inside another observed aggregate graph, it must not be accepted as a standalone public UoW enrollment target.

This is not a general generated-child classifier.

## Design Decisions

### 1. Public UoW Intent Remains Root-Oriented

Decision: Application code persists aggregate roots. UoW derives owned child lifecycle from the root graph.

Reason: The repository and generator model aggregate-owned children as part of the aggregate. Asking users to call `persist(child)` would make child lifecycle depend on call order and ID presence. That reintroduces the ambiguity PR #132 removed for Strong IDs.

Excluded alternatives:

- Expose child `PersistIntent` as a public API. This leaks internal aggregate lifecycle into application code.
- Make Repository own save semantics. Repository remains read/access/load; UoW owns persist intent, delete intent, commit, and save.
- Make `OwnedEntityList` register UoW changes. It would turn a relation facade into a persistence context.

### 2. Direct Child Fail-Fast Is Narrowed To Evidence We Already Have

Decision: Do not implement a general Direct Child Fail-Fast Classifier in this phase. Implement only the repository-observed child guard and duplicate-root-graph guard.

Required behavior:

- If repository observation recorded `root` and `child`, and `child` is not the observation root, `unitOfWork.persist(child)` must fail fast with a message telling the user to persist the aggregate root.
- The same rule applies to `unitOfWork.remove(child)`.
- If the same UoW contains a root entry and a separate child entry, and the child is reachable from that root through current generated owned traversal, save must fail fast before flush.
- `unitOfWork.persist(root)` after business logic adds a new owned child remains the valid path. The child is classified as create because it is reachable now but absent from the repository baseline.

Reason: This covers the realistic misuse left by the current generated model: a user loads an aggregate, takes a child object from it, and calls `uow.persist(child)`. Current code already records repository baseline with root plus owned children, so this can be enforced without adding annotations.

Why not a general classifier now:

- Generated child constructors and aggregate relation APIs already block many direct construction and mutation paths.
- Repositories load whole aggregate graphs by default, so the highest-risk child misuse can be detected through observation evidence.
- Current runtime does not have a reliable provider-neutral marker saying "this arbitrary object is a generated owned child".
- Adding an annotation just to make this classifier work is unacceptable for this phase.
- Scanning arbitrary JPA associations would exceed the bounded traversal and could misclassify non-owned relations.

Excluded alternatives:

- Add `@AggregateElement(root = false)` or a new annotation. This is out of scope.
- Treat every entity reachable through any JPA relation as an owned child. This is too broad.
- Silently accept observed child `persist(...)`. That makes child save independent from aggregate root and hides a boundary error.

Residual risk:

- If a manually constructed child is passed alone to `persist(child, CREATE)` and it was never observed as part of a repository-loaded aggregate and is not reachable from a pending root, this phase does not promise a reliable fail-fast. Existing generated constructors and aggregate APIs should make this uncommon for generated models. If that risk becomes unacceptable, roll back to technical design and define a real metadata carrier without annotations.

### 3. Baseline Means Original Observation, Not Current Mutated Graph

Decision: Baseline must preserve the first repository observation for a root and must not be overwritten by later observation of the same object graph after business mutation.

Reason: If business code loads a root, adds a child, and a later repository path observes the same object again, the new child must stay absent from baseline. Otherwise UoW would mistake it for an existing child.

Required behavior:

- Repository reads with `persist=true` and `persist=false` both record observation baseline.
- Baseline capture happens after repository materialization and load-plan application, before user code can mutate the returned result.
- The spec must not require "before detach" because current `AbstractJpaRepository` may detach for `persist=false` before `DefaultRepositorySupervisor` records observation.
- Baseline must record root identity and observed owned child identities when available.
- Baseline must record object identity even when persistent identity is not available.
- Baseline must not initialize relations beyond the selected load plan.
- Baseline must not traverse arbitrary non-owned relations.

Excluded alternatives:

- Recompute baseline at `persist(root)` time. That would capture the mutated graph and lose the difference between loaded and newly added children.
- Query the database to decide child existence. That adds persistence lookups and still does not prove aggregate ownership intent.

### 4. Child Lifecycle Is Derived From Root Intent Plus Baseline Diff

Decision: UoW derives owned child lifecycle internally from root intent, current reachable graph, and baseline.

Recommended internal states:

```kotlin
internal enum class OwnedEntityLifecycleState {
    CREATE,
    KNOWN_EXISTING,
    DELETE_CANDIDATE,
}
```

The exact representation can differ if tests prove the same behavior, but do not expose this as public API.

Required behavior for `CREATE` root:

- The root is create.
- Every reachable generated owned entity is create unless a conflict is detected.
- Missing generated own Strong IDs are completed through generated Strong ID support.
- Missing `@ApplicationSideId` IDs are completed only through the compatibility path.

Required behavior for `EXISTING` root:

- The root is known existing, not automatically updated.
- Reachable owned entities present in the repository baseline are known existing.
- Reachable owned entities absent from the repository baseline are create.
- Baseline owned entities no longer reachable are delete candidates only when the relation supports orphan removal through the generated owned traversal.
- Clean baseline entities must not be reported as updated only because the root was enrolled.
- Dirty existing update classification must come from provider dirty inspection or an explicitly documented fallback.

Excluded alternatives:

- Use non-null ID as proof of existing state. A new child may already have an application-side ID.
- Use null ID as proof of create state. Generated Strong IDs can be assigned before flush.
- Treat `persist(root)` as "the whole graph was updated". That creates false audit/listener events.

### 5. Generated Strong ID Is The Primary Generated Path

Decision: Generated Strong ID completion stays separate from `@ApplicationSideId` compatibility.

Required behavior:

- `CREATE` root completion may fill missing generated Strong IDs on the root and reachable owned children.
- `EXISTING` root completion must not replace a missing or changed root Strong ID. It must fail fast.
- `EXISTING` root completion may fill missing generated Strong IDs only for reachable owned children absent from baseline.
- Observed baseline Strong ID entities with missing or changed own IDs must fail fast.
- Generated Strong ID completion must use the same lifecycle boundary as child classification.

Reason: Generated Strong IDs are typed identity value objects produced by the generator. They should not depend on runtime annotation scanning intended for legacy/manual entities.

Excluded alternatives:

- Add `@ApplicationSideId` to generated Strong ID entities. This conflates two ID systems.
- Delete `@ApplicationSideId` or the identifier strategy system. That would break the compatibility path and is outside this spec.

### 6. `@ApplicationSideId` Compatibility Stays But Does Not Drive Generated Strong IDs

Decision: Keep `@ApplicationSideId` and `IdentifierStrategyRegistry` for manually authored compatibility use cases.

Reason: Current runtime tests and support classes still exercise this path. Removing it would be a separate migration decision, not part of owned child lifecycle classification.

Boundaries:

- Generated Strong ID entities should not use `@ApplicationSideId`.
- Compatibility ID assignment may still traverse owned JPA relations as current support does.
- Compatibility traversal must not be used as evidence that generated Strong ID lifecycle classification is complete.
- Do not rename this path in a way that hides its compatibility purpose.

### 7. `OwnedEntityList` Remains A Relation Facade

Decision: `OwnedEntityList` remains a small relation facade over a backing collection.

Required behavior:

- `add(entity)` mutates the backing collection.
- `remove(entity)` mutates the backing collection.
- `replace(value)` is the helper for generated owned-one facade shape.
- Accessing the facade does not assign IDs.
- Accessing the facade does not register UoW changes.
- Accessing the facade does not intentionally initialize lazy JPA collections.
- The facade object itself is never a UoW pending key.

Reason: Keeping `OwnedEntityList` passive preserves a clean boundary: aggregate mutation happens in domain objects, lifecycle classification happens in UoW, persistence is performed by JPA through the backing collection.

### 8. JPA Provider Logic Stays In JPA Modules

Decision: JPA-specific baseline capture, traversal, identity inspection, proxy handling, dirty inspection, and merge/persist/remove behavior stay in `ddd-domain-repo-jpa`.

Reason: `ddd-core` exposes the public UoW contract and relation facade. It must not gain JPA or Hibernate dependencies for this phase.

Excluded alternatives:

- Move Hibernate traversal helpers into `ddd-core`.
- Make generated entities depend on EntityManager, Repository, Mediator, UoW, or ID generator services.

### 9. Listener/Audit Claims Must Match The Actual Result Surface

Decision: Existing root enrollment must not emit update listener events unless provider dirty inspection identifies dirty existing entities. Child create/delete/update listener support must not be claimed unless the implementation deliberately exposes and tests those child result sets.

Reason: Current listener infrastructure mainly works with the persisted and removed public entry sets plus provider dirty results. Owned child lifecycle may be enough for ID completion and safety checks even before child listener publication is fully generalized.

Excluded alternative:

- Say child audit classification is fully supported because the graph diff exists. That overstates evidence.

## Runtime Flow

### New Aggregate Root Flow

1. Application code uses aggregate factory.
2. `DefaultAggregateFactorySupervisor` creates the aggregate root.
3. The factory supervisor calls `unitOfWork.persist(root, PersistIntent.CREATE)`.
4. UoW completes generated Strong IDs for the root and reachable owned children.
5. UoW may also run `@ApplicationSideId` compatibility assignment for manually annotated entities.
6. `save()` runs interceptors, repeats ID completion before transaction work, and persists the root.
7. JPA cascades persist/remove through generated owned backing collections.

Expected classification:

- root: `CREATE`
- each reachable owned child: `CREATE`

### Repository `persist=true` Existing Root Flow

1. Repository loads the root.
2. `AbstractJpaRepository` applies the requested `AggregateLoadPlan`.
3. `DefaultRepositorySupervisor` records repository observation baseline.
4. Because `persist=true`, `DefaultRepositorySupervisor` calls `unitOfWork.persist(root, PersistIntent.EXISTING)`.
5. Application code mutates the root graph.
6. `save()` validates baseline identity, completes IDs for newly reachable owned children, and merges or persists through the root path.
7. Clean existing roots do not emit update listener events merely from enrollment.

Expected classification:

- root: `KNOWN_EXISTING`
- observed reachable child: `KNOWN_EXISTING`
- child added after observation: `CREATE`
- observed child removed from orphan-removal relation: `DELETE_CANDIDATE`

### Repository `persist=false` Then Later Persist Flow

1. Repository loads the root.
2. `AbstractJpaRepository` applies the requested `AggregateLoadPlan`.
3. For `persist=false`, `AbstractJpaRepository` may detach the root before returning.
4. `DefaultRepositorySupervisor` records repository observation baseline before user code receives the returned value.
5. Application code decides whether to mutate.
6. If it mutates, it must call `unitOfWork.persist(root)`.
7. UoW promotes the earlier observation baseline and derives child lifecycle from the difference between baseline and current graph.

Example:

```kotlin
val order = repository.findOne(OrderSchema.predicateById(id), persist = false)
if (order != null && shouldChange(order)) {
    order.lineItems.add(newLine)
    unitOfWork.persist(order)
    unitOfWork.save()
}
```

`newLine` is create because it was not part of the repository observation baseline.

### Direct Child Misuse Flow

If application code does this:

```kotlin
val order = repository.findOne(OrderSchema.predicateById(id), persist = false)!!
val line = order.lineItems.single()
unitOfWork.persist(line)
```

UoW must fail fast because `line` is known from repository observation as a child inside `order`'s aggregate graph. The diagnostic should say to persist the aggregate root, not the generated owned child.

If application code adds a child through the aggregate and persists the root:

```kotlin
val order = repository.findOne(OrderSchema.predicateById(id), persist = false)!!
order.lineItems.add(newLine)
unitOfWork.persist(order)
```

This is valid. `newLine` is create because it is reachable from the root and absent from baseline.

## Generator Flow

No generator structural change is required by this spec.

Current generated-style relation structure from PR #133 is sufficient for this phase:

- private JPA backing collections carry persistence annotations;
- transient public `OwnedEntityList` accessors expose controlled mutation;
- owned children are reached by UoW through JPA-backed fields, not through the facade object;
- generated Strong ID types expose companion `new()` for UoW fallback completion.

Agents must not add generated annotations just to classify child instances. If a later design needs a provider-neutral generated metadata carrier, that must be a new spec with explicit source truth, generated output shape, migration, and tests.

## Graph Diff Rules

Diff current reachable owned entities against repository observation baseline by stable identity where available, and by observed object identity where stable identity is unavailable.

Preferred identity order:

1. persistent entity type plus non-default own ID;
2. generated Strong ID own value if available and non-default;
3. observed object identity for entities that do not yet have a persistent identity.

Required outcomes:

| Baseline State | Current Reachability | Derived State |
|---|---|---|
| absent | reachable | `CREATE` |
| present | reachable as same object or same persistent identity | `KNOWN_EXISTING` |
| present | no longer reachable | `DELETE_CANDIDATE` when orphan-removal deletion is supported |
| present | reachable through different object with same persistent identity | fail fast unless provider reconciliation is explicitly proven |
| absent | not reachable | ignore |

Rules:

- `clear()` plus adding new children means old baseline children are delete candidates and new children are create candidates.
- `clear()` plus adding the same loaded child instances back must not become delete plus create.
- Re-observing the same root later must not turn children added after the first observation into baseline children.
- ID presence alone must never decide create versus existing.

## Examples

### Existing Root With New Child

The child is created by business after the root was loaded.

```kotlin
val order = repository.findOne(OrderSchema.predicateById(orderId), persist = false)!!
order.lineItems.add(OrderLine.create(...))
unitOfWork.persist(order)
unitOfWork.save()
```

Expected result:

- `order` is existing.
- old loaded line items are known existing.
- the newly added line item is create.
- missing generated Strong ID on the new line item is completed by UoW before persistence.

### Clean Existing Root

```kotlin
val order = repository.findOne(OrderSchema.predicateById(orderId), persist = true)!!
unitOfWork.save()
```

Expected result:

- the root was enrolled as existing;
- clean enrollment alone does not emit update listener events;
- owned baseline children are not treated as updated simply because they were loaded.

### Observed Child Direct Persist

```kotlin
val order = repository.findOne(OrderSchema.predicateById(orderId), persist = false)!!
val line = order.lineItems.first()
unitOfWork.persist(line)
```

Expected result:

- fail fast;
- the error tells the user to call `unitOfWork.persist(order)` after mutating the aggregate root;
- no flush happens.

### Pending Root Plus Separate Child Entry

```kotlin
val order = repository.findOne(OrderSchema.predicateById(orderId), persist = false)!!
val line = order.lineItems.first()
unitOfWork.persist(order)
unitOfWork.persist(line)
unitOfWork.save()
```

Expected result:

- fail before flush;
- UoW must not process the same aggregate-owned child as a separate public entry while also processing its root graph.

### Compatibility Application-Side ID

```kotlin
class LegacyInvoice(
    @field:Id
    @field:ApplicationSideId(strategy = "snowflake")
    var id: Long = 0L,
)
```

Expected result:

- the compatibility path may assign `id` through `IdentifierStrategyRegistry`;
- this behavior does not mean generated Strong IDs should use `@ApplicationSideId`;
- tests for this path must remain if the annotation remains public runtime API.

## Migration

### Spec Migration From The Pre-PR Draft

Update the previous draft as follows:

- Replace transitional `UPDATE` wording with current `EXISTING` wording.
- Change "implementation still needs repository baseline" to "repository observation baseline already exists; this spec defines remaining constraints and guard behavior".
- Replace "baseline before detach" wording with "after materialization and load-plan application, before user mutation; current code may observe after detach for `persist=false`".
- Remove annotation-based child classifier candidates.
- Replace general direct child fail-fast with repository-observed child guard plus pending root graph duplicate guard.
- Split generated Strong ID completion from `@ApplicationSideId` compatibility.
- State explicitly that `@ApplicationSideId` compatibility remains, but generated Strong ID output must not depend on it.
- Keep Phase 4 create-time injection out of scope.

### Runtime Migration Expectations

Implementation may need to extend existing internal baseline support to expose enough facts for the repository-observed child guard:

- whether an observed object is the root of its observation;
- whether an observed object is a non-root child;
- the root associated with an observed child for diagnostics;
- whether a pending public entry is reachable from another pending root entry.

Do not add new source annotations or generated entity metadata just for this phase.

### Compatibility Expectations

- Existing generated Strong ID tests should remain valid.
- Existing `@ApplicationSideId` tests should remain valid.
- Existing `OwnedEntityList` facade and runtime tests should remain valid.
- Existing repository default-read behavior should remain: non-persistent reads observe baseline but do not enroll root persist intent.

## Verification Strategy

Use claim strength that matches evidence. A static review can only claim static conformance; compile and tests must actually run before claiming runtime verification.

Required focused tests for this spec:

- repository observation records root versus non-root child distinction;
- `unitOfWork.persist(observedChild)` fails fast when `observedChild` came from a repository-observed aggregate graph;
- `unitOfWork.remove(observedChild)` fails fast for the same reason;
- `unitOfWork.persist(root)` after adding a new child still completes the new child's generated Strong ID and persists through the root path;
- `unitOfWork.persist(root)` plus `unitOfWork.persist(reachableChild)` fails before flush;
- repeated repository observation does not absorb a child added after the first baseline;
- `persist=false` load followed by mutation and `unitOfWork.persist(root)` uses the earlier baseline;
- clean existing root enrollment does not emit update listener events;
- dirty existing root enrollment still emits update listener events through provider dirty inspection;
- observed existing Strong ID child with missing or changed ID fails fast;
- generated Strong ID behavior does not require `@ApplicationSideId`;
- compatibility `@ApplicationSideId` tests still pass.

Required static checks:

- No new annotation is introduced solely to identify owned children.
- `ddd-core` does not gain JPA or Hibernate dependencies.
- `OwnedEntityList` does not import or call UoW, Repository, Mediator, EntityManager, IdentifierGenerator, or IdentifierStrategyRegistry.
- Generated Strong ID templates do not add `@ApplicationSideId`.
- Direct child guard diagnostics tell the user to persist the aggregate root.
- Traversal remains bounded to generated owned relation evidence unless this spec is revised.

Suggested commands when implementation is ready:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest"
./gradlew :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest"
./gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest"
./gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.OwnedEntityListJpaRuntimeTest"
```

If broader starter tests fail because of known fixture isolation issues, report that separately and do not treat it as proof this lifecycle slice failed unless the focused tests above fail.

## Rollback Triggers

Stop implementation and revise this spec if any of these occur:

- The repository-observed child guard cannot distinguish observed root from observed child without adding annotations.
- The guard requires scanning arbitrary JPA associations.
- Rejecting observed child direct persist breaks valid `unitOfWork.persist(root)` cascade behavior.
- Baseline observation after `persist=false` detach loses the identity information needed for new-versus-existing child classification.
- Repeated observation cannot preserve the original baseline.
- Generated Strong ID completion requires `@ApplicationSideId`.
- Keeping `@ApplicationSideId` compatibility blocks generated Strong ID behavior.
- `OwnedEntityList` would need to call UoW or assign IDs to make this phase work.
- Child listener/audit support is claimed without tests proving child result sets.
- Implementation requires broad public API changes beyond `ddd-domain-repo-jpa` internal support and focused tests.

## Agent Handoff Notes

Implementation agents may change:

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt` only if lifecycle-derived ID completion needs small adjustments
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- starter/runtime focused tests if runtime behavior needs proof beyond mocks

Implementation agents must not change:

- public `PersistIntent` values unless a new spec replaces this contract;
- generated entity templates to add child-classifier annotations;
- generated Strong ID templates to add `@ApplicationSideId`;
- `OwnedEntityList` to call UoW, Repository, Mediator, EntityManager, or ID services;
- Repository save semantics;
- broad query APIs;
- arbitrary JPA relation traversal;
- Phase 4 create-time injection.

If the implementation needs any forbidden change, stop and report the smallest concrete blocker with file evidence.
