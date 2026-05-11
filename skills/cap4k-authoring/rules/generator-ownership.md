# Generator Ownership

## Ownership Classes

- Build-owned generated source lives under module `build/generated/cap4k/main/kotlin` and is normally overwritten by generation.
- Checked-in skeletons are project author surfaces, usually generated once with `SKIP` conflict policy and then handwritten.
- Copied snapshots, such as `src-generated/main/kotlin`, are audit or learning snapshots, not active generator output.
- Handwritten source is owned by the business project and must not be overwritten by regeneration.
- Template overrides are project-owned customizations of generator behavior.

## Conflict Policy

- Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot` before generation.
- Use `OVERWRITE` only for build-owned generated source or intentionally refreshed generated artifacts.
- Use `SKIP` for handlers, factories, specifications, behaviors, and other skeletons that receive project logic.
- If generation conflicts with handwritten code, stop and classify the target before editing or regenerating.

## Input Sources

- DB source defines aggregate model, relations, enums, schema metadata, repositories, factories, specifications, and unique helpers.
- Design source defines command, query, client, api_payload, domain_event, and validator contracts.
- Enum manifest defines shared enums referenced by DB `@Type` / `@T`.
- KSP metadata supplies aggregate metadata for design-driven artifacts.
- IR analysis is post-code observation used by analysis output, not normal business source generation.

## Addons

- Addon artifacts behave the same as built-in artifacts for project users.
- Inspect addon `templateId` values in the plan before setting override paths or conflict policies.
- Project template override dirs can override addon templates with the same relative path.
- Enum translation is addon-owned, not a core aggregate DSL toggle.
