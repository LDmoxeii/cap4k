---
name: cap4k-authoring
description: >
  Route cap4k business-authoring work through the self-contained cap4k skill
  system. Use when the user asks to discover a business slice, model DDD
  boundaries, write cap4k technical design, author generator inputs, review
  plan or generated output, implement handwritten business logic, design service
  integration, or verify cap4k work.
---

# Cap4k Authoring Router

This is the entry router for cap4k business-authoring agents. It is not the rulebook.

## Always Read

1. `routing.yaml`
2. `../shared/workflows/forced-rollback.md`

## Session Discipline

- Re-read this file and `routing.yaml` for every new user task.
- Do not use public docs, analysis maps, issues, Context7, or historical specs as runtime instructions.
- Route to the current phase skill before acting.
- For structural creation or modification, make sure the routed workflow loads `../shared/workflows/skeleton-generation-gate.md`.

## Routing Source

`routing.yaml` is the only routing source of truth. Do not maintain a second route table in Markdown.

## Priority

1. Current user instruction and explicit project scope.
2. `routing.yaml` phase and specialist route.
3. Routed skill rules and workflows.
4. Existing business project conventions.
5. Human audit remains required for domain decisions.
