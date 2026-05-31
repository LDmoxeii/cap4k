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

1. `../shared/rules/core-positioning.md`
2. `../shared/rules/ownership-and-generation-flow.md`
3. `rules/input-contracts.md`
4. `rules/output-ownership.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Bootstrap a project | `rules/template-and-addon-boundary.md` | `workflows/bootstrap-project.md` |
| Bootstrap framework database tables | `references/framework-database-scripts.md` | `workflows/bootstrap-project.md` |
| Generate aggregate skeletons from DB | `references/sources/db-schema.md`, `references/sources/source-map.md` | `workflows/generate-from-db.md` |
| Generate use-case or interface surfaces from design JSON | `references/sources/design-json.md`, `references/sources/source-map.md` | `workflows/generate-from-design.md` |
| Resolve enum or custom type input contract | `references/sources/enum-manifest.md`, `references/sources/value-object-manifest.md`, `references/sources/types-registry.md`, `references/sources/source-map.md` | `workflows/generate-from-db.md` |
| Check analysis or addon confusion | `references/sources/analysis.md`, `references/sources/addons.md`, `references/sources/source-map.md` | `workflows/review-plan-json.md` |
| Inspect ownership before writing | `rules/output-ownership.md`, `references/sources/source-map.md` | `workflows/review-plan-json.md` |

## Stop Conditions

Stop before generation when `plan.json` ownership is unclear, a target conflicts with handwritten code, or a requested design tag is unsupported.
