# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-48b824da998a2b1ea92a208954f0408704467e7b42020afb88859d7e76c6ff89",
    "evidence_refs": [
      "cap4k-ddd-integration-event-rocketmq-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/RocketMqIntegrationEventStarterBoundaryTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberRecoveryTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-4a32d431dc41a97d80d6ce7cd5c314630dbc5683020f9c6e3ac15d4c612df157",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberRecoveryTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-4faf2893e70fc2e06abaf82b272505ace7cd05326b5889dffa34b0cfb29fcd02",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqProviderStateCoordinatorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5ff70f87507b36d6f133a9d5210003be12c232167f5d15f003a5be657670810b",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/LogCapture.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapterTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberRecoveryTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8f92e46927e9577d739913a4f5ba6d53a106b19ab6f47b1df331ad68afc22e6c",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberRecoveryTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-913892a222cc2490a3530e63219556043dbe0bef5f014a1aca205df38d4af229",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapterTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-bc29981dc2f5bcde8f54661ba11cd224969a7af7be460c2fe3680ebc354461e6",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventRouteInterceptor.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventRouteInterceptorTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->
# Commands and results

- ./gradlew :ddd-integration-event-rocketmq:test :cap4k-ddd-integration-event-rocketmq-starter:test --no-daemon passed. The RocketMQ module executed 36 tests and the starter executed 6 tests, with zero failures, errors, or skips.
- ./gradlew check --no-daemon passed in 9m 29s. Gradle reported 202 actionable tasks: 5 executed, 70 from cache, and 127 up-to-date.
- Production-source scans reported zero matches for the retired event-package provider-state API, subscriberIdentity, exception-message access, sensitive RocketMQ log templates, and direct Throwable logging.
- git diff --check completed without whitespace errors.
- comet native check runtime-rocketmq-mainline-alignment passed with receipt runtime/evidence/check-receipts/b9be317077c844780ca983b66eb63854a3a3e3f5fe31d19e37a67c4568e72284.json.

# Skipped checks

- No real RocketMQ nameserver/broker smoke test was run because no broker environment was available. Recovery, send-result, acknowledgement, and consumer lifecycle behavior were verified through the RocketMQ SDK seams and focused tests.
- The unrelated PostgreSQL soft-delete environment test remained skipped by the repository's existing Gradle test conditions; the complete Gradle check task still passed.

# Spec consistency

- The provider uses only com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry through one stable reporter identity: integration-event-transport.rocketmq.
- Publisher and subscriber facts are aggregated with degraded/recovering precedence, and unchanged aggregate facts are not republished.
- Reliable delivery context remains transport-neutral. RocketMQ topology and subscriber/application identity are kept outside the shared context.
- Route resolution is enforced at eager attachment and defensive pre-persistence boundaries before repository save, while the publisher retains defensive resolution.
- Initial temporary broker unavailability keeps the application alive and schedules fresh-consumer recovery; deterministic failures are not retried; shutdown is terminal and releases active or concurrently starting consumers.
- Publisher, subscriber, recovery, state-reporting, and shutdown diagnostics expose only safe identity/category/type facts.

# Known limitations and risks

- Real broker interoperability, authentication, nameserver behavior, and SDK-version-specific reconnection behavior were not exercised against a live RocketMQ deployment.
- Delivery remains intentionally at-least-once. Process loss after broker handoff and before durable acknowledgement can produce a duplicate.
- After a consumer starts successfully, ordinary reconnect behavior remains owned by the RocketMQ SDK; cap4k recovery only owns failed initial enrollment.

# Conclusion

PASS. The implementation satisfies the confirmed RocketMQ transport contract on the post-PR-180 Runtime baseline, with complete focused and repository-wide automated verification and an explicit live-broker boundary.
