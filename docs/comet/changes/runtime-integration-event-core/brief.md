# Outcome

Introduce one Runtime-owned Integration Event envelope and one shared encode/decode boundary for outbound and inbound delivery. The envelope must preserve event identity, event type metadata, origin service/context, publication time, delivery attempt, and JSON payload without persisting entities. Transport adapters consume and emit this envelope while retaining ownership of provider-specific routing and acknowledgement.

# Scope

- Add a core `IntegrationEventEnvelope` and a core `IntegrationEventEnvelopeCodec` backed by `RuntimeJson`.
- Encode an `EventRecord` into a deterministic envelope containing event identity, event type, origin service, publication time, exact delivery attempt, execution-context elements, and payload JSON.
- Decode/validate inbound envelopes before payload type resolution and handler dispatch.
- Move Integration Event execution-context envelope encoding/decoding into `ddd-core`; remove the three transport-private copies.
- Update HTTP, RabbitMQ, and RocketMQ publishers/subscribers to use the shared envelope and preserve explicit stable subscriber identity in delivery metadata.
- Keep transport routing, broker publisher-confirm details, consumer acknowledgement mechanics, and HTTP experience behavior in later transport contracts.

# Non-goals

- No broker-specific routing redesign.
- No producer-side knowledge of all consumers and no global cross-consumer acknowledgement.
- No second reliable Event state machine.
- No persisted inbox or consumer-side deduplication store.
- No persistence of Aggregate, Entity, or other persistence-bound objects in the envelope.
- No automatic Analyzer-to-Generator feedback loop.

# Acceptance examples

- An annotated Integration Event with nested payload data and Strong IDs round-trips through the shared envelope with equal event identity, type metadata, origin context, publication time, delivery attempt, and payload JSON.
- Null/default payload properties remain present according to the Runtime Jackson boundary and envelope JSON is deterministic across repeated encodes.
- An envelope with a blank event id, blank event type, invalid publication time, non-positive delivery attempt, malformed context, malformed payload JSON, or an entity payload is rejected with safe diagnostic facts that do not include raw business payload content.
- An inbound delivery resolves a stable subscriber identity and exposes the same event id, event type, publication time, and attempt to the reliable delivery context regardless of HTTP, RabbitMQ, or RocketMQ adapter.
- Transport adapters no longer define their own Integration Event execution-context envelope implementation.

# Constraints and invariants

- Jackson through `RuntimeJson` is the only JSON boundary.
- Envelope field names and ordering are stable and deterministic.
- The payload is carried as JSON text inside the envelope; entity instances are never serialized into the reliable envelope.
- The sender owns one outbound event record; each subscriber owns its own delivery acknowledgement and retry boundary.
- Subscriber identity is explicit and stable for a destination/subscription and is not inferred from a transient broker delivery tag.
- `deliveryAttempt` is exact when supplied by the reliable Event state machine; unknown inbound provider metadata remains null/unknown rather than guessed.
- Handler methods remain synchronous; transport adapters only translate provider metadata and invoke the existing dispatcher/context scope.
- Existing static routes and transport configuration remain unchanged in this slice.

# Decisions

- The core envelope is the only cross-transport Integration Event representation.
- Origin context is represented by the existing versioned encoded execution-context elements and is decoded through the existing `ExecutionContextCodecRegistry` at the adapter boundary.
- Stable subscriber identity is carried as delivery metadata alongside the envelope, not as a sender-owned global consumer list.
- Payload type resolution remains catalog-driven by event type metadata; the envelope codec does not reflect arbitrary classes from untrusted metadata.
- Safe diagnostics expose event id/type and failure category, never raw payload JSON or entity contents.

# Open questions

# Verification expectations

- Focused core tests cover envelope round-trip, deterministic JSON, null/default values, nested payloads, Strong IDs, context ownership, validation failures, and entity rejection.
- Focused HTTP/RabbitMQ/RocketMQ tests prove all adapters use the shared envelope and preserve stable subscriber identity and delivery context metadata.
- Existing reliable Event and transport starter tests remain green.
- `git diff --check`, `comet native check runtime-integration-event-core`, and the focused Gradle matrix are run and recorded in Comet Verify.
