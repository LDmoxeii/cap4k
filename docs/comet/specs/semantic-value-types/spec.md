# Semantic value types

## Purpose

Cap4k shall model immutable structured values independently from how, where, or whether they are persisted. Value semantics are part of the core language; JSON/JPA conversion is an optional persistence projection.

The immediate consumer is issue #115's reusable owned-child creation value, but the semantic type contract shall not be Factory-specific.

## Core semantic contract

- A semantic value type shall have stable identity, package/scope, description, fields, nested type references, collection/nullability structure, and provenance.
- A semantic value type shall be immutable in its generated default shape.
- The type shall be reusable by more than one domain artifact, including checked-in Factory and Behavior code.
- A semantic value type may exist without being mapped to an Entity field or database column.
- Value equality and structural meaning shall not imply a persistence format.
- Type resolution shall use the same canonical symbol/identity rules as other cap4k-owned types and shall not rely on template-local short-name guessing.
- Canonical fields shall reference a resolved structured semantic type tree rather than retain a raw type string.
- The type tree shall represent stable named-type identity, container/generic structure, and nullability at the semantic node where each constraint applies.
- Source-native type evidence shall be resolved once at the compilation/assembly boundary. Generator planners, render models, and templates shall not parse type strings or perform a second symbol-resolution pass.
- A canonical named type shall carry resolved symbol identity/FQN evidence; a rendered short name shall remain a downstream presentation decision backed by explicit imports.
- A field default shall be part of the shared structured-value definition only when it is explicit and safely representable by the canonical default projector.
- Nullability shall describe the accepted value domain; it shall not by itself imply an omitted constructor argument or a generated `null` default.

## Building-block roles

- Command, Request, Response, Query, Event, Value Object, and Creation Intent shall remain distinct semantic roles.
- These roles shall share the same canonical structured-value definition for fields, nested values, collections, nullability, defaults, and referenced type identity.
- A Command or Event shall not become a persisted domain Value Object merely because its payload has value semantics.
- Intent, fact, routing, request/result direction, event persistence, and other building-block behavior shall remain owned by the corresponding semantic role.
- Generators shall consume the shared value definition for structural rendering and the owning role for behavior-specific planning.
- All owning roles shall reference the same resolved canonical type tree; role-specific models shall not duplicate alternative string-based field-type representations.

## Migration breadth

- Issue #115 shall migrate every existing structured building-block role to the shared canonical value definition and resolved type tree in one coherent change.
- The migration shall cover Command, Request, Response, Query, Client, Event, Value Object, Creation Intent, and root Factory payload field structures, including result structures where the owning role defines them.
- Each role shall retain its own identity, lifecycle, planner behavior, artifact family, annotations, persistence/routing semantics, and template ownership.
- The migration shall preserve the established generated API intent and checked-in hand-written workflow; it shall not rewrite existing checked-in artifacts merely because their canonical source changed.
- A second legacy canonical field structure based on unresolved `type: String` shall not remain for migrated roles.
- Source snapshots may carry unresolved/source-native evidence only until the compiler produces the shared canonical definition; Generator planners shall consume only the resolved definition.

## Persistence projection

- Persistence shall be an optional projection attached to a semantic value type rather than a mandatory property of all value types.
- Omitting the persistence declaration shall mean that the value type has no persistence projection.
- The source and compiler shall never infer JSON merely because persistence is omitted.
- JSON-backed Value Objects shall remain supported only through an explicit JSON persistence projection.
- The JSON projection shall continue to generate the approved JSON serialization and JPA `AttributeConverter` shape.
- A value type with no persistence projection shall generate no JPA converter, persistence annotation, column mapping, or storage requirement merely because it is a Value Object.
- Queryable relational/embedded persistence projections are outside issue #115 and require a separate confirmed change.
- The semantic and persistence-projection model introduced here shall not prevent later `RELATIONAL_EMBEDDED` or equivalent projections from being added without redefining value semantics.

## Projection artifact ownership

- The checked-in Value Object artifact shall contain only the immutable semantic value and user-owned domain behavior.
- The semantic value artifact shall use checked-in ownership with `SKIP` and shall not import Jackson, JPA, `ObjectMapper`, or `AttributeConverter` merely because an explicit projection exists.
- A JSON projection shall plan a separate build-owned generated converter artifact through a projection-specific template.
- The generated converter shall be refreshed, added, or removed with the explicit projection and shall not use checked-in conflict semantics.
- Entity JPA planning shall reference the converter's explicit canonical FQN rather than assuming a nested `<ValueObject>.Converter` class.
- A non-persistent Value Object shall plan no converter or other persistence adapter.
- Future relational/embedded projection artifacts shall remain separate from the semantic value template and are not defined by issue #115.

## Public authoring contract

- A manifest-authored Value Object shall declare persistence through an optional structured `persistence` object.
- Omitting `persistence` shall mean no persistence projection.
- JSON persistence shall be declared explicitly as `"persistence": { "kind": "json" }`.
- The former `storage` field and implicit JSON default shall be removed; the source shall not accept `storage` as a compatibility alias.
- Unknown persistence kinds and unsupported options shall fail with the Value Object identity and projection path.
- The structured object shall reserve projection-specific configuration space for a later relational/embedded design without defining that deferred mapping in issue #115.

