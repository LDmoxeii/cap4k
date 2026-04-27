# Cap4k irAnalysis Current-State Analysis

Date: 2026-04-27
Status: Analysis

## Summary

The current `irAnalysis` line does not need a broad rewrite before the validator and drawing-board work.

The main architectural issue is narrower:

- `nodes.json` and `rels.json` are analysis graph artifacts
- `design-elements.json` is a design-input projection
- all three currently travel through one `ir-analysis` source snapshot

That mixed transport is acceptable for now, but the semantics must be explicit.

The next implementation work should normalize the `design-elements.json` contract rather than redesign the whole analysis system.

## Current Artifacts

### `nodes.json`

Producer:

- `cap4k-plugin-code-analysis-compiler`
- `Cap4kIrGenerationExtension`
- `JsonFileMetadataSink`

Consumer:

- `cap4k-plugin-pipeline-source-ir-analysis`
- `FlowArtifactPlanner`

Purpose:

- represent source-code nodes discovered from Kotlin IR
- support flow graph export

Current node model:

```kotlin
data class Node(
    val id: String,
    val name: String,
    val fullName: String,
    val type: NodeType
)
```

The graph node vocabulary is analysis-oriented:

- controller
- controllermethod
- validator
- command
- commandhandler
- query
- queryhandler
- aggregate
- domainevent
- integrationevent
- and related handler/sender node types

This model is not design input.
It should remain graph-oriented.

### `rels.json`

Producer:

- `cap4k-plugin-code-analysis-compiler`
- `Cap4kIrGenerationExtension`
- `JsonFileMetadataSink`

Consumer:

- `cap4k-plugin-pipeline-source-ir-analysis`
- `FlowArtifactPlanner`

Purpose:

- represent source-code relationships discovered from Kotlin IR
- drive traversal from flow entry nodes to downstream command/query/client/aggregate/event nodes

Current relationship model:

```kotlin
data class Relationship(
    val fromId: String,
    val toId: String,
    val type: RelationshipType,
    val label: String? = null
)
```

This model is also graph-oriented and should not be forced into design semantics.

### `design-elements.json`

Producer:

- `cap4k-plugin-code-analysis-compiler`
- `DesignElementCollector`
- `DesignElementJsonWriter`

Consumer:

- `cap4k-plugin-pipeline-source-ir-analysis`
- `DefaultCanonicalAssembler`
- `DrawingBoardArtifactPlanner`

Purpose:

- project selected Kotlin source structures back into design input records
- support `cap4kAnalysisGenerate -> drawing_board_*.json`
- allow drawing-board JSON to become stable `cap4kGenerate` input

This artifact is not a pure IR graph artifact.
It is a design projection.

That distinction is the important boundary.

## Current Module Responsibilities

### `cap4k-plugin-code-analysis-core`

Current role:

- owns shared analysis output models
- contains graph models
- contains the current generic `DesignElement` projection model
- contains analysis compiler option keys

Current concern:

- `DesignElement` lives beside graph models, even though it is design projection data

This is semantically leaky, but not yet a reason for a broad module split.

The practical improvement is to document and evolve `DesignElement` as projection data.
Do not force `Node` and `Relationship` to follow design projection rules.

### `cap4k-plugin-code-analysis-compiler`

Current role:

- runs as a Kotlin compiler IR plugin
- scans compiled Kotlin source
- builds graph nodes and relationships
- writes `nodes.json` and `rels.json`
- derives request-to-aggregate information from graph relationships
- collects design projection records
- writes `design-elements.json`

Important implementation detail:

- `resolveOutputDir()` tries to resolve the current module root from source file paths
- output goes to that module's `build/cap4k-code-analysis`
- multi-module root merging is not done by the compiler plugin

This is a good boundary.
The compiler plugin should write local module snapshots.
The pipeline source should merge configured input directories.

Current concern:

- `DesignElementCollector` still emits old design tags such as `cmd`, `qry`, `cli`, `payload`, and `de`
- it does not collect ordinary validators
- its design projection scope is mixed into the compiler plugin, but the compiler plugin is the correct layer for source-code extraction

### `cap4k-plugin-pipeline-source-ir-analysis`

Current role:

- reads one or more `cap4k-code-analysis` directories
- requires `nodes.json` and `rels.json`
- optionally reads `design-elements.json`
- merges nodes by first node id wins
- de-duplicates edges by key
- de-duplicates design elements by `tag|package|name`
- emits one `IrAnalysisSnapshot`

This is currently the main merge point for multi-module analysis.

This boundary is reasonable.
It avoids a root-level physical merged snapshot.

Current concern:

