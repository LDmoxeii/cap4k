# Outcome

Introduce a pure compile-time analysis metadata contract that preserves lossless Analyzer, Drawing Board, and metadata-dependent Flow Analysis behavior without keeping analyzer-only annotations in `ddd-core`.

# Scope

- Move `BuildingBlock` and `AggregateElement` out of `ddd-core` into a dedicated published compile-time metadata module and package.
- Rename the annotations to responsibility-oriented metadata names without aliases or compatibility shims.
- Keep class target and BINARY retention; define no runtime behavior.
- Wire the metadata artifact into business modules as `compileOnly` when default generated templates can emit the annotations.
- Keep the default generator preset metadata-bearing.
- Update compiler Analyzer defaults, metadata consumers, Drawing Board, pipeline Flow Analysis, and legacy flow-export validation.
- Fail fast when requested analysis observes symbols whose required metadata is missing.
- Emit actionable diagnostics with missing symbols, affected capabilities, and restoration instructions.
- Add focused module, template, Analyzer, dependency-wiring, source-consumer, and cross-module Flow Analysis coverage.

# Non-goals

- No old annotation aliases, deprecated wrappers, or migration compatibility layer.
- No runtime scanning, reflection registry, or runtime meaning for the annotations.
- No sidecar skeleton metadata index and no attempt to reconstruct missing authoring metadata by guessing.
- No apparently complete partial Drawing Board or Flow output after metadata loss.
- No unrelated semantic round-trip repairs reserved for the later `fix/design-roundtrip-contract` slice.

# Acceptance examples

- Default generated design and aggregate sources compile with `cap4k-analysis-metadata` available only on compile classpaths; the artifact is absent from runtime dependency intent.
- A custom template may omit metadata, but requesting Drawing Board or metadata-dependent Flow Analysis then fails instead of silently dropping the symbol.
- A failure names each affected symbol, states whether Drawing Board and/or Flow Analysis is affected, and explains that restoring the default template or the corresponding metadata annotation plus the compile-only dependency re-enables the capability.
- Complete analyzer inputs continue to produce the same supported analysis outputs with the renamed annotations.
- `ddd-core` no longer contains or exposes the old annotations.

# Constraints and invariants

- Work is breaking and starts from the latest `origin/master` on `feature/analysis-metadata-contract` in an isolated worktree.
- Annotation retention remains BINARY and target remains CLASS.
- Analyzer truth remains compiled code plus explicit annotations; no sidecar skeleton index is introduced.
- Business behavior, handler bodies, repositories, and runtime contracts remain outside this change.
- Pull request targets `master` and follows repository PR validation scripts.

# Decisions

- Use a dedicated `cap4k-analysis-metadata` module under `com.only4.cap4k.analysis.metadata`.
- Rename `BuildingBlock` to `DesignBlockMetadata` and `AggregateElement` to `AggregateElementMetadata`.
- Default templates continue to emit the renamed annotations.
- Metadata-loss evidence is carried by existing Analyzer graph observations/diagnostics, not by a new skeleton index.
- The user's continuation request and ten-point audit contract explicitly confirm this breaking scope and completion through PR creation.

# Open questions

None.

# Verification expectations

Run at minimum:

- `:cap4k-analysis-metadata:test`
- `:ddd-core:test`
- `:cap4k-plugin-pipeline-renderer-pebble:test`
- `:cap4k-plugin-code-analysis-compiler:test`
- `:cap4k-plugin-pipeline-source-ir-analysis:test`
- `:cap4k-plugin-pipeline-generator-drawing-board:test`
- `:cap4k-plugin-pipeline-generator-flow:test`
- `:cap4k-plugin-code-analysis-flow-export:test`
- focused `:cap4k-plugin-pipeline-gradle:test` dependency/template/analysis functional coverage
- cross-module Flow Analysis coverage and generated-module compilation coverage
