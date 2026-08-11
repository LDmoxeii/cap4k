# Outcome

The active Runtime surface no longer exposes or builds the retired Locker capability. Current
framework code, module topology, configuration, SQL resources, tests, public documentation, and
Agent facts consistently describe the surviving Runtime only. The cleanup also proves that the
already retired Console, Snowflake Runtime, Saga Runtime, HTTP-JPA, FastJSON, and Gson surfaces have
not returned.

# Scope

- Remove the public `Locker` SPI from `ddd-core` because no surviving Runtime production code uses
  it.
- Remove the complete `ddd-distributed-locker-jdbc` implementation module, including its build,
  source, tests, and `locker.sql` resource.
- Remove the complete `cap4k-ddd-locker-jdbc-starter` module, including auto-configuration,
  properties, imports, tests, and dependencies.
- Remove both Locker modules from the Gradle settings/module graph and all active build references.
- Remove `__locker` and `locker.sql` from current Runtime database-schema documentation.
- Correct current project guidance that still describes Saga or Locker starters as active owners.
- Strengthen current-runtime-facts validation so reintroduction of active retired Runtime modules,
  classes, configuration, or SQL is rejected deterministically.
- Scan active source, build, scripts, current public documentation, and current Agent descriptors for
  retired Runtime surfaces and stale FastJSON/Gson usage.

# Non-goals

- Do not remove or rewrite `docs/comet/archive/**`, `docs/comet/runtime/transactions/**`, or
  `docs/superpowers/**`; they are historical or transactional evidence rather than active product
  claims.
- Do not remove retirement guards, negative classpath tests, or identifier-policy rejection tests
  merely because they mention a retired capability.
- Do not remove or redesign the surviving HTTP experience transport.
- Do not change Reliable Command/Event state machines, retry, claim, lease, redrive, retention,
  acknowledgement, delivery context, provider completion, or transport semantics.
- Do not add compatibility aliases, replacement Locker APIs, migration bridges, or a generic task or
  coordination framework.
- Do not modify Generator or Analyzer behavior as part of this Runtime cleanup.

# Acceptance examples

- Given the Gradle project graph, listing projects does not contain
  `ddd-distributed-locker-jdbc` or `cap4k-ddd-locker-jdbc-starter`.
- Given current production source and build files, no public `Locker`, `JdbcLocker`, Locker
  auto-configuration, Locker properties, or active Locker dependency remains.
- Given current Runtime SQL resources and database-schema documentation, only surviving Runtime
  infrastructure tables are documented; `__locker` and `locker.sql` are absent.
- Given the current-runtime-facts validator, an active Locker module/class/configuration/SQL
  reintroduction would be reported, while `RetiredRuntimeDescriptorPolicy` and explicit negative
  evidence remain allowed.
- Given active Runtime source/build/current docs, Console, Snowflake Runtime, Saga Runtime, HTTP-JPA,
  FastJSON, and Gson scans return no active capability or dependency.
- Given the surviving Integration Event modules, HTTP, RabbitMQ, and RocketMQ transports still
  compile and their existing tests remain green.
- Given Reliable Command/Event modules, their existing state-machine and persistence tests remain
  green and no Locker dependency is introduced.
- Given historical Comet and Superpowers artifacts, their contents remain unchanged by this cleanup.

# Constraints and invariants

- Breaking iteration is allowed and there are no external-user compatibility requirements.
- `origin/master` at `c634f289a304c1a4c5e8c245fc5e02fda1b4185e` is the implementation baseline and includes PR #179.
- Runtime JSON remains Jackson-only.
- The HTTP experience transport and the three landed transport contracts remain supported.
- `RetiredRuntimeDescriptorPolicy` continues to reject `console`, `locker`, `saga`, and `snowflake`
  descriptor identities.
- Snowflake input/strategy rejection guards remain active; a rejected legacy value is not an active
  capability.
- Negative classpath and dependency-boundary tests may remain when they prove retired capabilities
  are absent.
- Current product documentation must reflect current facts; historical design evidence must retain
  its original context.

# Decisions

- Delete the Locker SPI, implementation, starter, SQL, and active documentation as one breaking
  capability retirement; no replacement is introduced.
- Treat active source/build/scripts/current public docs and active Agent descriptors as the cleanup
  surface. Treat Comet archives, Comet transaction evidence, and Superpowers specs/plans as history.
- Keep the exact retired descriptor identity set as a rejection policy rather than deleting the
  policy after implementation removal.
- Extend `scripts/validate-current-runtime-facts.ps1` with precise active-surface checks and bounded
  allowlists for canonical specifications and negative evidence; do not use a broad ignore that could
  hide a real reintroduction.
- Preserve the surviving HTTP experience transport and do not confuse it with retired HTTP-JPA.
- The user explicitly confirmed this complete shared understanding on 2026-08-11.

# Open questions

None.

# Verification expectations

- Run focused tests for current-runtime-facts validation and surviving starter/module boundaries.
- Run the current-runtime-facts validation script directly.
- Run Gradle project/module graph inspection and dependency reports sufficient to prove Locker is no
  longer part of the build.
- Run bounded repository scans for active Locker, Console, Snowflake Runtime, Saga Runtime, HTTP-JPA,
  FastJSON, and Gson surfaces with historical evidence excluded.
- Run `git diff --check`.
- Run the complete Gradle `check` task.
- Run `comet native check`, record every Runtime-provided acceptance ID with real evidence or an
  honest skipped reason, and archive only after evidence remains fresh.
