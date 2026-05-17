# Run Analysis And Flow Review

1. Compile relevant modules when IR analysis depends on code relationships.
2. Confirm `sources.irAnalysis.inputDirs` points at module `build/cap4k-code-analysis` directories.
3. Run `cap4kAnalysisPlan`.
4. Inspect `build/cap4k/analysis-plan.json`.
5. Run `cap4kAnalysisGenerate`.
6. Review flow and drawing-board output for expected entries and relationship gaps.
7. Avoid claiming full behavior correctness from analysis output alone.

## Flow-Orchestration Review

- [ ] Commands are not acting as process coordinators by reading state, branching, and sending multiple follow-up commands.
- [ ] Command-to-command calls, if present, are local reuse inside one synchronous write use case.
- [ ] Fact-driven continuation uses domain events, external fact entries, jobs, or Saga instead of technical "command completed" events.
- [ ] Multiple listeners for the same event do not assume ordering.
- [ ] Listener-triggered commands are idempotent and zero-trust.
- [ ] External fact entries route writes into commands and do not mutate aggregates directly.

## Generated Ownership Review

- [ ] Newly added command, query, event, subscriber, client, validator, and API payload surfaces were checked against `design.json` generation support.
- [ ] Generator-supported surfaces were added through `design.json` and regeneration.
- [ ] Any handwritten surface includes the reason it could not be generated.
- [ ] Generated subscriber shells were not deleted merely because they were empty.
