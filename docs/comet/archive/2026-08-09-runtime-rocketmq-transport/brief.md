# Outcome

Complete RocketMQ as a broker-backed Integration Event transport on top of the shared Runtime transport foundation. The provider resolves explicit topic/tag routes, reports success only after a positive RocketMQ SDK result, acknowledges inbound messages only after the complete local Handler scope, and exposes truthful recoverable provider state without creating a second reliable-delivery state machine.

# Scope

- Bind typed `cap4k.ddd.integration-event.rocketmq.routes[eventName]` configuration with required `topic` and `tag` fields.
- Resolve outbound routes and inbound subscriptions through one immutable, prevalidated route catalog.
- Derive a deterministic RocketMQ Consumer Group from `applicationName + eventName` for every actual local Integration Event registration.
- Map the RocketMQ SDK send result, callback, exception, and timeout boundaries into the shared once-only `IntegrationEventPublishCompletion`.
- Preserve the canonical envelope, `ReliableEventDeliveryContext`, local Handler dispatch, managed async Query/Capability join, and provider consume success/retry boundary.
- Introduce or reuse the transport-neutral live provider-state boundary for `HEALTHY`, `DEGRADED`, and `RECOVERING` facts.
- Remove the historical arbitrary consumer/configuration extension and every annotation-derived or per-topic property fallback.
- Add focused tests and static scans for route, group, publisher completion, consumer acknowledgement, provider state, and retired surfaces.

# Non-goals

- Exactly-once delivery, global ordering, inbox/deduplication, framework DLQ, per-Handler progress, or sender-side downstream acknowledgement tracking.
- A RocketMQ-specific reliable Event state machine, retry loop, scheduler, or generic task framework.
- Runtime discovery of all consumers, dynamic arbitrary consumer creation, or a public topology customization API.
- Changes to the HTTP or RabbitMQ provider slices.
- Live broker probing from static Agent API manifests or production-grade external monitoring.

# Acceptance examples

- `content.published` resolves only through its configured `{topic, tag}` route; blank, missing, duplicate, or contradictory routes fail before any consumer is started.
- Two instances with the same application name and event name derive the same Consumer Group; different application names receive independent groups; two event names in one application receive distinct groups.
- Outbound handoff succeeds only for `SendStatus.SEND_OK`. Non-positive, null/unknown, timed-out, synchronous-exception, and asynchronous-failure paths resolve provider failure exactly once.
- A consumed message returns `CONSUME_SUCCESS` only after all matching synchronous Handlers and Runtime-managed async Query/Capability work complete; any failure returns `RECONSUME_LATER` and stops later Handlers.
- RocketMQ delivery attempt and redelivery metadata are mapped into the shared delivery context and that context is cleared after dispatch.
- Temporary broker loss does not kill the application: the provider reports `DEGRADED` or `RECOVERING`, outbound records remain retryable, and the consumer may reconnect.
- Duplicate SDK callbacks cannot invert completion, and diagnostics contain no raw business payload JSON or persistence-bound entity.

# Constraints and invariants

- `@IntegrationEvent.value` remains only the stable logical `eventName`; it never supplies topic, tag, group, nameserver, or subscriber topology.
- Reuse the canonical Jackson envelope, shared publish completion, provider composition, inbound registration view, Handler completion coordinator, and reliable Event state machine.
- Sender completion means one provider handoff only; the sender never enumerates consumers or waits for their acknowledgements.
- Inbound subscriptions come only from actual local Integration Event Handler registrations.
- Deterministic route/type/topology/registration defects fail startup or enrollment; temporary external broker failure is recoverable runtime state.
- Breaking removal is allowed. No compatibility alias or fallback is required.
- Implementation starts from `origin/master` commit `e44424d42e815db4147d60453d40017d6813e14a`, which contains PR #177 shared transport foundation.

# Decisions

- Every active event route requires an explicit non-blank `topic` and `tag`.
- One immutable resolver validates the complete active inbound/outbound route set before publisher delivery or partial subscriber enrollment.
- Consumer Group identity is Runtime-owned and uses a deterministic, collision-resistant, RocketMQ-legal, bounded encoding of `(applicationName, eventName)`.
- Delete `RocketMqIntegrationEventConfigure` and historical `${rocketmq.<topic>.*}`/annotation topology fallbacks instead of adapting them.
- `SendStatus.SEND_OK` is the only positive RocketMQ handoff result; all other statuses and malformed results are failures.
- All terminal publisher paths flow through the shared once-only completion object.
- RocketMQ contributes live facts to a transport-neutral provider-state registry; it does not add a provider-specific state machine or claim static health.
- The shared dispatcher remains responsible for Handler conditions, local `@Order`, failure short-circuiting, and managed async Query/Capability joining.
- The user confirmed this complete RocketMQ target on 2026-08-09; Build may remove the retired extension paths and introduce the shared live provider-state boundary described above.

# Open questions

- None.

# Verification expectations

- Focused route and Consumer Group tests, including deterministic invalid configuration and proof that no consumer starts before full validation.
- Publisher tests covering `SEND_OK`, every non-positive SDK status, null/unknown result, synchronous throw, asynchronous failure, timeout, and duplicate terminal callbacks.
- Subscriber tests covering canonical envelope decode, attempt/redelivery context, all-Handler completion, managed async join, failure retry, later-Handler short circuit, and context cleanup.
- Provider-state tests covering healthy handoff, temporary loss, recovering transition, and no false healthy status.
- Static scans proving removal of arbitrary consumer configuration, annotation/per-topic topology fallback, provider-specific durable state, consumer enumeration, and raw payload logging.
- Run the focused RocketMQ module/starter tests, relevant shared Runtime tests, repository `check`, Comet verification, and final diff/worktree checks.
