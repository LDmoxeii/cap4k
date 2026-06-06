# cap4k Analysis Documentation Redesign

Date: 2026-06-01

Status: Proposed

Scope: detailed design for #98, rebuilding `docs/superpowers/analysis/` as a current code map and fact index for cap4k maintenance agents and human maintainers.

## Backlog Source

This design implements the analysis part of the documentation-system redesign:

- #98: `analysis: rebuild internal agent-facing project map from code`
- Shared design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`

It does not rewrite `README.md`, `docs/public/`, or `skills/`.

## Reader And Purpose

`analysis` is 70% AI-friendly and 30% human-maintainer-friendly.

Its job is to help future cap4k maintenance agents quickly locate current code facts and verification routes. It must not become public user documentation or an internal whitepaper.

A useful analysis page answers:

- what capability this topic covers;
- where the current source-of-truth code and tests are;
- what the current contracts are;
- what changes are risky;
- what must be verified after changes;
- which old terms or assumptions must not be reused.

## Current Problems To Fix

The current `docs/superpowers/analysis/` directory mixes current facts, old capability maps, gap snapshots, and stale details.

Known drift from the audit:

- `cap4kPlan` / `cap4kGenerate` are described as depending on KSP in places, but KSP metadata source support is removed from the pipeline authoring contract.
- `cap4kGenerateSources` is described as wired into compile/KSP; current implementation registers generated sources into the Kotlin `main` source set and compile inputs, not KSP.
- `cap4kGenerateSources` is described too broadly as covering enum-manifest families; current generator filters are `aggregate` and `aggregate-projection`, with enum manifest acting as source/type input.
- `design validator` appears as a default design layout concept, but `validator` is not a core design tag.
- `sources.irAnalysis.enabled`, `generators.flow.enabled`, and `generators.drawingBoard.enabled` appear as stale DSL ideas; current analysis execution is driven by `sources.irAnalysis.inputDirs`, with analysis generator IDs `flow` and `drawing-board`.
- Some terminology says `client/cli handler` where current generator vocabulary is `client` / `client-handler`.
- Current-vs-history status is not explicit.

## Source Of Truth Policy

Code is final. Active analysis pages must cite source anchors.

Accepted anchors include:

- production Kotlin source;
- tests;
- Gradle plugin source;
- Gradle convention build logic;
- GitHub workflow YAML;
- templates;
- generated fixtures or snapshot expectations;
- validation scripts.

Historical specs/plans are not source of truth for active analysis facts. They may help understand why a decision happened, but they are not part of the default reading path.

## Target Directory

The detailed implementation may adjust file names, but the target should be small and map-oriented.

Proposed active files:

```text
docs/superpowers/analysis/
  README.md
  architecture-map.md
  pipeline-and-gradle-map.md
  source-and-generator-contract-map.md
  artifact-output-and-ownership-map.md
  runtime-and-integration-map.md
  analysis-flow-and-verification-map.md
  release-map.md
  documentation-and-skill-drift-map.md
```

Do not create a `snapshots/` archive. Old analysis files should be deleted, split, or rewritten into active maps. Git history preserves prior text.

## Page Template

Each active map should follow this structure unless a section is genuinely irrelevant:

```markdown
# <Topic> Map

## Purpose
What positioning or navigation problem this page solves.

## Current Facts
A table of current facts, each with source anchors.

## Source Anchors
Files and tests to inspect before changing this topic.

## Contracts
Current task, DSL, source ID, generator ID, output path, runtime, or release contracts.

## Change Impact
What modules, docs, skills, tests, generated output, or release behavior can change.

## Verification
Commands, tests, plan outputs, generated files, or workflow checks to inspect.

## Drift Watch
Stale terms, common wrong assumptions, and old wording to avoid.

