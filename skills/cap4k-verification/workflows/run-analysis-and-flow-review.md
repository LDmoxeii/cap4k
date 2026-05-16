# Run Analysis And Flow Review

1. Compile relevant modules when IR analysis depends on code relationships.
2. Confirm `sources.irAnalysis.inputDirs` points at module `build/cap4k-code-analysis` directories.
3. Run `cap4kAnalysisPlan`.
4. Inspect `build/cap4k/analysis-plan.json`.
5. Run `cap4kAnalysisGenerate`.
6. Review flow and drawing-board output for expected entries and relationship gaps.
7. Avoid claiming full behavior correctness from analysis output alone.
