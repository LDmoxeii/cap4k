# Run Analysis And Flow Review

1. Confirm analysis output is part of the task; do not generate analysis as unrelated churn.
2. Compile modules that feed compiler analysis so `build/cap4k-code-analysis` contains current nodes, rels, and design elements.
3. Check `sources.irAnalysis.inputDirs` point at the relevant module analysis directories.
4. Run `cap4kAnalysisPlan` before `cap4kAnalysisGenerate` when analysis output roots or generation behavior changed.
5. Inspect planned flow/drawing-board outputs and conflict policies.
6. Run `cap4kAnalysisGenerate`.
7. Review output for expected command-handler, query-handler, client/cli-handler, event subscriber, request, and UoW relationships.
8. Report exact commands, output paths, and meaningful flow findings.
