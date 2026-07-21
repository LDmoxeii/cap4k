# Cap4k ValueObject Runtime Removal

## Summary

This slice removes the legacy runtime `ValueObject<ID>` protocol and the runtime compatibility branches that still treat value objects as directly persisted or directly queried JPA objects.

The current value-object modeling path stays intact:

- plain Kotlin `data class`
- `@BuildingBlock(family = "value-object")`
- nested JPA converter for JSON-backed persistence
- generator-side value-object manifests and templates

This is a runtime contract cleanup, not a serialization-tool consolidation slice.

## Problem

The repository still carries a runtime-era `ValueObject<ID>` model that leaks into persistence, predicate building, and identifier generation.

Observed runtime coupling in `master`:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicate.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`

The old interface is no longer aligned with the current modeling direction. Value objects are now ordinary domain values, not standalone runtime identity objects.

## Goals

- Delete the runtime `ValueObject<ID>` interface.
- Remove all `ValueObject<*>` special casing from `JpaUnitOfWork`.
- Remove `JpaPredicate.byValueObject(...)` and the `valueObject` field from `JpaPredicate`.
- Delete `Md5HashIdentifierGenerator` and its starter configuration hook.
- Remove `supportValueObjectExistsCheckOnSave` and `generalIdFieldName` if they only exist for the deleted runtime path.
- Keep ordinary JPA repository behavior, aggregate factory support, `AggregatePayload`, `AggregateElement`, `Mediator.factories`, and the generator-side value-object concept.

## Non-Goals

- Do not unify fastjson, Jackson, or Gson in this slice.
- Do not introduce a replacement hash-based generator.
- Do not change generator-side value-object manifests, templates, or the current `data class` + converter output.
- Do not remove `ddd-domain-repo-jpa` as a module.
- Do not touch the aggregate-wrapper removal already merged into `master`, except where its disappearance makes local edits simpler.
- Do not add historical narration to `docs/public`; if a public page needs a wording update, it should describe only the current contract.

## Deletion Scope

### 1. Remove the old runtime interface

Delete:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/ValueObject.kt`

### 2. Remove JPA UoW value-object compatibility

In `JpaUnitOfWork`, remove:

- the `ValueObject` import
- `supportValueObjectExistsCheckOnSave`
- `isValueObjectAndExists(...)`
- the `is ValueObject<*> -> entity.hash()` branch in `isExists(...)`
- the `supportValueObjectExistsCheckOnSave && entity is ValueObject<*>` save branch

The remaining UoW logic should keep the ordinary entity path and the application-side-id path.

### 3. Remove predicate-level value-object entry points

In `JpaPredicate`, remove:

- the `valueObject: ValueObject<*>?` field
- `JpaPredicate.byValueObject(...)`
- any `ValueObject` import left behind after the removal

After this slice, repository predicates should expose only ordinary entity and specification paths.

### 4. Remove the md5 generator path

Delete:

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGenerator.kt`

Remove its test coverage in `ddd-domain-repo-jpa/src/test/...`.

Remove the starter wiring that configures it:

- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/configure/JpaUnitOfWorkProperties.kt`

If `ddd-domain-repo-jpa` no longer needs `fastjson` after the generator deletion, remove that dependency from `ddd-domain-repo-jpa/build.gradle.kts`.

### 5. Remove tests that only validate the deleted runtime contract

Delete or rewrite tests that exist only for the old interface or md5 generator behavior:

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/Md5HashIdentifierGeneratorTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/JpaPredicateSupportTest.kt`
- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt` cases that exercise the deleted value-object path

Keep tests that still cover ordinary JPA entity behavior, application-side-id behavior, and aggregate wrapper-free repository behavior.

## Preserved Contract

After this slice:

- value objects remain domain data types, not standalone runtime persistables
- JSON-backed value objects still use nested converters generated or handwritten at the value-object type
- ordinary entities still flow through the existing JPA repository and UoW path
- repository predicates still support ordinary entity ids and specifications
- generator-side value-object support remains available and should continue to render checked-in `data class` output with a nested converter

## Doc Rules

Public docs must stay present-tense.

If a public doc or example ever mentions the removed runtime interface, it should be rewritten to the current value-object model instead of explaining that an old concept was removed.

This slice should not add a new historical note to `docs/public`.

## Acceptance Criteria

- No main-code references remain to `ValueObject<ID>`, `JpaPredicate.byValueObject(...)`, `supportValueObjectExistsCheckOnSave`, `Md5HashIdentifierGenerator`, or `generalIdFieldName` in `ddd-core`, `ddd-domain-repo-jpa`, or `cap4k-ddd-starter`.
- `ddd-domain-repo-jpa` no longer keeps `fastjson` solely for `Md5HashIdentifierGenerator`.
- Generator-side value-object templates, manifests, and public value-object concept docs remain intact.
- The remaining runtime surface clearly treats value objects as ordinary domain values plus converters, not as standalone repository/UoW identities.
