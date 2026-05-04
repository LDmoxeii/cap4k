# Cap4k Aggregate Inverse-Navigation Owner and Fetch Policy Design

Date: 2026-05-04

Status: Proposed

Scope: define the parent-child ownership contract for aggregate inverse navigation, settle default fetch policy for owned parent-child bindings, and require real generated-entity audit verification once mapping safety is restored.

Out of scope: `mappedBy`, `@JoinTable`, `ManyToMany`, read/write association-scope separation, repository backend replacement, frontend TypeScript generation, legacy compatibility.

## Background and Dependency

This is the next mainline slice after the currently active relation line and the recently completed special-field/audit alignment work.

Relevant prior specs:

- `docs/superpowers/specs/2026-04-18-cap4k-aggregate-relation-parity-design.md`
- `docs/superpowers/specs/2026-04-20-cap4k-aggregate-relation-side-jpa-control-parity-design.md`
- `docs/superpowers/specs/2026-04-20-cap4k-aggregate-inverse-relation-read-only-parity-design.md`
- `docs/superpowers/specs/2026-05-03-cap4k-special-fields-managed-write-surface-and-only-engine-audit-alignment-design.md`

Dogfood evidence is already recorded in:

- `only-danmuku-zero/docs/dogfood/cap4k-pipeline-issue-backlog.md`

## Problem

Current generated parent-child output is not contractually unified.

Observed shape:

1. root-child output often uses:
   - parent `@OneToMany + @JoinColumn`
   - child scalar FK field
   - child read-only `@ManyToOne(insertable = false, updatable = false)`
2. child-child output can instead render:
   - parent `@OneToMany + @JoinColumn`
   - child owner-side `@ManyToOne + @JoinColumn`
3. when both sides own the same FK column, Hibernate fails at startup with duplicated-column mapping errors.

This means the current roadmap item is not only about eager/lazy defaults. It is first about restoring one stable ownership contract for aggregate-owned parent-child bindings.

There is a second problem behind the same line:

- default fetch policy for inverse navigation is still unsettled
- if mapping defaults become too eager, `AggregateLoadPlan.MINIMAL` stops having a real "do not force owned collections to load" boundary

There is also one completion-gap problem:

- runtime audit is currently proven only through probe/smoke coverage
- the real generated-entity audit verification was blocked by the broken parent-child mapping
- after this fix, audit verification must move back to real generated entities

## Goals

1. Define one parent-child ownership contract that applies equally to `root-child` and `child-child`.
2. Keep aggregate write behavior centered on the parent-side owned collection.
3. Preserve `AggregateLoadPlan.MINIMAL` and `AggregateLoadPlan.WHOLE_AGGREGATE` as meaningful use-case loading contracts.
4. Keep the inverse-navigation line bounded; do not reopen a general-purpose JPA ownership framework.
5. Make completion contingent on real generated-entity audit verification, not only probe smoke tests.

## Non-Goals

This slice must not:

- redesign aggregate parent-child ownership to child-side owner + `mappedBy`
- introduce `mappedBy`, `@JoinTable`, or `ManyToMany`
- reopen generic relation inference for non-owned association families
- introduce new user-facing DSL for relation ownership or fetch policy
- allow per-relation eager/lazy override on owned parent-child bindings
- replace the current repository/load-plan mechanism with annotation-driven loading policy

## Final Decisions

1. `@P` and the direct-parent FK column `@Ref` describe one parent-child relation, not two independent relations.
2. aggregate-owned parent-child bindings use parent-side ownership only:
   - parent: owner-side `@OneToMany + @JoinColumn`
   - child: optional read-only `@ManyToOne + @JoinColumn(insertable = false, updatable = false)`
3. `root-child` and `child-child` use the same contract.
4. parent inverse-navigation collections are generated only for owned child entities.
5. parent `@OneToMany` defaults to `LAZY`.
6. child read-only back-reference `@ManyToOne` defaults to `LAZY`.
7. this slice does not allow local eager/lazy override for owned parent-child bindings.
8. any direct-parent binding ambiguity or duplicate-owner situation is fail-fast.
9. completion requires real generated-entity audit verification after mapping safety is restored.

## Parent-Child Source Binding Contract

### One relation, one binding

For an owned child table:

- table-level `@P` states that the table belongs to a direct aggregate parent
- the child column-level `@Ref` on the direct-parent FK identifies which physical FK column carries that parent-child binding

These two inputs must resolve to one binding. They must not generate two independent JPA ownership lines.

### Direct-parent FK resolution

For every table with `parentTable` truth:

1. collect child columns that reference the direct parent table
2. require exactly one direct-parent FK binding candidate
3. use that binding as the single source of truth for:
   - parent owner-side collection join column
   - child scalar FK field if present
   - child read-only back-reference join column if generated

This rule applies equally to:

- aggregate root -> child
- child -> grandchild
- deeper owned parent-child chains

### No contract split between root-child and child-child

The pipeline must not treat root-child and child-child as different ownership families.

Any behavior difference between those two shapes is now considered a bug unless explicitly re-specified in a later spec.

## Rendered JPA Contract

### Parent side

For an owned parent-child binding, the parent-side surface remains the aggregate write-model primary surface:

- `@OneToMany(fetch = FetchType.LAZY, ...)`
- `@JoinColumn(name = "<direct_parent_fk>", nullable = false)`
- cascade/orphan-removal behavior stays on the parent-side owned collection line

### Child side

If child->parent object navigation is generated, it is always a read-only inverse navigation:

- `@ManyToOne(fetch = FetchType.LAZY)`
- `@JoinColumn(name = "<direct_parent_fk>", nullable = false, insertable = false, updatable = false)`

