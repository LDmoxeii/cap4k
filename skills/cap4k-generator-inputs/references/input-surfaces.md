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

## Source Notes

- DB/schema carries aggregate, entity, relation, repository, factory, specification, enum binding, unique helper, and primary-key identity facts.
- `design/design.json` carries command, query, client, api payload, domain event, integration event, domain service, and saga contracts.
- Value-object manifests carry JSON-backed value-object source definitions.
- Enum manifests carry shared enum definitions referenced by schema type annotations.
- Gradle extension settings, addons/options, and template override decisions are authoring infrastructure; they are not business source truth.
