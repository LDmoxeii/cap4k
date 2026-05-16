---
name: cap4k-modeling
description: >
  Use for cap4k business modeling before code: business intent, DDD boundaries,
  aggregates, entities, value concepts, domain events, integration events,
  domain services, specifications, and technical方案 before generation or implementation.
---

# Cap4k Modeling

Use this before generating or implementing when domain boundaries, events, invariants, or use-case shape are not settled.

## Always Read

1. `rules/domain-language.md`
2. `rules/tactical-modeling.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Clarify unclear requirements | `references/gotchas.md` | `workflows/clarify-business-intent.md` |
| Derive aggregates and tactical objects | `rules/tactical-modeling.md` | `workflows/derive-ddd-model.md` |
| Model external facts and integration events | `rules/tactical-modeling.md`, `references/gotchas.md` | `workflows/define-cross-boundary-events.md` |

## Hard Boundary

Do not invent cap4k generator support for first-class `value_object` or `domain_service`. Treat them as handwritten modeling choices unless a later generator capability exists in the codebase.
