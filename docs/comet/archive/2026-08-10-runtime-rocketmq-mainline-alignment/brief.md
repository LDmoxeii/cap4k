# Outcome

Refresh PR #178 onto the PR #180 Runtime baseline and make RocketMQ a correct owner of the unified Integration Event transport contract. The provider uses the mainline provider-state registry, installs only transport-neutral delivery context, rejects invalid routes before reliable persistence, recovers subscriptions after temporary initial broker unavailability, and emits only safe diagnostics.

# Scope

- Rebase `feature/runtime-rocketmq-transport` onto `origin/master` at or after `594e2d086c11a275c2d9794ce24ae94d54a81d67` and update the existing PR #178.
- Delete the PR-owned provider-state registry and use `com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry` with provider ID `integration-event-transport.rocketmq`.
- Aggregate publisher and subscriber component facts so one healthy path cannot mask another degraded or recovering component.
- Remove `subscriberIdentity` and every subscriber/application identity injection from reliable delivery metadata/context.
- Add a recoverable consumer enrollment lifecycle for initial temporary nameserver/broker failure, successful re-enrollment, healthy-instance reuse, and terminal shutdown.
- Add RocketMQ route validation at `onAttach` and `prePersist`, while retaining defensive publisher resolution.
- Harden publisher, subscriber, and recovery logs so arbitrary `Throwable`, messages, payloads, envelope JSON, external response bodies, and provider topology never enter diagnostics.
- Add focused adapter/starter tests, update Comet evidence, run full Gradle verification, and push the existing branch.

# Non-goals

- Restoring the PR #178 provider-state API, `subscriberIdentity`, or any topology field in `ReliableEventDeliveryContext`.
- Exposing topic, tag, Consumer Group, nameserver, RocketMQ message objects, payload JSON, or envelope JSON as shared business context or diagnostics.
- Adding exactly-once delivery, inbox/deduplication, a framework DLQ, global ordering, sender-side downstream acknowledgement aggregation, or a RocketMQ-specific reliable Event state machine.
- Recreating already healthy consumers, replacing the SDK reconnect behavior after a successful start, or adding a generic scheduler/task framework.
- Changing HTTP or RabbitMQ provider behavior.

# Acceptance examples

- Given a configured event route, eager attachment and lazy persistence validation both succeed; given a missing or invalid route, the reliable `EventRecord` is rejected before `EventRecordRepository.save` is invoked.
- Given a temporary initial nameserver/broker connection failure, application startup continues and provider state is `DEGRADED` or `RECOVERING`; when the broker becomes available, a newly created consumer starts, the subscription becomes active, and the aggregated provider state becomes `HEALTHY`.
- Given an already healthy consumer, a recovery trigger does not create or start another consumer for the same subscription.
- Given adapter shutdown, pending recovery stops, every active consumer is shut down, provider-state registration is closed by the starter lifecycle, and no later recovery creates resources.
- Given publisher degradation and subscriber health, the aggregate provider fact remains degraded until the publisher component recovers.
- Given inbound delivery, the installed context contains only event ID, event name, published time, optional exact attempt, and redelivery hint; it contains no subscriber or provider topology.
- Given any publisher, subscriber, or recovery failure, logs contain only approved provider/event identity and safe category/failure-type fields, never exception messages, arbitrary throwable rendering, payloads, envelopes, topology, or external response bodies.

# Constraints and invariants

- The stable provider ID is exactly `integration-event-transport.rocketmq`.
- The mainline `RuntimeProviderStateRegistry.register(providerId)` and `RuntimeProviderStateReporter` lifecycle are the only provider-state boundary.
- Deterministic route, topology, event-type, and registration errors fail before partial consumer activation; only temporary external unavailability enters recovery.
- All subscriptions and routes are materialized and validated before the first consumer start.
- Healthy consumers are not duplicated. A failed consumer instance is shut down and discarded before a replacement is created.
- Shutdown is terminal, idempotent, cancels recovery work, prevents later resource creation, and releases active consumers.
- The RocketMQ SDK remains responsible for reconnecting a consumer that has already started successfully.
- `SEND_OK` remains the only positive outbound handoff result, and reliable delivery remains at-least-once.

# Decisions

- The user explicitly rejected the PR #178 provider-state registry and `subscriberIdentity` on 2026-08-10.
- Provider state follows the PR #180 mainline contract and uses component aggregation for publisher/subscriber facts.
- Route validation mirrors the mainline RabbitMQ `IntegrationEventInterceptor` pattern at both eager attachment and defensive pre-persistence boundaries.
- The publisher retains defensive route resolution even after persistence guards are installed.
- Recovery is limited to subscriptions that failed their initial start because of temporary nameserver/broker unavailability; already started consumers remain SDK-managed.
- Initial-enrollment recovery uses a configurable positive fixed delay at `cap4k.ddd.integration-event.rocketmq.recovery-interval`, defaulting to five seconds.
- The user confirmed the complete shared understanding on 2026-08-10.

# Open questions

- None.

# Verification expectations

- Focused RocketMQ module and starter tests.
- Initial failure to broker recovery to successful consumer start, no duplicate healthy consumer, shutdown cancellation/resource release, and provider-state aggregation/unregistration tests.
- Eager and lazy route rejection tests proving `EventRecordRepository.save` is not called.
- Safe-diagnostics tests covering publisher, subscriber, and recovery failures.
- Static scans for the retired PR-owned provider-state package, `subscriberIdentity`, topology leakage, raw payload/envelope logging, exception messages, and throwable logging.
- `comet native check runtime-rocketmq-mainline-alignment` and a complete six-section verification report.
- `./gradlew check` with an explicit note that no real RocketMQ broker smoke test was run unless one becomes available.
