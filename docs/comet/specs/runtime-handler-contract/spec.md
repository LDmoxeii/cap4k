# Runtime Handler Contract

## Status

Merged baseline: PR #158. This file records the frozen contract for later Runtime branches.

## Contract

- `@EventListener` is the only public event-handler entry point.
- `EventSubscriber<T>` and legacy subscriber abstractions are retired; no compatibility alias is
  added.
- Handler methods are synchronous. `send` executes a command immediately; `enqueue`, `schedule`,
  and `delay` only schedule work through the Mediator.
- A handler scope waits for every `queries.askAsync*` and `capabilities.callAsync*` started by that
  scope before it completes. Any failed child operation fails the handler scope.
- Event listeners may declare `condition` and `@Order`. Order is local dispatch ordering only; no
  global cross-consumer delivery-order guarantee is made.

## Non-goals

No handler coroutine API, detached execution, implicit transaction widening, or generic task queue.

## Evidence

Focused handler-contract tests, static rejection of retired subscriber types, and failure/success
scope tests must remain green after every dependent Runtime branch.
