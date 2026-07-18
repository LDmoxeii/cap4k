---
name: cap4k-generation-review
description: >
  Use to review cap4k plan evidence, generated output ownership, conflict
  policies, template IDs, addon artifacts, generation drift, and the stop point
  before handwritten implementation.
---

# Cap4k Generation Review

Use this after coherent generator inputs exist and before handwritten implementation.

## Always Read

1. `rules/generation-stop-policy.md`
2. `../shared/references/output-ownership-taxonomy.md`
3. `workflows/review-plan-and-generate.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Review plan evidence before generation | `references/plan-review-gotchas.md` | `workflows/review-plan-and-generate.md` |
| Review generated output ownership or drift | `references/plan-review-gotchas.md` | `workflows/review-plan-and-generate.md` |

## Stop Conditions

- Plan evidence is missing.
- Ownership, conflict policy, template ID, module placement, or output kind is unclear.
- Generation has completed and the generated diff needs human review.
