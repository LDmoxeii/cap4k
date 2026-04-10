# Cap4k Pipeline IR Flow Slice Design

## Summary

This slice migrates the legacy flow export capability into the new pipeline architecture by introducing:

- an `ir-analysis` source module that reads compiler-produced `nodes.json` and `rels.json`
- a `flow` generator module that plans per-entry flow JSON, Mermaid, and flow index artifacts
- Gradle wiring that enables the slice through the new pipeline plugin without reusing the legacy flow-export plugin

This slice intentionally does not migrate drawing-board generation. `design-elements.json` remains out of scope until the flow path is stable.

## Why This Slice

After the completed `design-json + ksp-metadata` slice and the completed `db + aggregate` slice, the next highest-value migration target is the legacy `cap4k-plugin-code-analysis-flow-export` path.

Reasons:

- it already forms a self-contained input/output chain
- its inputs are stable compiler artifacts, not live Gradle or JDBC state
- it exercises the next missing source type in the new architecture
- it proves the pipeline can export non-Kotlin artifacts and multi-file output

Compared with drawing-board export, flow export has fewer moving parts because it only depends on `nodes.json` and `rels.json`.

## Goals

- Add a new `cap4k-plugin-pipeline-source-ir-analysis` module
- Read merged IR analysis inputs from repository-configured module output directories
- Normalize IR graph inputs into canonical pipeline models
- Add a new `cap4k-plugin-pipeline-generator-flow` module
- Generate:
  - one flow JSON per entry node
  - one Mermaid file per entry node
  - one `index.json`
- Wire the new slice into `cap4k-plugin-pipeline-gradle`
- Prove the slice with fixture-backed tests and Gradle TestKit

## Non-Goals

- Migrate drawing-board generation
- Read or depend on `design-elements.json`
- Reuse the old `cap4kFlowExport` task or old flow-export Gradle extension
- Change compiler plugin output format
- Add repository-user control over traversal rules or entry-node algorithm

## Recommended Scope Decision

Three possible next slices were considered:

1. `ir-analysis + flow`
2. `ir-analysis + drawing-board`
3. `ir-analysis + flow + drawing-board`

The recommended choice is `ir-analysis + flow`.

Reasoning:

- it is the narrowest closed migration path
- it avoids the extra `design-elements.json` dependency
- it migrates one source and one generator cleanly
- it keeps review and regression surface smaller than a combined flow+drawing-board rollout

## Existing Behavior To Preserve

The legacy `Cap4kFlowExportPlugin` currently:

- reads `nodes.json` and `rels.json` from one or more `build/cap4k-code-analysis` directories
- normalizes raw node and edge records
- filters to a fixed set of allowed edge types
- determines entry nodes from a fixed set of entry node types
- walks the graph from each entry node
- writes:
  - `<slug>.json`
  - `<slug>.mmd`
  - `index.json`

The new pipeline slice should preserve these functional rules:

- same allowed edge type allowlist
- same entry node type defaults
- same traversal semantics
- same slugging approach for per-entry files
- same basic payload shapes for flow JSON and index JSON

It does not need to preserve the old Gradle task or extension API.

## New Module Design

### `cap4k-plugin-pipeline-source-ir-analysis`

Purpose:

- read IR analysis output directories
- parse `nodes.json` and `rels.json`
- validate required files exist
- emit a raw pipeline snapshot with source provenance

Responsibilities:

- parse JSON only
- normalize missing or blank node fields to stable defaults
- preserve all edges before flow-specific filtering

Constraints:

- no graph traversal
- no flow entry selection
- no Mermaid rendering

### `cap4k-plugin-pipeline-generator-flow`

Purpose:

- consume canonical IR graph models
- compute per-entry reachable subgraphs
- plan JSON, Mermaid, and index outputs

Responsibilities:

- apply allowed edge filtering
- determine entry nodes from fixed entry type set
- generate output artifact plans only

Constraints:

- does not write files directly
- does not parse raw JSON
- does not know Gradle module discovery rules

## API And Model Changes

The API layer should gain IR-specific snapshot and canonical models.

### Source Snapshot

Recommended raw snapshot types:

```kotlin
data class IrNodeSnapshot(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class IrEdgeSnapshot(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class IrAnalysisSnapshot(
    override val id: String = "ir-analysis",
    val inputDirs: List<String>,
    val nodes: List<IrNodeSnapshot>,
    val edges: List<IrEdgeSnapshot>,
) : SourceSnapshot
```

### Canonical Model

Recommended canonical additions:

```kotlin
data class AnalysisNodeModel(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class AnalysisEdgeModel(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class AnalysisGraphModel(
    val inputDirs: List<String>,
    val nodes: List<AnalysisNodeModel>,
    val edges: List<AnalysisEdgeModel>,
)
```

`CanonicalModel` should gain:

- `analysisGraph: AnalysisGraphModel?`

This slice does not require drawing-board-specific canonical models yet.

## Fixed Rules For Flow Generation

These rules should be moved into the new generator, not exposed as repository configuration.

### Allowed Edge Types

Preserve the current allowlist from the legacy flow-export plugin.

### Entry Node Types

