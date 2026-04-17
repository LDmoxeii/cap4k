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

These are the currently covered cases handled by explicit FQN precedence and project-registry fallback:

- `ProjectConfig.typeRegistry`
- `DesignSymbolRegistry`
- explicit FQN precedence

Coverage note:

- this bucket is limited to the subset already resolved by those mechanisms
- synthesized PK aliases and guessed same-package IDs are not included here

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/design/builders/TypeMappingBuilder.kt` (explicit-FQN / registry-fallback subset only)
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/imports/TypeResolver.kt`

### 2. Deterministic Derived-Reference Candidates

These are convention-owned names that can be derived from canonical design data plus framework naming rules, without runtime mutation:

- `Q<Entity>`
- generated peer references whose package and simple name are deterministically derivable from canonical source type metadata

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EntityGenerator.kt`

### 3. Deferred Shared-Runtime-State Cases

These include pure shared-runtime registration cases plus mixed cases that still contain deferred portions for this slice.

Deferred-bucket note:

- `AggregateWrapperGenerator` is the strongest clean example of runtime-registered dependence
- `FactoryGenerator`, `SpecificationGenerator`, and parts of `UniqueQueryGenerator` are mixed and not deferred as a blanket category in future slicing
- this first slice still defers their non-deterministic/runtime-coupled portions

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
