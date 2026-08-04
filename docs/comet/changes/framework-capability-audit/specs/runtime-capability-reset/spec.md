# Runtime Capability Reset

## Target outcome

Cap4k Runtime shall provide explicit, composable tactical execution semantics whose installed providers fail clearly, whose reliable work is claimed atomically, and whose Integration Event transports implement a small at-least-once contract. Runtime shall not expose accidental Spring Data, distributed-lock, codec, subscriber-registry, or multi-transport behavior as public DDD capability. Generator, Analyzer, documentation, and Agent API facts shall describe this same current contract.

## Composition and provider boundaries

- One application/Bounded Context selects exactly one outbound Integration Event transport provider. Installing multiple publishers is a deterministic startup conflict; Core never broadcasts one event record to several transport providers.
- Deterministic local defects such as missing providers, duplicate registrations, invalid configuration, missing tables/schema, and conflicting transport providers fail startup with actionable diagnostics.
- Temporary broker unavailability does not terminate an otherwise valid application. The transport reports observable `DEGRADED` or `RECOVERING` state, durable outbound work remains retryable, inbound subscribers reconnect, and health/readiness never reports an unavailable transport as operational.
- Factory, repository-adapter, event-name, payload-type, and capability identities reject duplicate effective registrations instead of silently choosing the last registration.

## Runtime facts and operational health

- The Gradle/versioned-JSON Agent API remains external-I/O-safe. It may inspect resolved local Runtime configurations and packaged provider descriptors, but it never starts Spring, connects to a database, pings a broker, or presents dependency presence as operational readiness.
- `runtime.json` reports each application module's resolved Runtime artifacts/providers and provenance, capability and exclusivity group, required companions and configuration keys, redacted local declaration evidence, deterministic static diagnostics, and explicit `operationalProbe: NOT_PERFORMED` / `operationalState: UNKNOWN` fields.
- Runtime starters package stable machine-readable provider descriptors so the Agent API does not hard-code artifact names or maintain a second capability catalog.
- Application startup validates actual Spring provider composition and every deterministic local prerequisite. This startup validation, not Gradle inspection, is authoritative for the running application.
- A Runtime provider-state registry independent of Actuator tracks actual `STARTING`, `UP`, `DEGRADED`, `RECOVERING`, and `OUT_OF_SERVICE` state plus bounded failure facts. An optional Actuator adapter projects that registry into health/readiness; external connectivity and reconnect state never appear in the static Agent snapshot as probed facts.

## Repository runtime ownership

- Application behavior accesses aggregate persistence only through `Mediator.repositories`; application code does not receive a generated public Spring Data `JpaRepository` bean.
- Generator emits a cap4k JPA adapter/carrier bound to the aggregate root. Its Spring Data or `EntityManager` implementation is provider-private.
- Aggregate removal remains an explicit repository-supervisor operation. Ordinary managed aggregate changes continue through Hibernate dirty checking and the Command-owned Unit of Work.
- Generator and Analyzer preserve the adapter/carrier as framework-owned structure in the normalized design round trip.

## Repository and query consistency

- Repository and aggregate-factory route keys are unique. Duplicate effective `(entity type, predicate type)`, creation-payload, reflector, or unresolved generic registrations fail startup with all conflicting contributors identified; no map or `associateBy` path silently selects a winner.
- `JpaPredicate` is a closed exactly-one variant created only through ID, ordered ID-list, or specification factories. Empty/ambiguous internal variants fail consistently, and `findOne` rejects an ID-list containing more than one candidate rather than silently taking the first.
- ID-list queries preserve the caller's stable input order, apply page coordinates consistently across `find` and `findPage`, report total matched rows rather than current-page size, and preserve the requested page number for empty pages. Missing IDs do not change the relative order of present results.
- `PageData.transform` and Spring Data conversion preserve the source `pageNum`, `pageSize`, and `totalCount`; conversion changes only element type/content.

## Handler execution model

