# Cap4k Pipeline Drawing-Board Slice Design

## Summary

This slice migrates the legacy drawing-board export capability into the new pipeline architecture by introducing:

- an extension to `ir-analysis` source parsing so it also reads compiler-produced `design-elements.json`
- a drawing-board canonical slice that preserves the grouped design-element view previously built by `DrawingBoardContextBuilder`
- a new `drawing-board` generator module that plans one JSON document per design-element tag group
- Gradle wiring that enables drawing-board export through the new pipeline plugin without reviving the legacy `cap4kGenDrawingBoard` task

This slice intentionally does not change the compiler output format and does not yet improve design-template quality features such as `use()`, `imports()`, `type()`, auto-imports, nested-type expansion, or default-value inference.

## Why This Slice

After the completed `design-json + ksp-metadata` slice, the completed `db + aggregate` slice, and the completed `ir-analysis + flow` slice, the next missing legacy capability is drawing-board export.

Reasons:

- it completes the remaining analysis-output migration path
- it reuses the same compiler-produced input directory family as the new flow slice
- it proves the pipeline can consume one raw source snapshot and assemble two distinct canonical views from it
- it closes the current gap between migrated flow export and unmigrated drawing-board export

Compared with design-generator quality upgrades, drawing-board export is still architecture migration work, not output-polish work. That makes it the better next slice.

## Goals

- Extend `cap4k-plugin-pipeline-source-ir-analysis` to parse `design-elements.json`
- Add drawing-board-specific canonical models to the pipeline API
- Assemble a grouped drawing-board model from IR design elements
- Add a new `cap4k-plugin-pipeline-generator-drawing-board` module
- Generate one drawing-board JSON document per supported tag:
  - `cli`
  - `cmd`
  - `qry`
  - `payload`
  - `de`
- Wire the slice into `cap4k-plugin-pipeline-gradle`
- Prove the slice with unit tests and a Gradle TestKit fixture

## Non-Goals

- Change `Cap4kIrGenerationExtension` output schema
- Merge drawing-board output into a single file
- Reuse the old `cap4kGenDrawingBoard` task or old codegen context classes
- Add repository-user control over drawing-board grouping rules
- Add drawing-board template customization beyond the normal pipeline template override mechanism
- Improve command/query code generation quality

## Scope Decision

Three implementation approaches were considered:

1. add a dedicated drawing-board canonical slice and a dedicated generator
2. reuse `IrAnalysisSnapshot` directly in the planner without new canonical models
3. adapt the old `DrawingBoardContextBuilder` result into the new pipeline with minimal wrapping

The recommended choice is option 1.

Reasoning:

- it preserves the pipeline architecture boundary of `source -> canonical model -> plan -> render -> export`
- it keeps raw JSON parsing inside the source module
- it avoids dragging old context abstractions into the new core
- it keeps the future design-generator enhancement work decoupled from this slice

## Existing Behavior To Preserve

The legacy drawing-board path currently:

- reads `build/cap4k-code-analysis/design-elements.json` from adapter, application, and domain modules
- parses each design element record with:
  - `tag`
  - `package`
  - `name`
  - `desc`
  - `aggregates`
  - `entity`
  - `persist`
  - `requestFields`
  - `responseFields`
- de-duplicates across modules by `tag|package|name`
- groups elements by tag
- renders one JSON document per tag group through the shared `template/_tpl/drawing_board.json.peb` template

The new pipeline slice should preserve these rules:

- the same raw design-element fields remain available to the template
- de-duplication key remains `tag|package|name`
- grouping remains by `cli/cmd/qry/payload/de`
- empty groups should not produce files
- drawing-board output remains split per tag rather than merged into one document

The old Gradle task and old codegen context classes do not need to be preserved.

## Output Contract

This slice keeps the current per-tag split behavior.

The generator identifiers remain:

- `drawing_board_cli`
- `drawing_board_cmd`
- `drawing_board_qry`
- `drawing_board_payload`
- `drawing_board_de`

The default output directory should be `design/`.

Within that directory, the rendered file names should remain:

- `cli.json`
- `cmd.json`
- `qry.json`
- `payload.json`
- `de.json`

This matches the current template behavior more closely than introducing a new `drawing_board_<tag>.json` file-name contract.

## New Module Design

### `cap4k-plugin-pipeline-source-ir-analysis`

Purpose:

- keep ownership of compiler analysis directory parsing
- parse `nodes.json`, `rels.json`, and `design-elements.json`
- emit one raw snapshot describing the full contents of an IR analysis input directory set

