# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-01014d066a594988f4a6425fa023a679e51ef1ddeab77b93f66735eba53c6bf7",
    "evidence_refs": [
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/annotation/IntegrationEvent.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-36cbc56cc67e8004fe7111e201a1dbb225dbbc81995da3f23ffebd1f5af8332f",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfiguration.kt",
      "docs/public/reference/runtime-database-schema.md",
      "settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-7b8ae5b1544b2eb575bc7a460dd692a8e1b8a7ee1bf4af5f0b8624c8e7fce037",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelope.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventEnvelopeCodec.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventPublishCompletion.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventPublishCompletionTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-cda01a0ab72fda5955fc9cb696b9f61e052a1caf5505d432423e12c730de5d11",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/RuntimeProviderBinder.kt",
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventTransportFoundationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d692f76d4f82994fcb2c360b0bdfdb84a665cf57194af0043a90a17a5ebdc550",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/SpringEventTypeCatalog.kt",
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f0e0069f762b0d3d5f13c758e089968db4526b97de8b6ef830310f2769f9642c",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/SpringEventTypeCatalog.kt",
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew check`: passed; 202 actionable tasks, 2 executed and 200 up-to-date.
- Focused transport, core starter, generator, analyzer, and reliable-event composition tests: passed.
- `comet native check shared-transport-foundation`: passed; 41 files scanned, 0 issues, receipt `runtime/evidence/check-receipts/2aca6a60415dd7fdd1906e17c2dce3af9b2ec9a4ccd916fcbc6b7b0dcffaee3c.json`.
- `git diff --check`: passed.

# Skipped checks

- The environment-dependent real PostgreSQL soft-delete integration case remained skipped by its existing test condition. It is unrelated to the Integration Event transport contract.
- Live RabbitMQ and RocketMQ broker smoke tests were not run because provider confirm, acknowledgement, subscription, and reconnect behavior belongs to the subsequent provider-specific changes.

# Spec consistency

The implementation matches the closed Batch 4 shared contract: runtime metadata is event-name-only; inbound registrations are derived from the active catalog and real local synchronous Integration Event handlers; transport composition has one provider slot; canonical envelope, delivery context, and once-only completion remain shared; and HTTP dynamic subscriber registries plus HTTP-JPA persistence are absent. Generator and analyzer projections retain direction only as design metadata.

# Known limitations and risks

This shared foundation does not claim provider-specific publisher confirms, broker acknowledgements, reconnect behavior, or production-grade HTTP delivery. Those semantics remain owned by the HTTP, RabbitMQ, and RocketMQ provider implementation changes. The change intentionally provides no compatibility bridge for the removed `subscriber` annotation field or HTTP registry surfaces.

# Conclusion

Pass. The complete declared implementation scope is covered, the acceptance examples have concrete repository evidence, focused and full Gradle verification pass, and the Comet scoped text-safety check reports no issues.
