# Review Gotchas

- `src/main/kotlin` does not automatically mean handwritten.
- Plan output must be reviewed before judging generated files.
- Addon artifacts need the same ownership review as built-in artifacts.
- Addon template IDs must stay provider-namespaced under `addons/<providerId>/...`; provider-scoped options must not imply source/canonical SPI.
- Validator artifacts are addon-owned unless they are aggregate unique helper outputs; `validator` is not a core design tag.
- Enum and value-object manifest entries live under `types {}` and should not be duplicated in `types.registryFile`.
- Strong ID 1.0 drift often appears as primitive aggregate IDs, same-context references that do not resolve to target ID types, local `AuthorId` language replaced by upstream `UserId`, or aggregate IDs assigned in save-time paths.
- Public docs and AI skills can intentionally differ in audience, but must agree on facts.
