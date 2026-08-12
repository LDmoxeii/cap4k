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
- Update progress after spec, plan, PR, release, or downstream verification: read `workflows/update-issue-lifecycle.md`.
- Close a child or standalone issue: read `workflows/close-issue.md`.
- Audit and close a parent issue: read `workflows/close-parent-issue.md`.

## Gotchas

- Assign by repair code location, not discovery location.
- Do not split only because several directories change; split when slices are independently reviewable and mergeable.
- A child PR closes only its child or standalone issue. It references the parent without a closing keyword.
- A parent closes only after required children and composition evidence are accepted on one `origin/master` lineage.
- Use checklists, native sub-issues, and comments for progress. Do not introduce `state:*` labels.

## Boundaries

- This skill governs issue ownership, hierarchy, labels, lifecycle evidence, and closure.
- Canonical specs and plans remain repository design assets.
- Comet governs one change target and its acceptance loop; it is not a multi-PR live dependency graph.
- Repository-specific technical design remains in the target repository.