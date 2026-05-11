# Review Generated Output

1. Identify the source of each changed generated file: DB input, design input, addon, or template override.
2. Check whether the artifact should be active generated output, handwritten source, or copied snapshot.
3. Verify conflict policy for skeleton artifacts that users are expected to edit.
4. Verify template overrides are project-local and do not hide framework gaps.
5. Compare generated package names and file paths with the intended layered model.
6. Flag unused generated artifacts when they add no value to the example or project slice; remove them only when the current task explicitly allows file edits.
7. For read-only review, report the relevant generator plan/generate commands or inspect existing evidence. For implementation or verification tasks, run the relevant generator plan and generate tasks.
8. Report changed generated artifacts separately from handwritten logic.
