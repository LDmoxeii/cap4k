# Cap4k Framework Capability Audit And Positioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finalize the `#15` framework capability audit as a merged documentation/governance slice by aligning durable repo guidance, creating the explicit wrapper-removal follow-up issue, and closing the audit issue with stable references.

**Architecture:** Treat `#15` as a documentation and issue-governance implementation, not a runtime refactor. Keep code modules untouched; implement the audit outcome through one durable repository guidance update (`AGENTS.md`), one explicit follow-up issue for wrapper removal, and one lifecycle close-out on `cap4k#15` that links the spec, plan, and follow-up issue graph.

**Tech Stack:** Markdown, Git, GitHub issues, workspace issue-governance rules, repository guidance docs.

---

### Task 1: Align durable repository guidance with the audit baseline

**Files:**
- Modify: `AGENTS.md`
- Test: `AGENTS.md` content verification with `rg`

- [ ] **Step 1: Capture the current guidance drift**

Run:

```powershell
rg -n "mainline-roadmap|transaction policy must be request-family-specific|TypeScript frontend generation is only a candidate track" AGENTS.md
```

Expected:

- at least one stale roadmap reference or stale capability-positioning sentence is still present
- output proves `AGENTS.md` still needs to be aligned with the post-roadmap, post-`#14`, post-`#15` audit baseline

- [ ] **Step 2: Update `AGENTS.md` to match the current backlog and capability-positioning reality**

Apply this content shape:

```markdown
## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Original architecture reset spec](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- the relevant GitHub issue that now acts as backlog source of truth
- the most recent relevant spec or plan under `docs/superpowers/`
```

```markdown
## Current Planning State

GitHub issues are now the backlog source of truth. Repository docs remain design assets:

- issues track backlog, state, and closure
- specs and plans track design and implementation detail
- before starting implementation, re-read the target issue plus the newest relevant spec/plan against current `master`
```

```markdown
Recent durable decisions to preserve:

- old request-family transaction-scope concerns came from JPA lazy-loading failures around unit-of-work save boundaries; those failures were mitigated through object-graph expansion, so transaction-boundary widening is not an active direction without fresh evidence
- frontend TypeScript generation is currently not planned as a cap4k core slice unless a first-class endpoint tactical model or stable API-contract projection exists
```

```markdown
## Reading Order

1. [AGENTS.md](AGENTS.md)
2. the relevant GitHub issue
3. [2026-04-09-cap4k-pipeline-redesign-design.md](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
4. the most recent relevant specs/plans under `docs/superpowers/`
```

Also remove any remaining wording that still treats retired roadmap/backlog files as active sources of truth.

- [ ] **Step 3: Verify the guidance now reflects the audit baseline**

Run:

```powershell
rg -n "mainline-roadmap" AGENTS.md
rg -n "GitHub issues are now the backlog source of truth|frontend TypeScript generation is currently not planned as a cap4k core slice|transaction-boundary widening is not an active direction" AGENTS.md
```

Expected:

- the first command returns no matches
- the second command returns the updated guidance lines

- [ ] **Step 4: Commit the durable guidance update**

Run:

```bash
git add AGENTS.md
git commit -m "docs: align agent guide with capability audit baseline"
```

Expected:

- one docs-only commit exists for `AGENTS.md`
- no runtime module files are part of this commit

### Task 2: Create the explicit wrapper-removal follow-up issue and link the remaining advanced-concept teaching work

**Files:**
- No local files
- Remote: `cap4k` GitHub issues

- [ ] **Step 1: Create the wrapper-removal issue in `cap4k`**

Create a new GitHub issue in `LDmoxeii/cap4k` with:

- title: `framework: remove wrapper from core pipeline and docs`
- labels:
  - `type:cleanup`
  - `area:framework`
  - `priority:p1`

Use this body:

```markdown
## Background

`cap4k#15` concluded that `Wrapper` is the current explicit remove/deprecate target. The concept is still heavily implemented across planner, renderer, DSL, legacy codegen, tests, and docs, so removal requires a dedicated cleanup slice rather than implicit drift.

## Current Problem

