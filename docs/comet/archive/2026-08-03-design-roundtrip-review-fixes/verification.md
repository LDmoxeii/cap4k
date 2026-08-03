# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-561449d72c3101435c670304c4cad8abebe2670d0adad6e6525714d22e5697fa",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5c5502daa2e7b805ae9d6282adcb1327f53eef55a56b5416f3458ea1a056dcab",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-roundtrip-compile-sample/design/design.json"
    ]
  },
  {
    "acceptance_id": "acceptance-6431bdd41d93e41d39c3cf1598d8a517c1f3ffbd3d6830ec89007b914b8751df",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8a70957ff918072d35fe05e41ab9b6ae7fd9945f06dcffa044e2b1070c98acc5",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/SemanticValueCompiler.kt",
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/SemanticValueCompilerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8e496bee3547415898d40244529ba65f0d9e5096aea00f8d21cbd51290c30f7a",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt",
      "cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-b2d4eab28030697e6eb448a90396ea7a1601b9cfe39f000b8557811de177ac60",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt",
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d680f557c8aa2ad5abf7433023eaa22141246eeafc0cab013a6b118d36cce220",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-roundtrip-compile-sample/design/design.json"
    ]
  },
  {
    "acceptance_id": "acceptance-e0443e13e8fa3459ead5bb39620cbe14b3f1a7e7263b2ea84a57dfcd5cfe835d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-design-json:test --no-daemon --console=plain` passed with exit code 0 in 57 seconds.
- `./gradlew.bat :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon --console=plain` passed with exit code 0 in 1 minute 25 seconds. The output includes the exact event-literal, subscriber-direction, transient-event, form-feed, primitive-array recovery, page recovery, and business-body invariance tests.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.DesignRoundTripFunctionalTest" --no-daemon --console=plain` passed with exit code 0 in 2 minutes 27 seconds.
- The real gate freezes C0, bytes, and SHA-256 immediately after fixture copy; generates and compiles Project A; runs `Cap4kCodeAnalysisCompilerRegistrar` for domain/application/adapter; generates all seven Drawing Board tag files; explicitly registers only those files in Project B; then regenerates and compiles Project B while asserting canonical, framework-owned skeleton, and runtime annotation equality.
- `./gradlew.bat check --no-daemon --console=plain` passed with exit code 0 in 14 minutes 32 seconds: 227 actionable tasks, including the real `DesignRoundTripFunctionalTest`.
- `git diff --check` passed; Git emitted only expected Windows LF-to-CRLF conversion warnings.
- `comet native check design-roundtrip-review-fixes` passed: 16 scoped files scanned, 1,234,722 bytes, 0 issues.

# Skipped checks

Downstream validation in a real business project was not run because the Generator, Runtime, and Analyzer capability audits are intentionally completed before joint downstream validation. No acceptance item in this review-fix change was skipped.

# Spec consistency

The implementation matches the confirmed six-item review-fix contract. Same-context event direction and exact runtime annotation literals are preserved without automatic conversion; an explicit outbound-to-inbound edit remains a cross-context human decision. Drawing Board remains an explicitly registered ordinary Design JSON input and is never automatically backfed. Reliable-event Entity/Aggregate payload rejection remains unchanged.

# Known limitations and risks

Drawing Board is not automatically registered. Primitive arrays, handwritten business-body inference, same-context event-direction conversion, Analyzer auto-installation, and downstream real-project validation remain intentionally unsupported. The round-trip proof uses a rich isolated seven-tag fixture rather than a production business repository.

# Conclusion

Pass. All six PR #157 review findings are addressed by production changes or stronger evidence. The normalized tactical contract `Design JSON == generated skeleton == Drawing Board` now fails fast on the reviewed normalization gaps and passes a real compiler-backed, two-project round trip plus the repository required `check`.
