# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0b0b364c581f5b209c41be53484d5cbc249fde22b30aba709d6312865763658a",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelopeCodec.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-60780293bec01d87fdff9c1caa08908b527f1482f33396691b5f4dbc2a5c49b1",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelopeCodecTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-809e68399d3786bbeacf87c5ff73842fceff2902c6913cf16399f0644f953ea4",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelopeCodecTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-843d267a61a7bb54ae4ec8fe913c004917d8e3e83d6c1200c835ae3a535ae4e2",
    "evidence_refs": [
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventDeliveryContextTest.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventSubscriberAdapterTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapterTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f5208bcd06159d614b2897f67f5d97651d20a50b9309657ead97ac06eddc9645",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelopeCodecTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :ddd-integration-event-rocketmq:test --no-daemon`: passed the RocketMQ adapter and publisher suite, including the synchronous-send failure callback that returns the reliable record to retryable state.
- `.\gradlew.bat :ddd-core:test :ddd-domain-event-jpa:test :ddd-integration-event-http:test :ddd-integration-event-rabbitmq:test :ddd-integration-event-rocketmq:test --no-daemon`: passed the focused core, durable record, HTTP, RabbitMQ, and RocketMQ test matrix after the canonical envelope migration.
- `.\gradlew.bat :cap4k-ddd-integration-event-http-starter:test --tests com.only4.cap4k.ddd.application.event.HttpIntegrationEventConsumeHandlerTest --no-daemon`: passed the canonical HTTP consume endpoint contract, including rejection of malformed envelope metadata and ignoring retired query/header metadata.
- `.\gradlew.bat check --no-daemon`: passed the complete repository check in 7 minutes 12 seconds; 212 actionable tasks, 74 executed and 138 up-to-date.
- `git diff --check`: passed with no whitespace errors; Git reported only repository line-ending conversion advisories.
- Active source/test `rg` audit across the six implementation modules found no remaining `IntegrationEventExecutionContextEnvelope` reference.
- `comet native check runtime-integration-event-core`: passed. Receipt: `runtime/evidence/check-receipts/227f58431852192cd7b35998ee6bb7c355ac6328078d3885bc4950a8c8634b39.json`.

# Skipped checks

- No real HTTP process pair, RabbitMQ broker, or RocketMQ broker was started. Batch 3C verifies the shared envelope, serialization, adapter mapping, and delivery-context boundary; transport routing, publisher confirmation, broker acknowledgement, and subscription behavior remain the dedicated transport batch.
- No process-kill fault injection was run between an external transport side effect and the durable completion callback. The runtime remains explicitly at-least-once.

# Spec consistency

- One immutable `IntegrationEventEnvelope` now owns event identity, wire event type, origin service, publication time, optional delivery attempt, encoded execution context, and payload JSON.
- `IntegrationEventEnvelopeCodec` is the single Runtime Jackson boundary for outbound record projection, deterministic wire encoding, safe decoding, payload type validation, Strong ID handling, and aggregate/entity rejection.
- `DefaultEventPublisher` creates the envelope once before handing it to the single configured transport provider; providers no longer reconstruct transport-private execution-context envelopes.
- HTTP, RabbitMQ, and RocketMQ subscribers decode the same canonical envelope and install the same reliable delivery facts with an explicit stable subscriber identity.
- HTTP delivery with no configured subscriber is now an explicit publish failure, allowing the reliable Event state machine to retry instead of leaving completion unresolved.
- Transport routes, broker confirmation, consumer acknowledgement, and production topology were not redesigned in this slice.

# Known limitations and risks

- The shared envelope is transport-neutral, but the three transport adapters still own their route, broker metadata, confirmation, and acknowledgement mechanics. Those behaviors require the planned transport-specific implementation and verification batch.
- The sender envelope's reliable delivery attempt is authoritative across transports. When an envelope legitimately lacks that fact, a provider may supply an exact positive fallback attempt; otherwise the value remains null rather than guessed.
- HTTP callback requests still emit legacy timestamp and execution-context headers for transport-level transition convenience, but the canonical consume endpoint ignores them and derives runtime facts from the envelope body.
- At-least-once delivery permits duplicate external effects after process loss or lease expiry; consumers remain responsible for idempotent business handling.

# Conclusion

Pass. The Integration Event core now has one deterministic, entity-safe envelope and codec shared by outbound reliable publication and inbound HTTP, RabbitMQ, and RocketMQ delivery. The old transport-private execution-context envelopes and legacy publisher signature are removed, subscriber identity is explicit, and the complete repository verification passes. Transport production semantics remain intentionally deferred to the next batch.
