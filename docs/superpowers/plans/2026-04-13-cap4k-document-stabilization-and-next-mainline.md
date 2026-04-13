# Cap4k Document Stabilization and Next Mainline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the temporary handoff, stabilize `AGENTS.md` into a long-lived rule document, and add a durable roadmap document that preserves mainline and bootstrap decisions without relying on conversation context.

**Architecture:** This slice is documentation-only. `AGENTS.md` becomes the stable rule layer, `docs/superpowers/mainline-roadmap.md` becomes the stable progress and decision layer, and the old handoff file is deleted. No generator, renderer, source, or Gradle behavior should change.

**Tech Stack:** Markdown documentation, repository conventions, git verification commands

---

## File Structure

- Modify: `AGENTS.md`
  Responsibility: stable repository guidance, architectural boundaries, work classification, reading order, and execution rules

- Create: `docs/superpowers/mainline-roadmap.md`
  Responsibility: durable completed-slice summary, current next mainline slice, deferred tracks, and bootstrap contract decisions

- Delete: `docs/superpowers/2026-04-13-cap4k-mainline-handoff.md`
  Responsibility: remove the temporary handoff document once durable information is preserved elsewhere

- Verify only: `docs/superpowers/specs/2026-04-13-cap4k-document-stabilization-and-next-mainline-design.md`
  Responsibility: approved design source for this documentation cleanup slice

### Task 1: Stabilize `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`
- Test: `AGENTS.md`

- [x] **Step 1: Confirm the current `AGENTS.md` still depends on the temporary handoff and current-slice text**

Run:

```powershell
rg -n "Mainline handoff|handoff document|Current Next Mainline Target|design template migration / helper adoption" AGENTS.md
```

Expected: matches showing that `AGENTS.md` still points to `2026-04-13-cap4k-mainline-handoff.md` and still hard-codes `design template migration / helper adoption` as the current next step.

- [x] **Step 2: Replace `AGENTS.md` with the stable long-lived guidance content**

Replace the entire file with:

```md
# Cap4k Agent Guide

## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Mainline roadmap](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/mainline-roadmap.md)
- [Original architecture reset spec](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)

## What This Project Is Doing

`cap4k` is in a breaking redesign from the old mixed Gradle/codegen/plugin model to a fixed-stage pipeline.

The stable direction is:

- fixed pipeline stages owned by plugin developers
- repository-level source and generator configuration
- canonical model between sources and generators
- renderer helpers that stay thin and do not take type-resolution ownership back from Kotlin code

## Do Not Reopen These Boundaries

- Pipeline stage order is not customizable by project users.
- Project users can enable or disable sources and generators, but cannot inject custom runtime logic.
- Sibling design-entry type references are still unsupported.
- Short-name auto resolution must stay conservative.
- Symbol identity and explicit FQN remain the source of truth for imports.
- `use()` is design-template-only and must remain a thin explicit-import helper.
- Bootstrap or arch-template migration, when implemented, must remain a separate capability rather than widening design-template helper authority.

## Work Classification

There are three kinds of work in this repo now:

1. Mainline design-generator quality work
2. Real-project integration boundary work
3. Bootstrap or arch-template migration work

## Continuing Work

- If the user says "continue the original mainline", use the roadmap document and continue the current mainline slice from there.
- If the user says "unblock real project integration", read the relevant integration specs first. Do not silently turn an integration workaround into a new global framework rule.
- If the user says "work on bootstrap" or "work on arch-template migration", treat that as a separate slice. Do not silently mix it into design-template migration.

## Reading Order

1. [AGENTS.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/AGENTS.md)
2. [mainline-roadmap.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/mainline-roadmap.md)
3. [2026-04-09-cap4k-pipeline-redesign-design.md](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
4. the most recent relevant specs under `docs/superpowers/specs/`

## Notes

- `docs/superpowers/specs/` and `docs/superpowers/plans/` contain the historical slices
- the roadmap document records completed mainline slices, the next mainline slice, and durable bootstrap decisions
```

- [x] **Step 3: Verify `AGENTS.md` is stable and no longer depends on the handoff**

Run:

```powershell
rg -n "Mainline handoff|handoff document|Current Next Mainline Target|design template migration / helper adoption" AGENTS.md
rg -n "mainline-roadmap|Mainline roadmap|Bootstrap or arch-template migration work|continue the original mainline" AGENTS.md
```

Expected:
- first command returns no matches
- second command returns matches confirming the new roadmap reference, the third work classification, and the stable continuation rule

- [x] **Step 4: Commit the stabilized `AGENTS.md`**

```bash
git add AGENTS.md
git commit -m "docs: stabilize cap4k agent guide"
```

### Task 2: Add the Durable Roadmap and Delete the Temporary Handoff

**Files:**
- Create: `docs/superpowers/mainline-roadmap.md`
- Delete: `docs/superpowers/2026-04-13-cap4k-mainline-handoff.md`
- Test: `docs/superpowers/mainline-roadmap.md`

- [x] **Step 1: Verify the handoff exists and the new roadmap does not yet exist**

Run:

```powershell
if (Test-Path 'docs/superpowers/2026-04-13-cap4k-mainline-handoff.md') { 'HANDOFF_PRESENT' } else { 'HANDOFF_MISSING' }
if (Test-Path 'docs/superpowers/mainline-roadmap.md') { 'ROADMAP_PRESENT' } else { 'ROADMAP_MISSING' }
```

Expected:
- first line is `HANDOFF_PRESENT`
- second line is `ROADMAP_MISSING`

- [x] **Step 2: Create `docs/superpowers/mainline-roadmap.md` with durable mainline and bootstrap decisions**

Create the file with:

