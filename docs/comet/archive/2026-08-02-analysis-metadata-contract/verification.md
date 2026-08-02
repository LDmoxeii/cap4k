# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1b70985297113ef701555fae6014ddc84ed1c33e3f71be05e064f1601f2524b4",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt",
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-4648a379d494835391f387ff1c053afb876c32b0ced925a0c22ce37517ade6a0",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisMetadataContractIntegrationTest.kt",
      "cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-6d38c5cb065bb99bb2207d5d31b61987b88d63edeb3e873be50d549aca0d65e5",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-98436e35e709da279c429856d024670afb417a9bb849d854a6cb8e7f32cc13a0",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt",
      "cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d89d14ba5df96512cfe1b2c2a076595f28ad2f18443a7c6ecd443b4b25163993",
    "evidence_refs": [
      "cap4k-analysis-metadata/src/main/kotlin/com/only4/cap4k/analysis/metadata/AggregateElementMetadata.kt",
      "cap4k-analysis-metadata/src/main/kotlin/com/only4/cap4k/analysis/metadata/DesignBlockMetadata.kt",
      "cap4k-analysis-metadata/src/test/kotlin/com/only4/cap4k/analysis/metadata/AnalysisMetadataAnnotationTest.kt",
      "settings.gradle.kts"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat --no-daemon --console=plain :cap4k-analysis-metadata:test :ddd-core:test :cap4k-plugin-code-analysis-core:test :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-generator-drawing-board:test :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-code-analysis-flow-export:test` — PASS, `BUILD SUCCESSFUL in 47s`. This covered the metadata ABI and retention contract, removal from `ddd-core`, renderer templates, Analyzer extraction and metadata-loss evidence, canonical propagation, Drawing Board and Flow planner guards, legacy flow-export, and the real custom-template-to-Analyzer failure path.
- Focused `:cap4k-plugin-pipeline-gradle:test` run for generated Command/Query/Capability, handler, API payload, Domain Event, Integration Event, Aggregate factory, enum-manifest-only, integrated multi-family compilation, Flow Plan-only fail-fast, and Drawing Board Generate-only fail-fast — PASS, `BUILD SUCCESSFUL in 4m 9s` (10 selected TestKit tests).
- `./gradlew.bat --no-daemon --console=plain :cap4k-analysis-metadata:generatePomFileForMavenPublication :cap4k-plugin-pipeline-gradle:generatePomFileForPluginMavenPublication` — PASS, `BUILD SUCCESSFUL in 21s`. The generated metadata POM has coordinate `io.github.ldmoxeii:cap4k-analysis-metadata:0.6.0-dev`; the plugin POM does not publish analysis metadata as a plugin runtime dependency.
- JAR surface inspection with `System.IO.Compression.ZipFile` — PASS. `ddd-core-0.6.0-dev.jar` contains neither retired annotation class; `cap4k-analysis-metadata-0.6.0-dev.jar` contains exactly the new `DesignBlockMetadata` and `AggregateElementMetadata` contract classes under the new package.
- Active code/resource search for retired annotation imports, FQNs, and usages — PASS, no references in Kotlin, Gradle, Java, Pebble, properties, YAML, or JSON project surfaces.
- Search for configurable metadata FQN overrides — PASS, none remain; Analyzer and consumers share fixed contract FQNs.
- `git diff --check` — PASS. Only Git line-ending conversion advisories were printed; there are no whitespace errors.
- `comet native check analysis-metadata-contract --json` — PASS. Receipt `runtime/evidence/check-receipts/e165b5f4b3a5daad138186cf39ec6038c4a4fb7d493a076c3ed09344581e9c1b.json` scanned 54 scoped text files (1,377,192 bytes) and found zero issues.

# Skipped checks

- The repository-wide `./gradlew.bat check` was not run. Verification instead ran every changed production module plus the focused Gradle TestKit compilation and analysis scenarios required by the audit contract.
- Remote Maven Central publication and an external network consumer build were not run. Local Maven POM generation and composite/TestKit generated-module compilation cover the publication shape and consumer compile path without requiring credentials.

# Spec consistency

- The implementation matches the confirmed brief and complete target specification: the two analyzer-only annotations moved to a dedicated published module/package, were renamed without aliases, remain CLASS-target/BINARY-retained, and have no runtime reflection semantics.
- Pipeline dependency inference adds the metadata coordinate only to owning business modules' `compileOnly` configuration, deduplicates an explicit module dependency, and does not add it to implementation/runtime dependency intent.
- Every default metadata-bearing template now emits the renamed annotation; custom templates may omit it, but real Analyzer compilation records a typed metadata gap rather than guessing authoring values.
- Metadata gap evidence is preserved through IR source merge and canonical assembly. Drawing Board, pipeline Flow, and legacy flow export fail before planning/rendering when their required metadata is absent.
- Diagnostics report metadata owner symbols, missing annotation FQNs, affected capabilities, and restoration guidance. No sidecar skeleton index was added.

# Known limitations and risks

- This change intentionally does not implement the broader G-05 semantic round-trip repairs assigned by the audit to the later `fix/design-roundtrip-contract` slice (field normalization, artifact matrices, and full seven-tag second-generation gates).
- Metadata-loss candidate recognition remains based on compiled semantic surfaces (Cap4k marker interfaces, relevant runtime annotations, and generated aggregate/API shapes). It does not use source paths or a sidecar index; the intentional failure mode is conservative fail-fast rather than a guessed partial result.
- The working tree reports existing LF-to-CRLF conversion advisories on Windows, but `git diff --check` is clean and tests compile from the current checkout.

# Conclusion

PASS. The compile-time analysis metadata contract is implemented and the audit's metadata-specific acceptance criteria are satisfied by module, template, Analyzer, dependency, consumer, diagnostic, generated-compilation, and cross-module Flow Analysis evidence.
