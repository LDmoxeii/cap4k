# RabbitMQ Transport Contract

## Outcome

RabbitMQ is a broker-backed Integration Event provider. It contributes explicit exchange/routing
configuration, stable per-application subscriptions, real publisher-confirm semantics, and broker
consumer acknowledgement. Reliable ownership, retry, lease, and delivery context remain Runtime
contracts.

## Depends on

- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-provider-composition`
- `runtime-reliable-event-state`
- `runtime-handler-contract`

## Route configuration

The route for each event is explicit and keyed only by the stable event name:

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

- The route declares the provider destination; it is not inferred from the event annotation or a
  subscriber name.
- Invalid, missing, duplicate, or contradictory route definitions fail deterministically before
  delivery/enrollment.
- Queue names are Runtime-owned stable identities derived from `applicationName + eventName`.
  Applications do not provide an alternate per-subscriber identity in the event payload.

## Subscription identity and topology

- Each consuming application gets an independent queue for each actual local Integration Event
  Handler event name.
- Instances with the same `applicationName` share that queue and compete for messages. Different
  application names receive independent queues and therefore independent acknowledgement/retry
  boundaries.
- No global subscriber registry, broadcast list, dynamic subscription API, or per-consumer state
  table is introduced.
- The broker may preserve ordering within a delivery topology, but Runtime makes no cross-message,
  cross-instance, cross-service, or global ordering promise. Local `@Order` remains the only
  Handler ordering control.

## Publisher confirmation

For a claimed reliable outbound Event:

1. encode the canonical envelope;
2. publish to the configured exchange/routing key;
3. wait for the provider's actual positive publisher confirmation;
4. acknowledge the reliable Event only after that confirmation.

The return of a client send method without a broker-confirm result is not sufficient. Negative,
missing, timed-out, or otherwise unknown confirmation is a provider failure and is retried by the
Runtime reliable Event state machine. A process loss after broker handoff but before durable
acknowledgement may produce a duplicate.

The provider completion callback is terminal and once-only. Synchronous exceptions, asynchronous
provider failures, duplicate callbacks, and callback exceptions must not leave the reliable record
without a terminal provider result or invert a previously accepted result.

## Consumer acknowledgement

- The consumer decodes the canonical envelope, installs `ReliableEventDeliveryContext`, and invokes
  all matching synchronous local Handlers according to their `condition` and local `@Order`.
- Runtime joins every managed `queries.askAsync*` and `capabilities.callAsync*` operation started
  within each Handler scope before considering the delivery complete.
- All local work succeeding sends the provider's success acknowledgement (`basicAck` or the
  equivalent adapter operation).
- Any Handler or managed scoped operation failing sends no success acknowledgement; the provider's
  retry/requeue/redelivery path is used. Later local Handlers are not invoked for that delivery.
- Broker redelivery metadata is mapped only to the non-authoritative delivery hint in
  `ReliableEventDeliveryContext`; it is not used as proof of duplicate processing.

## Provider health and diagnostics

- Temporary broker loss leaves the application alive, reports `DEGRADED`/`RECOVERING`, retries
  claimed outbound records through the reliable state machine, and allows the consumer to reconnect.
- Deterministic codec, event-type, route, topology, and duplicate-registration errors fail at
  startup/enrollment rather than being retried forever.
- Logs and failure facts may include event identity/type, route identity, and provider failure
  category, but never raw business payload JSON or persistence-bound entities.

## Non-goals

- Sender-side knowledge of all consumers or collection of downstream acknowledgements.
- Exactly-once delivery, global order, inbox/deduplication tables, framework DLQ, per-Handler progress,
  or a RabbitMQ-specific reliable Event state machine.
- Route defaults or compatibility aliases based on historical annotation fields.

## Acceptance

Focused adapter and integration tests must prove:

- explicit exchange/routing-key route resolution and deterministic invalid-route failures;
- stable `applicationName + eventName` queue identity, same-app competing consumption, and
  independent queues for different applications;
- positive publisher confirm before reliable acknowledgement, including negative/timeout/unknown
  confirmation and process-loss duplicate windows;
- success acknowledgement only after all local synchronous Handlers and managed scoped async work;
- handler failure, no-ack/requeue/redelivery, and context installation/cleanup;
- degraded/recovering provider state and reconnect behavior;
- once-only completion, duplicate callback protection, safe diagnostics, and no raw payload logging.
