# cap4k All-Entity Strong ID Design

Date: 2026-07-22

Status as of 2026-07-23: design approved for implementation and dispatched outside `master`; no Phase 2 implementation PR has been merged into `master` yet.

## Reader Contract

This is the phase 2 design spec for the cap4k identity roadmap. It is written for an implementation agent who has no chat history.

Read this spec together with the phase 1 UoW spec and the current code files listed below. Do not infer behavior from older chats. If a rule here conflicts with phase 1, this phase may intentionally supersede the phase 1 runtime shape because there are no external users and breaking redesign is allowed.

This spec is not an implementation plan. It fixes the design boundary for all-entity Strong ID support and the UoW baseline semantics required to make generated entity IDs work without returning to ID-based create/update guessing.

## businessIntent

Generated cap4k entities need a coherent identity story:

- aggregate root own IDs can already become generated Strong IDs;
- owned child entity own IDs need the same option;
- factory-created aggregates should have application-side IDs as soon as the factory returns;
- repository-loaded aggregate graphs should be observed as known-existing baselines whenever they are materialized;
- `persist=true` on repository reads should mean immediate `EXISTING` UoW enrollment, not baseline capture permission;
- update audit must not run merely because an entity was loaded through a repository;
- application-side IDs must not make JPA or cap4k guess whether an entity is new or existing from the ID value.

The desired authoring experience is:

```kotlin
val aggregate = mediator.create(payload)
val id = aggregate.id
mediator.save()
```

For factory-created application-side Strong IDs, the ID is available after `create(...)`. For new owned children added to an existing aggregate, phase 2 guarantees pre-persistence ID completion before flush, not immediate post-method-call ID availability.

## ubiquitousLanguage

- **Own ID**: the primary key of the entity itself.
- **Root own ID**: the own ID of an aggregate root.
- **Child own ID**: the own ID of an owned entity inside an aggregate lifecycle.
- **Parent reference**: the structural FK column that binds an owned child to its parent aggregate/entity.
- **Reference ID**: an ID field that points to another aggregate or external concept; it is stored as an ID value, not as a writable object graph.
- **Strong ID**: a generated type wrapper around the physical ID value.
- **Application-side ID**: an ID allocated before database insert.
- **Database-side ID**: an ID allocated by the database or provider during insert.
- **Persist intent**: the UoW input classification before persistence.
- **Persist type**: the post-flush listener classification such as `CREATE`, `UPDATE`, or `DELETE`.
- **Repository observation baseline**: the set of entity identities known to exist when a repository-loaded aggregate graph is materialized, regardless of the repository `persist` flag.
- **Existing enrollment**: a UoW registration that promotes an observed existing graph into the persistence workflow without implying it is dirty.
- **ID completion pass**: a repeatable scan that fills missing application-side own IDs where the active intent and baseline rules allow it.
- **Dirty existing entity**: an existing managed entity that the JPA provider reports as changed before flush.

## aggregateBoundaries

This phase treats aggregate roots and owned child entities as the same lifecycle category for identity ownership. Both are entities with own IDs. They differ only in how they enter the UoW:

- roots enter through aggregate factories or repositories;
- owned children enter through root-owned generated relations;
- reference ID fields do not enter as lifecycle entities.

The phase 2 mainline is limited to generated cap4k write models. The generated owned relation shape is `@OneToMany` with `@JoinColumn`, persistence cascade, and `orphanRemoval=true`. General handwritten JPA graphs, inverse `mappedBy` ownership, and object references outside generated aggregate relations are not phase 2 mainline behavior.

## cap4kCarriers

This phase changes framework/runtime identity carriers, not business tactical carriers.

- **Strong ID**: expands from aggregate-root/reference ID use to all generated entity own IDs that opt into application-side identity.
- **Unit of Work**: carries `CREATE`, `EXISTING`, and remove registration semantics.
- **Repository**: remains the aggregate read boundary and records observation baselines for every loaded aggregate graph. `persist=true` additionally enrolls the observed graph as `EXISTING`.
- **Aggregate Factory**: remains the root create boundary and registers `CREATE`.
- **Persist Listener**: receives final `PersistType` events only after UoW/JPA classification.
- **Generator**: must model root own IDs, child own IDs, parent references, and reference IDs as separate facts.

