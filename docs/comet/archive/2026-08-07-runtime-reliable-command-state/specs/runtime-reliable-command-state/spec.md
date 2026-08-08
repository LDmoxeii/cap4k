# Reliable Command State Machine

## Depends on

`runtime-handler-contract` and `runtime-reliable-jpa-substrate`.

## Contract

- Reliable Command records use one state machine and one private substrate path.
- `Mediator.commands.send` remains synchronous. `enqueue`, `schedule`, and `delay` create reliable
  work without exposing a result-polling API.
- The source transaction persists the command record atomically. A detached worker claims one due
  record through the substrate before invoking the existing synchronous command supervisor.
- Claim, lease, retry, safe failure, acknowledgement, and retention transitions are delegated to
  the substrate. Every ownership mutation is fenced by the delivery token and unexpired lease.
- A successful handler is completed by token-bound acknowledgement. A failed handler is completed
  by the substrate failure transition, which stores only safe failure facts and applies the
  persisted retry-policy snapshot.
- Result polling repositories, archive records, `CommandManager`, `Locker` integration, and legacy
  scheduled retry paths are deleted from Command production wiring.
- Command handlers remain synchronous; only the scheduling operation is detached from the source
  call stack.

## Non-goals

No generic task framework, new command handler contract, public polling/result API, manual redrive
API, transport-specific command state, broad shared-Locker deletion, or full retention/cleanup
worker is introduced here.

## Acceptance

State-transition and runtime tests cover:

- successful execution and token-bound acknowledgement;
- retryable failure and terminal failure;
- duplicate claim prevention;
- lease-expiry recovery and old-owner fencing;
- source transaction rollback atomicity;
- synchronous `send` and scheduling-only `enqueue`/`schedule`/`delay` behavior;
- redrive eligibility facts without restoring the removed `CommandManager.retry` API.

Static scans prove that Command production sources no longer use `CommandManager`, `Locker`,
`getByNextTryTime`, or the legacy scheduled retry service.