Wrapper still exists as an actively implemented capability surface even though the framework capability audit no longer accepts it as a stable core tactical concept.

## Expected Result

Remove wrapper from core pipeline and documentation surfaces, or reduce it to a clearly deprecated compatibility boundary until final deletion is complete.

## Acceptance Criteria

- wrapper is no longer treated as a core framework concept
- planner / renderer / DSL / legacy references are either removed or explicitly deprecated behind a bounded transition
- tests and docs no longer present wrapper as a recommended capability

## Lifecycle Checklist

- [ ] spec written
- [ ] plan written
- [ ] implementation merged
```

Expected:

- GitHub returns a new `cap4k` issue URL
- the returned issue becomes the explicit implementation track for wrapper removal

- [ ] **Step 2: Add a linking comment to `cap4k#15`**

Post a comment to `cap4k#15` with this content shape:

```markdown
Follow-up issue graph after the capability audit:

- wrapper removal implementation track: <new wrapper-removal issue URL>
- advanced-concept teaching/examples track: https://github.com/LDmoxeii/cap4k/issues/21

This keeps the audit issue focused on capability classification and public positioning, while moving concrete wrapper cleanup and concept-teaching work onto their own slices.
```

Expected:

- `#15` now links both:
  - the new wrapper-removal issue
  - the already-open examples issue `#21`

- [ ] **Step 3: Verify the issue graph**

Check:

```text
- cap4k#15 contains the new follow-up comment
- the wrapper-removal issue exists with the expected title and lifecycle checklist
- cap4k#21 remains the teaching/examples follow-up for Domain Service / Value Object / Saga
```

Expected:

- the audit issue no longer leaves wrapper removal implicit
- advanced-concept education is clearly separated from wrapper cleanup

### Task 3: Finalize `#15` lifecycle after the audit slice is fully merged

**Files:**
- No local files
- Remote: `cap4k#15`

- [ ] **Step 1: Update `#15` checklist after the guidance commit is on `master`**

Update the issue body checklist so it becomes:

```markdown
## Lifecycle Checklist

- [x] spec written
- [x] plan written
- [x] implementation merged
```

Use the spec and plan paths as stable references:

- `docs/superpowers/specs/2026-05-04-cap4k-framework-capability-audit-and-positioning-design.md`
- `docs/superpowers/plans/2026-05-05-cap4k-framework-capability-audit-and-positioning.md`

Expected:

- `#15` body reflects that the audit slice itself is fully documented and merged

- [ ] **Step 2: Add the final implementation comment to `#15`**

Post a comment with this shape:

```markdown
Audit implementation merged:

- spec: `docs/superpowers/specs/2026-05-04-cap4k-framework-capability-audit-and-positioning-design.md`
- plan: `docs/superpowers/plans/2026-05-05-cap4k-framework-capability-audit-and-positioning.md`
- durable repo-guidance commit: <Task 1 commit hash>
- wrapper-removal follow-up: <wrapper-removal issue URL>
- advanced-concept teaching/examples follow-up: https://github.com/LDmoxeii/cap4k/issues/21

No runtime cleanup is bundled into this issue. Runtime cleanup, wrapper removal, README rewrite, and guide writing continue in their own slices.
```

Expected:

- the closing state is explicit
- no future worker has to infer what `#15` did or did not implement

- [ ] **Step 3: Close `cap4k#15`**

Close `cap4k#15` as completed.

Expected:

- the issue closes without leaving wrapper removal or advanced-concept teaching as hidden residual work
- downstream issues `#16`, `#17`, `#21`, and the new wrapper-removal issue remain open as the next explicit tracks

- [ ] **Step 4: Verify the repository and issue state**

Run:

```powershell
git status --short
git log -1 --oneline
```

Check:

```text
- working tree is clean or only contains unrelated user-held changes
- latest merged docs commit is the AGENTS alignment commit from Task 1
- cap4k#15 is closed
- spec, plan, and follow-up issues are all linked from the issue history
```

Expected:

- `#15` is fully closed as a documentation/governance slice
- no hidden implementation obligation remains inside the audit issue itself
