# Runtime Capability Map

This map captures runtime boundaries that installed cap4k authoring skills may rely on without requiring an agent to load maintenance analysis pages.

## Repository

Repository capability is read/access/load oriented. A repository can restore aggregates by identity, IDs, or query predicates and may use aggregate load plans where supported. It is not the owner of commit semantics.

Agent rule: do not describe Repository as the save owner. Command paths load aggregates through Repository and persist intent through Unit of Work.

## Unit Of Work

Unit of Work owns aggregate persistence intent, delete intent, provider synchronization, audit enrichment, Domain Event stabilization, and the outer Command transaction boundary. The outer Command completes it automatically. Application code may use advanced `flush()` to synchronize current provider state, but `flush()` does not commit or drain Domain Events.

Agent rule: application handlers record persistence or delete intent through Unit of Work and return normally; never generate or require a completion-oriented `save()` call. Use `flush()` only for an explicit constraint/provider-value need, and never describe it as Command completion.

## Mediator

Mediator is a static framework namespace across independently configured Command, Query, external Capability, repository, aggregate factory, domain service, Unit of Work, Integration Event, IoC, and identifier providers. It has no aggregate runtime instance or all-capabilities implementation and is not a separate business engine.

Agent rule: use only canonical names (`commands`, `queries`, `capabilities`, `repositories`, `factories`, `services`, `uow`, `events`, `ioc`, `identifiers`). Keep business decisions in domain/application code. An uninstalled optional provider fails when called; do not assume a monolithic starter or silent fallback.

## Command And Event Reliability

Command dispatch owns the REQUIRED transaction and automatic Unit of Work. Reliable enqueue/schedule/result requires the Command JPA provider and registration inside an active Command transaction. Local Domain Event delivery is synchronous inside the Unit of Work; persisted or delayed events require the Domain Event JPA provider.

Agent rule: do not assign reliable APIs to Query or Capability. Do not model local asynchronous work as an ordinary Domain Event plus `@Async`; use a reliable Command. Reliable record registration joins the local transaction but must not call `saveAndFlush()` or otherwise force a provider-wide flush; only the outer Coordinator owns final provider synchronization. Record recovery is retry, not inferred business compensation. Event payload types come from explicit listener signatures or a supplied event catalog, never an event-scan package.

## Integration Event Transport Split

Integration Event runtime has two distinct responsibilities:

- Framework/runtime transport adapter consumes external HTTP/message input, parses/registers events, stores event records, and dispatches typed Integration Event payloads through configured adapters.
- Business application inbound subscriber receives the typed external fact, handles idempotency and semantic translation, then delegates to commands or application behavior for state change.

Agent rule: never assign external protocol consumption, parser registration, or transport dispatch to the business subscriber. Never push inbound payloads directly into aggregates.

## Orchestration Boundary

Cap4k does not ship a built-in Saga runtime, Saga persistence, Saga starter, or Saga generator family. Multi-step workflows use explicit application orchestration, reliable Commands, Integration Events, and external capabilities, or a separately selected orchestration provider owned outside cap4k core.

Agent rule: never promise or handwrite a cap4k Saga skeleton. If durable progress, compensation, or cross-transaction orchestration is required, return to technical design and select an explicit external/provider-owned solution.

## Analysis Evidence

Analysis outputs are observation evidence. They can help review flow, drawing-board, source contracts, and drift, but they are not source skeletons and do not replace plan evidence for generation.

Agent rule: analysis evidence may support static review claims only. It must not be treated as business source truth or as an installed skill runtime prerequisite.
