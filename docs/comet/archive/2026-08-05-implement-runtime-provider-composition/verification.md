# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1dc888a0a1a6cba2482d00e3fbb0eecf1d989ac2d102b74d7a36a79ca574275e",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5917b6cf815724467c3c599a11545a2e11a2c93f2fba69ea2697b830d7cf11ad",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5b5c84c3fa24da19333cc0ffc50417b92c8e7634e1df9643d522a6039967e707",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ce6b2660a6c11d6b1a9113fe120956cfac078111cfd9958592fd254d300f4db5",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/ProviderSlotTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ea459cf608434745c405701fdb99d8cb0703fce32b846db5fded69f9d2d54244",
    "evidence_refs": [
      "cap4k-ddd-core-starter/src/test/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreStarterAutoConfigurationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew :ddd-core:test --tests '*ProviderSlotTest' --tests '*MediatorTest' --tests '*MediatorJavaInteropTest' --offline --no-configuration-cache`: passed. Duplicate registration, exact-owner release, and direct Mediator fixtures all passed.
- `./gradlew :cap4k-ddd-core-starter:test --tests '*CoreStarterAutoConfigurationTest' --offline --no-configuration-cache`: passed all 9 tests. This covers required and optional conflicts, required and optional absence, deterministic bean-name diagnostics, one-provider binding, overlapping-context rejection, and sequential-context release.
- `./gradlew :cap4k-ddd-jpa-starter:test --tests '*JpaStarterBoundaryTest' :cap4k-ddd-domain-event-jpa-starter:test --tests '*DomainEventJpaStarterBoundaryTest' :cap4k-ddd-integration-event-http-starter:test --tests '*HttpIntegrationEventAutoConfigurationTest' :cap4k-ddd-integration-event-rabbitmq-starter:test --tests '*RabbitMqIntegrationEventStarterBoundaryTest' :cap4k-ddd-integration-event-rocketmq-starter:test --tests '*RocketMqIntegrationEventStarterBoundaryTest' --no-configuration-cache`: passed all selected starter boundary tests.
- `./gradlew :cap4k-ddd-jpa-starter:compileKotlin :cap4k-ddd-domain-event-jpa-starter:compileKotlin :cap4k-ddd-integration-event-http-starter:compileKotlin :cap4k-ddd-integration-event-rabbitmq-starter:compileKotlin :cap4k-ddd-integration-event-rocketmq-starter:compileKotlin --no-configuration-cache`: passed.
- `git diff --check` plus production-source scans for `getIfUnique(` and direct `Support.configure(` calls: passed; no prohibited runtime paths remain.
- `./gradlew :cap4k-plugin-pipeline-gradle:test --tests '*CompositeBuildConsumerFunctionalTest' --no-configuration-cache --stacktrace`: passed after an earlier long-running aggregate check reported a transient failure in this fixture.

# Skipped checks

- A single uninterrupted `./gradlew check --no-configuration-cache` result was not obtained. The first run stopped at a transient Maven Central TLS handshake while resolving a KSP test dependency. The retry passed that point and all runtime, analyzer, pipeline, generator, renderer, and source tests shown before a transient Composite Build fixture failure; the remaining long tail was stopped after 24 minutes, and the failed fixture then passed in isolation. Slice-specific tests and every directly affected module were completed successfully.

# Spec consistency

- The implementation enforces at most one provider per slot, rejects required and optional ambiguity during context initialization, sorts conflicting Spring bean identities, and never degrades an ambiguous optional provider to absence.
- Static provider registries reject replacement and release only the exact provider owned by the closing application context. Transport auto-configurations no longer bypass the central binder.
- Routes, handler semantics, reliable record state machines, and broker behavior were not changed.

# Known limitations and risks

- Provider slots remain process-wide static runtime surfaces. Two simultaneously active Spring contexts in one JVM are intentionally rejected; sequential contexts are supported through exact lifecycle release.
- No compatibility bridge was added for last-write-wins registration or `getIfUnique()` behavior, consistent with the repository's breaking-redesign policy.

# Conclusion

PASS. The runtime provider composition contract is implemented and supported by deterministic startup-conflict, absence, registry ownership, lifecycle, starter-boundary, compilation, and consumer-fixture evidence.
