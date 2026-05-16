# Gotchas

## Mediator Bypass

Do not inject handlers directly when the cap4k project path is `Mediator.cmd`, `Mediator.qry`, `Mediator.requests`, repositories, factories, services, or `Mediator.uow`.

## Handler Placement Confusion

Query handlers and client/cli handlers are adapter-side physical handlers by default. Do not move them to application just because they implement request contracts.

## Repository Misplacement

Jobs, controllers, and transport adapters should not become direct write-persistence surfaces. Route writes through commands and use queries for read-oriented views when the boundary is clearer.

## Generated Snapshot Confusion

`src-generated/main/kotlin` is an audit or learning snapshot when copied into a project. Active generated source normally lives under `build/generated/cap4k/main/kotlin`.

## Skeleton Overwrite

Handlers, factories, specifications, and behavior skeletons that receive handwritten logic should use `SKIP` or equivalent protection after first generation.

## Design Tag Overreach

Do not use unsupported design tags such as first-class `value_object` or `domain_service` as if cap4k will generate them. Use `integration_event` only with its required `role`, `eventName`, request payload fields, and empty `responseFields`.

## Value Object Persistence Confusion

Do not treat `@VO` as the default value-object path. JSON-backed and inline value objects normally use `@T`, `types.registryFile`, a handwritten Kotlin type, and an explicit converter.

## Value Object UoW Confusion

Do not call `Mediator.uow.persist(valueObject)` for a value owned by an aggregate field. Save the aggregate root and let the mapping carry the value.

## Integration Event Table Surprise

When the HTTP-JPA integration event adapter is present, the project needs the framework subscriber registry table. Local examples must include compatible DDL for the chosen database.
