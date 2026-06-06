# cap4k Analysis Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `docs/superpowers/analysis/` for issue #98 as a current, code-anchored project map for cap4k maintenance agents and human maintainers.

**Architecture:** Replace dated snapshot-style analysis files with a small set of active maps. Each map uses the same sections: Purpose, Current Facts, Source Anchors, Contracts, Change Impact, Verification, Drift Watch, and Not Covered. Code is the final source of truth; old specs and plans are handoff context only, not active analysis evidence.

**Tech Stack:** Markdown, PowerShell 7, `rg`, Git, Gradle/Kotlin source inspection. The rewritten analysis pages should use Chinese prose with code identifiers, paths, Gradle tasks, DSL keys, source IDs, and generator IDs preserved in English.

---

## Source Inputs

Read these files before execution:

- `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- `docs/superpowers/specs/2026-06-01-cap4k-analysis-redesign.md`
- GitHub issue #98 body if available in the execution environment

Do not use conversation memory as evidence. Re-check code in the current worktree before writing facts.

## Files To Create

- `docs/superpowers/analysis/README.md`
- `docs/superpowers/analysis/architecture-map.md`
- `docs/superpowers/analysis/pipeline-and-gradle-map.md`
- `docs/superpowers/analysis/source-and-generator-contract-map.md`
- `docs/superpowers/analysis/artifact-output-and-ownership-map.md`
- `docs/superpowers/analysis/runtime-and-integration-map.md`
- `docs/superpowers/analysis/analysis-flow-and-verification-map.md`
- `docs/superpowers/analysis/release-map.md`
- `docs/superpowers/analysis/documentation-and-skill-drift-map.md`

## Files To Delete After Migration

- `docs/superpowers/analysis/2026-05-11-cap4k-bootstrap-plugin-and-template-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-business-project-authoring-capability-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-extension-spi-addon-and-gap-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-public-tactical-model-and-layering-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-testing-analysis-and-flow-map.md`
- `docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md`

## Global Rules

- Do not modify `README.md`, `docs/public/`, or `skills/` in Phase 1.
- Do not create `docs/superpowers/analysis/snapshots/` or another archive directory.
- Do not preserve dated analysis files as active pages.
- Every active map must contain source anchors and verification steps.
- Every current fact must be traceable to source code, tests, Gradle build logic, workflow YAML, templates, or validation scripts.
- Treat old analysis pages as seed material only after re-checking the corresponding code.
- Keep maps concise and table-heavy. If a map starts reading like a public tutorial, reduce it to facts, anchors, contracts, impacts, and checks.

## Shared Page Skeleton

Every active map should use this skeleton unless a section truly has no value for that topic:

```markdown
# <Topic> Map

## Purpose

## Current Facts

## Source Anchors

## Contracts

## Change Impact

## Verification

## Drift Watch

