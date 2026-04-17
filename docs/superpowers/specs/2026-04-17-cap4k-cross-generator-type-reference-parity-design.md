# Cap4k Cross-Generator Type-Reference Parity

Date: 2026-04-17
Status: Draft for review

## Summary

The design-generator quality line is complete, and the bounded bootstrap line is complete through generated-project verification hardening.

The next explicit framework slice should be:

- `cross-generator type-reference parity`

This slice does not try to finish old-codegen parity in one step.

It narrows the problem to one boundary:

- how the pipeline should represent type references that old codegen previously obtained from mutable shared `typeMapping` state

The first slice should start by auditing old `typeMapping` usage, classifying those usages into stable categories, and only introducing a new mechanism where direct derivation is impossible.

The preferred outcome is:

- explicit FQN stays explicit
- project registry stays project registry
- convention-owned generated type names become deterministic derived references
- mutable shared runtime maps do not return

## Goals

- Audit the remaining old-codegen `typeMapping` use sites that still matter for explicit parity work.
- Classify those use sites into:
  - already covered by current pipeline behavior
  - directly derivable from canonical data and stable naming rules
  - not appropriate for this slice because they still depend on old shared runtime state
- Define the smallest framework-owned derived type-reference mechanism needed for deterministic cases.
- Keep the solution aligned with immutable canonical data, explicit symbol identity, and conservative name resolution.
- Give future aggregate-side parity work a stable boundary for type-reference decisions without reopening generator execution order coupling.

## Non-Goals

- Do not restore a mutable shared `typeMapping` map between generators.
- Do not turn this slice into aggregate-side parity completion.
- Do not reopen relation parity, JPA annotation parity, or user-code-preservation parity.
- Do not widen bootstrap or change bootstrap contracts.
- Do not move type-resolution responsibility into renderer helpers or Pebble templates.
- Do not require a two-pass pipeline.
- Do not force the assembler to precompute every future generator output by default.

## Current Context

The old codegen system uses `typeMapping` in two very different ways:

1. as a short-name-to-FQN lookup table
2. as a mutable runtime registry that later generators read after earlier generators have registered newly generated types

The new pipeline already has partial answers for the first category:

- `ProjectConfig.typeRegistry`
- `DesignSymbolRegistry`
- conservative explicit-FQN and short-name handling in the design generator

That means the first parity slice should not assume the whole problem is still open.

Instead, it should separate:

- what the pipeline already covers
- what can be derived deterministically
- what still depends on old mutable execution-order state

## Why This Slice Exists

The current exploratory parity notes correctly identify `typeMapping` as a remaining full-replacement gap.

But if the repository jumps directly from that observation to a new global registry layer, it will undo several accepted boundaries:

- immutable configuration and canonical data
- fixed-stage pipeline ownership
- renderer helpers staying thin
- generator outputs not depending on hidden runtime side effects

So the first active slice should solve the boundary problem before it solves every usage site.

## Old Usage Categories

The old `typeMapping` usage points fall into four categories.

### 1. Explicit FQN Cases

Some references are already explicit or can be made explicit without any new framework mechanism.

These are not true parity gaps.

The pipeline should continue to treat them as:

- explicit FQN source of truth
- helper-driven import rendering

### 2. Project Registry Cases

Some old `typeMapping` lookups are really just:

- short type name -> known project FQN

The new pipeline already has a bounded answer for this:

- `ProjectConfig.typeRegistry`
- `DesignSymbolRegistry`

These cases should be classified as:

- already covered by current pipeline contract

not as justification for a new global runtime map.

### 3. Convention-Derived Cases

Some old lookups are not user-defined mappings at all.

They are deterministic naming conventions such as:

- entity -> `QEntity`
- aggregate/entity -> framework-owned companion generated type
- design request -> handler class name derived from request type name

These cases should be handled by:

- deterministic derivation from canonical data and framework naming rules

not by mutable shared state.

### 4. Shared Runtime-State Cases

Some old `typeMapping` usage points only work because earlier generators register new names into a shared mutable map and later generators read them back.

These are the most dangerous cases.

They should not be accepted automatically.

For this first slice they must be classified as:

- deferred unless they can be restated as explicit FQN, project-registry lookup, or deterministic derivation

## Design Decision

The first active slice should use:

- audit-first classification
- current-registry reuse where already sufficient
- minimal deterministic derived references for convention-owned cases

It should not use:

- a restored global `typeMapping`
- a hidden mutable registry shared across generator execution

This keeps the parity work compatible with the current pipeline architecture instead of treating parity pressure as an excuse to reintroduce old runtime coupling.

## Active Slice Scope

This first slice should cover:

- old-codegen `typeMapping` usage inventory and categorization
- current pipeline design-side and directly adjacent type-reference behavior
- the placement and contract of a minimal derived type-reference mechanism
- the first deterministic rule set only where the reference can be proven from canonical data and framework naming conventions

This first slice should not cover:

