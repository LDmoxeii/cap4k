# Plan Review Gotchas

- `src/main/kotlin` does not automatically mean handwritten; review plan ownership first.
- Plan output must be reviewed before judging generated files.
- Check `generatorId`, `templateId`, `outputKind`, `conflictPolicy`, `outputPath`, and `resolvedOutputRoot` together.
- Addon artifacts need the same ownership review as built-in artifacts.
- Addon template IDs must stay provider-namespaced under `addons/<providerId>/...`.
- Provider-scoped options must not imply source or canonical SPI ownership.
- Validator-like artifacts are addon-owned unless they are aggregate unique-helper outputs.
- Enum and value-object manifest entries live under `types {}` and should not be duplicated in custom type registry entries.
- Strong ID drift often appears as primitive aggregate IDs, same-context references that do not resolve to target ID types, local reference language replaced by upstream language, or aggregate identity assigned in persistence paths.
- Value-object inputs live in `types.valueObjectManifest`; check manifest entries and source conventions.
- `integration_event` without payload fields is invalid.
- Template overrides can change future generated skeleton shape and must be reviewed with the same ownership rules as normal outputs.
- Public docs and AI skills can intentionally differ in audience, but they must agree on code facts.
