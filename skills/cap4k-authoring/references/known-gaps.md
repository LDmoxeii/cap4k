# Known Gaps

These are cap4k product or authoring gaps that business-project AI authors must keep visible.

## Design Source Gaps

- No design support for `integration_event`; external integration event contracts cannot yet be generated from design.
- No design support for first-class `value_object`; general value objects remain handwritten domain types today. DB can express table-backed `@VO` and `@T` custom type bindings, but that is not full value-object generation.
- No design support for `domain_service`; domain services remain handwritten and should not be invented as generated support.

## Runtime And Generator Gaps

- Lifecycle recognition has known limitations; teach intended `onCreate`, `onUpdate`, and `onDelete` usage without claiming every subclass or child-entity case works.
- Enum translation is addon-owned and not a core aggregate DSL toggle.
- Integration event HTTP-JPA requires the subscriber registry table when that adapter is used; local H2 examples need compatible table DDL.

## Addon Boundary

- Business-project users add addon dependencies, inspect plans, override addon templates, and set conflict policies.
- Addon authoring guidance is separate from this skill.
