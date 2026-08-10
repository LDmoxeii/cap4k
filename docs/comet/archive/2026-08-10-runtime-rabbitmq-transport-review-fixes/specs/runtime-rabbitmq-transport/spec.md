# RabbitMQ Transport Contract

## Outcome

RabbitMQ is a broker-backed Integration Event provider. It contributes explicit exchange/routing
configuration, stable per-application subscriptions, actual publisher confirmation, broker consumer
acknowledgement, bounded publisher lifecycle, pre-persistence route validation, and Runtime-visible
health facts. Reliable ownership, retry, lease, canonical envelope, synchronous Handler completion,
and delivery context remain shared Runtime contracts.

## Configuration and routes

Routes use the stable logical event name as the only key:

```yaml
cap4k:
  ddd:
    integration-event:
      rabbitmq:
        routes:
          "[content.published]":
            exchange: content
            routing-key: published
```

- The route is provider topology and is never parsed from `@IntegrationEvent.value`.
- There is no route default, annotation fallback, placeholder topology syntax, or compatibility alias.
- The provider validates route keys and route values before delivery/enrollment. Missing, blank,
  duplicate, or contradictory static facts fail deterministically. A route cannot be rediscovered
  from an annotation, placeholder, legacy destination string, or alternate provider surface.
- Eager Integration Event attachment resolves the static route before the attachment is accepted.
- Lazy Integration Event attachment resolves the static route at `prePersist`; a missing route fails
  before `EventRecordRepository.save` and therefore cannot create a durable reliable Event record.
- The publisher independently resolves the route again before RabbitMQ delivery. This defensive
  boundary covers historical records, non-standard persistence paths, and configuration drift and
  is not removed when attach/pre-persist validation exists.
- Exactly one outbound Integration Event Transport may own an application. Deterministic zero/many
  provider composition errors required by enabled capabilities fail application startup rather than
  selecting or broadcasting among providers.
- Confirmation waiting is bounded. A missing, timed-out, negative, exceptional, or unknown provider
  result fails the provider completion and leaves retry ownership with the shared reliable Event
  state machine.

## Subscription identity and topology

- The inbound registration source is the shared catalog-derived view of actual local synchronous
  Integration Event Handlers.
- Each registration creates one stable queue identity derived only from `applicationName + eventName`.
- The operator-visible queue name is
  `cap4k.<application-slug>.<event-slug>.<sha256>`. Each slug is a bounded ASCII projection containing
  only letters, digits, dot, underscore, and hyphen. The complete lowercase SHA-256 is computed from
  the exact UTF-8 application name, a NUL separator, and the exact UTF-8 event name. The total name is
  bounded below RabbitMQ's 255-byte limit, remains recognizable in management tooling, and is
  collision-safe when slug cleanup, truncation, or delimiter ambiguity would otherwise merge exact
  pairs.
- No application property, annotation field, custom container, or legacy queue override may replace
  the Runtime-owned queue identity.
- Instances with the same application name share the queue and compete for deliveries. Different
  application names use independent queues and independent acknowledgement/retry boundaries.
- Queue identity is not supplied by event payloads or subscriber annotation fields.
- No registry, fan-out table, dynamic subscription API, or per-consumer state table is introduced.

## Outbound provider handoff

For one claimed reliable Event, the provider:

1. encodes the canonical envelope;
2. resolves its explicit exchange/routing-key route;
3. publishes with correlation identity;
4. waits for RabbitMQ's provider confirmation within the configured bound;
5. resolves `IntegrationEventPublishCompletion` exactly once.

`convertAndSend` returning is not success. Confirmation NACK, timeout, callback/future failure,
connection failure, and unknown result are failures. Process loss after broker handoff but before
Runtime durable acknowledgement may produce a duplicate under the at-least-once contract.

Publisher-confirm ACK is successful only when mandatory return did not report the message as
unroutable. The provider publishes with mandatory routing enabled. An unroutable return fails the
shared completion and leaves the reliable Event retryable; it is never converted into a false
successful handoff.

## Publisher lifecycle

- `RabbitMqIntegrationEventPublisher` exposes an explicit close lifecycle.
- An executor created internally by the publisher is publisher-owned and is shut down when the
  publisher closes or the Spring application context destroys the publisher bean.
- An executor supplied to the publisher is borrowed. Publisher closure never shuts down, interrupts,
  or otherwise takes ownership of that external executor.
- Close is idempotent.
- Publisher closure and task submission form one lifecycle boundary. After close returns, no new
  publish task is accepted and no Rabbit send is performed for that rejected submission.
