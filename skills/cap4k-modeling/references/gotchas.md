# Modeling Gotchas

- Do not let table shape define aggregate boundaries without business consistency reasoning.
- Do not create a domain service just because code feels procedural.
- Do not call every small type a generated value object. First-class `value_object` design generation is not supported.
- Do not pretend external callbacks are domain events.
- Do not define integration events without event name, role, and payload fields.
