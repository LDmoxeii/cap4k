# Outcome

Reconstruct the design for GitHub issue #115 against `master@d976c409`, replacing the outdated generator-semantic-model/addon draft with a current, implementation-ready product contract after the remaining user-visible decisions are confirmed.

# Scope

- Re-evaluate the missing aggregate-root Factory creation surface for owned child entities using the current Canonical Model, resolved per-entity write surfaces, owned-relation metadata, generated-own-ID lifecycle, and checked-in Factory artifact ownership.
- Define how a root creation input represents owned `ONE` and `MANY` child creation intentions without accepting child entity snapshots or exposing child persistence identity.
- Represent each child creation input as a reusable immutable domain value type that can be referenced by the checked-in Factory, aggregate Behavior, and other domain code; it is not a Factory-nested DTO.
- Generalize the core Value Object capability so value semantics are independent of persistence; JSON becomes one optional persistence projection rather than the definition of a Value Object.
- Keep Command, Request, Response, Query, Event, Value Object, and Creation Intent as distinct semantic roles while making them share one canonical structured-value definition and type-resolution system.
- Define how Factory construction attaches created children to the root-owned graph and how the result participates in the existing root-oriented Unit of Work lifecycle.
- Reconcile issue #115 with the post-issue Strong ID, parentRef, inverse-navigation, database-entrusted-field, Factory-default, and Unique-removal iterations.
- Define only the issue #115 owned-child Factory creation capability; broader external addon/source/generator extensibility is a separate future change.

# Non-goals

- Do not expose owned-child persistence IDs on commands, events, integration facts, callbacks, idempotency keys, command results, or follow-up lookup APIs.
- Do not make Factory payloads accept owned child entity instances directly.
- Do not force owned-child creation values through the current JSON-persisted value-object shape or generate a JPA converter merely because the type has value semantics.
- Do not create a separate one-off `CreationValueModel` with duplicated field and type-resolution rules.
- Do not implement relational/embedded/flattened queryable Value Object persistence in issue #115.
- Do not add a public child Factory or register children as independent final Unit of Work entries.
- Do not reintroduce generated parentRef scalar properties or automatic child-to-parent inverse navigation.
- Do not change the approved `uuid7`, `snowflake`, or database-identity allocation and persistence lifecycle.
- Do not modify downstream `booking-center` business code as the durable framework fix.
- Do not redesign `ArtifactAddonProvider`, external source/compiler registration, sealed source snapshots, Canonical Model addon namespaces, or user-contributed generator registration in this change.
- Do not recreate a Unique addon or restore the deleted aggregate Unique generation family.
- Do not split the current Factory into a build-owned contract/base plus a checked-in implementation.
- Do not promise that an existing checked-in Factory is automatically refreshed after its first materialization.
- Do not promise that an existing checked-in derived creation value is automatically refreshed after its first materialization.
- Do not add merge-region, patch, or managed-section machinery for checked-in Factory regeneration.
- Do not modify implementation before Shape is explicitly confirmed.

# Acceptance examples

- Given `BookingDemand` with an owned-many `cargoLines -> DemandCargoLine`, first-time generation exposes a reusable cargo-line creation value derived from the child's approved creation fields, references that value from the checked-in Factory payload, excludes child ID and structural persistence fields, constructs `DemandCargoLine` instances, and attaches them through the root-owned relation during aggregate creation.
- Given an owned `ONE` relation, first-time scaffolding uses an optional creation value with a `null` default. Given an owned `MANY` relation, first-time scaffolding uses a creation-value collection with an empty default.
- Given handwritten aggregate Behavior in the same domain model, it can accept or reuse the generated cargo-line creation value without depending on a nested `BookingDemandFactory` DTO.
- Given a multi-level owned graph, each owned Entity has one reusable creation value and that value recursively references creation values for its own owned descendants.
- Given an owned child with application-side `uuid7` or `snowflake` own ID, attaching the new child through the generated owned relation preserves the current typed `GeneratedOwnIdAccessor.assignIfMissing` lifecycle; the child ID is not supplied by the Factory caller.
- Given an owned child with database identity, the child is constructed without an ID, remains unassigned immediately after Factory creation, and receives its ID through the existing root-oriented JPA save lifecycle.
- Given child fields classified as parentRef, provider-assigned identity/version, system-transition-only, ORM relation, or otherwise outside the resolved create write surface, those fields do not appear in the child creation-intent input.
- Given a relation or constructor shape that the supported contract cannot map safely, planning fails with current canonical evidence instead of generating an ad hoc ID workaround or silently requiring handwritten payload surgery.

