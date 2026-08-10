# Outcome

Complete PR #180 as the shared Runtime contract baseline for RabbitMQ, HTTP, and RocketMQ by closing
the RabbitMQ publisher lifecycle leak, rejecting unusable RabbitMQ routes before reliable Event
records are saved, and proving the existing listener recovery path. The existing transport-neutral
provider-state and delivery-context decisions remain unchanged.

# Scope

- Make `RabbitMqIntegrationEventPublisher` explicitly closeable.
- Close only the executor created and owned by the publisher; never close an externally supplied
  executor.
- Wire the RabbitMQ publisher bean with an explicit Spring destroy method.
- Reject new publish submissions after publisher closure and keep closure idempotent.
- Add a RabbitMQ `IntegrationEventInterceptor` that resolves the static event-name route at eager
  attach and reliable `prePersist` boundaries.
- Prove that a missing route fails before `EventRecordRepository.save` while retaining the
  publisher's defensive route resolution.
- Add a focused recovery test covering temporary initial listener start failure, connection
  recovery, topology redeclaration, restart of non-running containers, and return to healthy
  provider state.
- Refresh focused tests, full repository verification, and Comet evidence for the additional fixes.

# Non-goals

- Do not remove or relocate the transport-neutral Runtime Provider State API.
- Do not restore `subscriberIdentity` or add exchange, queue, routing key, consumer identity, AMQP
  objects, or raw payload facts to `ReliableEventDeliveryContext`.
- Do not restore legacy RabbitMQ APIs, inferred destinations, custom container overrides, or
  compatibility layers.
- Do not change HTTP or RocketMQ Provider State migration in this change.
- Do not add a transport-owned retry store, listener recovery scheduler, inbox, DLQ, deduplication,
  exactly-once delivery, or downstream acknowledgement collection.
- Do not claim live RabbitMQ broker or Testcontainers evidence when it was not executed.

# Acceptance examples

- A publisher using its internal fixed thread pool receives `close()` twice: the owned executor is
  shut down once, both calls succeed, and a later publish is rejected without a Rabbit send.
- A publisher using an externally supplied executor is closed: the publisher rejects later
  publishes, while the external executor still accepts work until its owner shuts it down.
- Closing the Spring application context invokes the RabbitMQ publisher close lifecycle through the
  starter bean destroy contract.
- Eager attachment of an Integration Event whose stable event name has no RabbitMQ route fails at
  `onAttach`.
- Lazy attachment resolves an Integration Event with no route during release: a transient
  `EventRecord` may be created in memory, but `EventRecordRepository.save` is never called.
- The publisher still resolves the route defensively before every Rabbit publish, including records
  created outside the normal attachment path or affected by later configuration drift.
- A listener container whose initial start fails because the broker is temporarily unavailable
  reports degraded/recovering state. A later connection-created callback redeclares topology,
  starts only non-running containers, and returns the aggregated provider state to healthy after
  positive evidence.
- Containers already running under Spring AMQP recovery are not restarted by a second scheduler.

# Constraints and invariants

- `com.only4.cap4k.ddd.core.application.provider` remains the shared Runtime Provider State API.
- Provider registration ownership, duplicate `providerId` rejection, and reporter
  close/deregister semantics remain intact.
- `ReliableEventDeliveryContext` and `IntegrationEventDeliveryMetadata` remain free of
  `subscriberIdentity` and provider topology.
- Route validation happens before durable reliable Event persistence; publisher route resolution is
  a second defensive boundary, not the only boundary.
- Publisher close has a linearizable submission boundary: after close returns, no new publish task
  is accepted.
- A borrowed executor remains owned by its caller.
- Temporary broker absence is operational degradation, not deterministic startup misconfiguration.
- Breaking cleanup is allowed; no compatibility behavior is required.

# Decisions

- Use `AutoCloseable` as the publisher lifecycle surface and an explicit starter `destroyMethod`.
- Track executor ownership from construction: internal executors are owned, injected executors are
  borrowed.
- Use the existing `IntegrationEventInterceptor.onAttach` and `EventInterceptor.prePersist`
  pipeline for early RabbitMQ route validation.
- Keep `RabbitMqIntegrationEventPublisher` route resolution unchanged as a defensive check.
- Reuse Spring AMQP container recovery for running containers and the existing connection listener
  path for topology replay and stopped-container restart; introduce no new scheduler.
- Treat the new Comet change as a follow-up verification slice for PR #180 rather than rewriting the
  archived evidence from the original implementation.
- The user confirmed this complete shared understanding on August 10, 2026.

# Open questions

- None.

# Verification expectations

- Run RabbitMQ module and starter focused tests, including owned/borrowed executor lifecycle,
  idempotent close, post-close rejection, early route rejection with zero repository saves, and
  listener recovery to healthy.
- Run `./gradlew.bat check --no-daemon`.
- Run `comet native check runtime-rabbitmq-transport-review-fixes --json` and record a fresh receipt.
- Update the new change's `verification.md` with canonical acceptance evidence.
- Record live RabbitMQ broker/Testcontainers checks as skipped unless they are actually executed.
- Keep HTTP and RocketMQ Provider State migration outside the implementation scope.
