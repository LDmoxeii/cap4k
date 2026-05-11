# Review Generated Output

1. Identify the generator task and source inputs used for the output.
2. Read the plan output before file review when available.
3. For each relevant item, inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
4. Classify the target as build-owned generated source, checked-in skeleton, copied snapshot, template override, or handwritten source.
5. Check that checked-in skeletons preserve user edits through `SKIP` or an equivalent policy.
6. Check DB annotations, design tags, enum manifest references, and addon template IDs against the intended contract.
7. Verify copied snapshots are labeled as audit/learning snapshots, not active generator output.
8. Run targeted compile/tests or explain why this is review-only.
9. Report exact findings, changed ownership assumptions, and follow-up decisions for human audit.
