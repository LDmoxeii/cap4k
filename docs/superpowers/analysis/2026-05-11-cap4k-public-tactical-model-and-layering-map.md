# cap4k Public Tactical Model And Layering Map

Date: 2026-05-11

This file maps the tactical runtime concepts that business-project authors need in order to use generated skeletons correctly.

## Module Responsibilities

| Module | Responsibility | Typical contents |
|---|---|---|
| domain | Business model and domain decisions | aggregates, entities, value objects, domain events, domain services, behavior, factories, specifications |
| application | Use-case boundary and process orchestration | commands, command handlers, query/client request definitions, validators, subscribers, jobs, flow orchestration |
| adapter | Technical adapters and transport-facing handlers | HTTP controllers, API payloads, JPA repositories, query handlers, client/cli handlers |
| start | Runtime assembly | Spring Boot application, runtime configuration, local DB/script wiring |

Responsibility and physical package are related but not identical. Query handlers and client/cli handlers are generated into adapter packages by default because they often touch adapter concerns, but they still implement application request contracts.

## Mediator Surface

`Mediator` is the primary static access point:

| Surface | Alias | Use |
|---|---|---|
| `Mediator.factories` | `Mediator.fac` | Create aggregate roots from factory payloads |
| `Mediator.repositories` | `Mediator.repo` | Load aggregate roots through built-in repository adapters |
| `Mediator.aggregates` | `Mediator.agg` | Aggregate supervisor operations |
| `Mediator.services` | `Mediator.svc` | Resolve domain services |
| `Mediator.uow` | none | Persist/remove/save unit-of-work intentions |
| `Mediator.events` | none | Attach/publish integration events |
| `Mediator.requests` | `Mediator.req` | Send generic request params |
| `Mediator.commands` | `Mediator.cmd` | Send command requests |
| `Mediator.queries` | `Mediator.qry` | Send query requests |

Business code should use the static `Mediator.*` surface. It should not inject a mediator object just to call these supervisors.

## Command Handler Contract

Command handlers are the normal write-use-case boundary.

Allowed in command handlers:

- load aggregates through `Mediator.repositories`;
- create aggregate roots through `Mediator.factories`;
- invoke domain services through `Mediator.services`;
- change aggregate state through domain behavior methods;
- call `Mediator.uow.save()` to persist command changes;
- call client/cli requests only when the command result depends on the external capability result.

Avoid in command handlers:

- using queries for normal write decisions when repository access is the clearer tactical contract;
- hiding broad process orchestration inside a single command when a subscriber/job/process step is the natural flow boundary;
- annotating transactions manually when the UoW should own transaction execution.

## Query And Client/CLI Handlers

Query handlers:

- live in adapter packages by default;
- can wrap read-side repository/JPA access;
- are appropriate for controllers, jobs, and orchestration code that need read information without taking a write dependency.

Client/cli handlers:

- live in adapter packages by default;
- wrap external systems or local simulated external capabilities;
- are sent through the same request supervisor surface.

The current runtime has `RequestParam` and `RequestHandler`; command/query/client/cli distinctions are generated/design conventions over that request mechanism.

## Process Orchestration

Process orchestration belongs in application-facing flow code, such as:

- domain event subscribers under `application.subscribers.domain`;
- integration event subscribers under `application.subscribers.integration`;
- jobs;
- explicit process/application service classes when needed.

Orchestration code can use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` to compose steps. It should not directly use repositories when a query boundary is clearer, especially for job/read polling code.

## Factories

`AggregateFactory<Payload, Entity>` creates aggregate roots. The default factory supervisor resolves a factory by payload type, calls `create(payload)`, and immediately enlists the new entity in `JpaUnitOfWork.persist`.

Authoring implications:

- only aggregate roots should have factories;
- factory skeletons are important author-maintained code;
- creation logic belongs in factories rather than ad hoc constructors in command handlers;
- factory-driven creation automatically participates in the UoW.

## Built-In Repository

Generated repositories implement Spring Data JPA and expose cap4k `Repository<Entity>` through an adapter.

Typical authoring use:

- command handler loads aggregate roots through `Mediator.repositories.findOne/findFirst/find/...`;
- query handler can wrap repository/JPA read access for read flows;
- jobs should prefer query wrappers over direct repository access when they are doing read orchestration.

The built-in repository is part of the tactical model and should be demonstrated in business authoring docs.

## Unit Of Work

`UnitOfWork` supports:

- `persist`;
- `persistIfNotExist`;
- `remove`;
- `save(propagation = REQUIRED)`.

`JpaUnitOfWork` records persist/remove intentions, unwraps aggregate wrappers, assigns IDs, flushes entities, runs specifications, dispatches lifecycle listeners, publishes attached domain/integration events, and executes interceptors around the transaction.

Command handlers should call `Mediator.uow.save()` for write persistence. This is the canonical transaction boundary for generated command skeletons.

## Lifecycle Listeners

Aggregate behavior can define:

- `fun Entity.onCreate()`;
- `fun Entity.onUpdate()`;
- `fun Entity.onDelete()`.

`JpaUnitOfWork` invokes persist listeners when `supportEntityInlinePersistListener` is enabled. The generated behavior template already reserves lifecycle hooks; users can implement them without overriding entity templates.

Known limitation: lifecycle recognition has a defect/insufficiency that should be tracked separately. Authoring should still teach the intended usage, but should not claim unsupported subclass or child-entity behavior.

## Specifications

`Specification<Entity>` can validate aggregate state before or inside transaction through `SpecificationUnitOfWorkInterceptor`.

Authoring rules:

- generate specification only when the project intends to demonstrate or enforce this tactical concept;
- keep specification as checked-in `SKIP` skeleton;
- do not generate unused specification files in teaching projects.

## Domain Services

Domain services are Spring beans discovered through `DefaultDomainServiceSupervisor` and accessed through `Mediator.services`.

Use domain services when a business rule crosses aggregate boundaries or does not naturally belong to one aggregate root. Application code should orchestrate repository loading, then call the domain service for the domain decision, and finally write through the aggregate that owns the state change.

## Domain Events And Subscribers

Domain events represent domain facts and live with the domain model. Subscribers live under `application.subscribers.domain` by default and perform application-level follow-up work.

Subscriber method names should be semantic when the subscriber contains useful business logic. A generated `on` placeholder is acceptable only before implementation.

## Layering Rule Summary

| Code path | Should use |
|---|---|
| Command handler | factories, repositories, domain services, UoW, limited client/cli if result-dependent |
| Query handler | read repositories/JPA/query infrastructure |
| Client/cli handler | external/local adapter capability |
| Domain event subscriber | commands/queries/client requests for follow-up orchestration |
| Integration event subscriber | commands/queries/client requests after external event input |
| HTTP controller | API payload mapping and `Mediator.cmd/qry/requests` |
| Job | queries/client requests/commands; avoid direct repository access when a query boundary is clearer |
