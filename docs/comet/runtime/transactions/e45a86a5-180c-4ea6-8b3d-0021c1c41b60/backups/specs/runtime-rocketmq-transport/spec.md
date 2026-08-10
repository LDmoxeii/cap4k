# RocketMQ Transport Contract

## Outcome

RocketMQ is a broker-backed Integration Event provider. It contributes explicit topic/tag routing, stable per-application Consumer Groups, real SDK send-result semantics, broker acknowledgement after the complete local Handler scope, and truthful recoverable provider state. Reliable ownership, retry, lease, envelope, completion, and delivery context remain shared Runtime contracts.

## Dependencies

- `runtime-shared-transport-foundation`
- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-provider-composition`
- `runtime-reliable-event-state`
- `runtime-handler-contract`

## Route configuration and enrollment

Each stable logical event name has one explicit provider route:

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

- Both `topic` and `tag` are required, non-blank RocketMQ topology facts.
- Topic, tag, Consumer Group, nameserver, and subscriber identity are never inferred from `@IntegrationEvent`, payload packages, or historical per-topic properties.
- One immutable resolver validates the complete active route/registration set before outbound delivery or the first consumer start.
- Missing routes, blank fields, duplicate logical names, contradictory payload mappings, and invalid provider topology fail deterministically at startup or enrollment without partial subscriber activation.
- The historical `RocketMqIntegrationEventConfigure` arbitrary-consumer extension and annotation/property topology fallbacks do not remain as compatibility paths.

## Subscription and Consumer Group identity

- Inbound subscriptions are derived only from actual local Integration Event Handler registrations supplied by the shared registration view.
- Each `(applicationName, eventName)` pair derives one deterministic, collision-resistant, RocketMQ-legal, bounded Consumer Group identity.
- Instances with the same pair share the Consumer Group and compete for messages.
- Different application names receive independent groups, and distinct event names in one application receive distinct groups.
- No global subscriber registry, consumer enumeration, broadcast list, dynamic subscription API, or per-consumer delivery state is introduced.
- RocketMQ message order and retry behavior do not create a global Runtime ordering guarantee; only local Handler `@Order` is supported.

## Publisher handoff and completion

For each claimed reliable outbound Integration Event, the provider:

1. resolves the explicit topic/tag route by envelope event name;
2. encodes the canonical Jackson envelope;
3. invokes the RocketMQ SDK with the configured delivery timeout;
4. resolves the shared `IntegrationEventPublishCompletion` exactly once from the actual SDK result.

- `SendStatus.SEND_OK` is the only positive handoff result.
- Every other SDK status, null or malformed result, timeout, synchronous exception, and asynchronous failure callback is a provider failure.
- A positive result acknowledges only the selected provider handoff. It does not acknowledge downstream consumers.
- All normal and failed paths use the shared terminal once-only completion. Duplicate callbacks and callback exceptions cannot reopen, invert, or reclassify the attempt.
- Provider failure leaves the reliable Event retryable under the shared claim/lease/retry state machine. Process loss after broker handoff but before durable acknowledgement may produce an allowed at-least-once duplicate.

## Consumer acknowledgement and delivery context

For each broker delivery, the provider:

1. decodes and validates the canonical envelope;
2. maps the SDK attempt/redelivery metadata into the non-authoritative shared delivery metadata;
3. installs execution context and `ReliableEventDeliveryContext`;
4. invokes the shared local Integration Event dispatcher;
5. returns the provider consume result only after the dispatcher completes.

- The dispatcher evaluates Handler conditions, invokes matching synchronous Handlers in local `@Order`, and joins every Runtime-managed `queries.askAsync*` and `capabilities.callAsync*` operation started inside each Handler scope.
- Full local success returns `CONSUME_SUCCESS`.
- Any codec, Handler, or managed scoped-operation failure returns `RECONSUME_LATER`; later local Handlers are not invoked.
- Delivery and execution contexts are cleared after success or failure.
- Provider topology and raw RocketMQ message objects are not exposed through the shared reliable delivery context.

## Provider state and diagnostics

- RocketMQ contributes live `HEALTHY`, `DEGRADED`, and `RECOVERING` facts through the transport-neutral Runtime provider-state registry.
- A positive handoff or connected consumer activity may establish healthy evidence; temporary broker errors degrade the provider; reconnect or renewed delivery work reports recovering until positive evidence restores health.
- Temporary nameserver/broker unavailability does not fail application startup after deterministic configuration has passed, does not falsely claim healthy state, and does not consume the shared reliable record as success.
- Static Agent API manifests declare capabilities only and do not probe RocketMQ.
- Diagnostics may contain event identity, event name, resolved route identity, Consumer Group identity, and safe provider failure category. They never contain raw business payload JSON or persistence-bound entities.

## Non-goals

- Exactly-once delivery, global ordering, inbox/deduplication, framework DLQ, per-Handler progress, or sender-side collection of downstream acknowledgements.
- A RocketMQ-specific reliable Event record, retry loop, lease model, result repository, scheduler, or generic task framework.
- Dynamic arbitrary consumers, inferred/default routes, compatibility aliases, or user-supplied Consumer Groups.
- Changes to HTTP or RabbitMQ provider behavior.

## Acceptance

Focused adapter, starter, and shared Runtime tests must prove:

- explicit topic/tag resolution and deterministic full-set validation before partial enrollment;
- stable and distinct Consumer Group identities for same application/event, different applications, and different event names;
- `SEND_OK` as the only positive handoff result, with non-positive/null/unknown status, timeout, synchronous throw, asynchronous failure, and duplicate callback coverage;
- reliable acknowledgement only after the positive SDK result and retryability for every failed handoff;
- canonical envelope decode, attempt/redelivery mapping, delivery-context installation and cleanup;
- `CONSUME_SUCCESS` only after every matching Handler and managed scoped async operation completes, and `RECONSUME_LATER` with later-Handler short circuit on failure;
- degraded/recovering/healthy provider-state transitions without startup death or false health during temporary broker loss;
- safe diagnostics and static removal of arbitrary consumer configuration, annotation/per-topic topology fallbacks, consumer enumeration, raw payload logging, and provider-specific durable delivery state.
