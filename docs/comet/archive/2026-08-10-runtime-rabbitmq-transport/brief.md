# Outcome

RabbitMQ becomes a complete broker-backed Integration Event transport for cap4k. Stable logical
event names resolve through explicit RabbitMQ routes, outbound reliable Events complete only at the
confirmed provider handoff boundary, and inbound deliveries acknowledge only after the entire local
synchronous Handler scope succeeds.

# Scope

- Replace annotation-derived RabbitMQ topology with
  `cap4k.ddd.integration-event.rabbitmq.routes[eventName]` configuration.
- Validate exchange/routing-key routes and actual inbound registrations before delivery begins.
- Enforce exactly one outbound Integration Event Transport and reject deterministic provider
  composition, static route, and duplicate-registration errors during application startup.
- Derive one stable queue per `applicationName + eventName`; equal application names compete on the
  same queue and different application names receive independently.
- Implement correlated publisher confirms, bounded confirmation timeout, negative/unknown result
  handling, and once-only completion through the shared Runtime callback.
- Preserve manual consumer acknowledgement, requeue/redelivery, canonical envelope decoding,
  `ReliableEventDeliveryContext`, Handler conditions/order, and managed async-scope joining.
- Complete the minimal shared Runtime provider-state slot required to register and read declared
  `HEALTHY`, `DEGRADED`, and `RECOVERING` transport facts; PR #177 supplied route/catalog
  foundations but did not materialize this already-approved state boundary.
- Project RabbitMQ provider health as declared `HEALTHY`, `DEGRADED`, or `RECOVERING` Runtime facts
  without adding another reliable state machine.
- Add focused adapter/starter tests and bounded RabbitMQ integration evidence where the repository
  environment can run it.

# Non-goals

- Sender-side consumer enumeration or downstream acknowledgement collection.
- Exactly-once delivery, global ordering, inbox/deduplication, framework DLQ, or per-Handler progress.
- Annotation topology fields, compatibility aliases, inferred routes, dynamic subscription APIs, or
  a RabbitMQ-specific reliable Event state machine.
- Changes to the HTTP or RocketMQ provider contracts.

# Acceptance examples

- Given event name `content.published`, a route containing exchange `content` and routing key
  `published` is selected without parsing the annotation value as broker topology.
- Missing, blank, contradictory, or duplicate route/registration facts fail deterministically before
  delivery/enrollment rather than creating indefinitely retrying reliable records.
- Two instances named `media-worker` consume competitively from the same event queue; an application
  named `audit-worker` consumes from a different queue and has an independent acknowledgement path.
- A positive provider handoff completes the shared callback once; negative confirmation, timeout,
  connection failure, and unknown completion fail it once and leave Runtime retry ownership intact.
- The consumer sends `basicAck` only after all matching local Handlers and managed Query/Capability
  work succeed. Handler failure sends no success acknowledgement and follows RabbitMQ requeue/redelivery.
- Delivery context contains `eventId`, `eventName`, `publishedAt`, an exact provider attempt only when
  RabbitMQ can supply one, and the non-authoritative `UNKNOWN/FIRST/REDELIVERED` hint. It contains no
  subscriber identity, exchange, routing key, queue, message, or raw payload facts.
- Temporary broker loss does not kill the application; provider state becomes `DEGRADED` or
  `RECOVERING` and returns to `HEALTHY` only from positive provider evidence.

# Constraints and invariants

- `@IntegrationEvent.value` remains the only logical event name and never carries RabbitMQ topology.
- `IntegrationEventEnvelope`, Runtime Jackson, `IntegrationEventPublishCompletion`, the reliable JPA
  claim/lease state machine, and the synchronous Handler completion contract remain shared owners.
- The sender observes one RabbitMQ provider handoff only. It never knows all consumers.
- Runtime remains at-least-once; process loss after broker handoff but before durable acknowledgement
  may produce a duplicate.
- Logs and failure facts may contain safe event/route/category metadata but never raw business payload
  JSON or persistence-bound objects.
- Breaking replacement is allowed; this repository has no external compatibility obligation.

# Decisions

- Route configuration is keyed by stable event name and contains explicit `exchange` and
  `routing-key` fields.
- Queue identity is Runtime-owned and derived only from `applicationName + eventName`.
- The operator-visible queue name is
  `cap4k.<application-slug>.<event-slug>.<sha256>`. Slugs are bounded readable projections; the
  complete SHA-256 is computed from the exact UTF-8 `applicationName`, a NUL separator, and the exact
  UTF-8 `eventName`, so slug cleanup, truncation, or delimiter ambiguity cannot merge identities.
- Same-application instances compete on one queue; different applications use independent queues.
- Publisher completion requires actual RabbitMQ provider confirmation; returning from
  `convertAndSend` is insufficient.
- Publisher-confirm ACK is successful only when mandatory return did not report the message as
  unroutable. An unroutable return is a provider failure and remains retryable through the shared
  reliable Event state machine.
- Consumer success acknowledgement occurs only after the complete local Handler scope.
- Temporary external unavailability is a provider health transition and retry condition, not a
  second durable task model or unconditional application-startup failure.
- Exactly one outbound Integration Event Transport may own the application. Deterministic provider
  composition, static route, and duplicate-registration errors fail application startup; temporary
  broker unavailability does not.
- The missing shared provider-state slot is prerequisite implementation debt, not a new RabbitMQ
  product fork; this change supplies only the minimal transport-neutral registry required by the
  already-approved Runtime contract.
- On 2026-08-10 the user explicitly confirmed this Shape as the RabbitMQ projection of the already
  confirmed 2026-08-03 Runtime contract, with the readable-plus-full-SHA-256 queue encoding as the
  only new product decision.

# Open questions

# Verification expectations

- Focused route/configuration tests cover exact resolution and every deterministic invalid shape.
- Publisher tests cover confirm ACK/NACK, timeout, future exception, synchronous failure, duplicate or
  late callbacks, callback exceptions, and safe diagnostics.
- Consumer tests cover stable queue identity, same/different application names, ack after completion,
  failure requeue, redelivery context, context cleanup, and no raw payload logging.
- Starter tests cover property binding, exactly-one provider composition, enrollment validation,
  confirm prerequisites, and provider health state wiring.
- Run relevant RabbitMQ modules and starter tests, `./gradlew check`, `git diff --check`, and
  `comet native check`; record unavailable live-broker scenarios honestly under skipped checks.