## cleanArchitecturePlacement

- `ddd-core` owns the public UoW intent vocabulary and facade contracts.
- `ddd-domain-repo-jpa` owns JPA/Hibernate baseline tracking, dirty inspection, pre-persistence ID assignment, and JPA operation selection.
- `cap4k-plugin-pipeline-api` owns canonical model fields such as Strong ID kind, owner entity metadata, ID strategy metadata, and physical value type metadata.
- `cap4k-plugin-pipeline-core` owns DB/schema-to-canonical inference.
- `cap4k-plugin-pipeline-generator-aggregate` and renderer templates own generated Kotlin/JPA shape.
- Starter modules own runtime wiring and configuration.

Core must not depend on Hibernate. Hibernate-specific dirty checking is allowed only behind the JPA runtime implementation boundary.

## generatorInputPlan

The source input is DB/schema metadata.

Every primary key column must explicitly declare an identity strategy. Phase 2 should not rely on implicit defaults for generated primary-key identity. The generator input contract must distinguish:

- application-side own ID strategy;
- database-side own ID strategy;
- parent reference FK;
- same-context aggregate reference ID through `@RefAggregate`;
- local external reference ID through `@RefId`.

`@ParentRef` must not be treated as an own ID strategy. `@RefAggregate` and `@RefId` remain reference identity metadata and do not request ID generation for the containing entity.

## skeletonExpectations

Expected structural changes:

- `UnitOfWork` input intent should expose `CREATE` and `EXISTING`, with deletion remaining `remove(entity)`.
- `PersistType.UPDATE` remains a result/listener type and must not be used as the pre-flush UoW input intent.
- Strong ID canonical metadata should represent generated own IDs for any entity, not only aggregate roots.
- Entity templates should render `@EmbeddedId` for Strong own IDs on roots and children.
- Strong ID generation should support own-ID metadata and only generated own IDs should have `new()` capability.
- Repository and factory templates must resolve root ID types through the generalized own-ID metadata.
- UoW/JPA runtime must maintain existing baselines for repository-loaded generated owned graphs.
- UoW/JPA runtime must make application-side ID completion idempotent across creation-time, persist-intent-time, and pre-flush passes.
- UoW/JPA runtime must assign missing generated application-side own IDs for `CREATE` entries and new owned children discovered under an `EXISTING` root before persistence.
- UoW/JPA runtime must trigger update listeners only for dirty existing entities.

Expected generated-vs-handwritten ownership:

- Generated entity and Strong ID files are generator-owned.
- Runtime UoW and JPA support are framework-owned handwritten code.
- Business handlers should not own UoW mechanics.

## handwrittenLogicSlots

No business handwritten logic is introduced by this phase.

Runtime handwritten implementation is limited to framework code:

- UoW pending entry model;
- existing baseline snapshot/diff support;
- JPA/Hibernate dirty inspection;
- application-side Strong ID assignment support;
- listener result classification.

## ownershipExceptions

None. If implementation requires user project entities to be handwritten to make all-entity Strong ID work, the implementation has left the phase 2 boundary.

## Scope Boundary

### In Scope

- Breaking redesign of the UoW persist intent names introduced by phase 1.
- All generated entity own IDs, including aggregate roots and owned children.
- Strong ID canonical metadata and generated type files.
- Generated JPA entity fields for Strong own IDs.
- Factory-created root graph create-time ID assignment at UoW enrollment.
- Existing aggregate baseline tracking for all repository loads.
- New owned child detection under existing generated `@OneToMany + @JoinColumn` relations.
- Hibernate-backed dirty inspection inside `JpaUnitOfWork`.
- Persist listener classification based on final create, dirty existing, and delete results.

### Out Of Scope

