---
name: cap4k-generation
description: >
  Use when generating or regenerating cap4k business-project code,
  bootstrapping a cap4k project, generating from database schema or design JSON,
  reviewing generation ownership, or configuring template overrides and addons.
---

# Cap4k Generation

Use this before writing or regenerating cap4k generated output.

## Always Read

1. `rules/input-contracts.md`
2. `rules/output-ownership.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Bootstrap a project | `rules/template-and-addon-boundary.md` | `workflows/bootstrap-project.md` |
| Generate from DB | `references/gotchas.md` | `workflows/generate-from-db.md` |
| Generate from design JSON | `references/gotchas.md` | `workflows/generate-from-design.md` |
| Inspect ownership before writing | `rules/output-ownership.md` | `workflows/review-plan-json.md` |

## Stop Conditions

Stop before generation when `plan.json` ownership is unclear, a target conflicts with handwritten code, or a requested design tag is unsupported.