- A rejected post-close publish resolves the shared publish completion as failure exactly once.
- Closing a publisher that never created its internal executor does not create an executor merely to
  shut it down.

## Inbound acknowledgement and recovery

- The consumer decodes the canonical envelope and resolves payload type through the shared inbound
  registration view.
- It installs `ReliableEventDeliveryContext` containing `eventId`, `eventName`, `publishedAt`, an
  exact delivery attempt only where RabbitMQ can provide one, and the non-authoritative
  `UNKNOWN/FIRST/REDELIVERED` hint. It never adds subscriber identity or Rabbit topology facts.
- It dispatches matching synchronous Handlers according to condition and local `@Order`, and waits
  for every Runtime-managed async Query/Capability scope.
- Only complete success sends `basicAck`.
- Handler, managed-scope, decode, or dispatch failure sends no success acknowledgement and uses the
  RabbitMQ requeue/redelivery path. Later Handlers are not invoked after failure.
- Rabbit redelivery metadata maps only to the non-authoritative delivery hint. It never proves that
  business processing is a duplicate.
- Spring AMQP remains responsible for normal recovery of listener containers that are already
  running. The provider does not add a second recovery scheduler.
- When an initial temporary broker failure leaves a container not running, a later connection-created
  signal replays the remembered topology and starts only non-running containers.
- Recovery reports `DEGRADED` or `RECOVERING` until positive topology, connection, and subscriber
  evidence allow the aggregated RabbitMQ provider state to return to `HEALTHY`.

## Provider health

- Runtime supplies a transport-neutral provider-state registry with stable provider identity,
  registration ownership, duplicate-provider rejection, reporter close/deregister semantics,
  thread-safe state updates, and readable snapshots.
- Positive provider evidence may report `HEALTHY`.
- Connection or confirmation loss reports `DEGRADED`; active recovery/reconnection reports
  `RECOVERING` where Spring AMQP exposes that state.
- Temporary broker loss does not create a deterministic application-startup failure and does not
  create a provider-owned retry store.
- Publish failures caused by temporary broker, connection, confirm, or return-path loss leave the
  reliable Event incomplete so the shared Runtime retry policy continues execution.
- Static Agent facts remain declared capability facts and do not probe the broker.

## Delivery context and safety

- `ReliableEventDeliveryContext` and `IntegrationEventDeliveryMetadata` contain only
  transport-neutral reliable-delivery facts.
- They contain no subscriber identity, exchange, routing key, queue, consumer identity, channel,
  connection, AMQP message, or raw payload facts.
- Diagnostics may contain event ID, stable event name, safe route identity, queue identity, and a
  stable provider failure category.
- Diagnostics and failure facts never contain raw envelope/payload JSON, AMQP body bytes,
  persistence-bound entities, channels, connections, or arbitrary broker message `toString()` data.

## Non-goals

- Sender-side knowledge of all consumers or downstream acknowledgement collection.
- Exactly-once delivery, global order, inbox/deduplication, framework DLQ, or per-Handler progress.
- A RabbitMQ-specific reliable Event state machine, public scheduler, transport retry repository, or
  second listener recovery scheduler.
- Compatibility with historical annotation topology, inferred destination strings, or legacy APIs.
- HTTP or RocketMQ Provider State migration.

## Acceptance

- Internally owned executors close on publisher/context shutdown; borrowed executors remain usable by
  their owner; close is idempotent; and post-close publish is rejected before Rabbit delivery.
- Missing routes are rejected at eager attach and lazy `prePersist`, and the lazy failure path proves
  `EventRecordRepository.save` was not called.
- Publisher route resolution remains as a defensive second check.
- Explicit routes resolve one event name to one exchange/routing key and reject every invalid or
  conflicting configuration before durable reliable persistence or delivery.
- Queue identities are stable for equal application/event pairs and distinct for different
  application names or event names.
- Publisher success occurs only at the confirmed provider handoff boundary; negative, timeout,
  unroutable, exceptional, synchronous, duplicate, late, and post-close paths terminate exactly once.
- Consumer `basicAck` follows complete local Handler success; failures use requeue/redelivery and
  preserve context cleanup.
- A temporary initial listener start failure is followed by topology replay and restart of only the
  non-running container when the connection returns, with aggregated provider state returning to
  healthy after positive evidence.
- Focused tests and static scans prove canonical envelope usage, safe diagnostics, transport-neutral
  delivery context, and absence of a second reliable or recovery state machine.
