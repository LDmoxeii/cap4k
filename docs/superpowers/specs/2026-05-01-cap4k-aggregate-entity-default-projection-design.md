# 2026-05-01 cap4k Aggregate Entity Default Projection Design

## Status

Draft for review.

## Background

The `only-danmuku-zero` dogfood migration exposed a generator-quality problem in aggregate entity construction.

Generated aggregate entities can become awkward to construct when every non-null column must be passed explicitly. The first instinct was to add broad Kotlin fallback defaults such as `String = ""`, `Long = 0L`, `Int = 0`, or enum fallback values such as `UNKNOWN` / `UNKNOW`.

That direction is incorrect for the framework.

Most aggregate fields are business data. If the database schema, design input, or type system does not define a default, the generator must not invent one. Otherwise code generation leaks fake domain semantics into user code. The old `only-danmuku` experience already showed this: enum fields that were business-required ended up needing artificial `UNKNOW` values only because generated constructors wanted a default.

This spec narrows the entity constructor default policy to stable, explicit inputs only.

## Problem

Aggregate entity constructor defaults currently need a stricter contract.

The generator must support convenient construction where the input model has a real default, while avoiding these invalid shortcuts:

- guessing audit or soft-delete fields from names such as `deleted`, `createTime`, or `updateBy`
- treating all primitive Kotlin types as safe to default
- treating enum fallback items as semantic defaults
- converting value objects without a declared construction contract
- silently changing database default semantics into a different Kotlin runtime expression

The contract must be deterministic enough that users can reason from schema to generated constructor signature.

## Goal

Define aggregate entity constructor default projection rules for:

- nullable columns
- relation collection fields
- scalar database defaults
- enum database defaults under cap4k's numeric enum value model
- unsupported or unsafe default expressions

The outcome should make generated entity constructors more useful without creating fake domain defaults.

## Non-Goals

This slice does not:

- add blanket fallback defaults for `String`, `Long`, `Int`, `Boolean`, or other primitive types
- detect technical fields by column or property name
- require users to define `UNKNOWN` / `UNKNOW` enum items
- add enum default configuration beyond database defaults
- add value-object default configuration
- change enum persistence semantics away from numeric values
- change analysis / drawing-board `defaultValue` projection
- restore old `cap4k-plugin-codegen` behavior

## Default Source Priority

Constructor defaults must be selected in this order:

1. Relation collection fields use collection initialization, for example `mutableListOf()`.
2. If a database `DEFAULT` exists and can be safely projected to Kotlin, use that projected value.
3. If there is no database default and the column is nullable, use `null`.
4. If the field is non-null and has no safely projectable database default, do not generate a constructor default.

Nullable and database default are not contradictory.

For a nullable column with `DEFAULT 0`, omitting the constructor argument should use `0`, while explicitly passing `null` should still mean `null`.

## Explicit Non-Defaults

The generator must not default these fields unless one of the priority rules above applies:

- non-null scalar business fields
- enum fields without a database default
- value-object or custom-type fields without a supported explicit default
- audit fields
- soft-delete fields
- foreign-key scalar fields

This means `customerId: Long`, `email: String`, `status: Status`, and `deleted: Long` remain required constructor arguments unless the schema says otherwise.

If a project wants `deleted = 0`, the schema should express it as `DEFAULT 0`, or a future explicit column-default configuration should be designed.

## Database Default Normalization

Database default values arrive from JDBC metadata as dialect-shaped text. The generator should normalize only simple scalar literals in the first implementation.

Supported normalization examples:

- `0` -> numeric literal `0`
- `'0'` -> numeric-looking string literal `0` when the target type is numeric or enum
- `''` -> empty string
- `'abc'` -> string literal `abc`
- `true` / `false` -> boolean literal
- `1` / `0` -> boolean literal when the target Kotlin type is `Boolean`
- `NULL` -> `null`
- simple redundant wrapping such as `(0)` -> `0`

Unsupported database default expressions must not be converted into guessed Kotlin expressions:

- `CURRENT_TIMESTAMP`
- `now()`
- `uuid()`
- generated column expressions
- SQL functions
- dialect-specific computed defaults

If an unsupported expression is encountered, the first implementation should leave the constructor argument required unless a more precise diagnostic path is available. It must not replace the expression with a local Kotlin approximation such as `System.currentTimeMillis()`.

