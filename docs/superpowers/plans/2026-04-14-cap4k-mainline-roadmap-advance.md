# Cap4k Mainline Roadmap Advance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the durable `mainline-roadmap.md` so it records the completed representative-migration and query-handler slices and advances the next default mainline target to `client / client_handler`.

**Architecture:** This is a narrow documentation-only update. Modify only the roadmap record so it matches merged repository reality, keep support-track references intact, and avoid mixing roadmap maintenance with any implementation work on the next slice.

**Tech Stack:** Markdown, git

---

## File Structure

- Modify: `docs/superpowers/mainline-roadmap.md`
  Responsibility: record completed mainline slices accurately and advance the current next mainline slice

### Task 1: Advance the Durable Mainline Roadmap

**Files:**
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Update the completed Phase B slice list**

Edit `docs/superpowers/mainline-roadmap.md` so the Phase B completed list becomes:

```md
Completed:

- minimal usable
- type/import resolution
- auto-import
- template helpers
- default value
- `use()` helper
- design template migration / helper adoption
- representative old design template / override migration
- design query-handler family migration
```

- [ ] **Step 2: Update the Phase B status and traceability**

Replace the Phase B status sentence with:

```md
Status:

- current mainline is complete through representative migration and query-handler family migration
```

Append these two traceability bullets after the existing helper-adoption link:

```md
- [representative design template / override migration design](specs/2026-04-14-cap4k-representative-design-template-override-migration-design.md)
- [design query-handler family migration design](specs/2026-04-14-cap4k-design-query-handler-family-migration-design.md)
```

- [ ] **Step 3: Replace the current next mainline slice section**

Replace the entire `## Current Next Mainline Slice` section content with:

```md
## Current Next Mainline Slice

The next mainline slice is:

- design client / client_handler family migration

Scope:

- continue representative old design-family migration on the helper-first pipeline contract
- migrate old `client` and `client_handler` template families into bounded pipeline-owned template ids
- prove the migrated family with fixtures or tests

Non-goals:

- do not reopen generator-core architecture
- do not add sibling design-entry type support
- do not widen `use()` beyond design templates
- do not mix bootstrap migration into this slice
- do not turn support-track real-project findings into default framework rules without explicit approval
```

- [ ] **Step 4: Run a focused self-check on the roadmap file**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
Get-Content -Raw .\docs\superpowers\mainline-roadmap.md
git diff -- .\docs\superpowers\mainline-roadmap.md
```

Expected:

- the completed Phase B list includes both newly landed slices
- the new next-slice section points to `design client / client_handler family migration`
- support-track and bootstrap sections are unchanged outside incidental line reflow

- [ ] **Step 5: Commit the roadmap update**

Run:

```bash
git add docs/superpowers/mainline-roadmap.md
git commit -m "docs: advance mainline roadmap"
```

Expected: one documentation-only commit containing the roadmap update.
