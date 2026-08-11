# Runtime Surface Cleanup

## Depends on

Reliable Command State, Reliable Event State, Integration Event Transport Contract, and the already
merged Console/Snowflake retirement slices.

## Contract

Remove Locker, Snowflake, Saga, Console, and HTTP-JPA surfaces that are no longer supported:
modules, starters, auto-configuration, DSL keys, SQL, source packages, tests, documentation, and
Agent descriptors. Keep the surviving HTTP experience transport. Keep Runtime JSON Jackson-only.

## Non-goals

Do not restore compatibility aliases, migrate unrelated Generator/Analyzer docs, or change the
reliable state machine while deleting retired surfaces.

## Acceptance

Repository scans, Gradle dependency reports, module graph checks, and current-runtime-facts
validation show no active retired surface or stale JSON stack.