## Scalar Kotlin Projection

For supported scalar defaults, projection should use Kotlin-ready expressions:

- `String` -> escaped Kotlin string literal
- `Boolean` -> `true` or `false`
- `Byte` / `Short` / `Int` -> integer literal
- `Long` -> integer literal with `L`
- `Float` -> decimal literal with `f`
- `Double` -> decimal literal

The projection should reject incompatible defaults, such as a non-numeric string for an integer column.

## Enum Default Projection

cap4k's generated enum model uses numeric enum values. Entity constructor default projection must follow that model.

Supported enum defaults:

- `DEFAULT 0`
- `DEFAULT '0'`
- equivalent normalized numeric literals

These should project to the enum value resolver, for example:

```kotlin
status: VideoStatus = VideoStatus.valueOf(0)
```

The generator should not project enum defaults by enum item name in the first implementation.

Unsupported enum defaults:

- `DEFAULT 'NORMAL'`
- `DEFAULT 'UNKNOWN'`
- any non-numeric string

Those defaults conflict with the framework's numeric enum persistence model and should fail fast with a clear error. They should not be guessed as `VideoStatus.NORMAL`.

If a numeric enum default does not match any known enum value, that should also fail fast. Generating `VideoStatus.valueOf(9)` and allowing runtime failure is not acceptable when the generator already has the enum manifest.

## Value Object And Custom Type Defaults

Value-object and custom-type defaults are out of scope for this slice.

Even if a database column has a scalar default, the generator cannot safely construct a value object without knowing that type's construction contract. A future slice may add explicit type-registry default expressions, but this slice should not infer them.

For now:

- nullable value-object fields without a projectable database default may default to `null`
- non-null value-object fields remain constructor-required
- database defaults on value-object fields are not projected unless the type is already represented as a supported scalar Kotlin type in the aggregate render model

## Failure And Diagnostics

The generator should prefer clear failure over invalid generated code when the model is internally contradictory.

Fail-fast cases:

- enum default is a non-numeric string
- enum numeric default does not match a known enum item
- scalar default cannot be converted to the target scalar Kotlin type
- default normalization would require guessing business semantics

Non-projected cases:

- SQL function or computed default where a Kotlin constructor default would change execution semantics
- value-object or custom-type default without an explicit construction contract

Non-projected cases should remain constructor-required. If a diagnostic channel is available, the generator should report that the database default was intentionally not projected.

## Examples

Nullable column without a database default:

```kotlin
description: String? = null
```

Nullable column with a database default:

```kotlin
status: Int? = 0
```

Non-null scalar column with a database default:

```kotlin
deleted: Long = 0L
```

Non-null scalar column without a database default:

```kotlin
customerId: Long
```

Enum column with numeric database default:

```kotlin
postType: PostType = PostType.valueOf(0)
```

Enum column without database default:

```kotlin
postType: PostType
```

Relation collection:

```kotlin
val videos: MutableList<CustomerVideoSeriesVideo> = mutableListOf()
```

## Testing Matrix

The implementation should include focused generator tests for:

- non-null scalar without DB default produces no constructor default
- nullable scalar without DB default produces `= null`
- nullable scalar with DB default prefers DB default over `null`
- numeric DB default projects to `Int` / `Long` correctly
- quoted numeric DB default projects to numeric Kotlin defaults
- string DB default escapes Kotlin strings correctly
- boolean DB defaults support boolean and numeric forms
- enum default `0` projects to `EnumType.valueOf(0)`
- enum default `'0'` projects to `EnumType.valueOf(0)`
- enum default `'NORMAL'` fails
- enum default numeric value missing from enum manifest fails
- relation collection initialization remains unchanged
- unsupported SQL function default is not converted into a Kotlin runtime approximation

## Impact

This is a generator-contract hardening slice. It should not affect bootstrap, analysis-core, analysis-compiler, or old codegen.

Likely affected areas are aggregate planning and aggregate entity rendering. The source DB provider may need default literal normalization only if the existing metadata model does not preserve enough information for the aggregate planner to decide safely.

The most important behavior change is philosophical: constructor defaults are not a convenience layer. They are projections of explicit input semantics.
