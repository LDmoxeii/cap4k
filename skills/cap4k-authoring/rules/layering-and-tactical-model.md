# Layering And Tactical Model

## Layer Responsibilities

- Domain owns the model and behavior: aggregates, entities, value concepts, invariants, domain events, domain services, factories, and specifications.
- Value concepts may be primitive wrappers, composite values, JSON-backed fields, inline fields, or table-backed `@VO`; classify the domain concept before choosing the persistence carrier.
- Application owns request contracts, write-side command handling, orchestration, subscribers, validators, jobs/process intent, and use-case flow.
- Adapter owns HTTP, persistence adapters, query handlers, client/cli handlers, controllers, external bridges, and transport-facing mapping.
- Query handlers and client/cli handlers are adapter-side physical handlers by default, even when they implement application request contracts.

## Command Handling

- Command handlers are the normal write-use-case boundary.
- They may load aggregates through repositories, create aggregate roots through factories, call domain services, apply domain behavior, and persist through UoW.
- They may call client/cli requests only when the command result depends on the external capability result.
- They should avoid using queries for normal write decisions when repository access is the clearer tactical contract.

## Query And Client/CLI Handling

- Query handlers wrap read-side repository, JPA, or query infrastructure and are good boundaries for controllers, jobs, and orchestration needing read information.
- Client/cli handlers wrap external systems or simulated external capabilities.
- Jobs and controllers should not become direct business persistence surfaces; route writes through commands and reads through queries when that boundary is clearer.

## Orchestration

- Process orchestration belongs in application-facing flow code: command handlers for focused write cases, domain/integration subscribers for follow-up work, jobs for scheduled work, or explicit process services when needed.
- Orchestration code may use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` to compose steps.
- Subscriber method names should be semantic once they contain business logic; a generated placeholder is acceptable only before implementation.
