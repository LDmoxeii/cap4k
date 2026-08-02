# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1bad127566ae1863f61765fbc242c7281e9be3e4798a76c434adf8a898661365",
    "evidence_refs": [
      "cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/Node.kt",
      "cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/Relationship.kt",
      "cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt",
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md"
    ]
  },
  {
    "acceptance_id": "acceptance-2c4f94d119c3619cbf7ba19d6f36f1a54886f19d6a384f6f7fe2821c3e1fd673",
    "evidence_refs": [
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md",
      "skills/cap4k-authoring/routing.yaml",
      "skills/cap4k-tactical-modeling/workflows/map-tactical-carriers.md",
      "skills/cap4k-technical-design/references/technical-design-contract.md"
    ]
  },
  {
    "acceptance_id": "acceptance-45307c0e0d7180db6ea1349331220e6c3d352824b4ddfc49f7fcbb668876bd9a",
    "evidence_refs": [
      "cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt",
      "cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt",
      "cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DomainEventPayloadValidator.kt",
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md"
    ]
  },
  {
    "acceptance_id": "acceptance-5f9744c8ae89fcb88a19f434926bb11fbee5d8c7874cb57b22fcb5f96a752499",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/capability/CapabilityCall.kt",
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md",
      "skills/cap4k-service-integration/rules/integration-event-boundaries.md"
    ]
  },
  {
    "acceptance_id": "acceptance-ccb83ab81fcdfacc59239889d7595fca28880c456812deaafcd0cb4e26efb5a9",
    "evidence_refs": [
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md",
      "skills/cap4k-service-integration/rules/integration-event-boundaries.md",
      "skills/shared/references/tactical-affordance-map.md"
    ]
  },
  {
    "acceptance_id": "acceptance-faf14eebc9dc09beed130444eff656e7bbb45867e0ecda8adb8498e733b2205f",
    "evidence_refs": [
      "docs/superpowers/analysis/2026-08-01-ddd-authoring-capability-coverage-audit.md"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `pwsh -NoLogo -NoProfile -File skills/scripts/validate-cap4k-skills.ps1` — passed: `cap4k skill validation passed.`
- `./gradlew.bat :ddd-core:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-generator-types:test :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-pipeline-generator-drawing-board:test --no-daemon` — passed: `BUILD SUCCESSFUL`, 59 actionable tasks.
- `./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test :ddd-domain-repo-jpa:test --no-daemon` — passed: `BUILD SUCCESSFUL`, 24 actionable tasks.
- `git diff --check` — passed with no whitespace errors.
- `gh issue view 25` and `gh issue view 100` against `LDmoxeii/cap4k` — both confirmed open on the audit date.
- `comet native check cap4k-ddd-capability-audit --json` — passed: one scoped report file scanned, zero issues; receipt `runtime/evidence/check-receipts/14413ed3868131774e26538335b78514166088406b6217c8680fd72d559aaa0d.json`.

# Skipped checks

- Full-repository Gradle `check` was not run because this change is a documentation capability audit; focused owner-module tests cover the generator, runtime, skill, and analyzer facts cited by the report.
- A real consumer reference project was not executed. The lack of accepted end-to-end reference-project evidence is itself recorded as a limitation and backlog blocker.
- Production event delivery, retry, compensation, performance, and provider behavior were not dynamically observed.
- Domain-expert interviews and organizational/team-boundary validation were outside this repository audit.

# Spec consistency

The report covers every IDDD Chapters 2-14 concept family required by the brief, rates all four capability blocks, separates strategic from tactical responsibilities, evaluates every named Context Map pattern, identifies handoff friction, distinguishes skill-only/framework/provider gaps, and gives a direct start/no-start conclusion.

The user-confirmed invariant is preserved: the runtime Domain Event historical-fact guard is correct and must not be weakened. The live aggregate field in the default event template is classified as generator drift. No framework implementation was changed by this audit.

# Known limitations and risks

- The result is point-in-time evidence for repository commit `6c4971c3e4f723ea3ccec6131ba6fef77894935e` and open-issue state on 2026-08-01.
- Passing renderer and runtime tests do not disprove the Domain Event drift: those suites currently assert opposing contracts independently, and no cross-block test joins generated payload rendering to runtime validation.
- Analyzer evidence is static and cannot prove business intent, transaction commit, delivery, retries, provider behavior, or strategic correctness.
- Modern extensions such as Event Sourcing, full CQRS, semantic module enforcement, and durable Process Managers/Sagas are intentionally disclosed separately from the core Chapters 2-14 benchmark.

# Conclusion

Pass. The audit deliverable satisfies the confirmed acceptance contract. This pass means the capability assessment is complete and evidence-backed; it does not mean cap4k itself already provides a complete DDD workflow. The supported conclusion is to begin strategic skill and projection-ledger work now, while fixing the Domain Event generator drift before claiming a coherent default tactical path.
