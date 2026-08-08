# Outcome

Make reliable Event execution consume the private JPA ownership substrate in production. Persisted Domain Events and outbound Integration Events must use one token/lease/state-transition authority, so a delivery is claimed before execution, completed only after its synchronous delivery scope returns, and retried only through the same durable state machine. After-commit notifications may wake delivery, but must never bypass claim, lease, acknowledgement, or failure transitions.

# Scope

- Wire persisted Domain Event delivery and outbound Integration Event handoff to the shared private claim/renew/ack/fail substrate.
- Replace the old read-then-save event delivery path, JVM-only retry loop, and direct after-commit publishing path with ownership-driven execution.
- Make `Mediator.events.enqueue`, `schedule`, and `delay` the only public Integration Event scheduling operations; preserve aggregate-bound Domain Event attachment as the domain-side recording mechanism.
- Use one reliable delivery context core for local Domain Event dispatch and outbound handoff, while keeping their provider boundaries distinct.
- Carry event id, origin, attempt, and redelivery facts through `ReliableEventDeliveryContext` without persisting entities or exposing ownership tokens as business payload.
- Apply the frozen retry-policy snapshot and safe failure-facts boundary on every failed attempt; make the first real claim attempt number `1`.
- Renew an active lease while a delivery scope is executing and reject stale or replaced tokens for every owner-sensitive transition.
- Add focused Core, Event JPA, and starter-level integration evidence for local success/failure, retries, lease recovery, duplicate delivery, after-commit wake-up, outbound handoff, context cleanup, and payload rejection.

# Non-goals

- No broker routing, HTTP/RabbitMQ/RocketMQ provider redesign, consumer subscription identity, or transport-specific publisher-confirm details; those belong to the Integration Event transport/core slices.
- No inbound Integration Event envelope or consumer-ack implementation in this branch.
- No exactly-once guarantee, global consumer ordering guarantee, or knowledge of all downstream recipients; sender-side success means the configured local provider accepted the handoff.
- No manual redrive, retention/cleanup, generic Scheduler/Job/Task framework, or restoration of Locker, Saga, Snowflake, Console, or other retired runtime surfaces.
- No detached handler execution, handler coroutines, implicit transaction widening, or replacement public event API beyond the agreed scheduling surface.
- No compatibility aliases for retired EventSubscriber/legacy event transition APIs.

# Acceptance examples

- A persisted Domain Event becomes durable in the committing transaction. An after-commit signal only wakes the coordinator; the coordinator claims it, loads the payload, dispatches all matching synchronous listeners, and acknowledges it with the claim token after the handler scope completes.
- A local listener failure causes the current owner to record safe failure facts and a retry/terminal state through the token-bound transition. A later due claim can redeliver the event; duplicates are permitted and are not treated as an exactly-once violation.
- An outbound Integration Event created in the same unit of work is released only after the owning transaction commits. The sender claims it before handoff and acknowledges it only after the configured provider reports successful acceptance; a failed handoff enters the same retry/terminal state.
- An event whose lease expires can be reclaimed with a new token. The old owner cannot renew, acknowledge, or record failure after its lease/token is stale.
- An event derived while handling a local Domain Event is not published when the parent handler scope fails. It is released only after the enclosing successful scope reaches its durable handoff boundary.
- A payload containing a persisted Aggregate, Entity, or other persistence-bound instance is rejected before it becomes a reliable delivery record.
- A successful handler scope waits for every `queries.askAsync*` and `capabilities.callAsync*` launched inside that scope, but the handlers themselves remain synchronous; `send` executes now and `enqueue`/`schedule`/`delay` only schedule.

# Constraints and invariants

- There is one authoritative Event state machine. Existing Event carriers may keep separate local/outbound provider metadata, but no second mutable state machine may decide ownership or completion.
- Claim eligibility is evaluated at the database boundary using service/consumer identity, state, due time, record expiry, and absent/expired lease predicates. Claim, attempt increment, token, and lease update atomically.
- Every delivery attempt must possess the current opaque token before loading/dispatching the payload. Ack and failure/retry are conditional on that token and a live lease; a mismatch or stale lease has zero write effect.
- The runtime renews an active lease according to the configured lease policy while the synchronous delivery scope is executing. Lease expiry is recovered by a new claim, never by reviving the old token.
- The first real claim of a newly persisted event is attempt `1`; persistence-time initialization must not consume an attempt.
- Retry timing, budget, and terminal behavior come from the immutable retry-policy snapshot stored on the record. Only structured safe failure facts are persisted; business payloads and exception stack traces are not.
- Local Domain Event dispatch and outbound Integration Event handoff share delivery facts and ownership semantics, but remain separate providers so a local listener completion is not confused with external transport confirmation.
- `ReliableEventDeliveryContext` is scoped to one delivery attempt, is cleared on success and failure, and exposes only event/delivery facts needed by handlers. It never carries or persists an entity graph or internal lease token.
- The runtime does not promise `@Order` across consumers or services. Any supported order is local dispatch order only.

# Decisions

- Remove old EventRecord mutation methods and old production `getByNextTryTime`/`resume`/direct-publish authority rather than preserving compatibility; repository reads may remain for diagnostics until the later surface-cleanup slice removes them.
- Convert after-commit publication from “execute now” to “durably recorded, then wake/claim”; if the wake-up is lost, the due-record coordinator remains the recovery path.
- Keep sender-side at-least-once semantics. Transport-specific confirmation, routing, and inbound acknowledgement are downstream responsibilities; this slice only defines the reliable record and handoff boundary they consume.
- Treat `Mediator.events.enqueue` as immediate due scheduling, `schedule` as explicit due-time scheduling, and `delay` as relative due-time scheduling. These operations create the same durable event envelope and state machine entry.
- Derived outbound events are committed/released according to the existing unit-of-work boundary and are not independently visible before the enclosing transaction/handler scope succeeds.
- No public generic reliable-execution framework is introduced; the claim coordinator remains private Runtime infrastructure.

# Open questions

No unresolved product questions. Transport-specific provider confirmation and inbound subscriber acknowledgement remain explicitly deferred to the later Integration Event transport/core changes.

# Verification expectations

- Use `comet native show`/Shape transition validation for the contract now; run the built-in Comet check only after Build has produced a complete implementation scope and entered Verify.
- During Build, run focused tests for the Core event supervisor/publisher, Event JPA carrier/repository/provider, Event JPA starter auto-configuration, and the real claim/dispatch/ack/fail path.
- Include evidence for concurrent claim ownership, token mismatch, lease renewal and expiry recovery, first-attempt numbering, retry snapshot stability, safe failure facts, terminal/cancel rejection, after-commit wake-up recovery, handler-scope async-child waiting, context cleanup, derived-event release, duplicate delivery, and entity-payload rejection.
- Verify no production path can publish or transition a reliable Event without first owning a current token, and report any database-dialect-specific coverage limits explicitly.
