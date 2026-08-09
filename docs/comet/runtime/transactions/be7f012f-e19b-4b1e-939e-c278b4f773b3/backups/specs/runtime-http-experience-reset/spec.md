# HTTP Experience Transport Reset

## Outcome

HTTP is the smallest Integration Event transport for local demonstrations. It allows two Spring
Boot processes to exchange events without RabbitMQ or RocketMQ, while keeping the same reliable
Event, envelope, Handler completion, and retry semantics used by the Runtime core.

HTTP is not a production broker. Its topology is deliberately static and one-to-one.

## Depends on

- `runtime-integration-event-core`
- `runtime-integration-event-transport`
- `runtime-reliable-event-state`
- `runtime-handler-contract`

## Configuration and topology

There is exactly one HTTP route map:

```yaml
cap4k:
  ddd:
    integration-event:
      http:
        routes:
          "[content.published]": http://localhost:8082
          "[media.processing.requested]": http://localhost:8083
```

- A route key is the stable `eventName`; its value is the destination base URL.
- One event has one HTTP target. There is no list-valued route, broadcast, discovery, dynamic
  registration, or `eventName@registerUrl` syntax.
- The receiver uses the Runtime-owned fixed endpoint `POST {baseUrl}/cap4k/integration-events`.
  Trailing-slash normalization belongs to the adapter; the endpoint path is not a second user
  configuration surface.
- A process may route an event to its own base URL. Self-routing is the supported local
  “自产自销” demonstration and uses the same route map as every other HTTP target.
- Missing, blank, malformed, or conflicting routes are rejected deterministically before a reliable
  Event can be accepted for delivery. The sender must never leave a record waiting for a route that
  cannot be resolved.

## Outbound delivery

For a claimed reliable Event, the HTTP provider:

1. encodes the canonical Integration Event envelope with Runtime Jackson;
2. resolves the single static route;
3. posts the envelope to the fixed receive endpoint;
4. treats an HTTP `2xx` response as successful handoff;
5. treats non-`2xx`, connection failure, and timeout as provider failure and lets the reliable Event
   state machine retry it.

The response is the receiving application's local completion result. The sender does not know about
any other service and does not wait for downstream consumers beyond this one target.

## Inbound delivery and acknowledgement

- The receiver decodes the same envelope, resolves the payload through its local event catalog,
  installs `ReliableEventDeliveryContext`, and dispatches only actual local Integration Event
  Handlers.
- Every matching Handler must return synchronously. Runtime-managed `queries.askAsync*` and
  `capabilities.callAsync*` work started in a Handler scope must finish before the response is sent.
- All matching Handlers succeeding produces HTTP `2xx`. Any Handler or managed scoped operation
  failing produces non-`2xx`; later local Handlers are not invoked and the sender retries.
- An event with no matching local Handler is an explicit failed delivery, not a silent success.
- Duplicate requests are allowed by the at-least-once contract and must execute through the same
  context/Handler boundary; business idempotency remains the application's responsibility.

## Removed surface

The HTTP experience transport must not retain or introduce:

- subscriber fields on `IntegrationEvent`;
- in-memory or JPA subscriber registries and subscriber tables;
- subscribe/unsubscribe/callback-registration capabilities or HTTP management endpoints;
- route discovery, dynamic fan-out, a separate loopback mode, or a second route syntax;
- a transport-owned reliable Event state machine.

## Non-goals

- Broker durability, production-grade delivery guarantees, or global ordering.
- HTTP broadcast or multi-consumer coordination. Use RabbitMQ or RocketMQ for independent
  multi-service consumers.
- A second inbox, deduplication store, or HTTP-specific retry scheduler.

## Acceptance

Focused tests must cover:

- one static route per event, including self-routing and two-process delivery;
- missing/blank/malformed/conflicting route rejection;
- canonical envelope round-trip and context installation/cleanup;
- success only after synchronous Handler completion and all managed scoped async work;
- Handler failure, no-handler failure, non-`2xx`, connection failure, timeout, and retry response;
- duplicate request handling without a second state machine;
- absence of subscriber registry/JPA subscription persistence and absence of raw payload logging.
