# Outcome

Retire Cap4k's custom Snowflake identifier capability completely. UUID7 becomes the only built-in application-side Strong ID allocation strategy, while database-assigned identity remains a persistence policy rather than an application-side generator.

# Scope

- Remove the custom Snowflake algorithm and every supporting runtime surface: Worker-ID dispatch, `__worker_id` schema, heartbeat/lifecycle, Hibernate bridge, Runtime module, starter, auto-configuration, properties, descriptors, and dependencies.
- Remove Generator and canonical/catalog support for Snowflake as an accepted Strong ID strategy.
- Remove Agent API capability claims, generated catalogs/options, tests that assert Snowflake support, fixtures, and public documentation.
- Preserve negative validation coverage: explicit `snowflake` requests from Design JSON or database/schema input fail with a deterministic diagnostic that identifies the supported application-side ID strategy.
- Remove stale module/dependency/settings/catalog/documentation references from the active repository surface.

# Non-goals

- Do not modify `docs/framework-capability-audit` or implement other Runtime audit findings.
- Do not introduce another Snowflake algorithm, another distributed ID strategy, or a maintained-library integration in this change.
- Do not alter database-assigned/autoincrement identity as a persistence policy.
- Do not preserve aliases, deprecated APIs, dual paths, fallback codecs, silent conversion, or compatibility bridges.
- Do not restore historical Snowflake material that is retained only inside archived design/history records.

# Acceptance examples

- Given an application Strong ID that uses the built-in default strategy, generated allocation uses UUID7 and the Runtime catalog exposes only UUID7.
- Given Design JSON with an explicit `snowflake` strategy, collection/normalization or planning fails deterministically and the diagnostic states that `uuid7` is the supported application-side ID strategy; generation never silently substitutes UUID7.
- Given database/schema input that explicitly requests `snowflake`, validation fails with the same supported-ID guidance instead of treating database assignment as an application-side fallback.
- Given a normal database-assigned/autoincrement entity ID, persistence ownership remains valid and no application-side Snowflake/UUID7 allocation is inferred.
- Given the active Gradle project graph, no Snowflake Runtime module or starter is included or published.
- Given active source, resource, test, configuration, Agent API, and public-documentation surfaces, no custom Snowflake algorithm, Worker-ID dispatcher/schema/lifecycle, Hibernate bridge, support claim, or compatibility alias remains.

# Constraints and invariants

- Work starts from current `origin/master` on isolated branch `feature/runtime-snowflake-retirement`.
- Cap4k currently has no external users; this is an intentionally breaking single-contract reset.
- The confirmed Runtime capability-reset specification on `docs/framework-capability-audit` is the authoritative product contract; implementation remains isolated from that audit branch.
- Before creating or updating the pull request, wait for the Console-retirement PR to merge, then rebase onto latest `origin/master` and resolve overlapping settings, documentation, and Console Snowflake-handler deletion.
- The final pull request targets `master` and is created with the repository PR script.

# Decisions

- UUID7 is the only built-in application-side Strong ID allocation strategy.
- Database-assigned identity is persistence policy only.
- An explicit retired strategy request is invalid input, not an alias or migration signal.
- Future Snowflake support requires a separate confirmed design using a maintained open-source implementation and must not revive this custom coordination subsystem.
- The user confirmed this focused implementation scope in the current request after the complete Runtime target contract had already been confirmed on the audit line.

# Open questions

None.

# Verification expectations

- Run focused tests for affected Runtime, starter, Generator/source/canonical/catalog, Agent API, and schema/design validation ownership.
- Add or update negative tests proving explicit `snowflake` requests fail with the supported `uuid7` diagnostic and are never converted silently.
- Run a repository stale-surface scan covering Snowflake names, modules, artifacts, Worker-ID/schema/heartbeat/lifecycle/Hibernate bridge claims, with only intentional negative-validation and historical archive evidence allowed.
- Run the repository required `check` command after the Console merge rebase.
- Record exact commands and outcomes in Comet verification evidence; never report an unrun check as passed.