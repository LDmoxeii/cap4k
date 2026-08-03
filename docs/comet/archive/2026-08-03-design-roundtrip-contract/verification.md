# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1dba376e069764612d75a6b3102485e442cc266c64289eaa53274b2a5bcfc0a1",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-roundtrip-compile-sample/design/design.json"
    ]
  },
  {
    "acceptance_id": "acceptance-2d3a86173b3a3016752258ffd6f7425077dc61dc6ccea3294b31ef8e8e24248b",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-93e33d86c0f488d030d387dddb293e569bb84deb8ab0f9e73abd46b9e3983455",
    "evidence_refs": [
      "cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt",
      "cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a9bf3847cb93b8a329271c2e88b23e7c886c0845105eafb906798aee08a8e53e",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt",
      "cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-b6b0ac5e3c75fe1810bf6f724ffdf54199d51a2ab5951d69273596c8602a1980",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ba7cc2e22174e85b8ac34e1a8034bbfe1848e99b19cc50e94bdba1b9f84c926f",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt",
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/SemanticValueCompilerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d777a711ba5e928ba093b101c69a38820c2fee04f0ba573fae829555cc79c7d3",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt",
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d8fcfbf33d9293d7dfb9b13c751faff16edd319a7827bc8de25771593a48faed",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-e4d7e9cc43e2af94ad54892061bb9c9ede4a7c4b7c6e96975538ad1f312ef720",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f2b27ca45f10b35b3873e98635794ca83a52974ab7d7b6117325d94f348110c8",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt",
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-generator-drawing-board:test --no-daemon --console=plain -q` passed with exit code 0.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.DesignRoundTripFunctionalTest" --no-daemon --stacktrace` passed: 1 test, 0 failures, 0 errors, 0 skipped; 117.552 seconds; build completed in 2 minutes 9 seconds.
- The round-trip gate generated and compiled Project A, invoked `Cap4kCodeAnalysisCompilerRegistrar` for domain/application/adapter, generated all seven Drawing Board tag files, explicitly registered only those files in clean Project B, regenerated and compiled Project B, and passed normalized canonical, framework-owned skeleton, and runtime annotation equality assertions.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate domain event flow writes domain event and domain event subscriber artifacts" --no-daemon --console=plain -q` passed with exit code 0 after updating the persisted-event fixture expectation.
- `./gradlew.bat check --no-daemon --console=plain -q` passed with exit code 0. Current XML reports summarize 274 suites, 1969 tests, 0 failures, 0 errors, and 15 repository-declared skips.
- `git diff --check` passed; Git emitted only expected Windows LF-to-CRLF conversion warnings.
- `comet native check design-roundtrip-contract` passed: 36 scoped files scanned, 1,482,028 bytes, 0 issues.

# Skipped checks

Downstream validation in a real business project was not run. It remains outside this branch and is tracked separately by Issue #27. No acceptance item in this change was skipped.

# Spec consistency

The implementation matches the confirmed complete target specification. It keeps Drawing Board as explicit observation evidence that may be registered as ordinary Design JSON, preserves event direction within one context, permits an explicit outbound-to-inbound decision only across contexts, and does not weaken reliable-event Entity/Aggregate payload rejection.

# Known limitations and risks

Drawing Board is not automatically registered or fed back into generation. Primitive arrays, handwritten business-body inference, automatic cross-context event-direction conversion, Analyzer auto-installation, and downstream real-project validation remain intentionally unsupported. The cross-module gate uses a rich isolated seven-tag fixture rather than a production business repository.

# Conclusion

Pass. The normalized tactical contract `Design JSON == generated skeleton == Drawing Board` is implemented and verified across focused negative tests, a real compiler-backed two-project round trip, and the repository required `check`.
