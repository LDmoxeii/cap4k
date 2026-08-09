# Outcome

PR #177 closes the remaining lifecycle gaps in the shared Integration Event transport foundation. Blank logical event names are rejected before any reliable record can be created or saved, the JPA carrier keeps a final persistence invariant, and an active HTTP transport validates its inbound registration view during application startup rather than on the first message.

# Scope

- Harden Integration Event payload validation at Supervisor registration/release boundaries.
- Preserve lazy attachment semantics while rejecting the resolved blank-name payload before repository creation or save.
- Add a JPA persistence-boundary invariant so lower-level or accidental bypass paths cannot insert a durable record with a blank `eventType`.
- Materialize and validate `InboundIntegrationEventRegistrationView.integrationEventTypesByName()` when the HTTP transport adapter is enrolled.
- Add focused core, JPA, and HTTP starter tests for eager/lazy rejection, no durable write, blank registration identity, and duplicate name/different payload conflicts.

# Non-goals

- Reintroducing annotation properties other than `IntegrationEvent.value`.
- Reintroducing HTTP dynamic subscription, subscriber registries, JPA subscriber persistence, discovery, compatibility layers, or sender-side fan-out.
- Implementing RabbitMQ or RocketMQ routes/topology, or completing HTTP POST/status/timeout behavior.
- Changing the canonical envelope, completion callback semantics, or single-provider composition contract.

# Acceptance examples

- Scheduling an eager `@IntegrationEvent("")` or whitespace-only payload fails immediately; `EventRecordRepository.create()` and `save()` are not called.
- Scheduling a lazy supplier remains lazy, but `release()` rejects its resolved blank-name payload before repository creation/save.
- Direct JPA persistence of an `Event` whose `eventType` is blank fails and leaves no database row.
- Starting an application with the HTTP transport and an inbound registration view containing a blank event name fails during context startup.
- Starting an application with the HTTP transport and two different payload classes registered under the same event name fails during context startup.
- A pure core application without an active Integration Event transport is not forced to materialize provider enrollment.

# Constraints and invariants

- No durable reliable Event record may contain a blank `eventType`.
- Integration Event event-name validation uses `isNotBlank`, not only non-null/non-empty checks.
- Eager payload validation occurs before attachment; release repeats the validation defensively before record creation.
- Lazy suppliers are not evaluated during registration, but their result is validated before persistence.
- HTTP startup validation is owned by the active HTTP transport enrollment boundary, not by unconditional core startup.
- Existing event-name-only annotation identity, canonical Jackson envelope, provider completion, and exactly-one provider selection remain unchanged.

# Decisions

- Use one shared Integration Event payload-name validator from the Supervisor and the JPA payload-to-record path so both layers enforce the same annotation rule.
- Keep a JPA entity lifecycle invariant for blank `eventType` as the final defense against direct repository bypass.
- Eagerly materialize the inbound name map in the HTTP adapter constructor/bean enrollment path; do not add a global core startup validator.
- Treat this as hardening of the published `runtime-shared-transport-foundation` capability and replace its complete target specification.
- The user explicitly confirmed this complete contract on 2026-08-09.

# Open questions

None.

# Verification expectations

- Focused tests for `ddd-core`, `ddd-domain-event-jpa`, and `cap4k-ddd-integration-event-http-starter` pass.
- Tests prove eager and lazy blank-name paths fail before repository save and that direct JPA persistence produces no row.
- Application context tests prove active HTTP startup rejection for blank and duplicate event names while a core-only context remains unaffected.
- `./gradlew check` and `git diff --check` pass before PR #177 is updated.
