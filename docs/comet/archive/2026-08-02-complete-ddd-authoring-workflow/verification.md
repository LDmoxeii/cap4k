# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-275f851b53ab166bb875e07c403d35c1b9358298e5544860217bb3f27103c72b",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt",
      "skills/cap4k-authoring/references/ownership-boundaries.md"
    ]
  },
  {
    "acceptance_id": "acceptance-2aceddaf0f18c59d3aa0320c10ce1deb5b021b35de89d4ad710e71217aee70a6",
    "evidence_refs": [
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/AgentContracts.kt",
      "cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTask.kt",
      "skills/cap4k-authoring/references/runtime-analysis-boundaries.md"
    ]
  },
  {
    "acceptance_id": "acceptance-470346ae1d696fb328a1b71bbeb585a7bc570f78a9377f0fcc65979e7539b0c9",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentCredentialRedactor.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentHashingIdentityTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTaskTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-48cc6184e22ed8bec425939a52fdca80b951b07f9638eda856e179d037326269",
    "evidence_refs": [
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineCapabilityDescriptors.kt",
      "skills/cap4k-authoring/references/runtime-analysis-boundaries.md",
      "skills/cap4k-authoring/references/tactical-carriers.md"
    ]
  },
  {
    "acceptance_id": "acceptance-64cf0fa2d119d97edc79e7ba96d72ba6bec75c7f9ac8aa4df7f6fa06393d9eb9",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt",
      "skills/cap4k-authoring/SKILL.md",
      "skills/cap4k-authoring/routing.yaml"
    ]
  },
  {
    "acceptance_id": "acceptance-6d0a665f5ce89fac3f104261d9cf24144da99d16e2c2f684548af6387b460ac4",
    "evidence_refs": [
      "docs/public/authoring/plan-review-and-generation.md",
      "skills/cap4k-authoring/SKILL.md",
      "skills/cap4k-authoring/references/ownership-boundaries.md"
    ]
  },
  {
    "acceptance_id": "acceptance-9c13ba9a73059b29ea64a55813e7e8b945e4251b84106a887410aadddb11ef42",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTaskTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt",
      "settings.gradle.kts",
      "skills/scripts/checks/active-term-scan.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-c9ca886e40faf915933991a555992b102738fb1bf834d485a6c0f9d9dd32aaa7",
    "evidence_refs": [
      "AGENTS.md",
      "skills/cap4k-authoring/SKILL.md",
      "skills/cap4k-authoring/routing.yaml"
    ]
  },
  {
    "acceptance_id": "acceptance-e2de2ae54acfded335793c6486bf6adb5d9ca55df057427c4ce948e496b68601",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTask.kt",
      "docs/public/reference/agent-api.md",
      "skills/cap4k-authoring/routing.yaml"
    ]
  },
  {
    "acceptance_id": "acceptance-eaac588c77d6161ae1b266b80907812cc209809eb20febcb1d37bc41f26265fc",
    "evidence_refs": [
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineCapabilityDescriptors.kt",
      "cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt",
      "skills/cap4k-authoring/references/tactical-carriers.md"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `.\gradlew.bat :cap4k-plugin-pipeline-agent:test --rerun-tasks --no-configuration-cache` — passed; 15 actionable tasks executed and all 15 Agent API core tests passed.
- `.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kAgentSnapshotTaskTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kAgentSnapshotFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.LocalInputStateTest" --rerun-tasks --no-configuration-cache` — passed; 65 actionable tasks executed, including invalid/partial snapshot publication, exact-eight output cleanup, corrupt plan evidence, duplicate diagnostics, local-input drift, database no-connect, and manifest-first dry-run cases.
- `.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.event.impl.DomainEventPayloadValidatorTest" :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventArtifactPlannerTest" --rerun-tasks --no-configuration-cache` — passed; runtime and generator both rejected direct, nested, collection, map, array, and Value Object paths containing persistent Entity/Aggregate payloads.
- The broader owner-module regression completed successfully with `:cap4k-plugin-pipeline-gradle:check`; 96 actionable tasks completed with `BUILD SUCCESSFUL` in 18m32s.
- `.\skills\scripts\validate-cap4k-skills.ps1` — passed: `thin-surface files=5 bytes=7474 alwaysReadBytes=3686 alwaysReadLines=49` and `cap4k skill validation passed.`
- `git diff --check` — passed with no whitespace errors; Git emitted only existing LF-to-CRLF conversion warnings.
- Active Bootstrap and stale-current-term scans — passed; Bootstrap names remain only in two negative task-registration assertions and one Agent catalog negative assertion, while README/public docs/thin skill contain no active Bootstrap entry.
- Three fresh, read-only agent scenarios passed: Chinese project inspection selected `inspect-project`; Handler ownership selected `implement-owned-logic`; company-level strategic DDD was rejected as outside the thin cap4k skill boundary.
- `comet eval --skill-path skills/cap4k-authoring --skill-name cap4k-authoring --collect` — discovered the `generic-skill-smoke` dynamic Skill case and prepared the local evaluator environment.
- `comet eval --skill-path skills/cap4k-authoring --skill-name cap4k-authoring --quick --html` — did not produce pass evidence: five evaluator cases were skipped because Docker and Anthropic API authentication were unavailable.
- `comet native check complete-ddd-authoring-workflow --json` — receipt `runtime/evidence/check-receipts/88c64e3745c9fbb37e02d958b63a8b56988561f92431b9cc5471ee39066d91c8.json` failed before scanning because the implementation scope includes a deliberately deleted Bootstrap template path; the failed receipt is not submitted as pass evidence.

# Skipped checks

- A formal executed `comet eval` result is unavailable. The evaluator installed its isolated dependencies with user approval, but its actual dynamic Skill case requires Docker plus `ANTHROPIC_API_KEY` or `ANTHROPIC_AUTH_TOKEN`; all five cases were skipped rather than passed.
- The independent #27 runnable reference project was not executed. This change supplies a bounded fixture/dry run and explicitly does not claim the separate real-project proof gate is complete.
- MCP was not implemented or tested; the confirmed delivery boundary defers it until the adapter-neutral Agent API contract is stable.
- The independent `cap4k-template` repository was not changed or tested.

# Spec consistency

The implementation matches the confirmed responsibility reset: cap4k supplies tactical framework facts and machine-readable engineering evidence, while strategic DDD, organizational research, and final domain decisions remain human responsibilities. The thin skill is a five-file repo-local router/field guide rather than a strategic workflow engine.

The Agent API is Gradle-first, adapter-neutral at its core, versioned JSON, manifest-first, external-I/O-safe by default, provider-descriptor-derived, credential-redacted, and explicit about invalid, partial, unavailable, and freshness semantics. A successful publish owns exactly manifest plus seven section files; malformed project state and corrupt plan evidence preserve diagnostics before failure when the task can start.

Bootstrap tasks, DSL, modules, runner, renderer, guards, markers, slots, fixtures, docs, and skill routes are retired without compatibility aliases. Ordinary checked-in skeleton ownership, `ConflictPolicy.SKIP`, Aggregate Behavior, handwritten surfaces, and managed-field handler slots remain covered. The runtime Domain Event historical-fact boundary remains strict and was reverified against generator payload validation.

# Known limitations and risks

- Comet recorded an explicitly approved partial-scope allowance because 107 fully attributed change details exceeded its display budget. Enumeration found zero unattributed items; the hidden detail tail is content-bound by hash but not expanded line by line in Runtime output.
- The optional Comet scoped text checker cannot currently scan a scope containing a deleted file and returned `scan-limit` before reading any file. Independent active-term, link, thin-surface, Gradle, and whitespace checks provide the actual verification evidence.
- `comet eval` has no executed behavioral result in this environment. The three isolated agent scenarios are useful local behavior evidence but are not a substitute for a future Docker/Anthropic-backed evaluator run if formal eval becomes a release gate.
- Analyzer evidence remains structural observation; it cannot prove business intent, strategic correctness, transaction commit, delivery retry, compensation, or provider behavior.
- Real-project closure remains tracked separately by #27, and MCP/Template integration remain separate future changes.

# Conclusion

Pass. The confirmed repo-local thin skill, Agent API, Bootstrap retirement, documentation reset, ownership preservation, and Domain Event boundary are implemented and supported by fresh owner-module tests and bounded agent scenarios. The conclusion does not claim a formal `comet eval` pass, a completed #27 real-project proof, MCP delivery, Template integration, or analyzer authority beyond observed structure.
