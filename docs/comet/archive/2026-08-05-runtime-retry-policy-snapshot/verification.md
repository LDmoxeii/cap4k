# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-179bd85a9b87e5b98af53e0bafe5891e99c6e3b3afc02a7e2cbc9497eff4e1fe",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRetryPolicySnapshotTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/retry/ReliableRetryPolicySnapshotTest.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventRetryPolicySnapshotTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-1aa16117aa8290521b4b68dc23ca44f6935288a10804368f3c6070860c481261",
    "evidence_refs": [
      "ddd-application-command-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/command/persistence/ArchivedCommandRecordEntity.kt",
      "ddd-application-command-jpa/src/main/resources/command.sql",
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRetryPolicySnapshotTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/retry/ReliableRetryPolicySnapshotTest.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/ArchivedEvent.kt",
      "ddd-domain-event-jpa/src/main/resources/event.sql",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventRetryPolicySnapshotTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d24e47a5d470ed8edcbac05c6e73ebd14427d789b7dcdfe04f3ab0f81d669686",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRetryPolicySnapshotTest.kt",
      "ddd-domain-event-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventRetryPolicySnapshotTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew :ddd-core:test :ddd-application-command-jpa:test :ddd-domain-event-jpa:test --no-daemon` — passed after correcting one compile-time nullable `IntArray` handling error. All three capability-owner module suites passed, including the new snapshot, archive, payload-confidentiality, and annotation-mutation tests.
- `git diff --check` — passed; Git reported only the repository's expected Windows LF-to-CRLF checkout warnings.
- `git grep -n "getAnnotation(Retry::class.java)" -- ddd-application-command-jpa/src/main ddd-domain-event-jpa/src/main` — passed; annotation reads remain only at initial Command/Event capture and no longer occur during retry calculation.
- `git grep -n "retry_policy" -- ddd-application-command-jpa/src/main/resources/command.sql ddd-domain-event-jpa/src/main/resources/event.sql` — passed; active and archive schemas for both reliable record types contain the mandatory snapshot column.
- Independent read-only implementation review — passed with no contract defects found.

# Skipped checks

- Repository-wide `./gradlew check --no-daemon` was attempted but could not complete configuration because Maven Central TLS handshakes failed while downloading unrelated RocketMQ starter transitive dependencies (`opentelemetry-sdk-extension-autoconfigure-spi` and `perfmark-api`). No repository-wide test task ran in that attempt; PR CI must rerun the required `check` job in its normal network environment.

# Spec consistency

The implementation matches the complete target specification: policy version, retry limit, `ANY_EXCEPTION` classification, and the effective delay curve are captured once; active and archived records persist the same JSON snapshot; retry calculation reads only that snapshot. The reliable state machines, attempt-count baselines, retry defaults, and persisted-entity Event payload rejection remain unchanged.

# Known limitations and risks

- Backward compatibility is intentionally absent. Existing database rows without a valid `retry_policy` value cannot be redelivered and deployments must apply the breaking schema reset/migration expected by the project contract.
- Exception classification is persisted as `ANY_EXCEPTION` because that is the only current runtime semantic; this slice does not introduce a configurable classifier.
- Repository-wide verification still depends on PR CI because the local Maven TLS failure prevented resolving unrelated transport dependencies.

# Conclusion

Pass for the `runtime-retry-policy-snapshot` slice. Focused capability-owner tests and static boundary checks demonstrate immutable capture, persistence/archive round trips, configuration-mutation immunity, and absence of raw business payload in the snapshot contract.
