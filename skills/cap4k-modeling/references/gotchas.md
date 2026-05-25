# Modeling Gotchas

- Do not let table shape define aggregate boundaries without business consistency reasoning.
- Do not create a domain service just because code feels procedural.
- Do not choose a value object persistence carrier before modeling its business semantics.
- Do not pretend external callbacks are domain events.
- Do not choose transport or generation details before classifying the service interaction boundary.
- Do not split a domain event just because it has multiple consumers. Split only when the completed business facts are different.
- Do not hide multi-process ownership in one central listener that branches into several private business reactions.
