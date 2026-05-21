---
name: cap4k-implementation
description: >
  Use when implementing cap4k business project code: command handlers, query
  handlers, subscribers, jobs, controllers, factories, specifications, domain
  services, Mediator usage, repository access, value types, and UoW persistence.
---

# Cap4k Implementation

Use this after modeling and generation boundaries are clear.

## Always Read

1. `../shared/rules/default-path-and-write-boundaries.md`
2. `../shared/rules/ownership-and-generation-flow.md`
3. `rules/source-of-truth-and-skeletons.md`
4. `rules/layering.md`
5. `rules/mediator-and-uow.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Implement write use case | `rules/source-of-truth-and-skeletons.md`, `rules/value-types.md`, `references/gotchas.md` | `workflows/implement-command-slice.md` |
| Implement read use case | `rules/source-of-truth-and-skeletons.md`, `references/gotchas.md` | `workflows/implement-query-slice.md` |
| Implement subscriber or job | `rules/source-of-truth-and-skeletons.md`, `rules/layering.md`, `rules/mediator-and-uow.md` | `workflows/implement-subscriber-or-job.md` |

## Stop Conditions

Stop when aggregate boundaries are unclear, generated ownership is unclear, a required generator-capable skeleton is missing, a generation input contract is missing, or a write path bypasses command/UoW without explicit human approval.
