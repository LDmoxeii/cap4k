# HTTP Experience Transport Reset

## Outcome

HTTP is the smallest Integration Event provider for local demonstrations. It lets two Spring Boot
processes, or one self-routing process, exchange the canonical Integration Event envelope without a
message broker. It uses the same reliable Event ownership, Handler completion, delivery context,
and at-least-once semantics as the Runtime core.

HTTP is not a production broker. Its topology is static and one-to-one.

## Dependencies

- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-shared-transport-foundation`
- `runtime-reliable-event-state`
- `runtime-handler-contract`

## Route configuration

There is exactly one HTTP route map:

```yaml
cap4k:
  ddd:
    integration-event:
      http:
        routes:
          "[content.published]": http://localhost:8082
          "[media.processing.requested]": http://localhost:8083/context
```

- Each key is the stable `@IntegrationEvent.value` event name.
- Each value is one absolute HTTP or HTTPS base URI with a host. A path prefix is allowed; query and
  fragment components are not.
- The provider validates every configured route during startup. Blank keys, blank values, malformed
  URIs, unsupported schemes, or unusable route values fail provider enrollment deterministically.
- The final bound map contains one target per event name. There is no list target, fallback,
  broadcast, discovery, annotation-derived route, or alternate syntax.
- A missing route for an outbound payload is rejected before its reliable Event record is saved.
  Publish-time resolution remains a defensive check and must not silently select another target.
- The destination is the normalized base URI plus `/cap4k/integration-events`, with exactly one slash
  boundary and any configured base path preserved.
- A route may target the same application instance. Self-routing is not a separate mode.

## Outbound provider handoff

For a claimed reliable Integration Event the HTTP provider:

1. receives the core-owned canonical envelope;
2. resolves the one static route by stable event name;
3. performs one HTTP POST to the fixed endpoint using bounded connection and response waits;
4. reports publish success exactly once only for an HTTP 2xx response;
5. reports publish failure exactly once for non-2xx, connection failure, timeout, client execution
   failure, or another unknown handoff result.

The existing reliable Event coordinator owns retry, lease, token, attempt, and durable
acknowledgement. HTTP does not retain a second retry queue or mutate reliable state directly. A
process loss after the receiver succeeds but before durable sender acknowledgement may cause a
duplicate, as allowed by the at-least-once contract.

Temporary endpoint unavailability does not fail application startup. Live provider facts must not
claim health without an observed successful handoff; failures expose degraded/recovering state for
the Runtime registry while the reliable record remains retryable.

## Fixed inbound endpoint

- The Runtime owns one endpoint: `POST /cap4k/integration-events`.
- Other HTTP methods are rejected with non-2xx and do not decode or dispatch the request.
- The request body is the canonical Integration Event envelope. Query parameters and legacy HTTP
  metadata do not override envelope identity, type, origin, attempt, or context.
- A successful response is any adapter-selected 2xx. All deterministic decode/type/configuration
  failures and all local delivery failures return non-2xx.
- Safe response categories may distinguish malformed envelope, unknown event/no Handler, and local
  Handler failure. Responses and logs do not echo raw payload JSON.

## Inbound Handler completion

The receiver:

1. decodes and validates the canonical envelope;
2. resolves the payload class from the already validated inbound registration view;
3. installs decoded execution context and `ReliableEventDeliveryContext` with the application name
   as stable HTTP subscriber identity;
4. invokes matching local synchronous Integration Event Handlers through the shared dispatcher;
5. waits for every Runtime-managed scoped async Query/Capability operation started by those
   Handlers;
6. returns 2xx only when the entire local scope succeeds.

An unknown event name or event with no actual local Handler is a failed delivery. Any Handler or
managed scoped operation failure stops later local Handlers and returns non-2xx. Execution and
delivery contexts are cleared after success or failure.

## Duplicate delivery

The same envelope may be delivered more than once. Each request enters the same decode, context,
and Handler boundary again. The HTTP provider does not add an inbox, deduplication store, or
exactly-once claim; business idempotency remains the consumer's responsibility.

## Retired surface

The active HTTP transport contains no:

- subscriber property on `IntegrationEvent`;
- in-memory or JPA subscriber registry, subscriber table, or HTTP-JPA starter;
- subscribe/unsubscribe/callback-registration capability or management endpoint;
- subscriber/event listing endpoint;
- `eventName@registerUrl`, fan-out, discovery, loopback mode, or second route syntax;
- HTTP-owned reliable Event state machine or retry scheduler.

## Safety and diagnostics

- Canonical envelope and payload validation continue to reject persistence-bound Aggregate/Entity
  values.
- Logs and failure facts may contain event identity/type, normalized route identity, HTTP status,
  and safe provider failure category.
- Raw envelope bodies, `payloadJson`, business object serialization, credentials, and transport
  request objects are not written to failure diagnostics.
- Deterministic route, codec, catalog, and configuration failures are separated from temporary
  endpoint failures so invalid configuration cannot retry forever as a durable record.

## Non-goals

- Production-grade broker durability, global ordering, exactly-once, inbox/deduplication, framework
  DLQ, or downstream acknowledgement collection.
- Multiple HTTP targets, broadcast, dynamic registration, discovery, or production message-bus
  guarantees.
- RabbitMQ/RocketMQ provider implementation or a provider-independent HTTP abstraction beyond the
  existing shared Integration Event contracts.

## Acceptance

Focused tests must prove:

- bracket-key configuration binding and one event-to-one-base-URI resolution;
- startup rejection of blank/malformed/unsupported routes and pre-persistence rejection of a
  missing outbound route with no durable record;
- path/trailing-slash normalization, self-routing, and real fixed-endpoint HTTP delivery;
- positive completion only after 2xx and once-only failure for non-2xx, connection failure, timeout,
  and synchronous/asynchronous client failures;
- POST-only inbound handling, canonical envelope identity, no-Handler failure, Handler/scoped-child
  failure, and 2xx only after complete local success;
- delivery/execution context installation and cleanup on success and failure;
- duplicate request re-dispatch without a second state machine;
- honest provider state transitions and application survival during temporary endpoint failure;
- absence of subscriber/JPA/dynamic-management surfaces and absence of raw payload logging.
