# Source Map

| Contract | Config location | Responsible for | Not responsible for | Missing contract routes to |
|---|---|---|---|---|
| DB schema / DDL | `sources.db` | aggregate model, entity shape, relation and field-mapping inputs, enum bindings, repositories, factory/specification/unique helpers | command/query/client/payload/event contracts, standalone relation/field-mapping output families | `cap4k-modeling` |
| `design.json` | `sources.designJson` | `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, domain-event subscriber/handler shells, inbound integration-event subscriber shells | aggregate structure, DB carrier decisions, validator surfaces | `cap4k-modeling` |
| enum manifest | `sources.enumManifest` | shared enum definitions referenced by DB `@T` | aggregate behavior, enum translation addon artifacts | `cap4k-modeling` |
| `types.registryFile` | `types {}` | custom type FQNs and converter policy for `@T`-bound fields | command/query/event contracts | `cap4k-modeling` |
| KSP metadata | `sources.kspMetadata` | aggregate metadata that design-driven generation needs | main business flow analysis | generation/setup input problem; stay in `cap4k-generation` |
| IR analysis | `sources.irAnalysis` | flow and drawing-board observation after compile | normal business-source generation | not part of business-source generation |
| addon artifacts | `cap4kAddon` + `templates` | extra plan items and template-rendered artifacts | business modeling facts | not a modeling source |

`domain_event` is allowed to generate subscriber/handler shells, and inbound `integration_event` is allowed to generate subscriber shells.
If the fact exists but the skeleton is missing, stay in `cap4k-generation`.
If the missing expectation is a standalone relation or field-mapping plan item, the expectation is wrong; those facts belong to aggregate/entity generation input.
If the fact itself is missing, return to `cap4k-modeling`.
