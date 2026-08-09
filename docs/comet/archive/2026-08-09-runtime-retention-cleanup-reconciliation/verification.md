# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1a6b4931de0d34068b44cdc51d921a62db3271f0f0e8f4888f495709dcb6b497",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-6242e465678ee8c8c23409adf0cc1bcc4afbe9725db44ec3dd91b4dbaedc02dd",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-70c83735d946e5a9fa4ed9b22da5243d10f0ade651ae016f48ef2831fb5f977b",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8a5824c320b1a66302a3b9e234fc1eeb71961a4878b11b8e1773397c45a67a2b",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt",
      "ddd-application-command-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRecordEntity.kt",
      "ddd-application-command-jpa/src/main/resources/command.sql",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/Event.kt",
      "ddd-domain-event-jpa/src/main/resources/event.sql"
    ]
  },
  {
    "acceptance_id": "acceptance-99a21bb57dccd6ef92f26f0fa74eeef4c376b0479e27c47bdddf850c7f9613db",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt",
      "ddd-application-command-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRecordJpaRepository.kt",
      "ddd-domain-event-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/event/persistence/EventJpaRepository.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f34da0d84fe3e1d21ea53ef89e8fd9738bad35a47c86c8d5836b45b18b709cc3",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f5b26331b077e19e66ae663f1dc5d54a53a26d8ea3c80bb2179ca80bfecfb81e",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-ddd-command-jpa-starter:test --tests com.only4.cap4k.ddd.starter.command.JpaCommandAtomicClaimIntegrationTest --tests com.only4.cap4k.ddd.starter.command.JpaCommandRetentionCleanupIntegrationTest :cap4k-ddd-domain-event-jpa-starter:test --tests com.only4.cap4k.ddd.starter.event.JpaEventAtomicClaimIntegrationTest --tests com.only4.cap4k.ddd.starter.event.JpaEventRetentionCleanupIntegrationTest --no-daemon` passed: Command and Event atomic claim, Manual Redrive, carrier/SQL parity, retention eligibility, bounded cleanup, final delete, and concurrent-redrive cases all passed.
- `./gradlew.bat :cap4k-ddd-command-jpa-starter:test :cap4k-ddd-domain-event-jpa-starter:test --no-daemon` passed in 19 seconds.
- `./gradlew.bat check --no-daemon` passed in 11 minutes 3 seconds: 221 actionable tasks completed with no failed checks.
- `git diff --check` passed with no whitespace errors.
- `comet native check runtime-retention-cleanup-reconciliation` passed with receipt `runtime/evidence/check-receipts/542967a218131a82e41a4cd0e7f6700f902f264200b6bfd1bcb946021b3a7201.json`.

# Skipped checks

None.

# Spec consistency

The implemented Command and Event queries now require `EXHAUSTED.expireAt <= now` in both candidate selection and final delete. Successful, expired, exhausted, ineligible-state, live-lease, service-isolation, bounded, repeated-call, and stale-candidate cases are covered by real JPA integration tests. Manual Redrive keeps its expected-state/version/service/lease CAS and preserves retry-policy, failure-fact, and original-expiry semantics while clearing terminalization on a successful reset.

# Known limitations and risks

Cleanup is intentionally explicit and private: this reconciliation adds no automatic retention scheduler, payload archive, generic task framework, or transport behavior. As with any at-least-once durable delivery cleanup, an operator must choose retention durations that preserve the desired diagnostic history.

# Conclusion

Pass. The reconciled retention target preserves Manual Redrive eligibility for future-expiry exhausted records, physically removes only terminal records that cannot be redriven, and has fresh focused, owner-suite, root-check, diff-hygiene, and Comet-check evidence.