- Every cap4k Handler invocation is synchronous: `CommandHandler`, Query Handler, Capability Handler, and cap4k `@EventListener` complete only after the invoked method returns or throws and every Mediator-managed parallel task in that Handler scope has converged.
- Synchronous cap4k Event Handlers are controller-like local adapters and may call synchronous `Mediator.commands.send`, `Mediator.queries.ask`, and `Mediator.capabilities.call`. They may also use `queries.askAsync` and `capabilities.callAsync` for structured parallel fan-out inside the same Handler invocation: start independent tasks, explicitly await the results needed to construct a dependent Command, and then invoke `commands.send` or register it through `commands.enqueue/schedule/delay`.
- Runtime tracks every Mediator-managed async Query/Capability task in the current Event Handler scope. After the method body returns, Runtime automatically waits for all scoped tasks before declaring the Handler complete; any task failure fails the Handler and propagates to the surrounding transaction or reliable delivery/ack boundary. Manually spawned threads, coroutines, executors, or reactive subscriptions remain outside this scope and outside cap4k completion guarantees.
- “Asynchronous” describes Mediator-managed scheduling or parallel invocation, never a Handler implementation return contract. `queries.askAsync` and `capabilities.callAsync` can execute independent local work concurrently but remain scoped to the synchronous Handler completion boundary. `Mediator.commands.send` invokes a synchronous Command Handler now; `enqueue/schedule/delay` synchronously accepts a reliable Command record, and a scheduler later invokes the same ordinary synchronous Command Handler.
- Reliable Command registration joins the current cap4k Command Unit-of-Work transaction when one exists. A synchronous cap4k Event Handler with no ambient transaction may call `enqueue/schedule/delay` directly; the provider opens a short record-persistence transaction, commits before returning the command ID, and throws registration failure through the Event Handler. This infrastructure transaction does not turn the Event Handler into a business transaction root.
- Public outbound Integration Event registration uses `Mediator.events.enqueue/schedule/delay`; `attach/detach` is removed without a compatibility path. Registration is synchronous while transport publication remains scheduled. With an active Command Unit of Work, the outbox record joins the current transaction and commits or rolls back with the originating state change. Without one, a synchronous Event Handler uses a provider-owned short outbox transaction that commits before returning the event ID; persistence failure propagates through the Handler.
- Each committed outbound Integration Event registration creates a new outbox record. Source-event redelivery may register another event, and Runtime does not pretend that immediate local persistence makes remote publication or consumption exactly once. Stable event IDs and downstream business idempotency remain part of the at-least-once contract.
- Runtime provides no producer-side Reliable Command deduplication, explicit `deduplicationKey`, or automatically derived event/listener/command key. Every committed registration creates a new record; source-event redelivery may register another. Stable event IDs let projects carry source identity into Commands and enforce business idempotency, while cap4k continues to declare at-least-once rather than a partial exactly-once claim.
- `@Async`, `suspend`, Future/CompletionStage, reactive return values, and manual thread/coroutine/executor/reactive fire-and-forget are not framework-managed Handler completion. Detectable contract violations fail startup; manually detached work is outside cap4k reliability and should be expressed as a reliable Command or Integration Event.

## Synchronous Domain Event boundary

- Ordinary non-persistent Domain Events execute synchronously on the current Command transaction frontier. Handler failure propagates and can roll back that transaction.
- Method-level Spring `@EventListener` is the sole public cap4k Domain/Integration Event handler contract. One bean may declare several independent listener methods for the same payload. `EventSubscriber<T>`, `AbstractEventSubscriber<T>`, public dynamic subscribe/unsubscribe, and the duplicate custom subscriber registry are removed without deprecation or compatibility bridges.
- A cap4k listener has exactly one concrete cap4k event payload, returns only `Unit`/`void`, and completes synchronously on the publishing thread. `classes` multi-event declarations and supertype/polymorphic subscriptions are unsupported and fail startup; one bean may still declare multiple independent methods for the same concrete event. A non-blank Spring `condition` SpEL and Spring `@Order` are allowed as handwritten Runtime filtering/priority policy but are not Design JSON, generated-skeleton semantics, Analyzer recovery, or Drawing Board content. Lower order values run first within one local delivery attempt; equal values have no guaranteed order.
- Detectable class/method `@Async`, `@TransactionalEventListener`, and non-`Unit`/non-`void` returned-event publication are deterministic startup failures with guidance to enqueue a reliable Command or publish another Integration Event. Manually launching a thread, coroutine, executor task, or reactive subscription and then returning is outside cap4k's completion guarantee and does not become framework-managed work. Non-cap4k Spring application events are outside these restrictions.
- Cap4k's listener adapter explicitly refuses asynchronous execution even when the standard Spring multicaster has an executor. Direct and Spring-composed method annotations use standard merged-annotation resolution, but the resulting listener descriptor must satisfy the same cap4k payload, return, condition, and local-priority constraints. Discovery, validation, diagnostics, dispatch, and inbound subscription derivation use this one resolved descriptor model; Generator and Analyzer model each resolved method-level reaction without maintaining an interface-based second path.
- Persisted Domain Events use the reliable Event state machine and do not weaken the synchronous contract of ordinary Domain Events.

