# Outcome

Reliable Domain Event and inbound Integration Event handlers can inspect one
transport-neutral, immutable delivery context while they execute. The context
survives through the scoped asynchronous Query and Capability work owned by the
handler, then disappears deterministically on every exit path.

# Scope

- Add a public read-only `ReliableEventDeliveryContext` and a public accessor
  with strict and nullable lookup operations.
- Install the context only around handler dispatch for persisted/deferred Domain
  Events and inbound HTTP, RabbitMQ, and RocketMQ Integration Events.
- Preserve the context in PR #158's locally propagated ExecutionContext snapshot
  without defining a codec, so scoped async Query/Capability work can observe it
  but the value cannot cross a transport boundary as generic attribution.
- Persist and transport one stable original publication timestamp for reliable
  records. Project exact delivery attempt only where the owner has authoritative
  attempt data; otherwise omit it and expose only a non-authoritative hint.
- Suppress any ambient reliable-delivery context while dispatching an ordinary
  synchronous Domain Event, including a synchronous event nested inside a
  reliable handler.
- Add focused owner tests and repository-wide stale-surface evidence.

# Non-goals

- Do not change Integration Event routes, attach/detach, publisher selection,
  reliable-record state transitions, archive policy, or the HTTP subscriber
  registry.
- Do not expose exchange, routing key, topic, queue, consumer group, HTTP URL, or
  any transport object through the context.
- Do not add inbox persistence or claim authoritative deduplication.
- Do not preserve aliases, deprecated entrypoints, dual implementations,
  fallback timestamp codecs, or compatibility bridges.
- Do not repair any other Runtime audit finding.

# Acceptance examples

- A persisted or deferred Domain Event handler observes its stable event ID,
  event name, original publication time, exact JPA delivery attempt, and a
  FIRST/REDELIVERED hint derived from that attempt.
- An inbound HTTP Integration Event handler observes ID, name, and publication
  time with no exact attempt and an UNKNOWN hint.
- An inbound RabbitMQ Integration Event handler observes ID, name, and
  publication time with no exact attempt and a FIRST/REDELIVERED hint derived
  from the broker redelivery flag.
- An inbound RocketMQ Integration Event handler observes ID, name, publication
  time, exact `reconsumeTimes + 1` attempt, and a FIRST/REDELIVERED hint.
- A normal synchronous Domain Event handler sees no delivery context, even when
  it is synchronously raised from a reliable handler.
- Success, handler failure, Spring condition skip, interceptor failure, and
  scoped async Query/Capability success or failure leave no context behind.
- Calling the strict accessor outside an applicable delivery fails clearly;
  nullable lookup returns null.

# Constraints and invariants

- The context contains only `eventId`, `eventName`, `publishedAt`, nullable exact
  `attempt`, and `UNKNOWN`/`FIRST`/`REDELIVERED` non-authoritative hint.
- `eventId`, `eventName`, and `publishedAt` are required. Exact attempts are
  positive when present and absent when the current owner cannot prove them.
- `publishedAt` is the immutable time at which the framework first registers the
  reliable event for publication; retry or materialization must not replace it.
- Context lifetime is narrower than transport/interceptor bookkeeping and covers
  handler invocation plus all PR #158 managed async work owned by that handler.
- The context is local execution state, not serialized attribution and not a
  mutable/global ThreadLocal API.
- Existing sequential, ordered, fail-fast handler semantics remain unchanged.

# Decisions

- Reuse immutable ExecutionContext snapshot propagation with a framework-private
  key and no codec rather than introducing an independent ThreadLocal.
- Wrap the dispatcher seam, not payload handler signatures or transport routes.
- Use the existing Cap4k timestamp wire header with one strict epoch-millisecond
  representation and no fallback parser. For this route-neutral slice, inbound
  Integration Event `eventName` is the resolved payload type's simple class name;
  it is not copied from a route or endpoint identifier.
- HTTP has `attempt = null` and UNKNOWN; RabbitMQ has `attempt = null` and uses
  the broker redelivery flag as a hint; RocketMQ exposes its broker counter as an
  exact attempt; the JPA reliable record exposes its owned attempt counter.
- The full Runtime contract was confirmed in the framework capability audit on
  2026-08-03; this change implements only the explicitly requested delivery-
  context slice on an isolated feature branch.

# Open questions

None. The audit contract and the implementation request define the public
behavior and exclusions for this slice.

# Verification expectations

- Focused tests in `ddd-core`, `ddd-domain-event-jpa`, the HTTP/RabbitMQ/RocketMQ
  Integration Event modules, and affected starters.
- Tests prove visibility, exact/null attempt semantics, hints, scoped async
  convergence, ordinary-event suppression, and cleanup after success, failure,
  and condition skip.
- `scripts/validate-current-runtime-facts.ps1`, PR workflow guard tests,
  `buildSrc` tests, repository `check`, and `git diff --check` pass.
- Static review proves the public type has no topology/deduplication fields and
  no compatibility path was introduced.
