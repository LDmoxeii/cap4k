# Outcome

Replace Cap4k's mixed Request/UoW/event execution semantics with a coherent application execution model in which Command, Query, Capability, and Event have independent public meaning. Commands own one REQUIRED transaction and one automatically completed transaction-level UoW. Synchronous Domain Events are stabilized in non-reentrant causal frontiers, while asynchronous Commands and Integration Events are durably registered before commit and executed or published afterward. Implement the confirmed design across runtime, starters, generators, fixtures, and active documentation.

The repository design authority for this change is `docs/superpowers/specs/2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md`. The obsolete generator/addon/owned-child design document is removed rather than revised because its source assumptions and decisions no longer describe the intended runtime architecture.

# Scope

- Define Command, Query, Capability, Domain Event, and Integration Event as independent application concepts.
- Remove the public generic Request abstraction and category-wide policies.
- Define Command-only REQUIRED transaction ownership and automatic outer UoW completion.
- Define one physical transaction, one UoW Context, and one outer stabilization Coordinator.
- Define immediate nested Command execution without nested UoW completion or recursive event draining.
- Define persistence-intent normalization, candidate change detection, audit enrichment, final change detection, provider flush, and baseline advancement.
- Define root-oriented aggregate enrollment, root-only Domain Event ownership, optional reflective `onCreate`/`onDeleted`, and removal of `onUpdate`.
- Define non-reentrant synchronous Domain Event frontiers, unspecified sibling order, historical event payloads, current-state repository reads, and fail-fast Handler failure.
- Define reliable asynchronous Commands and transactional-outbox Integration Events.
- Define optional explicit persistence `flush()` without event drain or transaction commit.
- Rename public Client semantics to External Capability, shortened to Capability in code.
- Remove the built-in Saga runtime and avoid a speculative Saga SPI.
- Preserve JPA as the first provider while expressing public execution semantics in provider-neutral phases.
- Produce a new standalone design spec and delete the obsolete 2026-07-28 spec.
- Implement the confirmed model across the affected runtime modules, starter wiring, generator models/templates, tests, fixtures, and active documentation.

# Non-goals

- Design queryable Value Object persistence.
- Define the complete public third-party Persistence Provider SPI.
- Replace JPA or choose another JVM ORM.
- Guarantee aggregate-root optimistic-lock advancement for every owned-child change.
- Guarantee event order, Handler order, or occurrence-time aggregate snapshots.
- Provide asynchronous Domain Event Handlers.
- Provide a workflow engine, process manager, Saga implementation, or speculative Saga Provider SPI.
- Preserve backward compatibility for the affected APIs.
- Rewrite immutable historical Comet archive evidence that mentions the removed document.

# Acceptance examples

- Given an outer Command with no active transaction, when it is sent, then Cap4k creates one REQUIRED transaction and one UoW Context, invokes the Handler, stabilizes all local work, commits once, and clears the Context.
- Given a nested Command sent by a Command or synchronous Domain Event Handler, when it executes, then it joins the current transaction and UoW, returns its Handler result, and cannot independently flush by default, drain events, commit, or roll back.
- Given business state changes, when the outer Coordinator stabilizes, then candidate detection occurs before audit enrichment, final detection occurs afterward, and only the final changes are flushed.
- Given Domain Events produced by one stabilization round, when they are dispatched, then they form one frontier; events produced by their Handlers enter the next frontier and do not recursively re-enter the active dispatcher.
- Given sibling events or sibling Handlers, when they execute, then application correctness does not rely on a promised order.
- Given a synchronous Handler failure, when dispatch reaches it, then dispatch stops immediately and the local transaction rolls back.
- Given a Handler that needs occurrence facts, when it handles an event, then those facts come from the immutable payload; a Repository query deliberately returns current UoW state.
- Given an asynchronous Command registered inside a Command transaction, when the transaction commits, then the durable record becomes executable as a new outer Command; rollback removes the registration.
- Given an Integration Event registered inside a Command transaction, when the transaction commits, then outbox delivery may start; external publication never happens before commit.
- Given an explicit `flush()`, when it succeeds, then eligible persistent changes and reliable records are synchronized and the provider baseline advances, but Domain Events remain pending and the transaction remains open.
- Given CREATE followed by REMOVE before first synchronization, when intent is normalized, then the net persistence effect is NONE and unreleased root events are discarded.
- Given a root without `onCreate` or `onDeleted`, when factory creation or removal occurs, then absence of the optional reflective callback is valid.
- Given active generator/runtime contracts, when the redesign is complete, then generic Request, Client, and Saga public concepts are absent and Capability owns external-service semantics.
- Given the documentation change, when inspected, then the new 2026-07-30 standalone design exists, the obsolete 2026-07-28 design does not, and active documentation has no dangling reference to the removed path.
- Given the completed implementation, when the affected Gradle checks and focused fixtures run, then the new category boundaries, automatic UoW completion, non-reentrant event frontiers, reliable work boundaries, Capability migration, and Saga removal are demonstrated without production references to the removed APIs.

