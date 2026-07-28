---
name: issue-governance
description: Use this skill when the user asks to "create an issue", "move backlog to GitHub", "triage roadmap items", "decide which repo an issue belongs to", "update issue status", or "close an issue". Activate for cross-repository issue governance across cap4k, only-engine, and only-danmuku-zero.
---

# Issue Governance

Shared workspace rules for backlog triage and GitHub issue lifecycle.

## Always Read

- [rules/repository-ownership.md](rules/repository-ownership.md)
- [rules/title-label-priority.md](rules/title-label-priority.md)
- [rules/lifecycle-policy.md](rules/lifecycle-policy.md)

## Common Tasks

- Create a new issue:
  Read `rules/repository-ownership.md`, `rules/title-label-priority.md`, `workflows/create-issue.md`
- Triage roadmap or backlog items into issues:
  Read `rules/repository-ownership.md`, `rules/title-label-priority.md`, `workflows/triage-backlog.md`
- Update an issue after spec, plan, implementation, release, or downstream verification:
  Read `rules/lifecycle-policy.md`, `workflows/update-issue-lifecycle.md`
- Decide whether an issue can be closed:
  Read `rules/lifecycle-policy.md`, `workflows/close-issue.md`

## Gotchas

- A dogfood-discovered issue does not automatically belong in `only-danmuku-zero`; assign by repair code location.
- Do not close an upstream issue just because code merged if release or downstream verification is still pending.
- Use checklist and comments for progress; do not introduce `state:*` labels.

## Boundaries

- This skill governs issue ownership, labels, lifecycle updates, and closure.
- Spec and plan documents still live in repository docs.
- Repository-specific technical design still belongs in the target repository.
