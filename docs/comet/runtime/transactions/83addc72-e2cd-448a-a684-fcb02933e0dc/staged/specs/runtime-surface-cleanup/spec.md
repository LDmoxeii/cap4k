# Runtime Surface Cleanup

## Purpose

The active cap4k Runtime surface contains only supported capabilities. Retired Runtime capabilities
are absent from production modules, public APIs, starters, configuration, SQL, current public
documentation, and active Agent descriptors, while explicit retirement guards and historical
evidence remain available.

## Supported surface

- Reliable Command and reliable Domain/Integration Event execution remain Runtime-owned.
- HTTP remains the lightweight experience Integration Event transport with static routes and the
  fixed receive endpoint.
- RabbitMQ and RocketMQ remain supported Integration Event transports under their landed provider
  contracts.
- Runtime serialization remains Jackson-only.

## Locker retirement

- The Runtime MUST NOT expose a public `Locker` SPI or an equivalent replacement coordination API.
- The Gradle module graph MUST NOT include `ddd-distributed-locker-jdbc` or
  `cap4k-ddd-locker-jdbc-starter`.
- Active production source and resources MUST NOT contain `JdbcLocker`, its auto-configuration,
  configuration properties, starter imports, `locker.sql`, or the `__locker` table definition.
- Current public Runtime documentation MUST NOT present Locker or `__locker` as a supported
  capability or schema.
- The retirement is breaking. The framework MUST NOT provide compatibility aliases, migration
  bridges, or a replacement distributed-lock implementation.

## Other retired Runtime surfaces

- Active Runtime source, modules, starters, configuration, SQL, and current public documentation
  MUST NOT restore Console, Snowflake Runtime, Saga Runtime, or HTTP-JPA.
- Active Runtime and build dependencies MUST NOT use FastJSON or Gson; Jackson is the sole Runtime
  JSON boundary.
- The surviving HTTP Integration Event transport MUST NOT be classified or removed as HTTP-JPA.

## Retirement evidence

- `RetiredRuntimeDescriptorPolicy` MUST continue to reject the descriptor identities `console`,
  `locker`, `saga`, and `snowflake`.
- Explicit Snowflake strategy/input rejection guards MUST remain because rejecting a legacy value is
  part of the current boundary, not restoration of the capability.
- Negative classpath, provider-boundary, and dependency tests MAY mention retired capabilities when
  they prove those capabilities are absent.
- Current-runtime-facts validation MUST reject active retired modules, classes, configuration, SQL,
  or descriptors with deterministic diagnostics.
- Allowances in validation MUST be precise enough that canonical retirement specifications,
  retirement guards, and negative evidence remain visible without hiding an active reintroduction.

## Historical evidence boundary

- Comet archives, Comet transaction evidence, and Superpowers specifications/plans are historical or
  workflow evidence and MUST NOT be deleted or rewritten merely because they describe a capability
  that was supported at that time.
- Current product guidance, public Runtime references, and active Agent facts MUST describe the
  current supported surface instead of relying on historical statements.

## Reliability invariants

- This cleanup MUST NOT change Reliable Command/Event state transitions, retry-policy snapshots,
  claim or lease ownership, delivery tokens, redrive, retention, acknowledgement, delivery context,
  or provider-completion behavior.
- This cleanup MUST NOT introduce a generic scheduler, task framework, distributed coordination
  abstraction, or a second Integration Event routing model.
- Existing HTTP, RabbitMQ, and RocketMQ transport behavior and verification suites MUST remain valid.

## Acceptance

- Gradle project listing and dependency evidence contain no active Locker implementation or starter
  module.
- Active source/build/current-document scans contain no Locker implementation, public SPI,
  auto-configuration, properties, SQL, or current capability claim.
- Current Runtime database-schema documentation contains no `__locker` or `locker.sql` entry.
- Active source/build/current-document scans contain no restored Console, Snowflake Runtime, Saga
  Runtime, HTTP-JPA, FastJSON, or Gson surface.
- Retirement guards and negative evidence still pass and continue to reject retired descriptor or
  strategy identities.
- Surviving Runtime, starter, reliable-state, and Integration Event transport tests pass.
- Historical Comet and Superpowers evidence is unchanged by the cleanup.
