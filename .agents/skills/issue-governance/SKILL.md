---
name: issue-governance
description: Use this skill when the user asks to create, split, link, update, audit, or close GitHub issues, including parent issues, sub-issues, implementation slices, and cross-repository backlog governance across cap4k, only-engine, and only-danmuku-zero.
---

# Issue Governance

Shared workspace rules for issue ownership, hierarchy, evidence, and lifecycle.

## Always Read

- [rules/repository-ownership.md](rules/repository-ownership.md)
- [rules/issue-hierarchy.md](rules/issue-hierarchy.md)
- [rules/lifecycle-policy.md](rules/lifecycle-policy.md)

## Common Tasks

- Create a standalone issue: read `rules/title-label-priority.md` and `workflows/create-issue.md`.
- Create a design parent and implementation slices: read `rules/title-label-priority.md` and `workflows/create-parent-and-children.md`.
- Triage roadmap or backlog items: read `rules/title-label-priority.md` and `workflows/triage-backlog.md`.
- Update progress after a Comet change, decision record, PR, release, or downstream verification: read `workflows/update-issue-lifecycle.md`.
- Close a child or standalone issue: read `workflows/close-issue.md`.
- Audit and close a parent issue: read `workflows/close-parent-issue.md`.

## Gotchas

- Do not create an Issue merely because a Comet change is starting. Immediate, authorized, coherent work may start directly as a change.
- Assign by repair code location, not discovery location.
- Do not split only because several directories change; split when slices are independently reviewable and mergeable.
- When an Issue exists, one Child/Standalone Issue normally maps to one Comet change. A Parent normally composes multiple changes.
- Keep the Issue macro-level. Comet Shape owns detailed behavior, scenarios, acceptance checks, and change evidence.
- A child PR closes only its child or standalone issue. It references the parent without a closing keyword.
- A parent closes only after required children and composition evidence are accepted on one `origin/master` lineage.
- Use checklists, native sub-issues, and comments for progress. Do not introduce `state:*` labels.

## Boundaries

- This skill governs issue ownership, hierarchy, labels, lifecycle evidence, and closure.
- Issues preserve deferred, prioritized, blocked, cross-repository, release, downstream, and multi-change work; they are not mandatory predecessors for every change.
- Active Comet change specs describe one authorized target. Canonical Comet specs describe the accepted authoritative contract after Archive and may precede implementation; neither is backlog storage or proof of current code support.
- Comet governs one change target and its acceptance loop; it is not a multi-PR live dependency graph.
- Repository-specific technical design remains in the target repository.