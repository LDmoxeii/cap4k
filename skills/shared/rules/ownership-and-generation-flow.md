# Ownership And Generation Flow

- Source generation follows the cap4k planned-source contract: `cap4kPlan` establishes `plan.json` ownership before `cap4kGenerate` materializes source.
- Generator-capable skeletons belong to generation inputs and planned output, not ad hoc implementation files.
- Design, DDL, `types.enumManifest`, `types.valueObjectManifest`, and `types.registryFile` are generation input contracts.
- `src/main/kotlin` does not automatically mean handwritten ownership.
- Copied generated snapshots are evidence only, not active authoring surfaces.
- Generated or checked-in skeleton editability depends on `generatorId`, `templateId`, `outputKind`, `resolvedOutputRoot`, and `conflictPolicy`.
