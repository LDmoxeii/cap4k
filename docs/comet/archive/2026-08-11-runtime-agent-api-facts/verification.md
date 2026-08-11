# Acceptance evidence
<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-06083e652180cf9e1ecd8f8c92e46bbf0b07ab562e9790ce73d8253df72b30dd",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-90accafbb2a94e759c9e4a57cdf116fe71cecc0cbd4d481e2a04104399d7ca88",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotCodec.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotCodecTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-9d27c431752a0337e782971a013644ce30fca51d17634fce0b45c5a7f7fb2e44",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-b76a8b29e7d3a67ea72464c3576761fe2f4e139387d4c6fcd1c2b2cf633f2294",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalog.kt",
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/AgentContracts.kt",
      "cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTask.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-cb03eb1d358d165409e9dc3945faac91ba9049b9670c428188a70c50a992e90e",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalog.kt",
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsPolicy.kt",
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/AgentContracts.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d07237a19a1ea229eaf4dcf3a61c4d1f6089c226692043bd547c4aa067edbc74",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalogTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTaskTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->
# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-agent:compileKotlin :cap4k-plugin-pipeline-gradle:compileKotlin --no-daemon` — passed.
- `./gradlew.bat :cap4k-plugin-pipeline-agent:test --tests "*RuntimeAgentFactsCatalogTest" --tests "*AgentSnapshotServiceTest" --tests "*AgentSnapshotCodecTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kAgentSnapshotTaskTest" --no-daemon` — passed after correcting one test assertion so it verifies the contract's sorted output rather than input order.
- `./gradlew.bat :ddd-core:test --tests "*RuntimeProviderStateTest" :cap4k-ddd-integration-event-http-starter:test :cap4k-ddd-integration-event-rabbitmq-starter:test :cap4k-ddd-integration-event-rocketmq-starter:test --no-daemon` — passed.
- `pwsh -NoProfile -File scripts/validate-current-runtime-facts.ps1` — passed. The script also self-checks that its forbidden-state regex rejects `HEALTHY`; a direct negative proof covered `HEALTHY`, `DEGRADED`, `RECOVERING`, and `SUCCESS`.
- `./gradlew.bat check --no-daemon` — passed in 14m 3s; 197 actionable tasks, 82 executed, 13 from cache, 102 up-to-date.
- `git diff --check` — passed; Git reported only line-ending conversion warnings.
- `comet native check runtime-agent-api-facts --json` — passed; receipt `runtime/evidence/check-receipts/66e2b6a794e402879adf6b85fcb7f7c8f637132ee9e75afb7e41b7797c8f750a.json`, 13 files scanned, 0 issues.

# Skipped checks

- The existing real PostgreSQL soft-delete integration test was skipped by the repository's full Gradle suite because no external PostgreSQL environment was configured. It is outside this Runtime Agent API slice.
- No real application assembly or external broker health probe was performed. Static facts intentionally report application assembly and operational state as `UNKNOWN`, and observation/verification as `NOT_PERFORMED`; live state remains owned by `RuntimeProviderStateRegistry`.
- No Actuator exposure was tested because the confirmed contract explicitly does not add an Actuator adapter.

# Spec consistency

The implementation publishes exactly seven Runtime capability IDs and three Integration Event provider IDs. Capability/provider ownership is explicit, catalog order is normalized before stable Jackson encoding, duplicate and retired identities invalidate the snapshot deterministically, and static facts cannot represent live health or execution success. The Gradle task does not read or copy `RuntimeProviderStateRegistry`; it only points to that registry as the authoritative live source.

The three provider IDs were checked against the landed HTTP, RabbitMQ, and RocketMQ registration constants. Console, Snowflake, Locker, and Saga remain rejected by `RetiredRuntimeDescriptorPolicy` and by the current-runtime-facts validation script.

# Known limitations and risks

- Static output cannot establish whether an application assembled a provider or whether a provider was healthy at a given instant. This is intentional and represented by `UNKNOWN` / `NOT_PERFORMED`, not inferred success.
- The optional read-only Actuator adapter remains unimplemented by decision. If added later, it must delegate each read directly to `RuntimeProviderStateRegistry.snapshot()` and cannot become a second state source.
- The independent read-only review found an invalid U+0008 regex boundary in the validation script. The guard was repaired to use literal `\b` boundaries, all U+0008 bytes were removed, and the prohibited-state negative checks now pass.

# Conclusion

PASS. All six Runtime-provided acceptance items have project-relative evidence and none is marked skipped. Focused Agent API tests, provider registry/transport tests, the complete Gradle suite, runtime-fact validation, diff hygiene, and the Comet scoped text-safety check passed. External application assembly and live provider health remain deliberately unobserved and are represented truthfully as `UNKNOWN` / `NOT_PERFORMED`.