# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0dd8e17f6fe8bc5c5a48850d707f4c13f7672cf0d03cc56bfa9f17d239468179",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/JpaReliableCommandWorkerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-16d7b1cde4bd117a31307ebb2388ece54f0ef489b392400f987b654bdbc3599e",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/JpaReliableCommandWorkerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-1837af374da5a4fa50b6c39f7123771c62e3ad0109fe46179f10cb07f2982270",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/ReliableCommandProductionPathIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-6c34d0e503c6a98cc3233a931dd9a6a1abdd80a44e54d2078a6a2239e20070d8",
    "evidence_refs": [
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/command/impl/DefaultReliableCommandSupervisorTest.kt",
      "docs/public/concepts/execution-and-ownership/command.md"
    ]
  },
  {
    "acceptance_id": "acceptance-7f2e92904a3c4b917b605ece9f3f77cef4c8c9a18c3652f2cd21c792b4d2c14a",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/ReliableCommandProductionPathIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-9d294297667ab3e3d13355b8e774136a5d0fa9f1ddee50a0dfdfde07de5e1a90",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c3ac1df66ca383c646ff76a5bbda6bbb9f5d65b5b525c2de0b4f0de277d55276",
    "evidence_refs": [
      "docs/public/concepts/execution-and-ownership/command.md"
    ]
  },
  {
    "acceptance_id": "acceptance-dfd2ef91d154d916c8eb434e54f2f4c2ab9bbe9a6e22a3c699bcd377e7b0cdfe",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/ReliableCommandProductionPathIntegrationTest.kt",
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRetryPolicySnapshotTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :ddd-core:compileKotlin :ddd-application-command-jpa:compileKotlin :cap4k-ddd-core-starter:compileKotlin :cap4k-ddd-command-jpa-starter:compileKotlin --no-daemon` — passed.
- The corresponding four `compileTestKotlin` tasks — passed after the legacy Command tests were migrated.
- `./gradlew.bat :ddd-core:test :ddd-application-command-jpa:test :cap4k-ddd-core-starter:test :cap4k-ddd-command-jpa-starter:test --no-daemon` — passed.
- `./gradlew.bat :ddd-application-command-jpa:test :cap4k-ddd-command-jpa-starter:test --no-daemon` — passed after lifecycle and shutdown fencing changes.
- `./gradlew.bat :cap4k-ddd-command-jpa-starter:test --tests "*ReliableCommandProductionPathIntegrationTest" --no-daemon` — all three real production-path cases passed: commit executes, rollback persists nothing, and the stored retry snapshot reaches a successful second attempt.
- `./gradlew.bat check --no-daemon` — passed in 16m 3s; 221 actionable tasks (118 executed, 61 from cache, 42 up-to-date).
- `git diff --check` — no whitespace errors; Git emitted only local LF-to-CRLF conversion notices.
- Scoped production-source search for `CommandManager`, `command-manager`, `getByNextTryTime`, `JpaCommandScheduleService`, `CommandScheduleProperties`, `CommandProperties`, and Command-side `Locker` usage found no Command legacy path. The only repository-wide matches were the parallel Event contract and the still-shared core `Locker` interface, both outside this change.
- Changed-path search for Domain Event and Integration Event production files returned no matches.

# Skipped checks

- The repository's real PostgreSQL soft-delete integration test remained skipped by its existing environment gate during the full `check`; it is unrelated to the Command state contract.
- No long-running multi-process soak or production database benchmark was run. The state and ownership contract is covered by deterministic H2/JPA concurrency, lease, token-fencing, rollback, retry, lifecycle, and shutdown tests.

# Spec consistency

- `Mediator.commands.send` remains synchronous.
- `enqueue`, `schedule`, and `delay` only register durable work inside the current Unit of Work and transaction; rollback leaves no reliable Command record.
- Execution goes through the private JPA claim/lease/token substrate. Completion, renewal, failure, and retry transitions remain token- and lease-fenced.
- Retry policy and safe failure facts are persisted; result polling and archived result retrieval are not reintroduced.
- `CommandManager`, the Locker polling scheduler, mutable repository execution APIs, partition scheduling options, and direct retry bypasses are removed from the Command path.
- Worker startup occurs after runtime provider binding, while shutdown prevents new claims and leaves interrupted ownership recoverable by lease expiry.
- No Domain Event or Integration Event implementation file was changed; the parallel 3B Event branch retains ownership of that state machine.

# Known limitations and risks

- Manual redrive, retention/cleanup, shared Locker removal, and Event-state convergence belong to later or parallel runtime slices.
- The after-commit wake-up is only a best-effort immediate scan hint. Durable `nextTryTime` polling is the recovery source of truth, so correctness does not depend on an in-memory timer surviving restart.
- Worker sizing and polling properties are operational tuning controls, not a general-purpose task framework or public scheduler contract.

# Conclusion

Pass. The Command half of 3B now uses the unified reliable JPA state substrate end to end, removes the legacy scheduling/result surface, preserves synchronous handler execution, and has repository-wide plus production-path evidence for commit, rollback, retry, lease recovery, ownership fencing, lifecycle ordering, and shutdown safety.
