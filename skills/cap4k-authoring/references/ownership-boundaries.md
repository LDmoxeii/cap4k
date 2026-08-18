# Ownership Boundaries

- Generator inputs are author-owned sources. Plan, diagnostics, analysis and visual outputs are evidence, not business source truth.
- `CHECKED_IN_SOURCE` is first-materialized project source. Existing handwritten logic is protected by `SKIP`; regeneration is an explicit delete/rematerialize/version-control review operation. Manifest-authored Business Enums follow this contract under domain `src/main/kotlin`: authors may extend them with domain logic, and later manifest changes do not overwrite the checked-in file.
- `GENERATED_SOURCE` is build-owned and replaceable. Manifest-authored Business Enum artifacts are not in this category. `cap4kGenerateSources` may read the enum manifest as canonical type input for other generated artifacts, but it must not materialize or overwrite the checked-in enum class. Never place durable handwritten logic there. Endpoint RPC Provider registrations and the feature-scoped `endpoint-client` remote Handlers/auto-configuration are generated outputs; `endpoint-client` is reusable Consumer outbound-adapter packaging, not a fifth default DDD layer or a home for checked-in business code.
- Review generator identity, template identity, output kind, resolved path/root and conflict policy together before mutation.
- If supported inputs can express missing structure, update inputs and plan again. Do not create a parallel skeleton to make compilation pass.
- Handwritten logic belongs in checked-in author-owned surfaces or an explicit structural exception. Preserve ordinary handwritten slots and managed-field handler slots.
