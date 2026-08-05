# Runtime Provider Composition and Startup Conflicts

## Depends on

Runtime Jackson core contract. This slice may run in parallel with retry-policy and safe-failure
cleanup, but it must land before the final Runtime Agent facts.

## Contract

- Each provider slot has at most one active implementation.
- Duplicate implementations fail during application-context initialization with the slot name and
  conflicting bean identities.
- Optional providers use an explicit zero-or-one rule; `getIfUnique()` must not silently degrade a
  multi-provider configuration.
- Registry configuration rejects duplicate provider identities and never uses last-write-wins.

## Non-goals

Do not change routes, handler semantics, reliable record states, or broker behavior.

## Acceptance

ApplicationContextRunner tests cover required and optional provider conflicts, missing providers,
registry duplicate identities, and deterministic diagnostics.
