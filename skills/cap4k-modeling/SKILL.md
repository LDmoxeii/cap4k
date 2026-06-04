---
name: cap4k-modeling
description: >
  Use for cap4k business modeling before code: business intent, DDD boundaries,
  aggregates, entities, value objects, domain events, service interaction
  boundaries, domain services, specifications, and technical方案 before
  generation or implementation.
---

# Cap4k Modeling

Use this before generating or implementing when domain boundaries, events, invariants, or use-case shape are not settled.

## Always Read

1. `../shared/rules/cap4k-positioning.md`
2. `../shared/rules/layer-and-runtime-boundaries.md`
3. `../shared/workflows/forced-rollback.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Clarify unclear requirements | `references/gotchas.md` | `workflows/clarify-business-intent.md` |
| Derive aggregates and tactical objects | `rules/tactical-modeling.md` | `workflows/derive-ddd-model.md` |
| Model external facts and integration events | `rules/tactical-modeling.md`, `references/gotchas.md` | `workflows/define-cross-boundary-events.md` |
