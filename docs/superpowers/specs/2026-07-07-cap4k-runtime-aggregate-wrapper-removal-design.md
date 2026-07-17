# Cap4k Runtime Aggregate Wrapper Removal Design

Date: 2026-07-07
Status: Proposed

## Summary

The aggregate `wrapper` generator surface has already been removed from the active pipeline and docs in the earlier wrapper-removal slice.
The remaining problem is runtime and API residue: `ddd-core` and `ddd-domain-repo-jpa` still expose and support the old `Aggregate` wrapper model as if it were a current framework capability.

This slice removes that runtime wrapper surface.

The intended outcome is narrow:

- remove the old `Aggregate` wrapper API from `ddd-core`
- remove `AggregateSupervisor` from `Mediator`, `X`, and `DefaultMediator`
- remove JPA aggregate-wrapper predicate and supervisor support
- remove `DefaultDomainEventSupervisor` and `JpaUnitOfWork` compatibility paths that unwrap and rewrap aggregate wrappers
- keep the current aggregate factory and behavior model intact

This is a breaking cleanup of obsolete wrapper semantics, not a removal of aggregate roots, factories, repositories, UoW, or domain events.

## Source Context

Primary context:

- static audit and follow-up discussion from Codex thread `019f3706-233d-7572-9b54-eb7f27374ffc`
- previous wrapper pipeline/doc removal spec: `docs/superpowers/specs/2026-05-08-cap4k-remove-wrapper-from-core-pipeline-and-docs-design.md`
- previous implementation plan explicitly left runtime wrapper compatibility out of scope: `docs/superpowers/plans/2026-05-08-cap4k-remove-wrapper-from-core-pipeline-and-docs.md`

Current user decision:

- do not delete `AggregateFactory`
- do not delete `AggregatePayload`
- do not delete `AggregateElement`
- do not delete `Mediator.factories` / `Mediator.fac`
- do remove the old aggregate wrapper model and its compatibility branches

## Baseline And Constraints

Execution baseline:

- static-only inspection of the local `cap4k` workspace on 2026-07-07
- no compile, run, test, install, git index update, or commit was performed while drafting this design

Hard constraints for this slice:

- do not remove `ddd-domain-repo-jpa` as a module
- do not remove the ordinary JPA repository / UoW path
- do not remove aggregate factory generation
- do not remove `Behavior.kt` generation
- do not remove `AggregateFactorySupervisor` or `DefaultAggregateFactorySupervisor`
- do not use this slice to remove Specification, ValueObject, Snowflake, Locker, request/event/saga scheduling, or the whole Querydsl module unless a direct aggregate-wrapper dependency forces a local edit
- do not preserve wrapper compatibility by renaming the old API; this slice is a deletion design

## Current Runtime Wrapper Surface Classification

### Current Surfaces To Keep

These are current aggregate-root authoring surfaces and are outside deletion scope:

