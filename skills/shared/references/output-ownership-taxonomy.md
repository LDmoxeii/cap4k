# Output Ownership Taxonomy

Use this taxonomy when reviewing plan evidence, generated diffs, template overrides, or handwritten implementation surfaces.

## Checked-In Skeleton

Checked-in skeletons are first-materialized source files intended to live in the repository, commonly under a module `src/main/kotlin` root. They may contain handwritten slots and initial generated wiring shape, but the generator does not retain a managed-section refresh contract.

Plan signals:

- `outputKind` is `CHECKED_IN_SOURCE`.
- `resolvedOutputRoot` points to a checked-in module source root.
- `conflictPolicy` is always `SKIP` to protect existing handwritten logic.

Review rule: after first materialization, maintain the file as checked-in project source. If regeneration is intentional, use version control to delete, rematerialize, and review the complete diff.

## Build-Owned Generated Source

Build-owned generated source is recreated by generation and belongs under a build-generated source root. It is not a durable handwritten implementation area.

Plan signals:

- `outputKind` is `GENERATED_SOURCE`.
- `resolvedOutputRoot` points under a build-generated cap4k source root.
- `conflictPolicy` commonly uses `OVERWRITE`.

Review rule: never place long-term business logic in build-owned generated source. Change the generator input, template, or addon instead.

## Generated Snapshot Or Evidence

Generated snapshots and evidence include plan files, bootstrap plans, analysis plans, flow output, drawing-board output, and other generated artifacts used for review. They explain what generation observed or planned; they are not business source truth.

Plan signals:

- `outputKind` may be `OUTPUT_ARTIFACT`.
- The path is under a build artifact root.
- The artifact records planning, bootstrap, analysis, visualization, or review evidence.

Review rule: use evidence to guide rollback and ownership decisions, but do not implement business behavior in evidence outputs.

## Template Override

A template override changes the generated shape for a family. It is source-controlled authoring infrastructure, not ordinary business logic.

Plan signals:

- `templateId` maps to an overridden template or custom template source.
- The same generator family and output kind still determine ownership.
- Template override shape can change future first-materialized files, but cannot change existing checked-in source away from `SKIP`.

Review rule: template overrides require generation review because they can alter all future skeletons in that family.

## Handwritten Logic

Handwritten logic is business behavior, orchestration, translation, idempotency, compensation, policy, and tests written inside approved surfaces. It should live inside generated skeleton slots or explicitly documented structural exceptions.

Typical locations:

- Aggregate behavior, value object validation, factory policy, and domain service logic.
- Command/query handlers, subscribers, saga processes, scheduled reactions, and external capability orchestration.
- Adapter protocol mapping, controller request conversion, client-handler translation, and persistence mapping.

Review rule: handwritten logic must preserve generated-vs-handwritten ownership and must not create parallel skeleton families when generator inputs can express the structure.

## Conflict Policy Review Rule

Read `generatorId`, `templateId`, `outputKind`, `resolvedOutputRoot`, `outputPath`, `context`, and `conflictPolicy` together.

| `conflictPolicy` | Typical Meaning | Required Review |
|---|---|---|
| `SKIP` | Existing checked-in file is protected; this is mandatory for `CHECKED_IN_SOURCE`. | Confirm first-materialization-only ownership and accept that template updates are not synchronized. |
| `OVERWRITE` | Build-owned or explicitly regenerated output can be replaced. | Confirm no handwritten logic lives in that output root. |
| `FAIL` | Existing file should block materialization. | Stop and resolve ownership or bootstrap conflict before generation proceeds. |

If policy and ownership disagree, stop implementation and return to generation review or technical design.
