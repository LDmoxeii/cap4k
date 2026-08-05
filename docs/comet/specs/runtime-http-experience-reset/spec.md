# HTTP Experience Transport Reset

## Depends on

Integration Event Core Contract and Integration Event Transport Contract.

## Contract

- Configuration remains a single static event-type-to-base-URL route map.
- A process may publish and consume its own route for local demonstrations.
- HTTP delivery waits for the receiver's synchronous handler completion and treats a successful
  response as acknowledgement.
- HTTP mode makes no production durability or broker-ordering claim.

## Non-goals

Do not add a second route syntax, service discovery, broker emulation, or HTTP-specific event state
machine.

## Acceptance

Two-process and self-routing tests cover route selection, handler failure, retry response, duplicate
delivery, context installation, and no external broker dependency.
