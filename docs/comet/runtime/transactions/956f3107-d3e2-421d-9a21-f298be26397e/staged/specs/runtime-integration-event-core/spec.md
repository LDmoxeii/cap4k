# Integration Event Core Contract

## Outcome

The Runtime exposes one transport-neutral Integration Event envelope and one Jackson-backed codec. Outbound `EventRecord` values are encoded once by the core contract; inbound adapters decode the same representation before catalog-driven payload resolution and synchronous handler dispatch. Every built-in Integration Event provider resolves its publish callback exactly once at the provider hand-off boundary.

## Envelope

The canonical envelope JSON object contains these fields:

- `eventId`: non-blank stable event identity.
- `eventType`: non-blank logical Integration Event type used by the event catalog and routing layer.
- `originService`: non-blank sender service name.
- `publishedAt`: ISO-8601 UTC instant.
- `deliveryAttempt`: positive exact attempt when known; `null` when the provider cannot supply an exact value.
- `executionContext`: deterministic array of versioned encoded execution-context elements.
- `payloadJson`: deterministic JSON text for the Integration Event payload.

The codec preserves explicit null/default payload properties according to `RuntimeJson`. Envelope property ordering is deterministic. The codec validates required metadata, context shape, payload JSON shape, and payload object safety before returning a decoded envelope.

## Core API

`IntegrationEventEnvelope` is an immutable value object. `IntegrationEventEnvelopeCodec` provides:

- `encode(event: EventRecord): String`, which requires an `@IntegrationEvent` payload and serializes only its JSON representation;
- `decode(json: String): IntegrationEventEnvelope`, which validates envelope metadata and context JSON without reflecting arbitrary payload classes;
- `payloadJson(envelope: IntegrationEventEnvelope, eventClass: Class<*>): Any`, which resolves the event class from the caller-owned `EventTypeCatalog` and decodes through `RuntimeJson`.

`IntegrationEventPublishCompletion` is the shared once-only terminal boundary for `IntegrationEventPublisher.PublishCallback`. It accepts the first success or failure transition, marks completion before invoking callback code, ignores duplicate transitions, and logs/swallow callback exceptions without invoking the opposite callback.

## Provider completion

- HTTP success is resolved after all registered subscribers return from their capability calls. An empty subscriber set is an explicit failure.
- RabbitMQ success is resolved only after `convertAndSend` returns. Its message post-processor only writes message identity and timestamp metadata.
- RocketMQ success is resolved from the SDK success callback; synchronous `asyncSend` throws and asynchronous error callbacks resolve failure.
- All providers protect envelope encoding, destination resolution, registration lookup, executor submission, message construction, and send invocation so no normal or failed path leaves the callback unresolved.
- Success and failure are mutually exclusive and each is invoked at most once.

## Origin context and subscriber identity

Origin attribution uses the existing encoded execution-context elements and the shared Runtime execution-context JSON helper. Each inbound adapter supplies an explicit stable `subscriberIdentity` in delivery metadata. The identity is scoped to the configured destination/subscription and remains stable across redelivery; broker delivery tags, offsets, and callback URLs are not used as subscriber identity.

The sender does not enumerate consumers and there is no global acknowledgement. Every destination/subscriber retains its own acknowledgement and retry boundary under the existing reliable Event state machine.

## Inbound and outbound symmetry

- Outbound publishers receive the core envelope and map only provider metadata, route, and provider confirmation into the existing `IntegrationEventPublisher.PublishCallback`.
- Inbound adapters decode the same envelope, resolve payload type through their `EventTypeCatalog`, install the decoded execution context and `ReliableEventDeliveryContext`, and dispatch synchronously.
- Transport-specific state machines are forbidden; adapters use the already-landed reliable Event coordinator and acknowledgement boundary.

## Safety

- Persisted `Aggregate`, `Entity`, or other persistence-bound instances are rejected before envelope serialization.
- Malformed metadata, context, or payload causes a safe decoding/validation exception containing only event identity/type and failure category; raw business payload JSON is not included.
- The codec never infers business truth from provider metadata and never guesses an exact delivery attempt when none is available.

## Non-goals

- Broker route redesign, publisher confirm implementation, consumer acknowledgement implementation, and HTTP experience simplification remain transport-specific follow-up contracts.
- No inbox persistence, deduplication store, global event ordering, or automatic retry scheduler is introduced.