Responsibilities:

- validate `nodes.json` and `rels.json` exactly as the current flow slice does
- read `design-elements.json` when present
- normalize malformed or partial element records conservatively
- preserve source provenance through `inputDirs`

Constraints:

- no grouping by tag
- no drawing-board file naming
- no template resolution

### `cap4k-plugin-pipeline-generator-drawing-board`

Purpose:

- consume canonical drawing-board models
- produce one artifact plan per non-empty tag group

Responsibilities:

- filter to supported tag groups
- skip empty groups
- compute output paths using a fixed default output directory plus optional generator override
- prepare a minimal template context for the shared JSON template

Constraints:

- does not parse raw JSON
- does not walk the analysis graph
- does not write files directly

## API And Model Changes

The pipeline API should gain raw snapshot types for design elements plus canonical types for grouped drawing-board export.

### Raw Snapshot Additions

Recommended raw snapshot types:

```kotlin
data class DesignFieldSnapshot(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignElementSnapshot(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignFieldSnapshot> = emptyList(),
    val responseFields: List<DesignFieldSnapshot> = emptyList(),
)
```

`IrAnalysisSnapshot` should become:

```kotlin
data class IrAnalysisSnapshot(
    override val id: String = "ir-analysis",
    val inputDirs: List<String>,
    val nodes: List<IrNodeSnapshot>,
    val edges: List<IrEdgeSnapshot>,
    val designElements: List<DesignElementSnapshot> = emptyList(),
) : SourceSnapshot
```

This keeps source ownership aligned with the real compiler output boundary: one analysis directory set yields graph data and drawing-board element data.

### Canonical Additions

Recommended canonical types:

```kotlin
data class DrawingBoardFieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DrawingBoardElementModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DrawingBoardFieldModel> = emptyList(),
    val responseFields: List<DrawingBoardFieldModel> = emptyList(),
)

data class DrawingBoardModel(
    val elements: List<DrawingBoardElementModel>,
    val elementsByTag: Map<String, List<DrawingBoardElementModel>>,
)
```

`CanonicalModel` should gain:

- `drawingBoard: DrawingBoardModel?`

This keeps graph traversal and drawing-board planning independent even though they share the same raw source snapshot.

## Source Parsing Rules

`IrAnalysisSourceProvider` should continue to require:

- `nodes.json`
- `rels.json`

That avoids weakening the already-landed flow slice.

`design-elements.json` should be treated as optional at source level:

- if the file exists, parse it
- if the file is absent, return an empty `designElements` list

The reason for this split:

- flow-only projects should keep working unchanged
- drawing-board-specific validation should happen when the drawing-board generator is enabled, not earlier

Parsing rules for `design-elements.json`:

- ignore non-object entries
- require non-blank `tag`
- default missing `package` to `""`
- default missing `name` to `""`
- default missing `desc` to `""`
- default missing `aggregates`, `requestFields`, and `responseFields` to empty lists
- only keep field items with non-blank `name`
- preserve `entity` and `persist` when present

## Canonical Assembly Rules

`DefaultCanonicalAssembler` should:

- map raw design-element snapshots to canonical drawing-board elements
- de-duplicate by `tag|packageName|name`
- preserve first-wins ordering across input directories
- group by supported tags only

Supported tags remain:

- `cli`
- `cmd`
- `qry`
- `payload`
- `de`

Unknown tags should be ignored in the drawing-board canonical slice. They may still remain available inside raw snapshots for future slices if needed, but this slice does not plan output for them.

The assembler should produce `drawingBoard = null` when there are no canonical elements after filtering. That keeps planner behavior consistent with other optional slices.

## Planning Rules

The new drawing-board generator should use a fixed generator id:

- `drawing-board`

It should produce one `ArtifactPlanItem` per non-empty supported tag group.

Recommended output mapping:

- tag `cli` -> `design/cli.json`
- tag `cmd` -> `design/cmd.json`
- tag `qry` -> `design/qry.json`
- tag `payload` -> `design/payload.json`
- tag `de` -> `design/de.json`

Recommended template id:

- `drawing-board/document.json.peb`

Recommended template context:

```kotlin
mapOf(
    "drawingBoardTag" to tag,
    "elements" to elementsForTag,
)
```

`elementsByTag` does not need to be passed to the template in this slice because each artifact renders one tag group at a time.

## Generator Configuration

