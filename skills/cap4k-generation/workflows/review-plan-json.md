# Review Plan Json

1. Run the relevant plan task before generation.
2. Open `build/cap4k/plan.json` or `build/cap4k/bootstrap-plan.json`.
3. For each item, inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
4. Classify every target as build-owned, checked-in skeleton, snapshot, template override, or handwritten source.
5. Stop if a handwritten target would be overwritten or if ownership is unclear.
6. Report the target classification before running generation.
