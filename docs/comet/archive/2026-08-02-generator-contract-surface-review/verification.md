# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-46e017f853ec4bdb3502b27aebbe95664bc7b04bee03dedbb0fcfa819718fa50",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt",
      "docs/public/generator/inputs-and-sources.md",
      "docs/public/reference/index.md"
    ]
  },
  {
    "acceptance_id": "acceptance-623e340d6d7c599807e93ef8471e453aa1f4182fc0c278d106d4526089967ff3",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt",
      "cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-6c92a8f242e0446abb82a6c86df039b861d4d51854a1f822fe389319c1f2893d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/design.json"
    ]
  },
  {
    "acceptance_id": "acceptance-710806a08e5ac08ef270b76fe36c34d0eee554c146f4852c88ae899ba9e0e633",
    "evidence_refs": [
      "cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt",
      "cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7c92219cd23a0514b33b087b8623a7ff768fa6bc045099253b8fb925475c5c78",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAgentSnapshotTaskTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonProviderDescriptorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-87559019599210e0cb150ed7711d69e0f92cb541a6e63b77530c1c018a69c9a4",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotCodecTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt",
      "docs/public/reference/agent-api.md",
      "docs/public/reference/plan-json.md"
    ]
  },
  {
    "acceptance_id": "acceptance-e37d63f2ca145d8b42a730161a9a5f4d9e62ba618eff8cd7e83659a80028e906",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/test/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentSnapshotCodecTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonProviderDescriptorTest" --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest" --console=plain --no-daemon`: passed; BUILD SUCCESSFUL.
- `.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest" --console=plain --no-daemon`: passed; BUILD SUCCESSFUL.
- `.\gradlew.bat :cap4k-plugin-pipeline-agent:test --tests "com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotCodecTest" --console=plain --no-daemon`: passed; both codec tests passed.
- `.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain --no-daemon`: passed; all selected renderer tests passed.
- `.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kAgentSnapshotTaskTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kPlan writes pretty printed plan json" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate behavior source compiles against generated entities when module build dir is customized" --console=plain --no-daemon`: passed; BUILD SUCCESSFUL in 4m20s. The seven-tag plan drift guard and structured generated-source output-root assertions passed.
- `git diff --check`: passed with no whitespace errors; Git emitted only expected LF-to-CRLF checkout warnings.
- Live scans excluding `docs/superpowers/**`, `docs/comet/**`, and ignored build output: no retired validator entry points, old positive generator ids, Specification helper/planner, or Scheduled Reaction generator overclaim remained.
- `comet native check generator-contract-surface-review`: passed; receipt `runtime/evidence/check-receipts/69c962287233297eacf52f1b4cc13f1e2c12eeb3b23a486bb52145fd5a28d1e4.json`.

# Skipped checks

- Full `.\gradlew.bat check --no-daemon` was not run; this PR is a focused contract-surface repair and the changed owners were covered by the focused module, renderer, Agent, and TestKit tests above.
- Historical `docs/superpowers/**` and immutable `docs/comet/archive/**` were not rewritten; their old references are retained as provenance and excluded from live-surface scans.

# Spec consistency

The implementation matches the complete target specification: Scheduled Reaction/Job remains handwritten, plan and Agent ownership fields use current path semantics, the validator and dead Specification surfaces remain retired, and no compatibility alias or runtime carrier was added. The repair does not touch Strong ID MVC binding or the broader Analyzer/Drawing Board round-trip work.

# Known limitations and risks

The existing PR required check may still be running remotely after this branch update. The full repository check was intentionally skipped, so unrelated modules are not newly re-proven by this repair.

# Conclusion

PASS: the contract-surface repair satisfies the scoped audit findings and focused verification evidence is green.

