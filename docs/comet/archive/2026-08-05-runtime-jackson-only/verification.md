# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1ab18e6d1c8e08c3bbdb348c72672de4e6770c1d0815d414091fe8039b91189b",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DomainEventPayloadValidator.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJson.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJsonTest.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-4e465b8105c97cf5bfba60c36ec68fb2da45744fa018a02aeebebfd8a347099a",
    "evidence_refs": [
      "ddd-application-command-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRecordEntity.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJson.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJsonTest.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c0f7aacbe58d2659ac246d115c466f3fad982e1e8d7ee41e0c3fd31b4a004676",
    "evidence_refs": [
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/IntegrationEventExecutionContextEnvelope.kt",
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventExecutionContextTest.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c1d888a963d8f7ac9f28a9703bb3b9ddd89195afc80d51b3c18e5ab15ba7328b",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/CommandExecutionContextPersistenceTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJsonTest.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/EventExecutionContextPersistenceTest.kt",
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventDeliveryContextTest.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventExecutionContextTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-de4058df69d982290c78e9d710bb56bb91eb929f05715f84c6c3a4e87a8d1281",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJson.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/json/RuntimeJsonTest.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/capabilities/IntegrationEventHttpCallbackTriggerCapability.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ef12aa9cf74cb3ec94c596d4f536dde7d23f58857925d61fbc5a103f33b54952",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/build.gradle.kts",
      "ddd-application-command-jpa/build.gradle.kts",
      "ddd-core/build.gradle.kts",
      "ddd-domain-event-jpa/build.gradle.kts",
      "ddd-integration-event-http-jpa/build.gradle.kts",
      "ddd-integration-event-http/build.gradle.kts",
      "ddd-integration-event-rabbitmq/build.gradle.kts",
      "ddd-integration-event-rocketmq/build.gradle.kts",
      "gradle/libs.versions.toml",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew :ddd-core:test --tests 'com.only4.cap4k.ddd.core.share.json.RuntimeJsonTest' --no-daemon`: passed; Kotlin data class/private constructor/default/null/collections/nested values, deterministic map keys, Strong ID scalar strings, envelope round-trip, and payload-safe failures are covered.
- `./gradlew :ddd-application-command-jpa:test :ddd-domain-event-jpa:test :ddd-integration-event-http:test :ddd-integration-event-rabbitmq:test :ddd-integration-event-rocketmq:test :cap4k-ddd-integration-event-http-starter:test --no-daemon`: passed; 50 actionable tasks, including reliable Command/Event persistence and all three Integration Event transport owners.
- `./scripts/validate-current-runtime-facts.ps1`: passed; current Runtime facts contain no retired Runtime surfaces.
- `./gradlew check --no-daemon`: passed; 212 actionable tasks, 61 executed and 151 up-to-date.
- `git diff --check`: passed; Git emitted only expected LF-to-CRLF checkout warnings.
- Production-source and build-graph scans found no active Runtime FastJSON/Gson usage, no `fastjson`/`gson` version or dependency aliases, and no raw business payload in Runtime failure/log messages.

# Skipped checks

- No external broker or PostgreSQL service was required by the owner-focused Runtime codec tests; transport envelope tests use the existing in-process fixtures.
- The pipeline generator family was not changed by this Runtime-only slice. Its existing Jackson-facing generated Strong ID contract is consumed by the Runtime tests; generator redesign remains outside this branch.

# Spec consistency

- Reliable Command/Event records now persist only JSON payloads through the shared Jackson baseline. Domain Event persistence validates and rejects entity-backed payloads before recording them.
- HTTP, RabbitMQ, and RocketMQ Integration Event envelopes share the same deterministic Jackson execution-context representation. Routes, provider selection, HTTP self-routing, at-least-once delivery, retry/claim/lease/redrive/retention, and outbox state machines were not changed.
- Handlers remain synchronous and no retired EventSubscriber compatibility surface, codec fallback, or alias was restored.

# Known limitations and risks

- The focused verification does not exercise a live external RabbitMQ/RocketMQ broker; existing transport owner tests cover envelope construction and subscriber/publisher execution with in-process fixtures.
- Jackson's mapper is intentionally the single active Runtime codec. Breaking removal of FastJSON/Gson aliases and fallback paths is part of this branch's contract; consumers must use the current Runtime JSON APIs.

# Conclusion

Pass. The active Runtime JSON surfaces are unified on Jackson, the requested round-trip and safety contracts are covered by focused tests and the complete repository check, and no retired Runtime boundaries or transport semantics were reintroduced.
