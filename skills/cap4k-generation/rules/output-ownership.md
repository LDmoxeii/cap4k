# Output Ownership

- Build-owned generated source lives under module `build/generated/cap4k/main/kotlin` and can be overwritten.
- Checked-in skeletons are project author surfaces only when their conflict policy protects handwritten logic.
- Copied snapshots such as `src-generated/main/kotlin` are audit or learning snapshots, not active generator output.
- Handwritten source must not be overwritten by regeneration.
- Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot` before writing files.
- Use `SKIP` for handlers, behavior, factory, specification, and subscriber skeletons that receive project logic.
- Use `OVERWRITE` only for build-owned generated source or intentional regenerated artifacts.
