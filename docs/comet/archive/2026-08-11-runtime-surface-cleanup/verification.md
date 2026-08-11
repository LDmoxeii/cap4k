# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1e7f0756ec626a5df05ee4ee116fd2d2cfdd14dd26b8ba97102fcafe7d91f8fc",
    "evidence_refs": [
      "scripts/validate-current-runtime-facts.ps1",
      "settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-24c591b997b0d927f361996844c7f99bba1a3a8ee8619419f675f54ec59f8ebc",
    "evidence_refs": [
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventPublisherTest.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventPublisherTest.kt",
      "ddd-integration-event-rocketmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisherTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-32dbf4a11a4fa43e25a9439c25925546e5a715b39c8d4338b505fa12d711d982",
    "evidence_refs": [
      "docs/superpowers/analysis/architecture-map.md",
      "docs/superpowers/analysis/runtime-and-integration-map.md",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-6cec1ff3458df0444afffde9a4fe4f99fde6c679e246790557c96e03b2a5e8d5",
    "evidence_refs": [
      "docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-77f4bf74211d47d8feffd95b364cc06f376d492919e65d40a5a9c205aea09f4e",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicyTest.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-7d16745967b25d7810b3065996de12953fe93416f0d109ea1bd31d022af935af",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7ed1722854b90419ec9f8ddb0acc32be76940c1b587d1de15b5c596dba2eba7f",
    "evidence_refs": [
      "ddd-application-command-jpa/src/main/resources/command.sql",
      "ddd-domain-event-jpa/src/main/resources/event.sql",
      "docs/public/reference/runtime-database-schema.md"
    ]
  },
  {
    "acceptance_id": "acceptance-ad58d34b0701816e3a2e8ed206898812237120325f6caf98233c994bda64afa1",
    "evidence_refs": [
      "docs/superpowers/analysis/runtime-and-integration-map.md",
      "scripts/validate-current-runtime-facts.ps1",
      "settings.gradle.kts"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `.\gradlew.bat projects --quiet` passed and listed neither `ddd-distributed-locker-jdbc` nor `cap4k-ddd-locker-jdbc-starter`.
- `.\gradlew.bat :cap4k-ddd-command-jpa-starter:dependencies --configuration runtimeClasspath :cap4k-ddd-domain-event-jpa-starter:dependencies --configuration runtimeClasspath --console=plain` passed; neither Runtime classpath contains a Locker project or module.
- `.\gradlew.bat :ddd-core:test :cap4k-ddd-command-jpa-starter:test :cap4k-ddd-jpa-starter:test :cap4k-ddd-integration-event-http-starter:test :cap4k-ddd-integration-event-rabbitmq-starter:test :cap4k-ddd-integration-event-rocketmq-starter:test :cap4k-plugin-pipeline-agent:test --console=plain` passed in 1m 44s. This covers required-provider boundaries, reliable state, transport composition, and retired descriptor rejection.
- `.\gradlew.bat check --console=plain` passed on August 11, 2026: `BUILD SUCCESSFUL in 10m 19s`, 197 actionable tasks, with the environment-guarded PostgreSQL soft-delete fixture skipped.
- `pwsh -NoProfile -File scripts/validate-current-runtime-facts.ps1` passed after scanning current public/spec/analysis facts plus active build, source, Spring imports, version catalogs, and common descriptor resource formats.
- `git diff --name-status "$(git merge-base origin/master HEAD)"..HEAD -- docs/comet/archive docs/comet/runtime docs/superpowers/specs docs/superpowers/plans` returned only `A` records for this change's own `docs/comet/archive/2026-08-11-runtime-surface-cleanup/**` and `docs/comet/runtime/transactions/83addc72-e2cd-448a-a684-fcb02933e0dc/**` evidence. It returned no `M`, `D`, or `R` operation against a historical file that already existed at the merge-base.
- `git diff --check origin/master` passed. Git emitted line-ending conversion warnings only and no whitespace errors.
- `comet native check runtime-surface-cleanup --json` passed with zero findings; the immutable receipt is supplied separately to the Verify transition to avoid a self-referential report hash.

# Skipped checks

- No live RabbitMQ or RocketMQ broker was started. Existing deterministic transport tests cover route validation, publisher completion, acknowledgement, provider state, and recovery boundaries without claiming broker-backed smoke evidence.
- The PostgreSQL Testcontainers soft-delete fixture remained skipped by its existing environment guard during the full repository check.

# Spec consistency

- The implementation removes the public Locker SPI, JDBC implementation module, starter, auto-configuration, properties, Spring import, SQL schema, tests, and Gradle project entries without adding a compatibility alias or replacement coordination abstraction.
- Reliable Command/Event state-machine source was not changed. Existing atomic claim, lease, token fencing, retry snapshot, manual redrive, retention, acknowledgement, and transport composition tests all remained green.
- HTTP remains the lightweight experience transport; HTTP, RabbitMQ, and RocketMQ modules and starters remain active and verified.
- Current public and maintenance facts no longer present Locker as supported. This change adds its own Comet archive and transaction evidence; historical files that already existed at the merge-base were not modified, deleted, or renamed, and no Superpowers specification or plan was changed.
- `RetiredRuntimeDescriptorPolicy` continues to reject `console`, `locker`, `saga`, and `snowflake`; negative retirement evidence remains part of the current boundary.

# Known limitations and risks

- The current-runtime-facts guard is a deterministic textual regression guard. It detects the retired module, class, configuration, SQL, JSON-stack, and descriptor patterns in active carriers, but it is not a substitute for semantic review of newly invented aliases.
- Historical evidence may still contain active-looking Locker examples because it records earlier contracts. Those paths are intentionally excluded from current product facts and remain discoverable as history.
- This is a breaking retirement with no migration bridge. Any internal consumer still depending on the deleted Locker modules must remove that dependency and rely on the Runtime-owned reliable execution substrate.
- Live broker smoke tests remain separate deployment evidence; this cleanup proves no transport regression through the existing deterministic suites and full repository check.

# Conclusion

Pass. The active Locker capability is fully retired from the Runtime surface, current facts and schema are reconciled, retired-surface guards are stronger, and the reliable execution plus three supported Integration Event transports remain green under focused and full-repository verification.
