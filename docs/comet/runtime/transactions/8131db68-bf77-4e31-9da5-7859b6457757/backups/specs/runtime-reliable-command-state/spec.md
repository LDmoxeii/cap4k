# Reliable Command State Machine

## Depends on

`runtime-handler-contract` and `runtime-reliable-jpa-substrate`.

## Contract

- Reliable Command records use one state machine and one substrate path.
- `Mediator.commands.send` remains synchronous; `enqueue`, `schedule`, and `delay` create reliable
  work without exposing a polling API.
- Claim, lease, retry, safe failure, acknowledgement, and retention transitions are delegated to
  the substrate without changing command handler completion semantics.
- Result polling repositories, archive records, Locker integration, and legacy scheduling bypasses
  are deleted.

## Non-goals

No generic task framework, new command handler contract, or transport-specific command state.

## Acceptance

State-transition tests cover success, retryable failure, terminal failure, duplicate claim,
lease-expiry recovery, and redrive eligibility. Static scans prove old result/Locker bypasses are
gone.
