# Review Plan And Generate

1. Read technical design and generator inputs.
2. Read `../references/plan-review-gotchas.md` before reviewing plan or output.
3. Run cap4kPlan only when the user instruction and environment policy permit runtime/generation commands.
4. If cap4kPlan is not permitted, request permission or report the exact command to run instead of executing it.
5. Review `plan.json` or `bootstrap-plan.json`.
6. Classify output ownership as checked-in skeleton, build-owned generated source, generated snapshot or evidence, template override, or handwritten logic.
7. Check `conflictPolicy`, `templateId`, module placement, `outputKind`, `generatorId`, `outputPath`, and `resolvedOutputRoot`.
8. Stop if handwritten logic would be overwritten, if ownership is unclear, or if plan output does not match the technical design.
9. Run cap4kGenerate only when the user instruction and environment policy permit runtime/generation commands and plan review gate allows it.
10. If cap4kGenerate is not permitted, request permission or report the exact command to run instead of executing it.
11. After cap4kGenerate, stop for human review and do not continue to handwritten implementation.

## Findings To Report

- Plan evidence reviewed.
- Ownership classification by output family.
- Conflicts, template override risks, addon artifacts, or drift.
- Whether generation was run, permission was requested, or an exact command was reported.
- The human review gate now blocking handwritten implementation.
