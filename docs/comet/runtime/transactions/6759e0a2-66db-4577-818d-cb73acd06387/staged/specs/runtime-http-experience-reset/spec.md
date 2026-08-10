# HTTP Experience Transport Reset

## Outcome

HTTP is the smallest Integration Event transport for local demonstrations. It lets two Spring Boot processes, or one self-routing process, exchange the canonical Integration Event envelope without RabbitMQ or RocketMQ while using the same Runtime-owned reliable Event state, Handler completion, Provider State, and at-least-once semantics.

HTTP is not a production broker. Its topology is static and one-to-one.

## Dependencies

- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-shared-transport-foundation`
- `runtime-reliable-event-state`
- `runtime-handler-contract`
- the mainline Runtime Provider State contract

## Configuration and routing

There is exactly one route map under `cap4k.ddd.integration.event.http.routes`.

- Each key is the stable `@IntegrationEvent.value` event name.
- Each value is one absolute HTTP or HTTPS base URI with a host. A base path is allowed; query, fragment, and user-info components are rejected.
- The provider validates configured routes deterministically. Blank keys, blank values, malformed URIs, unsupported schemes, and unusable routes are rejected.
- One event name resolves to one target. There is no broadcast, fallback, discovery, annotation-derived destination, dynamic registration, list-valued route, or alternate route syntax.
- A missing outbound route is rejected before its reliable Event record is persisted. Publish-time route resolution remains a defensive check and never selects another destination.
- The destination is the normalized base URI plus the fixed `/cap4k/integration-events` path, with the configured base path preserved and one slash boundary.
- A route may point back to the same application. Self-routing is not a separate mode.

## Runtime Provider State

- HTTP registers exactly one provider with stable ID `integration-event-transport.http` through `com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry`.
- Registration uses the mainline contract and therefore begins as `RECOVERING` with category `enrolled`.
- HTTP does not define another registry, reporter, state enum, `UNKNOWN` initial state, compatibility layer, or convenience reporting API.
- Observed handoff progress and outcomes are reported through the mainline reporter: work may report `RECOVERING`, a successful 2xx handoff reports `HEALTHY`, and observed failures report `DEGRADED` with a safe category.
- The Spring-owned reporter registration is closed on application shutdown. Closing unregisters the provider fact and permits a later owner to register the same provider ID.
- Provider State is process-local operational evidence. It does not own broker behavior, retry, lease, token, attempt, or durable acknowledgement.

## Outbound handoff

For a claimed reliable Integration Event, HTTP:

1. receives the core-owned canonical Jackson envelope;
2. resolves the single static route by stable event name;
3. executes one HTTP POST to the fixed endpoint with bounded connect and response waits;
4. reports publish success exactly once only for an HTTP 2xx response;
5. reports publish failure exactly once for non-2xx, connection failure, timeout, client execution failure, codec failure, route failure, or another unknown handoff result.

The reliable Event coordinator owns retry, lease, token, attempt, and durable acknowledgement. HTTP has no second retry queue, durable state machine, or direct reliable-record mutation. A process loss after receiver success but before sender acknowledgement may produce a duplicate under the at-least-once contract.

Endpoint unavailability does not fail application startup. Startup enrollment remains `RECOVERING`; health is claimed only after observed successful handoff.

## Fixed inbound endpoint

- Runtime exposes one endpoint: `POST /cap4k/integration-events`.
- Other methods return non-2xx without decoding or dispatching the request.
- The request body is the canonical Integration Event envelope. Query parameters and legacy HTTP metadata cannot override envelope identity, type, origin, attempt, or execution context.
- Any adapter-selected 2xx is success. Deterministic decode/type/catalog failures and local Handler failures return non-2xx with safe categories that do not echo payload JSON.
- Unknown event names and events without an actual local Handler are failed deliveries.

## Delivery Context and Handler completion

- `ReliableEventDeliveryContext` contains only transport-neutral delivery facts: event ID, event name, published time, attempt, and redelivery hint.
- HTTP does not put subscriber identity, application name, destination URL, endpoint, route, queue, group, or other topology into `ReliableEventDeliveryContext` or `IntegrationEventDeliveryMetadata`.
- Decoded execution context and delivery context are installed only around the shared local Handler dispatch boundary, not around transport interceptors or routing work.
- Matching local Integration Event Handlers run through the shared synchronous dispatcher.
- Runtime-managed scoped asynchronous Query or Capability work started by a Handler must complete before the Handler scope and HTTP response complete.
- All local work succeeding returns 2xx. Any Handler or managed scoped operation failure stops later local Handlers and returns non-2xx.
- Execution and delivery contexts are cleared after both success and failure.

## Duplicate delivery

The same envelope may be delivered more than once. Every request enters the same decode, context, and Handler boundary again. HTTP does not add an inbox, deduplication store, or exactly-once claim; business idempotency remains the consumer's responsibility.

## Retired surface

The active HTTP transport contains no:

- subscriber property on `IntegrationEvent`;
- subscriber identity or transport topology in the shared Delivery Context;
- in-memory or JPA subscriber registry, subscriber table, or HTTP-JPA starter;
- subscribe/unsubscribe/callback-registration capability or management endpoint;
- subscriber/event listing endpoint;
- `eventName@registerUrl`, fan-out, discovery, loopback mode, or second route syntax;
- HTTP-owned reliable Event state machine or retry scheduler;
- parallel Runtime Provider State implementation or `UNKNOWN` compatibility state.

## Safety and diagnostics

- Canonical envelope and payload validation reject persistence-bound Aggregate/Entity values.
- Logs and failure facts may contain event identity/type, HTTP status, provider ID, and safe failure category.
- Raw envelope bodies, `payloadJson`, business serialization, credentials, destination URLs, endpoint topology, and transport request objects are not written to Delivery Context or unsafe diagnostics.
- Deterministic route, codec, catalog, and configuration failures remain distinguishable from temporary endpoint failures.

## Non-goals

- Production broker durability, global ordering, exactly-once, inbox/deduplication, framework DLQ, or downstream acknowledgement collection.
- Multiple HTTP targets, broadcast, dynamic registration, discovery, or production message-bus guarantees.
- RabbitMQ/RocketMQ behavior changes.
- A provider-independent HTTP abstraction beyond the shared Runtime contracts.

## Acceptance

Focused tests must prove:

- one static route per event, route validation, path normalization, pre-persistence missing-route rejection, and real self-routing;
- fixed POST-only inbound delivery using the canonical envelope;
- 2xx-only success and exactly-once failure for non-2xx, connection failure, timeout, and client failures;
- Handler/scoped-child completion, no-Handler failure, duplicate redispatch, and context cleanup;
- registration of `integration-event-transport.http`, initial `RECOVERING/enrolled`, observed state transitions, close/unregister, and same-ID re-registration;
- absence of the PR-local `com.only4.cap4k.ddd.core.runtime` Provider State implementation;
- absence of subscriber/application/URL/endpoint/topology fields from shared Delivery Context and metadata;
- absence of subscriber/JPA/dynamic-management surfaces and raw payload logging;
- full repository Gradle check and fresh Comet verification evidence after rebasing onto the PR #180 master baseline.