## Reliable scheduled event delivery

- A persisted/deferred Domain Event that is dequeued outside its originating transaction and an inbound Integration Event are the same reliable scheduled-delivery family for Handler transaction and Unit-of-Work semantics.
- They differ only at the upstream confirmation boundary: successful persisted Domain Event handling conditionally completes its local Event record, while successful inbound Integration Event handling acknowledges the broker delivery or returns HTTP 2xx.
- Ordinary non-persistent Domain Events remain synchronously invoked inside the originating Command transaction and are not moved into this scheduled-delivery boundary.
- Every event Handler invocation remains synchronous. Handlers entered by persisted/deferred Domain Event delivery and inbound Integration Event delivery are controller-like adapters rather than transaction roots; Runtime creates no ambient transaction or Unit of Work around their dispatch.
- A handler that changes local model state sends a local Command; every outer Command establishes and owns its own transaction and Unit of Work. Repository invocation guards continue to reject direct aggregate access outside Command/Query scope, so an event adapter cannot silently perform non-transactional aggregate work.
- One reliable scheduled event delivery may have multiple local handlers. Dispatch is a synchronous in-memory call chain with no per-handler persistence: evaluate each matching Handler's `condition` and invoke each applicable synchronous Handler once per attempt in Spring `@Order` priority, leave equal values unordered, complete/acknowledge only after the dispatcher returns, and stop at the first thrown failure so the whole delivery remains retryable. Every redelivery restarts from the first applicable Handler; local priority creates no cross-message, cross-instance, cross-service, or transactional ordering guarantee.
- If a later handler fails, Commands committed by earlier handlers remain committed and redelivery may invoke them again. This is intentional at-least-once behavior and requires idempotent event reactions; Runtime does not create a cross-handler transaction that presents external effects as rollback-safe.

## Codec contract

- Jackson is the only JSON codec family used by active Runtime transports, reliable persistence, build tooling, Agent API, Design JSON, and Drawing Board paths.
- FastJSON and Gson dependencies, fallback paths, and compatibility codecs are removed.
- Runtime and build tooling may own separately configured mappers, but they share stable semantic rules for Strong ID scalar form, event/command envelopes, nullability, defaults, collection shapes, type identity, and deterministic machine output.

## Identifier generation

- UUID7 is the only built-in application-side Strong ID allocation strategy. Database-assigned identity remains a persistence/provider policy and is not an application-side ID generator.
- The self-written Snowflake algorithm, database Worker-ID dispatcher, `__worker_id` schema, heartbeat/lifecycle, Hibernate bridge, `ddd-distributed-snowflake`, `cap4k-ddd-snowflake-starter`, configuration, Generator/catalog options, Agent API capability claims, tests, and public documentation are removed.
- Design and schema inputs that request the retired `snowflake` strategy fail deterministic validation with an actionable UUID7-only diagnostic. No alias, fallback, silent conversion, or compatibility path is retained.
- Any future Snowflake support requires a separate confirmed design based on a maintained open-source implementation; it does not revive the retired custom coordination subsystem.

## Reliable Command and Event persistence

