# Reliable Event State Machine

## Depends on

`runtime-handler-contract` and `runtime-reliable-jpa-substrate`.

## Contract

- Persisted Domain Events and outbound Integration Events use the same reliable state semantics.
- `Mediator.events.enqueue`, `schedule`, and `delay` are the only scheduling entry points.
- Local Domain Event dispatch and outbound delivery share the core reliable context while retaining
  their distinct provider boundaries.
- `ReliableEventDeliveryContext` carries origin and delivery facts without persisting entities.
- Every handler returns synchronously; acknowledgement happens only after the handler scope is
  complete.

## Non-goals

No broker routing, consumer ordering promise, or replacement event API in this branch.

## Acceptance

Tests cover local event success/failure, outbound handoff, retry/terminal state, context ownership,
entity-payload rejection, and duplicate delivery behavior.