```md
# Cap4k Mainline Roadmap

Date: 2026-04-13

## Purpose

This document records durable redesign status and decisions for `cap4k`.

It replaces the temporary handoff style with a longer-lived repository record for:

- completed mainline slices
- the current next mainline slice
- work that must stay on separate tracks
- bootstrap contract decisions that should not be rediscovered from chat history

## Original Mainline

The original mainline is:

1. reset architecture to a fixed-stage pipeline
2. migrate major capability slices into that pipeline
3. improve design-generator output quality until old template migration becomes practical

## Completed Mainline Slices

### Phase A: Pipeline Architecture Reset

Completed:

- pipeline foundation
- db aggregate minimal
- ir flow
- drawing-board
- DSL consolidation

Status:

- complete

### Phase B: Design Generator Quality Mainline

Completed:

- minimal usable
- type/import resolution
- auto-import
- template helpers
- default value
- `use()` helper
- design template migration / helper adoption

Status:

- current mainline is complete through helper adoption and migration-contract stabilization

## Current Mainline Contract

These points remain in force:

- `use()` is design-template-only
- `use()` only accepts explicit FQN strings
- `use()` is only for explicit imports, not type resolution
- `imports()` remains the output path for import lines
- `defaultValue` in render models is Kotlin-ready, not raw source text
- short-name handling remains conservative
- explicit FQN and symbol identity remain the truth source

## Current Next Mainline Slice

The next mainline slice is:

- representative old design template / override migration

Recommended first group:

- `command.kt.peb`
- `query.kt.peb`
- `query_list.kt.peb`
- `query_page.kt.peb`

Scope:

- migrate a representative set of old design-template or override patterns into helper-first pipeline-compatible forms
- prove those migration patterns with fixtures or tests

Non-goals:

- do not reopen generator-core architecture
- do not add sibling design-entry type support
- do not widen `use()` beyond design templates
- do not mix bootstrap migration into this slice

## Separate Future Slice

The following is a separate future slice, not the current next mainline target:

- bootstrap / arch-template migration

## Bootstrap Decision

The bootstrap direction is:

- bootstrap should become a separate framework capability
- the public bootstrap contract should use slot-based extension
- users may add arbitrary numbers of files through bounded slots
- the public contract should not restore arbitrary insertion into any architecture-tree node
- the old `archTemplate` JSON remains migration input or reference material, not the future public runtime contract

## Non-Default Work

The following remain separate from the default mainline path:

- real-project integration boundary work
- project-specific unblock work
- broader bootstrap flexibility beyond slot-based extension

## Continue Rules

- If the user says only "continue", continue the original mainline.
- Do not default into an integration workaround.
- Do not silently merge bootstrap migration into design-template migration.
- Only promote project-specific patterns into framework contract when explicitly approved.
```

- [x] **Step 3: Delete the temporary handoff file**

Delete:

```text
docs/superpowers/2026-04-13-cap4k-mainline-handoff.md
```

- [x] **Step 4: Verify the roadmap exists, the handoff is gone, and the durable decisions are present**

Run:

```powershell
if (Test-Path 'docs/superpowers/2026-04-13-cap4k-mainline-handoff.md') { 'HANDOFF_PRESENT' } else { 'HANDOFF_MISSING' }
if (Test-Path 'docs/superpowers/mainline-roadmap.md') { 'ROADMAP_PRESENT' } else { 'ROADMAP_MISSING' }
rg -n "representative old design template / override migration|bootstrap / arch-template migration|slot-based extension|do not silently merge bootstrap migration" docs/superpowers/mainline-roadmap.md
```

Expected:
- first line is `HANDOFF_MISSING`
- second line is `ROADMAP_PRESENT`
- the `rg` command returns matches for the next mainline slice and bootstrap decisions

- [x] **Step 5: Commit the roadmap addition and handoff deletion**

```bash
git add docs/superpowers/mainline-roadmap.md docs/superpowers/2026-04-13-cap4k-mainline-handoff.md
git commit -m "docs: replace cap4k handoff with roadmap"
```

### Task 3: Run the Documentation Verification Sweep

**Files:**
- Test: `AGENTS.md`
- Test: `docs/superpowers/mainline-roadmap.md`
- Test: `docs/superpowers/2026-04-13-cap4k-mainline-handoff.md`

- [x] **Step 1: Verify `AGENTS.md` and the roadmap agree on the new stable direction**

Run:

```powershell
rg -n "Mainline roadmap|mainline-roadmap|Bootstrap or arch-template migration work|continue the original mainline" AGENTS.md
rg -n "Current Next Mainline Slice|representative old design template / override migration|bootstrap / arch-template migration|slot-based extension" docs/superpowers/mainline-roadmap.md
```

Expected: both commands return matches that align on the stable reading order, work classification, next mainline slice, and bootstrap separation.

- [x] **Step 2: Verify the temporary handoff is no longer part of the live guidance path**

Run:

```powershell
rg -n "2026-04-13-cap4k-mainline-handoff" AGENTS.md docs/superpowers/mainline-roadmap.md
```

Expected: no matches.

- [x] **Step 3: Verify repository state is clean and only the documentation commits landed**

Run:

```bash
git status --short
git log --oneline --decorate -3
```

Expected:
- `git status --short` shows a clean working tree
- `git log --oneline --decorate -3` shows the two documentation commits from Task 1 and Task 2 at the top of history

- [x] **Step 4: Capture the final diff summary for review**

Run:

```bash
git diff --stat HEAD~2..HEAD
```

Expected: the diff summary is limited to:
- `AGENTS.md`
- `docs/superpowers/mainline-roadmap.md`
- deletion of `docs/superpowers/2026-04-13-cap4k-mainline-handoff.md`
