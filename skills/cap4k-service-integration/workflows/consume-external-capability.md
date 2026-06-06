# Consume External Capability

Use this workflow when internal code consumes a capability owned by another service or provider.

## Input Signals

- User names payment, storage, media processing, moderation, notification, search, third-party API, or another external provider.
- A Command or application use case needs an outside side effect or outside status.
- Provider vocabulary is starting to appear in business or Domain names.

## Technical Design Fields To Update

- `businessIntent`: why the outside capability is needed.
- `ubiquitousLanguage`: internal capability name and result terms.
- `cleanArchitecturePlacement`: application-facing client contract, adapter client handler, Domain behavior receiving translated result.
- `cap4kCarriers`: external capability request/client, Command handler, optional Query/read model for status.
- `generatorInputPlan`: expected client/request/handler skeletons and any required design input or exception.
- `handwrittenLogicSlots`: provider mapping, retry/idempotency policy, command orchestration, Aggregate behavior call.
- `verificationEvidence`: provider terms contained to adapter-facing areas, use case enters Command before state change.
- `rollbackTriggers`: provider terms in Domain, client-first write split, or missing skeleton input.

## Generator Input Implications

- Use generator-supported client/request/handler skeletons where cap4k can express the external capability.
- Record any handwritten structural exception in technical design before creating new client or handler paths.
- Keep provider SDK DTOs and transport status fields out of generator inputs meant to represent business language.

## Handwritten Slots

- Application-facing client contract names the capability in business language.
- Adapter client handler maps provider protocol, credentials, status codes, and DTOs.
- Command handler invokes the capability when it is part of the write use case.
- Aggregate behavior receives translated results and records business state.
- UoW records persistence intent through framework capability after Domain behavior changes state.

## Verification Evidence

- Technical design shows the capability name, provider mapping boundary, retry/idempotency policy, and rollback target.
- Diff contains provider terms only in adapter-facing handlers, config, or infrastructure mapping.
- Write paths enter Command or application use case before external client invocation changes business state.
- No entry code performs client call first and command delegation later for the same write use case.

## Rollback Target

Return to tactical modeling when the outside dependency is not clearly a capability, or to technical design/generator inputs when the client skeleton, provider mapping boundary, or idempotency policy is unclear.