## Not Covered
```

---

### Task 0: Safety Check And Source Recapture

**Files:**
- Read: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- Read: `docs/superpowers/specs/2026-06-01-cap4k-analysis-redesign.md`
- Read: source anchors listed in later tasks

- [ ] **Step 1: Confirm the worktree branch**

Run:

```powershell
git status --short --branch
```

Expected: branch is `spec/documentation-system-redesign`; do not execute this plan on `master`.

- [ ] **Step 2: Capture current old analysis inventory**

Run:

```powershell
Get-ChildItem -Path docs/superpowers/analysis | Select-Object Name,Length | Format-Table -AutoSize
```

Expected: the eight dated analysis files listed in `Files To Delete After Migration` are present before rewrite.

- [ ] **Step 3: Recapture key source anchors**

Run:

```powershell
rg --files | rg "(PipelinePlugin|Cap4kExtension|Cap4kProjectConfigFactory|Cap4kPlanTask|Cap4kAnalysisPlanTask|Cap4kGenerateSourcesTask|ProjectConfig|ArtifactLayoutResolver|DesignJsonSourceProvider|DefaultCanonicalAssembler|OptionsKeys|Cap4kIrGenerationExtension|AutoConfiguration.imports|maven-central-release|CentralReleaseVersion|CentralPublishTaskPolicy|validate-cap4k-skills)"
```

Expected: output includes the Gradle plugin, pipeline API/core, design-json source, code-analysis compiler/core, starter autoconfiguration, release workflow/buildSrc, and skill validation anchors used below.

- [ ] **Step 4: Inspect the implementation facts before writing**

Run:

```powershell
rg -n "cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate|io.github.ldmoxeii.cap4k.pipeline" cap4k-plugin-pipeline-gradle
rg -n "design-json|enum-manifest|value-object-manifest|ir-analysis|flow|drawing-board|aggregate-projection|integration_event|domain_service|saga|client-handler" cap4k-plugin-pipeline-* cap4k-plugin-code-analysis-* 
rg -n "cap4k.codeanalysis.outputDir|nodes.json|rels.json|design-elements.json|analysis-plan.json|build/cap4k-code-analysis" cap4k-plugin-code-analysis-* cap4k-plugin-pipeline-* 
```

Expected: these commands provide the final code facts for the maps. If any expected string has moved, use `rg --files` and focused `rg -n` to locate the new anchor before writing.

- [ ] **Step 5: Stop if the branch or source anchors are wrong**

If Step 1 is not on the feature branch, or if key source anchors cannot be found, stop and report the blocker instead of writing analysis pages.

---

### Task 1: Create Analysis Landing Page

**Files:**
- Create: `docs/superpowers/analysis/README.md`

- [ ] **Step 1: Write `README.md` with the shared map skeleton adapted for navigation**

Required content:

- State that `analysis` is for cap4k maintenance agents and human maintainers, not public users.
- State that code wins over analysis when they conflict.
- State that historical specs/plans are not default reading material.
- Link only to the eight active map files created by this plan.
- Include a quick reading path:
  - module orientation -> `architecture-map.md`
  - Gradle task or DSL change -> `pipeline-and-gradle-map.md`
  - source ID, generator ID, or design JSON change -> `source-and-generator-contract-map.md`
  - output ownership or generated-vs-handwritten boundary -> `artifact-output-and-ownership-map.md`
  - runtime tactical behavior -> `runtime-and-integration-map.md`
  - compiler analysis, flow, drawing-board -> `analysis-flow-and-verification-map.md`
  - Maven Central release -> `release-map.md`
  - public docs or skills drift -> `documentation-and-skill-drift-map.md`

- [ ] **Step 2: Check the landing page for stale cross-links**

Run:

```powershell
rg -n "2026-05-11|2026-05-14|snapshots|docs/public|skills/" docs/superpowers/analysis/README.md
```

Expected: no dated analysis links and no detailed public/skills authoring instructions. Mentions of downstream `docs/public/` and `skills/` are allowed only as pointers to the drift map.

- [ ] **Step 3: Commit the landing page**

Run:

```powershell
git add docs/superpowers/analysis/README.md
git commit -m "docs: add analysis navigation map"
```

Expected: commit succeeds and only `README.md` is included in this commit.

---

### Task 2: Create Architecture And Pipeline Maps

**Files:**
- Create: `docs/superpowers/analysis/architecture-map.md`
- Create: `docs/superpowers/analysis/pipeline-and-gradle-map.md`

- [ ] **Step 1: Inspect module layout and starter anchors**

Run:

```powershell
Get-Content -Path settings.gradle.kts -Raw
Get-Content -Path cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports -Raw
rg -n "java-gradle-plugin|io.github.ldmoxeii.cap4k.pipeline|plugins \{|id\(" cap4k-plugin-pipeline-gradle/build.gradle.kts build.gradle.kts settings.gradle.kts
```

Expected: module names, starter autoconfiguration entries, and the pipeline plugin id are visible from source.

- [ ] **Step 2: Write `architecture-map.md`**

Required current facts:

- Separate responsibility groups for runtime `cap4k-ddd-*`, pipeline `cap4k-plugin-pipeline-*`, code analysis `cap4k-plugin-code-analysis-*`, starter/autoconfiguration, examples/tests, and documentation/skills.
- Distinguish compile-time generation, runtime tactical framework behavior, generated output, and documentation/skills maintenance.
- Explain that `cap4k-ddd-starter` imports autoconfiguration entries from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- State that this page does not teach DDD usage; public conceptual explanation belongs to later Phase 2.

Required source anchors:

- `settings.gradle.kts`
- `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Representative module roots found by `Get-ChildItem -Directory -Filter 'cap4k-*'`

