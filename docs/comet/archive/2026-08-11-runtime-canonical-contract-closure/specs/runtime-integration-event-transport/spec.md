# Integration Event Transport Contract

## Outcome

The Runtime provides one transport-neutral Integration Event delivery contract. A reliable
outbound Event is handed to exactly one selected Transport provider, and an inbound delivery is
acknowledged only after the receiving application has completed its local synchronous Handler
scope. The sender observes provider handoff only; it does not discover, enumerate, or wait for all
downstream consumers.

This contract completes the provider-independent boundary. HTTP, RabbitMQ, and RocketMQ define
only their provider facts in the child specifications; none of them may create a second reliable
Event state machine.

## Depends on

- `runtime-reliable-event-state`
- `runtime-handler-contract`
- `runtime-provider-composition`
- `runtime-integration-event-core`
- `runtime-jackson-only`

## Provider composition and runtime state

- An application has at most one active outbound Integration Event Transport provider.
- When the Integration Event capability is enabled, the required provider slot must have exactly
  one owner. A missing required provider, multiple providers, duplicate provider identity, or
  duplicate route/registration is a deterministic startup or enrollment failure.
- Providers are not broadcast, chained, or selected by `getIfUnique()` fallback behavior.
- Temporary broker or endpoint unavailability does not make the application fail to start. The
  provider reports `DEGRADED` or `RECOVERING`, reliable records remain retryable, and subscribers
  continue reconnecting where the provider supports it.
- Static Agent API facts describe declared capabilities only. They do not probe a broker, database,
  route, or endpoint.
- `RuntimeProviderStateRegistry` is the sole current live provider-state source. No Actuator endpoint
  currently exists. A future optional Actuator endpoint may only provide a read-only projection by
  delegating every read directly to `RuntimeProviderStateRegistry.snapshot()`; it must not cache,
  merge, derive, or become a second source of provider state.

## Event identity and routing

- `@IntegrationEvent.value` is the only stable logical `eventName`.
- The annotation does not contain subscriber, destination, URL, exchange, routing key, topic, tag,
  queue, or consumer-group defaults.
- Deployment topology is supplied by the selected provider's `routes[eventName]` configuration.
  There is one route syntax per provider, and there is no compatibility or inferred fallback route.
- Inbound subscriptions are derived only from real local Integration Event Handler methods. A
  declared event type with no real Handler does not create a subscription.
- The same `eventName` must resolve to one payload type in the local catalog. Contradictory type
  registrations fail deterministically before delivery.

## Outbound handoff and sender knowledge

The sender owns one reliable Event record and one provider handoff result. It does not own a
per-consumer delivery table.

```text
claim reliable Event
    -> encode the canonical envelope
    -> invoke the selected provider
    -> await the provider's supported positive handoff confirmation
    -> acknowledge the reliable Event
```

- A provider may complete synchronously or asynchronously, but every normal and failed path must
  resolve the shared publish completion exactly once. The first terminal result wins; duplicate
  callbacks and callback exceptions cannot reopen or invert the result.
- Returning from a client send method is not, by itself, a confirmation unless the provider's
  contract explicitly defines it as the positive handoff boundary.
- Explicit provider failure, timeout, unavailable/unknown handoff, or process loss before the
  durable acknowledgement leaves the Event retryable under the existing claim/lease state
  machine. A duplicate handoff is an allowed at-least-once outcome.
- Successful handoff means that the selected Transport accepted the message according to its
  provider contract. It does not mean that every downstream consumer has processed it.

## Inbound acknowledgement and consumer independence

Each consumer application owns an independent local completion boundary:

```text
decode envelope
    -> install ReliableEventDeliveryContext
    -> evaluate Handler conditions
    -> invoke matching synchronous Handlers in local @Order order
    -> join all Runtime-managed async Query/Capability work started by each Handler
    -> all succeed: acknowledge the provider delivery
```

- Any Handler or managed scoped Query/Capability failure stops later local Handlers and produces no
  broker ack or no HTTP 2xx response. The provider's retry mechanism receives the failure.
- The sender never collects downstream acknowledgements. Multiple consumer applications use their
  own queue/group identity and retry boundary.
- `@Order` is local dispatch ordering only. No global order is promised across messages, instances,
  queues, groups, or transports.
- HTTP inbound delivery, persisted Domain Event delivery, and broker inbound delivery share this
  Handler completion boundary. Ordinary in-process Domain Events have no reliable transport
  context.

## Reliable delivery context and safety

Inbound and reliable local Event delivery install the shared `ReliableEventDeliveryContext` for the
current scope. Its complete public data is stable event identity, stable logical event name,
published time, an exact attempt when the envelope or provider can supply one, and a
non-authoritative `UNKNOWN`/`FIRST`/`REDELIVERED` hint. Origin attribution remains encoded execution
context rather than an additional delivery-context field.

It must not expose provider topology such as exchange, routing key, topic, queue, consumer group,
HTTP URL, subscriber identity, transport message objects, or an assertion that a delivery is
definitely a duplicate.

The canonical Jackson envelope rejects persisted `Aggregate`, `Entity`, and other
persistence-bound instances before serialization or durable reliable handoff. Failure diagnostics
contain event identity/type and a safe failure category; raw business payload JSON is never logged
or persisted as failure data.

## Non-goals

- Exactly-once delivery, global consumer ordering, or sender-side downstream tracking.
- Inbox persistence, framework DLQ, per-Handler progress, or a framework deduplication store.
- A generic task/scheduler framework or provider-specific public scheduling API.
- HTTP broadcast, discovery, dynamic subscription, or production message-bus guarantees.
- A second envelope, delivery context, retry loop, provider-owned reliable Event state machine, or
  provider-state source.
- A current Actuator Runtime endpoint.

## Acceptance

Focused provider and Runtime tests must prove:

- one-provider selection and deterministic missing/duplicate/conflicting configuration failures;
- degraded/recovering status for temporary external unavailability without false healthy claims;
- event-name-only annotation identity and explicit provider route selection;
- one canonical envelope round-trip and persistence-bound payload rejection;
- one terminal publish completion at the callback boundary, with duplicate terminal callbacks
  ignored;
- positive provider handoff before outbound reliable acknowledgement;
- independent consumer queue/group identities and no sender-side consumer enumeration;
- acknowledgement only after all matching synchronous Handlers and managed scoped async work
  complete;
- failure/no-ack or non-2xx, duplicate delivery, context installation/cleanup, and safe diagnostics;
- static scans showing no dynamic subscriber registry, no provider-specific delivery state machine,
  no second provider-state source, and no raw payload in transport failure logs.

Provider-specific confirmation and failure evidence is defined by:

- `runtime-http-experience-reset`
- `runtime-rabbitmq-transport`
- `runtime-rocketmq-transport`
