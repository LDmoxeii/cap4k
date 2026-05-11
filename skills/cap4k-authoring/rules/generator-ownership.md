# Generator Ownership

## Ownership Classes

- Generated source is owned by the generator and participates in compile when configured that way.
- Handwritten source is owned by the project author.
- `src-generated/main/kotlin` is a copied snapshot for audit and learning, not the active generated output path.
- Template overrides are project-owned customizations of generator behavior.

## Conflict Policy

- Handler and factory skeletons should normally use `SKIP` conflict policy.
- Regenerating should not overwrite project-owned business logic.
- If a generated artifact has no business purpose in the current slice, disable or remove it instead of keeping noise.

## Input Sources

- DB input should use supported database annotations and naming contracts instead of hardcoded tactical metadata.
- Design input should drive commands, queries, cli, domain events, and future supported tactical contracts.
- Missing design support for a tactical concept must be recorded as a gap.

## Addon And SPI

- Addon-generated artifacts must behave like built-in artifacts from the project user's perspective.
- Project template overrides and conflict policies must work the same for built-in and addon artifacts.
