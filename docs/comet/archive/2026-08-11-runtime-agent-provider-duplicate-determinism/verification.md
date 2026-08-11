# Acceptance evidence
<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0568e8d076066cd6a868ae2fc1877c84ae241a9b5f22cbe52595cd1649a00090",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-95eedb0b746e7bb679279f6b5ff3eadf29d7eee58e33e337ba7e069eac7cd632",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotService.kt",
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-b7e588db95451a2b77f23e5626274faa7b073460438e779b46183d1a6b1f71ce",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c6f66e216c5c7251a9192dfd19c97edae0385a45cf958821d5b3bb76561a13a0",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotService.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->
# Commands and results

- `.\gradlew.bat :cap4k-plugin-pipeline-agent:test --tests "*AgentSnapshotServiceTest" --no-daemon` — the first attempt reached test compilation and failed because the new test used the wrong named codec parameter (`pluginVersion` instead of `cap4kVersion`); the test-only call was corrected and the rerun passed all 9 `AgentSnapshotServiceTest` cases.
- `.\gradlew.bat :cap4k-plugin-pipeline-agent:test --tests "*RuntimeAgentFactsCatalogTest" --tests "*AgentSnapshotServiceTest" --tests "*AgentSnapshotCodecTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kAgentSnapshotTaskTest" --no-daemon` — passed. The mixed duplicate test proves both input orders are `INVALID`, produce identical diagnostic IDs/messages/counts and Runtime reason, and encode byte-identical `runtime.json`, `diagnostics.json`, `manifest.json`, and snapshot hash.
- `& .\scripts\validate-current-runtime-facts.ps1` — passed: current Runtime facts contain no retired Runtime surfaces.
- `git diff --check` — passed; Git emitted only line-ending conversion warnings.
- `.\gradlew.bat check --no-daemon` — passed in 10m 26s; 188 actionable tasks, 3 executed and 185 up-to-date.
- `comet native check runtime-agent-provider-duplicate-determinism` — passed; receipt `runtime/evidence/check-receipts/1dc6d63c9154937e929175f6255c7f2e25ac94315ac55920a306684de0f860b8.json`.

# Skipped checks

- The repository's real PostgreSQL soft-delete integration test was skipped because no external PostgreSQL environment was configured. It is outside this Runtime Agent API policy slice.
- The required GitHub `check` cannot run until this change is committed and pushed to PR #183; it will be observed after the Comet archive is committed and the branch is pushed.

# Spec consistency

`RuntimeAgentFactsPolicy` now groups every provider fact by normalized provider identity and validates each capability reference against the complete candidate set. A reference is valid only when the candidate set is non-empty and every candidate targets that capability. Therefore two providers that normalize to `provider.shared` but target `runtime.a` and `runtime.b` produce one duplicate-provider diagnostic plus one stable capability-provider mismatch for each referencing capability, independent of list order.

The existing duplicate-provider-same-capability rejection test remains. `AgentSnapshotService` always prefixes an invalid static Runtime catalog with `The static Runtime fact catalog is invalid.` and appends a distinct pre-existing Runtime reason only as supplemental context. No capability catalog, provider catalog, Agent contract v3, Runtime registry, retired identity guard, transport, state machine, repository, Analyzer, or Actuator boundary changed.

# Known limitations and risks

- The policy diagnoses inconsistent static facts; it does not repair or select among duplicate providers.
- Live provider state remains outside this static catalog validation and continues to come only from `RuntimeProviderStateRegistry`.
- GitHub branch-protection evidence is pending until push, as recorded under skipped checks.

# Conclusion

PASS. All four Runtime-provided acceptance items have project-relative evidence. The mixed duplicate provider association is deterministic by semantics rather than by ordering before a last-write-wins map, and both section diagnostics and encoded snapshot bytes are stable under reversed inputs.