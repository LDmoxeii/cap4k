# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0689a9a15a5f825846b098497ddbbf6c3ccf25b10ffb4a2c9625cb8a6da35cdf",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/CommandRecord.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/CommandSupervisor.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/ReliableCommandSupervisor.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/command/impl/DefaultReliableCommandSupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-1af8c735eab9bf1d0438abcdea6d399693dfb72a485d3ee67f2fc3cbc76aa50e",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/CommandRecordImplTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventPublisher.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/ReliableFailureFacts.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-3d6a36d3a2cc89f6fe81bc050eae6e25feaad09f79b4337225da0abe76d8102b",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/main/kotlin/com/only4/cap4k/ddd/application/command/CommandJpaAutoConfiguration.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventJpaAutoConfiguration.kt",
      "ddd-application-command-jpa/src/main/resources/command.sql",
      "ddd-domain-event-jpa/src/main/resources/event.sql",
      "docs/public/reference/runtime-database-schema.md"
    ]
  },
  {
    "acceptance_id": "acceptance-750400274f3d0a034e1a9049274cdfcff0e474b90ec878da207a76d8de5dd5e5",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/JpaCommandRecordRepositoryTest.kt",
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/JpaCommandScheduleServiceTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/CommandRecordRepository.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/EventRecordRepository.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/JpaEventRecordRepositoryTest.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/JpaEventScheduleServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-bac9ad557002e4295b9a9994a5deefade18d5157102fbcc57d1ab5349deb1471",
    "evidence_refs": [
      "ddd-application-command-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRecordEntity.kt",
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/CommandRecordImplTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/ReliableFailureFacts.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew :ddd-core:test :ddd-application-command-jpa:test :ddd-domain-event-jpa:test :cap4k-ddd-command-jpa-starter:test :cap4k-ddd-domain-event-jpa-starter:test --no-daemon`: passed.
- `./gradlew check --no-daemon`: completed 224 pipeline Gradle tests with two transient failures after all changed Core, Command, Event, and Starter tests passed. One failure was a Plugin Portal TLS handshake interruption; the other was a nested Gradle Tooling connection interruption.
- `./gradlew :cap4k-plugin-pipeline-gradle:test --tests 'com.only4.cap4k.plugin.pipeline.gradle.CompositeBuildConsumerFunctionalTest' --no-daemon --no-configuration-cache`: passed on isolated rerun.
- `./gradlew :cap4k-plugin-pipeline-gradle:test --tests 'com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.generated quoted mixed case entity completes hibernate soft delete lifecycle' --no-daemon --no-configuration-cache`: passed on isolated rerun.
- `git grep -nE 'ArchivedCommandRecord|ArchivedEvent|archiveByExpireAt|__archived_command|__archived_event|CommandSupervisor\.result|ReliableCommandSupervisor\.result|result_type' -- ':!docs/superpowers/**' ':!docs/comet/archive/**' ':!docs/comet/changes/**'`: exited 1 with no matches, as expected.
- `git diff --check`: passed.
- `comet native check runtime-safe-failure-result-repository`: passed with receipt `runtime/evidence/check-receipts/a46e66e1809631a1269c25f4819458e7da9e182b1487c8121c8eefe42fca32d9.json`.

# Skipped checks

- The existing real PostgreSQL integration test remained skipped because its external PostgreSQL evidence environment was unavailable; this slice changes only Command/Event reliable record tables and does not alter aggregate soft-delete behavior.
- No compatibility migration was executed because the confirmed contract intentionally removes obsolete tables and APIs without a compatibility bridge.

# Spec consistency

The implementation matches the confirmed brief: shared failure facts contain only controlled safe data; reliable Command result polling and embedded result persistence are removed; Command/Event archive storage and scheduling are removed; active retry scans remain. No retry-policy snapshot, claim/lease, transport, payload-rule, or broader state-machine behavior was added.

# Known limitations and risks

The full repository check did not finish green in one invocation because two unrelated nested-build tests encountered transient external TLS and Gradle Tooling connection failures. Both exact failures passed when rerun independently. Existing retry scans remain intentionally non-atomic until the dedicated reliable JPA substrate slice replaces them.

# Conclusion

Pass. The acceptance examples are covered by implementation, focused persistence tests, static absence checks, isolated regression reruns, and the Comet text-hygiene receipt.