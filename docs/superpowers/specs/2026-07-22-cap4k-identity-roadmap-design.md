# cap4k Identity Roadmap Design

Date: 2026-07-22

## Reader Contract

This roadmap is for agents and maintainers who have not seen the prior chat history.

It explains the identity redesign sequence that starts with Unit of Work write intent and ends with optional create-time ID injection. Each phase must produce its own focused design spec before implementation. This roadmap is not an implementation plan and does not authorize code changes by itself.

When assigning work to another agent, give that agent this roadmap plus the phase-specific spec for the slice being implemented. If the phase-specific spec does not exist yet, the next task is design, not implementation.

## Why This Roadmap Exists

cap4k currently has several identity mechanisms that were introduced at different times:

- primitive database identity IDs;
- application-side ID metadata through `@ApplicationSideId`;
- runtime ID allocation through `IdAllocator` and `IdStrategyRegistry`;
- generated Strong ID wrappers for aggregate roots and references;
- Unit of Work persist-or-merge behavior that still depends partly on JPA newness and existence checks.

These mechanisms are individually useful, but they do not yet form one coherent authoring story. The desired direction is:

- generated entities can receive real IDs at creation time;
- user code can still manually allocate IDs when it must return or publish an ID before save;
- Strong ID is optional but becomes the best-supported generated identity shape;
- Unit of Work classification is explicit enough to separate create audit from update audit;
- persistence behavior does not depend on guessing whether a non-default ID means new or existing.

The work must be split because each phase changes a different contract surface: runtime UoW behavior, generator identity modeling, mediator-facing API, generated Strong ID factories, and possible non-Strong-ID runtime injection.

## Roadmap Summary

| Phase | Name | Status | Main Outcome |
|---|---|---|---|
| 1 | Unit of Work persist intent | Spec exists in this docs folder | `persist(entity, PersistIntent.CREATE/UPDATE)` separates create and update intent before JPA sees the entity. |
| 2 | All-entity Strong ID support | Needs new spec | Generated Strong ID support expands from aggregate roots/references to every generated entity own ID where the ID strategy requests it. |
| 3 | Mediator ID allocation entry | Needs new spec | Application code gets a stable mediator-facing way to allocate IDs manually without depending directly on low-level registries. |
| 4 | Strong-ID create-time injection | Needs new spec | Generated Strong ID entity constructors/factories receive real IDs during creation without save-time mutation. |
| 5 | Optional non-Strong-ID create-time injection | Future research | Explore whether primitive application-side IDs can get the same create-time ergonomics without weakening the Strong ID path. |

The phases are intentionally ordered. Phase 1 fixes write intent first, because every later application-side ID strategy can create an entity whose ID is already non-default before persistence. Without explicit create/update intent, audit and listener classification remain ambiguous.

## Current Evidence

Current master evidence:

- `UnitOfWork` lives in `ddd-core` and currently exposes `persist(entity)`, `persistIfNotExist(entity)`, `remove(entity)`, and `save(propagation)`.
- `Mediator` extends `UnitOfWork`, `AggregateFactorySupervisor`, `RepositorySupervisor`, `DomainServiceSupervisor`, `IntegrationEventSupervisor`, and `RequestSupervisor`, but it does not expose `IdAllocator`.
- `DefaultAggregateFactorySupervisor.create(...)` creates an aggregate through the registered factory and then calls `unitOfWork.persist(instance)`.
- `DefaultRepositorySupervisor` calls `unitOfWork.persist(entity)` only when repository reads use `persist=true`.
- `JpaUnitOfWork` has separate thread-local sets for persisted and removed entities, plus a processing set.
- `JpaUnitOfWork.save(...)` currently chooses create versus update through application-side ID metadata, JPA `EntityInformation.isNew(entity)`, `EntityManager.contains(entity)`, and existence queries.
- `JpaUnitOfWork` still contains a persistence-context scan helper and commented-out scan code, so the current implementation does not cleanly document the intended write boundary.
- `PersistType.CREATE`, `PersistType.UPDATE`, and `PersistType.DELETE` already exist as post-flush listener classifications.
- `IdAllocator`, `DefaultIdAllocator`, `IdStrategyRegistry`, and `@ApplicationSideId` already exist in core ID runtime code.
- `@ApplicationSideId` is documented in code as a compatibility runtime annotation for manually authored application-side IDs. Generated Strong ID aggregates are not supposed to use save-time ID assignment as their final path.
- `StrongId` currently exposes `value: String`, and `StrongIds` currently supplies UUIDv7 string generation and validation.
- `StrongIdKind` currently distinguishes `AGGREGATE_ROOT`, `AGGREGATE_REFERENCE`, and `REFERENCE`.
- `StrongIdModel` currently carries type name, package, value type, kind, and owning aggregate fields, but it does not yet model owned entity own IDs as first-class identity owners.
- `DbIdStrategy` currently contains only `DB_IDENTITY`.

