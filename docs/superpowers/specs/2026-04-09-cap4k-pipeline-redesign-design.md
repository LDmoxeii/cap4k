# Cap4k Pipeline Redesign

## Summary

This design replaces the current cap4k code generation and analysis plugin structure with a fixed-stage pipeline architecture. The redesign is intentionally breaking: old Gradle extension structure, old task internals, and old configuration formats are not preserved as compatibility targets.

The new system keeps two constraints fixed:

- Pipeline execution order is not customizable by project users.
- Project users may enable or disable generators and switch data sources through repository configuration.

The main goal is to remove the current model and boundary confusion across Gradle tasks, context builders, code generators, KSP metadata readers, IR analysis consumers, and template rendering logic.

## Background

The current cap4k plugin system already contains the raw capabilities needed for a generator platform:

- database-driven aggregate generation
- design-json-driven request/query/client/event generation
- KSP metadata extraction for aggregate information
- IR analysis export for graph and drawing-board outputs
- template-driven file generation with alias and tag resolution

However, these capabilities are split across task classes, context builders, generators, template selection logic, and analysis/export plugins. The result is that responsibilities are mixed:

- Gradle tasks contain business generation logic
- generators depend on task context details instead of stable canonical models
- multiple plugins duplicate module discovery and output resolution logic
- source-specific models leak directly into generation stages
- documentation and configuration have already drifted from implementation

The redesign solves this by introducing a single pipeline core with a single canonical model and strict module boundaries.

## Goals

- Build a single fixed-stage pipeline for collection, normalization, enrichment, planning, rendering, and export.
- Separate data collection from canonical modeling, generation planning, rendering, and filesystem export.
- Support repository-level configuration for:
  - enabling or disabling generators
  - enabling or disabling data sources
  - configuring source parameters
  - configuring template presets and overrides
- Keep execution order owned by plugin developers.
- Make generator modules consume only canonical models, not Gradle, database, KSP, or IR details directly.
- Make rendering replaceable, with Pebble as the default renderer implementation.
- Support breaking changes where needed to cleanly reset the architecture.

## Non-Goals

- Preserve backward compatibility with old configuration files or task internals.
- Allow project users to define custom pipeline stage order.
- Allow project users to inject arbitrary runtime logic or scripts into the pipeline.
- Introduce LiteFlow or another external orchestration framework as the pipeline runtime.
- Build a general-purpose workflow engine.

## Architecture Overview

The redesigned system is composed of five layers:

1. `cap4k-plugin-api`
2. `cap4k-plugin-core`
3. `cap4k-plugin-source-*`
4. `cap4k-plugin-generator-*`
5. `cap4k-plugin-gradle`

An additional rendering split is introduced:

- `cap4k-plugin-renderer-api`
- `cap4k-plugin-renderer-pebble`

Responsibility rules:

- sources only read inputs
- core only orchestrates transformation and planning
- generators only produce artifact plans
- renderers only render plans into artifact content
- Gradle only adapts repository configuration and task entrypoints

## Module Design

### `cap4k-plugin-api`

Purpose:

- define stable contracts and shared models

Contents:

- `ProjectConfig`
- `SourceSnapshot`
- `CanonicalModel`
- `ArtifactPlanItem`
- `RenderedArtifact`
- `PipelineResult`
- `SourceProvider`
- `GeneratorProvider`
- renderer-facing contracts if shared beyond renderer-api

Constraints:

- no Gradle dependency
- no database dependency
- no Pebble dependency
- no KSP or Kotlin compiler dependency

### `cap4k-plugin-core`

Purpose:

- implement the fixed pipeline runner
- own normalization, enrichment, planning, conflict detection, diagnostics, and execution reporting

Contents:

- `PipelineRunner`
- stage implementations
- canonical model assembly logic
- validation and diagnostics
- plan merge and conflict detection

Constraints:

- depends only on `api` and renderer abstraction
- does not depend on Gradle APIs

### `cap4k-plugin-renderer-api`

Purpose:

- abstract template resolution and rendering

Contents:

- `TemplateResolver`
- `ArtifactRenderer`
- template lookup contracts

### `cap4k-plugin-renderer-pebble`

Purpose:

- provide the default Pebble-based rendering implementation

Contents:

- Pebble engine setup
- template preset loading
- override directory support
- encoding and render diagnostics

### `cap4k-plugin-source-db`

Purpose:

- collect database schema data into a `SchemaSnapshot`

Constraints:

- no aggregate inference
- no template variable generation
- no direct file generation

### `cap4k-plugin-source-design-json`

Purpose:

- collect design specification files from repository configuration into a `DesignSpecSnapshot`

Notes:

- if YAML or TOML support is needed later, it should be another source module, not a generator concern

