# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0fe04cc6bff98acff51368f90673789c704e3fe71decc5cc82fa1a4fcd9f34e0",
    "evidence_refs": [
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventPublisherTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-4ee1c3504d31cc4654bf87c4f8ff5b9303a6351ffc898b26f15780784df95e9e",
    "evidence_refs": [
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqQueueIdentity.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqQueueIdentityTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-93170b64820544d831ed8ae5d3ca29783255d0017e754e94f9778b9dcb68dbbf",
    "evidence_refs": [
      "cap4k-ddd-integration-event-rabbitmq-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/RabbitMqIntegrationEventStarterBoundaryTest.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventRoute.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqTopologyManager.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqTopologyManagerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-9370c1ab078447a6600ee89a0647bbbf09ae5e5a0ef868206b2ddc7c90c1ed6d",
    "evidence_refs": [
      "cap4k-ddd-integration-event-rabbitmq-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventAutoConfiguration.kt",
      "cap4k-ddd-integration-event-rabbitmq-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/configure/RabbitMqIntegrationEventAdapterProperties.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-93f0abebe844d57d143748a2d67589418c015a7e565770a46d6c4dfc839739ea",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/provider/RuntimeProviderState.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/provider/RuntimeProviderStateTest.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqProviderStateCoordinator.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqProviderStateCoordinatorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d18b084f946790f0d93710d717262bb8c4c41f1e3201e52c8e8c0cb4f1338b44",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kApplicationListenerMethodAdapter.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/invocation/InvocationScopeTest.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapterTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-e3bfa8de83a923568774a5fdd7d508860b9b89f25f323dd1fe6a93fc7ff02ff3",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelope.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/ReliableEventDeliveryContext.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapterTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :ddd-core:test :ddd-integration-event-rabbitmq:test :cap4k-ddd-integration-event-rabbitmq-starter:test --no-daemon` passed. The focused suite covers provider-state registry lifecycle, queue identity, route/topology validation, publisher confirmation terminal paths, manual acknowledgement, requeue, and delivery-context cleanup.
- `./gradlew.bat check --no-daemon` passed on August 10, 2026: `BUILD SUCCESSFUL in 9m 24s`, 211 actionable tasks, with the existing PostgreSQL integration fixture skipped by its environment guard.
- `comet native check runtime-rabbitmq-transport --json` passed with zero findings. Receipt: `runtime/evidence/check-receipts/82ec10e1f6b61c7496b3da1c154890478b7778e8fcef1248a6ddc0ba75905bb6.json`.
- Static scans found no active RabbitMQ legacy configure/queue/destination parsing surface and no active `subscriberIdentity` field in Runtime source, transport starters, or permanent Comet specifications.
- `git diff --check` passed. Git emitted only line-ending conversion warnings and no whitespace errors.

# Skipped checks

- No live RabbitMQ broker or Testcontainers fixture was available, so real wire-level publisher-confirm/mandatory-return ordering was not executed.
- Broker restart, network interruption, consumer recovery, RabbitMQ redelivery metadata, and manual acknowledgement behavior were not exercised against a live broker. The implementation is covered by deterministic unit tests and Spring AMQP API contracts, but those checks do not replace a broker-backed smoke test.

# Spec consistency

- The implementation follows the confirmed August 3, 2026 Runtime contract: explicit event-name routes, stable application/event queue identity, correlated bounded publisher confirmation, manual acknowledgement after shared Handler completion, truthful provider state, and shared Runtime retry ownership.
- Permanent shared specifications were reconciled with the same confirmed contract: `ReliableEventDeliveryContext` exposes event delivery facts only, excludes subscriber identity and Rabbit topology, and uses the stable logical Integration Event name.
- Historical Comet archive snapshots were intentionally left unchanged as historical evidence.

# Known limitations and risks

- Delivery remains at least once. Process loss after broker handoff but before durable Runtime acknowledgement may produce duplicates.
- The provider does not add inbox deduplication, framework DLQ, exactly-once delivery, global ordering, downstream consumer acknowledgement collection, or per-Handler progress persistence.
- Provider state is a process-local live fact. It does not prove broker-wide health or inspect downstream consumers.
- A live-broker fixture remains the strongest follow-up evidence for confirm/return timing and reconnect/redelivery behavior.

# Conclusion

Pass. The RabbitMQ transport satisfies the confirmed bounded contract with focused tests, full repository verification, Comet scanning, and static surface checks. The absence of a live-broker smoke test is recorded as a limitation rather than treated as executed evidence.
