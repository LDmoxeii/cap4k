# Type-Reference Audit

Date: 2026-04-17
Status: Draft

## Purpose

This document records the first active audit of old-codegen `typeMapping` usage for the `cross-generator type-reference parity` slice.

It classifies old usage sites into:

- already covered by current pipeline behavior
- deterministic derived-reference candidates
- deferred shared-runtime-state cases

## Categories

### 1. Already Covered by Current Pipeline Behavior

These are short-name or explicit-FQN cases already handled by:

- `ProjectConfig.typeRegistry`
- `DesignSymbolRegistry`
- explicit FQN precedence

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/design/builders/TypeMappingBuilder.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/imports/TypeResolver.kt`

### 2. Deterministic Derived-Reference Candidates

These are convention-owned names that can be derived without runtime mutation:

- `Q<Entity>`
- generated peer names whose package and simple-name convention are stable

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EntityGenerator.kt`

### 3. Deferred Shared-Runtime-State Cases

These are still tied to old execution-order registration and are not part of this first slice.

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/FactoryGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SpecificationGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueValidatorGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateWrapperGenerator.kt`

## First-Slice Conclusion

The first active parity slice should:

- reuse current registry behavior where already sufficient
- add only deterministic derived references
- defer all cases that still require shared mutable runtime registration
