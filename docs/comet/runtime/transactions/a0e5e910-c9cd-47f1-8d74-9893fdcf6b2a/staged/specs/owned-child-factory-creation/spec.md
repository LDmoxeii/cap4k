# Owned-child Factory creation

## Purpose

The aggregate generator shall provide a first-class creation contract for owned child entities inside aggregate-root Factory creation while keeping child persistence identity and relation plumbing internal.

This capability specification covers only the product behavior requested by GitHub issue #115. External addon/source/generator extensibility and Unique-addon design are separate future changes.

## Requirements

### Root-owned creation boundary

- The generated creation contract shall be rooted at the aggregate root Factory.
- The aggregate root shall keep the current nested `<RootName>Factory.Payload` invocation contract; this capability shall not replace it with a top-level `<RootName>Creation` type.
- Root scalar creation fields and owned-relation creation fields shall be exposed through that nested payload. Owned-relation fields shall reference the corresponding top-level child `<EntityName>Creation` types.
- The nested root payload shall remain a Factory-specific aggregate entry contract, while top-level owned Entity creation values remain reusable by Factory, Behavior, and other aggregate-domain code.
- The Factory shall remain one checked-in source artifact protected by `SKIP`; this capability shall not split it into a build-owned base/contract and a checked-in implementation.
- The framework shall guarantee the complete initial Factory skeleton when the file is first materialized. It shall not guarantee automatic synchronization of an existing checked-in Factory after schema or model changes.
- Delete/regenerate, Git reconstruction, and patch workflows for an existing Factory shall remain user-owned operations rather than framework-managed merge behavior.
- Owned child creation shall produce child entities inside the same initial aggregate graph and attach them through the generated forward owned relation.
- The Factory shall return only the aggregate root and shall not create independent final Unit of Work entries for owned descendants.
- The capability shall not introduce a public standalone Factory for owned child entities.

### Child creation-intent inputs

- An owned-child input shall be a reusable immutable domain value representing creation intent, not an Entity snapshot, transport DTO, persistence record, or Factory-private nested DTO.
- Each owned Entity creation value shall be emitted as a top-level `<EntityName>Creation` type in the aggregate's domain package, for example `DemandCargoLineCreation`.
- The creation value shall not be nested under the Factory or placed in a Factory-specific, adapter, request, or transport subpackage.
- The aggregate domain package plus `<EntityName>Creation` shall be its stable canonical identity. A collision with another canonical or generated type shall fail before rendering.
- The generated type shall be usable from both the aggregate Factory and aggregate Behavior/other domain code without importing a nested Factory payload type.
- The type shall use the generalized core value-object/value-type semantic and shall have no persistence projection by default.
- The type shall not receive JSON storage or a JPA converter unless a separate explicit persistence contract applies.
- The caller shall not pass an owned child Entity instance directly as Factory payload data.
- The creation value shall remain pure immutable data and shall not generate `create()`, `toEntity()`, persistence, ID allocation, or owned-relation mutation behavior.
- Entity materialization from creation values shall remain owned by the aggregate root Factory; consuming the value from Behavior shall not create an independent child construction entry point.
- The input shall be derived from the owned child's canonical creation semantics rather than from handwritten field duplication or a mandatory second declaration made only for Factory generation.
- The input shall exclude the child's own persistence ID, structural parentRef column, ORM relation fields, provider-assigned identity/version, system-transition-only fields, and every field outside the child's resolved create write surface.
- Ordinary child creation fields shall preserve canonical Kotlin type, Strong ID reference, enum, nullability, and supported default semantics.
- Root `Factory.Payload` and owned `<EntityName>Creation` scalar fields shall render only explicit defaults accepted by the canonical default projector.
- Nullability alone shall not add a `null` default, and an ordinary collection field shall not receive an inferred empty default merely because it is a collection.
- Owned-relation defaults are explicit creation-graph policy rather than scalar inference: owned `ONE` remains `null`, and owned `MANY` remains empty.
- An explicit default that cannot be safely projected to the generated Kotlin contract shall fail with the semantic field path and unsupported default evidence instead of being silently ignored or emitted as a raw storage expression.

### Automatic relation scaffolding

- First-time Factory generation shall scaffold every owned relation present in the canonical aggregate graph without requiring a second explicit creation-participation declaration.
- Owned `ONE` shall scaffold as an optional child creation value with a `null` default.
- Owned `MANY` shall scaffold as `List<ChildCreation> = emptyList()`.
- The input order shall be preserved during deterministic child construction and attachment.
- The generated contract shall not widen owned `MANY` to `Collection`, `Iterable`, a mutable collection, array, set, sequence, or vararg.
- Relation cardinality, join-column nullability, unique constraints, and physical persistence requirements shall not be used to infer that a child is mandatory during aggregate creation.
- Handwritten Factory code may later remove, require, normalize, validate, or replace these defaults as business policy.
- Because the Factory is checked-in and protected by `SKIP`, a relation added after first materialization shall not silently widen the existing Factory contract.

### Recursive creation graph

