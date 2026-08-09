# Outcome

Complete the HTTP Integration Event provider as the smallest no-broker experience transport. Two
Spring Boot processes, or one self-routing process, exchange the canonical Integration Event
envelope through one static event-name route and one fixed POST endpoint. Provider success is
reported only after the receiver has completed its local synchronous Handler scope.

# Scope

- Finish the single `cap4k.ddd.integration-event.http.routes[eventName] -> baseUrl` configuration.
- Validate configured HTTP base URLs at startup and reject a missing route for an outbound payload
  before its reliable Event record is saved.
- Send the canonical Jackson envelope to `POST {baseUrl}/cap4k/integration-events` with bounded
  connection and response waits.
- Treat only HTTP 2xx as provider handoff success. Non-2xx, connection failure, and timeout resolve
  the once-only publish completion as failure so the existing reliable Event state machine retries.
- Make the fixed receiver endpoint POST-only and map decode/type/Handler failures to non-2xx.
- Return 2xx only after all matching synchronous local Handlers and Runtime-managed scoped async
  Query/Capability work complete.
- Keep `ReliableEventDeliveryContext` and execution context installed only for the local delivery
  scope and clear them on both success and failure.
- Expose honest live HTTP provider state without probing endpoints at startup or claiming health
  before an observed successful handoff.
- Add focused adapter, starter, route, reliable-boundary, self-routing, and real HTTP handoff tests.

# Non-goals

- Production broker durability, global ordering, exactly-once, inbox/deduplication, framework DLQ,
  or sender-side downstream acknowledgement tracking.
- Broadcast, multiple targets per event, route discovery, dynamic subscription, callback
  registration, a loopback mode, or another route syntax.
- Restoring subscriber annotation fields, HTTP subscriber registries, HTTP-JPA persistence, or
  retired management endpoints.
- A second HTTP retry scheduler, reliable Event state machine, envelope, or delivery context.
- RabbitMQ/RocketMQ route, confirmation, subscription, or reconnect implementation.
- Creating or rewriting GitHub backlog issues as part of this implementation branch.

# Acceptance examples

- Given `routes[content.published] = http://localhost:8082`, publishing `content.published` performs
  exactly one POST to `/cap4k/integration-events`; the reliable Event is acknowledged only after the
  receiver returns 2xx.
- A route may point back to the same process. Self-routing uses the same route resolver, HTTP client,
  endpoint, envelope, and Handler completion boundary as a two-process delivery.
- Blank or malformed configured base URLs fail application startup. A payload whose event name has
  no configured route fails before `EventRecordRepository.save`, leaving no durable poison record.
- A non-2xx response, connection refusal, or response timeout invokes publish failure exactly once
  and leaves retry/attempt ownership to the existing reliable Event coordinator.
- A non-POST request, malformed envelope, unknown event name/no local Handler, payload decode error,
  Handler exception, or failed scoped async operation returns non-2xx and never reports delivery
  success.
- A valid POST returns 2xx only after the full local Handler scope succeeds. Context accessors are
  empty after both successful and failed requests.
- Repeating the same valid envelope dispatches it again through the same boundary. Business
  idempotency remains the application's responsibility.
- Logs and durable failure facts may contain safe event/route categories but never raw business
  payload JSON.

# Constraints and invariants

- The implementation starts from merged shared foundation PR #177, commit `e44424d4`.
- `@IntegrationEvent.value` remains the only logical event name. Provider topology stays in HTTP
  configuration.
- `IntegrationEventEnvelope`, `IntegrationEventEnvelopeCodec`, and
  `IntegrationEventPublishCompletion` remain the only wire and terminal completion boundaries.
- Reliable claim, lease, token, retry snapshot, acknowledgement, and failure transitions remain
  Runtime-owned.
- Handler methods remain synchronous; `Mediator` enqueue/schedule/delay operations do not extend the
  current inbound acknowledgement scope.
- Route and codec configuration errors are deterministic and must not become endlessly retrying
  durable records.
- Temporary endpoint unavailability must not fail application startup.
- Breaking cleanup is allowed; no compatibility aliases are required.
- No dedicated GitHub issue currently owns this Batch 4 slice. The merged canonical Runtime specs
  and roadmap are the confirmed implementation source. Open issue #103 is an older investigation
  and must not reopen the later confirmed HTTP/event-name decisions.

# Decisions

- The receiver is the single fixed POST endpoint `/cap4k/integration-events`.
- One stable event name resolves to one HTTP base URL. Base URL path prefixes are preserved and the
  fixed endpoint is appended with one slash boundary.
- A usable route is an absolute `http` or `https` base URI with a host and without query or fragment.
- All configured route values are validated during provider startup. Missing per-payload routes are
  validated again before reliable record persistence and defensively at publish time.
- The provider uses bounded HTTP waits, but timeout implementation/default tuning is provider-owned
  and does not create a second route/configuration model.
- The public HTTP acknowledgement contract is only 2xx versus non-2xx; exact non-2xx status codes
  and safe response categories are adapter implementation details.
- Live state cannot claim `HEALTHY` before an observed positive HTTP handoff. Temporary failures are
  represented as degraded/recovering facts without probing routes during startup.
- Duplicate requests are delivered again; the transport does not add an inbox or deduplication key.
- On 2026-08-09, the user explicitly confirmed this complete Shape contract and authorized the
  change to enter Build.

# Open questions

- None.

# Verification expectations

- Focused unit tests for URL validation/normalization, missing route rejection before repository
  save, once-only completion, non-2xx, connection failure, timeout, and safe diagnostics.
- Starter web tests for POST-only mapping, success/non-2xx responses, no Handler, Handler failure,
  context cleanup, duplicate delivery, and configuration binding with bracketed event-name keys.
- At least one real local HTTP test proving fixed-endpoint delivery, including self-routing or two
  independently started application contexts.
- Existing reliable Event, Handler scope, envelope, provider composition, and retired-surface tests
  remain green.
- Static scans prove there is no dynamic subscriber/JPA surface, second route syntax, raw payload
  logging, or HTTP-owned retry state machine.
- Run focused Gradle tests, full `./gradlew check`, `git diff --check`, and `comet native check`.