- aggregate-side parity completion as a whole
- every old aggregate generator that once wrote into `typeMapping`
- a generalized future-type registry for all generators

## Placement Decision

If this slice introduces any new mechanism, it should live near:

- planner-owned or render-model-owned type-reference construction

It should not live in:

- mutable runtime context
- renderer helper state
- templates
- a global assembler-owned future-output map by default

That means the preferred shape is:

- generator-local
- deterministic
- convention-based
- read-only once constructed

## First-Slice Reference Resolution Order

This slice should lock in the following decision order:

1. explicit FQN wins
2. project registry fallback comes next
3. deterministic convention-based derivation comes after that
4. only then evaluate whether a minimal derived reference layer is still needed

If a case reaches step 4 and still requires mutable execution-order state, it is out of scope for this slice.

## Minimal Derived Mechanism

If a new mechanism is needed, it should be a small framework-owned derived layer, not a general-purpose registry.

Recommended shape:

- a generator-owned or planner-owned derived reference view
- built from:
  - canonical model data
  - project type registry
  - framework naming conventions
- consumed as read-only data during planner or render-model construction

The first slice should prefer direct functions over a new global model.

Example direction:

- `deriveQueryDslType(entity)` rather than `globalTypeMap["Q${entity.name}"]`

Only if repeated deterministic patterns are proven across multiple consumers should this be lifted into a small reusable structure such as:

- `DerivedTypeReferences`

Even then, it must remain:

- local to one generator family or one planning pass
- immutable once built
- independent of generator execution order

## First-Slice Target Cases

The first slice should not attempt to implement every old aggregate-side use site.

It should start with the cases that are closest to the current pipeline and easiest to classify:

1. design-side cases already handled by current registry or explicit identity rules
2. deterministic generated-type references whose names are fixed by framework convention
3. old-codegen usage points that can be formally marked as deferred because they still depend on shared mutable registration

This means the slice is allowed to conclude that some current cases need:

- no code change

if the audit proves they are already covered by:

- explicit FQN handling
- project type registry
- local direct derivation

That is a valid slice outcome.

The point is to settle the contract boundary, not to force a new abstraction where one is not justified.

## Relationship To Existing Type Registry Work

This slice must not reopen the already accepted project type registry decision.

The current registry remains:

- project-scoped
- JSON-backed
- fallback-only
- not a sibling design-entry reuse mechanism

This parity slice should build on that decision, not replace it.

So any new derived mechanism must remain distinct from:

- user-authored type registry entries

Registry entries are project-specified names.
Derived references are framework-owned convention outputs.

## Relationship To Aggregate-Side Parity

This slice exists partly because aggregate-side parity will eventually need a stable answer for generated companion references such as:

- `Q<Entity>`
- schema or factory related references
- convention-owned generated peers

But the first slice should not directly absorb:

- factory parity
- specification parity
- repository parity
- unique-query parity
- wrapper parity

Instead, it should define the rule that later slices must follow:

- derive deterministically when possible
- never depend on mutable shared runtime registration by default

## Validation Strategy

This slice should validate at three levels.

### 1. Old Usage Audit

Add or update characterization coverage that makes old `typeMapping` use categories explicit.

The goal is not to freeze old implementation details forever.
The goal is to record which old usage sites belong to which category:

- explicit
- registry-backed
- convention-derived
- deferred shared-state

### 2. Pipeline Unit Coverage

If a minimal derived mechanism is introduced, unit tests should prove:

- explicit FQN remains highest priority
- project registry remains fallback-only
- deterministic derived references do not require runtime mutation
- ambiguous or non-deterministic cases still fail clearly

### 3. No-Boundary-Regression Checks

The slice should confirm that:

- renderer helpers do not gain new type-resolution authority
- short-name handling remains conservative
- project registry semantics do not widen into sibling design-entry reuse

## Success Criteria

This slice is successful when:

- old `typeMapping` usage points are classified into durable categories
- the repository has a written and testable resolution order for cross-generator type references
- current pipeline coverage is clearly separated from true remaining parity gaps
- any new mechanism introduced is deterministic, local, and immutable
- no mutable shared generator runtime map is reintroduced
- future aggregate-side parity slices have a stable type-reference boundary to build on

## Why This Is The Right Next Slice

This slice is a better next step than immediately reopening aggregate-side parity because it settles a cross-cutting rule first.

Without this boundary, later parity work will tend to fall back to the easiest old pattern:

- register generated names globally
- read them later from shared mutable state

That would be a regression in architecture, even if it made parity look faster in the short term.

By solving the type-reference boundary now, later aggregate-side work can proceed on cleaner terms.

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. auditing and classifying old `typeMapping` usage points
2. proving which current pipeline cases are already covered
3. introducing only the smallest derived type-reference support needed for deterministic cases
4. adding characterization and unit coverage that locks the boundary in place

That keeps the slice narrow enough to implement while still making real progress on the remaining parity line.