## Not Covered
Explicit scope boundaries.
```

If a page cannot fill `Source Anchors` and `Verification`, it is probably not an active analysis page.

## Map Responsibilities

### README.md

Purpose:

- define the reader;
- explain that `analysis` is a current code map, not public docs;
- list active maps and when to read each;
- state that code wins over analysis;
- state that historical specs/plans are not default reading material.

Must include:

- quick reading path for common maintenance tasks;
- warning against trusting old dates or historical plans as current facts;
- cross-links to active maps only.

### architecture-map.md

Purpose: module and responsibility orientation.

Must cover:

- major Gradle modules and responsibility groups;
- runtime modules vs plugin modules vs code-analysis modules;
- starter/autoconfiguration boundary;
- compile-time vs runtime vs generated-output ownership.

Suggested anchors:

- `settings.gradle.kts`
- `cap4k-ddd-*` modules
- `cap4k-plugin-pipeline-*` modules
- `cap4k-plugin-code-analysis-*` modules
- `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### pipeline-and-gradle-map.md

Purpose: Gradle plugin, tasks, DSL, and task dependency orientation.

Must cover:

- plugin id `io.github.ldmoxeii.cap4k.pipeline`;
- task list: `cap4kBootstrapPlan`, `cap4kBootstrap`, `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`;
- plan output paths;
- `sources.irAnalysis.inputDirs` driven analysis dependency inference;
- generated-source source set registration;
- no KSP metadata source contract.

Suggested anchors:

- `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- `Cap4kExtension.kt`
- `Cap4kProjectConfigFactory.kt`
- `Cap4kPlanTask.kt`
- `Cap4kAnalysisPlanTask.kt`
- `Cap4kGenerateSourcesTask.kt`

### source-and-generator-contract-map.md

Purpose: pipeline source IDs, generator IDs, design-json tags, and artifact family contracts.

Must cover:

- source IDs: `db`, `design-json`, `enum-manifest`, `value-object-manifest`, `ir-analysis`;
- source grouping: source generation vs analysis runner;
- generator IDs for source generation and analysis;
- design JSON tags currently supported;
- unsupported tags such as core `validator` and `value_object` design tag;
- `domain_service` and `saga` support state from code;
- `integration_event` inbound/outbound and subscriber selection rules;
- page variant and payload rules that are easy to misstate.

Suggested anchors:

- `DesignJsonSourceProvider.kt`
- `DefaultCanonicalAssembler.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/...`
- design generator tests
- `ProjectConfig.kt`

### artifact-output-and-ownership-map.md

Purpose: generated output kinds, output roots, conflict policies, generated-vs-handwritten boundaries.

Must cover:

- `CHECKED_IN_SOURCE` vs `GENERATED_SOURCE` output semantics;
- default output roots;
- `build/generated/cap4k/main/kotlin`;
- `flows` and `design` defaults;
- `src-generated/main/kotlin` as audit/learning snapshot only if still used in examples;
- plan review expectations;
- generated skeleton vs handwritten logic ownership.

Suggested anchors:

- `ArtifactLayoutResolver.kt`
- `ProjectConfig.kt`
- `FilesystemArtifactExporter` tests
- generator planner tests
- plan task output behavior

### runtime-and-integration-map.md

Purpose: runtime tactical framework facts.

Must cover:

- starter module scope and compileOnly integration dependencies;
- domain/application/adapter responsibility boundaries;
- `Mediator` request flow;
- repository and Unit of Work behavior;
- domain event flow;
- integration event flow and adapter runtime boundaries;
- Saga runtime scope as compensation-oriented, not a generic callback-resume workflow engine.

Suggested anchors:

- `ddd-core`
- `ddd-application-*`
- `ddd-domain-*`
- `ddd-integration-event-*`
- `ddd-saga-*`
- runtime tests
- starter autoconfiguration imports

### analysis-flow-and-verification-map.md

Purpose: compiler analysis, `irAnalysis`, flow, drawing-board, and verification route.

Must cover:

- compiler plugin output: `nodes.json`, `rels.json`, `design-elements.json`;
- default `build/cap4k-code-analysis` output;
- compiler options such as `cap4k.codeanalysis.outputDir`;
- `sources.irAnalysis.inputDirs` consumption;
- `cap4kAnalysisPlan` output `build/cap4k/analysis-plan.json`;
- `cap4kAnalysisGenerate` observation outputs;
- `flow` and `drawing-board` as analysis/observation outputs, not default business source generation;
- flow export plugin distinction if still relevant.

Suggested anchors:

- `cap4k-plugin-code-analysis-core/src/main/kotlin/.../OptionsKeys.kt`
- `cap4k-plugin-code-analysis-compiler/.../Cap4kIrGenerationExtension.kt`
- `cap4k-plugin-pipeline-source-ir-analysis`
- `cap4k-plugin-pipeline-generator-flow`
- `cap4k-plugin-pipeline-generator-drawing-board`
- related tests

### release-map.md

Purpose: release and publication facts.

Must cover:

- tag-driven Maven Central workflow;
- `publish/maven-central` gate;
- `v<major>.<minor>.<patch>` tag behavior;
- `release.version` behavior;
- plugin marker publish policy;
- local publish checks and CentralPortal task gating.

Suggested anchors:

- `.github/workflows/maven-central-release.yml`
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`
- `buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt`
- related buildSrc tests

