# Outcome

Implement the already-confirmed `runtime-provider-composition` canonical contract so every runtime provider slot has a deterministic zero-or-one or exactly-one composition rule and invalid compositions fail during Spring application-context initialization.

# Scope

- Add one shared provider-composition boundary for named Spring beans.
- Apply strict exactly-one validation to required Mediator provider slots.
- Apply strict zero-or-one validation to optional provider slots, including every current `getIfUnique()` path.
- Reject duplicate static provider-slot registration without allowing last-write-wins, while preserving legitimate sequential Spring context lifecycles.
- Add focused context and registry tests with deterministic conflict diagnostics.

# Non-goals

- Do not change routes, handler semantics, reliable record states, retry behavior, or broker behavior.
- Do not redesign transport-provider identities or Runtime Agent facts in this slice.
- Do not add compatibility aliases or preserve silent provider fallback behavior.

# Acceptance examples

- Given two required `CommandSupervisor` beans, application-context initialization fails and the diagnostic names the `commands` slot and both conflicting bean identities.
- Given two optional `IntegrationEventManager`, `ReliableDomainEventProvider`, or `RepositorySupervisor` beans, application-context initialization fails instead of resolving the dependency as absent; the diagnostic names the slot and all conflicting bean identities in deterministic order.
- Given no optional provider, the context starts and the corresponding runtime capability remains unavailable.
- Given one provider, the context starts and the slot resolves that provider.
- Given a duplicate provider-slot registration within one active context ownership lifecycle, registration fails and preserves the original provider; closing that context releases its registrations so a later context in the same JVM can start normally.

# Constraints and invariants

- The canonical contract is `docs/comet/specs/runtime-provider-composition/spec.md` and is not changed by this implementation-only change.
- Provider conflict checks happen during application-context initialization.
- Diagnostics include the stable slot name and sorted conflicting Spring bean identities.
- A multi-provider optional composition is an error, never an implicit `null`.
- Static runtime slots never use last-write-wins.
- Breaking iteration is allowed; no external-user compatibility bridge is required.

# Decisions

- Reuse one named-bean composition helper rather than duplicating `ObjectProvider.getIfUnique()` checks.
- Treat provider registration ownership as a Spring context lifecycle so static slots can reject concurrent duplicate ownership without leaking across sequential test/application contexts.
- The user assigned implementation of this previously confirmed and merged canonical slice on 2026-08-05; this execution does not reopen its product contract.

# Open questions

None.

# Verification expectations

- Focused `ApplicationContextRunner` tests cover required conflicts, optional conflicts, missing optional providers, successful unique composition, registry duplicate rejection, deterministic diagnostics, and sequential-context cleanup.
- Run focused starter/core test tasks first, then the repository-wide `check` if focused verification passes.
- Review all production `getIfUnique()` uses and prove none remain in runtime provider composition paths.
