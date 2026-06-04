# Layer And Runtime Boundaries

## Always True

- Put business invariants in Domain.
- Put use-case orchestration in Application.
- Put protocol shape mapping in Adapter.
- Put runtime assembly in Start.
- Use Repository for aggregate read, access, and load.
- Use Unit of Work for persistence intent, delete intent, commit, and save.
- Treat Mediator as a framework facade.
- Let framework/runtime HTTP and message transport handle consume, parse, register, and dispatch.
- Let business inbound subscribers interpret typed external facts, ensure idempotency, translate semantics, and delegate.
- Keep runtime guidance self-contained.

## Drift Checks

- Prevent "Repository owns aggregate persistence."
- Prevent "project code owns Unit of Work mechanics."
- Prevent "Mediator is a business engine."
- Prevent "subscribers own transport parsing and dispatch."
- Prevent "HTTP/message subscribers own consume/parse/register/dispatch."
- Prevent "controllers or adapters own domain invariants."
