# Layer And Runtime Boundaries

## Always True

- Put business invariants in Domain.
- Put use-case orchestration in Application.
- Put protocol shape mapping in Adapter.
- Put runtime assembly in Start.
- Use Repository for aggregate read, access, and load.
- Use Unit of Work for persistence intent, delete intent, commit, and save.
- Treat Mediator as a framework facade.
- Let framework and runtime integration-event transport consume, parse, register, and dispatch.
- Let business inbound subscribers interpret typed external facts, ensure idempotency, translate semantics, and delegate.
- Keep runtime guidance self-contained.

## Drift Checks

- Prevent "Repository saves aggregates."
- Prevent "business code implements Unit of Work."
- Prevent "Mediator is a business engine."
- Prevent "subscribers own transport parsing and dispatch."
- Prevent "controllers or adapters own domain invariants."
