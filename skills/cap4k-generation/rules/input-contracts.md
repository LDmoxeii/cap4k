# Input Contracts

- DB source defines aggregate model, relations, enum bindings, generated IDs, soft delete, versions, managed/exposed fields, provider controls, repositories, factories, specifications, and unique helpers.
- Design JSON defines command, query, client, api_payload, domain_event, integration_event, and validator contracts.
- `integration_event` requires `role`, `eventName`, at least one request field, and empty response fields.
- Enum manifest supplies shared enums referenced by DB `@Type` / `@T`.
- KSP metadata supplies aggregate metadata for design-driven artifacts.
- IR analysis is post-code observation for flow and drawing-board output, not normal business source generation.
- Unsupported design tags are first-class `value_object` and `domain_service`.