# Constraints and invariants

- `FactoryArtifactPlanner` currently plans only aggregate roots and derives only root scalar payload fields from `ResolvedWriteSurfacePolicy.createAllowedFields`.
- `CanonicalModel` currently contains per-entity fields, resolved write surfaces, Strong ID policies, and forward owned relations, but no first-class aggregate creation graph or owned-child creation-intent model.
- Factory is a checked-in source artifact and uses the configured conflict policy, whose default is `SKIP`; its payload and handwritten creation logic currently share one file. Any durable design must explicitly settle generated versus handwritten ownership instead of assuming regeneration can overwrite an existing Factory safely.
- For this capability, checked-in Factory ownership intentionally means first-materialization scaffolding only. `SKIP` protects all later handwritten edits; delete/regenerate, Git-based reconstruction, or patch workflows remain user-owned operations rather than framework promises.
- The current `ValueObjectModel` is not a neutral structured-value abstraction: it requires `ValueObjectStorage.JSON`, and its template emits a JPA `AttributeConverter`. Reusing it unchanged for child creation input would incorrectly attach persistence/storage semantics.
- Commands, requests, responses, queries, and events already carry immutable field structures, but their Canonical Model types currently duplicate structure without a shared semantic value-type abstraction.
- Current generated Schema fields use `root.get(fieldName)` against one JPA attribute. A JSON-converted Value Object is therefore queryable only as that whole converted attribute through portable Criteria semantics; its nested members are not separately addressable as relational Schema fields.
- Queryable nested Value Object persistence would require an explicit relational/embedded projection, physical column binding, generated JPA shape, and nested Schema path contract. Optional persistence projection creates the correct boundary but does not itself deliver that behavior.
- Generated Entity relation facades already allocate missing application-side child IDs on `add`/non-null `replace`; JPA/UoW already supplies database identity and acts as a backstop for generated own IDs.
- The root remains the aggregate creation and final Unit of Work entry boundary.
- Physical parentRef metadata remains relation/cardinality evidence but is not a child domain scalar.
- The aggregate generator no longer contains Unique Query/Handler/Validator planners. Physical unique metadata remains only for storage and relation inference.
- `ArtifactAddonProvider` remains a ServiceLoader-based, planning-only projection SPI. Built-in source and generator registration remains fixed by the Gradle plugin, and arbitrary source snapshots remain closed by the sealed snapshot/canonical assembler boundary.

# Decisions