Required verification commands in the page:

```powershell
Get-ChildItem -Directory -Filter 'cap4k-*' | Select-Object Name
Get-Content cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- [ ] **Step 3: Inspect Gradle task and extension anchors**

Run:

```powershell
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt -Raw
```

Expected: task names, DSL blocks, source IDs, generator IDs, output paths, and source-set registration behavior can be verified from Kotlin source.

- [ ] **Step 4: Write `pipeline-and-gradle-map.md`**

Required current facts:

- Plugin id: `io.github.ldmoxeii.cap4k.pipeline`.
- Tasks: `cap4kBootstrapPlan`, `cap4kBootstrap`, `cap4kPlan`, `cap4kGenerate`, `cap4kGenerateSources`, `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`.
- Plan outputs: `build/cap4k/bootstrap-plan.json`, `build/cap4k/plan.json`, and `build/cap4k/analysis-plan.json` where confirmed by task source.
- `cap4kGenerateSources` registers generated Kotlin output under `build/generated/cap4k/main/kotlin` into the Kotlin `main` source set and compile inputs.
- `sources.irAnalysis.inputDirs` drives analysis input selection; do not document stale `sources.irAnalysis.enabled`.
- Do not document a KSP metadata source contract for current `cap4kPlan` or `cap4kGenerate` behavior.

Required drift watch entries:

- Old wording that `cap4kPlan` / `cap4kGenerate` depend on KSP metadata is stale.
- Old wording that `cap4kGenerateSources` is wired into KSP is stale.
- Old `sources.irAnalysis.enabled`, `generators.flow.enabled`, and `generators.drawingBoard.enabled` DSL switch wording is stale unless code reintroduces it.

Required verification commands in the page:

```powershell
rg -n "cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" cap4k-plugin-pipeline-gradle/src/main/kotlin
rg -n "inputDirs|build/generated/cap4k/main/kotlin|analysis-plan.json|plan.json|bootstrap-plan.json" cap4k-plugin-pipeline-gradle/src/main/kotlin
```

- [ ] **Step 5: Scan the new maps**

Run:

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled" docs/superpowers/analysis/architecture-map.md docs/superpowers/analysis/pipeline-and-gradle-map.md
```

Expected: matches appear only in Drift Watch entries that explicitly label them stale, or no matches appear.

- [ ] **Step 6: Commit architecture and pipeline maps**

Run:

```powershell
git add docs/superpowers/analysis/architecture-map.md docs/superpowers/analysis/pipeline-and-gradle-map.md
git commit -m "docs: map cap4k architecture and pipeline"
```

Expected: commit succeeds with only the two new map files.

---

### Task 3: Create Source, Generator, And Output Ownership Maps

**Files:**
- Create: `docs/superpowers/analysis/source-and-generator-contract-map.md`
- Create: `docs/superpowers/analysis/artifact-output-and-ownership-map.md`

- [ ] **Step 1: Inspect design-json, canonical assembly, project config, and layout anchors**

Run:

```powershell
Get-Content -Path cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt -Raw
rg -n "validator|value_object|integration_event|domain_service|saga|client-handler|aggregate-projection|CHECKED_IN_SOURCE|GENERATED_SOURCE" cap4k-plugin-pipeline-* 
```

Expected: supported tags, unsupported historical terms, generator IDs, output kinds, and layout defaults are visible from code and tests.

- [ ] **Step 2: Write `source-and-generator-contract-map.md`**

Required current facts:

