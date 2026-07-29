# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0d8a580287e2dfa059c526631d80c42c550c8d097d025c77538e98f4394c1035",
    "evidence_refs": [
      ".github/workflows/maven-central-release.yml",
      "buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts",
      "docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-4913a5220decf3ddd47c9d80ff5ad30f30bf563a971e6cbf587bd522d85e2adf",
    "evidence_refs": [
      ".github/PULL_REQUEST_TEMPLATE.md",
      ".github/workflows/ci.yml",
      "scripts/create-pr.ps1",
      "scripts/test-pr-workflow.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-a177aa754f944530424950479e604b1613bc90c1213d8e3093f308e070562963",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/CompositeBuildConsumerFunctionalTest.kt",
      "cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/official-composite-consumer-sample/build.gradle.kts",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/official-composite-consumer-sample/gradle/libs.versions.toml",
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/official-composite-consumer-sample/settings.gradle.kts"
    ]
  },
  {
    "acceptance_id": "acceptance-a6309dbb6f8b2dbba8519882c61d29557f92c1d2168ab3c59d831c1db8e53900",
    "evidence_refs": [
      ".github/workflows/maven-central-release.yml",
      "AGENTS.md",
      "buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts",
      "docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-e782b5f6e319721b5b64459cce79d049fc39be406e89614e38ff54dec52b67ef",
    "evidence_refs": [
      "AGENTS.md",
      "docs/superpowers/analysis/release-map.md",
      "docs/superpowers/plans/2026-07-21-cap4k-github-workflow-governance.md",
      "docs/superpowers/plans/2026-07-28-cap4k-single-mainline-release-governance.md",
      "docs/superpowers/specs/2026-05-22-cap4k-publish-channel-governance-design.md",
      "docs/superpowers/specs/2026-07-21-cap4k-github-workflow-governance-design.md",
      "docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-e94efeae4c4cda7064f1d8575f91243f485a66e503127c247ea7226223a206d3",
    "evidence_refs": [
      "cap4k-plugin-pipeline-gradle/src/test/resources/functional/official-composite-consumer-sample/settings.gradle.kts",
      "docs/superpowers/analysis/local-composite-development.md",
      "docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-f90b153d22242f6ad644dfd29334ac999f3237bffc515c39cc40a431d99ac941",
    "evidence_refs": [
      ".github/workflows/maven-central-release.yml",
      "buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts",
      "docs/superpowers/analysis/local-composite-development.md",
      "docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md"
    ]
  },
  {
    "acceptance_id": "acceptance-fafeeefcef47d1c463e05ed5c45258c75cd632cf299f40ace7dd49ec7567a5e5",
    "evidence_refs": [
      ".github/workflows/maven-central-release.yml"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `./gradlew.bat -p buildSrc test --tests buildsrc.convention.CentralReleaseVersionTest --tests buildsrc.convention.CentralPublishTaskPolicyTest --no-daemon --console=plain`: passed. Central release version parsing, Snapshot rejection, and remote publish task gating remain green without Aliyun policy code.
- `./scripts/test-pr-workflow.ps1`: passed. The script-created isolated repositories verify master-only PR creation, template validation, and docs-only/full-check classification.
- `python -c "... yaml.safe_load(...) ..."`: passed and parsed all 8 YAML files below `.github`.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.CompositeBuildConsumerFunctionalTest" --no-daemon --console=plain`: passed. The fixture uses `999.0.0-local`, does not use `withPluginClasspath()`, runs `cap4kPlan`, compiles domain/application/adapter, runs the start smoke test, and verifies composite substitution for the plugin marker and three runtime modules.
- `./gradlew.bat help --no-daemon --console=plain`: passed without Central or signing credentials.
- `git diff --check`: passed; Git emitted only expected LF-to-CRLF working-copy warnings.
- Production-surface `rg` over the release issue/template, workflows, `scripts/create-pr.ps1`, the JVM convention, `gradle.properties`, and `settings.gradle.kts`: no active Aliyun or publish-branch references found. Current AGENTS and release-map also contain no active publish-branch contract; the new spec/plan mention those names only as historical context and post-merge cleanup targets.
- `./gradlew.bat check --no-daemon --console=plain`: reached 243 actionable tasks and the pipeline suite completed 218 tests with 216 passed, 1 skipped, and 1 failed. The only failure was an external Maven Central TLS handshake interruption while downloading `org.springframework:spring-expression:6.2.11` in a generated-project TestKit build; no assertion or compilation error from the change was reported.
- `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginBootstrapGeneratedProjectFunctionalTest.generated bootstrap preview project domain application and adapter modules compile" --no-daemon --console=plain`: passed on immediate retry in 38 seconds after the dependency became available, confirming the full-check failure was transient dependency transport rather than a code regression.

# Skipped checks

- No real Maven Central upload, Central Portal publication, signing operation, or GitHub Release was triggered. Those actions require release credentials and an intentional future stable tag.
- The negative tag-containment branch was inspected statically but was not exercised by pushing a deliberately invalid public tag.
- The PostgreSQL soft-delete integration test remained skipped by the existing suite because no PostgreSQL evidence environment was configured; it is unrelated to release governance.
- The separate official Template repository was not changed or rebuilt in this cap4k code PR. This change defines and tests the opt-in Composite Build consumer contract while keeping the public Template default Maven-Central-only.
- Remote branch protection/rules, Aliyun secrets, and the two remote publish branches were not deleted. The confirmed contract deliberately defers those external mutations until this PR is merged and a release smoke is complete.

# Spec consistency

The implementation matches the complete target specification: `master` is the only supported long-lived source/base branch; exact stable tags must be contained in `origin/master`; Central remains the only remote publication path; Aliyun and Snapshot behavior are removed; local source co-development is explicit and property-gated through Gradle Composite Build; and normal consumers remain Maven-Central-only unless they opt in.

Historical release documents retain their original decisions with a concise superseded pointer. Current AGENTS, release-map, workflows, scripts, templates, and issue form describe only the new contract. The existing `v2.0.1` tag and release history were not modified.

# Known limitations and risks

Comet recorded a bounded partial-scope allowance for exactly three intentionally deleted paths: `.github/workflows/aliyun-snapshot.yml`, `buildSrc/src/main/kotlin/buildsrc/convention/AliyunPublishVersion.kt`, and `buildSrc/src/test/kotlin/buildsrc/convention/AliyunPublishVersionTest.kt`. The runtime requires build artifacts to exist, so deleted paths cannot be supplied through `--artifact`; all remaining changed paths are explicitly covered.

The full `check` command did not itself exit successfully because Maven Central terminated one TLS handshake. The exact failed test passed on immediate focused retry, and all other reported tests passed or retained their existing environment skip. A live release remains the final evidence for external Central/GitHub behavior after merge.

# Conclusion

Pass. Repository implementation, focused tests, governance tests, YAML parsing, static policy checks, and the explicit Composite Build consumer demonstrate the confirmed single-mainline contract. The only full-suite failure was an external download interruption that passed on immediate retry; external publication and remote governance cleanup remain intentionally deferred operational steps.