- Broad handwritten JPA graph support.
- `mappedBy` `@OneToMany` or `@OneToOne` as a generated write-model mainline.
- General object references as aggregate write-model relations.
- Treating `@RefAggregate` or `@RefId` as writable object relations.
- Composite primary keys.
- Mediator-facing manual ID allocation. That is phase 3.
- Full framework-level child method create-time ID injection after `root.addChild(...)`. This phase guarantees pre-persistence child ID completion for existing roots.
- Non-Strong-ID create-time ID injection.
- Core-layer dependency on JPA or Hibernate dirty checking.

## Code Map

Start implementation research from these files:

- [UnitOfWork.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt>)
- [DefaultAggregateFactorySupervisor.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt>)
- [DefaultRepositorySupervisor.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt>)
- [DefaultMediator.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt>)
- [JpaUnitOfWork.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt>)
- [JpaApplicationSideIdSupport.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt>)
- [JpaAggregateLoadPlanSupport.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaAggregateLoadPlanSupport.kt>)
- [PipelineModels.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt>)
- [DefaultCanonicalAssembler.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt>)
- [AggregateRelationInference.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt>)
- [AggregateInverseRelationInference.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt>)
- [EntityArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt>)
- [StrongIdArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt>)
- [FactoryArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt>)
- [RepositoryArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt>)
- [entity.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb>)
- [strong_id.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb>)

## Current Evidence

Current master evidence:

- `UnitOfWork` currently exposes `persist(entity)`, `persistIfNotExist(entity)`, `remove(entity)`, and `save(propagation)`.
- Phase 1 is merged to `master` as of 2026-07-23 through PR #128, merge commit `390c44bb`.
- `PersistIntent.CREATE/UPDATE` exists in `ddd-core`; phase 2 intentionally replaces the public `UPDATE` input intent with `EXISTING`.
- `DefaultAggregateFactorySupervisor.create(...)` creates an aggregate through the registered factory and then calls `unitOfWork.persist(instance, PersistIntent.CREATE)` before returning the instance.
- `DefaultRepositorySupervisor` currently calls `unitOfWork.persist(entity, PersistIntent.UPDATE)` only when repository reads use `persist=true`; repository reads are validation/read-only by default. Phase 2 changes the design contract: every repository read must record an observation baseline, while `persist=true` only controls immediate `EXISTING` enrollment.
- `JpaUnitOfWork` currently uses an object-identity pending-change model with internal `CREATE`, `UPDATE`, and `REMOVE` intents.
- `JpaUnitOfWork.save(...)` currently assigns application-side IDs before persistence through `JpaApplicationSideIdSupport.assignMissingIds(...)` and `assignMissingIdsToOwnedRelations(...)`.
- `JpaApplicationSideIdSupport.assignMissingIds(...)` already traverses owned cascaded `@OneToMany` and `@OneToOne` values and skips `@ManyToOne`.
- Current generated owned relation inference emits `ONE_TO_MANY`, `@JoinColumn`, cascade `PERSIST/MERGE/REMOVE`, `orphanRemoval=true`, `owned=true`, and `ONE_TO_MANY_JOIN_COLUMN`.
- Current generated inverse relation inference emits read-only `ManyToOne` with `insertable=false` and `updatable=false`.
- Current entity template renders generated `ONE_TO_MANY` as `@OneToMany(...)` plus `@JoinColumn(...)`; it does not render `mappedBy`.
- `@RefAggregate` and `@RefId` are resolved in canonical assembly as Strong ID field/reference metadata, not as generated writable JPA relation fields.
- `StrongIdKind` currently distinguishes `AGGREGATE_ROOT`, `AGGREGATE_REFERENCE`, and `REFERENCE`; owned entity own IDs are not first-class own-ID metadata.
- `StrongIdArtifactPlanner` currently allows `new()` only for `AGGREGATE_ROOT` Strong IDs.
- `StrongId` currently exposes `value: String`, and the default `strong_id.kt.peb` template validates/generates UUIDv7 strings.
- `DbIdStrategy` currently contains only `DB_IDENTITY`, so application-side generated strategies need new canonical input support before all-entity Strong ID generation can be complete.
- Hibernate is already a JPA runtime dependency. Current version evidence is `hibernate-core = "6.4.10.Final"` in the Gradle version catalog.

## Design Corrections Over Phase 1