### `cap4k-plugin-source-ksp-metadata`

Purpose:

- read KSP outputs and convert them into a `KspMetadataSnapshot`

### `cap4k-plugin-source-ir-analysis`

Purpose:

- read or trigger IR analysis outputs and convert them into an `IrAnalysisSnapshot`

### `cap4k-plugin-generator-aggregate`

Purpose:

- consume canonical aggregate and schema models
- produce aggregate-related artifact plans

### `cap4k-plugin-generator-design`

Purpose:

- consume canonical request/query/command/client/event models
- produce design-code artifact plans

### `cap4k-plugin-generator-drawing-board`

Purpose:

- consume canonical design and graph models
- produce `drawing_board.json` artifact plans

### `cap4k-plugin-generator-flow`

Purpose:

- consume canonical graph models
- produce flow JSON and Mermaid artifact plans

### `cap4k-plugin-gradle`

Purpose:

- expose the only public Gradle extension and task entrypoints
- translate repository DSL into `ProjectConfig`
- invoke the pipeline runner

Constraints:

- no embedded generation logic
- no duplicated module resolution logic
- no source-specific canonicalization logic

## Core Model Design

The redesign standardizes four model layers.

### 1. `ProjectConfig`

Repository-owned configuration model.

Responsibilities:

- define enabled sources
- define enabled generators
- define source parameters
- define module layout
- define base package and output roots
- define template preset and override directories
- define output conflict policy

Important rule:

- Gradle DSL is only an adapter into `ProjectConfig`; business logic must not read Gradle `Property` objects directly

### 2. `SourceSnapshot`

Raw collected input from individual sources.

Examples:

- `SchemaSnapshot`
- `DesignSpecSnapshot`
- `KspMetadataSnapshot`
- `IrAnalysisSnapshot`

Rules:

- preserve source provenance
- do not perform cross-source inference
- do not prepare template variables

### 3. `CanonicalModel`

The single standard internal model for the entire pipeline.

Minimum recommended submodels:

- `ModuleModel`
- `AggregateModel`
- `EntityModel`
- `RequestModel`
- `EventModel`
- `ApiPayloadModel`
- `AnalysisGraphModel`
- `TemplateBindingModel`

Rules:

- all generators read only canonical models
- all source-specific structures must be normalized before planning starts

### 4. `ArtifactPlan`

A generation plan, not a final file.

Each plan item should include:

- generator id
- target module
- target logical artifact type
- template id or template path
- template context payload
- target output path
- conflict policy
- metadata for diagnostics

Rules:

- generators return plans
- rendering and export happen later

## Fixed Pipeline

Pipeline order is fixed and owned by plugin developers.

### Stage 1: `collect`

Input:

- `ProjectConfig`

Output:

- `List<SourceSnapshot>`

Responsibilities:

- execute enabled sources
- perform source-local validation
- collect raw inputs

### Stage 2: `normalize`

Input:

- source snapshots

Output:

- initial `CanonicalModel`

Responsibilities:

- convert source snapshots into standard model structures
- unify naming and shape differences
- avoid cross-source inference here

### Stage 3: `enrich`

Input:

- normalized canonical model

Output:

- enriched canonical model

Responsibilities:

- fill cross-source relationships
- resolve module ownership
- resolve aggregate associations
- apply naming rules and package strategy
- attach template binding hints

### Stage 4: `plan`

Input:

- enriched canonical model

Output:

- `List<ArtifactPlanItem>`

Responsibilities:

- execute enabled generators
- produce artifact plans only
- perform generator-level diagnostics

### Stage 5: `render`

Input:

- artifact plans

Output:

- `List<RenderedArtifact>`

Responsibilities:

- resolve templates
- apply Pebble or another renderer
- produce final content and render diagnostics

### Stage 6: `export`

Input:

- rendered artifacts

Output:

- filesystem writes and execution report

Responsibilities:

- write files
- apply conflict policy
- support dry-run and plan-only output
- report created, changed, skipped, and conflicted outputs

## Key Interfaces

Recommended stable interfaces:

```kotlin
interface SourceProvider {
    val id: String
    fun collect(ctx: CollectContext): SourceSnapshot
}

interface GeneratorProvider {
    val id: String
    fun plan(ctx: PlanContext): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
```

Rules implied by these interfaces:

- sources do not render templates
- generators do not write files
- Gradle tasks do not perform business generation logic

## Repository Configuration DSL

The new DSL should expose only four top-level blocks:

- `project`
- `sources`
- `generators`
- `templates`

Example:

```kotlin
cap4k {
  project {
    basePackage.set("com.example.demo")
    layout.set("multi-module")
    module("domain", ":demo-domain")
    module("application", ":demo-application")
    module("adapter", ":demo-adapter")
  }

  sources {
    db {
      enabled.set(true)
      url.set("jdbc:mysql://localhost:3306/demo")
      username.set("root")
      password.set("password")
      schema.set("demo")
      includeTables.set(listOf("*"))
      excludeTables.set(emptyList())
    }

    designJson {
      enabled.set(true)
      files.from("design/design.json")
    }

    kspMetadata {
      enabled.set(true)
      inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
    }

    irAnalysis {
      enabled.set(true)
      inputDir.set("demo-application/build/cap4k-code-analysis")
    }
  }

  generators {
    aggregate {
      enabled.set(true)
    }

    design {
      enabled.set(true)
    }

    drawingBoard {
      enabled.set(false)
    }

    flow {
      enabled.set(false)
    }
  }

  templates {
    preset.set("ddd-default")
    overrideDirs.from("codegen/templates")
    conflictPolicy.set("skip")
  }
}
```

Constraints:

- project users may enable or disable sources
- project users may enable or disable generators
- project users may pass source and generator parameters
- project users may override templates
- project users may not reorder pipeline stages
- project users may not inject custom execution logic into the pipeline

## Migration Strategy

The implementation should proceed in five steps.

### Step 1: Establish the new kernel

Create:

- `api`
- `core`
- `renderer-api`
- `renderer-pebble`
- `gradle`

Deliverables:

- `ProjectConfig`
- `CanonicalModel`
- `ArtifactPlan`
- fixed pipeline runner
- baseline tests for pipeline stages

Do not migrate old generators yet.

### Step 2: Migrate sources

Recommended order:

1. `design-json`
2. `ksp-metadata`
3. `db`
4. `ir-analysis`

Reason:

- IR analysis currently has the deepest coupling and should move last

### Step 3: Stabilize the canonical model

This is the main architecture milestone.

Tasks:

- convert old task-specific contexts into canonical models
- lock module ownership rules
- lock aggregate/request/event model structure
- remove source-specific data leakage from generation logic

If this step is weak, generators will re-fragment.

### Step 4: Rewrite generators

Recommended order:

1. aggregate
2. design
3. drawing-board
4. flow

Rules:

- each generator produces only `ArtifactPlanItem`
- no generator accesses Gradle APIs directly
- no generator accesses database or source snapshots directly

### Step 5: Collapse the Gradle surface

Retire old task internals and expose a smaller new task surface.

Recommended tasks:

- `cap4kGenerate`
- `cap4kPlan`
- `cap4kExportFlow`

`cap4kPlan` is important because it allows artifact planning and diagnostics before any file write.

## Validation Strategy

### Core tests

- stage-level tests for collect, normalize, enrich, plan, render, export

### Source tests

- fixture-based tests that assert snapshot outputs from stable inputs

### Generator tests

- canonical-model-based snapshot tests for produced artifact plans
- render output tests for key templates

### Gradle tests

- Gradle TestKit integration tests against a minimal demo project

### End-to-end tests

- at least one real sample project kept as a regression target

## Error Handling and Diagnostics

The redesigned pipeline should standardize diagnostics by stage.

Recommended behavior:

- source failures report source id, input location, and validation error
- normalize failures report the source artifact that could not be normalized
- enrich failures report unresolved cross-source references
- plan failures report generator id and affected canonical object
- render failures report template id, output path, and render context summary
- export failures report output path, conflict policy, and filesystem error

`PipelineResult` should include:

- stage summaries
- warnings
- errors
- generated artifact count
- skipped artifact count
- conflict count

## Risks

### Highest risk: canonical model instability

If the canonical model is under-designed, source modules and generator modules will continue to encode local assumptions and the redesign will fail structurally.

Mitigation:

- lock the canonical model before migrating all generators
- keep normalization and enrichment explicit and separately tested

### Medium risk: IR analysis migration

IR analysis is currently the most deeply coupled source of graph and design export information.

Mitigation:

- migrate it last
- keep its source module strictly snapshot-oriented
- avoid letting compiler-plugin data structures leak into generators

### Medium risk: renderer/template coupling

Existing code mixes template selection, path resolution, and generation logic.

Mitigation:

- force generators to return artifact plans only
- move template resolution into renderer stage

## Decisions

- LiteFlow will not be introduced for this redesign.
- Pipeline execution order remains fixed and developer-owned.
- Project users may only configure sources, generators, templates, and parameters.
- Backward compatibility is not a design goal.
- The redesign will be implemented as a new architecture with staged migration rather than incremental cleanup of current task classes.

## Open Implementation Direction

The only major implementation freedom intentionally left open is rollout sequencing detail. The target architecture, module responsibilities, stage order, and configuration boundary are fixed by this design.
