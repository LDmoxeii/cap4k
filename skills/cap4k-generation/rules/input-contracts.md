# Input Contracts

- DB schema / DDL defines aggregate family generation: structure, relations, enum bindings, repositories, factories, specifications, and unique helpers.
- Database primary-key metadata is the input contract for aggregate/entity IDs; aggregate-root primary keys generate Strong ID types by default.
- `@RefAggregate=<AggregateName>` is the input contract for same-context aggregate references and resolves to the referenced aggregate ID type.
- `@RefId=<TypeName>` is the input contract for current-context reference identities that map external concepts into local language.
- `@GeneratedValue=identity` / `@GeneratedValue=database-identity` are legacy compatibility for explicit database identity semantics, not the default aggregate ID path.
- `design.json` defines `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, and `saga` contracts; `domain_event` can derive domain-event subscriber/handler shells, and inbound `integration_event` can derive subscriber shells.
- `types.enumManifest` supplies shared enums referenced by DB `@Type` / `@T`; enum manifest entries do not need matching `types.registryFile` entries.
- `types.valueObjectManifest` supplies JSON-backed value-object definitions and generates checked-in source; `scope = shared | aggregate` separates shared and aggregate-local definitions.
- `types.registryFile` supplies custom type FQNs and converter policy for `@T`-bound fields that are not declared by enum or value-object manifests.
- IR analysis is post-compile observation for flow and drawing-board output, not business source generation.
- addons contribute extra artifacts, not business modeling facts; addon-provided validators must not mutate canonical model or built-in render context.
- Unsupported core design tags include `value_object` and `validator`.
- Missing facts route back to `cap4k-modeling`; existing facts with missing skeletons stay in `cap4k-generation`.