Related design evidence:

- `2026-05-02-cap4k-uuid7-id-generator-default-policy-design.md` is marked implemented and introduced application-side ID policy, `IdAllocator`, and JPA save-time assignment support.
- `2026-05-22-cap4k-strong-id-1-0-design.md` established Strong ID as the preferred generated aggregate-root/reference identity shape and validated the `@Embeddable/@EmbeddedId` JPA route.
- `2026-07-22-cap4k-identity-contract-design.md` is the newer identity-contract draft. It pushes generated identity toward explicit `@IdStrategy` values, all generated application-side IDs as Strong IDs, and create-time assignment instead of save-time mutation.
- `2026-07-22-cap4k-uow-persist-intent-design.md` is the phase 1 runtime spec. It separates create/update/remove intent before JPA operation selection.

The May and July designs are not identical. The May ID policy design is implemented history and compatibility evidence. The July identity-contract direction supersedes it for the generated default path where the two conflict.

## Terms

### Own ID

Own ID is the primary key of the entity being generated. Aggregate roots and owned child entities both have own IDs when their tables have primary-key columns.

### Reference ID

Reference ID is a field that stores another aggregate, context, or published-language identity. It is not generated by the entity that contains the field.

### Database Identity

Database identity means the database or JPA provider assigns the primary-key value during insert. The domain object cannot promise a durable ID before persistence.

### Application-Side Identity

Application-side identity means cap4k or user code can allocate an ID before persistence. The ID being non-default does not by itself prove whether the entity should be inserted or updated.

### Save-Time Assignment

Save-time assignment means UoW scans an object graph during `save()` and fills missing application-side IDs before JPA persistence. This remains a compatibility path, not the final generated Strong ID happy path.

### Create-Time Assignment

Create-time assignment means the entity receives a real ID during construction or factory creation, before it enters the UoW and before `save()` is called.

### Manual Allocation

Manual allocation means application code asks cap4k for an ID before constructing or mutating an aggregate. This is needed when a command must return an ID, publish it, use it for idempotency, or pass it into an external boundary before save.

### Persist Intent

Persist intent is the command-side UoW classification declared before JPA operation selection. It is separate from JPA entity state and separate from post-flush `PersistType`.

## Roadmap Non Goals

- Do not merge all phases into one implementation slice.
- Do not ask implementation agents to infer missing design from chat history.
- Do not rely on EF Core or JPA to automatically inspect the database and infer new versus existing rows from a non-default ID.
- Do not make repository reads imply writes unless `persist=true` or explicit UoW persistence is used.
- Do not make dirty checking a cap4k responsibility in this roadmap.
- Do not move business invariants into Mediator, Repository, UoW, or persistence adapters.
- Do not treat Strong ID as a DDD tactical carrier like aggregate, command, or domain event. It is an identity type and generation/runtime contract.

## Phase 1: Unit of Work Persist Intent

Phase-specific spec: [2026-07-22-cap4k-uow-persist-intent-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-22-cap4k-uow-persist-intent-design.md>).

### Purpose

This phase makes create, update, and remove intent explicit inside the UoW before JPA operation selection.

The public direction is:

```kotlin
enum class PersistIntent {
    CREATE,
    UPDATE,
}

interface UnitOfWork {
    fun persist(entity: Any, intent: PersistIntent = PersistIntent.UPDATE)
    fun remove(entity: Any)
    fun save(propagation: Propagation = Propagation.REQUIRED)
}
```

`remove(entity)` remains the public delete operation. `REMOVE` can exist internally as a pending-change intent, but `persist(entity, REMOVE)` is not a public API.

### Required Behavior

- Factory-created aggregates register `PersistIntent.CREATE`.
- Repository reads with `persist=true` register update intent through explicit `persist(entity, PersistIntent.UPDATE)`.
- Repository reads with `persist=false` remain validation-only and must not enter the UoW write set.
- Manual new entity creation outside a factory must use `persist(entity, PersistIntent.CREATE)`.
- Manual `persist(entity)` means update intent.
- Same-instance `CREATE` followed by default `persist(entity)` remains `CREATE`.
- Same-instance `CREATE` followed by `remove(entity)` cancels the pending change and produces no insert, delete, or listener event.
- `persistIfNotExist(entity)` is removed from the preferred contract because it restores existence-query semantics.
- Listener classification follows final pending change result: create intent becomes `PersistType.CREATE`, update intent becomes `PersistType.UPDATE`, delete intent becomes `PersistType.DELETE`.

### Why It Is First

Application-side IDs make entities look identified before persistence. That is the desired future. But once a new entity has a non-default ID, ID value alone cannot decide whether JPA should insert or update.

JPA itself has explicit operations: `persist` means insert a new managed entity, and `merge` means copy detached state into a managed instance. EF Core has a similar explicit-state model through APIs such as `Add`, `Update`, and `Attach`. Neither model should be used as an implicit business decision engine.

cap4k's UoW is the right place to carry command-side intent. Without this phase, later create-time ID work will continue to risk wrong create/update audit classification.

### Implementation Boundary

Allowed implementation cleanup:

- replace separate pending persist/remove sets with a single internal pending-change model;
- remove dead/commented persistence-context scan logic from `JpaUnitOfWork`;
- keep `UnitOfWorkInterceptor` shape unless a later design explicitly changes it;
- keep repository read APIs, but preserve the meaning of `persist=true` as update intent.

Out of scope:

- cap4k dirty/no-op detection;
- generated Strong ID template changes;
- ID generator API design;
- factory payload redesign.

## Phase 2: All-Entity Strong ID Support

Phase-specific spec: not written yet.

### Purpose

This phase expands generated Strong ID support from aggregate roots and references to every generated entity own ID that uses an application-side identity strategy.

The result should be:

- aggregate root own IDs can be Strong IDs;
- owned child entity own IDs can be Strong IDs;
- reference IDs remain reference IDs and do not generate new values from the owning entity;
- database identity IDs stay primitive nullable IDs and do not become Strong IDs;
- parent reference columns remain structural FK storage, not child own IDs.

### Required Design Decisions

The phase-specific spec must decide these points explicitly:

- Whether `StrongIdKind.AGGREGATE_ROOT` is replaced by a more general own-ID kind such as `ENTITY_ID`, or whether a new kind is added while preserving the old enum value during migration.
- How `StrongIdModel` represents owner entity name/package separately from owner aggregate name/package.
- How `canGenerateNew`, strategy name, and value type are represented for own IDs.
- How String-backed UUID7, UUID-backed UUID7, and Long-backed Snowflake Strong IDs are modeled.
- How generated Strong ID files are named and packaged for owned entities.
- How repository IDs, factory output, field imports, and type registry entries resolve owned entity ID types.
- How parent-ref storage compatibility is validated against the parent own-ID storage type.

### Required Behavior

- Application-side own-ID strategies generate Strong ID wrappers.
- `db_identity` or equivalent database identity does not generate a Strong ID wrapper.
- Own IDs and reference IDs are separate model facts even when their physical storage type is the same.
- `@RefAggregate` and `@RefId` continue to create or reuse non-generating reference identity types.
- Removed DB annotations such as `@Reference` must not re-enter Strong ID inference.
- Composite primary keys remain unsupported unless a future spec expands the aggregate generator.

