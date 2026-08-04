# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-25b3d3ed85e6c35c54d83bd969162a3f7ea2080a5b03a1d280b6a1b66844975d",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-27f9766ba87d809872f2fa0b1fa897e4380ddbb44b0551005dcabd85b460c66b",
    "evidence_refs": [
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventExecutionContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7955fefa4578919154d57cd2fdc3cef4b9738e64173e70b5ef5d063bb32e398c",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/ReliableEventDeliveryContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-85abe188b2f726b8f98466d137a2575a2662103d85148e8ed346be38603945f3",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/ReliableDomainEventExecutionContextTest.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/EventRecordImplTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8bf6a5aefa1c33d3da86160d1caf0188ceb739a5d55925a975f2de5fbf6cd12c",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-9ee77ea70b5dfdeecdcb10d57ca2ac8d87dccae4fb3f052b0943e298fade8860",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventConsumeHandlerTest.kt",
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventDeliveryContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a800933123e5f3d4563dda3f8c4a07694c13c027fcea9d60e8f4de24db1fbdbf",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventListenerFactoryTest.kt",
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventDeliveryContextTest.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventExecutionContextTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->
# Commands and results

- PASS: `./gradlew.bat :ddd-core:test --tests ReliableEventDeliveryContextTest --tests DefaultDomainEventSupervisorTest --tests ReliableDomainEventExecutionContextTest --tests DefaultEventPublisherTest --tests Cap4kEventListenerFactoryTest`.
- PASS: focused JPA, HTTP, RabbitMQ, RocketMQ, affected starter, and generated Domain Event runtime contract tests.
- PASS: `./gradlew.bat :ddd-domain-event-jpa:test --tests 'com.only4.cap4k.ddd.domain.event.JpaEventRecordRepositoryTest$ArchiveByExpireAtTest' :ddd-application-command-jpa:test --tests 'com.only4.cap4k.ddd.application.command.JpaCommandScheduleServiceTest$RetryTest' --no-daemon --console=plain --max-workers=2`.
- PASS: `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests 'com.only4.cap4k.plugin.pipeline.gradle.DesignRoundTripFunctionalTest' --no-daemon --console=plain --max-workers=1`.
- PASS: `./gradlew.bat check --no-daemon --console=plain --max-workers=2` (227 actionable tasks; repository required check completed in 19m 17s).
- PASS: `./scripts/validate-current-runtime-facts.ps1` reported no retired Event Subscriber APIs.
- PASS: `./scripts/test-pr-workflow.ps1`.
- PASS: `./gradlew.bat -p buildSrc test --no-daemon --console=plain --max-workers=2`.
- PASS: `git diff --check`.
- PASS: static forbidden-surface scan found no topology fields, deprecated aliases, or typealiases in the public context.
- PASS: `comet native check reliable-event-delivery-context` scanned 43 scoped files with zero issues; receipt `runtime/evidence/check-receipts/e0e98908a08139f8e71eef06e87c7eedd85aca564e384e41493da22634ab6688.json`.

# Skipped checks

None.

# Spec consistency

The implementation matches the complete target specification: it installs immutable local-only context at persisted/deferred Domain Event and inbound HTTP/RabbitMQ/RocketMQ handler dispatch, suppresses ordinary synchronous Domain Event dispatch and interceptor bookkeeping, propagates only through local ExecutionContext snapshots, and registers no transport codec. Stable event ID and original publication time cross all supported transports; exact attempts are exposed only for JPA and RocketMQ owners. No Integration Event route, attach/detach behavior, publisher selection, reliable-record state transition, or HTTP subscriber registry behavior was changed.

# Known limitations and risks

The context intentionally provides no inbox, authoritative deduplication, exactly-once guarantee, transport topology, or endpoint data. HTTP and RabbitMQ expose no exact attempt. Manually detached threads, coroutines, executors, and reactive work remain outside PR #158 scoped completion and propagation guarantees.

# Conclusion

PASS. All acceptance examples are covered by focused owner tests, the repository stale-surface and workflow checks pass, and the full required Gradle check passes.