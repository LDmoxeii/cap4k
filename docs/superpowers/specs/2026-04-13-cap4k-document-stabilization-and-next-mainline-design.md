# Cap4k Document Stabilization and Next Mainline Design

Date: 2026-04-13
Status: Implemented and merged

## Summary

`cap4k` no longer needs a temporary handoff document to carry the redesign forward.

The next step is to stabilize the repository guidance so future agents do not depend on conversation context or on a handoff file that keeps changing whenever the current slice changes.

This slice does three things:

1. remove the temporary handoff document
2. stabilize `AGENTS.md` into a long-lived rule and boundary document
3. introduce a longer-lived roadmap or decision document that records durable mainline judgments and names the next mainline slice

At the same time, this slice locks one important planning decision:

- `bootstrap / arch-template migration` is a separate future slice
- it should use a slot-based extension model
- it should not restore arbitrary tree insertion as the public framework contract

The next mainline slice after this documentation cleanup is:

- representative old design template / override migration

## Why This Slice

The just-completed mainline work finished:

- design template migration / helper adoption

That means the previous handoff document is now already stale in the most important place:

- its “next mainline step” is no longer the next step

If `AGENTS.md` continues to point to a changing handoff file for the current target, the repository guidance will keep drifting.

The right fix is not to keep writing new handoffs.

The right fix is to separate:

- stable repository rules
- stable recorded decisions
- current implementation slices

This reduces future guidance churn and makes mainline continuation depend on repository documents rather than chat reconstruction.

## Goals

- Delete the temporary handoff document
- Make `AGENTS.md` stable enough that it does not need frequent edits for each slice
- Preserve important redesign decisions in a durable repository document
- Explicitly separate:
  - mainline design-generator work
  - integration boundary work
  - bootstrap or arch-template migration work
- Record the next mainline slice after helper adoption
- Record the bootstrap decision that slot-based extension is acceptable and arbitrary tree insertion is not the public target

## Non-Goals

This slice will not:

- implement the next mainline slice
- implement bootstrap migration
- restore the old handoff document under a different name
- create a compatibility layer for old `archTemplate` JSON
- redefine pipeline architecture
- change generator, renderer, or source behavior

## Scope Decision

Three approaches were considered.

### Option 1: Stable rules plus stable roadmap

Keep `AGENTS.md` focused on long-lived rules and add a separate durable roadmap or decision document for mainline status and key judgments.

Pros:

- reduces `AGENTS.md` churn
- keeps durable decisions in-repo
- avoids handoff-style drift

Cons:

- adds one more document to maintain

### Option 2: Put everything into `AGENTS.md`

Delete the handoff and move all current status and future direction into `AGENTS.md`.

Pros:

- fewer files

Cons:

- `AGENTS.md` becomes unstable again
- every slice completion would require rewriting the same guidance file

### Option 3: Delete the handoff and rely only on specs and plans

Keep `AGENTS.md` minimal and do not create a replacement roadmap document.

Pros:

- smallest document surface

Cons:

- important cross-slice judgments become easy to lose
- future agents must reconstruct too much state from historical specs

### Recommendation

Implement Option 1.

`AGENTS.md` should carry long-lived rules.
The new roadmap or decision document should carry durable mainline state and direction.

## Stable Document Model

After this slice, repository guidance should be split as follows.

### `AGENTS.md`

Purpose:

- stable agent guidance
- stable architectural boundaries
- stable work classification rules
- stable reading order

`AGENTS.md` should not contain:

- the current “next target” for only one moment in time
- handoff-style progress narration
- per-slice completion details

### Roadmap or Decision Document

Purpose:

- durable redesign progress summary
- durable next mainline target
- durable postponed-track decisions
- durable bootstrap migration judgment

This document replaces the handoff role, but not in handoff style.

It should read like repository truth, not like chat continuation notes.

### Specs and Plans

Purpose:

- detailed design for each slice
- detailed execution plans for each slice

These remain the historical implementation record.

## `AGENTS.md` Target Shape

`AGENTS.md` should keep five stable sections.

### 1. Project Direction

Keep:

- fixed-stage pipeline direction
- repository-level source and generator configuration
- canonical model between sources and generators
- thin renderer helpers

### 2. Stable Architectural Boundaries

Keep:

- pipeline stage order is not user-customizable
- project users may enable and disable sources and generators but may not inject arbitrary runtime logic
- sibling design-entry type references remain unsupported
- short-name resolution remains conservative
- explicit FQN and symbol identity remain the source of truth
- `use()` is design-template-only and remains a thin explicit-import helper

Add:

- bootstrap or arch-template migration, when implemented, must remain a separate capability rather than widening design-template helper authority

### 3. Work Classification

The file should explicitly distinguish:

