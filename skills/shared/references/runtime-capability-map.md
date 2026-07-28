# Runtime Capability Map

This map captures runtime boundaries that installed cap4k authoring skills may rely on without requiring an agent to load maintenance analysis pages.

## Repository

Repository capability is read/access/load oriented. A repository can restore aggregates by identity, IDs, or query predicates and may use aggregate load plans where supported. It is not the owner of commit semantics.

Agent rule: do not describe Repository as the save owner. Command paths load aggregates through Repository and persist intent through Unit of Work.

## Unit Of Work

Unit of Work owns persistence intent, delete intent, commit/save, transaction propagation, and lifecycle interception. It collects entities to persist or remove and coordinates save behavior with runtime interceptors.

Agent rule: application handlers record persistence or delete intent through Unit of Work and call commit behavior according to the framework contract. Do not assign Unit of Work mechanics to business project code.

## Mediator

Mediator is a static framework namespace across independently configured repository, aggregate factory, domain service, Unit of Work, integration event, request, IoC, and identifier capabilities. It has no aggregate runtime instance or all-capabilities implementation and is not a separate business engine.

Agent rule: use only canonical names (`commands`, `queries`, `requests`, `repositories`, `factories`, `services`, `uow`, `events`, `ioc`, `identifiers`). Keep business decisions in domain/application code. An uninstalled optional capability fails when called; do not assume a monolithic starter or silent fallback.

## Request And Event Reliability

Core Request dispatch is synchronous and does not create persistence records. Reliable schedule/result requires the Request JPA capability. Local Domain Event delivery is synchronous through Spring inside the Unit of Work transaction; persisted or delayed events require the Domain Event JPA capability.

Agent rule: do not describe reliable Request/Event calls as Core behavior and do not downgrade them when their provider is absent. Event payload types come from explicit Spring listener signatures or a supplied event catalog, never an event-scan package.

## Integration Event Transport Split

Integration Event runtime has two distinct responsibilities:

- Framework/runtime transport adapter consumes external HTTP/message input, parses/registers events, stores event records, and dispatches typed Integration Event payloads through configured adapters.
- Business application inbound subscriber receives the typed external fact, handles idempotency and semantic translation, then delegates to commands or application behavior for state change.

Agent rule: never assign external protocol consumption, parser registration, or transport dispatch to the business subscriber. Never push inbound payloads directly into aggregates.

## Saga Runtime Scope

Saga runtime supports request-oriented process coordination, subprocesses, compensable subprocesses, explicit compensation requests, retry, archival, and scheduled compensation. This supports compensation-oriented Saga modeling.

Agent rule: use Saga when persistent progress, retry, recovery, or compensation is required. Do not describe Saga as a generic callback-resume workflow engine unless this installed skill bundle has been updated from verified code facts.

## Analysis Evidence

Analysis outputs are observation evidence. They can help review flow, drawing-board, source contracts, and drift, but they are not source skeletons and do not replace plan evidence for generation.

Agent rule: analysis evidence may support static review claims only. It must not be treated as business source truth or as an installed skill runtime prerequisite.