Default entry node types remain:

- `controllermethod`
- `commandsendermethod`
- `querysendermethod`
- `clisendermethod`
- `validator`
- `integrationevent`

The pipeline DSL may later expose enable/disable controls for flow generation itself, but not custom traversal rules in this slice.

### Traversal

Traversal should remain:

- forward-only
- reachability-based
- edge-deduplicated
- node-deduplicated

### Slugging

Preserve the existing slug generation behavior:

- normalize non-alphanumeric spans to `_`
- trim leading/trailing `_`
- cap length
- append digest suffix on collision

## Output Planning

The flow generator should emit three artifact families:

1. flow JSON files
2. Mermaid files
3. flow index JSON

Recommended module role:

- `project`

Recommended default output root:

- `flows/`

Planned output examples:

- `flows/controller_order_submit.json`
- `flows/controller_order_submit.mmd`
- `flows/index.json`

The exact filenames should come from the generator slugging logic, not from template expansion.

## Rendering Strategy

This slice needs two rendering modes:

1. structured JSON rendering
2. Mermaid text rendering

The simplest clean design is:

- keep rendering inside the renderer layer
- add preset templates or renderer helpers for:
  - `flow/entry.json`
  - `flow/entry.mmd`
  - `flow/index.json`

However, unlike Pebble-based Kotlin code generation, Mermaid rendering is algorithmic rather than mostly textual substitution.

Recommended design:

- `flow` generator prepares rich context objects
- `renderer-pebble` supports JSON templates for `entry.json` and `index.json`
- Mermaid content is produced by a dedicated helper inside the flow generator module and passed as a string field to a simple text template, or rendered directly by a small flow-specific renderer helper

The key rule is unchanged:

- generator plans artifacts
- renderer turns plan context into content

The generator must not write files directly.

## Gradle Integration

`cap4k-plugin-pipeline-gradle` should be extended with the minimum new fields needed to enable the flow slice.

Recommended extension additions:

- `irAnalysisInputDirs`
- `flowOutputDir`

Behavior:

- if `irAnalysisInputDirs` is non-empty, enable source `ir-analysis`
- if source `ir-analysis` is enabled and generator `flow` is present in the fixed runner, enable generator `flow`
- `flowOutputDir` defaults to `flows`

This slice does not need a full new nested DSL yet. It should follow the same thin-adapter pattern used in the existing pipeline Gradle module.

## Testing Strategy

### Source Module Tests

Add fixture tests for `cap4k-plugin-pipeline-source-ir-analysis` that verify:

- successful parsing of `nodes.json` and `rels.json`
- missing-file failure is clear
- duplicate node ids preserve first normalized node
- blank node fields normalize as expected

### Core Tests

Extend canonical assembler tests to verify:

- `IrAnalysisSnapshot` maps into `AnalysisGraphModel`
- existing request/aggregate slices remain unaffected when IR snapshot is absent

### Generator Tests

Add `cap4k-plugin-pipeline-generator-flow` tests that verify:

- allowed edge filtering
- entry node selection
- reachable subgraph extraction
- slug collision handling
- planned artifact paths and template ids

### Renderer Tests

Add renderer coverage for:

- flow JSON rendering
- flow index JSON rendering
- Mermaid rendering path if handled by the renderer layer

### Gradle Functional Tests

Add a TestKit fixture with:

- root project
- one or more fake `build/cap4k-code-analysis` directories
- sample `nodes.json`
- sample `rels.json`

Functional assertions:

- `cap4kPlan` includes `flow/*.json`, `flow/*.mmd`, and `flow/index.json` artifacts
- `cap4kGenerate` writes the expected files under the configured output directory
- missing `nodes.json` or `rels.json` fails clearly

## Migration Order

Recommended implementation order:

1. expand API models for IR snapshot and canonical graph
2. add `source-ir-analysis`
3. extend canonical assembler
4. add `generator-flow`
5. add renderer support for flow payloads
6. wire Gradle integration and TestKit fixture

This keeps the migration aligned with earlier slices and allows small verification checkpoints.

## Risks

### Main Risk: Over-coupling flow output to old plugin code

If the new slice copies the old `Cap4kFlowExportPlugin` wholesale into the generator or Gradle module, the new boundaries will collapse immediately.

Mitigation:

- raw parsing stays in source module
- graph canonicalization stays in core
- flow traversal stays in generator
- file output stays in renderer/export

### Secondary Risk: Mermaid rendering leaking into generator logic

If Mermaid assembly is implemented entirely inside the generator and hidden as pre-rendered file content, the renderer layer becomes inconsistent.

Mitigation:

- allow a small flow-specific render helper, but keep artifact production as a renderer concern

### Secondary Risk: Scope creep into drawing-board

Once `design-elements.json` is nearby, it is easy to start migrating drawing-board in the same slice.

Mitigation:

- do not add `design-elements.json` to the source snapshot in this slice
- keep drawing-board for the next dedicated plan

## Decision

The next implementation slice should be:

- `ir-analysis source + flow generator/export`

and it should explicitly exclude:

- drawing-board generation
- `design-elements.json`
- old flow-export task compatibility