Phase 1 is complete and merged to `master`. Phase 2 starts from that implemented `CREATE/UPDATE` UoW shape and may still perform breaking redesign because there are no external users.

The phase 2 public UoW input vocabulary is:

```kotlin
enum class PersistIntent {
    CREATE,
    EXISTING,
}

interface UnitOfWork {
    fun persist(entity: Any, intent: PersistIntent = PersistIntent.EXISTING)
    fun remove(entity: Any)
    fun save(propagation: Propagation = Propagation.REQUIRED)
}
```

Rules:

- `CREATE` means this entity is new in the current UoW.
- `EXISTING` means this entity is known to exist and should be enrolled into the UoW using an already observed repository baseline where available.
- `remove(entity)` remains the only public deletion entry.
- `UPDATE` is not a public input intent.
- `PersistType.UPDATE` remains a post-flush listener/result classification.
- `persistIfNotExist(entity)` remains removed.

The important conceptual split:

| Concept | Layer | Meaning |
|---|---|---|
| repository observation baseline | UoW/JPA internal | Known-existing graph captured by repository materialization. It is not a write registration. |
| `PersistIntent.CREATE` | UoW input | New entity lifecycle entry. |
| `PersistIntent.EXISTING` | UoW input | Known existing entity and baseline enrollment. |
| `remove(entity)` | UoW input | Deletion request. |
| `PersistType.CREATE` | listener result | Insert/create was applied. |
| `PersistType.UPDATE` | listener result | Existing entity was actually dirty and update listener should run. |
| `PersistType.DELETE` | listener result | Delete/remove was applied. |

Do not reintroduce `UPDATE` as an input synonym. It causes root and child lifecycle semantics to diverge and makes load-time audit pollution easy to reintroduce.

## UoW Runtime Source Rules

### Factory Source

`DefaultAggregateFactorySupervisor.create(payload)` must register the newly created aggregate as `CREATE`:

```kotlin
unitOfWork.persist(instance, PersistIntent.CREATE)
```

At that moment, `JpaUnitOfWork` must complete missing application-side own IDs for:

- the root entity itself;
- generated owned child entities currently reachable through generated owned relations.

This is phase 2's create-time ID guarantee. It is create-time from the caller's point of view because `mediator.create(...)` returns only after UoW enrollment and ID completion.

### Repository Source

Repository reads remain read-only by default.

Every repository read must record a repository observation baseline for the loaded aggregate graph. This baseline is observational. It must not trigger persistence operations, listener events, audit updates, or dirty classification by itself.

When a repository read uses `persist=false`, no UoW write enrollment occurs. The loaded graph is only observed, so later validation can still decide not to write anything.

When a repository read uses `persist=true`, `DefaultRepositorySupervisor` should record the same observation baseline and then call default `persist(entity)`, which means `EXISTING`:

```kotlin
unitOfWork.persist(loaded)
```

This promotes the observed baseline into an existing enrollment. It must not trigger update audit and must not mean the root will be updated.

If a handler first reads through `persist=false` and later decides the aggregate must be written, a later `unitOfWork.persist(loaded)` must reuse or promote the previously captured observation baseline. It must not capture a fresh "before" state after business mutations and pretend that state is the original database state.

### Manual Source

Manual construction for create must call:

```kotlin
unitOfWork.persist(entity, PersistIntent.CREATE)
```

Manual enrollment of an existing entity can call:

```kotlin
unitOfWork.persist(entity)
```

The reliable mainline for `EXISTING` is repository-loaded, provider-visible entities. Detached replacement objects that were not repository-loaded in the current UoW are not a phase 2 happy path. If the implementation cannot establish a baseline or provider dirty state for a manual existing object, it should fail fast with a diagnostic instead of pretending every existing object is dirty.

## Pending Entry Model

`JpaUnitOfWork` should use one pending-entry model rather than separate persist/remove sets.

Repository observation baselines may be stored in a separate baseline registry. They are not pending write entries. If an implementation chooses a single state machine, its `OBSERVED` state must be explicitly non-persistent until promoted by `persist(EXISTING)`.

Recommended internal concepts:

