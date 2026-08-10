# Acceptance evidence

The implementation covers the shared provider-state contract and the RocketMQ-specific route, publisher, subscription, consumer-group, envelope, delivery-context, acknowledgement, and diagnostic boundaries.

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0495ef280ad76a56846fcd9b2d8b908cef3e9165a36d9e2297625762a22c1ea2",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-08433c2e81dd1a6394eaedbb638aefd49dab4613bd3fef5966deaf165e930244",
    "evidence_refs": [
      "cap4k-ddd-integration-event-rocketmq-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/RocketMqIntegrationEventPropertiesTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventRouteTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-0941dee252529d9af12ba01af2ef642bc008c1c6bd15b30799ef3ef1100e8c57",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-0f8136da84b19a0eb678f8c95bb5f29a8f256e6fc04310d4a3ccc6764d138a20",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventRoute.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventRouteTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-101d6525f922f6f7f980cac7d1ab49aeeec37003891824b2f989ce1e07a5bbd9",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt",
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-635bcb4a8cad3640a7823119d550e794731fd63c36b29764f936b851ec4440e8",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisher.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-e90595cc46d80425eff4957066e37f3c2b336b8ff866bf3934b6d556a8b60a0f",
    "evidence_refs": [
      "ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapter.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventExecutionContextTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventSubscriberAdapterTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `comet native check runtime-rocketmq-transport` passed. The receipt is `runtime/evidence/check-receipts/417396463bf955d4210a6a5656fb18036939d36d6d7d609a0f0d55307a57fda8.json`; the scoped text-safety check scanned 16 files and recorded zero issues.
- Focused Core, Core Starter, RocketMQ adapter, and RocketMQ Starter tests passed.
- `./gradlew.bat check --no-daemon` passed with `BUILD SUCCESSFUL in 8m 28s` (`211 actionable tasks: 123 executed, 58 from cache, 30 up-to-date`). The only skipped test was the existing environment-dependent PostgreSQL soft-delete integration test.
- `git diff --check` passed.
- Static production-source scans found no remaining `RocketMqIntegrationEventConfigure`, annotation/per-topic topology fallback, arbitrary consumer parser, or raw payload logging path.
- Implementation commits are `b26dc87e` (transport-neutral provider-state registry and once-only completion result) and `0dacbd81` (RocketMQ route, publisher, subscriber, starter, and focused tests).

# Skipped checks

- No live RocketMQ broker was available in the verification environment, so broker handoff, consumer redelivery, and reconnect behavior were not exercised against a running nameserver/broker.
- Reconnect after a successfully started consumer remains delegated to the official RocketMQ push-consumer SDK; cap4k does not add a second reconnect loop.
- The PostgreSQL soft-delete integration test was skipped by the existing local environment guard; this is unrelated to the RocketMQ change.

# Spec consistency

The implementation follows the shared transport foundation and Integration Event core contracts. RocketMQ only maps provider metadata and topology: route resolution, Consumer Group derivation, SDK send status, consumer acknowledgement, redelivery metadata, and provider-state evidence. Reliable Event ownership, claim/lease/retry semantics, canonical Jackson envelope encoding, once-only completion, execution context, and managed Handler-scope joining remain Runtime-owned shared behavior. No RocketMQ-specific durable delivery record, retry loop, inbox, DLQ, consumer enumeration, sender-side downstream acknowledgement aggregation, or global ordering promise was added.

# Known limitations and risks

- `RuntimeProviderStateRegistry` is an in-memory current-fact registry. It intentionally has no persisted history, TTL, Actuator projection, or cross-component aggregation in this slice.
- The subscriber reports healthy provider evidence when a broker delivery reaches the adapter. Decode, Handler, and scoped-operation failures remain delivery failures and are returned as `RECONSUME_LATER`; they do not falsely degrade the transport itself.
- A process loss after broker handoff but before the reliable record acknowledgement can produce an allowed at-least-once duplicate.
- RabbitMQ publisher-confirm limitations remain outside this RocketMQ slice and are not changed here.

# Conclusion

PASS. The RocketMQ provider now satisfies the confirmed Batch 4 transport contract within the available evidence scope: explicit typed routes, deterministic Consumer Groups, `SEND_OK`-only positive handoff, shared once-only completion, canonical envelope and delivery context, full local Handler acknowledgement boundary, and transport-neutral live provider-state facts. The remaining skipped checks are environmental/live-broker coverage, not unresolved contract decisions or known deterministic defects.
