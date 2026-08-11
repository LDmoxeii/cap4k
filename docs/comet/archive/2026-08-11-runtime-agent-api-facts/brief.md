# Outcome

Publish final Runtime facts through the existing Gradle-first, manifest-first Agent API. Static facts
describe landed framework support and ownership while explicitly saying that application assembly,
live observation, and verification were not performed. Live transport state remains available only
through `RuntimeProviderStateRegistry.snapshot()`.

# Scope

- Bump `runtime.json` to a breaking schema with a deterministic Runtime capability/provider catalog.
- Separate framework support, application assembly, live observation, operational state, and
  verification.
- Publish the exact HTTP, RabbitMQ, and RocketMQ registry identities and ownership.
- Preserve Event Handler, extension, boundary, duplicate, and retired-descriptor contracts.
- Add focused generation/codec/task/registry/transport tests and update Agent API documentation.

# Non-goals

- No Actuator endpoint, MCP, remote service, application startup, classpath probing, dynamic project
  scan, or second Agent API.
- No business intent, Bounded Context, domain-model, strategic DDD, or Analyzer truth claims.
- No Runtime state-machine, Repository, transport, or cleanup product changes.
- No compatibility aliases for the previous runtime schema.

# Acceptance examples

- Static `integration-event-transport.http` is framework-supported but has assembly `UNKNOWN`,
  observation `NOT_PERFORMED`, operational state `UNKNOWN`, and verification `NOT_PERFORMED`.
- Only a live registry fact may report `HEALTHY`, `DEGRADED`, or `RECOVERING`.
- Reordered inputs produce byte-identical JSON and snapshot IDs.
- Duplicate capability/provider identities fail deterministically before encoding.
- Console, Snowflake, Locker, and Saga remain rejected, not emitted as placeholders.
- Facts contain no payload, credential, URI, topology, exception message, or stack trace.

# Constraints and invariants

- Baseline is `c046b4c01e6b45ddaab457b41a9cce18862efb71`, the repository-resolved full SHA for
  the requested `c046b4c0` baseline.
- Static facts use the existing Jackson-only stable encoder.
- `RuntimeProviderStateRegistry` is the sole live state source; the snapshot does not copy its map.
- Class, config, dependency, or prose presence never proves assembly, availability, health, or success.
- `NOT_PERFORMED` means the operation deliberately did not run. `UNKNOWN` means the current evidence
  source cannot establish the fact.
- `RetiredRuntimeDescriptorPolicy` remains a hard guard.

# Decisions

- Keep one `runtime.json`; do not create a parallel live-state file.
- Do not add Actuator now. A future adapter may only delegate each read to the registry snapshot.
- Exact provider IDs are `integration-event-transport.http`,
  `integration-event-transport.rabbitmq`, and `integration-event-transport.rocketmq`.
- Confirmed capability IDs/owners:
  - `runtime.core-dispatch` — `ddd-core` / `cap4k-ddd-core-starter`
  - `runtime.identifier-allocation` — `ddd-core` / `cap4k-ddd-core-starter`
  - `runtime.local-domain-event` — `ddd-core` / `cap4k-ddd-core-starter`
  - `runtime.jpa-persistence` — `ddd-domain-repo-jpa` / `cap4k-ddd-jpa-starter`
  - `runtime.reliable-command` — `ddd-application-command-jpa` / `cap4k-ddd-command-jpa-starter`
  - `runtime.reliable-event` — `ddd-domain-event-jpa` / `cap4k-ddd-domain-event-jpa-starter`
  - `runtime.integration-event-transport` — shared `ddd-core` contract; providers own implementations.

# Open questions

None. The user confirmed the seven-capability catalog and ownership granularity.

# Verification expectations

- Focused Agent contract/service/codec/Gradle task, registry snapshot, and exact transport identity tests.
- `scripts/validate-current-runtime-facts.ps1`, `./gradlew check`, and `git diff --check`.
- Comet Native check, evidence formatting, Verify, and Archive.
