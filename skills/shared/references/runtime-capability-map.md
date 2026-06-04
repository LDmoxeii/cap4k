# Runtime Capability Map

This map captures runtime boundaries that installed cap4k authoring skills may rely on without requiring an agent to load maintenance analysis pages.

## Repository

Repository capability is read/access/load oriented. A repository can restore aggregates by identity, IDs, or specification and may use aggregate load plans where supported. It is not the owner of commit semantics.

Agent rule: do not describe Repository as the save owner. Command paths load aggregates through Repository and persist intent through Unit of Work.

## Unit Of Work

Unit of Work owns persistence intent, delete intent, commit/save, transaction propagation, and lifecycle interception. It collects entities to persist or remove and coordinates save behavior with runtime interceptors.

Agent rule: application handlers record persistence or delete intent through Unit of Work and call save/commit behavior according to the framework contract. Do not ask business projects to implement Unit of Work.

## Mediator

Mediator is a framework facade and delegation entrypoint across runtime supervisors such as repository, aggregate factory, domain service, Unit of Work, integration event, and request capabilities. It is not a separate business engine.

Agent rule: use Mediator as framework-facing convenience when appropriate, but keep business decisions in domain/application code.

## Specification

Specification and unique helper behavior can participate in pre-save checks through Unit of Work interception. This makes pre-save constraints a domain/runtime boundary, not a controller-only validation rule.

Agent rule: when a pre-save constraint is needed, prefer generator-supported specification/unique helper surfaces or record a technical design exception before handwritten structure.

## Integration Event Transport Split

Integration Event runtime has two distinct responsibilities:

- Framework/runtime transport adapter consumes external HTTP/message input, parses/registers events, stores event records, and dispatches typed Integration Event payloads through configured adapters.
- Business application inbound subscriber receives the typed external fact, handles idempotency and semantic translation, then delegates to commands or application behavior for state change.

Agent rule: never assign external protocol consumption, parser registration, or transport dispatch to the business subscriber. Never push inbound payloads directly into aggregates.

## Saga Runtime Scope

Saga runtime supports request-oriented process coordination, subprocesses, compensable subprocesses, explicit compensation requests, retry, archival, and scheduled compensation. This supports compensation-oriented Saga modeling.

Agent rule: use Saga when persistent progress, retry, recovery, or compensation is required. Do not describe Saga as a generic callback-resume workflow engine without current code evidence.

## Analysis Evidence

Analysis outputs are observation evidence. They can help review flow, drawing-board, source contracts, and drift, but they are not source skeletons and do not replace plan evidence for generation.

Agent rule: analysis evidence may support static review claims only. It must not be treated as business source truth or as an installed skill runtime prerequisite.