# Layering And Tactical Model

## Layer Rules

- Domain owns aggregate behavior, entity behavior, value concepts, domain events, and invariant language.
- Application owns command/query/cli handlers, process orchestration, subscribers, and transaction-facing use-case work.
- Adapter owns HTTP, persistence adapters, external callbacks, jobs, and transport bridges.
- Infrastructure details must not become the public tactical model.

## Write-Side Rules

- Commands own write-side business behavior.
- A command should normally mutate one aggregate root.
- Repository, factory, domain service, and unit of work usage belongs in command handling.
- Use `Mediator.cmd` for command dispatch and `Mediator.uow` for unit-of-work execution when persistence is involved.

## Orchestration Rules

- Process orchestration may use command, query, and cli boundaries.
- Domain-event and integration-event subscribers are orchestration entry points when they continue a business process.
- A subscriber with meaningful business work should use a semantic handler method name, not a generic default name.
- Command handlers should not use queries unless a fresh issue explicitly accepts that exception.
- Command handlers should avoid cli calls unless the command depends on an external capability result.
