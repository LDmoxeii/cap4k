# Testing And Verification

## Default Verification

- Use TDD when changing behavior: add or update a focused failing test before implementation when feasible.
- Run compile and tests for affected modules after code changes.
- For docs-only or skill-only work, use targeted scans and `git diff --check`.
- Report exact commands, exit status, and meaningful results.

## Generation Verification

- Make a plan before generating: `cap4kPlan`, `cap4kBootstrapPlan`, or the project-specific equivalent.
- Inspect planned `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
- Generate only after plan review and after classifying build-owned, skeleton, snapshot, and handwritten targets.
- Re-run compile/tests after generated sources or checked-in skeletons change.

## Analysis Verification

- Run compiler analysis by compiling the relevant modules when analysis output depends on code relationships.
- Run `cap4kAnalysisPlan` before `cap4kAnalysisGenerate` when analysis roots or outputs change.
- Review generated flow/drawing-board outputs at a useful level; avoid brittle line-by-line snapshot claims.

## Evidence

- Evidence should name the command, scope, result, and any skipped checks with reasons.
- Do not claim behavior is complete until compile/tests or the agreed substitute checks have run.
- Human audit remains required for domain decisions and final acceptance.