```kotlin
private enum class UnitOfWorkEntryKind {
    CREATE,
    EXISTING,
    REMOVE,
}

private data class UnitOfWorkEntry(
    val entity: Any,
    val kind: UnitOfWorkEntryKind,
)
```

The internal pending collection must use object identity. Entity `equals` and `hashCode` must not decide UoW registration identity.

Same-instance merge rules:

State merge and ID completion are separate concerns. A repeated `persist(...)` call may leave the entry kind unchanged and still run another ID completion pass.

| Existing State | New Action | Result State | ID Completion Behavior |
|---|---|---|---|
| no entry | `persist(CREATE)` | `CREATE` | Run `CREATE` rules for the root and currently reachable generated owned graph. |
| no entry, observed baseline exists | `persist(EXISTING)` | `EXISTING` | Promote the observation baseline and run `EXISTING` rules. |
| no entry, no observed baseline | `persist(EXISTING)` | `EXISTING` only if provider-visible existing state can be established; otherwise fail fast | Run `EXISTING` rules only after a trustworthy baseline or provider state exists. |
| `CREATE` | `persist(CREATE)` | `CREATE` | Run `CREATE` rules again to fill newly added generated owned children. |
| `CREATE` | `persist(EXISTING)` | `CREATE` | Keep `CREATE`; run `CREATE` rules again. A factory-created object cannot be downgraded to existing by a default persist call. |
| `EXISTING` | `persist(EXISTING)` | `EXISTING` | Run `EXISTING` rules again to fill newly discovered generated owned children. |
| `EXISTING` | `persist(CREATE)` | fail fast | Do not change an observed existing instance into a create entry. |
| `CREATE` | `remove(entity)` | cancel pending entry | Stop future completion for this entry. Already assigned in-memory IDs are not rolled back. |
| `EXISTING` | `remove(entity)` | `REMOVE` | Do not run ID completion. |
| `REMOVE` | `persist(CREATE)` | fail fast | Deleted entries cannot be re-persisted in the same UoW. |
| `REMOVE` | `persist(EXISTING)` | fail fast | Deleted entries cannot be re-persisted in the same UoW. |
| `REMOVE` | `remove(entity)` | `REMOVE` | Do not run ID completion. |

Same-identity conflict detection should be pragmatic and provider-aware. If two different instances represent the same persistent identity inside one UoW and at least one is registered as `CREATE` or `REMOVE`, fail before flush when the identity can be recognized safely.

## ID Completion Idempotency Contract

Application-side ID completion can run multiple times in one UoW. It must be deterministic and idempotent: a later pass may fill IDs that were missing in newly reachable entities, but it must never overwrite a value that was already present.

Allowed completion cases:

- The field is the entity's own primary key, not a parent FK or reference ID.
- The own ID strategy is explicitly application-side generated.
- The current own ID value is missing.
- The entity is classified as new by UoW intent plus baseline rules.

Forbidden completion cases:

- Existing non-missing own IDs must never be overwritten, including user-supplied IDs.
- Database-side ID strategies must never receive application-side generated values.
- Parent references, weak reference IDs, `@RefAggregate`, and `@RefId` fields must never be treated as own IDs.
- A baseline entity with a missing own ID must fail fast.
- An existing root with a missing own ID must fail fast.
- A baseline entity whose persistent own ID changed must fail fast.

New-versus-existing classification for ID completion must use intent and baseline, not ID presence alone:

| Baseline Status | Current Own ID | Completion Meaning |
|---|---|---|
| in baseline | present and unchanged | Existing entity; do not fill own ID. |
| in baseline | missing | Fail fast. |
| in baseline | changed | Fail fast. |
| not in baseline | present | New entity with user-supplied own ID; do not overwrite. |
| not in baseline | missing | New entity needing application-side own ID completion. |

Completion modes:

- `CREATE` mode fills the enrolled root and every generated owned entity currently reachable under it that is not already assigned.
- `EXISTING` mode never fills the existing root own ID. It may fill generated owned entities that are reachable under the root and absent from the observation baseline.
- Pre-flush completion is a final safety pass. It must use the same rules and must not introduce a different new-versus-existing heuristic.

