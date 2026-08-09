# Outcome

Close the post-rebase Integration Event provider completion contract. Every built-in provider must resolve the durable publish attempt exactly once with either success or failure, including synchronous preflight failures, asynchronous send failures, and empty HTTP subscriber sets. Callback exceptions must not reclassify or reopen a terminal outcome.

# Scope

- Add one core, transport-neutral once-only completion guard for `IntegrationEventPublisher.PublishCallback`.
- Make HTTP, RabbitMQ, and RocketMQ publishers route every preflight, synchronous, and asynchronous failure through that guard.
- Make provider success resolve only at the provider hand-off boundary: HTTP after all registered subscribers return; RabbitMQ after `convertAndSend` returns; RocketMQ from the SDK success callback.
- Keep the canonical `IntegrationEventEnvelope`, Jackson codec, entity/aggregate rejection, stable subscriber identity, and existing reliable Event state machine unchanged.
- Add focused core/provider tests for exact callback counts, callback exclusivity, callback exception isolation, HTTP empty-subscriber failure, and RocketMQ synchronous failure.

# Non-goals

- No RabbitMQ publisher-confirm redesign.
- No RocketMQ production topology or routing redesign.
- No HTTP route reset or new transport mode.
- No second transport-owned reliable state machine, inbox, deduplication store, global ordering, or automatic scheduler.
- No compatibility layer for retired publisher contracts.

# Acceptance examples

- Each provider's success path calls `onSuccess` exactly once and never calls `onException`.
- Each synchronous and asynchronous failure path calls `onException` exactly once and never calls `onSuccess`.
- Duplicate provider callbacks are idempotently ignored.
- A callback that throws does not cause the opposite callback to be invoked.
- A normal provider path never returns without resolving a callback.
- HTTP with no registered subscriber explicitly fails the publish attempt.
- RocketMQ synchronous `asyncSend` failure explicitly fails the publish attempt.
- RabbitMQ message post-processing only adds provider metadata; it does not acknowledge publish success before `convertAndSend` returns.

# Constraints and invariants

- One canonical `IntegrationEventEnvelope` and one Runtime Jackson codec remain the only payload contract.
- Persisted Aggregate/Entity instances remain rejected from reliable event payloads.
- Subscriber identity remains explicit, stable, and transport-neutral.
- Provider completion is a terminal callback boundary only; retry, lease, ack, and retention remain Runtime-owned.
- No raw business payload is emitted in completion diagnostics.

# Decisions

- Centralize the once-only terminal transition in a small core helper backed by an atomic guard.
- Mark the terminal state before invoking user callback code; callback exceptions are logged and swallowed.
- Treat the provider hand-off boundary as success, not user callback execution, so callback failures cannot reclassify a successful send.
- Treat an empty HTTP subscriber set as an explicit failure because it cannot establish a delivery hand-off.

# Open questions

None.

# Verification expectations

- Focused tests in `ddd-core`, `ddd-integration-event-http`, `ddd-integration-event-rabbitmq`, and `ddd-integration-event-rocketmq`.
- Full Gradle `check`, `git diff --check`, and Comet Native check/report/verify.
- Verify no reliable Event record can remain unresolved after a provider's normal or failed terminal path.