- the snapshot type name `IrAnalysisSnapshot` contains both graph data and design projection data
- this is transport-level coupling, not necessarily architectural failure

### `cap4k-plugin-pipeline-generator-flow`

Current role:

- consumes `CanonicalModel.analysisGraph`
- filters allowed edge types
- selects entry nodes
- emits flow JSON, Mermaid, and index JSON

It does not consume `design-elements.json`.

This boundary is clean.

Current concern:

- legacy `cap4k-plugin-code-analysis-flow-export` still exists as an old plugin module
- the new pipeline does not use it
- keeping it around may create mental overhead, but it is not blocking the new analysis task family

### `cap4k-plugin-pipeline-generator-drawing-board`

Current role:

- consumes `CanonicalModel.drawingBoard`
- emits one JSON document per supported tag group
- writes `drawing_board_<tag>.json`

It consumes only design projection data.

Current concern:

- supported tag list currently omits `validator`
- the upstream projection still contains old tag names and requires canonical normalization

### `cap4k-plugin-pipeline-gradle`

Current role:

- separates source-generation tasks and analysis-export tasks
- `cap4kPlan` / `cap4kGenerate` use source runner
- `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` use analysis runner
- analysis runner installs only `IrAnalysisSourceProvider`, `DrawingBoardArtifactPlanner`, and `FlowArtifactPlanner`
- compile task inference depends on `irAnalysis.inputDirs` under subproject `build/`

This boundary matches the previous generate-entrypoint separation decision.

## Current Data Flow

### Analysis Production

The compiler plugin runs during Kotlin compilation.

It writes:

```text
<module>/build/cap4k-code-analysis/nodes.json
<module>/build/cap4k-code-analysis/rels.json
<module>/build/cap4k-code-analysis/design-elements.json
```

The plugin does not produce root-level merged output.

### Analysis Source Collection

The pipeline Gradle DSL configures:

```kotlin
sources {
    irAnalysis {
        enabled.set(true)
        inputDirs.from(...)
    }
}
```

`Cap4kProjectConfigFactory` turns those files into absolute `inputDirs`.

`IrAnalysisSourceProvider` reads all input dirs and merges them into one snapshot.

### Canonical Assembly

`DefaultCanonicalAssembler` projects one `IrAnalysisSnapshot` into:

- `AnalysisGraphModel`
- `DrawingBoardModel`

`AnalysisGraphModel` is used by `flow`.

`DrawingBoardModel` is used by `drawing-board`.

This split is good.
The questionable part is that both are derived from the same raw snapshot type.

### Analysis Export

`cap4kAnalysisPlan`:

- filters config to analysis sources/generators
- runs analysis runner with `exportEnabled = false`
- writes `build/cap4k/analysis-plan.json`

`cap4kAnalysisGenerate`:

- filters config to analysis sources/generators
- runs analysis runner with `exportEnabled = true`
- writes flow/drawing-board outputs

`cap4kPlan` and `cap4kGenerate` do not run analysis generators.

This is the correct entrypoint separation.

## Findings

### Finding 1: No Broad irAnalysis Rewrite Is Required

The current graph path is coherent enough:

```text
compiler -> nodes/rels -> ir-analysis source -> analysisGraph -> flow
```

The current drawing-board path is also conceptually coherent, but the projection contract needs normalization:

```text
compiler -> design-elements -> ir-analysis source -> drawingBoard -> drawing_board_*.json
```

The issue is not one monolithic bad architecture.
The issue is that `design-elements.json` still carries old design dialect.

### Finding 2: `design-elements.json` Needs A Stronger Contract

The file currently looks like a generic old drawing-board payload.

It should instead be treated as:

```text
analysis-side design projection
```

That means:

- projected tags should be new pipeline tags
- projected validator structure should be present when supported
- unsupported validators should not be projected into stable input
- aggregate unique validators should stay out of ordinary validator projection

This aligns with the already-written analysis design projection normalization spec.

### Finding 3: `IrAnalysisSnapshot` Is A Mixed Transport Type

`IrAnalysisSnapshot` currently contains:

- `inputDirs`
- `nodes`
- `edges`
- `designElements`

This mixes graph data and design projection data.

That is not ideal semantically, but it is acceptable as a transport snapshot because:

- both artifacts are produced by the same compiler plugin
- both are read from the same configured input directories
- both are used only by the analysis task family
- canonical assembly already splits them into different canonical models

Do not split this prematurely unless the model starts creating implementation friction.

### Finding 4: Compiler Plugin Is The Right Extraction Layer

Validator projection requires reading Kotlin source structure:

- annotation class
- `@Constraint`
- `@Target`
- nested validator type
- `ConstraintValidator<Annotation, ValueType>`
- annotation constructor defaults