- Source IDs include `db`, `design-json`, `enum-manifest`, `value-object-manifest`, and `ir-analysis` in their confirmed contexts.
- Source generation and analysis generation are separate responsibilities.
- Generator IDs include source generation IDs and analysis generator IDs; document only IDs verified in code.
- Design JSON tags include the currently supported interaction tags verified from `DesignJsonSourceProvider.kt` and design generator tests.
- Core `validator` and `value_object` design JSON tags are not current normal design tags unless code now supports them.
- `integration_event` rules must distinguish event shape, inbound/outbound behavior, and explicit subscriber selection based on generator source/tests.
- Use `client` and `client-handler`; do not write `client/cli handler` as current terminology.

Required source anchors:

- `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`

Required verification commands in the page:

```powershell
rg -n "command|query|client|api_payload|domain_event|integration_event|domain_service|saga|validator|value_object" cap4k-plugin-pipeline-source-design-json cap4k-plugin-pipeline-generator-design
rg -n "client-handler|client/cli|aggregate-projection|integration-subscriber" cap4k-plugin-pipeline-*
```

- [ ] **Step 3: Write `artifact-output-and-ownership-map.md`**

Required current facts:

- Explain `CHECKED_IN_SOURCE` and `GENERATED_SOURCE` output semantics from `ArtifactLayoutResolver.kt` and tests.
- Document default roots verified from `ProjectConfig.kt` and Gradle task source, including `build/generated/cap4k/main/kotlin` for generated sources and confirmed plan roots for source-generation plans.
- Document flow and drawing-board defaults only after verifying generator source.
- Explain generated skeleton vs handwritten logic ownership: cap4k-generated skeletons are owned by generator inputs/templates, while complex business logic is handwritten inside generated skeletons unless technical design explicitly decides otherwise.
- State that bypassing generator-owned skeletons is a design-stage decision. If discovered during implementation, stop and return to design/generator inputs.

Required source anchors:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`
- `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`

Required verification commands in the page:

```powershell
rg -n "CHECKED_IN_SOURCE|GENERATED_SOURCE|build/generated/cap4k/main/kotlin|src-generated|flows|drawing" cap4k-plugin-pipeline-* docs/superpowers/analysis
```

- [ ] **Step 4: Scan terminology in the new source/output maps**

Run:

```powershell
rg -n "design validator|client/cli|enum-manifest.*generator|sources\.irAnalysis\.enabled" docs/superpowers/analysis/source-and-generator-contract-map.md docs/superpowers/analysis/artifact-output-and-ownership-map.md
```

Expected: no active factual claims use those stale terms. If they appear, they must be in Drift Watch with explicit stale labeling.

- [ ] **Step 5: Commit source/generator and ownership maps**

Run:

```powershell
git add docs/superpowers/analysis/source-and-generator-contract-map.md docs/superpowers/analysis/artifact-output-and-ownership-map.md
git commit -m "docs: map cap4k generator contracts and outputs"
```

Expected: commit succeeds with only the two new map files.

---

### Task 4: Create Runtime And Analysis-Flow Maps

**Files:**
- Create: `docs/superpowers/analysis/runtime-and-integration-map.md`
- Create: `docs/superpowers/analysis/analysis-flow-and-verification-map.md`

- [ ] **Step 1: Inspect runtime anchors**

Run:

```powershell
Get-Content -Path cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports -Raw
rg -n "class .*Mediator|interface .*Mediator|UnitOfWork|Repository|DomainEvent|IntegrationEvent|Saga|AutoConfiguration" cap4k-ddd-* cap4k-ddd-starter
```

Expected: runtime module boundaries, autoconfiguration entries, Mediator, repository/UoW, domain event, integration event, and Saga anchors are visible.

- [ ] **Step 2: Write `runtime-and-integration-map.md`**

Required current facts:

- Separate domain, application, adapter/infrastructure, integration-event, and Saga responsibilities based on current modules and autoconfiguration.
- Explain `Mediator` as the request dispatch entry point only according to code anchors.
- Explain repository and Unit of Work behavior only with source/test anchors.
- Explain domain event and integration event flow with current modules and adapter boundaries.
- Explain Saga runtime scope as compensation-oriented if confirmed by current code; do not present Saga as a generic callback-resume workflow engine unless code supports that wording.
- State that public tactical concept education belongs to Phase 2 public docs.

Required source anchors:

- `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/`
- runtime modules under `cap4k-ddd-*`
- runtime tests under `cap4k-ddd-starter/src/test/kotlin/`

Required verification commands in the page:

```powershell
rg -n "Mediator|UnitOfWork|Repository|DomainEvent|IntegrationEvent|Saga|AutoConfiguration" cap4k-ddd-* cap4k-ddd-starter
Get-Content cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- [ ] **Step 3: Inspect code-analysis and analysis generator anchors**

