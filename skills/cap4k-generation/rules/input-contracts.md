# Input Contracts

- DB schema / DDL defines aggregate family generation: structure, relations, enum bindings, repositories, factories, specifications, and unique helpers.
- Database primary-key metadata is the input contract for aggregate/entity IDs; aggregate-root primary keys generate Strong ID types by default.
- `@RefAggregate=<AggregateName>` is the input contract for same-context aggregate references and resolves to the referenced aggregate ID type.
- `@RefId=<TypeName>` is the input contract for current-context reference identities that map external concepts into local language.
- `@GeneratedValue=identity` / `@GeneratedValue=database-identity` are legacy compatibility for explicit database identity semantics, not the default aggregate ID path.
- `design.json` defines command, query, client, api payload, domain event, and integration event contracts; `domain_event` can derive domain-event subscriber/handler shells, and inbound `integration_event` can derive subscriber shells.
- Enum manifest supplies shared enums referenced by DB `@Type` / `@T`.
- `types.registryFile` supplies custom type FQNs and converter policy for `@T`-bound fields.
- KSP metadata supplies aggregate metadata for design-driven artifacts.
- IR analysis is post-compile observation for flow and drawing-board output, not business source generation.
- addons contribute extra artifacts, not business modeling facts.
- Unsupported design tags remain first-class `value_object` and `domain_service`.
- Missing facts route back to `cap4k-modeling`; existing facts with missing skeletons stay in `cap4k-generation`.