That belongs in `analysis-compiler`, not in `pipeline-source-ir-analysis`.

`pipeline-source-ir-analysis` should parse JSON.
It should not infer Kotlin semantics that the compiler already knew.

### Finding 5: Multi-Module Merge Belongs In Pipeline Source

The old flow plugin inferred submodules and merged module snapshots.

The new pipeline uses explicit `sources.irAnalysis.inputDirs`.

That is better for the new architecture:

- no hidden module suffix assumptions
- no root-level generated merge directory required
- Gradle dependency inference can be based on configured input dirs
- dogfood/test projects can provide exact fixture dirs

Keep this direction.

## What Should Change

### Short-Term Changes

Do these before considering any larger restructuring:

1. Normalize `design-elements.json` projected tags.
2. Expand `DesignElement` / `DesignElementSnapshot` only enough for validator structural projection.
3. Add validator projection in `DesignElementCollector`.
4. Filter aggregate unique validators out of ordinary validator projection.
5. Add `validator` to drawing-board supported tags.
6. Keep `nodes.json` and `rels.json` unchanged.

These changes are enough to support the current roadmap items:

- analysis design projection normalization
- validator generation capability expansion

### Medium-Term Cleanup

Consider these later, only if implementation shows real friction:

- rename `DesignElement` to make the projection role explicit
- split projection models from graph models inside `code-analysis-core`
- add projection diagnostics for unsupported validators
- document the JSON schemas for graph and design projection separately

These are cleanup candidates, not prerequisites.

### Do Not Do Now

Do not:

- rewrite `Cap4kIrGenerationExtension`
- move design projection out of compiler extraction
- make `pipeline-source-ir-analysis` infer validator semantics
- create a root-level merged analysis directory
- merge analysis export back into `cap4kGenerate`
- resurrect legacy `cap4kFlowExport` behavior inside the new pipeline

## Recommended Decision

Do not open an `irAnalysis restructuring` implementation track right now.

Instead:

1. implement analysis design projection normalization
2. implement validator generation capability expansion
3. verify `cap4kAnalysisGenerate -> drawing_board_validator.json -> cap4kGenerate`
4. revisit irAnalysis restructuring only if those implementations expose unavoidable model friction

This keeps the work incremental and avoids destabilizing the already-working flow path.

## Role In The Combined Implementation Plan

This analysis is not an implementation spec.

It should be used as a constraint document for one combined plan:

```text
Cap4k Validator Projection And Generation Normalization
```

That plan should combine:

- analysis design projection normalization
- validator generation capability expansion

This analysis contributes the following constraints:

- do not rewrite `nodes.json`
- do not rewrite `rels.json`
- do not restructure flow traversal
- do not merge analysis export back into `cap4kGenerate`
- keep multi-module analysis merge in `IrAnalysisSourceProvider`
- keep compiler output module-local
- treat `design-elements.json` as the design projection artifact

The plan should include verification that graph output remains stable while design projection changes.

Recommended combined execution shape:

1. Add the shared validator design model fields.
2. Make `designJson -> designValidator -> generated Kotlin` work with those fields.
3. Make `analysis-compiler -> design-elements.json` emit the same fields.
4. Make `ir-analysis source -> drawing-board` preserve those fields.
5. Prove round-trip: generated or hand-written validator source -> `drawing_board_validator.json` -> `cap4kGenerate`.
6. Re-run flow/source-ir tests to confirm graph output was not disturbed.

## Residual Risks

### Risk: `DesignElement` Keeps Growing

Adding validator fields to the generic `DesignElement` model may make it feel less generic.

This is acceptable in the short term because `DesignElement` is not actually a pure generic IR model.
It is a design projection model.

If more design families add family-specific fields, a later split into typed projection records may become justified.

### Risk: Old Tag Compatibility Hides Producer Drift

`DefaultCanonicalAssembler` currently normalizes old analysis tags.

This can hide the fact that the compiler still emits old tags.

Mitigation:

- update compiler projection to emit standard tags
- keep assembler compatibility only temporarily if tests need migration
- update fixtures to assert new tags

### Risk: Flow And Drawing-Board Share Source Provider

Both flow and drawing-board depend on `IrAnalysisSourceProvider`.

Adding design projection parsing must not weaken graph validation.

Mitigation:

- keep `nodes.json` and `rels.json` required
- keep `design-elements.json` optional at source level
- make drawing-board fail clearly when no design elements exist
- keep source provider tests for flow-only input

## Conclusion

The current architecture is usable.

The immediate problem is semantic normalization, not structural collapse.

The correct next implementation line is:

```text
normalize design projection and validator structure
```

not:

```text
rewrite irAnalysis
```