### documentation-and-skill-drift-map.md

Purpose: document and skills drift watch for future maintainers.

Must cover:

- where public docs live;
- where skills live;
- what should not be duplicated;
- high-risk terms that caused drift;
- how to audit public/skills against code facts;
- Context7 compatibility boundary;
- self-contained skills bundle boundary.

Suggested anchors:

- this design packet;
- `README.md`;
- `docs/public/`;
- `skills/`;
- `skills/scripts/validate-cap4k-skills.ps1`.

## Old Analysis File Handling

Current old files should not be kept as dated active maps.

Suggested handling:

| Old file | Handling |
| --- | --- |
| `2026-05-11-cap4k-bootstrap-plugin-and-template-map.md` | Split facts into `pipeline-and-gradle-map` and artifact/template sections; delete old file. |
| `2026-05-11-cap4k-generator-input-output-and-verification-map.md` | Use as seed for source/generator and ownership maps after code verification; delete old file. |
| `2026-05-11-cap4k-testing-analysis-and-flow-map.md` | Rewrite into `analysis-flow-and-verification-map`; delete old file. |
| `2026-05-11-cap4k-public-tactical-model-and-layering-map.md` | Split runtime facts into runtime map; public-facing concepts go to #99 later; delete old file. |
| `2026-05-11-cap4k-runtime-support-and-integration-map.md` | Rewrite into runtime map; delete old file. |
| `2026-05-11-cap4k-extension-spi-addon-and-gap-map.md` | Extract current addon facts if still needed; do not preserve gap snapshot. |
| `2026-05-11-cap4k-business-project-authoring-capability-map.md` | Do not keep as active analysis; useful current authoring concepts belong to #99/#100. |
| `2026-05-14-cap4k-maven-central-release-verification.md` | Rewrite/rename into `release-map.md` if still current. |

Implementation should verify each old file before deleting. Correct content can migrate; stale structure should not.

## Non-Goals

- Do not make `analysis` a public manual.
- Do not preserve old dated file names as active documentation.
- Do not create an archive directory.
- Do not rewrite public docs or skills in #98.
- Do not make historical specs/plans part of default analysis reading.
- Do not require Context7.

## Verification Criteria

A completed #98 rewrite must satisfy:

- `docs/superpowers/analysis/README.md` defines reader, reading path, and code-wins policy.
- Each active map has source anchors and verification steps.
- Stale KSP, old enabled-switch, enum-manifest-as-generator, design-validator, and client/cli wording is removed or explicitly listed as stale in `Drift Watch`.
- No active map depends on historical spec/plan context.
- Public-doc and skills concerns are only referenced as downstream consumers, not written in detail.
- Analysis pages are concise enough for agents to load by topic.
- Running a search for stale phrases in `docs/superpowers/analysis/` after rewrite does not find active factual claims using old terms.

Suggested post-rewrite scans:

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|design validator|client/cli|analysis plan\.json|cap4k code analysis" docs/superpowers/analysis
rg -n "cap4k-code-analysis|analysis-plan\.json|cap4kGenerateSources|aggregate-projection|integration_event|domain_service|validator" docs/superpowers/analysis
```

## Handoff For Future Agents

If context is lost, continue #98 by reading:

1. `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
2. this file
3. #98 issue body
4. the source anchors listed above

Then write the #98 implementation plan. Do not depend on conversation memory or old analysis file structure.