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

1. `rules/integration-event-boundaries.md`
2. `../shared/rules/layer-and-runtime-boundaries.md`

## Common Routes

| Task | Workflow |
|---|---|
| Open Host Service | `workflows/design-open-host-service.md` |
| external capability consumption | `workflows/consume-external-capability.md` |
| inbound integration event | `workflows/handle-inbound-integration-event.md` |

## Additional Reads

Read `rules/published-language.md` when designing boundary contracts, Open Host Service payloads, or Integration Event fields.
Read `references/gotchas.md` before writing technical design, generator inputs, or handwritten slots for service-boundary work.

## Stop Conditions

Stop when transport mechanics are assigned to business code, an entry writes repository or aggregate state directly, provider terms leak into boundary language, or a write flow calls a client before entering the command/application use case.
