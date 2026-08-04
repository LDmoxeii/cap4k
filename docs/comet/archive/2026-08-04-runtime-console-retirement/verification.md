# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1fef2c23420980745556d199fc08f407c395b697ca96f51c087aa422220970d4",
    "evidence_refs": [
      "cap4k-ddd-locker-jdbc-starter/build.gradle.kts",
      "cap4k-ddd-snowflake-starter/build.gradle.kts",
      "settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-4f510d547d9d5c3f9f5f40d6d303dc5d00237e530ae5459722bdbdf7798cc081",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/CommandManager.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/EventPublisher.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7d7f6c22453859b1d850c07acb19f38c14e9aa379ccabc8930d2da24a49e57ef",
    "evidence_refs": [
      "scripts/validate-current-runtime-facts.ps1",
      "settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-8465b4c2679a7c945eeb4d448ccb610bff52df29419f2a0736e30ce89b14bafc",
    "evidence_refs": [
      "docs/public/concepts/execution-and-ownership/mediator.md",
      "docs/superpowers/analysis/architecture-map.md",
      "docs/superpowers/specs/2026-07-25-cap4k-official-default-project-and-runtime-boundaries-design.md",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-d5336768c03c31b6d7470d52777214baed1751cc6fb51ff9b26b6150338dc1e5",
    "evidence_refs": [
      "settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-ed4e4fc8c5672c244476456f6e8ec8b7cfcd355709ce5cbf39be1fd47c1f1e1d",
    "evidence_refs": [
      "scripts/validate-current-runtime-facts.ps1",
      "settings.gradle.kts"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.application.command.impl.DefaultReliableCommandSupervisorTest" --tests "com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventPublisherTest" :cap4k-ddd-locker-jdbc-starter:test :cap4k-ddd-snowflake-starter:test --console=plain` — passed (`BUILD SUCCESSFUL`); this covers the retained programmatic Command/Event recovery behavior and the retained Locker/Snowflake starters.
- Repository stale-surface checks — passed: the `cap4k-ddd-console` directory is absent; active build/code/test/public-doc/Agent-fact searches found no Console module, package, auto-configuration, or `/cap4k/console` endpoint; `git diff --quiet origin/master` confirmed the Command/Event runtime, reliable state machine, Locker Runtime, and Snowflake Runtime modules are unchanged; `CommandManager.retry(id)` and `EventPublisher.retry(uuid)` remain present.
- `./scripts/validate-current-runtime-facts.ps1` — passed with `OK: current runtime facts contain no retired Runtime surfaces.`
- `./scripts/test-pr-workflow.ps1` — passed with `OK: PR workflow script tests passed.`
- `./gradlew.bat -p buildSrc test --console=plain` — passed (`BUILD SUCCESSFUL in 1m 7s`).
- First `./gradlew.bat check --console=plain` run — all relevant project checks passed, but the TestKit sub-build for `generated quoted mixed case entity completes hibernate soft delete lifecycle()` failed while downloading H2 because Maven Central terminated a TLS handshake. This was an external dependency-resolution failure, not a product assertion failure.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.generated quoted mixed case entity completes hibernate soft delete lifecycle" --console=plain` — passed on retry (`BUILD SUCCESSFUL in 2m 23s`).
- Final `./gradlew.bat check --console=plain` — passed (`BUILD SUCCESSFUL in 26m 9s`, 222 actionable tasks, configuration cache reused).
- `comet native check runtime-console-retirement --json` — passed with zero scoped-text-safety issues; receipt `runtime/evidence/check-receipts/a433b284404d456ee4c8deb077538af5f45c9a9d6780d9ad44b656efbdf44b7a.json`.
- `git diff HEAD --check` — passed with no whitespace errors.

# Skipped checks

- `PostgreSqlSoftDeleteIntegrationTest > real PostgreSQL proves native UUID soft delete from metadata through executed planner SQL()` was skipped by the repository test suite because no PostgreSQL integration environment was configured. It is unrelated to Console retirement; the remaining PostgreSQL cleanup-path tests passed.

# Spec consistency

- The implementation matches the confirmed brief and target specification: the complete Console module, its build registration, Spring Boot auto-configuration, HTTP administration endpoints, direct SQL services, tests, active public documentation, and Agent facts are retired.
- The implementation preserves `CommandManager.retry(id)` and `EventPublisher.retry(uuid)` and does not modify the reliable state machine.
- Locker and Snowflake Runtime modules remain in `settings.gradle.kts` and their focused starter tests pass.
- No alias, deprecated API, fallback codec, dual implementation, or compatibility bridge was added.

# Known limitations and risks

- Comet Native recorded a confirmed partial-scope allowance only for the 12 deleted `cap4k-ddd-console` files. Those files no longer exist as current artifacts and were absent from the Native baseline, so the Runtime could not attribute the removals directly. Git diff, exact stale-surface searches, focused tests, and the full repository check provide the deletion evidence.
- Historical archived Comet material and historical implementation plans are not rewritten; active public docs, current architecture facts, current Runtime boundary specs, and the stale-surface validator are updated.

# Conclusion

Pass. The Runtime Console capability is fully retired from active code, build, tests, auto-configuration, HTTP/SQL administration surfaces, public documentation, and Agent facts while the required programmatic recovery APIs, reliable state machine, Locker Runtime, and Snowflake Runtime remain intact.
