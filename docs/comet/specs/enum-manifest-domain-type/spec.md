# Enum Manifest Domain Type

## Purpose

Cap4k shall model manifest-authored Business Enum declarations as stable, typed domain types that can carry explicit business properties in addition to their persisted integer identity and human-readable description. The generated enum class is first-materialized into checked-in domain source so project authors can add domain behavior without later generation overwriting it.

## Manifest contract

- `types.enumManifest` remains the authoring entry point and the manifest root remains a JSON array.
- Each enum entry shall declare `name`, `package`, optional `aggregates`, optional `fields`, and required `items`.
- Omitting `fields` shall preserve the existing `value` / `name` / `desc` item shape.
- `fields` shall be an ordered array of property declarations. Each declaration shall contain exactly a property `name` and a semantic `type` expression.
- `fields` defines property identity, type, and constructor order only. It shall not define a default value.
- Every item shall explicitly provide a value for every declared custom property. A nullable property shall use an explicit JSON `null`; omission shall not imply `null`.
- Every item shall continue to provide:
  - `value`: the stable persisted integer identity;
  - `name`: the Kotlin enum constant identity;
  - `desc`: the authoring description projected as generated `description`.
- Item properties not declared by `fields` shall be rejected rather than silently discarded.

## Property type and literal algebra

- The first supported custom-property types are `String`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInteger`, and `BigDecimal`, including explicit nullability where supported by the canonical type expression.
- A custom property may reference a resolved canonical enum type. Its item value shall be the exact referenced enum constant name encoded as a JSON string; canonical compilation shall resolve and validate the constant before planning.
- Integral and decimal JSON literals shall be validated against the declared target type and numeric range without passing unchecked source text to the renderer.
- Collections, `Map`, Value Object construction, arbitrary object construction, and raw Kotlin expressions are unsupported in this capability.
- Symbol identity, FQN resolution, literal validation, rendered type, and imports shall be decided before the template boundary. Pebble shall consume already validated typed context and shall not parse type expressions or infer literals.

## Canonical model

- Source snapshots and the canonical enum definition shall retain the ordered custom-property schema and a typed value for every item/property pair.
- Canonical enum equality and ambiguity checks shall compare enum identity, scope, built-in item members, ordered property schema, and all typed item values.
- Unknown item members or unresolved values shall never be dropped while compiling to the canonical model.
- `name` remains enum constant identity and shall not automatically become a constructor property.
- `value` remains the only built-in persisted numeric identity.
- `desc` remains the authoring key while canonical and generated APIs continue to expose `description`.

## Validation and diagnostics

Generation shall fail before rendering with enum, item, and property identity when any of the following occurs:

- a declared property is missing from an item;
- an undeclared property appears on an item;
- a property literal is incompatible with its declared type, nullability, or numeric range;
- a property name is duplicated, is not a valid Kotlin property identity, or conflicts with a reserved generated enum member;
- an enum constant name is duplicated or invalid;
- a persisted `value` is duplicated, non-integral, or outside the supported `Int` contract;
- a referenced canonical enum type or constant is missing or ambiguous;
- two declarations with the same canonical enum identity disagree on schema or item values.

Diagnostics shall identify the manifest/source path and the relevant enum, item, property, type, or value.

## Generated Kotlin API

- Declared custom properties shall become typed constructor properties after the existing `value: Int` and `description: String` properties, in manifest `fields` order.
- Each enum constant shall pass explicit constructor arguments for every declared custom property.
- The generated API shall preserve the existing enum FQN, `value: Int`, `description: String`, `valueOfOrNull`, and nested JPA `Converter` shape.
- The nested converter shall continue to persist and restore the enum through its stable integer `value`; custom properties shall not change the database representation.
- Custom properties may be consumed by project-authored methods, derived properties, and domain rules after first materialization.

## Artifact ownership and materialization

- Manifest-authored shared and aggregate-owned Business Enum Kotlin classes shall be planned as `CHECKED_IN_SOURCE` with effective `SKIP` conflict behavior under the domain module `src/main/kotlin` source root.
- The enum class shall no longer be a `GENERATED_SOURCE` artifact under `build/generated`.
- First generation may materialize a missing checked-in enum file. Once present, project source is the evolution authority and repeated plan/generate/generateSources runs shall not overwrite it.
- Cap4k shall not add managed regions, merge logic, patching, or automatic synchronization for an already materialized enum source file.
- Clean deletion of build directories shall not remove the checked-in enum or make the project uncompilable.
- Planner evidence, `plan.json`, AgentFacts ownership, descriptors, tests, Public Docs, and the authoring Skill shall project the same checked-in path, output kind, and conflict semantics.

## Compatibility and migration

- Existing manifests without `fields` remain valid and preserve their generated enum API and numeric converter behavior.
- Existing enum FQNs and persisted numeric values shall not change because ownership moves from build-owned output to checked-in source.
- No compatibility alias, duplicate build-generated enum, or second enum FQN shall be retained.
- Consumers adopting the new generator shall first materialize and commit the enum source; subsequent generation shall skip that file.
- Changing an already persisted enum `value` remains a business/data migration concern and is not automated by this capability.

## Acceptance scenarios

### Typed business property

Given a manifest enum with ordered `group: String` and `terminal: Boolean` fields, each item explicitly supplies both values and generation produces typed constructor properties and constants. Domain code can add and preserve behavior that consumes those properties.

### Explicit values only

Given a declared custom property, an item that omits it fails deterministically. A nullable property accepts an explicit `null` but omission does not inherit a default or imply `null`.

### Invalid item property

Given an undeclared item key, an incompatible literal, an unresolved enum constant reference, a duplicate property, or an invalid persisted value, source/canonical compilation fails with enum/item/property evidence before rendering.

### Checked-in evolution

Given an enum first materialized under domain `src/main/kotlin`, a project author adds a domain method. Re-running plan, generate, and generateSources preserves the file byte-for-byte and reports checked-in `SKIP` ownership.

### Persistence compatibility

Given a JPA field bound to the enum converter, an H2/JPA round trip continues to persist the integer `value` and restore the same enum constant even when the enum has custom properties.

### Legacy manifest

Given an existing enum manifest with no `fields`, generation preserves the current `value`, `description`, `valueOfOrNull`, and nested converter API while changing only the artifact ownership and source location.

## Cross-surface propagation

- Source provider, canonical assembler/catalog, type resolution, planner context, renderer template, generator descriptor, AgentFacts, public reference, authoring Skill, and functional/compile fixtures shall be updated together.
- Focused evidence shall include source validation failures, canonical equality/ambiguity, planner ownership/path, renderer output, repeated-generation preservation, JPA converter round trip, and a real Composite consumer using a custom business property.

## Non-goals

- Dynamic enums, database dictionaries, runtime mutation, arbitrary code expressions, collections/object-valued properties, automatic editing of existing checked-in enum source, converter extraction, or global removal of `conflictPolicy`.
