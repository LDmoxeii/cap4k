# cap4k DDD Starter Test Fixture Isolation Design

> Date: 2026-05-06
> Status: Proposed
> Scope: `cap4k-ddd-starter` test fixture isolation, scoped package scanning, stale test fixture property repair
> Out of scope: production auto-configuration semantics, DDD core runtime changes, JPA runtime behavior changes, disabling failing tests, broad starter test rewrites

## Issue

This design covers GitHub issue `#20`:

- `testing: isolate cap4k-ddd-starter auto-configuration test fixtures`

The goal is to close the current `:cap4k-ddd-starter:test` failure wave by fixing test-side fixture boundaries instead of treating the failures as framework regressions.

## Background

`cap4k/AGENTS.md` already calls out a known starter test-fixture debt:

- full `:cap4k-ddd-starter:test` fails around old `@SpringBootTest` context startup tests
- broad test applications under `com.only4.cap4k.ddd` scan too much
- repeated repository bean names collide across test contexts
- some contexts still use stale Snowflake property keys and accidentally boot DB-backed Snowflake
- some contexts fail domain-event subscriber scanning because the event scan package is blank
- one initialization test excludes Hibernate JPA auto-configuration while still enabling JPA repository scanning

Those observations match the current repository state.

## Reproduction

Reproduced on 2026-05-06 with:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --no-daemon
```

Observed result:

- 64 tests executed
- 28 failed
- 3 skipped

The passing tests already show the intended boundary:

- `IdPolicyAutoConfigurationTest`
- `ApplicationSideIdJpaRuntimeTest`
- `AggregateJpaRuntimeDefectReproductionTest`

The failing cluster is concentrated in the old starter smoke/context tests:

- `AutoConfigurationLoadTest`
- `BasicAutoConfigurationTest`
- `BeanDependencyTest`
- `BeanInitializationTest`
- `BeanLifecycleTest`
- `CircularDependencyTest`
- `CoreInitializationTest`
- `JavaVersionTest`
- `SimpleAutoConfigurationTest`
- `SimpleBeanLoadTest`

## Problem

The old starter tests mix three separate problems into one red build:

1. Package-scan contamination
2. Repository/entity fixture collisions
3. Single-fixture configuration drift

The first two are the main issue. The third does not disappear automatically once isolation is fixed, so it must be repaired in the same slice to produce a stable starter test run.

## Root Cause

### 1. Root-package test applications scan sibling test fixtures

Multiple starter tests declare nested or top-level Spring Boot applications inside package `com.only4.cap4k.ddd` and then rely on one of these patterns:

- Spring Boot default scan from a root-package `@SpringBootApplication`
- `@ComponentScan(basePackages = ["com.only4.cap4k.ddd"])`
- `@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])`
- `@EntityScan(basePackages = ["com.only4.cap4k.ddd"])`

That causes one test context to discover unrelated test fixtures from sibling files, including:

- other nested `@SpringBootApplication` classes
- unrelated test repositories and entities
- runtime test commands and repositories
- production configuration classes discovered through component scanning instead of Boot auto-configuration import

This is why failures such as repository bean re-registration mention multiple unrelated test application classes in the same context.

### 2. Broad JPA scanning duplicates framework repositories

Tests that use broad `@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])` and broad `@EntityScan(basePackages = ["com.only4.cap4k.ddd"])` register every starter-visible repository and entity fixture in the same context.

That pulls together repositories such as:

- request repositories
- saga repositories
- integration-event subscriber repositories

The result is repeated bean definitions and unstable context startup.

### 3. Two fixture configurations are internally stale

After the scan boundary is repaired, two separate fixture problems still remain:

- some tests still rely on the old Snowflake property naming and therefore accidentally boot DB-backed Snowflake, which immediately queries ``__worker_id``
- `BeanInitializationTest` excludes Hibernate JPA auto-configuration while still enabling JPA repository/entity scanning, so `entityManagerFactory` is missing by construction

These are test-fixture self-consistency problems, not production defects.

## Goals

- Make starter tests only scan the fixture packages they actually own.
- Remove cross-test discovery of unrelated nested test applications.
- Replace broad JPA scanning with scoped `basePackageClasses` or equivalent explicit package ownership.
- Repair the two confirmed single-fixture configuration drifts so the starter smoke-test cluster can boot consistently.
- Keep all changes inside `cap4k-ddd-starter/src/test/**` unless a verification artifact under `src/test/resources` is required.

## Non-Goals

- Do not change starter production auto-configuration semantics.
- Do not modify DDD core or JPA runtime behavior to "work around" broken test fixtures.
- Do not rewrite the old starter smoke tests into a different testing style as part of this slice.
- Do not disable failing tests to hide contamination.
- Do not broaden this issue into a framework capability redesign.

## Design

### 1. Stop using `com.only4.cap4k.ddd` as a shared fixture scan boundary

The central rule for this slice is:

> No starter smoke-test application used by the failing cluster may scan `com.only4.cap4k.ddd` directly.

Instead, the tests should point at dedicated test applications that live in narrow fixture packages under `src/test/kotlin`, so Boot default scanning stays local to the owning fixture package.

### 2. Introduce dedicated fixture applications for the failing starter smoke tests

Use a small set of top-level test applications instead of many root-package nested applications:

- a minimal non-JPA fixture application for tests that only need Boot auto-configuration startup
- a scoped JPA fixture application for tests that intentionally exercise starter JPA wiring

These applications should live in dedicated test-only packages such as:

- `com.only4.cap4k.ddd.fixture.minimal`
- `com.only4.cap4k.ddd.fixture.jpa`

Because those packages are narrower than `com.only4.cap4k.ddd`, Spring Boot default component scanning will no longer discover unrelated sibling test classes.

### 3. Use explicit JPA ownership for the scoped JPA fixture

The scoped JPA fixture application should replace broad string-based scans with `basePackageClasses` or equivalent explicit package ownership for:

- starter JPA entities that are actually needed
- starter JPA repositories that are actually needed

This keeps framework persistence fixtures available without registering every repository in the test tree.

### 4. Keep event subscriber scanning non-blank without reopening package sprawl

Tests that still boot domain-event-related auto-configuration must set a non-blank event scan package that points at a dedicated test-only marker package, not at the entire starter test root.

This fixes the `scanPath must not be blank` failure without reintroducing shared package scanning.

### 5. Repair Snowflake fixture drift in test properties

For the old starter smoke tests in this slice, real DB-backed Snowflake worker leasing is not the behavior under test.

Recommended repair:

- explicitly set `cap4k.ddd.distributed.id-generator.snowflake.enable=false`

This uses the currently effective property prefix and prevents accidental startup of the worker-dispatcher path that queries ``__worker_id``.

The design does not introduce a worker-table schema fixture because that would widen the test surface beyond what these smoke tests need.

### 6. Repair the no-Hibernate fixture to stop requiring JPA infrastructure

`BeanInitializationTest` currently mixes:

- `spring.autoconfigure.exclude=...HibernateJpaAutoConfiguration`
- `@EnableJpaRepositories(...)`
- `@EntityScan(...)`

That combination is self-contradictory.

Recommended repair:

- point the test at the minimal non-JPA fixture application
- remove the test-side JPA scanning requirement from that fixture path

This keeps the test focused on bean initialization smoke coverage instead of forcing an `entityManagerFactory` that the fixture explicitly excluded.

## File Scope

Expected test-only change surface:

- modify failing starter smoke tests under `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd`
- create dedicated fixture applications under `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/fixture/**`
- optionally add a small marker class package under `src/test/kotlin`
- optionally update `src/test/resources` only if a tiny fixture-specific resource becomes necessary during implementation

No production file changes are expected.

## Verification Strategy

Verification should stay short and Windows-safe:

1. Reproduce the original full failure with:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --no-daemon
```

2. Verify the repaired starter smoke tests by running short class-level commands instead of a single very long `--tests` chain.

3. Re-run the full module test task:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --no-daemon
```

Expected success condition:

- the old starter smoke/context cluster no longer fails because of cross-fixture scanning, repository collisions, blank event scan packages, accidental Snowflake worker-table access, or missing `entityManagerFactory` from a self-contradictory no-Hibernate fixture

## Remaining Risks

- These tests are old smoke tests with many disabled methods; after fixture isolation, some assertions may still deserve later cleanup, but that is a separate maintenance slice.
- Passing runtime fixtures are intentionally not redesigned in this issue unless implementation evidence shows they are still participating in the failing starter smoke contexts after the scoped applications are introduced.