- Automatic scaffolding shall recursively traverse the complete owned graph from the aggregate root.
- Each owned Entity shall have one reusable creation value type, and its owned relations shall reference the corresponding descendant creation value types.
- Recursive graph construction shall instantiate descendants from the leaves upward or through an equivalent deterministic algorithm and attach each child through its owner's generated forward relation facade.
- Owned graph cycles, conflicting multiple owners, duplicate semantic type identity, and unresolved targets shall fail before artifact rendering.
- The generator shall not truncate a supported owned graph at an arbitrary depth.

### Creation value artifact ownership

- Derived creation value types shall be checked-in source scaffolds protected by `SKIP`.
- The framework shall guarantee their first materialization from current canonical creation semantics.
- Later schema/model synchronization, handwritten field evolution, deletion/regeneration, Git reconstruction, and patch workflows shall remain user-owned.

### Identity lifecycle preservation

- Child ID strategy shall remain an Entity persistence/lifecycle concern and shall not become caller input.
- For application-side `uuid7`, current typed generated-own-ID allocation shall remain authoritative, including allocation through generated owned relation mutation. UUID7 is the only built-in application-side allocation strategy.
- For database identity, the child ID shall remain absent during construction and shall become observable through the existing root-oriented JPA save lifecycle.
- Missing, unknown, or storage-incompatible ID strategies shall continue to fail through the current source/canonical policy contract; the Factory generator shall not synthesize counters, random fallbacks, or placeholder IDs.

### Canonical planning evidence

- Factory planning shall consume a source-independent semantic model that distinguishes root scalar creation fields, owned relations, child creation-intent fields, relation cardinality, and graph attachment.
- Raw DB snapshots, Gradle properties, template-only maps, and handwritten Factory code shall not be the semantic source of truth for owned-child creation.
- Creation fields and relation-value references shall use the shared resolved canonical type tree; Factory planning and templates shall not reparse field type strings.
- Plan evidence shall identify the root, relation, child Entity, cardinality, included child input fields, excluded semantic roles, and the child ID policy used by the existing lifecycle.
- First materialization of a supported owned relation shall include the child value and Factory payload/graph-construction skeleton without handwritten payload surgery.
- After the checked-in Factory exists, `SKIP` preservation takes precedence over freshness; automatic update of that file is not part of this capability.
- Generated Factory construction code may use private helpers for readability, but those helpers shall remain Factory implementation details and shall not become public methods on creation values or standalone child factories.

### Failure behavior

- Planning shall fail deterministically when the canonical relation target, child write surface, child constructor requirements, or graph attachment cannot be resolved under the supported contract.
- Diagnostics shall identify the aggregate root, relation path, child Entity, and unresolved semantic requirement.
- The generator shall not respond to an unsupported mapping by exposing child IDs, restoring parentRef scalars, accepting Entity snapshots, or emitting an application-side counter workaround.

## Acceptance scenarios

### Booking demand with cargo lines

Given an aggregate root `BookingDemand` with owned-many relation `cargoLines` targeting `DemandCargoLine`, and given child business fields that are allowed for creation, the generated Factory creation contract includes a cargo-line creation-intent collection, maps each item to a new `DemandCargoLine`, and attaches every new child to the root-owned relation before returning the root.

The generated child input does not contain `DemandCargoLine.id`, the parent reference column, JPA relation objects, version, deleted marker, or other managed fields excluded by the resolved write surface.

The cargo-line creation value is a reusable aggregate-domain type that can also be referenced by `BookingDemandBehavior`; it is not nested under `BookingDemandFactory`.

### Application-side child ID

Given a child own ID strategy of `uuid7`, the Factory caller supplies no child ID. When the child is attached through the generated owned relation, the existing typed accessor allocates an ID if missing, and a preassigned internal ID remains unchanged.

### Database-side child ID

Given a child own ID strategy of database identity, the Factory caller supplies no child ID, the child remains without an ID immediately after graph construction, and the ID becomes observable only through the existing root save/cascade/flush lifecycle.

### Unsupported mapping

Given an owned relation whose target or construction requirements cannot be resolved from current canonical evidence, generation fails with a diagnostic naming the full relation path and missing semantic requirement. It does not generate a Factory that requires child-ID input or an ad hoc ID generator.

## Explicit exclusions

- No child ID exposure on commands, events, integration events, callbacks, idempotency keys, results, or follow-up lookup surfaces.
- No owned-child Entity instances as Factory payload inputs.
- No forced JSON storage or JPA converter solely because child creation input has value semantics.
- No public child Factory or child-level persistence API.
- No parentRef scalar or automatic inverse-navigation restoration.
- No change to approved `uuid7`, database identity, Unit of Work, or JPA allocation behavior.
- No downstream `booking-center` business-code patch as the framework solution.
- No redesign of `ArtifactAddonProvider`, external source/compiler registration, sealed source snapshots, Canonical Model addon namespaces, or user-contributed generator registration.
- No recreation of a Unique addon or restoration of the deleted aggregate Unique generation family.
- No build-owned Factory base/contract split.
- No automatic merge, managed region, patch, or freshness guarantee for an existing checked-in Factory.

## Deferred contract boundary

- Queryable relational/embedded Value Object persistence remains outside issue #115 and does not block owned-child Factory creation.
