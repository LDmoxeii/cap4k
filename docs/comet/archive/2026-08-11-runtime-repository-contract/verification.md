# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0765c56c8418203f23f1fde88b31ca4ef3060de3565d08ef5d51d8c640b1bc1c",
    "evidence_refs": [
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt",
      "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-28c50651e5b6fe2be26548ada9af4c6408764c9111d19c3eaba21539387dbe54",
    "evidence_refs": [
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb",
      "cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-308fdd26d83e03c9f0b6053af95f79d9869d5b92f905cab41c19b1079839e10e",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/repository/AbstractJpaRepositoryH2RuntimeTest.kt",
      "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/FieldTest.kt",
      "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/PredicatesTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-859bfc49e8e036f0af72f69d144648eb2a36f36e38ab9d0608bf0b025694119f",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/PageData.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/PageDataTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-aaabca3e39ca8a8862a18f79d6ac5a2debb05df099f3cacbbd8b8bfd97253c5a",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/repository/AbstractJpaRepositoryH2RuntimeTest.kt",
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt",
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryProvider.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c8a3512d670443f9f88b6755cc04a2620f1ee5e573eeb4dffecbba60dfb67ff3",
    "evidence_refs": [
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt",
      "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f7446c7ded0ad2f0be6de8f6ea866c973925c92a23f301643c047f62cd752160",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt",
      "ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt",
      "ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-ffeaaa69fcb556906cedeeb9db90f3dd71c8b42ad9d8a921a345b22332163c24",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/repository/AbstractJpaRepositoryH2RuntimeTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :ddd-core:test --tests "*PageDataTest" :ddd-domain-repo-jpa:test :cap4k-ddd-jpa-starter:test --tests "*AbstractJpaRepositoryH2RuntimeTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"`: passed; focused contract, real H2, and generated-carrier tests completed successfully.
- `./gradlew.bat check`: passed in 16m 2s; 211 actionable tasks, 0 failed.
- `comet native check runtime-repository-contract --json`: passed; 12 files selected and scanned, 0 issues, receipt `runtime/evidence/check-receipts/e59397b9920d22dfe22dd4dbd66b24513901466262724121caf295fdbf837373.json`.
- `git diff --check`: passed with no whitespace errors; Git emitted only repository line-ending conversion notices.

# Skipped checks

- The existing external PostgreSQL soft-delete integration fixture remained skipped by the full Gradle suite because its external database environment was unavailable. It is unrelated to this Repository query contract; the new query behavior was exercised against a real H2/JPA runtime fixture.

# Spec consistency

- Duplicate repository routes are resolved and rejected before the supervisor map becomes usable; distinct routes remain supported.
- ID predicates and Specifications now share database-side sorting, pagination, counting, and page metadata semantics. Explicit sorting gains an entity-ID ascending tie-breaker; unsorted calls intentionally make no ordering promise.
- JpaPredicate input IDs are snapshotted and deduplicated, and the declared Field/Predicates operation families are covered by unit and H2 execution tests, including nested composition.
- PageData.transform preserves pageNum, pageSize, and totalCount.
- Repository reads remain observation-only; explicit removal and existing aggregate factory/UoW paths retain persistence-intent ownership.
- Generated repository carriers remain internal, and no Reliable Command/Event state machine, Transport, or Analyzer production code changed.

# Known limitations and risks

- Queries without explicit OrderInfo remain intentionally unordered, matching the confirmed contract.
- Composite-ID predicates are constructed from JPA identifier metadata, but this change does not add a dedicated composite-ID H2 fixture. No current acceptance example requires one, and the generic implementation remains compile-checked by the full build.

# Conclusion

PASS. All eight Runtime-derived acceptance items have concrete project evidence, focused tests and the complete Gradle check passed, and the implementation remains within the Runtime Repository Contract boundary.