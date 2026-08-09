# RocketMQ Transport Contract

## Outcome

RocketMQ is a broker-backed Integration Event provider. It contributes explicit topic/tag routing,
stable per-application consumer groups, SDK send-result semantics, and broker consumer
acknowledgement. Reliable ownership, retry, lease, envelope, and delivery context remain Runtime
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
      rocketmq:
        routes:
          "[content.published]":
            topic: content
            tag: published
```

- Topic and tag are provider route facts. They are not inferred from the event annotation, Java/Kotlin
  package, or a subscriber field.
- Invalid, missing, duplicate, or contradictory route definitions fail deterministically before
  delivery/enrollment.
- Consumer-group identity is Runtime-owned and derived from `applicationName + eventName`.
  Applications do not put a group name in the event payload or annotation.

## Subscription identity and topology

- Each consuming application gets an independent Consumer Group for each actual local Integration
  Event Handler event name.
- Instances with the same `applicationName` share the group and compete for messages. Different
  application names receive independent groups and therefore independent acknowledgement/retry
  boundaries.
- No global subscriber registry, broadcast list, dynamic subscription API, or per-consumer state
  table is introduced.
- RocketMQ ordering or retry behavior must not be promoted to a global Runtime ordering guarantee;
  local `@Order` remains the only Handler ordering control.

## Publisher send result and completion

For a claimed reliable outbound Event:

1. encode the canonical envelope;
2. send to the configured topic/tag;
3. accept the handoff only after the SDK's documented positive send result/confirmation;
4. acknowledge the reliable Event only after that positive result.

The implementation must map the actual RocketMQ SDK result, not invent a provider-independent
success value. A synchronous send exception, asynchronous failure callback, timeout, unavailable or
unknown result is a provider failure and is retried by the Runtime reliable Event state machine.
A process loss after broker handoff but before durable acknowledgement may produce a duplicate.

Provider completion may be synchronous or asynchronous, but the shared completion callback is
terminal and once-only. Duplicate SDK callbacks and callback exceptions cannot reclassify or reopen
the durable attempt.

## Consumer acknowledgement

- The consumer decodes the canonical envelope, installs `ReliableEventDeliveryContext`, and invokes
  all matching synchronous local Handlers according to their `condition` and local `@Order`.
- Runtime joins every managed `queries.askAsync*` and `capabilities.callAsync*` operation started
  within each Handler scope before considering the delivery complete.
- All local work succeeding returns the provider's success consumption result (for example,
  `CONSUME_SUCCESS` through the adapter).
- Any Handler or managed scoped operation failing returns the provider's retry instruction (for
  example, `RECONSUME_LATER`). Later local Handlers are not invoked for that delivery.
- Provider redelivery/attempt metadata is mapped only to the non-authoritative delivery hint and
  exact attempt when the SDK supplies one.

## Provider health and diagnostics

- Temporary broker loss leaves the application alive, reports `DEGRADED`/`RECOVERING`, retries
  claimed outbound records through the reliable state machine, and lets the consumer reconnect.
- Deterministic codec, event-type, route, topology, and duplicate-registration errors fail at
  startup/enrollment rather than being retried forever.
- Logs and failure facts may include event identity/type, route identity, and provider failure
  category, but never raw business payload JSON or persistence-bound entities.

## Non-goals

- Sender-side knowledge of all consumers or collection of downstream acknowledgements.
- Exactly-once delivery, global order, inbox/deduplication tables, framework DLQ, per-Handler progress,
  or a RocketMQ-specific reliable Event state machine.
- Route defaults or compatibility aliases based on historical annotation fields.

## Acceptance

Focused adapter and integration tests must prove:

- explicit topic/tag route resolution and deterministic invalid-route failures;
- stable `applicationName + eventName` Consumer Group identity, same-app competing consumption,
  and independent groups for different applications;
- positive SDK send result before reliable acknowledgement, including synchronous throw,
  asynchronous failure, timeout/unknown result, and process-loss duplicate windows;
- success consumption acknowledgement only after all local synchronous Handlers and managed scoped
  async work;
- handler failure/retry instruction/redelivery and context installation/cleanup;
- degraded/recovering provider state and reconnect behavior;
- once-only completion, duplicate callback protection, safe diagnostics, and no raw payload logging.
