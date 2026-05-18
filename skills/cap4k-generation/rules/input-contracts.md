# Input Contracts

- DB schema / DDL defines aggregate family generation: structure, relations, enum bindings, repositories, factories, specifications, and unique helpers.
- `design.json` defines command, query, client, api payload, domain event, integration event, and validator contracts; `domain_event` can derive domain-event subscriber/handler shells, and inbound `integration_event` can derive subscriber shells.
- Enum manifest supplies shared enums referenced by DB `@Type` / `@T`.
- `types.registryFile` supplies custom type FQNs and converter policy for `@T`-bound fields.
- KSP metadata supplies aggregate metadata for design-driven artifacts.
- IR analysis is post-compile observation for flow and drawing-board output, not business source generation.
- addons contribute extra artifacts, not business modeling facts.
- Unsupported design tags remain first-class `value_object` and `domain_service`.
- Missing facts route back to `cap4k-modeling`; existing facts with missing skeletons stay in `cap4k-generation`.