Run:

```powershell
Get-Content -Path cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt -Raw
Get-Content -Path cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt -Raw
```

Expected: compiler options, `nodes.json`, `rels.json`, `design-elements.json`, `build/cap4k-code-analysis`, `ir-analysis`, `flow`, and `drawing-board` behavior can be verified.

- [ ] **Step 4: Write `analysis-flow-and-verification-map.md`**

Required current facts:

- Compiler analysis output files: `nodes.json`, `rels.json`, `design-elements.json`.
- Default compiler analysis output root: `build/cap4k-code-analysis`, if still confirmed by code.
- Compiler option key: `cap4k.codeanalysis.outputDir`, if still confirmed by `OptionsKeys.kt`.
- `sources.irAnalysis.inputDirs` supplies inputs to `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` through the pipeline source provider.
- `cap4kAnalysisPlan` output is `build/cap4k/analysis-plan.json`, if still confirmed by task code.
- `flow` and `drawing-board` are analysis/observation generators, not the default business source generation path.
- Distinguish any flow export plugin behavior from the pipeline `flow` generator if both remain in code.

Required verification commands in the page:

```powershell
rg -n "cap4k.codeanalysis.outputDir|nodes.json|rels.json|design-elements.json|build/cap4k-code-analysis" cap4k-plugin-code-analysis-*
rg -n "ir-analysis|flow|drawing-board|analysis-plan.json|inputDirs" cap4k-plugin-pipeline-* 
```

- [ ] **Step 5: Scan runtime and analysis-flow maps for old paths**

Run:

```powershell
rg -n "analysis plan\.json|cap4k code analysis|generators\.flow\.enabled|generators\.drawingBoard\.enabled" docs/superpowers/analysis/runtime-and-integration-map.md docs/superpowers/analysis/analysis-flow-and-verification-map.md
```

Expected: no stale spaced paths or old enabled-switch claims appear as current facts.

- [ ] **Step 6: Commit runtime and analysis-flow maps**

Run:

```powershell
git add docs/superpowers/analysis/runtime-and-integration-map.md docs/superpowers/analysis/analysis-flow-and-verification-map.md
git commit -m "docs: map cap4k runtime and analysis flows"
```

Expected: commit succeeds with only the two new map files.

---

### Task 5: Create Release And Documentation Drift Maps

**Files:**
- Create: `docs/superpowers/analysis/release-map.md`
- Create: `docs/superpowers/analysis/documentation-and-skill-drift-map.md`

- [ ] **Step 1: Inspect release workflow and buildSrc anchors**

Run:

```powershell
Get-Content -Path .github/workflows/maven-central-release.yml -Raw
Get-Content -Path buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt -Raw
Get-Content -Path buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt -Raw
Get-Content -Path buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt -Raw
Get-Content -Path buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt -Raw
```

Expected: tag-trigger, `publish/maven-central` containment gate, `release.version`, and Central Portal task policy are visible.

- [ ] **Step 2: Write `release-map.md`**

Required current facts:

