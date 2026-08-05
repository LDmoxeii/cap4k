# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-01f0301fff4468d1c4b2777060bbbc8e7c06f4809667e81674e03945179f832d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-6c1a4505f5489bcd53d4fe34982fd70ad919e361f6b243c32d1ba6222fe42c91",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a1cbc255d37868ad5e150da5b6a79c1c871010ef45e0bf4e4a6bcc74ecab773c",
    "evidence_refs": [
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-dffe15570bb8c314f1b29d054978ee017f24ef25fe63bb9ac6d060805782f79d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotService.kt",
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt",
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotServiceTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `pwsh -NoProfile -File scripts/validate-current-runtime-facts.ps1`: passed; no retired Runtime surfaces or descriptor declarations were found.
- `./gradlew :cap4k-plugin-pipeline-agent:test --no-daemon`: infrastructure failure before project compilation because Maven Central terminated the TLS handshake while resolving `kotlin-scripting-jvm:2.2.20`.
- `./gradlew :cap4k-plugin-pipeline-agent:test --offline --no-daemon --no-configuration-cache`: passed; 18 Agent tests passed, including both new retired-identity tests.
- `git diff --check`: passed.
- `comet native check runtime-agent-retired-descriptors`: passed; receipt `runtime/evidence/check-receipts/99ae965841107dbe24486b0d82a92ae7b79156ee535b657d699315a961fb18cf.json` scanned four implementation artifacts with zero issues.

# Skipped checks

- The full repository test suite and `cap4k-plugin-pipeline-gradle` functional suite were not run because the production change is contained in the shared Agent service and validator; the owning Agent module compiled and its complete focused suite passed.
- No temporary source mutation was injected to demonstrate the validator's negative path; the validator implementation is referenced directly, while runtime rejection is exercised for all four identities in unit tests.

# Spec consistency

The implementation rejects retired descriptors before observation normalization or manifest encoding, preserves all surviving descriptor projections, and does not add a status placeholder. It does not add live Runtime provider registry facts or change the current status enum, matching the confirmed non-goals.

# Known limitations and risks

The PowerShell source scan recognizes ordinary literal `capabilityId`, `providerId`, and provider `override val id` declarations across every top-level production Kotlin source root. Indirect constant/function declarations are enforced at snapshot assembly by the authoritative Runtime descriptor policy rather than by static regex. Agreement with active Runtime provider identities remains deferred until the final `runtime-agent-api-facts` slice supplies that registry.

# Conclusion

Pass. The scoped retirement guard, tests, current-facts validation, and text-safety check satisfy the confirmed contract without claiming the deferred Runtime registry/status work.