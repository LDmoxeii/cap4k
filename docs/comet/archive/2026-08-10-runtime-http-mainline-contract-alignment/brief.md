# Outcome

Rebase the existing HTTP experience transport PR onto `origin/master@594e2d086c11a275c2d9794ce24ae94d54a81d67` and migrate it to the Runtime contracts introduced by PR #180 without restoring parallel Provider State or delivery-topology APIs.

# Scope

- Update existing PR #179 and its branch; do not create another PR.
- Remove the PR-local `com.only4.cap4k.ddd.core.runtime` Provider State implementation.
- Register HTTP through `com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry` with provider ID `integration-event-transport.http`.
- Use the mainline registration lifecycle, including initial `RECOVERING/enrolled`, state reporting through `report(...)`, and reporter close/unregister on Spring shutdown.
- Remove HTTP use of `ReliableEventDeliveryContext.subscriberIdentity` and keep application name, URL, endpoint, and all transport topology outside Delivery Context.
- Resolve PR #180 conflicts in favor of the shared Runtime contracts while preserving the completed HTTP route, handoff, receiver, self-route, timeout, lifecycle, failure-classification, and safe-logging behavior.
- Refresh Comet verification evidence after the new master baseline is integrated.

# Non-goals

- No compatibility layer, `UNKNOWN` state, duplicate registry, or convenience reporter API.
- No topology fields in `ReliableEventDeliveryContext` or `IntegrationEventDeliveryMetadata`.
- No changes to RabbitMQ or RocketMQ transport behavior.
- No new route syntax, dynamic subscription, broadcast, HTTP-owned retry state, or production broker guarantees.

# Acceptance examples

- Starting the HTTP starter registers exactly `integration-event-transport.http`; its first fact is `RECOVERING/enrolled`.
- A successful HTTP 2xx handoff reports `HEALTHY`; timeout, connection, non-2xx, codec, route, or handoff failures report safe categorized state through the mainline reporter.
- Closing the Spring-managed reporter removes the provider fact and permits a later registration with the same provider ID.
- Inbound HTTP delivery installs only event/delivery facts; application name, target URL, endpoint, queue, group, or subscriber identity are absent.
- Self-routing still posts the canonical envelope to the fixed endpoint and completes only after the local Handler scope succeeds.
- Missing routes are still rejected before a reliable record is persisted.

# Constraints and invariants

- Base commit is the current `origin/master` commit `594e2d086c11a275c2d9794ce24ae94d54a81d67`.
- Mainline core Provider State and Delivery Context definitions are authoritative during conflict resolution.
- HTTP configuration keeps the mainline prefix `cap4k.ddd.integration.event.http` and one static `routes` map.
- Reliable retry, lease, token, attempt, and durable acknowledgement remain Runtime-owned.
- Logs and failure facts never include raw payload/envelope data or credentials.

# Decisions

- Stable provider ID: `integration-event-transport.http`.
- Provider registration starts as mainline `RECOVERING` with category `enrolled`; `UNKNOWN` is not restored.
- The registry-owned reporter is a Spring bean with `destroyMethod = "close"`; the publisher uses it but does not own registration removal.
- Delivery Context remains transport-neutral and contains no subscriber or topology identity.
- PR #180 shared adapter/core contracts win all conflicts; only HTTP-specific behavior from PR #179 is reapplied.

# Open questions

- Confirmed on 2026-08-10 after canonical spec rebase: the HTTP alignment contract is unchanged; no product behavior or scope was added or removed.

# Verification expectations

- HTTP module and starter focused tests.
- Provider State registration, initial fact, state changes, close/unregister, and re-registration tests.
- Real self-routing HTTP test, including Handler-scoped asynchronous work.
- Static checks for removed parallel Provider State and removed Delivery Context topology.
- Full `./gradlew.bat check --stacktrace`.
- Fresh Comet scope, check receipt, verification report, and archive for this alignment change.