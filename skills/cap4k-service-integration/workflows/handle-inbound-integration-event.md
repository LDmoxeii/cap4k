# Handle Inbound Integration Event

Use this workflow when an external fact arrives through an Integration Event, callback, webhook, or message path and the business project must interpret it.

## Input Signals

- User says inbound Integration Event, external fact, callback, webhook, message, partner event, payment result, media-ready event, or provider status update.
- A received fact might advance internal state.
- The task is mixing transport listener details with business interpretation.

## Technical Design Fields To Update

- `businessIntent`: external fact source and why the project cares.
- `ubiquitousLanguage`: typed external fact name, Published Language fields, provider-term translations.
- `cleanArchitecturePlacement`: runtime transport outside business code, application subscriber for interpretation, Domain behavior behind Command/application delegation.
- `cap4kCarriers`: Integration Event type, inbound subscriber, Command/application behavior, optional Domain Event fan-out.
- `generatorInputPlan`: `design.json` `integration_event` entry or other supported input for event type and skeleton placement.
- `handwrittenLogicSlots`: idempotency, semantic translation, duplicate handling, command delegation, failure/retry policy.
- `verificationEvidence`: typed fact boundary, no transport mechanics in business subscriber, no protocol fields reaching Domain behavior.
- `rollbackTriggers`: unclear inbound/outbound direction, missing idempotency, or state change without Command/application delegation.

## Generator Input Implications

- Use cap4k generator support for Integration Event type and subscriber skeleton placement when available.
- Do not handwrite framework transport listeners, parser setup, registration hooks, dispatch loops, or message-consumer plumbing as a business project workaround.
- If generation cannot express the expected skeleton, return to generator inputs or record a technical design exception before creating structure.

## Handwritten Slots

- Application inbound subscriber interprets the typed external fact.
- Idempotency check protects duplicate delivery.
- Translation converts provider or partner terms into internal use-case language.
- State changes delegate to Command or explicit application behavior.
- Domain behavior receives only business facts and enforces invariants.

## Explicit Rejections

- The business subscriber does not create transport listeners, protocol registration, external envelope parsing, or runtime dispatch.
- Callback bodies, transport headers, and provider protocol fields do not enter Domain code.
- Message/callback delivery mechanics stay out of Domain and Application business logic; only typed fact meaning, idempotency, and delegation belong in the business path.

## Verification Evidence

- Technical design records source, typed fact, Published Language fields, idempotency key, command/application delegate, retry policy, and rollback target.
- Diff shows framework transport work is not recreated in the business project.
- Subscriber code receives a typed fact and delegates state changes through Command or application behavior.
- Domain APIs do not expose callback bodies, headers, provider status fields, or transport envelopes.

## Rollback Target

Return to tactical modeling if the fact might be a Domain Event, Saga, Query, or external capability response instead. Return to technical design/generator inputs when the Integration Event skeleton, idempotency strategy, or delegate use case is unclear.
