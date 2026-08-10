# Runtime Shared Transport Foundation

## Outcome

The Runtime exposes one shared Integration Event foundation for all transport
providers. An Integration Event has one stable logical `eventName`, one
canonical Jackson envelope, one provider-level terminal completion callback,
and one local catalog/handler view for inbound registration. Topology and
acknowledgement remain provider-owned.

## Annotation and identity

- `@IntegrationEvent.value` is the only public annotation property.
- The value is the stable logical event name used by the local catalog and by
  provider route configuration.
- Subscriber, destination, URL, exchange, routing key, topic, tag, queue, and
  consumer-group values do not appear on the annotation.
- A blank value is invalid when a payload is registered, released, converted
  into a reliable record, or persisted for Integration Event delivery.
- Eager attachment rejects a blank event name before attachment. Lazy
  attachment remains lazy, but the resolved payload is rejected before record
  creation or repository save.
- The durable JPA Event carrier rejects blank `eventType` values at its
  persistence lifecycle boundary so lower-level paths cannot create a durable
  invalid record.

## Canonical envelope and completion

- `IntegrationEventEnvelope` and `IntegrationEventEnvelopeCodec` in `ddd-core`
  remain the only wire representation.
- The envelope carries event identity/type, origin service, published time,
  optional exact delivery attempt, encoded execution context, and JSON payload.
- The codec performs Jackson shape/metadata validation and rejects persisted
  Aggregate/Entity payloads. Raw business payload JSON is not copied into
  failure facts or diagnostics.
- `IntegrationEventPublishCompletion` accepts only the first terminal
  provider success/failure transition. Later callbacks and callback exceptions
  are contained and cannot reopen or invert the result.
- Completion means provider handoff only; it is not a global downstream
  acknowledgement.

## Catalog-derived inbound registration

- The active application catalog is the source of payload types.
- The shared registration view contains only Integration Event payloads that
  have at least one real local synchronous Handler method.
- A catalog entry with no matching Handler does not create a broker queue,
  consumer group, or HTTP subscription.
- Multiple Handler methods for the same payload remain local dispatch metadata;
  no registry or per-handler transport state is created.
- Provider adapters consume this view and derive their own queue/group/endpoint
  identity from application configuration and provider topology.
- An active transport materializes and validates its registration view during
  provider enrollment. Blank event names and one event name mapped to different
  payload classes fail deterministically before message delivery begins.
- Core applications without an active Integration Event transport do not
  perform provider enrollment validation merely because the catalog/view beans
  exist.

## Common provider boundary

The shared runtime boundary provides:

1. stable event-name lookup from the catalog;
2. resolved provider route identity (event name plus provider-owned destination
   facts);
3. deterministic exactly-one provider selection through
   `RuntimeProviderComposition`;
4. a provider state slot that may report declared `HEALTHY`, `DEGRADED`, or
   `RECOVERING` facts without probing external systems from the static Agent
   manifest.

The shared layer does not parse provider-specific route values or perform
network/broker operations.

## Retired HTTP surface

The active HTTP experience no longer contains:

- `HttpIntegrationEventSubscriberRegister` or its in-memory/JPA
  implementations;
- `EventHttpSubscriber` persistence, repository, SQL table, or JPA starter
  auto-configuration;
- subscribe/unsubscribe capabilities and management endpoints;
- event/subscriber listing endpoints;
- `eventName@registerUrl` discovery syntax or callback fan-out.

The later HTTP provider branch owns the fixed receive endpoint and the single
static `routes[eventName] -> baseUrl` map.

## Provider independence

- Each application has at most one active Integration Event publisher provider.
- Missing/duplicate/ambiguous provider ownership is a deterministic startup
  failure; there is no fallback publisher and no broadcast composition.
- The sender does not know all consumers and does not maintain a per-consumer
  delivery table.
- Inbound reliable delivery context is installed by the selected provider and
  is cleared after the local synchronous Handler scope. Event identity, logical
  event name, and publication time come from the canonical envelope; the
  provider may supply only its exact attempt and non-authoritative redelivery
  hint. Subscriber identity and provider topology are not exposed as shared
  business context.

## Verification

- Core tests prove eager and lazy blank-name Integration Events fail before
  `EventRecordRepository.create()` or `save()`.
- JPA tests prove payload conversion rejects blank names and direct persistence
  cannot insert a record with blank `eventType`.
- HTTP starter context tests prove active transport enrollment fails for blank
  event names and duplicate names mapped to different payload classes.
- Core-only context tests prove provider enrollment validation is not imposed
  when no Integration Event transport is active.

## Non-goals

- Provider-specific routes, SDK confirmation/ack semantics, reconnect behavior,
  or HTTP status mapping.
- Exactly-once, global ordering, inbox/deduplication, framework DLQ, or a
  generic scheduler/task framework.
- Strategic DDD decisions or Analyzer-to-Generator feedback.
