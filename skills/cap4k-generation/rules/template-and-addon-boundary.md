# Template And Addon Boundary

- Template override dirs are checked before addon and built-in resources.
- Addon artifacts behave like built-in artifacts for plan, render, conflict policy, and generation.
- Addon template conflict policies use the exact `templateId` from `cap4kPlan`.
- Runtime dependencies and generation-time `cap4kAddon` dependencies are separate.
- Enum translation is addon-owned, not a core aggregate DSL toggle.
- Do not override templates before proving the default output is the wrong abstraction.
