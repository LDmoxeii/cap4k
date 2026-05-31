---
name: cap4k-authoring
description: >
  Route cap4k business-project AI authoring tasks to focused skills. Use when
  the user asks to model a cap4k business project, generate cap4k code from DB
  or design JSON, implement a cap4k project slice, design cap4k service
  integration, verify cap4k work, or review generated cap4k output.
---

# Cap4k Authoring Router

This is a router, not the rulebook. Pick the focused skill that matches the current task and read that skill before acting.

If a task matches multiple rows, read `references/route-map.md` before choosing or chaining focused skills.

## Boundaries

- Use these skills for business projects built with cap4k.
- Do not load public docs as runtime instructions unless the user asks for public documentation work.
- Focused skills carry the shared cap4k constraints; this router only chooses the task path.

## Routes

| Task | Use Skill |
|---|---|
| Clarify business intent, aggregate boundaries, DDD concepts, events | `cap4k-modeling` |
| Bootstrap or generate from DB/design/enum/value-object/addon inputs | `cap4k-generation` |
| Implement command/query/subscriber/job/controller project code | `cap4k-implementation` |
| Design or implement service-boundary interaction | `cap4k-service-integration` |
| Run tests, compile, analysis, flow/drawing-board, final evidence | `cap4k-verification` |
| Review generated output, plan output, or ownership | `cap4k-generated-output-review` |

## Priority

1. Current user instruction and explicit project scope.
2. Focused skill rules for the routed task.
3. Existing project conventions.
4. Human audit remains required for domain decisions.