- Maven Central release workflow is tag-driven according to `.github/workflows/maven-central-release.yml`.
- The release tag pattern and branch containment policy must match workflow source.
- Document `release.version` behavior from `CentralReleaseVersion.kt` and its tests.
- Document Central Portal task gating and plugin marker policy from `CentralPublishTaskPolicy.kt` and tests.
- State that this page does not replace release runbooks outside current repo source.

Required verification commands in the page:

```powershell
rg -n "v\*|publish/maven-central|release.version|centralPortal|plugin marker|PluginMarker|publishPlugins" .github/workflows/maven-central-release.yml buildSrc/src/main/kotlin buildSrc/src/test/kotlin
```

- [ ] **Step 3: Inspect documentation and skills anchors**

Run:

```powershell
Get-ChildItem -Path docs/public -Recurse -File | Select-Object FullName
Get-ChildItem -Path skills -Recurse -File | Select-Object FullName
Get-Content -Path skills/scripts/validate-cap4k-skills.ps1 -Raw
```

Expected: current public-doc and skill locations are visible. Do not rewrite public docs or skills during this task.

- [ ] **Step 4: Write `documentation-and-skill-drift-map.md`**

Required current facts:

- Public docs live in `README.md` and `docs/public/`; they are for human cap4k users and will be rewritten in Phase 2.
- Skills live in `skills/`; they are for agents authoring business systems with cap4k and will be rewritten in Phase 3.
- Installed skills must be self-contained at runtime and cannot depend on `docs/superpowers/analysis/`, `docs/public/`, GitHub issues, historical specs/plans, Context7, or the cap4k source repository.
- Document high-risk drift terms from #98: KSP plan/generate wording, old enabled switches, enum-manifest-as-generator wording, `design validator`, `client/cli handler`, spaced output paths.
- Context7 compatibility is a future public-doc concern, not a skill runtime dependency.
- This page is a drift-audit map, not a detailed public or skill design.