- GitHub issue #115 remains open and has no follow-up comments; its original child-ID premise must be reconciled with later accepted ID lifecycle contracts rather than copied verbatim.
- Application-side own IDs are now supported for every eligible generated entity, including owned children, using entity-specific Strong IDs with `uuid7` or `snowflake`; database identity remains a separate supported strategy.
- Owned-child parentRef scalar projection and unconditional inverse navigation have already been removed from the current model.
- Aggregate Factory is now always planned, but owned-child Factory input remains deliberately outside the latest completed official-default-project iteration.
- Unique generation was removed from the aggregate mainline without creating a Unique addon in that iteration. The outdated draft's proposed Unique-addon migration is not current implementation direction by default.
- The outdated 2026-07-28 draft is historical discussion evidence, not an approved implementation contract.
- The user confirmed that this replacement change is limited to issue #115. Addon/source/generator extensibility and Unique-addon design are explicitly deferred to separate future changes.
- The user selected the existing single checked-in Factory model. Factory remains `CHECKED_IN_SOURCE` with `SKIP`; the framework guarantees the initial generated skeleton, not automatic synchronization of an existing handwritten file.
- Users may deliberately delete and regenerate, reconstruct through Git, or apply patches, but those workflows are outside the framework contract.
- Child input is a reusable immutable value object/value type for both Factory and Behavior, not a Factory-specific nested input DTO.
- The user selected a generalized core Value Object semantic with optional persistence projection rather than a separate `CreationValueModel`.
- Existing JSON-backed Value Objects remain supported as Value Objects with a JSON persistence projection. A child creation value has no persistence projection and therefore receives no JPA converter merely because it has value semantics.
- There is no external-user compatibility requirement for the current manifest default. Persistence must become explicit: omitting persistence means no persistence projection and must never continue to imply JSON.
- Existing in-repository JSON Value Object declarations shall be migrated to an explicit JSON projection. The old implicit-JSON default and any compatibility alias/fallback are not part of the target contract.
- Public Value Object authoring replaces `storage` with an optional structured `persistence` object. A JSON-backed declaration uses `"persistence": { "kind": "json" }`; omitting `persistence` creates a non-persistent value type.
- The old `storage` field is removed rather than accepted as an alias. The structured projection shape is intentionally extensible for a later separately designed relational/embedded mapping contract.
- The semantic Value Object declaration is a checked-in `SKIP` artifact containing only the immutable value and user-owned domain behavior. JSON/JPA support is emitted as a separate build-owned generated artifact and template.
- A non-persistent value produces no projection artifact. Adding, changing, or removing an explicit projection updates generated adapters without rewriting the checked-in value file.
- The semantic value template shall not import Jackson/JPA or branch on persistence. Entity JPA planning references the generated converter's explicit FQN rather than assuming a nested `<ValueType>.Converter`.
- Canonical semantic fields use a resolved structured type tree rather than `type: String`. The tree records stable named-type identity, container/generic structure, and nullability at the relevant node.
- Source collection may preserve source-native unresolved type evidence, but the compilation/assembly boundary resolves it exactly once. Canonical Model, Generator planners, render models, and templates may not reparse strings or perform short-name guessing.
- Command, Request, Response, Query, Event, Value Object, Creation Intent, Factory payload, and derived creation-graph fields consume the same canonical type tree while retaining their distinct semantic roles.
- JSON/design manifest authors use one formally specified Kotlin-style type expression, for example `List<Money?>?`. The expression carries nullability at every node and may use explicit FQNs for disambiguation.
- Manifest semantic fields remove the separate `nullable` property so nullability cannot conflict with the type expression. Source compilers parse the expression and resolve symbols before canonical assembly; generators never consume the raw expression.
- The first canonical semantic type algebra is closed: scalar/built-in leaves, resolved named symbols, `List<T>`, `Set<T>`, and `Map<K,V>`, with recursive nesting and nullability on every node.
- Container arity is validated. Mutable collections, generic `Collection`/`Iterable`, lazy `Sequence`, arrays, tuples, functions, variance/star projections, and arbitrary generic constructors are unsupported and fail before generation.
- Persistence projections may impose stricter rules over the common type tree, such as supported JSON map-key shapes, without weakening or duplicating the core semantic type contract.
- Issue #115 performs the canonical migration for every existing structured building-block role in one change: Command, Request, Response, Query, Client, Event, Value Object, Creation Intent, and root Factory payload structures all reference the shared semantic value definition/type tree.
- The migration changes internal source-compilation and canonical/planner contracts, not the distinct role behavior, public generated artifact intent, checked-in ownership, or established command/query/client/event/Factory hand feel.
- No parallel legacy canonical `FieldModel.type: String` path remains for migrated semantic structures. Source-native snapshots may retain unresolved evidence only until the compiler produces the shared canonical definition.
- Command, Request, Response, Query, Event, Value Object, and Creation Intent remain distinct semantic building blocks. They share a canonical `SemanticValueType`/structured-value definition rather than all being reclassified as Value Objects.
- Message semantics such as intent, fact, routing, result direction, and persistence remain on their owning building block; field structure, nesting, collection shape, nullability, and type identity come from the shared value definition.
- Queryable relational/embedded Value Object persistence is deferred to a separate change. Issue #115 establishes only the optional projection boundary and preserves current JSON behavior.
- First-time Factory generation automatically scaffolds every owned relation known to the canonical aggregate graph.
- Persistence ownership/cardinality does not prove creation requiredness. Owned `ONE` inputs default to optional/`null`; owned `MANY` inputs default to an empty collection.
- The checked-in Factory may later remove, require, normalize, or otherwise change these scaffolded inputs as handwritten business policy. `SKIP` prevents later schema additions from silently widening the existing Factory.
- Automatic creation scaffolding recursively traverses the complete owned aggregate graph rather than stopping at direct root children.
- Each owned Entity receives one reusable checked-in creation value scaffold. Nested relations reference the descendant Entity's creation value.
- Ownership cycles, conflicting multiple owned parents, repeated type identity, and unresolved relation targets fail during canonical creation-graph planning.
- Creation value scaffolds use `CHECKED_IN_SOURCE + SKIP`: the framework owns first materialization, while later handwritten evolution and synchronization are user-owned.
- Each owned Entity creation value is emitted as a top-level `<EntityName>Creation` type in the aggregate's domain package, for example `DemandCargoLineCreation`. It is not nested under the Factory and does not live in a Factory-specific or transport-specific subpackage.
- The aggregate domain package plus `<EntityName>Creation` is the stable semantic identity. Any collision with another canonical/generated type fails before rendering rather than being disambiguated through template-local renaming.
- The aggregate root keeps the current nested `<RootName>Factory.Payload` invocation contract. Issue #115 adds owned-relation fields referencing top-level child creation values to that payload; it does not introduce a top-level `<RootName>Creation` type or replace the existing Factory API.
- Root `Factory.Payload` remains Factory-specific because it is the aggregate creation entry contract. Owned Entity creation values are top-level because they are reusable construction values for Factory, Behavior, and other aggregate-domain code.
- Owned `<EntityName>Creation` types are pure immutable data values. They do not generate `create()`, `toEntity()`, persistence, relation mutation, or ID-allocation behavior.
- The root checked-in Factory exclusively owns generated Entity materialization and recursive owned-graph attachment. Reusing a Creation value from Behavior does not expose an independent child construction boundary.
- Root `Factory.Payload` and owned `<EntityName>Creation` scaffolds propagate only explicit defaults that the canonical default projector can render safely. Nullability by itself never adds `= null`, and ordinary collections do not receive an inferred empty default merely because they are collections.
- The automatic relation defaults remain the deliberate exception: owned `ONE` is `null`, and owned `MANY` is empty, as already confirmed for first-time aggregate creation scaffolding.
- Owned `MANY` creation fields use the stable Kotlin shape `List<ChildCreation> = emptyList()`. The contract preserves input order and uses a read-only value-oriented collection rather than `Collection`, `Iterable`, mutable collections, arrays, sets, or varargs.
- An explicit default that cannot be represented by the supported canonical/Kotlin projection fails with field-path evidence rather than being silently discarded or copied as a raw database expression.
- Solving this core semantic type gap does not by itself require external SourceProvider/GeneratorProvider registration or addon model namespaces. Those become necessary only if third-party providers must contribute new languages or private semantic models.

# Open questions

- None. The user approved this consolidated Shape on 2026-07-28 and authorized Build planning/implementation.

# Verification expectations

- Focused API/core tests must prove any new semantic creation model is source-independent and preserves current field-role and relation evidence.
- Aggregate/type planner and renderer tests must prove reusable child value generation, Factory and Behavior type visibility, child input exclusion rules, owned-one/owned-many mapping, graph attachment, deterministic diagnostics, and no child-ID input.
- Value-type tests must prove a type can exist without persistence, JSON-backed Value Objects retain converter behavior, and persistence projection is not inferred merely from value semantics.
- Materialization tests must prove first generation produces the complete checked-in Factory skeleton and later `SKIP` preserves user edits without claiming automatic freshness.
- Functional generation/compile fixtures must cover at least the `BookingDemand -> DemandCargoLine` shape plus both application-side and database-side child IDs.
- Runtime evidence must prove the generated graph enters the existing root-oriented lifecycle without independent child persistence or exposed child identity.
- Static checks must prove Unique mainline generation, parentRef scalar projection, inverse navigation, and ad hoc application-side counters are not reintroduced.
