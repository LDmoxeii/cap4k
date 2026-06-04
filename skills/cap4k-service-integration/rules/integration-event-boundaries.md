# Integration Event Boundaries

Integration Events carry confirmed facts across service or bounded-context boundaries through Published Language.

## Runtime And Business Split

- cap4k framework/runtime transport handles HTTP and message intake, parsing, registration, and dispatch.
- Business application inbound subscribers receive typed external facts.
- Business subscribers interpret the fact, handle idempotency, translate semantics, and delegate to Command or application behavior when state must change.
- Domain code receives typed business facts only; never callback bodies, transport headers, provider status fields, or protocol envelopes.
- Framework Unit of Work and Mediator capabilities are used by business projects; project code must not own those framework mechanisms.
- Do not require users to handwrite framework transport intake, parser registration, runtime dispatch, or message-consumer plumbing for Integration Events.

## Inbound Event Rules

- Model inbound Integration Events as external facts, not internal Domain Events.
- Define the typed external fact and its Published Language before choosing handlers.
- Put idempotency and semantic translation in the application inbound subscriber.
- Delegate state changes through Command or explicit application behavior.
- Keep Aggregate invariants in Domain behavior reached through the use case; do not pass protocol fields into Aggregate methods.

## Outbound Event Rules

- Outbound Integration Events publish confirmed internal facts outward in Published Language.
- Domain Events may trigger outbound Integration Event publication, but they are not the same contract.
- Do not expose internal Aggregate shape, persistence structure, or non-public technical IDs as the Integration Event contract.
- Coordinate outbound event emission at an application orchestration point supported by cap4k runtime capabilities; do not make Aggregate roots own cross-service delivery mechanics.

## Review Questions

- Is this fact inbound or outbound?
- Which Published Language terms are stable for external readers?
- Which part is framework/runtime transport, and which part is business interpretation?
- What idempotency key and duplicate-handling behavior protect the inbound path?
- Which Command or application behavior owns any state change?
- What technical design field records versioning, compatibility, retry, and rollback decisions?