The three supported timing points are:

1. creation-time assignment, where framework factories or generated create paths can produce IDs before the caller receives the entity;
2. intent-time completion, where every `unitOfWork.persist(...)` call may run the appropriate completion mode;
3. pre-flush completion, where `JpaUnitOfWork` catches newly reachable owned children that appeared after the last persist call.

## Existing Baseline Contract

An existing baseline is a UoW/JPA record of what entity identities were known to exist when an aggregate graph was observed through a repository load. It is captured for both `persist=false` and `persist=true` repository reads.

Baseline capture rules:

- Capture the root identity.
- Capture initialized generated owned relation entities under the root.
- Use persistent identity where available: entity type plus JPA ID.
- Use object identity only as a temporary fallback for objects that have no persistent identity yet.
- Do not capture `ManyToOne` inverse references.
- Do not capture `@RefAggregate` or `@RefId` fields as lifecycle entities.
- Do not initialize arbitrary lazy relations during baseline capture unless the repository load plan already requires whole-aggregate loading.
- `persist=true` must reuse the repository observation baseline it just captured.
- A later `persist(EXISTING)` on an object previously loaded with `persist=false` must promote the previously captured observation baseline.
- If `persist(EXISTING)` cannot find a trustworthy observation baseline or provider-visible existing state, it must fail fast or force an explicit reload path. It must not capture the current mutated graph as the original baseline.

Baseline diff rules before persistence:

- An entity in baseline and still reachable is existing.
- An entity not in baseline and reachable through generated owned relation traversal is a new owned child.
- A baseline entity no longer reachable from an enrolled root is a deletion candidate only when generated relation metadata and JPA orphan-removal semantics support deletion.
- A same persistent identity represented by a different object instance should fail fast unless implementation evidence proves JPA can reconcile it without audit ambiguity.

`clear()` plus adding new children is allowed and means old children are removed by orphan-removal semantics while new children are created. `clear()` plus adding the same loaded child instances back should not become delete-plus-create.

## Generated Owned Relation Traversal

The phase 2 traversal should be based on generated cap4k write-model relation semantics, not general JPA association semantics.

Phase 2 mainline traversal includes:

- `@OneToMany`;
- generated owned relation metadata;
- `@JoinColumn`;
- cascade that includes persistence behavior;
- `orphanRemoval=true` for deletion semantics;
- initialized collection values.

Phase 2 traversal excludes:

- `@ManyToOne`;
- read-only inverse relations;
- `mappedBy` relations;
- ordinary object references not represented in `model.aggregateRelations`;
- reference ID fields;
- lazy values that are not initialized by the repository load plan or normal business access.

The current helper `JpaApplicationSideIdSupport.ownedRelationValues(...)` can inspire traversal, but phase 2 should avoid baking "all cascaded JPA relations are owned lifecycle relations" into the new Strong ID path. Prefer a small runtime metadata extractor that mirrors generated relation semantics.

## Strong ID Model

Phase 2 changes the meaning of the canonical Strong ID model. It is no longer just a root/reference type catalog. It becomes the generator/runtime identity catalog for any generated entity own ID or reference ID.

### Required Model Concepts

The model should carry these facts separately:

- `typeName`: the generated Strong ID wrapper name.
- `packageName`: the generated package.
- `kind`: whether this is an own ID or a reference ID.
- `ownerEntityName` / `ownerEntityPackageName`: the entity that owns the physical column.
- `ownerAggregateName` / `ownerAggregatePackageName`: the aggregate that owns the entity, if applicable.
- `valueType`: the physical scalar backing type.
- `idStrategy`: the source strategy name or policy key.
- `canGenerateNew`: whether this Strong ID can be created through a `new()`-style runtime helper.
- `isEmbeddedId`: whether this Strong ID is rendered as `@EmbeddedId`.

### Kind Semantics

The current `StrongIdKind` values are not sufficient for phase 2 if they continue to encode aggregate-root-versus-reference only. The phase 2 model should either:

1. replace the kind with an own-ID-centric model such as `OWN_ID`, `REFERENCE`, `AGGREGATE_REFERENCE`; or
2. keep the old values only as migration aliases and add a new own-ID kind for generated entity IDs.

The design choice is intentionally explicit because aggregate-root own ID is no longer special enough to be the only generating case.

Recommended semantic split:

- **Own ID**: generating or owning entity primary key.
- **Reference ID**: non-generating ID field that references another concept.
- **Parent reference**: structural FK field that is not an ID generation target.

`@RefAggregate` and `@RefId` remain reference ID metadata. They must not gain `new()` helpers.

### Value Type Rules

Phase 2's required implementation mainline is the current String-backed UUIDv7 Strong ID route, expanded from aggregate roots to all generated entity own IDs.

The canonical model should still carry physical value type metadata so later phases can add UUID-backed or Long-backed Strong IDs without another model rewrite. That metadata is not permission to silently claim runtime support for value types that the current `StrongId` interface and default template cannot represent.

Rules:

- String-backed UUIDv7 Strong IDs are in scope for implementation.
- UUID-backed and Long-backed Strong IDs are model-aware future expansion points unless the phase 2 implementation explicitly revises this spec before coding them.
- Unsupported application-side strategy/value-type combinations must fail fast with a clear generator diagnostic.
- Database-side IDs remain outside Strong ID generation for this phase.

## Generator Input and Output Plan

The generator still uses DB/schema as the primary identity source.

### Input Rules

The phase 2 generator must infer or validate:

- every primary key has an explicit `@IdStrategy`;
- application-side own IDs generate Strong ID wrappers;
- database-side IDs remain primitive/provider-generated;
- `@RefAggregate` resolves to the referenced aggregate own ID type;
- `@RefId` creates or reuses a reference Strong ID type;
- parent FK columns are not Strong ID generation targets;
- child own IDs are separate from parent FK columns even when they share the same physical storage family.

### Output Rules

Generated files should follow these rules:

- root own-ID Strong types remain generated;
- child own-ID Strong types are now generated the same way as root own-ID Strong types;
- factory templates use generated own-ID metadata to initialize create paths;
- repository templates use generated own-ID metadata to load and persist current roots;
- entity templates render `@EmbeddedId` for Strong own IDs;
- strong ID template generation keeps `new()` only for own IDs that are application-side generated;
- reference IDs do not receive `new()` and do not imply create-time generation.

### Strong ID Artifact Planner Rules

The planner must stop keying `canGenerateNew` on aggregate-root-only semantics. It should key it on own-ID generation capability.

The planner must also stop treating `AGGREGATE_ROOT` as the only entity that can own an embedded Strong ID. Owned child own IDs should be allowed to flow through the same template path.

### Factory Artifact Rules

Generated factory code must understand generated own IDs for the aggregate root and any generated owned children that exist in the factory payload shape.

Phase 2 does not require a full create-time child injection UX. It only requires that if a generated owned child exists in the created graph, its missing own ID can be completed before the entity is handed to JPA.

### Entity Artifact Rules

Entity rendering should keep the following distinctions:

- own ID fields use Strong ID wrappers and `@EmbeddedId`;
- parent references use structural FK fields and are not own IDs;
- read-model weak references stay out of the write-model relation renderer;
- inverse read-only relations remain read-only and are not owned IDs.

## JPA Runtime Algorithm

`JpaUnitOfWork` should move from "ID existence plus entity state" to "baseline plus intent plus provider dirty state."

Recommended processing order:

1. Repository reads capture observation baselines when entities are materialized.
2. Each `persist(...)` call merges the requested intent into the pending entry model.
3. Each `persist(...)` call runs intent-time ID completion with the active entry state after merge.
4. `save(...)` drains pending entries from the UoW.
5. Split entries into create, existing, and remove buckets.
6. Retrieve or promote existing baseline identities for enrolled existing entities.
7. Run a pre-flush `CREATE` completion pass for create entries.
8. For existing roots, diff current owned graph against baseline and register newly discovered owned children as create entries.
9. Run a pre-flush `EXISTING` completion pass for newly discovered owned children.
10. Ask the JPA provider which managed existing entities are actually dirty.
11. Apply persistence operations.
12. Flush.
13. Dispatch final listener classifications.