- Automatic retry policy is an operational Runtime/deployment concern. Reliable Command, persisted Domain Event, and Integration Event payload types do not carry `@Retry`, and retry counts, backoff, or execution expiry do not participate in Design JSON, generated skeletons, Analyzer recovery, or Drawing Board semantics.
- Runtime provides one shared fallback retry policy for reliable Command, persisted Domain Event, and Integration Event. Runtime configuration may override it by capability and message identity, but the framework does not embed separate historical fallback schedules for the three capabilities. Every configured policy is validated deterministically; unresolved or structurally invalid effective policy fails startup rather than silently selecting another policy.
- At reliable-record creation, Runtime resolves and persists the effective attempt budget, backoff policy, and execution expiry/deadline with the record. Later deployment-configuration changes affect newly accepted work only and do not rewrite the semantics of existing records.
- The shared fallback becomes due immediately at `firstAvailableAt`, permits 50 total claimed executions including the first, and retries after 1, 2, 4, 8, and 16 minutes, then capped 30-minute delays. Each delay receives deterministic record-ID-derived ±10% jitter so replicas calculate the same schedule while spreading synchronized failures.
- The shared fallback execution deadline is 24 hours after `firstAvailableAt`; future-scheduled work does not consume its retry lifetime before it is eligible. Exhausting 50 real attempts first enters `EXHAUSTED`, while reaching the execution deadline first enters `EXPIRED`. A next-attempt time beyond the deadline is not executed.
- Accepted reliable Command/Event execution has no retryable/non-retryable exception taxonomy. Every execution exception follows the effective retry policy already snapshotted into the record; Runtime provides neither `NonRetryableExecutionException` nor a project `FailureClassifier`. Deterministic configuration, route, type, codec, and payload defects fail at startup or registration, while expected business refusal is expressed as a normal business result, no-op, or event rather than an execution exception.
- Reliable records persist only bounded safe failure facts: `failureId`, exception type, failure timestamp, attempt, and status. Exception messages, stack traces, and duplicate payload copies are not persisted as failure detail. Full diagnostics go to structured logs/traces correlated by `failureId`; transport adapters do not emit raw payloads as failure logs, and programmatic recovery surfaces expose only the safe record facts.
- Synchronous Command and ordinary synchronous local Domain Event remain non-persistent.
- Synchronous `Mediator.commands.send` continues to return the Command Handler result. Reliable `enqueue/schedule/delay` returns only the registration ID: `Mediator.commands.result(id)` and reliable-record result payload/type persistence are removed without compatibility paths. Reliable Command completion is fire-and-forget at the Runtime task boundary; business outcomes are observed through domain state, Query, Domain Event, or Integration Event rather than a temporary or long-term Runtime result warehouse.
- Reliable Command records and reliable Event/outbox records remain separate tables and concepts. They may share provider-internal claim, lease, retry, retention, and verification infrastructure but not a public generic task API.
- Each persistence provider atomically claims due work using a short transaction, database time, and a claim token, lease/version check, `SKIP LOCKED`, or equivalent compare-and-set mechanism. Query-then-lock and JVM-only mutexes are not reliable claims.
- While a synchronous reliable Command/Event Handler is executing, Runtime renews its lease using the active claim token and database time. Process death stops renewal so another instance may atomically reclaim after expiry. Renewal and terminal updates are conditional on the same token; lease loss rejects stale completion and reports degraded provider state. Side effects produced before lease loss remain an at-least-once ambiguity rather than being presented as rollback-safe.
- Attempt counts advance only for real claimed executions. Scheduling time, first available time, retry eligibility, lease expiry, and terminal retention are not conflated.
- Success and failure updates are conditional on the active claim. A crash or timeout can yield an ambiguous outcome, which remains retryable under at-least-once semantics.
- Manual retry/redrive accepts only records in `EXCEPTION` or `EXHAUSTED`. It preserves the original logical Command/Event record ID and must atomically reclaim the record through the same eligibility, claim, attempt-accounting, and conditional-completion state machine as automatic recovery.
- Each manual redrive of an `EXHAUSTED` record grants exactly one additional claim opportunity. It preserves the original attempt history and does not restart the 50-attempt/24-hour automatic policy; the explicit operator authorization bypasses the exhausted automatic execution deadline for that one claim. Success completes the record. Failure returns it to `EXHAUSTED`, records a new terminal transition and `terminalAt`, and requires another explicit redrive for another attempt.
- Manual retry rejects `INIT`, active `EXECUTING`/`DELIVERING`, successful `EXECUTED`/`DELIVERED`, `CANCEL`, and `EXPIRED` records. An intentional re-execution or redelivery of completed or expired work uses the ordinary new `send`/`enqueue` registration path and creates a new logical record and ID.
- The public `Locker` API, JDBC Locker implementation/starter/table/configuration, and scheduler dependency on that Locker are removed. A future business-facing distributed lease requires a separately designed fenced-lock capability.
- Runtime does not create or mutate MySQL partitions. Project-owned schema migrations own tables, indexes, partitions, and retention DDL.
- Reliable Command storage uses only `__command`, and reliable Event storage uses only `__event`. The write-only `__archived_command` and `__archived_event` tables, archived JPA models/repositories, and active-to-archive migration are removed.
- Provider-owned cleanup hard-deletes retained terminal records in bounded batches. Command and Event retention are separately configurable and both default to seven days from the record's latest transition into a terminal state, recorded as an explicit `terminalAt`; execution `expireAt` is never reused as the retention clock.
- Manual recovery clears the prior terminal marker while the `EXHAUSTED` record is actively reclaimed. A later terminal transition sets a new `terminalAt` and restarts the retention window. Records remain manually recoverable only before cleanup deletes them; other long-term history belongs to logs, metrics, tracing, or project-owned audit storage rather than a cap4k archive warehouse.

