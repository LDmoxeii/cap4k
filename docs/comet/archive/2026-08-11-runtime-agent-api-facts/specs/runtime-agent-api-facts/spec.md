# Runtime Agent API Facts

## Contract

The existing Gradle-first Agent snapshot publishes one deterministic `runtime.json`. It is a static
framework and engineering fact document; it does not start an application, inspect Spring, query a
broker, or infer business truth.

### Static capability catalog

Each Runtime capability fact contains stable identity, display name, contract/implementation/starter
ownership, framework support, current-application assembly, runtime observation, verification, and
optional provider identities.

The confirmed active capability identities are:

- `runtime.core-dispatch`;
- `runtime.identifier-allocation`;
- `runtime.local-domain-event`;
- `runtime.jpa-persistence`;
- `runtime.reliable-command`;
- `runtime.reliable-event`;
- `runtime.integration-event-transport`.


### Static provider catalog

The Integration Event transport capability declares exactly:

- `integration-event-transport.http`;
- `integration-event-transport.rabbitmq`;
- `integration-event-transport.rocketmq`.

Each provider fact contains capability identity, implementation/starter ownership, framework support,
application assembly, runtime observation, operational state, verification, and authoritative live
state source. The Gradle snapshot marks support from the landed catalog, assembly `UNKNOWN`,
observation `NOT_PERFORMED`, operational state `UNKNOWN`, and verification `NOT_PERFORMED`. Source,
configuration, dependency, or prose presence never promotes those fields.

### Live provider state

`RuntimeProviderStateRegistry` remains the sole live source. Its snapshot fields stay `providerId`,
`state` (`HEALTHY`, `DEGRADED`, or `RECOVERING`), `observedAt`, and safe `category`.

Presence in an actually observed registry snapshot proves current registration in that application.
Static absence proves nothing. Absence from an actually performed live snapshot means no registration
was observed then.

No Actuator endpoint is added. A future optional endpoint must delegate each read directly to
`RuntimeProviderStateRegistry.snapshot()` and must not cache, merge, or derive provider state.

### Identity, duplicate, and retirement rules

Normalized capability and provider identities are unique. Duplicates produce deterministic diagnostics
and prevent a valid snapshot. Static provider IDs exactly match the IDs registered by the three
transport starters.

`RetiredRuntimeDescriptorPolicy` continues to reject capability identity segments and exact provider
identities `console`, `snowflake`, `locker`, and `saga`. They are never emitted as placeholders.

### Safety and determinism

All Agent JSON uses the shared Jackson-only stable encoder. Catalogs, ownership, and provider references
are normalized and sorted before hashing.

Facts contain no raw payload, execution-context payload, credential, token, header, URI, broker detail,
topology secret, exception message, cause, or stack trace. Static output does not copy arbitrary live
registry categories.

The manifest does not claim business intent, Bounded Context correctness, domain-model correctness,
strategic DDD quality, or Analyzer business truth.

## Acceptance

- Manifest generation exposes the confirmed capability catalog, ownership, and exact provider IDs.
- Static assembly/state fields remain `UNKNOWN`; observation/verification remain `NOT_PERFORMED`.
- Registry tests prove sorted snapshots, duplicate rejection, reporter fencing, close/re-register, and
  exact transport identities.
- Duplicate capability/provider identities fail deterministically.
- Reordered inputs produce byte-identical `runtime.json` and snapshot hashes.
- JSON round-trip uses the shared Jackson codec only.
- Retired tests and `scripts/validate-current-runtime-facts.ps1` prove no active retired facts.
- Focused tests, `./gradlew check`, `git diff --check`, and Comet Verify pass; unavailable external
  execution is reported `NOT_PERFORMED` rather than successful.