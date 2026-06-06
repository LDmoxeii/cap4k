---
name: cap4k-tactical-modeling
description: >
  Use when a coherent cap4k business brief needs tactical DDD decisions mapped
  to cap4k carriers, generator input expectations, rollback notes, and the
  next technical design phase.
---

# Cap4k Tactical Modeling

Map business language to cap4k tactical carriers.

## Always Read

1. `../shared/references/tactical-affordance-map.md`
2. `../shared/workflows/forced-rollback.md`
3. `workflows/map-tactical-carriers.md`

## Rules

- Start from a business brief, not table shape or generator convenience.
- Every generic DDD concept must map to a cap4k carrier or stay an open decision.
- Record rollback notes when a business signal has no clear carrier.
- Route to `cap4k-technical-design` only after the carrier table is coherent.
