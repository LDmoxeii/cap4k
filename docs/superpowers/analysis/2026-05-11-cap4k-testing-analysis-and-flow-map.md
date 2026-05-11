# cap4k Testing, Analysis, And Flow Map

Date: 2026-05-11

This file maps how a business project should prove generated and hand-written cap4k code.

## Testing Contract

Current official direction:

- no built-in testing skeleton;
- no generated test artifacts;
- no runtime test-support library;
- docs plus project-local helpers plus runnable reference project discipline;
- first-version default focuses on domain and application behavior tests;
- adapter, persistence, and integration tests are allowed but not the first unified skeleton.

Business authoring should teach tests that explain the domain/application contract, not tests that merely preserve implementation residue from building an example project.

## Useful Test Layers

| Layer | Good tests | Avoid |
|---|---|---|
| Domain | Aggregate behavior, value decisions, lifecycle behavior, domain service decisions | Spring-heavy tests for pure behavior |
| Application command | command handler happy path, UoW save behavior, factory/repository/domain-service collaboration | direct DB assertions for every internal step when command output/domain events are enough |
| Application subscriber | domain/integration event follow-up orchestration | repository access in subscriber tests when a query/command boundary should exist |
| Adapter HTTP | smoke-level endpoint mapping and request dispatch | exhaustive controller tests duplicating command tests |
| Integration event | HTTP consume smoke or adapter bridge smoke | pretending external callback is a domain event |
| Analysis output | flow files exist and contain expected entries | brittle line-by-line snapshots of generated analysis |

Architecture-policing tests should be used sparingly. If a test only documents an implementation accident, it should be removed before presenting the project as a reference.

## Reference Project Expectations

A reference project should keep:

- domain behavior tests for meaningful domain decisions;
- application tests for command/subscriber happy paths;
- at least one end-to-end smoke path when the project claims runnable behavior;
- `.http` files for manual HTTP interaction when Swagger/OpenAPI is not part of the teaching goal;
- generated snapshot checks only when the snapshot is intentionally part of audit/learning.

It should remove:

- residue tests created during implementation debugging;
- tests named after defects rather than user-facing behavior;
- architecture contract tests that the user cannot learn from;
- duplicate tests that only assert generated files exist when compile/plan checks already prove it.

## Analysis Compiler

The compiler analysis plugin writes, per module:

- `build/cap4k-code-analysis/nodes.json`;
- `build/cap4k-code-analysis/rels.json`;
- `build/cap4k-code-analysis/design-elements.json`.

It detects relationships such as:

- command to command handler;
- query to query handler;
- client/cli to handler;
- command handler to aggregate;
- event subscriber relationships;
- request and UoW interactions.

`irAnalysis` consumes these directories and merges nodes, relationships, and design elements across modules.

## Pipeline Analysis

Pipeline analysis uses:

- `sources.irAnalysis.enabled`;
- `sources.irAnalysis.inputDirs`;
- `generators.flow.enabled`;
- `generators.drawingBoard.enabled`;
- `cap4kAnalysisPlan`;
- `cap4kAnalysisGenerate`.

Unlike hand-written input configuration, a normal multi-module example should set `inputDirs` to the module analysis directories:

- domain module `build/cap4k-code-analysis`;
- application module `build/cap4k-code-analysis`;
- adapter module `build/cap4k-code-analysis`.

`cap4kAnalysisPlan` should be run before `cap4kAnalysisGenerate` when introducing or changing analysis output roots.

## Flow Export Plugin

The separate flow export plugin has:

- `cap4kFlowExport`;
- `cap4kFlowClean`;
- `cap4kFlowCompile`;
- `cap4kFlow`;
- `cap4kFlow { inputDirs, outputDir, labelPrefixes, entryTypes, entryTypeExcludes }`.

It can infer input dirs from pipeline `sources.irAnalysis` when available. Public authoring should avoid presenting both pipeline analysis and flow export as mandatory unless the scenario needs both.

## Drawing Board

Drawing board output is useful for architecture/design review. It should become more important when integration event contracts are design-generated because it can show external communication shape.

Future authoring target:

- service A publishes integration event contract as design/drawing-board artifact;
- service B uses the shared design to generate local event class/subscriber skeleton;
- both services avoid depending on each other's application module.

This future path depends on integration-event design support that does not exist today.

## Verification Commands

A business-project verification ladder should usually be:

1. `cap4kPlan` for checked-in generation review;
2. `cap4kGenerate` only after plan review;
3. `cap4kGenerateSources` for build-owned sources;
4. module compile/test;
5. `cap4kAnalysisPlan`;
6. `cap4kAnalysisGenerate`;
7. optional end-to-end smoke using `.http` or integration test.

For docs-only or analysis-only changes, use targeted file scans and `git diff --check` instead of unrelated full builds.

## AI Authoring Implication

The future AI skill should carry the testing and analysis rules directly. It should not tell the agent to read the whole public authoring tree or clone a reference project during normal execution.