# Constraints and invariants

- Only the outer Coordinator may finish the UoW and physical transaction.
- Public Command propagation is REQUIRED only.
- A physical transaction may not contain multiple logical Cap4k UoW Contexts.
- Explicit flush may synchronize database state but may not drain Domain Events or commit.
- Domain Events originate from aggregate roots and do not carry Aggregate, Entity, persistence proxy, or mutable Carrier references.
- Sibling ordering is intentionally unspecified; required ordering must be represented by causality through a derived event or explicit orchestration.
- Reliable asynchronous work must be durably registered in the local transaction and signaled only after commit.
- Capability invocation does not create, suspend, commit, or enlist a local distributed transaction.
- JPA/Hibernate details must not leak into public Command, Query, Capability, Event, or audit contracts.
- Checked-in generator ownership remains first-generation plus SKIP; this change does not introduce overwrite strategies.
- Historical archive artifacts remain immutable evidence even if they reference the removed old spec.

# Decisions

- Command, Query, Capability, and Event are separate public contracts rather than Request variants.
- Command alone owns automatic REQUIRED transaction and UoW completion.
- Nested Commands execute immediately in the current transaction and UoW.
- Mandatory application `UnitOfWork.save()` is removed; optional advanced `flush()` remains.
- Stabilization uses intent normalization, candidate detection, audit enrichment, final detection, flush, and repeated event frontiers.
- Synchronous Domain Event dispatch is non-reentrant and fail-fast.
- Event and Handler sibling order is not guaranteed.
- Derived events are deferred to the next frontier.
- Event payload is the immutable historical fact; Repository reads are current-state reads.
- One Handler/one Command is authoring guidance, not a runtime restriction.
- Ordinary Domain Events do not mix synchronous and `@Async` Handlers.
- Local asynchronous work uses reliable asynchronous Commands.
- Cross-context facts use Integration Event outbox registration and publish after commit.
- CREATE followed by REMOVE before first synchronization folds to NONE and discards unreleased root events.
- `onCreate` and `onDeleted` are optional reflective aggregate-root callbacks; `onUpdate` is removed.
- Owned-child changes do not force aggregate-root version advancement in the first implementation.
- Client becomes External Capability/Capability.
- Built-in Saga is removed and no speculative Saga SPI is introduced.
- JPA remains the first Persistence Provider implementation; the full third-party Provider SPI is deferred.
- The user confirmed this complete design and authorized immediate implementation, including parallel sub-agent work.

# Open questions

None.

# Verification expectations

- Run `git diff --check` and an equivalent whitespace check for untracked artifacts.
- Confirm the new standalone spec exists and the obsolete spec is deleted.
- Search active documentation outside `docs/comet/archive/**` for references to the deleted filename.
- Review the new spec headings and fixed-decision section against this brief and the proposed target specification.
- Run focused module tests for each implementation slice and the repository-level verification required by the affected build graph.
- Search production sources, templates, fixtures, and active documentation for removed generic Request, Client, Saga, propagation, and mandatory `save()` contracts.
