# Output Ownership

- Build-owned generated source lives under module `build/generated/cap4k/main/kotlin` and can be overwritten.
- Checked-in skeletons are project author surfaces only when their conflict policy protects handwritten logic.
- Copied/generated source snapshots are audit or learning snapshots, not active generator output.
- Handwritten source must not be overwritten by regeneration.
- Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot` before writing files.
- Use `SKIP` for handlers, behavior, factory, specification, and subscriber skeletons that receive project logic.
- Use `OVERWRITE` only for build-owned generated source or intentional regenerated artifacts.

### Generated-Capable Surfaces

- Before adding event, subscriber, command, query, client, or API payload surfaces, decide whether `design.json` can generate that surface.
- If the generator supports the surface, update `design.json` first and regenerate.
- Do not quietly handwrite generator-supported surfaces.
- If a surface cannot be generated, state the reason in review notes or final notes.
- Do not delete generated subscriber shells simply because they are empty. Implement business logic inside the generated boundary when the behavior is ready.

### Event Generation Boundary

- Templates and addons must not generate automatic event-to-request or event-to-release routing annotations.
- Templates and addons must not generate outbound event payload delivery calls that bypass `Mediator.events.attach(...)`.
- Generated outbound integration event support stops at contracts and entry skeletons; business code still attaches outbound facts from application orchestration points.
- Generated inbound integration event skeletons receive external facts and route state advancement into internal commands.
- `integration_event` design input is a contract and skeleton signal, not permission to generate transport/runtime publication logic in business code.
