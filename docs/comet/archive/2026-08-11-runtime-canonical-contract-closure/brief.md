# Outcome

Close the remaining documentation drift after Runtime PRs #158-#183 so the canonical Comet specs
and public Runtime KDoc describe the current `master` contracts without implying that retired
surfaces or unimplemented observability endpoints still exist.

# Scope

- Convert `runtime-roadmap` from a pending implementation roadmap into a completed Runtime
  capability index through PR #183.
- Add explicit historical/current time boundaries for the Jackson migration, HTTP subscriber
  registry retirement, reliable delivery context exclusions, and Actuator projection.
- Replace the retired reliable archive wording in the application UoW contract.
- Record the exact version-1 retry-policy snapshot facts already implemented by PR #167.
- Correct `IntegrationEventSupervisor` KDoc to describe reliable enqueue/schedule/delay semantics.
- Strengthen `scripts/validate-current-runtime-facts.ps1` with narrow canonical-contract drift
  checks when they can be expressed without scanning archived history.

# Non-goals

- No Runtime behavior, public API, state-machine, persistence schema, provider composition, route,
  transport, acknowledgement, retry algorithm, or delivery-context implementation change.
- No Analyzer implementation or downstream end-to-end validation.
- No live MySQL/PostgreSQL, RabbitMQ, RocketMQ, multi-process soak, process-crash, or external-side-
  effect/ack-window experiment.

# Acceptance examples

- A reader of `runtime-roadmap` sees Repository Contract, Surface Cleanup, and Runtime Agent facts
  as completed through PR #183 rather than pending work.
- A reader can distinguish `DomainEventSupervisor.attach/detach`, public
  `IntegrationEventSupervisor.enqueue/schedule/delay`, and transport-internal attachment hooks.
- Current specs state that `RuntimeProviderStateRegistry` exists and no Actuator endpoint exists;
  any future endpoint is only a direct read-only projection of the registry.
- Retry snapshot readers can derive policy version, classification, annotation overrides, default
  delay curve, repeated final custom interval, and carrier-specific fallback limits.
- The validation script rejects the known stale roadmap, subscriber-registry, archive, Actuator,
  and Integration Event KDoc wordings if they reappear in current facts.

# Constraints and invariants

- The branch starts from `origin/master` commit `bab4898942f5895075d0c46fd90fa2c9459158f1`.
- Historical change scope may be described, but every historical statement must be separated from
  current Runtime state.
- Current public outbound Integration Event operations remain only enqueue, schedule, and delay.
- Domain Event/UoW attach/detach and transport-internal attachment hooks remain valid and must not
  be deleted or mislabeled as public Integration Event APIs.
- External environment evidence remains `NOT_PERFORMED`; it is future real-project/provider
  verification and does not become a false implementation pass.

# Decisions

- PRs #158-#183 complete the audited Runtime implementation batches and supporting Repository,
  Surface Cleanup, and Agent facts slices.
- The canonical roadmap becomes a completed capability index. Remaining work in this change is
  documentation closure; unavailable external evidence is tracked separately.
- PR #164 did not own transport topology. PRs #177 and #179 later removed the HTTP subscriber
  registry, so current Runtime has no such registry.
- `ReliableEventDeliveryContext` does not own routing or topology. Public Integration Event
  registration uses enqueue/schedule/delay; Domain Event attach/detach and internal transport
  attachment hooks are separate concepts.
- The current live state source is `RuntimeProviderStateRegistry`. There is no current Actuator
  endpoint. A future optional endpoint may only delegate directly to the registry snapshot.
- Retry policy snapshot version is `1`, classification is `ANY_EXCEPTION`, and the implemented
  delay/override/fallback rules are canonical current facts.

# Open questions

None. The user explicitly confirmed this bounded documentation closure and prohibited Runtime
behavior changes.

# Verification expectations

- `comet native check runtime-canonical-contract-closure` passes against the implementation scope.
- `scripts/validate-current-runtime-facts.ps1` passes and its focused guard coverage remains green.
- Focused `ddd-core` compilation/tests cover the KDoc-only source change.
- Repository `./gradlew.bat check` and `git diff --check` pass.
- Verification records live database/broker/process evidence as `NOT_PERFORMED`, not passed.
