# Application Execution And Hibernate UoW Stabilization

## Target outcome

The application runtime shall implement the approved application-execution and Hibernate UoW contract from `docs/superpowers/specs/2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md`, with the archived PR #150 capability `managed-field-policy-and-pipeline-extension` taking precedence wherever managed-field, admission, enrichment, or pipeline semantics overlap. Command, Query, Capability, and Event remain independent public concepts. The implementation shall be auditable through focused tests and repository-wide evidence, and shall fail clearly when a supported contract cannot be established.

## Public categories and invocation

- Command creates or joins exactly one REQUIRED write transaction and one Cap4k UoW Context. Only the outer Coordinator stabilizes, completes, or rolls back it; nested Commands execute immediately in the same physical transaction and context.
- Query owns one Handler-wide REQUIRED read-only transaction covering validation, interceptors, Handler execution, Repository lazy navigation, and DTO mapping. Query creates no write UoW, audit, event drain, or flush.
- Capability is persistence-neutral and may execute with an ambient Command transaction without using Repository, Factory, UnitOfWork, Command, or Query entrypoints. Caller Runs must install a temporary Capability scope above the caller scope.
- Domain Events are immutable local facts handled synchronously in causal frontiers. Integration Events are outbox records published only after commit; inbound Integration Events and local Domain Events share the same synchronous, sequential, fail-fast Event Handler execution model.
- A cap4k Event Handler is exactly one method-level Spring `@EventListener` method for one concrete `@DomainEvent` or `@IntegrationEvent` payload. It returns `Unit/void`; startup discovery rejects `@Async`, `suspend`, `@TransactionalEventListener`, `defaultExecution=false`, multiple event declarations, polymorphic subscriptions, and non-`Unit/void` return types.
- InvocationScope is a strict local LIFO stack identifying COMMAND, QUERY, CAPABILITY, or the shared Event Handler scope. It is installed before validation/interceptors/handlers and never serialized or copied to another thread.

## Asynchronous execution and context

- Query and Capability expose one blocking Handler shape plus synchronous and asynchronous supervisor methods. Their executors are separate, bounded, replaceable, and default to Caller Runs with an explicit shutdown/rejection failure path.
- Async Query/Capability validation, resolution, transaction, Handler, overload, and shutdown failures complete the returned CompletionStage exceptionally. Sync APIs throw their original failures. Timeout limits waiting only and does not promise cancellation.
- An Event Handler may combine synchronous Command/Query/Capability calls with parallel `askAsync()` / `callAsync()` information gathering. Before the Handler completes, Runtime waits for every managed async stage started in that Handler scope; any stage failure fails the Handler and therefore the enclosing transaction or inbound delivery. State-changing work that must outlive the current call stack uses reliable Command `enqueue`, `schedule`, or `delay`.
- Async Command means durable registration, later execution after commit, and a new outer Command transaction. It never falls back to Caller Runs or joins the caller UoW.
- ExecutionContext is an immutable typed snapshot with duplicate-rejecting keys, versioned codecs, explicit allowed boundaries, strict reliable decode, and tolerant unknown external ingress only for unknown names. It propagates attribution only, never UoW, EntityManager, transaction, InvocationScope, event state, or arbitrary ThreadLocals.
- Framework-owned async, reliable, RPC, and Integration Event boundaries capture and restore the snapshot. Reliable records preserve origin context across persistence, claim, retry, redrive, and the final terminal transition; there is no current archive path. Null legacy context decodes as EMPTY; decode failure prevents Handler execution; Integration Event publication is post-commit.

## UoW, Repository, and Hibernate behavior

