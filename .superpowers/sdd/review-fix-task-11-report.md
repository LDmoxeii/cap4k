# Task 11 Report: Fix JPA Existing Enrollment Runtime Semantics

## Status

DONE

## Implementation Summary

- `JpaUnitOfWork.applyExisting` now records the managed instance returned by `EntityManager.merge` and passes that managed instance to Hibernate dirty inspection and update listeners.
- `JpaHibernateDirtyInspector` no longer classifies an object without a Hibernate `EntityEntry` as dirty. Only managed entries participate in dirty-state comparison.
- `JpaGeneratedStrongIdSupport.completeExisting` validates the root own Strong ID before any completion, excludes the root from existing completion, preserves observed entities, and completes only unobserved reachable owned children.
- Repository observation baselines now retain the first observed identity per object identity. Existing enrollment validates the current identity against that baseline on both the initial `persist` call and the save-time preparation pass.
- Observed Strong ID fields are checked for both non-null presence and equality with the recorded baseline identity.
- Runtime coverage now includes clean and dirty managed entities, clean detached merge, dirty detached merge, managed listener identity, missing root Strong ID rejection, observed identity mutation rejection, and new-child-only Strong ID completion.

## TDD RED Evidence

### JPA unit test RED

Command:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --rerun-tasks --console=plain
```

Observed result before production changes:

```text
38 tests completed, 3 failed
BUILD FAILED
```

Expected failing tests:

```text
clean detached existing entity is inspected through its managed merge result
  PersistListenerManager.onChange(TestEntity(...), UPDATE) was called

EXISTING persist rejects an observed entity whose strong id changed
  Expected IllegalStateException to be thrown, but nothing was thrown

EXISTING persist rejects a missing root strong id before completing owned children
  Expected IllegalStateException to be thrown, but nothing was thrown
```

The command wrapper reached its 122.9 second timeout after Gradle had already completed the 38 tests and written the failing test report. The report and console output both showed the three expected behavioral failures.

### Hibernate runtime RED

The initial four-test runtime fixture, including the new dirty managed test, passed. Two detached scenarios were then added before production changes and the same focused command was rerun:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --rerun-tasks --console=plain
```

Observed result before production changes:

```text
6 tests completed, 2 failed
BUILD FAILED in 43s
```

Expected failing tests:

```text
clean detached existing enrollment does not emit update listener
dirty detached existing enrollment emits update listener for managed merge result
```

These failures confirmed that the detached source instance was being inspected and notified instead of the managed merge result.

## TDD GREEN Evidence

### JPA unit tests

Command:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --rerun-tasks --console=plain
```

Final output summary:

```text
38 tests completed, 0 failed
BUILD SUCCESSFUL in 9s
```

### Hibernate runtime tests

Command:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --rerun-tasks --console=plain
```

Final output summary:

```text
6 tests completed, 0 failed
BUILD SUCCESSFUL in 23s
```

The runtime suite explicitly passed these update-listener cases:

```text
dirty loaded existing enrollment emits update listener
clean detached existing enrollment does not emit update listener
dirty detached existing enrollment emits update listener for managed merge result
```

## Files Changed

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaHibernateDirtyInspector.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`
- `.superpowers/sdd/review-fix-task-11-report.md`

## Self-Review

- Confirmed `EXISTING` never fills a missing root own Strong ID and fails before completing an owned child.
- Confirmed an observed root and observed child retain their IDs while a newly added unobserved child receives its own Strong ID.
- Confirmed observed identity validation runs immediately during enrollment and again during save-time preparation, covering mutation after `persist` but before flush.
- Confirmed dirty inspection receives only managed instances and rejects candidates without a Hibernate persistence-context entry.
- Confirmed clean managed and clean detached entities do not emit `PersistType.UPDATE`.
- Confirmed dirty managed and dirty detached/merged entities do emit `PersistType.UPDATE`; detached listeners receive the managed merge result.
- Confirmed changes are scoped to Task 11 files plus this required report. The unrelated untracked review-fix plan file was not modified or staged.
- Confirmed `git diff --check` reports no whitespace errors.

## Concerns

- Focused Gradle commands still print pre-existing Kotlin/deprecation warnings in unrelated modules and fixtures. No new warning was introduced by Task 11.
- The first RED JPA command hit the external command timeout after the tests completed; the final GREEN command completed normally with exit code 0.

## Review Fix Loop

### Changes Requested

The Task 11 review identified three remaining Hibernate runtime gaps:

1. An uninitialized Strong ID Hibernate proxy exposed its identifier through the proxy lazy initializer while its inherited `@EmbeddedId` field remained null, causing a false missing-root-ID failure.
2. A managed proxy's Hibernate `EntityEntry` belonged to the initialized implementation object, so dirty inspection of the proxy object missed an UPDATE.
3. The last observed-identity validation occurred before transaction interceptors, allowing an interceptor to change an observed ID before merge/flush.

### Fix-Loop RED Evidence

JPA interceptor-order regression command:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --rerun-tasks --console=plain
```

RED output before production fixes:

```text
39 tests completed, 1 failed
EXISTING revalidates observed identity after beforeTransaction interceptors FAILED
Expected IllegalStateException to be thrown, but nothing was thrown
BUILD FAILED in 9s
```

Hibernate proxy regression command:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --rerun-tasks --console=plain
```

Initial RED output before production fixes:

```text
8 tests completed, 2 failed
existing enrollment accepts uninitialized strong id proxy FAILED
dirty managed proxy emits update listener for initialized implementation FAILED
java.lang.IllegalStateException: Existing-intent root ...StrongContent.id has missing Strong ID
BUILD FAILED in 31s
```

After only the proxy identifier fix, the command was rerun to isolate dirty inspection:

```text
8 tests completed, 1 failed
existing enrollment accepts uninitialized strong id proxy PASSED
dirty managed proxy emits update listener for initialized implementation FAILED
BUILD FAILED in 17s
```

This intermediate RED proved that proxy identifier resolution and proxy dirty normalization were independent defects.

### Fix-Loop Implementation

- `JpaGeneratedStrongIdSupport` now resolves a Strong ID from `HibernateProxy.hibernateLazyInitializer.identifier` before falling back to direct field access. Root and observed-entity validation share this resolution path.
- `JpaHibernateDirtyInspector` normalizes each proxy candidate to its initialized Hibernate implementation before looking up the persistence-context entry and running `findDirty`.
- `JpaUnitOfWork.applyExisting` revalidates the complete observed reachable graph immediately before root validation and merge, after `beforeTransaction` and `preInTransaction` interceptors have run.

### Fix-Loop GREEN Evidence

Final JPA command output:

```text
39 tests completed, 0 failed
BUILD SUCCESSFUL in 8s
```

Final Hibernate runtime command output:

```text
8 tests completed, 0 failed
BUILD SUCCESSFUL in 22s
```

New passing regression tests:

```text
EXISTING revalidates observed identity after beforeTransaction interceptors
existing enrollment accepts uninitialized strong id proxy
dirty managed proxy emits update listener for initialized implementation
```

### Fix-Loop Self-Review

- Proxy identifier validation does not require initializing an uninitialized reference.
- Dirty inspection returns the managed implementation to listeners, matching the Hibernate persistence-context entry that was actually inspected.
- The pre-merge validation executes after both interceptor phases and before any `EntityManager.merge` or flush call.
- Existing managed, detached, clean, dirty, missing-root, owned-child, and baseline identity tests remain green.
- No generator, planning documentation, CI, or PR body file was edited. The only non-code update is this required Task 11 report.