## Operations surface

- The `cap4k-ddd-console` module, its auto-configuration, direct SQL services, unauthenticated HTTP search/retry/unlock handlers, tests, and public documentation are removed.
- Runtime retains only the state-machine-validated programmatic manual-recovery API for reliable Command and Event records. Cap4k does not publish an operations UI or administrative HTTP endpoint.
- A consuming project that exposes manual recovery owns authentication, authorization, payload/exception redaction, operator audit, and network policy at its own application boundary.

## Integration Event delivery

- One Integration Event creates one durable outbox record and one delivery state for the selected transport. There is no per-consumer, per-endpoint, or per-handler delivery table.
- Transport confirmation means that the selected transport accepted the publication; it does not mean every downstream consumer completed business handling.
- Explicit failure, missing confirmation, timeout, or ambiguous outcome remains retryable. Stable event IDs cross every transport boundary and consumers are expected to handle duplicate delivery idempotently.
- Inbound Integration Event receivers remain stateless. Cap4k does not add an inbox table, consumer-side durable scheduler, per-consumer delivery progress, deduplication store, or framework-owned DLQ.
- RabbitMQ and RocketMQ acknowledge only after every applicable local Handler and its Mediator-managed parallel tasks succeed; handler failure is returned to the selected broker/container, whose deployment configuration owns redelivery limits and dead-letter behavior. HTTP returns non-2xx on any local handling failure so the sender's reliable record remains retryable. Consuming projects own business idempotency.
- Public handler signatures remain payload-only and do not accept a transport envelope. A read-only scoped `ReliableEventDeliveryContext` is installed and cleared consistently for persisted/deferred Domain Event and inbound Integration Event dispatch. It exposes transport-neutral stable `eventId`, `eventName`, `publishedAt`, an exact delivery attempt only when available, and a non-authoritative `UNKNOWN/FIRST/REDELIVERED` hint. Ordinary synchronous Domain Events have no reliable delivery context. Strict and nullable accessors distinguish active delivery from absence; no exchange, routing key, topic, queue, consumer group, HTTP topology, or authoritative duplicate claim leaks through it.
- Publisher success is based on the transport's real acknowledgement boundary: broker confirm/ack for RabbitMQ, asynchronous send success for RocketMQ, and HTTP 2xx for the HTTP experience transport. Message construction or local executor submission is not publication success.
- Initial after-commit publication, scheduled recovery, and manual retry use the same atomic claim and conditional completion state machine. No entrypoint bypasses claim, status eligibility, attempt accounting, or persistence of failure.
- Persisted Domain Event dispatch remains one local delivery attempt. A handler failure makes the attempt retryable; Runtime does not track individual handler progress, so handlers must tolerate retry.

## HTTP experience transport

- HTTP is a non-production experience transport for exercising serialized Integration Event delivery without RabbitMQ or RocketMQ.
- Its only routing configuration shape is `routes[eventName] -> baseUrl`. Pointing a route to the current application explicitly supports self-production/self-consumption through the same HTTP path; there is no separate loopback or default-target mode.
- The receiver exposes one fixed consume endpoint. The publisher appends that fixed path to the configured base URL and sends stable event ID, event name, payload, and supported execution context.
- Missing routes are deterministic configuration errors. HTTP 2xx confirms the attempt; non-2xx, connection failure, and timeout persist failure and remain retryable.
- HTTP supports one target per event and does not claim dynamic subscription, broadcast, discovery, production-grade availability, or per-receiver progress. Multiple receivers require a broker transport.
- Dynamic subscribe/unsubscribe and inspection endpoints, subscriber registries, their JPA implementation/starter/table, and `eventName@registerUrl` are removed.

