---
name: cap4k-runtime-integration
description: >
  Use for cap4k runtime integration work: integration events, external callbacks,
  HTTP/RabbitMQ/RocketMQ event adapters, request runtime, framework persistence
  tables, event subscriber bridge, and cross-service event contracts.
---

# Cap4k Runtime Integration

Use this when external systems, integration event transport, callbacks, or runtime framework tables are involved.

## Always Read

1. `rules/request-and-event-runtime.md`
2. `rules/integration-event-adapters.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| External callback enters project | `references/framework-tables.md` | `workflows/handle-external-callback.md` |
| Configure HTTP integration event path | `references/framework-tables.md` | `workflows/configure-http-integration-events.md` |

## Stop Conditions

Stop when an external callback is being modeled as a domain event or required framework tables are missing.