1. Aggregate factory API
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateFactory.kt`
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregatePayload.kt`
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateFactorySupervisor.kt`
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateFactorySupervisorSupport.kt`
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt`

2. Aggregate element annotation
   - `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/AggregateElement.kt`
   - current templates still emit `@AggregateElement` for aggregate roots and nested elements

3. Mediator factory facade
   - `Mediator.factories`
   - `Mediator.fac`
   - `X.factories`
   - `X.fac`

4. Behavior model
   - generated `*Behavior.kt` classes remain the intended handwritten domain behavior extension point
   - reference project already uses `events().attach(this)` from behavior files rather than old wrapper helpers

5. Repository and UoW concepts
   - `Mediator.repositories`
   - `Mediator.uow`
   - `JpaUnitOfWork` as the JPA UoW implementation, after wrapper compatibility is removed

### Obsolete Runtime/API Surfaces To Delete

These represent the old wrapper model and should be removed as active framework API:

1. `ddd-core` wrapper API
   - `Aggregate.kt`
   - `AggregatePredicate.kt`
   - `AggregateSupervisor.kt`
   - `AggregateSupervisorSupport.kt`

2. Mediator wrapper facade
   - `Mediator.aggregateSupervisor`
   - `Mediator.aggregates`
   - `Mediator.agg`
   - corresponding `X.aggregates` and `X.agg` shortcuts
   - `DefaultMediator` implementations of `AggregateSupervisor`

3. JPA wrapper implementation
   - `JpaAggregatePredicate.kt`
   - `JpaAggregatePredicateSupport.kt`
   - `DefaultAggregateSupervisor.kt`
   - starter auto-configuration bean for `DefaultAggregateSupervisor`

4. Wrapper compatibility branches
   - `DefaultDomainEventSupervisor` unwrapping `Aggregate<*>` before attach/release/access
   - `JpaUnitOfWork` unwrapping wrapper arguments before persist/remove and rewrapping after persistence
   - `DefaultRepositorySupervisor` branch for `JpaAggregatePredicate`
   - `JpaPredicate.toAggregatePredicate`
   - Querydsl `toAggregatePredicate` bridge while the Querydsl slimming slice is still pending

5. Compiler/code-analysis residue
   - `Cap4kIrGenerationExtension` handling for `AggregatePredicate`
   - any type-resolution branch that exists only to support wrapper aggregate predicates

## Design Decisions

### 1. Delete, Do Not Deprecate, The Runtime Wrapper API

The pipeline no longer generates wrapper artifacts and the reference project no longer uses wrapper APIs.
Keeping the runtime API as deprecated would keep the maintenance surface alive without serving the current model.

The deletion should be direct:

- remove the public wrapper API files
- remove exports and imports that mention the wrapper API
- update or delete tests that were only proving wrapper behavior

This is expected to be an API-breaking change for external consumers that still call `Mediator.aggregates`, `Mediator.agg`, `Aggregate._wrap`, or `Aggregate._unwrap`.

### 2. Preserve Aggregate Factory As The Creation Boundary

`AggregateFactory`, `AggregatePayload`, `AggregateElement`, `AggregateFactorySupervisor`, and `Mediator.factories` are not wrapper semantics.
They are still used by the reference project and by the current aggregate creation model.

`DefaultMediator` should continue to delegate factory calls to `aggregateFactorySupervisor`.
The starter should continue to register `DefaultAggregateFactorySupervisor`.

### 3. Treat UoW Inputs As Actual Persisted Entities

After wrapper deletion, `JpaUnitOfWork.persist`, `persistIfNotExist`, `remove`, and `save` should treat their argument as the actual entity/value passed by the caller.

Remove:

- `wrapperMapThreadLocal`
- `unwrapEntity`
- `rewrapEntity`
- `Aggregate` import
- `_unwrap` and `_wrap` usage

The resulting UoW behavior is simpler: it persists/removes the received entity and clears the normal UoW thread-local state.

### 4. Domain Event Supervisor Should Use Entity Identity Directly

`DefaultDomainEventSupervisor` should no longer accept wrapper objects as alternate handles for the underlying entity.
It should attach, release, and inspect events against the object passed by the caller.

Remove:

- `Aggregate` import
- wrapper-aware `unwrapEntity`
- wrapper unwrapping before event operations

Reference behavior classes already attach events against `this`, which is the aggregate root entity instance.

### 5. Repository Predicates Stay Entity-Based

The active repository path should stay based on entity predicates and generated `S*` helpers.
The old bridge from `JpaPredicate` / Querydsl predicate to `AggregatePredicate` should be removed.

Remove:

- `JpaPredicate.toAggregatePredicate`
- `QuerydslPredicate.toAggregatePredicate`
- `DefaultRepositorySupervisor` branch for `JpaAggregatePredicate`

Do not remove ordinary repository lookup APIs in this slice.

### 6. Remove Wrapper Supervisor From Mediator Composition

`Mediator` should no longer extend `AggregateSupervisor`.
`DefaultMediator` should no longer implement aggregate wrapper operations.
`X` should no longer expose `aggregates` or `agg`.

Keep:

- `Mediator.factories` / `Mediator.fac`
- `Mediator.repositories` / `Mediator.repo`
- `Mediator.uow`
- other existing mediator capabilities unrelated to wrapper aggregates

### 7. Querydsl Bridge Cleanup Is Local, Full Querydsl Removal Is Separate

`ddd-domain-repo-jpa-querydsl` is already a high-priority slimming candidate for Kotlin projects.
However, this design does not need to delete the whole module to remove runtime aggregate wrapper support.

Minimum required action in this slice:

- remove Querydsl imports and extension functions that produce `AggregatePredicate`

Preferred sequencing if Querydsl removal is accepted first:

- delete the Querydsl module/default starter exposure in its own slimming slice
- this wrapper slice then only needs to ensure no aggregate-wrapper references remain

## File-Level Change Contract

### Delete Or Empty Wrapper API Files

Expected deletion candidates:

- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/Aggregate.kt`
- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregatePredicate.kt`
- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisorSupport.kt`
- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicate.kt`
- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupport.kt`
- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`

If binary/source compatibility policy requires a staged removal, these files may first become deprecated stubs in a separate transitional release.
For this requested cleanup, the target state is deletion.

### Modify Core Mediator Files

Expected edits:

- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
  - remove `AggregateSupervisor` inheritance
  - remove `aggregateSupervisor` property
  - remove `aggregates` and `agg` accessors
  - preserve `aggregateFactorySupervisor`, `factories`, and `fac`

- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/X.kt`
  - remove `aggregates` and `agg`
  - preserve `factories` and `fac`

- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
  - remove constructor/property dependency on `AggregateSupervisor`
  - remove `AggregateSupervisor` delegation methods
  - preserve `AggregateFactorySupervisor` delegation methods

### Modify Starter Wiring

Expected edits:

- `cap4k/cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
  - remove `DefaultAggregateSupervisor` import
  - remove `defaultAggregateSupervisor` bean
  - preserve `defaultAggregateFactorySupervisor` bean

Starter initialization tests that assert aggregate supervisor registration should be updated or removed.

### Modify JPA UoW And Event Supervisor

Expected edits:

- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
  - remove wrapper map thread-local state
  - remove wrapper unwrap/rewrap helpers
  - persist/remove actual entity arguments directly

- `cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
  - remove wrapper-aware entity unwrapping
  - attach/release/access events by direct entity instance

These two files are the key compatibility points called out by the audit.
They must be handled in the same removal slice as the public wrapper API; otherwise deleted API references will remain in runtime implementation code.

### Modify Repository Predicate Bridges

Expected edits:

- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
  - remove `toAggregatePredicate`
  - remove imports of wrapper aggregate predicate types

- `cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
  - remove `JpaAggregatePredicate` branch
  - keep ordinary entity predicate branches

- `cap4k/ddd-domain-repo-jpa-querydsl/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/querydsl/QuerydslPredicate.kt`
  - remove aggregate predicate bridge, unless the whole module is removed in a prior slice

### Modify Code Analysis Plugin

Expected edits:

- `cap4k/cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
  - remove `aggregatePredicateFq`
  - remove branches that construct or classify `AggregatePredicate`
  - preserve current entity predicate / specification / repository analysis that is still active

This should be reviewed carefully because compiler plugin cleanup can fail silently at static review level if an import is left unused or a branch is only reachable for generated code.

## Test And Fixture Contract

Wrapper-specific tests should be deleted or rewritten according to what they prove.