- Application code has no public UoW locator/lifecycle or save/persist/remove/flush surface. Repository has no persistence flags, `PersistIntent.EXISTING`, explicit detach, detached-root merge, or `AggregateLoadPlan`.
- Command Repository loads observe managed aggregate roots without treating clean reads as writes. Query may read roots or owned entities for the duration of its read-only Handler transaction. Repository removal accepts only a managed root in an active Command.
- Hibernate MANUAL flush starts before Command validation, interceptors, and Handler execution, remains through nested Commands and event frontiers, and is restored during cleanup. Only provider stabilization calls `EntityManager.flush()`; required Hibernate integration failures are not silently downgraded.
- Change detection is aggregate-root organized with Entity-level detail and owned-graph ownership. Untouched lazy relations stay unloaded; changed or deleting relations may be initialized when classification requires it. Shared/ambiguous ownership, detached owned attachment, independent child merge, managed ID mutation, and detached root removal fail.
- Managed-field admission initializes and validates supported Strong IDs before root `onCreate` or owned-child attachment. UoW validation is side-effect-free and never performs late ID allocation or repair. CREATE then REMOVE before first synchronization folds to NONE and discards unreleased root events; after synchronization it remains INSERT then DELETE.
- Optional root `onCreate` and `onDeleted` callbacks are valid when absent, `onDeleted` runs at most once, and `onUpdate` is removed. Child changes do not automatically advance root version or root audit fields.

## Stabilization, audit, and events

- Each outer Command captures one audit time and ExecutionContext snapshot. Every round performs intent normalization, candidate detection, qualifier-owned managed-field enrichment, topology/event guard, final detection, explicit provider flush, and baseline advancement.
- Clean loaded aggregates are not audit candidates. Each `JpaPersistenceEnricher` receives exact managed-field handles and may change only its declared provider-property footprints; its immediate dirty delta is checked before the next enricher runs. Topology, lifecycle, Command/Event, and persistence mutations fail. Separate qualifiers have no semantic order; coordinated fields use one qualifier and unique slots. A later round may enrich the same Entity idempotently with the same UoW context.
- The outer Coordinator drains non-reentrant Domain Event frontiers until there is no dirty state, unresolved intent, reliable/outbox record, pending event, or active frontier. Sibling Event order is unspecified. Handlers for one concrete event execute by method-level Spring `@Order` with lower values first; equal values remain unordered. Dispatch is fail-fast and rolls back the current transaction on Handler failure.
- Late mutation after stabilization, loop-limit overflow, detection failure, audit failure, ownership/ID failure, flush failure, serialization failure, or commit failure rolls back the current transaction with causal diagnostics. Later reliable execution or external publication failure belongs to its own retry/delivery domain.

## Generator and compatibility boundaries

- Generators and active examples use Command/Query/Capability and one Handler shape each; async belongs to supervisors. Client, Saga, generic Request, public persistence callbacks, and removed UoW/Repository surfaces are not reintroduced.
- Active runtime and generator surfaces use PR #150's exact managed-field policy keys, generated `ManagedFieldCatalog`, admission Initializers, qualifier-owned `JpaPersistenceEnricher`, explicit value Adapters, and startup-complete runtime bindings; the removed `JpaPersistenceAuditEnricher` and parallel Strong-ID-only registry are not reintroduced.
- Checked-in generated Factory and Behavior ownership remains first-generation create followed by SKIP. Generated root and owned-child Strong ID allocation remains immediate.
- Spring Data JPA plus Hibernate is the supported persistence runtime for this target. No public provider SPI, alternative ORM, or compatibility layer is part of this capability. Direct EntityManager flush, bulk DML, native SQL, custom transaction synchronization, and arbitrary user executor propagation remain outside the contract.

## Verification contract

The implementation is accepted only when focused owner tests and static searches cover the examples above and the archived PR #150 acceptance contract, the repository-wide check is run when the final scope requires it, skipped checks are recorded honestly, and every Runtime-derived acceptance item has project-relative evidence or an explicit skipped reason. A confirmed violation found during the audit is repaired in the owning module with a regression test before Verify proceeds; an unconfirmed suspicion is recorded as a limitation rather than changing behavior without evidence.