### Implementation Boundary

Likely implementation areas:

- DB/source snapshot ID strategy parsing and validation;
- canonical model Strong ID metadata;
- type registry and import resolution;
- aggregate entity render models;
- Strong ID templates;
- repository/factory templates that mention ID types;
- focused runtime fixtures for `@Embeddable/@EmbeddedId` owned entity IDs.

Out of scope:

- Mediator ID allocation entry;
- runtime create/update UoW classification beyond the Phase 1 dependency;
- final create-time injection ergonomics;
- non-Strong-ID create-time injection.

## Phase 3: Mediator ID Allocation Entry

Phase-specific spec: not written yet.

### Purpose

This phase gives application code a stable way to request IDs through the cap4k facade it already uses.

Today `IdAllocator` exists, but `Mediator` does not expose it. A command handler that needs to manually allocate an ID must depend on lower-level ID runtime APIs or generated Strong ID static helpers. That is not the desired long-term application surface.

Manual allocation is needed when:

- the command response must include the ID before `save()`;
- a domain event or integration event must carry the ID before persistence;
- an external call or idempotency key must reference the new entity before persistence;
- user-authored construction is used instead of the generated aggregate factory;
- custom application logic must pass an ID into a factory payload or constructor intentionally.

### Required Design Decisions

The phase-specific spec must decide the public API shape. Candidate shapes include:

```kotlin
mediator.ids.next("uuid7", UUID::class)
mediator.nextId("uuid7", UUID::class)
mediator.nextId(ContentId::class)
```

The roadmap does not choose between these shapes. The design must weigh:

- keeping `Mediator` readable versus making ID allocation discoverable;
- exposing strategy strings versus typed ID allocation;
- allowing primitive ID allocation for compatibility versus prioritizing Strong ID types;
- whether generated Strong ID metadata can remove the need for caller-supplied strategy names;
- how Java callers should use the same API;
- whether `Mediator` should extend an ID allocation interface or expose one as a property.

### Required Behavior

- ID allocation has no UoW side effect.
- ID allocation does not persist or register an entity.
- Database identity strategies cannot allocate IDs through this API.
- Unknown strategies fail fast with clear diagnostics.
- Requested output type and strategy output type must match.
- The API should be available from application handlers without binding those handlers to JPA modules.
- The API should delegate to existing runtime ID strategy infrastructure where possible.

### Implementation Boundary

Likely implementation areas:

- `ddd-core` ID allocation facade contract;
- `Mediator` and `DefaultMediator`;
- starter/runtime wiring for the allocator implementation;
- tests that prove handlers can allocate IDs without using JPA-specific classes.

Out of scope:

- generated Strong ID constructor/factory injection;
- all-entity Strong ID model expansion;
- UoW create/update intent;
- persistence operation selection.

## Phase 4: Strong-ID Create-Time Injection

Phase-specific spec: not written yet.

### Purpose

This phase makes generated application-side Strong IDs real at creation time.

The important behavior is not merely "the ID exists before the database insert." It must exist before the new entity enters UoW persistence and before business code needs to publish or return it.

The happy path should become:

```kotlin
val aggregate = mediator.create(CreateOrderPayload(...))
val id = aggregate.id
mediator.save()
```

For generated application-side Strong IDs, `aggregate.id` is already real after `create(...)`. UoW does not need to mutate the root ID during `save()`.

### Required Design Decisions

The phase-specific spec must decide where create-time ID allocation happens:

- generated Strong ID companion, for example `OrderId.new()`;
- generated aggregate factory, by injecting an ID allocation facet and passing `OrderId` into the constructor;
- generated entity constructor default, if static generation is accepted;
- a generated identity factory or policy object;
- another explicit runtime hook.

This distinction matters. A constructor default such as `id: OrderId = OrderId.new()` is create-time, but it is not dependency injection unless `OrderId.new()` itself delegates to configured runtime policy. If the final goal is true injection, the phase-specific spec must say what object is injected, where it is injected, and how generated code accesses it.

The phase-specific spec must also decide:

- how generated Strong ID types encode or locate their generation strategy;
- how generated factories allocate aggregate-root IDs;
- how generated root factories allocate owned child IDs when nested child input generation exists;
- whether users can override a generated own ID intentionally, and through which non-default path;
- how JPA protected no-arg constructors avoid generating random IDs during hydration;
- how `@EmbeddedId` fields remain immutable enough for domain code while still usable by JPA;
- how generated application-side Strong IDs interact with the compatibility `@ApplicationSideId` save-time path.

### Required Behavior

- Application-side Strong ID own IDs are assigned before UoW registration.
- Generated root factory payloads do not ask for generated own IDs by default.
- Nested owned child inputs do not ask for generated child own IDs by default.
- Parent reference fields are not user input for owned child creation.
- JPA hydration must never allocate a new ID.
- UoW must not replace a non-default generated Strong ID during save.
- Create audit classification comes from Phase 1 UoW intent, not from ID defaultness.

### Implementation Boundary

Likely implementation areas:

- Strong ID template generation;
- aggregate/entity constructor render models;
- aggregate factory templates;
- nested owned child input/factory planning after its own design exists;
- runtime ID allocation integration if the chosen approach requires injection;
- JPA and Jackson fixtures proving Strong ID persistence, find-by-id, and boundary serialization still work.

Out of scope:

- primitive non-Strong-ID create-time injection;
- broad repository API redesign;
- changing domain event tactical modeling;
- cap4k dirty checking.

## Phase 5: Optional Non-Strong-ID Create-Time Injection

Phase-specific spec: future research only.

### Purpose

This phase explores whether cap4k can give primitive application-side IDs the same create-time ergonomics as Strong IDs.

This is explicitly lower priority. Strong ID should remain the preferred generated path because it gives cap4k a type-level place to attach identity semantics, parsing, validation, JSON shape, and generation helpers.

### Research Questions

- Can primitive `UUID`, `String`, or `Long` own IDs be generated at factory creation time without reintroducing save-time mutation as the normal path?
- Can primitive IDs carry enough strategy metadata without `@ApplicationSideId` becoming the main generated contract again?
- Can generated payloads stay clean while still allowing advanced manual ID override?
- Can UoW and audit stay intent-driven without falling back to default-value guessing?
- Does the extra optionality confuse the authoring story enough that it should remain unsupported?

### Required Constraints If Pursued

- Database identity still cannot promise an ID before persistence.
- Primitive application-side ID generation must be explicit opt-in, not a weakening of the Strong ID default.
- Primitive IDs must not bypass Phase 1 create/update intent.
- Existing compatibility `@ApplicationSideId` can be reused only if the phase-specific spec explains why it does not regress the generated happy path back to save-time assignment.

## Phase Dependencies

The roadmap should be executed in this order:

1. Phase 1 first, because all later create-time ID behavior depends on explicit write intent.
2. Phase 2 next, because generated own-ID modeling must be settled before the create-time story is locked in.
3. Phase 3 either after Phase 1 or alongside Phase 2 if the chosen API stays allocation-only and does not depend on the final Strong ID factory shape.
4. Phase 4 after Phase 1 and Phase 2, and after Phase 3 if the final design uses mediator-backed allocation for Strong IDs.
5. Phase 5 only after the first four phases are stable enough to evaluate whether primitive IDs truly need the same ergonomics.

If a later phase exposes a contradiction in an earlier phase, stop and roll back to the earliest phase that introduced the wrong assumption instead of papering over it in implementation.

## What A New Agent Should Read First

When delegating a slice from this roadmap, give the next agent:

1. This roadmap.
2. The phase-specific spec for the slice.
3. The current code or current diff for the exact module they will touch.

The agent should not be asked to read unrelated phase specs unless the chosen slice explicitly crosses those boundaries.

## Phase Spec Template

Every future phase spec should answer these questions explicitly:

- What is the user-visible problem?
- What current code or docs prove the problem exists?
- What new contract becomes true after the change?
- What stays out of scope?
- Which existing modules are affected?
- What are the rollback triggers?
- How is the change verified?

This keeps the roadmap distributable to newcomers and prevents each spec from depending on private chat context.