Delete or rewrite candidates:

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/JpaAggregatePredicateSupportTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`
- wrapper-specific branches in `JpaUnitOfWorkTest.kt`
- wrapper-specific branches in `DefaultDomainEventSupervisorTest.kt`
- starter tests that assert a `DefaultAggregateSupervisor` bean exists

Keep or update tests that protect current behavior:

- factory supervisor registration and lazy initialization
- `Mediator.factories` / `Mediator.fac`
- repository supervisor behavior for ordinary predicates
- UoW persistence behavior for direct entity instances
- domain event attachment for direct entity instances
- generator tests that assert wrapper artifacts are absent

## Acceptance Criteria

The implementation is complete when all of the following are true:

1. `AggregateFactory`, `AggregatePayload`, `AggregateElement`, `AggregateFactorySupervisor`, `DefaultAggregateFactorySupervisor`, `Mediator.factories`, and `Mediator.fac` remain available.
2. Production code has no imports or type references to the old wrapper API:
   - `Aggregate`
   - `AggregatePredicate`
   - `AggregateSupervisor`
   - `AggregateSupervisorSupport`
   - `JpaAggregatePredicate`
   - `JpaAggregatePredicateSupport`
   - `DefaultAggregateSupervisor`
3. Production code has no `_wrap` or `_unwrap` calls.
4. `Mediator`, `X`, and `DefaultMediator` no longer expose wrapper aggregate supervisor APIs.
5. `DefaultDomainEventSupervisor` no longer unwraps aggregate wrappers before event operations.
6. `JpaUnitOfWork` no longer unwraps or rewraps aggregate wrappers around persist/remove operations.
7. `DefaultRepositorySupervisor`, `JpaPredicate`, and Querydsl bridge code no longer expose aggregate-wrapper predicates.
8. Starter auto-configuration no longer registers `DefaultAggregateSupervisor`.
9. Current pipeline/generator behavior remains wrapper-free and still emits behavior/factory/entity artifacts as before.
10. The reference content-studio model continues to rely on factories, repositories, UoW, generated `S*` predicates, and `*Behavior.kt`, with no wrapper dependency added back.

## Static Verification Checklist

These checks are static-only and safe to run under the current workspace rules:

```powershell
rg -n "AggregateSupervisor|AggregatePredicate|JpaAggregatePredicate|DefaultAggregateSupervisor|AggregateSupervisorSupport|JpaAggregatePredicateSupport" cap4k
rg -n "_wrap|_unwrap|wrapperMapThreadLocal|unwrapEntity|rewrapEntity" cap4k
rg -n "Mediator\.aggregates|Mediator\.agg|X\.aggregates|X\.agg" cap4k
rg -n "toAggregatePredicate" cap4k
rg -n "AggregateFactory|AggregatePayload|AggregateElement|Mediator\.factories|Mediator\.fac" cap4k
```

Expected result:

- the first four searches should return only archived specs/plans or historical documentation, not production `src/main` code
- the final search should still return active production code and generated/reference usage

## Recommended User/CI Verification

Compilation and test execution are intentionally not part of this spec authoring pass.
If the implementation is later applied, the user or CI should run focused verification for affected modules:

```powershell
./gradlew :ddd-core:test
./gradlew :ddd-domain-repo-jpa:test
./gradlew :cap4k-ddd-starter:test
./gradlew :cap4k-plugin-code-analysis-compiler:test
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
```

If Querydsl is not removed before this slice, include:

```powershell
./gradlew :ddd-domain-repo-jpa-querydsl:test
```

## Migration Notes For Consumers

Consumers using the old wrapper API must move to current entity-based patterns:

- replace `Mediator.aggregates` / `Mediator.agg` creation with `Mediator.factories` where aggregate factory creation is intended
- replace aggregate wrapper lookups with `Mediator.repositories` and generated entity predicates
- persist direct aggregate root entities through `Mediator.uow`
- attach domain events from behavior/entity methods using the active event attachment API

No migration is needed for code already using the reference content-studio pattern:

- factory payloads for creation
- generated `*Behavior.kt` for aggregate behavior
- repositories with generated predicates
- direct entity persistence through UoW

## Risks

1. External API breakage
   - downstream projects still using wrapper aggregate APIs will fail at source compatibility level
   - this is intentional for the deletion target, but it should be called out in release notes

2. Hidden compiler plugin coupling
   - `Cap4kIrGenerationExtension` may contain wrapper-specific branches that are not easy to validate without compilation
   - static search must be paired with later module tests by user/CI

3. Starter test churn
   - tests around bean initialization may assert the old aggregate supervisor bean exists
   - these tests should be updated to assert the factory supervisor remains available instead

4. Querydsl sequencing
   - if Querydsl removal happens first, wrapper cleanup there becomes unnecessary
   - if Querydsl remains, its `toAggregatePredicate` bridge must be deleted in this slice

5. Documentation drift
   - historical docs under `docs/superpowers/specs/**` and `docs/superpowers/plans/**` may still mention wrapper by design
   - active docs should not present wrapper as a supported runtime capability

## Open Questions

1. Should this removal be released as a single breaking change, or staged with one short deprecation release?
2. Should the Querydsl module be removed before this slice to reduce the number of wrapper bridge edits?
3. Should active public docs get a short migration note from wrapper aggregate APIs to factories/repositories/UoW, or should release notes be the only migration surface?

## Non-Goals

This design does not remove or redesign:

- aggregate roots as domain entities
- `AggregateFactory`
- `AggregatePayload`
- `AggregateElement`
- `AggregateFactorySupervisor`
- `DefaultAggregateFactorySupervisor`
- `Mediator.factories` / `Mediator.fac`
- generated `*Behavior.kt`
- ordinary JPA repository support
- `UnitOfWork`
- domain event support
- Specification support
- old ValueObject interface support
- Snowflake support
- Locker support
- request/event/saga scheduling
- full Querydsl module exposure, except for direct aggregate-wrapper bridge removal