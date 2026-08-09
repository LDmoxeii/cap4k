# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-2344c398c375d6e0e25bdb5d073fca515f2ee454394ee8fa6ae089c17c1dbe1c",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventPayloadValidator.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisor.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7006f74b2e858c490a8cc6a7b4b75652ab15acbe90b146db3c8587d6da818c9e",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7d6ec2161f3c2c896cc93b68a05cd06fae4c7a1f57bec62a0dd04e969b61fc22",
    "evidence_refs": [
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventCoordinatorCompositionIntegrationTest.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a733a0fd44d62e680cfdfd4eabd6e8b710836e508e014245bfd9a00fe5dd4077",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfigurationTest.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ad68c1d2ce57493bf9c9928475f73cca2c241922dd6e1bcaa57920fd62e4b8e8",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfigurationTest.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-b5bb0fcde907a6dbafdbe083013efb3ce57e607516f49902726f133940c3aef2",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventPayloadValidator.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisor.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisorTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- Focused Gradle tests for `ddd-core`, the JPA reliable Event boundary, the HTTP transport starter, and the core-only starter completed successfully. The selected tests included `DefaultIntegrationEventSupervisorTest`, `EventRecordImplTest`, `JpaEventCoordinatorCompositionIntegrationTest`, `HttpIntegrationEventAutoConfigurationTest`, `CoreStarterAutoConfigurationTest`, and the existing shared transport foundation contract tests.
- `./gradlew check` completed successfully in 9 minutes 1 second with 202 actionable tasks (50 executed, 152 up-to-date).
- `git diff --check` completed successfully with no whitespace errors.
- An independent static review found no blocking correctness issue in the eager/lazy validation, JPA persistence invariant, HTTP startup enrollment, or pure-core isolation paths.

# Skipped checks

Remote PR CI was not run inside this local verification phase. Updating PR #177 will trigger the protected `check` workflow against the pushed commit.

# Spec consistency

The implementation hardens the existing event-name-only annotation and shared envelope/completion contract. It does not restore subscriber or provider-topology annotation properties, HTTP dynamic subscription, JPA subscriber persistence, compatibility layers, or sender-side fan-out. HTTP owns enrollment validation only while that transport is active; core-only startup remains provider-neutral.

# Known limitations and risks

The complete HTTP POST/status/connection behavior and RabbitMQ/RocketMQ route topology remain intentionally deferred to their provider-specific Batch 4 changes. The JPA lifecycle invariant also rejects legacy or manually constructed reliable Event rows with blank `eventType`, which is the intended breaking safety boundary for this repository.

# Conclusion

Pass. All six acceptance examples are supported by executable tests or persistence-boundary evidence, the full repository check passes, and the implementation remains within the confirmed shared Transport foundation scope.
