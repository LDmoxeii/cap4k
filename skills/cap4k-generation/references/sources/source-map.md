# Source Map

| Contract | Config location | Responsible for | Not responsible for | Missing contract routes to |
|---|---|---|---|---|
| DB schema / DDL | `sources.db` | aggregate model, entity shape, relation and field-mapping inputs, enum bindings, repositories, factory/specification/unique helpers | command/query/client/payload/event contracts, standalone relation/field-mapping output families | `cap4k-technical-design` |
| `design.json` | `sources.designJson` | `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, `saga`, domain-event subscriber/handler shells, inbound integration-event subscriber shells | aggregate structure, DB carrier decisions, validator surfaces | `cap4k-technical-design` |
| enum manifest | `types.enumManifest` | shared enum definitions referenced by DB `@T` | aggregate behavior, enum translation addon artifacts, type-registry duplication | `cap4k-technical-design` |
| value-object manifest | `types.valueObjectManifest` | JSON-backed value-object source, nested converter, shared or aggregate-local ownership via `aggregates` | command/query/event contracts, table-backed `@VO` modeling | `cap4k-technical-design` |
| `types.registryFile` | `types {}` | custom type FQNs and converter policy for `@T`-bound fields not declared by manifests | command/query/event contracts, enum/value-object manifest entries | `cap4k-technical-design` |
| IR analysis | `sources.irAnalysis` | flow and drawing-board observation after compile | normal business-source generation | not part of business-source generation |
| addon artifacts | `cap4kAddon` + `templates` | extra plan items and template-rendered artifacts | business modeling facts | not a modeling source |

`domain_event` is allowed to generate subscriber/handler shells, inbound `integration_event` is allowed to generate subscriber shells, `domain_service` is allowed to generate domain service skeletons, and `saga` is allowed to generate saga skeletons.
If the fact exists but the skeleton is missing, stay in `cap4k-generation`.
If the missing expectation is a standalone relation or field-mapping plan item, the expectation is wrong; those facts belong to aggregate/entity generation input.
If the fact itself is missing, return to `cap4k-tactical-modeling or cap4k-technical-design`.
