---
name: cap4k-service-integration
description: >
  Use for cap4k business service interaction design and implementation:
  Open Host Service entries, Published Language, external capability clients,
  external fact entries, inbound/outbound integration events, callbacks,
  message listeners, and command/query routing across service boundaries.
---

# Cap4k Service Integration

Use this when a cap4k business project needs to consume external capabilities, expose synchronous capabilities, receive external facts, or publish internal facts across boundaries.

## Always Read

1. `../shared/rules/default-path-and-write-boundaries.md`
2. `../shared/rules/naming-layout-and-testing.md`
3. `rules/service-boundaries.md`
4. `rules/integration-events.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Internal code consumes an external capability | `references/gotchas.md` | `workflows/consume-external-capability.md` |
| External systems or frontend consume internal capability synchronously | `references/gotchas.md` | `workflows/design-open-host-service.md` |
| External callback, message, or inbound event enters the project | `references/gotchas.md` | `workflows/handle-external-fact.md` |

## Stop Conditions

Stop when an entry implementation is about to write repository or aggregate state directly, when an external protocol leaks into domain/application contracts, or when a write flow calls client first and command second.
