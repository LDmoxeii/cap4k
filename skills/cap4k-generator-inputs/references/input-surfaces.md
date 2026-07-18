# Input Surfaces

Supported generator input surfaces:

- DB/schema
- design/design.json
- value-object manifest
- enum manifest
- Gradle extension
- addons/options
- template override decisions

Use these surfaces as source inputs only when the technical design contract supports the carrier, placement, ownership, and expected skeleton.

## Contract References

- `sources.designJson.files`: see `design-json-contract.md`.
- DB/schema DDL comments: see `db-schema-annotations.md`.
- `types.enumManifest.files` and `types.valueObjectManifest.files`: see `manifest-contracts.md`.
- Gradle extension fields: use project Gradle files and approved technical design; this index is not a full Gradle DSL reference.

## Source Notes

- DB/schema carries aggregate, entity, relation, repository, factory, specification, enum binding, unique helper, and primary-key identity facts.
- `design/design.json` carries command, query, client, api payload, domain event, integration event, domain service, and saga contracts.
- Value-object manifests carry JSON-backed value-object source definitions.
- Enum manifests carry shared enum definitions referenced by schema type annotations.
- Gradle extension settings, addons/options, and template override decisions are authoring infrastructure; they are not business source truth.

## Identity And Reference Contracts

- Aggregate-root primary-key metadata defaults to generated Strong ID types.
- Same-context aggregate references use `@RefAggregate=<AggregateName>` and resolve to the referenced aggregate ID type.
- Local external-reference identities use `@RefId=<TypeName>` when local language maps an upstream or external concept.
- `@GeneratedValue=identity` and `@GeneratedValue=database-identity` express explicit database identity semantics, not the default aggregate ID path.
- If identity, reference, or generated-value facts are missing or unclear, return to `cap4k-technical-design` before plan review.

## Analysis Evidence Boundary

- Analysis outputs, including flow and drawing-board outputs, are observation evidence by default.
- Analysis outputs are not ordinary source-generation input skeletons.
- Manually copied drawing-board content must satisfy the supported input contract before it is registered through a generator input surface.
