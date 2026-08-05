# Integration Event Transport Contract

## Depends on

`runtime-reliable-event-state` and the shared Runtime Jackson contract.

## Contract

- Exactly one transport provider is active for each configured transport identity.
- HTTP uses one static route map and remains an experience transport; self-routing is allowed for
  local demos.
- RabbitMQ and RocketMQ use explicit route/subscription identity; no inferred fan-out registry is
  introduced.
- Publishers report real broker confirmation where the broker supports it.
- Consumers acknowledge only after the synchronous handler scope completes; failures remain
  retryable according to the reliable state machine.
- Inbound Integration Events install `ReliableEventDeliveryContext` before handler dispatch.

## Non-goals

No second envelope/state machine, broker abstraction that hides provider facts, or production claim
for HTTP mode.

## Acceptance

Focused tests cover route selection, publisher confirmation, subscription identity, acknowledgement
timing, failure propagation, duplicate delivery, and context installation per transport.
