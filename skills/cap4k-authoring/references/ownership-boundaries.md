# Ownership Boundaries

- Generator inputs are author-owned sources. Plan, diagnostics, analysis and visual outputs are evidence, not business source truth.
- `CHECKED_IN_SOURCE` is first-materialized project source. Existing handwritten logic is protected by `SKIP`; regeneration is an explicit delete/rematerialize/version-control review operation.
- `GENERATED_SOURCE` is build-owned and replaceable. Never place durable handwritten logic there.
- Review generator identity, template identity, output kind, resolved path/root and conflict policy together before mutation.
- If supported inputs can express missing structure, update inputs and plan again. Do not create a parallel skeleton to make compilation pass.
- Handwritten logic belongs in checked-in author-owned surfaces or an explicit structural exception. Preserve ordinary handwritten slots and managed-field handler slots.
