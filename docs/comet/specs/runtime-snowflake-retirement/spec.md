# Runtime Snowflake Retirement

## Target outcome

Cap4k has one built-in application-side Strong ID allocation strategy: UUID7. Database-assigned identity remains a persistence/provider policy and is not an application-side generator. The retired custom Snowflake subsystem has no active implementation, module, starter, configuration, schema, generator/catalog option, Agent API claim, test support claim, or public documentation.

## Runtime and module removal

- Remove the self-written Snowflake algorithm and its public/runtime APIs.
- Remove database Worker-ID dispatch, `__worker_id` DDL/table support, heartbeat and lifecycle coordination, and the Hibernate identifier bridge.
- Remove the Snowflake Runtime module, starter, auto-configuration, configuration properties, provider metadata, publication coordinates, settings entries, inter-module dependencies, and test fixtures.
- No alias, deprecated facade, delegating bridge, feature flag, optional fallback, or parallel implementation remains.

## Generator and input contract

- UUID7 is the only accepted built-in application-side Strong ID allocation strategy in canonical models, generated accessors, generator configuration, descriptors, and generated catalogs.
- Design JSON that explicitly requests `snowflake` fails deterministic validation before code generation. Its diagnostic identifies the rejected value and states that `uuid7` is the supported application-side ID strategy.
- Database/schema input that explicitly requests `snowflake` fails deterministic validation with the same supported-ID guidance.
- Invalid Snowflake input is never normalized, aliased, or silently converted to UUID7, database assignment, or another strategy.
- Database-assigned/autoincrement identity remains valid only as persistence policy. It is not listed or treated as an application-side Strong ID generator.

## Agent and documentation contract

- Machine-readable Agent API facts do not advertise a Snowflake Runtime capability, module, starter, provider, strategy, configuration key, catalog option, or generated support claim.
- Public README, guides, examples, templates, and active reference documentation describe UUID7 as the sole built-in application-side Strong ID allocation strategy.
- Historical archived specifications and plans may remain as immutable history, but they are not linked or described as current support.

## Future boundary

- This change introduces no replacement distributed-ID algorithm.
- Any future Snowflake capability requires a separate confirmed design and a maintained open-source implementation.
- A future design must not restore the retired custom Worker-ID coordination subsystem by compatibility bridge or hidden fallback.

## Verification contract

- Focused tests prove UUID7 allocation remains valid and all affected catalogs/descriptors expose UUID7 only.
- Negative Design JSON and schema/database-source tests prove explicit `snowflake` input fails with actionable UUID7-only diagnostics and no generated output is silently rewritten.
- Module/settings/publication checks prove the Runtime module and starter are absent.
- Repository stale-surface checks prove active implementation, test-support claims, configuration, Agent API, and public documentation no longer contain retired Snowflake/Worker-ID surfaces, except intentional negative-validation fixtures and historical archived records.
- The branch passes the repository required `check` after rebasing the merged Console-retirement change from latest `origin/master`.

## Non-goals

- Other Runtime capability-audit findings.
- Changes to the continuous framework audit branch.
- Compatibility aliases, deprecated APIs, dual strategies, fallback implementations, or migration bridges.
- Changing database-assigned identity semantics.