# Outcome

Land the single shared Integration Event transport foundation that the HTTP,
RabbitMQ, and RocketMQ provider branches consume. The foundation has one
event-name-only identity, one canonical envelope/completion boundary, one
catalog-derived inbound subscription view, and one provider-selection boundary.

# Scope

- Remove `IntegrationEvent.subscriber` and `NONE_SUBSCRIBER` from the public
  annotation and generated Integration Event skeletons.
- Remove the HTTP dynamic subscriber registry, subscribe/unsubscribe
  capabilities/endpoints, and HTTP-JPA subscription carrier from the active
  runtime surface.
- Introduce a transport-neutral route/catalog contract that provider adapters
  can use without inventing provider state machines or subscriber discovery.
- Keep `IntegrationEventEnvelope`, its Jackson codec, delivery metadata, and
  once-only publish completion in `ddd-core` as the only shared wire/runtime
  boundary.
- Derive inbound event registrations from the local `EventTypeCatalog` and
  actual Integration Event handler descriptors; no annotation field or
  registry entry may create a subscription.
- Make provider composition deterministic through the existing runtime
  provider slot rules; do not add a shared fallback publisher.

# Non-goals

- Do not implement HTTP, RabbitMQ, or RocketMQ route parsing, client calls,
  publisher confirms, consumer acknowledgements, reconnect loops, or provider
  health state in this change.
- Do not change reliable Command/Event state machines, retry policy, claim,
  lease, redrive, retention, or `ReliableEventDeliveryContext` semantics.
- Do not add compatibility aliases, a second route syntax, dynamic discovery,
  broadcast, inbox/deduplication, global ordering, or a generic task framework.
- Do not make handlers asynchronous or change Generator/Analyzer business
  modeling semantics beyond removing the retired subscriber field.

# Acceptance examples

- A payload annotated as `@IntegrationEvent("content.published")` exposes only
  the stable event name; compiling code that supplies `subscriber = ...` fails.
- A local Integration Event class with no actual handler is absent from the
  inbound registration view, even when it is present in the catalog.
- Two handler methods for the same Integration Event produce two local handler
  descriptors without a registry or provider-specific subscriber metadata.
- A malformed or duplicate provider slot is rejected deterministically by the
  shared composition boundary; no provider is silently selected.
- Every provider can encode/decode the same envelope and use the same
  once-only publish completion; no provider-specific envelope or completion
  type is introduced.
- The HTTP starter exposes only the fixed event receive surface after this
  change; subscribe/unsubscribe/events/subscribers management surfaces and
  their JPA table are absent.

# Constraints and invariants

- `IntegrationEvent.value` is the only annotation identity and must be
  non-blank for a usable Integration Event.
- The sender observes only the selected provider handoff; it never enumerates
  or waits for all consumers.
- Inbound acknowledgement remains outside this change and is owned by each
  provider after the local synchronous Handler scope completes.
- Reliable event payloads continue to reject persisted Aggregate/Entity
  instances; this change must not weaken that boundary.
- Work is implemented from the latest `origin/master` on a short-lived branch
  and must remain independently mergeable before provider branches start.

# Decisions

- The shared foundation owns the retired subscriber removal and common
  catalog/route/provider-selection API so the three provider branches cannot
  recreate conflicting contracts.
- Provider-specific route objects remain in provider modules; the shared API
  carries only stable event names and resolved destination identity.
- Existing `IntegrationEventEnvelope`, `IntegrationEventEnvelopeCodec`,
  `IntegrationEventDeliveryMetadata`, `deliveryContext`, and
  `IntegrationEventPublishCompletion` remain canonical and are reused.

# Open questions

None. Batch 4 product decisions are already closed by the canonical Runtime
transport specifications.

# Verification expectations

- Focused Kotlin tests for annotation identity, catalog-derived inbound event
  registration, route/provider-slot validation, and once-only completion.
- Starter boundary tests prove the retired HTTP registry/capabilities/JPA
  surface is no longer auto-configured.
- Repository-wide static scans prove no active `subscriber` annotation field,
  `NONE_SUBSCRIBER`, dynamic HTTP registry, or old subscribe/unsubscribe
  management path remains.
- Run the affected Gradle module tests and `comet native check` before Verify.
