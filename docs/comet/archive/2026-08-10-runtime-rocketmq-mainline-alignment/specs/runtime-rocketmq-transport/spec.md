# RocketMQ Transport Contract

## Outcome

RocketMQ is a broker-backed Integration Event provider built on the unified Runtime transport boundary. It contributes explicit topic/tag routes, deterministic per-application Consumer Groups, actual SDK send-result semantics, recoverable subscription enrollment, broker acknowledgement after the complete local Handler scope, and safe aggregate provider-state facts. Reliable ownership, retry, lease, envelope, completion, provider registry, and delivery context remain shared Runtime contracts.

## Dependencies

- `runtime-shared-transport-foundation`
- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-provider-composition`
- `runtime-reliable-event-state`
- `runtime-handler-contract`

## Route configuration and pre-persistence validation

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
- One immutable route resolver validates the active route table and is used by enrollment, persistence guards, and the defensive publisher boundary.
- A highest-precedence `IntegrationEventInterceptor` resolves the route during eager `onAttach` and defensive `prePersist` processing.
- Missing or invalid routes fail before `EventRecordRepository.save` is invoked. The publisher still resolves the route defensively before SDK handoff.
- Duplicate logical names, contradictory payload mappings, and invalid provider topology fail deterministically before partial subscriber activation.
- The historical arbitrary-consumer extension and annotation/property topology fallbacks do not remain as compatibility paths.

## Subscription identity and Consumer Group

- Inbound subscriptions are derived only from actual local Integration Event Handler registrations supplied by the shared registration view.
- Each `(applicationName, eventName)` pair derives one deterministic, collision-resistant, RocketMQ-legal, bounded Consumer Group identity.
- Instances with the same pair share the Consumer Group and compete for messages.
- Different application names receive independent groups, and distinct event names in one application receive distinct groups.
- Consumer Group and application/subscriber identity remain provider enrollment facts. They are not written into the shared delivery context.
- No global subscriber registry, consumer enumeration, broadcast list, dynamic subscription API, or per-consumer durable delivery state is introduced.
- RocketMQ message order and retry behavior do not create a global Runtime ordering guarantee; only local Handler `@Order` is supported.

## Consumer enrollment and recovery lifecycle

- All active registrations, routes, Consumer Groups, and subscription specifications are materialized and validated before the first consumer start.
- A consumer that starts successfully becomes the single active consumer for its subscription. Later recovery triggers do not create a duplicate healthy consumer.
- Temporary nameserver or broker unavailability during initial start does not fail application startup. The failed instance is shut down and discarded, provider state becomes `DEGRADED` or `RECOVERING`, and the subscription remains pending for later recovery.
- Recovery creates a fresh consumer for each pending subscription and attempts startup again. A successful replacement becomes active and contributes healthy subscriber evidence.
- Pending initial enrollment is retried with the positive fixed delay configured by `cap4k.ddd.integration-event.rocketmq.recovery-interval`; the default is five seconds.
- Deterministic configuration, event-type, route, topology, duplicate-registration, and invalid-client-state failures are not retried as temporary external unavailability.
- After a consumer starts successfully, ordinary connection recovery remains owned by the RocketMQ SDK; cap4k does not run a competing reconnect loop for that healthy instance.
- Adapter shutdown is terminal and idempotent: it stops pending recovery, prevents later consumer creation, shuts down active instances, and releases provider-owned resources.

## Publisher handoff and completion

For each claimed reliable outbound Integration Event, the provider:

1. resolves the explicit topic/tag route by envelope event name;
2. encodes the canonical Jackson envelope;
3. invokes the RocketMQ SDK with the configured delivery timeout;
4. resolves the shared `IntegrationEventPublishCompletion` exactly once from the actual SDK result.

- `SendStatus.SEND_OK` is the only positive handoff result.
- Every other SDK status, null or malformed result, timeout, synchronous exception, and asynchronous failure callback is a provider failure.
- A positive result acknowledges only the selected provider handoff. It does not acknowledge downstream consumers.
- Duplicate callbacks and callback exceptions cannot reopen, invert, or reclassify the attempt.
- Provider failure leaves the reliable Event retryable under the shared claim/lease/retry state machine. Process loss after broker handoff but before durable acknowledgement may produce an allowed at-least-once duplicate.

## Consumer acknowledgement and delivery context

For each broker delivery, the provider:

1. decodes and validates the canonical envelope;
2. maps only SDK attempt/redelivery metadata into the non-authoritative shared metadata;
3. installs execution context and `ReliableEventDeliveryContext`;
4. invokes the shared local Integration Event dispatcher;
5. returns the provider consume result only after the dispatcher completes.

- The complete delivery-context data is event ID, stable event name, published time, optional exact attempt, and `UNKNOWN`/`FIRST`/`REDELIVERED` hint.
- Subscriber identity, application identity, topic, tag, Consumer Group, nameserver, route identity, and RocketMQ message objects are never exposed in the delivery context.
- The dispatcher evaluates Handler conditions, invokes matching synchronous Handlers in local `@Order`, and joins every Runtime-managed `queries.askAsync*` and `capabilities.callAsync*` operation started inside each Handler scope.
- Full local success returns `CONSUME_SUCCESS`.
- Any codec, Handler, or managed scoped-operation failure returns `RECONSUME_LATER`; later local Handlers are not invoked.
- Delivery and execution contexts are cleared after success or failure.

## Unified provider state

- RocketMQ registers exactly one mainline provider-state reporter with stable provider ID `integration-event-transport.rocketmq` through `com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry`.
- The starter owns registration and closes the reporter during bean destruction, removing the provider fact from the registry.
- Publisher and subscriber components report through an aggregate coordinator so one healthy component cannot mask another component's degraded or recovering fact.
- Initial enrollment, deferred recovery, renewed recovery work, positive publisher handoff, successful consumer start/activity, and temporary failures contribute safe `RECOVERING`, `HEALTHY`, or `DEGRADED` facts.
- Static Agent API manifests declare capability only and do not probe RocketMQ.
- RocketMQ does not introduce a second registry, persisted provider state, health-history store, broker probe, or provider-specific reliable Event record.

## Safe diagnostics

- Publisher, subscriber, enrollment, recovery, and shutdown paths never pass arbitrary `Throwable` objects directly to logs.
- Diagnostics never record exception messages, payload values, payload JSON, envelope JSON, external response bodies, topic, tag, Consumer Group, nameserver, or other provider topology.
- Allowed diagnostic facts are the stable provider ID, safe failure category, exception/failure type, event ID, and stable logical event name where that identity is already available.
- Callback containment and shutdown containment preserve the primary result while emitting only the same safe fields.

## Non-goals

- Exactly-once delivery, global ordering, inbox/deduplication, framework DLQ, per-Handler progress, or sender-side collection of downstream acknowledgements.
- A RocketMQ-specific reliable Event record, retry loop, lease model, result repository, public scheduler, or generic task framework.
- Dynamic arbitrary consumers, inferred/default routes, compatibility aliases, user-supplied Consumer Groups, or topology in business context.
- A second reconnect loop for consumers that have already started successfully.
- Changes to HTTP or RabbitMQ provider behavior.

## Acceptance

Focused adapter, starter, and shared Runtime tests must prove:

- explicit topic/tag resolution, deterministic full-set validation, eager attachment rejection, and defensive pre-persistence rejection with zero repository `save` calls;
- stable and distinct Consumer Group identities for same application/event, different applications, and different event names without delivery-context leakage;
- `SEND_OK` as the only positive handoff result, with non-positive/null/unknown status, timeout, synchronous throw, asynchronous failure, and duplicate callback coverage;
- initial temporary start failure leaves the application alive and subscription pending; later recovery creates and starts one replacement consumer; healthy consumers are not duplicated; shutdown prevents later recovery and releases active consumers;
- aggregate provider-state transitions and reporter deregistration under the stable `integration-event-transport.rocketmq` identity;
- reliable acknowledgement only after the positive SDK result and retryability for every failed handoff;
- canonical envelope decode, attempt/redelivery mapping, transport-neutral delivery-context installation and cleanup;
- `CONSUME_SUCCESS` only after every matching Handler and managed scoped async operation completes, and `RECONSUME_LATER` with later-Handler short circuit on failure;
- safe diagnostics for publisher, subscriber, recovery, and shutdown paths, plus static removal of arbitrary consumer configuration, legacy topology fallbacks, the PR-owned provider-state registry, `subscriberIdentity`, raw payload/envelope logging, throwable logging, and provider-specific durable delivery state.
