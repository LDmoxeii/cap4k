---
name: cap4k-authoring
description: >
  Use this for AI authors implementing business projects using cap4k, including
  requests such as "build a cap4k project", "derive cap4k DDD design", "generate
  cap4k code from DB or design", "implement a cap4k project slice", "review cap4k
  generated output", or "run cap4k analysis". Activate when the task is business
  project authoring with cap4k, not cap4k framework maintenance.
---

# Cap4k Business Project Authoring

This skill is for AI authors implementing business projects using cap4k. It helps turn agreed business intent, DDL, design JSON, and generated skeletons into runnable project code with evidence for human audit.

## Boundaries

- Use this for business-project modeling, generation, implementation, review, testing, and analysis.
- Do not use it to govern cap4k framework issue lifecycle or maintain cap4k runtime/generator internals unless the user explicitly changes scope.
- Human users own domain decisions and final audit; AI authors assist, implement, verify, and report evidence.

## Always Read

1. [rules/role-boundary.md](rules/role-boundary.md)
2. [rules/layering-and-tactical-model.md](rules/layering-and-tactical-model.md)
3. [rules/generator-ownership.md](rules/generator-ownership.md)

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Clarify domain/design before code | `rules/role-boundary.md`, `references/capability-index.md`, `references/known-gaps.md` | `workflows/clarify-domain-design.md` |
| Bootstrap a minimal project | `rules/generator-ownership.md`, `rules/testing-and-verification.md` | `workflows/bootstrap-minimal-project.md` |
| Generate from DB | `rules/generator-ownership.md`, `references/gotchas.md` | `workflows/generate-from-db.md` |
| Generate from design | `rules/generator-ownership.md`, `references/known-gaps.md` | `workflows/generate-from-design.md` |
| Implement a project slice | `rules/layering-and-tactical-model.md`, `rules/runtime-tactical-contract.md`, `rules/testing-and-verification.md` | `workflows/implement-project-slice.md` |
| Review generated output | `rules/generator-ownership.md`, `rules/testing-and-verification.md`, `references/gotchas.md` | `workflows/review-generated-output.md` |
| Run analysis and flow review | `rules/testing-and-verification.md`, `references/capability-index.md` | `workflows/run-analysis-and-flow-review.md` |

## Priority

1. Current user instruction and project scope.
2. Agreed business model, DDL, design JSON, spec, or plan.
3. This skill's rules and workflows.
4. Existing project conventions.

When cap4k does not support a requested authoring capability, call it a gap and offer a local project choice instead of implying framework support.