1. mainline design-generator quality work
2. real-project integration boundary work
3. bootstrap or arch-template migration work

This gives future agents a stable place to classify requests without relying on a changing handoff.

### 4. Reading Order

The reading order should point to:

1. `AGENTS.md`
2. the new roadmap or decision document
3. the architecture reset spec
4. the most recent relevant slice specs

It should no longer point to the deleted handoff file.

### 5. Execution Rules

`AGENTS.md` should state:

- when the user says continue the original mainline, use the roadmap document’s current mainline slice
- do not silently pivot into integration workaround work
- do not silently mix bootstrap migration into design-template migration
- do not promote project-specific workarounds into framework rules without explicit approval

## Handoff Deletion

The handoff document should be removed:

- `docs/superpowers/2026-04-13-cap4k-mainline-handoff.md`

Reason:

- it is intentionally temporary
- it hard-codes a “next step” that has already changed
- keeping it encourages future repeated rewrites instead of stable documentation

Its durable information should be folded into the new roadmap or decision document.

## New Roadmap or Decision Document

Add a durable document, for example:

- `docs/superpowers/mainline-roadmap.md`

or:

- `docs/superpowers/decision-log.md`

This design prefers `mainline-roadmap.md` because the immediate problem is mainline continuity.

## Roadmap Content

The roadmap should record:

### Completed Mainline Slices

- pipeline architecture reset
- major pipeline capability slices
- design generator quality slices through helper adoption
- design template migration / helper adoption

### Current Next Mainline Slice

- representative old design template / override migration

### Explicitly Separate Future Slice

- bootstrap / arch-template migration

### Bootstrap Decision

Record these decisions explicitly:

- bootstrap migration is not part of the current design-template migration slice
- bootstrap should become a separate framework capability
- the public bootstrap contract should use slot-based extension
- arbitrary insertion into any architecture-tree node is not the public target contract
- old `archTemplate` JSON remains migration input or reference material, not the future public runtime contract

### Deferred or Non-Default Work

Keep clearly separated:

- real-project integration boundary work
- project-specific unblock work
- experimental or broader bootstrap flexibility beyond slot-based extension

## Next Mainline Slice Definition

After this documentation cleanup, the next mainline slice should be:

- representative old design template / override migration

Target scope:

- choose a representative set of old design-template or override patterns
- migrate them into helper-first pipeline-compatible forms
- prove the migration patterns with fixtures or tests

Recommended first group:

- `command.kt.peb`
- `query.kt.peb`
- `query_list.kt.peb`
- `query_page.kt.peb`

Why this group:

- it is close to the current design-generator model
- it has high migration value
- it avoids immediately pulling in broader framework-import-heavy patterns such as handlers, validators, or clients

## Bootstrap Boundary Decision

This slice should also preserve a durable framework decision about bootstrap migration.

### What is acceptable

A future bootstrap capability may support:

- arbitrary numbers of extra user files
- bootstrap preset selection
- module enabling or disabling
- slot-based extension points for attaching extra files
- template override directories

### What is not the public target

The public contract should not restore:

- arbitrary insertion into any architecture-tree node
- unrestricted tree-shape mutation by project users
- the old architecture JSON as the future framework-owned runtime DSL

### Why

That level of freedom would effectively recreate the old architecture-tree DSL and blur the new framework boundaries again.

The stable direction is:

- framework-owned bootstrap tree
- user-extensible via bounded slots

not:

- user-owned architecture tree semantics

## Risks

### Risk 1: `AGENTS.md` remains unstable

If the file still names a concrete “current next target,” the same churn problem returns.

Mitigation:

- remove current-slice progress from `AGENTS.md`
- keep only stable boundaries and reading rules

### Risk 2: Important decisions disappear when the handoff is deleted

Mitigation:

- move durable decisions into the new roadmap document before deleting the handoff

### Risk 3: Bootstrap and design-template migration get merged conceptually

Mitigation:

- record bootstrap as a distinct future slice
- record the slot-based-extension decision explicitly

## Acceptance Criteria

This slice is complete when:

- the temporary handoff document is deleted
- `AGENTS.md` no longer depends on handoff-style next-step text
- `AGENTS.md` clearly separates mainline, integration, and bootstrap work
- a durable roadmap or decision document records completed slices and the next mainline slice
- the roadmap records bootstrap as a separate future slice
- the roadmap records slot-based bootstrap extension as the intended public direction

## Expected Outcome

After this slice:

- repository guidance no longer depends on a temporary handoff file
- `AGENTS.md` becomes stable enough to avoid repeated slice-by-slice rewrites
- future agents can recover mainline direction from repository documents alone
- the next mainline slice is clearly defined as representative old design template or override migration
- bootstrap migration remains possible, but on a separate and better-bounded contract path
