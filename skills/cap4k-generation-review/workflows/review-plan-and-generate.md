# Review Plan And Generate

1. Read technical design and generator inputs.
2. Run or request cap4kPlan when allowed.
3. Review `plan.json` or `bootstrap-plan.json`.
4. Classify output ownership as checked-in skeleton, build-owned generated source, generated snapshot or evidence, template override, or handwritten logic.
5. Check `conflictPolicy`, `templateId`, module placement, `outputKind`, `generatorId`, `outputPath`, and `resolvedOutputRoot`.
6. Stop if handwritten logic would be overwritten, if ownership is unclear, or if plan output does not match the technical design.
7. Run cap4kGenerate only when allowed.
8. Stop for human review after generation.

## Findings To Report

- Plan evidence reviewed.
- Ownership classification by output family.
- Conflicts, template override risks, addon artifacts, or drift.
- Whether generation was run or must be requested.
- The human review gate now blocking handwritten implementation.