## Manifest type-expression contract

- JSON/design manifest semantic fields shall use a formally specified Kotlin-style type expression.
- The expression shall carry nullability at every node, for example `List<Money?>?`.
- Manifest semantic fields shall not expose a second top-level `nullable` property.
- Named types may use explicit FQNs for disambiguation and otherwise follow the conservative canonical symbol-resolution contract.
- Source compilers shall parse and resolve the expression before canonical assembly. Raw expressions may remain provenance/diagnostic evidence but shall not be the Generator or renderer source of truth.
- Structured JSON type objects and mixed string/object type forms shall not be part of the public manifest syntax.

## Supported type algebra

- The first canonical type algebra shall contain scalar/built-in leaves, resolved named-symbol leaves, `List<T>`, `Set<T>`, and `Map<K,V>`.
- Every node shall support explicit nullability and every container argument may recursively contain another supported semantic type.
- `List` and `Set` shall require exactly one type argument; `Map` shall require exactly two.
- Mutable collections, generic `Collection` and `Iterable`, lazy `Sequence`, arrays, `Pair`/`Triple`, function types, variance, star projections, and arbitrary user-defined generic constructors shall be unsupported.
- Unsupported constructors, invalid arity, and malformed nested nullability shall fail during source compilation with the full semantic field path and original expression.
- A persistence projection may apply stricter validation to the resolved type tree. In particular, issue #115 does not promise that every core `Map` key shape is supported by JSON or a later relational projection.

## Owned-child creation value

- Issue #115 shall derive a reusable semantic value type from an owned child's resolved creation semantics.
- First-time generation shall derive such a value for every automatically scaffolded owned relation.
- The generated Kotlin type shall be a top-level `<EntityName>Creation` declaration in the aggregate's domain package, for example `DemandCargoLineCreation`.
- The aggregate domain package plus `<EntityName>Creation` shall be the stable semantic identity; the type shall not be nested under a Factory or placed in a Factory-specific/transport subpackage.
- Duplicate canonical type identity shall fail before rendering rather than trigger template-local renaming.
- Derived owned-child creation values shall recursively reference the semantic value types of owned descendants.
- Derived creation values shall use checked-in first-materialization ownership with `SKIP`, consistent with their intended reuse and handwritten evolution in domain code.
- The derived value shall have no persistence projection by default.
- Factory payload and Behavior/domain code shall reference the same generated value type.
- Derivation shall not require a duplicate manual field declaration made only to satisfy Factory generation.
- The aggregate root shall continue to use its nested `<RootName>Factory.Payload`; issue #115 shall not derive a top-level `<RootName>Creation` semantic value.
- An owned creation value shall be pure immutable data. Entity materialization and relation attachment shall not be part of the semantic value-type contract.
- Owned `MANY` references shall use `List<DescendantCreation> = emptyList()` and preserve input order.

## Existing behavior preservation

- Current in-repository JSON Value Object manifests shall be migrated to the new explicit JSON projection syntax as part of this breaking change.
- The current implicit `ValueObjectStorage.JSON` default shall be removed. No compatibility alias, legacy fallback, or omitted-field-to-JSON behavior shall remain.
- Current nested converter references shall be migrated to the separate generated JSON converter FQN; preserving the nested `<ValueObject>.Converter` class shape is not required.
- Current manifest fields using a separate `nullable` property shall be migrated to nullable type expressions; compatibility parsing is not required.
- Existing type registry, FQN identity, aggregate scope, nested collection, enum, Strong ID, and nullability resolution shall remain source-of-truth constraints.
- A JSON-backed Value Object shall not silently become non-persistent, and a non-persistent value shall not silently become JSON-backed.

## Failure behavior

- Missing or ambiguous type identity, unsupported nested type structure, incompatible persistence projection, and duplicate conflicting declarations shall fail before rendering.
- Diagnostics shall identify the semantic value type, field path, referenced type, and persistence projection when relevant.
- Templates shall not infer persistence from type names, packages, usage sites, or the presence of value semantics.
- An explicit field default that cannot be projected safely shall fail with type and field-path evidence rather than being discarded or rendered as an unchecked raw expression.

## Acceptance scenarios

### Non-persistent creation value

Given a derived `DemandCargoLineCreation` semantic value with business fields, generation produces an immutable reusable Kotlin value type without JPA converter or JSON storage code. Both `BookingDemandFactory` and `BookingDemandBehavior` can reference it.

### Existing JSON Value Object

Given a manifest-declared Value Object whose persistence projection explicitly selects JSON, generation preserves its immutable data shape and JSON/JPA converter.

Given a Value Object declaration that omits persistence, generation produces a non-persistent immutable value type and no converter. It does not apply the former implicit-JSON default.

### No implicit storage

Given a semantic value type without a persistence projection, generation does not infer JSON storage from its aggregate scope, fields, name, or use by an Entity, Factory, Behavior, Command, Query, or Event.

## Deferred contract boundary

- Queryable relational/embedded persistence for Value Objects is a known product concern explicitly deferred to a separate change.
