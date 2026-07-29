# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0d36a0ca1dbd210149af36f66f00d382a5051ba2e66ccaa4e0a98c8ac091e6d0",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateCreationArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb"
    ]
  },
  {
    "acceptance_id": "acceptance-1a1ece8f36e1fa0768f95d13aa65772509924a323adffb1a76f5c7faee54b26d",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt",
      "cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateCreationArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-1bcbc0442c1f59df7a48418ce76991d9d475bd034b5068e8afc2adeb4dd8d0a2",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateCreationGraphValidator.kt",
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/CreationValueArtifactPlanner.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5425ec27ece7e41377722aa344f2d57d18c85322c1853eb9eb9b4846ac5e9883",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt",
      "cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateCreationArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb"
    ]
  },
  {
    "acceptance_id": "acceptance-722f980b6d63889e3f8f469d89880d8b367905316ab2cb04c98d65626bccd293",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/CreationValueArtifactPlanner.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/creation.kt.peb"
    ]
  },
  {
    "acceptance_id": "acceptance-c86ae7de2648d5e30fe5cef6b8b5e1c0a3fcbddb552e78cd439e37f79a75f1bd",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt",
      "cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateCreationArtifactPlannerTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-d80ebe032962789969ff0d1bdfff2a07e74de13edc2a305e87660699558df734",
    "evidence_refs": [
      "cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateCreationGraphValidator.kt",
      "cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateCreationGraphValidatorTest.kt",
      "cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateCreationArtifactPlannerTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-fd35c1cdb2619071524870c8ab154e0122870c2ae48cbd0cffc28828329f23a6",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdAccessor.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-core:test --no-daemon`: passed; 286 tests completed with 13 pre-existing skips.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --no-daemon`: passed; 225 tests completed, with 224 passed and the existing real-PostgreSQL integration test skipped.
- `./gradlew.bat check --no-daemon`: passed; 234 actionable tasks, comprising 11 executed, 48 from cache, and 175 up-to-date.
- `python scripts/validate-cap4k-generator-inputs.py --design cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/design.json --value-object cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/value-objects.json --json`: passed with `[]`.
- `git diff --check`: passed with no whitespace errors; Git emitted only expected LF-to-CRLF checkout warnings.
- Residual source scans for the removed `DesignTypeResolver`, `DesignFieldTypeParser`, `DesignFieldImportResolver`, and `DesignDefaultValueNormalizer` implementations returned no matches.
- Residual manifest scans found no live source-provider use of the removed field-level `nullable` or value-object `storage` JSON properties.
- `comet native check issue-115-generator-semantics-refresh`: passed; 114 files scanned, 2,443,804 bytes scanned, and zero issues. Receipt: `runtime/evidence/check-receipts/070e7251643d88702978dc10c63831bbcc40615395ccd3d1b986bb8168bbf40b.json`.

# Skipped checks

- The existing real-PostgreSQL native UUID soft-delete integration test remained skipped because this environment does not provide the required real PostgreSQL service. H2 runtime fixtures, generated-project compile fixtures, planner tests, and the complete repository `check` all ran.

# Spec consistency

- The implementation matches the confirmed `owned-child-factory-creation` capability: one checked-in Factory, top-level reusable child Creation values, recursive owned-child construction, relation defaults, child-ID exclusion, deterministic pre-render rejection, and fixed checked-in SKIP ownership.
- The implementation matches the confirmed `semantic-value-types` capability: a shared closed semantic type algebra, canonical value definitions across all requested roles, the query-only `PageData<Item>` envelope, explicit optional value-object persistence, pure checked-in value objects, and separate build-owned JSON converters.
- The replacement superpowers design and public authoring/reference documentation describe the implemented contract and explicitly defer addon SPI, Unique addon, and relational/embedded queryable value-object persistence.

# Known limitations and risks

- Checked-in Factory and Creation artifacts intentionally provide first materialization only. Existing handwritten or stale files are not refreshed, merged, or patched by the generator.
- Relational or embedded queryable value-object persistence remains deferred; this slice supports pure value objects and explicit JSON persistence projection only.
- The real-PostgreSQL integration test was not executed in this environment, as recorded under Skipped checks.

# Conclusion

Pass. The implementation, focused tests, generated-project compile tests, full repository check, input validator, documentation, and Comet scoped text-safety check consistently satisfy the confirmed issue #115 contract.