The drawing-board generator should support one optional repository-level setting:

- `outputDir`

Rules:

- defaults to `design`
- must be a valid relative filesystem path
- absolute paths and `..` segments are rejected

This matches the flow generator pattern and keeps filesystem safety consistent across export-style generators.

## Error Handling

If the drawing-board generator is enabled and:

- `ir-analysis` source is not enabled, fail during Gradle plugin assembly
- `ir-analysis` source is enabled but no `design-elements.json` data is available, fail during planning with a clear message

Recommended planner failure text:

`drawing-board generator requires at least one parsed design-elements.json input.`

This avoids a false-success run where no files are generated and the user cannot tell whether the pipeline was misconfigured or simply empty.

## Renderer Design

Add one new preset template under the default Pebble preset:

- `drawing-board/document.json.peb`

This template should preserve the current JSON shape used by `template/_tpl/drawing_board.json.peb`:

- element-level fields stay the same
- `requestFields` and `responseFields` remain arrays of objects
- optional `entity` and `persist` remain conditional

The renderer does not need drawing-board-specific code. It only needs the new preset file and tests.

## Gradle Wiring

`cap4k-plugin-pipeline-gradle` should be extended so the new slice can be enabled through the same plugin already used for other migrated slices.

Recommended behavior:

- keep using `irAnalysisInputDirs` as the source configuration entry point
- add drawing-board generator registration in the plugin assembly path
- add `drawingBoardOutputDir` to the extension as a thin convenience property that maps to generator option `outputDir`

Task behavior should remain:

- `cap4kPlan` writes drawing-board plan items to `build/cap4k/plan.json`
- `cap4kGenerate` writes the rendered JSON files

As with the flow slice, `cap4kPlan` and `cap4kGenerate` should continue to depend on `compileKotlin` when IR analysis input dirs are configured.

## Testing Strategy

The slice should be proven with the same layered test strategy used by earlier pipeline work.

### Source Tests

`cap4k-plugin-pipeline-source-ir-analysis` should cover:

- parsing `design-elements.json`
- ignoring malformed element entries
- preserving optional `entity` and `persist`
- returning empty `designElements` when the file is absent

### Core Tests

`DefaultCanonicalAssemblerTest` should cover:

- assembling `DrawingBoardModel`
- de-duplication across multiple input directories
- grouping into `elementsByTag`
- filtering unsupported tags

### Generator Tests

`cap4k-plugin-pipeline-generator-drawing-board` should cover:

- one plan item per non-empty tag group
- relative output-dir validation
- skipping empty groups
- failure when no drawing-board data exists

### Renderer Tests

`PebbleArtifactRendererTest` should cover:

- rendering drawing-board JSON with optional `entity` and `persist`
- rendering field arrays including nullable/default-value values

### Gradle Functional Tests

`PipelinePluginFunctionalTest` should add a fixture that:

- configures `irAnalysisInputDirs`
- provides `nodes.json`, `rels.json`, and `design-elements.json`
- runs `cap4kPlan`
- runs `cap4kGenerate`
- asserts `design/cli.json` and `design/cmd.json` are generated

The fixture does not need to cover all five tags. Two representative tags are enough to prove grouping and file generation.

## Risks And Mitigations

### Risk: Regressing the existing flow slice

Because the same source module is being extended, it is easy to accidentally break the already-landed flow behavior.

Mitigation:

- keep `nodes.json` and `rels.json` validation unchanged
- add source tests that still cover graph-only input
- rerun `source-ir-analysis`, `generator-flow`, `renderer-pebble`, and `pipeline-gradle` verification together before claiming completion

### Risk: Re-introducing old context leakage

The old implementation used `DrawingBoardContext` and `elementsByTag` directly inside the codegen task path.

Mitigation:

- move only the behavior, not the old types
- keep grouping inside canonical assembly
- keep planning inside the new generator module

### Risk: Silent empty output

If the generator quietly skips output when `design-elements.json` is absent, users may think drawing-board export succeeded.

Mitigation:

- fail explicitly when drawing-board generator is enabled but no parsed design-element data exists

## Follow-Up Work After This Slice

Once this slice lands, the next remaining large work items under the approved migration strategy are:

1. repository-level DSL cleanup for source and generator switches
2. design-generator output quality upgrades:
   - `use()`
   - `imports()`
   - `type()`
   - auto-imports
   - nested-type handling
   - default-value handling

Those should stay separate from this slice so drawing-board migration remains a bounded architecture task.