### Provider Dirty Inspection

This phase expects `JpaUnitOfWork` to use JPA/Hibernate capabilities to distinguish dirty managed entities from clean loaded entities.

The implementation may use Hibernate SPI because the runtime module is already JPA/Hibernate-specific. The boundary requirement is that the core API does not become provider-specific.

Dirty inspection must happen before update listeners are emitted. If update listeners run before dirty state is known, `updatedAt` or similar audit fields will pollute the entity and create false updates.

### Listener Classification

Final listener classification must be derived from actual applied result:

- create entries that become inserts -> `PersistType.CREATE`
- existing entries that are dirty -> `PersistType.UPDATE`
- remove entries that become deletes -> `PersistType.DELETE`

Loaded existing entities that are not dirty must not receive update listener events.

### Audit Rules

Audit handlers must not be triggered by load alone.

Audit handlers must not be triggered by baseline enrollment alone.

Audit handlers must run only when a persisted result exists.

This is the mechanism that keeps `updatedAt` clean while still allowing repository-loaded entities to enter the UoW as managed or baseline-known objects.

## Current Code Changes Expected

This phase is expected to touch the following implementation surfaces:

- `ddd-core` UoW API and forwarding;
- `ddd-domain-repo-jpa` UoW runtime and ID support;
- canonical assembler and aggregate planners;
- aggregate Strong ID planner;
- entity/factory/repository templates;
- phase 2 runtime fixtures and tests.

Likely deletions or removals:

- `persistIfNotExist(...)`;
- `UPDATE` as a public UoW input intent;
- root-only Strong ID kind gating;
- any root-only `new()` capability check;
- any repository observation baseline logic that only runs for `persist=true`;
- any `JpaUnitOfWork` logic that classifies create versus update by ID non-defaultness or existence queries.

## verificationEvidence

Static and focused runtime evidence should prove:

- `unitOfWork.persist(entity, CREATE)` assigns IDs immediately during factory creation;
- repository `persist=false` reads capture observation baselines without UoW write enrollment;
- a later `unitOfWork.persist(entity)` on a previously observed `persist=false` repository result promotes the original baseline;
- `unitOfWork.persist(entity)` on repository-loaded aggregates does not imply update audit;
- repeated `unitOfWork.persist(entity)` calls rerun idempotent ID completion and can fill owned children added after the previous call;
- newly added owned children under an existing aggregate receive generated own IDs before flush;
- unchanged repository-loaded entities do not emit update listeners;
- mutated repository-loaded entities do emit update listeners;
- generated own-ID Strong types exist for both roots and owned children;
- reference IDs remain reference IDs and do not gain create helpers;
- `@RefAggregate` and `@RefId` remain separate from lifecycle own IDs;
- generated owned relation templates still render `@OneToMany + @JoinColumn`, not `mappedBy`.

Recommended test classes to update or add:

- `JpaUnitOfWorkTest`
- `JpaApplicationSideIdSupportTest`
- `StrongIdJpaRuntimeTest`
- `AggregateArtifactPlannerTest`
- `DefaultCanonicalAssemblerTest`

## rollbackTriggers

Roll back this design if any of these assumptions fail during implementation:

- repository-loaded existing baselines cannot be distinguished from actual dirty updates;
- generated owned child IDs cannot be completed before flush without forcing update audit on clean entities;
- Strong ID metadata cannot be generalized from root-only to all own IDs without breaking type resolution;
- generated relation traversal must support `mappedBy` as the default write model;
- reference IDs start acting like lifecycle entities;
- parent references cannot remain separate from own IDs.

## Implementation Notes For Newcomers

- Do not interpret "existing" as "updated".
- Do not interpret "loaded through repository" as "needs update audit."
- Do not use ID defaultness to guess create versus existing.
- Do not make Strong ID type generation root-only.
- Do not let read-model references leak into write-model ownership.
- Do not add a second identity vocabulary without documenting which layer owns it.

## openDecisions

None blocking for phase 2 spec handoff.
