# Capability Index

## Bootstrap

- Use `cap4kBootstrapPlan` before `cap4kBootstrap`.
- Review slots, template override dirs, module layout, source roots, and expected runtime wiring before writing files.

## Plugin Configuration

- Project authoring commonly touches project/layout settings, sources, generators, templates, bootstrap, and `cap4kAddon`.
- Template override dirs are checked before addon and built-in resources.

## Inputs

- DB input models aggregates, relations, enum bindings, generated IDs, versions, soft delete, managed/exposed fields, and uniqueness.
- Design input models command, query, client, api_payload, domain_event, and validator contracts.
- Enum manifest supplies shared enums referenced by DB type annotations.
- KSP metadata supports design-driven generation from aggregate metadata.
- IR analysis feeds flow and drawing-board output after code compiles.

## Output Verification

- Plan before generate.
- Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
- Distinguish build-owned generated source, checked-in skeleton, copied snapshot, template override, and handwritten source.

## Tactical Model

- Domain owns model and behavior.
- Application owns request contracts, command handling, orchestration, subscribers, validators, and process intent.
- Adapter owns HTTP, persistence adapters, query handlers, client/cli handlers, controllers, and external bridges.
- Static `Mediator.*` usage is the normal project path.

## Integration Events

- Domain events are domain facts; integration events are cross-boundary messages.
- Inbound integration events should be translated to application commands or process steps.
- Cross-service contract sharing should focus on event name, schema, and serialization behavior.

## Testing And Analysis

- Prefer domain and application behavior tests first.
- Use adapter/integration smoke tests when the project claims runnable HTTP or external event behavior.
- Use `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` when analysis output is part of the task.

## Addons

- `cap4kAddon` dependencies can provide artifacts that behave like built-in artifacts.
- Addon templates can be overridden through normal template override dirs.
- Addon conflict policies use the exact `templateId` seen in the plan.
