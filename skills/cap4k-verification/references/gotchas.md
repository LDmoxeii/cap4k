# Verification Gotchas

- `cap4kAnalysisGenerate` does not replace `cap4kGenerate`.
- Analysis output proves observed relationships, not business correctness.
- Passing plan generation does not prove generated source compiles.
- Docs-only changes still need scans and `git diff --check`.