This child-side field is navigation-only. It does not participate in write ownership.

### Surface shape

The accepted surface is:

- parent -> children collection kept
- child -> parent object back-reference kept
- only the parent-side collection owns writes

This preserves aggregate usability without reintroducing dual ownership.

## Fetch Policy Contract

### Parent owned collection

Default fetch for owned `@OneToMany` is fixed to `LAZY`.

Reason:

- repository default behavior already uses `AggregateLoadPlan.WHOLE_AGGREGATE`
- `JpaAggregateLoadPlanSupport` is the approved mechanism that expands owned collections for whole-aggregate reads
- `AggregateLoadPlan.MINIMAL` must remain able to avoid forcing owned collections to load

Therefore "whole aggregate by default" is a repository/use-case policy, not a JPA annotation default policy.

### Child read-only back-reference

Default fetch for read-only child `@ManyToOne` back-reference is fixed to `LAZY`.

Reason:

- child->parent back-reference is navigation surface, not the aggregate loading authority
- making it eager would add constant loading cost while not helping the repository-level whole-aggregate contract

### No local override in this slice

Owned parent-child bindings do not accept local eager/lazy override in this slice.

That means:

- no new DSL knob for owned parent-child fetch policy
- no DB annotation override such as `@Lazy=true/false` on the direct-parent FK for this owned relation family

If such input is present on an owned parent-child direct-parent binding, the pipeline should fail fast instead of silently mixing policies.

Non-owned relation families remain outside this spec and should keep their previously bounded behavior.

## Fail-Fast Rules

The pipeline must fail before generation when any of the following is true:

1. a child table declares `@P=<parent_table>` but no direct-parent FK column references that parent table
2. multiple child columns reference the direct parent table and the pipeline cannot uniquely identify one binding
3. one parent-child binding would generate both:
   - parent owner-side `@OneToMany + @JoinColumn`
   - child owner-side `@ManyToOne + @JoinColumn`
4. a generated parent collection exists for the owned child, but the child side is also treated as an independent owner relation
5. a local fetch override is declared on an owned direct-parent binding
6. any other source shape prevents stable direct-parent binding resolution

This slice prefers bounded correctness over heuristic repair.

## Canonical and Planner Contract Changes

This slice does not introduce a second independent ownership model.

Instead, canonical assembly and planning must treat owned parent-child binding as one relation truth that can project into two rendered surfaces:

1. one owner-side parent collection relation
2. zero or one child read-only inverse navigation relation

Expected planner behavior:

- derive the parent collection from the direct-parent binding
- derive the child back-reference from the same binding
- always carry `insertable = false` and `updatable = false` on the child inverse side
- always carry `LAZY` fetch on both rendered sides for this owned family
- never let explicit direct-parent `@Ref` metadata reopen child-side owner rendering

This keeps the existing bounded inverse-relation line, but removes the remaining split-brain ownership behavior.

## Implementation Scope by Repository

### cap4k

Required work:

1. tighten direct-parent binding inference so `@P` and direct-parent `@Ref` resolve to one owned binding
2. unify `root-child` and `child-child` planning output
3. keep parent owner-side `ONE_TO_MANY` planning
4. keep child inverse `MANY_TO_ONE`, but force it to read-only and `LAZY`
5. reject owned direct-parent local fetch overrides
6. add focused canonical/planner/renderer tests for:
   - root-child owned binding
   - child-child owned binding
   - no direct-parent FK failure
   - multiple direct-parent FK ambiguity failure
   - duplicate-owner prevention

### only-danmuku-zero

Required work:

1. re-enable real generated aggregate entity scanning for the chosen verification slice after parent-child mapping is fixed
2. stop treating the probe-only smoke as sufficient evidence for audit alignment
3. add one real generated-entity audit verification path that survives application startup and JPA interaction

This should use a representative generated owned chain that previously failed due to duplicated owner mapping.

### only-engine

No new audit runtime contract is introduced here.

The requirement on `only-engine` is verification-facing:

- the already introduced audit module must now be proven against real generated entities, not only probe entities

## Verification Requirements

### cap4k verification

At minimum, verification should prove:

1. `root-child` and `child-child` now produce the same ownership contract
2. child inverse navigation is always read-only for owned parent-child bindings
3. owned parent-child fetch defaults are `LAZY` on both sides
4. ambiguous or missing direct-parent bindings fail before rendering
5. generated JPA output no longer contains dual owner mappings for the same FK column

### real generated-entity audit verification

Completion is not satisfied by probe smoke alone.

After the owner/fetch fix, verification must restore at least one real generated aggregate path and prove:

1. application startup succeeds with real generated entity scanning enabled for that path
2. insert fills:
   - `createTime`
   - `updateTime`
   - `createUserId` / `createBy` when provider data is available
3. update refreshes:
   - `updateTime`
   - `updateUserId` / `updateBy` when provider data is available
4. soft-delete, version, and managed-field behavior do not break runtime audit on that real generated entity set

The existing probe smoke may remain as a fast test, but it is not the completion gate for this slice.

## Resulting Mainline Boundary

After this spec, the parent-child inverse-navigation line is explicitly bounded as:

- parent-side owner collection for owned children
- child-side optional read-only back-reference
- `LAZY` mapping defaults on both sides
- repository/load-plan controlled whole-aggregate behavior
- fail-fast on direct-parent ambiguity or dual ownership
- completion gated by real generated-entity audit verification

This keeps aggregate write semantics clear, preserves the load-plan contract, and removes the current Hibernate duplicated-mapping failure mode without widening the relation line into a general JPA ownership framework.
