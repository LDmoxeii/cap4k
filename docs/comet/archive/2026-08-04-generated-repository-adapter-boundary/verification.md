# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-197dad28bf0ed87715ad7d82fb73182df6c73496512544103af9345828acba75",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb"
    ]
  },
  {
    "acceptance_id": "acceptance-37d54be53b6113e06772260f3d76e49b920accfb92b904d3ab629578aa6cb101",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AggregateElementJsonWriter.kt",
      "cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt",
      "cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c6ce41c7b8b6c14b83b97f98012919cb2e802236794a2ba69bcc9dcd9216bb2a",
    "evidence_refs": [
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb",
      "cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-e126e23c9020a2233a4d128f002e42a4de20ecb6c4824be873b4d319934c62e1",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt",
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-e76eff998e4ff72aa8d25a42d3bb015282ae2c09b7f535c763f0911409014f5d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt",
      "cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-eecd77201b2f8ebb00868f017221e9c8ecb6b096f1dea42186910efa12f7c19e",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/DesignRoundTripFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-fde3f7bf6d24c7b854851ab3d4a6d783ca50d4877da4c446e3c5093545b1503b",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt",
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryProvider.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --no-daemon`: passed, 223 tests with no failures; one PostgreSQL environment test skipped by its existing environment assumption.
- `./gradlew.bat check --no-daemon`: passed, `BUILD SUCCESSFUL` (222 actionable tasks; 1 executed and 221 up-to-date).
- Real Design JSON and DB-source round trip: passed; Project A and Project B compile with the provider-private carrier, Analyzer recovery, Drawing Board structural evidence, and stable second generation.
- Focused JPA runtime tests: passed for `Mediator.repositories` query, Hibernate dirty checking/Unit of Work, and `RepositorySupervisor.remove` semantics.
- Active repository stale-surface checks: passed; no generated aggregate public `JpaRepository`/`JpaSpecificationExecutor` interface, no active `AggregateRepository` surface, and no current public documentation contract for one.
- `git diff --check HEAD`: passed.
- `comet native check generated-repository-adapter-boundary`: passed; 47 files scanned, 0 issues, receipt `runtime/evidence/check-receipts/72835c57340d9f1b481e3cbaaba0e03599d9174260b99aeafb147e6c03f6c785.json`.
- An earlier full-check attempt exposed TestKit cache isolation and a stale flow fixture sidecar; both were corrected centrally and the focused module run plus final repository check passed afterward.

# Skipped checks

- The real PostgreSQL soft-delete integration test remained skipped because no PostgreSQL environment was configured. Its cleanup tests passed. This is environment coverage, not a repository-adapter failure.
- Comet implementation scope intentionally excludes 12 `cap4k-ddd-console` deletion paths that were already merged on `origin/master` before this branch was rebased. The user confirmed that partial scope allowance; those paths are unrelated to this change.

# Spec consistency

- Generator input remains `cap4k-plugin-pipeline-source-db`; Repository is not added to Design JSON input.
- Generator emits one internal framework-owned JPA carrier and no public Spring Data repository interface or constructor dependency.
- Runtime keeps `Mediator.repositories` as the sole business repository path, with dirty checking/Unit of Work for ordinary updates and `RepositorySupervisor.remove` for explicit deletion.
- Analyzer emits deterministic aggregate-element evidence and Drawing Board keeps repository structure as separate structural evidence.
- The real round-trip gate keeps the seven Design JSON tags separate from DB-derived Repository generation and proves a second generation does not restore the removed public surface.

# Known limitations and risks

- This slice does not address unrelated Runtime findings such as route-key uniqueness, predicate variants, ordering/pagination, codecs, transport, or other provider behavior.
- PostgreSQL-specific behavior still needs an environment-enabled run; H2/JPA runtime and compilation coverage are green.
- Historical superseded plans may still mention public Spring Data repositories; active source, templates, fixtures, and current documentation are the enforced surface.

# Conclusion

Pass. The generated repository boundary is now framework-owned and provider-private, the application-facing contract remains `Mediator.repositories`, and Generator/Runtime/Analyzer/Drawing Board preserve the intended DB-driven round-trip without compatibility aliases or a second repository API.