Required verification commands in the page:

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|design validator|client/cli|analysis plan\.json|cap4k code analysis" README.md docs/public skills docs/superpowers/analysis
Get-Content skills/scripts/validate-cap4k-skills.ps1 -Raw
```

- [ ] **Step 5: Commit release and drift maps**

Run:

```powershell
git add docs/superpowers/analysis/release-map.md docs/superpowers/analysis/documentation-and-skill-drift-map.md
git commit -m "docs: map release and documentation drift"
```

Expected: commit succeeds with only the two new map files.

---

### Task 6: Remove Old Dated Analysis Files

**Files:**
- Delete: all eight files listed in `Files To Delete After Migration`

- [ ] **Step 1: Re-read old files only to verify no unique current fact was missed**

Run:

```powershell
foreach ($file in Get-ChildItem docs/superpowers/analysis/2026-*.md) {
  "--- $($file.Name) ---"
  rg -n "cap4kPlan|cap4kGenerate|cap4kGenerateSources|irAnalysis|integration_event|domain_service|saga|Maven Central|release.version|generated" $file.FullName
}
```

Expected: any useful current fact found here is already represented in one of the new active maps with a current source anchor. If a useful current fact is missing, add it to the relevant active map before deleting the old file.

- [ ] **Step 2: Delete the old dated files**

Run:

```powershell
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-bootstrap-plugin-and-template-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-business-project-authoring-capability-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-extension-spi-addon-and-gap-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-public-tactical-model-and-layering-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-11-cap4k-testing-analysis-and-flow-map.md
Remove-Item -LiteralPath docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md
```

Expected: no `docs/superpowers/analysis/2026-*.md` file remains.

- [ ] **Step 3: Confirm active analysis inventory**

Run:

```powershell
Get-ChildItem -Path docs/superpowers/analysis -File | Select-Object Name | Sort-Object Name
```

Expected file names:

```text
README.md
analysis-flow-and-verification-map.md
architecture-map.md
artifact-output-and-ownership-map.md
documentation-and-skill-drift-map.md
pipeline-and-gradle-map.md
release-map.md
runtime-and-integration-map.md
source-and-generator-contract-map.md
```

- [ ] **Step 4: Commit deletion of old analysis files**

Run:

```powershell
git add docs/superpowers/analysis
git commit -m "docs: remove stale dated analysis maps"
```

Expected: commit succeeds and records deletions of the eight dated files.

---

### Task 7: Final Drift Audit And Cross-Verification

**Files:**
- Verify: `docs/superpowers/analysis/*.md`

- [ ] **Step 1: Run stale phrase scan**

Run:

```powershell
rg -n "kspKotlin|sources\.irAnalysis\.enabled|generators\.flow\.enabled|generators\.drawingBoard\.enabled|design validator|client/cli|analysis plan\.json|cap4k code analysis" docs/superpowers/analysis
```

Expected: no matches, or matches only inside `Drift Watch` sections that explicitly mark the wording stale. If a match is a current factual claim, fix the map before continuing.

- [ ] **Step 2: Run current fact scan**

Run:

```powershell
rg -n "cap4k-code-analysis|analysis-plan\.json|cap4kGenerateSources|aggregate-projection|integration_event|domain_service|validator|build/generated/cap4k/main/kotlin|io.github.ldmoxeii.cap4k.pipeline" docs/superpowers/analysis
```

Expected: matches exist in the appropriate maps and include source anchors or verification commands.

- [ ] **Step 3: Confirm every active map has the required sections**

Run:

```powershell
$files = Get-ChildItem docs/superpowers/analysis -Filter *.md
$sections = @('## Purpose','## Current Facts','## Source Anchors','## Contracts','## Change Impact','## Verification','## Drift Watch','## Not Covered')
foreach ($file in $files) {
  $text = Get-Content $file.FullName -Raw
  foreach ($section in $sections) {
    if ($file.Name -eq 'README.md' -and $section -ne '## Verification' -and $section -ne '## Drift Watch') { continue }
    if ($text -notmatch [regex]::Escape($section)) { "$($file.Name) missing $section" }
  }
}
```

Expected: no missing-section output for map files. `README.md` may use navigation-specific headings but must still state reader, code-wins policy, active map links, and the default reading path.

- [ ] **Step 4: Check for references to deleted active paths**

Run:

```powershell
rg -n "2026-05-11-cap4k|2026-05-14-cap4k|snapshots/" docs/superpowers/analysis docs/superpowers/specs docs/superpowers/plans
```

Expected: references may remain in specs/plans as historical inputs, but not as active analysis navigation links.

- [ ] **Step 5: Run repository whitespace check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 6: Review changed files**

Run:

```powershell
git status --short
git diff --stat HEAD~5..HEAD -- docs/superpowers/analysis
```

Expected: the rewrite consists of the nine active analysis files and deletion of eight dated files. Adjust the `HEAD~5` range if the execution made a different number of commits.

- [ ] **Step 7: Final commit for audit fixes if needed**

If any audit fixes were made after Task 6, run:

```powershell
git add docs/superpowers/analysis
git commit -m "docs: audit cap4k analysis maps"
```

Expected: commit succeeds. If no audit fixes were needed, skip this commit and record that no additional commit was necessary.

---

## Self-Review Checklist

Before marking #98 implementation complete, verify:

- `docs/superpowers/analysis/README.md` defines reader, reading path, and code-wins policy.
- The eight active map files exist and use the shared map structure.
- The eight dated old files are deleted.
- No active analysis page relies on historical specs/plans as source of truth.
- Stale KSP, old enabled-switch, enum-manifest-as-generator, `design validator`, `client/cli handler`, and spaced output-path wording is removed from current facts or explicitly flagged as stale.
- Public-doc and skills concerns are only referenced as downstream consumers, not rewritten in detail.
- Each active map contains commands a future agent can rerun to refresh facts from code.
- `git diff --check` passes.

## Execution Handoff

Recommended execution mode: **Subagent-Driven**. Use a fresh subagent per task group when the environment exposes subagent/thread tools, then review the output before moving to the next task. If subagent tooling is unavailable, execute inline with `superpowers:executing-plans` and preserve the same task boundaries and commits.
