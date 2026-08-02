# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-23c132faffddea65026a31dd5d250cf7c3818540219cf0da8da08ff91cbea87e",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdSpringMvcRuntimeTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb",
      "cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-29bf43169fba11c9a886393e54aa037187a0a56c3688b6844ed3ff0d018b5260",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt",
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdSpringMvcRuntimeTest.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-970f6be9bb5f8ead91561f9cb687c228cbefa455fbe25e087417aea4a8157938",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt",
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdSpringMvcRuntimeTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb"
    ]
  },
  {
    "acceptance_id": "acceptance-c021c207546935ce530a92b37eb43c5356edc652af941125f364065f4c690b5a",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdSpringMvcRuntimeTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-f5a48e87fb5811c64542a5fdc22925a134a6e3db11af33992826f36fab4a690d",
    "evidence_refs": [
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt",
      "cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdSpringMvcRuntimeTest.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.StrongIdsTest" :cap4k-ddd-jpa-starter:test --tests "com.only4.cap4k.ddd.runtime.StrongIdJacksonRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdSpringMvcRuntimeTest" --no-daemon`: passed; renderer module suite plus focused Strong ID core, Jackson, JPA, and MVC runtime tests all passed.
- `git diff --check`: passed with no whitespace errors; Git emitted only expected LF-to-CRLF checkout warnings for the touched tracked files.
- `comet native check strong-id-mvc-binding`: passed; receipt `runtime/evidence/check-receipts/6e91fa8fe08127d72a7d2aacda2c881e96a41623f5c21ef564b1a0aa736a6d8c.json`.

# Skipped checks

- None.

# Spec consistency

- The generated Strong ID template now exposes one shared JVM-static `from(String)` entry point for all four supported backings and delegates directly to `parse(String)`.
- The implementation preserves the existing semantic validation path through `StrongIds.requireUuidV7(...)` and `StrongIds.requireSnowflake(...)`; no alternate converter logic was introduced.
- Real Spring MVC evidence proves `@PathVariable` and `@RequestParam` binding for UUIDv7 String/UUID and Snowflake String/Long aggregate-root Strong IDs, plus generated-style non-root Strong ID types.
- The runtime boundary remains unchanged by design: there is no reflection-based converter, no Strong ID scan, no registry-driven MVC path, and no JSON/JPA contract regression.

# Known limitations and risks

- The focused runtime evidence lives in test fixtures rather than a generated-project functional sample. It proves Spring default conversion plus generated-type shape, but it does not add a new full Gradle functional fixture for controller projects.
- `git diff --check` continues to print Windows checkout line-ending warnings for touched tracked files; the command reported no whitespace defects.

# Conclusion

Pass. The template change, renderer assertions, Strong ID semantic tests, Jackson/JPA runtime coverage, and focused Spring MVC binding tests satisfy the confirmed `strong-id-mvc-binding` contract without adding any runtime converter or scanning mechanism.
