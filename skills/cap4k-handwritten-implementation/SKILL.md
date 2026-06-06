---
name: cap4k-handwritten-implementation
description: >
  Use when implementing cap4k handwritten business logic inside approved
  generated skeletons after human generated-output review: command handlers,
  query handlers, subscribers, jobs, controllers, factories, specifications,
  domain services, Repository access, Mediator routing, and UoW persistence.
---

# Cap4k Handwritten Implementation

Implement business logic only inside approved generated skeleton surfaces.

## Always Read

1. `rules/implementation-entry-gates.md`
2. `../shared/rules/generated-skeleton-ownership.md`
3. `workflows/implement-inside-generated-skeletons.md`

## Route Boundary

Use after `cap4k-generation-review` and human review of generated output.
If a structural skeleton is missing, ownership is unclear, or the handwritten
slot is not in the technical design, return to the earlier phase instead of
creating parallel structure.

## Common Tasks

| Task | Read | Workflow |
|---|---|---|
| Fill approved command, query, subscriber, or job logic | `references/implementation-gotchas.md` | `workflows/implement-inside-generated-skeletons.md` |
| Add internal command/query routing | `references/implementation-gotchas.md` | `workflows/implement-inside-generated-skeletons.md` |
| Persist aggregate changes | `references/implementation-gotchas.md` | `workflows/implement-inside-generated-skeletons.md` |

Route-level `routing.yaml` supplies the shared Skeleton Generation Gate,
generator-supported skeleton map, and runtime capability map for this route.
