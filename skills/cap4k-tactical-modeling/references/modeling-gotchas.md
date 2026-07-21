# Modeling Gotchas

Generic DDD concepts must map to cap4k carriers. Do not accept `aggregate`, `event`, `service`, `specification`, `workflow`, or `boundary` as sufficient unless the model also records generator input surface, expected skeleton or plan evidence, handwritten logic location, verification evidence, and rollback trigger.

## High-Risk Words

- Boundary: aggregate, entity, value object, strong ID, reference, lifecycle, invariant, consistency.
- Use case: command, query, handler, orchestration, read model, projection.
- Event: domain event, integration event, subscriber, saga, scheduled reaction, timeout, retry, compensation, recovery.
- Service boundary: external fact, callback, webhook, message, Published Language, Open Host Service, external capability.
- Policy: eligibility, approval, quota, uniqueness, duplicate, validation, pre-save.

## Common Failures

- Do not let table shape define aggregate boundaries without business consistency reasoning.
- Do not create a Domain Service just because code feels procedural.
- Do not choose a Value Object persistence carrier before modeling business semantics.
- Do not pretend external callbacks are Domain Events.
- Do not choose transport or generation details before classifying the service interaction boundary.
- Do not split a Domain Event just because it has multiple consumers. Split only when the completed business facts differ.
- Do not hide multi-process ownership in one central listener that branches into private business reactions.
- Do not model automatic event-to-request or event-to-release behavior. Outbound Integration Events are attached from explicit application orchestration or subscriber decisions.

## Carrier Mapping Check

For every risky concept, answer:

- Which cap4k carrier owns this signal?
- When should this carrier not be used?
- Which generator input surface can express it?
- What plan or skeleton evidence should appear?
- Where does handwritten business logic belong?
- What evidence can verify the decision?
- Which earlier phase receives rollback if the decision is wrong?
