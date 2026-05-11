---
name: cap4k-authoring
description: >
  Use this when working on cap4k-based project authoring, AI-assisted DDD implementation,
  generated-vs-handwritten ownership, cap4k tactical modeling, or requests such as
  "build a cap4k project", "write cap4k application/domain code", "review cap4k generated output",
  "apply the cap4k testing contract", or "update cap4k issue evidence". Activate when the task
  involves cap4k project code, docs, specs, plans, generator output, or final verification before
  human audit.
---

# Cap4k Authoring

Use this skill to help an AI author implement and review cap4k-based project work from self-contained repo-local rules.

## Structure Rationale

This uses the full skill structure because cap4k authoring has routed design, implementation, generated-output review, closure workflows, and a gotcha log.

## When To Use

- Building or changing a cap4k project slice
- Deciding where command, query, cli, domain event, integration event, factory, repository, or domain service code belongs
- Reviewing generated output, template overrides, conflict policy, or `src-generated` snapshots
- Preparing final evidence before human review
- Updating issue status after spec, plan, implementation, or verification

## When Not To Use

- Generic Kotlin, Spring, or DDD explanations not tied to cap4k
- Framework runtime implementation inside cap4k itself unless the issue explicitly targets project-authoring rules
- Pure frontend work with no cap4k boundary

## Always Read

1. [rules/role-boundary.md](rules/role-boundary.md)
2. [rules/layering-and-tactical-model.md](rules/layering-and-tactical-model.md)
3. [rules/generator-ownership.md](rules/generator-ownership.md)

## Common Tasks

| Task | Read | Workflow |
|---|---|---|
| Clarify a requested cap4k change before code | `rules/role-boundary.md`, `references/public-tactical-model.md`, `references/known-gaps.md` | `workflows/design-before-code.md` |
| Implement a cap4k project slice | `rules/layering-and-tactical-model.md`, `rules/generator-ownership.md`, `rules/verification-contract.md`, `references/gotchas.md` | `workflows/implement-cap4k-project-slice.md` |
| Review generated output or template overrides | `rules/generator-ownership.md`, `references/gotchas.md` | `workflows/review-generated-output.md` |
| Finish work before human audit | `rules/verification-contract.md`, `references/issue-lifecycle.md`, `references/known-gaps.md` | `workflows/close-task-with-evidence.md` |

## Priority

1. Current user instruction
2. Active GitHub issue and latest approved spec/plan
3. This skill's rules
4. Existing repository patterns

When a framework capability is missing, record the gap instead of implying support.

## Session Discipline

- Re-read this `SKILL.md` when a new distinct cap4k authoring task starts.
- Re-read this `SKILL.md` after `/clear`, `/compact`, context summarization, or a long interruption.
- Route each task through the Common Tasks table instead of relying on memory from a previous task.