## Transport routing

- `IntegrationEvent.value` is only the stable semantic `eventName`. It never embeds an HTTP URL, RabbitMQ exchange/routing key, RocketMQ topic/tag, subscriber identity, or another deployment address.
- Each installed transport has one explicit `routes[eventName]` configuration map. HTTP route values provide a target base URL, RabbitMQ route values provide exchange and routing key, and RocketMQ route values provide topic and tag.
- Publisher and listener adapters resolve the same semantic event name through the selected transport's route table. Switching transports or broker topology changes configuration, not Design JSON, generated event contracts, Analyzer output, or Drawing Board semantics.
- Every generated outbound event and every listener-discovered inbound event required by the selected provider has exactly one valid route. Missing, duplicate, blank, or structurally invalid effective routes fail deterministic validation.
- Runtime does not infer a Broker destination directly from the event name and provides no fallback exchange/topic convention. The removed `exchange:routingKey`, `topic:tag`, and `eventName@registerUrl` annotation grammars are not retained as compatibility paths.

## Inbound subscription identity

- Actual Spring Integration Event listeners are the only default inbound discovery root. An annotated payload type without a listener does not create a broker subscription.
- `IntegrationEvent` retains the event identity but removes `subscriber` and `NONE_SUBSCRIBER`; deployment topology is not part of the event annotation.
- Each resolved `applicationName + eventName` identifies one inbound subscription. RabbitMQ creates one event-specific queue and RocketMQ creates one event-specific consumer group; replicas with the same application identity share it for competing consumption.
- The subscription owns one payload type and dispatches to all local handlers for that type. Duplicate resolved event names associated with different payload classes fail startup.
- Subscriber startup and reconnect failures are observable provider state. They are not swallowed into a permanently absent listener.

## Verification contract

- Focused tests cover provider conflict and absence, deterministic startup failures, degraded/recovering broker state, atomic multi-worker claiming, active lease renewal and stale-token rejection, crash/lease recovery, retry accounting, one-opportunity manual redrive, safe persisted failure facts, reliable Command result-polling removal, and stable event IDs.
- Handler tests prove the one-descriptor method-level contract, multiple methods per bean, `condition` filtering, local `@Order` priority with unordered ties, scoped async Query/Capability convergence and failure propagation, rejection of unsupported async/transactional/return/multi-event/polymorphic shapes, and removal of the interface-based subscriber path.
- Delivery-context tests prove identical `ReliableEventDeliveryContext` installation/cleanup for persisted/deferred Domain Event and every inbound Integration Event transport, explicit absence for ordinary Domain Event, transport-neutral optional attempt/redelivery hints, and no topology or authoritative duplicate claim.
- Transport tests prove RabbitMQ confirm/nack/return/timeout behavior, RocketMQ synchronous and asynchronous failures, HTTP static routes including self-routing, missing-route failure, listener-derived subscriptions, event-specific queue/group identity, reconnect, and duplicate event-name rejection.
- Repository tests prove that no public generated Spring Data repository is required and that `Mediator.repositories` operations retain current JPA semantics.
- Repository-wide dependency checks prove removal of FastJSON, Gson, JDBC Locker, Console, and custom Snowflake surfaces; machine-contract tests prove UUID7-only catalogs, Jackson-deterministic round trips, and truthful Runtime Agent API facts.

## Non-goals

- Exactly-once publication or consumption.
- Sender knowledge of all downstream consumers or their processing status.
- Production-grade HTTP message-bus behavior, HTTP broadcast, service discovery, or durable subscriber registration.
- A public generic durable-task runtime or a replacement business distributed lock.
- Restoring the retired Saga modules, persistence, scheduler, or public capability; historical Saga design documents remain history only.
- Compatibility aliases, deprecated paths, dual codecs, or migration bridges for hypothetical users.
