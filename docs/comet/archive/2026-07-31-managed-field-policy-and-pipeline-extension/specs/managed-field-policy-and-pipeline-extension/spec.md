# Managed Field Policy And Pipeline Extension

## Purpose and relationship to existing capabilities

Cap4k shall represent every infrastructure-owned persistent field through one exact managed-field policy reference, resolve that policy into canonical semantics before generator planning, and use generated metadata to execute the resolved contract at runtime.

Cap4k shall also expose one Pipeline Extension installation model for approved build-time contributions. Managed-field policy definitions and Artifact Addons shall be typed contributions to that model rather than independently discovered installation roots.

This capability shall complement rather than replace the existing application-execution/UoW, owned-child Factory creation, and semantic-value-type capabilities. In particular:

- one outer REQUIRED Command transaction and one UoW Context remain authoritative;
- candidate recognition still precedes enrichment and final dirty recognition;
- owned children participate through the aggregate graph and do not originate Domain Events;
- checked-in Factory and Behavior artifacts remain first-materialization plus `SKIP`;
- semantic value types remain independent of persistence projection;
- JPA/Hibernate remains the only persistence implementation for this capability.

## DB source contract

### Exact managed annotation

The active database column comment contract shall be:

```text
@Managed=<policy-key>
```

A policy key shall match:

```text
[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*
```

Policy keys shall be case-sensitive lowercase kebab-case segments separated by dots. The source shall preserve the exact key and source location without converting it into a broad role, identifier strategy, JPA flag, constructor rule, or template value.

The active source contract shall remove, without compatibility aliases:

```text
@IdStrategy
@Inherited
@Managed=system
@Managed=scope
@Managed=deleted
```

`@Managed=version` shall remain valid only as the exact built-in `version` policy. Soft deletion shall use `@Managed=soft-delete`.

The parser shall strip only supported annotations from cleaned comments. Removed, malformed, duplicated, or unsupported annotations shall fail rather than disappear silently.

### Physical schema facts

The DB source shall retain physical primary-key, column, JDBC type, nullability, default-expression, uniqueness, generated-column, and relation evidence independently from managed policy selection.

A source column shall carry a nullable raw `managedPolicyKey`. The source model shall remove `idStrategy`, `managedRole`, and `inherited` after active callers migrate.

An `identifier.*` policy shall be valid only on a physical primary-key column. A physical primary-key column shall resolve exactly one identifier policy through:

```text
explicit column policy
  -> configured identifier default policy
  -> failure
```

A non-primary-key column using an identifier policy, a primary-key column using a non-identifier policy, or a table with more than one physical primary-key column shall fail canonical resolution. Cap4k shall not infer database identity merely from JDBC auto-increment metadata.

### Project defaults and provenance

Project defaults shall select exact policy keys. The supported precedence shall be:

```text
explicit column @Managed
  -> exact configured column policy default
  -> role-specific default such as identifier default
  -> no managed policy or failure when one is required
```

Canonical resolution shall record why a field selected a policy separately from who defined that policy. Selection provenance shall distinguish at least explicit column annotation, exact column default, and identifier default. Definition ownership shall distinguish built-in definitions from `(extensionId, contributionId)` definitions.

## Pipeline Extension contract

### Installation root and typed contributions

The build-time installation root shall be a `PipelineExtensionProvider` with a versioned `PipelineExtensionDescriptor` and a list of typed contributions. The initial allowed contribution types shall be:

- `ArtifactAddonProvider`;
- `ManagedFieldPolicyProvider`.

Unknown contribution types shall fail loading. A marker interface shall not grant permission to insert arbitrary execution hooks.

Providers shall be discovered through:

```text
META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
```

The build-time dependency bucket shall be `cap4kPipelineExtension`. The old `cap4kAddon` configuration and direct `ServiceLoader<ArtifactAddonProvider>` root shall be removed without aliases.

Repository configuration shall address extension provider ID, contribution ID, and contribution-scoped options independently. The canonical contribution identity shall be `(extensionId, contributionId)`. Contribution IDs shall be unique within one extension across all contribution types. Artifact Addon IDs shall additionally remain globally unique because their preserved resource namespace does not contain the extension ID.

### Contribution authority

A Managed Field Policy contribution shall return declarative policy definitions from immutable project configuration and its own options. It shall not mutate a source snapshot, mutate an existing Canonical Model, plan unrelated artifacts, write files, or insert a pipeline stage.

An Artifact Addon contribution shall retain post-canonical artifact planning and the stable template namespace:

```text
addons/<artifact-addon-id>/<path>
```

An extension may contribute both capability types. Each contribution shall retain its own boundary and diagnostics.

### Loader and dependency rules

The initial loader shall use one task-scoped `URLClassLoader` for the resolved `cap4kPipelineExtension` classpath, with the Cap4k API classloader as parent. It shall:

- discover providers once from the complete resolved classpath;
- validate SPI version before contribution use;
- validate extension, contribution, and global Artifact Addon identity rules;
- build typed contribution registries;
- validate configured IDs against discovered descriptors;
- preserve Artifact Addon option routing and template ownership;
- remain a Gradle `@Classpath` task input;
- close the classloader after the task run.

A non-empty resolved extension classpath that yields no provider shall fail. A provider may come from a transitive component; the initial implementation shall not promise one provider per direct dependency or one classloader per extension.

Build-time extension dependencies and application runtime dependencies shall remain explicit and separate. Pipeline Extensions shall not mutate the application's runtime dependency graph.

## Canonical managed-field model

### Policy definition

A policy definition shall declare:

- one exact policy key;
- a managed-field role;
- default creation-input policy;
- explicit-value policy;
- a non-empty lifecycle set;
- optional handler qualifier and slot;
- a non-null semantic type reference;
- optional value-adapter qualifier;
- operation-specific INSERT and UPDATE value authority.

Source-specific field name, column name, target type, nullability, primary-key status, and selection provenance shall come from the resolved field, not from the reusable definition.

The supported roles shall be:

```text
IDENTIFIER
VERSION
SOFT_DELETE
SCOPE
INITIALIZATION
ENRICHMENT
DATABASE_GENERATED
```

Roles shall support validation and diagnostics. Runtime execution shall be selected by lifecycle, operation authority, and qualifier rather than property-name convention.

### Creation input and explicit values

The default creation-input policies shall be:

```text
OMIT
OPTIONAL
REQUIRED
```

They shall control only the first generated checked-in Factory input. They shall not prescribe the entity constructor, prohibit user-edited Factory code, or decide whether JPA writes the column.

The explicit-value policies shall be:

```text
PRESERVE_IF_VALID
REQUIRE
REQUIRE_CONTEXT_MATCH
OVERWRITE
FORBID
```

The definition shall select ownership behavior but shall not contain executable validation code. Generic runtime code shall validate declared semantic/target types and nullability. The owning runtime component shall validate policy-specific absence, identifier version/range, sentinel, context equality, or other semantic rules.

`PRESERVE_IF_VALID`, `REQUIRE`, and `REQUIRE_CONTEXT_MATCH` shall require `ENTITY_ADMISSION`. `OVERWRITE` shall require either `ENTITY_ADMISSION` or `PERSISTENCE_ENRICHMENT`. `FORBID` may be used at any lifecycle, subject to provider/database placeholder rules.

### Lifecycles and semantic type

The supported lifecycles shall be:

```text
ENTITY_ADMISSION
PERSISTENCE_ENRICHMENT
PERSISTENCE_PROVIDER
DATABASE
```

Every lifecycle set shall be non-empty. One policy shall not combine `ENTITY_ADMISSION` with `PERSISTENCE_ENRICHMENT`; separate exact policies shall represent those different visibility and candidate semantics. Admission may coexist with provider or database ownership when different operations have different owners.

There shall be no public construction callback. A generated Factory may prepare a value before constructor invocation, but runtime Initializers shall execute at aggregate admission rather than inside arbitrary constructors.

Every policy shall use one non-null semantic type reference:

- `TargetField`, meaning the resolved entity field type;
- `FixedFqn`, naming one stable semantic JVM type.

Audit time shall use `java.time.Instant` semantic values. Storage and external serialization formats shall remain separate adaptations.

### Operation-specific value authority

The supported value authorities shall be:

```text
CALLER
FRAMEWORK
MANAGED_HANDLER
PERSISTENCE_PROVIDER
DATABASE
NONE
```

Value authority shall identify who owns a semantic transition for INSERT or UPDATE. It shall not be a direct alias for JPA `insertable` or `updatable`.

Canonical resolution shall enforce:

| Operation | Authority | Required owner |
|---|---|---|
| INSERT | `CALLER` | Admission Initializer validates the supplied value |
| UPDATE | `CALLER` | Invalid for a managed field |
| INSERT | `FRAMEWORK` | Built-in admission Initializer |
| UPDATE | `FRAMEWORK` | Invalid; use handler or provider ownership |
| INSERT | `MANAGED_HANDLER` | Admission Initializer or persistence Enricher, but not both |
| UPDATE | `MANAGED_HANDLER` | Persistence Enricher |
| INSERT/UPDATE | `PERSISTENCE_PROVIDER` | Provider lifecycle |
| INSERT/UPDATE | `DATABASE` | Database lifecycle |
| INSERT/UPDATE | `NONE` | No transition permitted |

Extension-defined runtime policies shall use `MANAGED_HANDLER`; `FRAMEWORK` shall be reserved for built-in policies with a standard Cap4k owner. Every non-`NONE` operation authority shall have exactly one executable owner.

### Handler qualifiers and slots

Policy keys shall be exact definitions; prefix matching shall not be used.

A lifecycle requiring application runtime behavior shall declare a nonblank handler qualifier. A qualifier shall belong to exactly one Handler kind across the application: admission Initializer or JPA persistence Enricher.

For one entity and one qualifier:

- a single field may have a null slot;
- multiple fields shall all have nonblank, unique slots;
- slotted and unslotted fields shall not be mixed.

Slots shall coordinate fields only inside one Handler kind. Separate qualifiers shall have no guaranteed semantic order, and Spring `Ordered`, `@Order`, or injection-list order shall not define managed-field behavior.

## Standard policy catalog

### Identifier policies

Cap4k shall provide:

| Policy key | Qualifier | Factory input | Explicit value | Lifecycles | INSERT | UPDATE |
|---|---|---|---|---|---|---|
| `identifier.uuid7` | `identifier.uuid7` | `OMIT` | `PRESERVE_IF_VALID` | admission | framework | none |
| `identifier.snowflake` | `identifier.snowflake` | `OMIT` | `PRESERVE_IF_VALID` | admission | framework | none |
| `identifier.assigned` | `identifier.assigned` | `REQUIRED` | `REQUIRE` | admission | caller | none |
| `identifier.database-identity` | none | `OMIT` | `FORBID` | database | database | none |

UUID7 admission shall reject a non-version-7 UUID. Snowflake admission shall validate the configured Strong ID backing type, range, and strategy output. Assigned identity shall require a caller-provided value and allocate nothing. Database identity shall reject ordinary explicit identity input and remain absent until provider/database assignment.

The application-side identifier policies shall use generated target-field-specific allocation and validation support. A valid explicit application identifier shall be preserved, while absence shall be allocated no later than admission. Once admitted, entity identity shall be immutable.

### Provider and database policies

Cap4k shall provide:

| Policy key | Role | Qualifier | Input | Explicit value | Lifecycles | INSERT | UPDATE |
|---|---|---|---|---|---|---|---|
| `version` | version | none | omit | forbid | provider | provider | provider |
| `soft-delete` | soft delete | `soft-delete` | omit | preserve-valid | admission + provider | framework | provider |
| `database.generated-on-insert` | database generated | none | omit | forbid | database | database | none |
| `database.generated-always` | database generated | none | omit | forbid | database | database | database |

Soft delete shall preserve the existing generated active-sentinel initialization and INSERT participation. Admission shall preserve and validate only the active sentinel and reject a new entity already carrying a tombstone. Repository removal plus provider projection shall own the later tombstone transition.

### Runtime-handler policies

Cap4k shall define these standard semantics:

| Policy key | Qualifier | Slot | Input | Explicit value | Lifecycle | INSERT | UPDATE |
|---|---|---|---|---|---|---|---|
| `scope.tenant` | `scope.tenant` | none | omit | context-match | admission | handler | none |
| `initialization.request-context` | `initialization.request-context` | none | omit | overwrite | admission | handler | none |
| `enrichment.audit-time.created-at` | `enrichment.audit-time` | `created-at` | omit | overwrite | enrichment | handler | none |
| `enrichment.audit-time.updated-at` | `enrichment.audit-time` | `updated-at` | omit | overwrite | enrichment | handler | handler |
| `enrichment.audit-actor.created-by` | `enrichment.audit-actor` | `created-by` | omit | overwrite | enrichment | handler | none |
| `enrichment.audit-actor.updated-by` | `enrichment.audit-actor` | `updated-by` | omit | overwrite | enrichment | handler | handler |

Cap4k shall provide the audit-time Enricher because the outer UoW owns a stable `Instant`. A project or runtime integration shall supply tenant, request-context, and audit-actor values. Use of one of those policies without exactly one runtime owner shall fail startup.

Extension-defined policies may cover identifiers, scope, initialization, or enrichment when they fit the current canonical and JPA capabilities. They shall not gain permission to replace repositories, Query Schema, UoW, or persistence backend behavior.

## Canonical resolution and generator planning

### Fixed resolution phase

The fixed canonical flow shall be:

```text
DB source snapshot with raw policy key
  -> collect built-in and extension policy definitions
  -> validate exact-key uniqueness
  -> apply explicit/default selection precedence
  -> attach selection provenance and definition ownership
  -> validate schema role, field type, lifecycle, authority, qualifier, slot, and adapter requirements
  -> produce one resolved managed-field policy
  -> expose the completed Canonical Model to planners
```

Generators shall never resolve raw policy keys independently. Renderers and templates shall not infer roles, types, JPA participation, runtime owners, or defaults from strings or property names.

The resolved model shall be the single source of truth for:

- generated construction and Factory planning;
- Behavior and ordinary business write-surface planning;
- JPA annotation and column-participation planning;
- generated managed-field catalogs;
- runtime handler, adapter, accessor, and support validation;
- plan evidence and diagnostics.

`FieldModel.managedRole`, `FieldModel.inherited`, and `SpecialFieldWritePolicy` shall not remain independent policy sources after migration.

### Construction and checked-in source

Entity constructor, Factory input, and Command request shall remain independent surfaces.

The default checked-in Factory shall apply `OMIT`, `OPTIONAL`, or `REQUIRED` only when first materialized. Existing checked-in Factory and Behavior artifacts shall retain `SKIP` ownership; Cap4k shall not promise overwrite, merge, patch, or freshness after schema evolution.

A generated Factory may prepare an application identifier before invoking a constructor that requires it. A constructor or custom Factory may accept a caller-supplied valid value when the selected policy permits it. Allowing such construction shall not force the default Factory or a Command request to expose the field.

The generator shall not expose arbitrary business setters for immutable managed fields merely because persistence access is required. Domain API accessibility and persistence accessibility shall remain separate.

### JPA projection

The JPA planner shall derive INSERT, UPDATE, generated-value, identity, version, soft-delete, and ordinary-column behavior from the resolved managed semantics. A generic managed field shall not automatically become `insertable=false, updatable=false`.

Representative intent shall be:

| Policy | INSERT participation | UPDATE participation |
|---|---|---|
| application identifier | written with assigned value | excluded |
| database identity | provider/database generated | excluded |
| version | provider controlled | provider controlled |
| soft delete | active sentinel written | provider-owned tombstone transition |
| tenant/context field | written after admission | excluded unless another exact policy says otherwise |
| created audit field | written after enrichment | excluded |
| updated audit field | written after enrichment | written after enrichment |
| database generated on insert | database generated/read back | excluded |
| database generated always | database generated/read back | database generated/read back |

The exact Hibernate-compatible annotation form may depend on the supported Hibernate version. Unsupported policy/type/projection combinations shall fail with provider-specific evidence.

### Plan evidence

`cap4kPlan` shall expose, for every used managed field:

- exact policy key;
- selection provenance and definition owner;
- role, creation-input rule, and explicit-value rule;
- lifecycle set;
- handler qualifier and slot;
- semantic type, target type, and adapter qualifier;
- INSERT and UPDATE authority;
- final JPA projection;
- generated runtime binding identity.

Raw extension implementation classes shall not be plan evidence.

## Generated managed-field catalog

### Binding metadata

The generator shall emit build-owned `ManagedFieldCatalog` runtime metadata for every used managed field on aggregate roots and owned entities. Each `ManagedFieldBinding` shall carry at least:

- entity type;
- canonical field name;
- exact JPA/Hibernate persistence property name;
- column name, target type, and nullability;
- policy key, role, explicit-value rule, and lifecycle set;
- handler qualifier and slot;
- semantic type and adapter qualifier;
- operation-specific persistence authority;
- optional typed runtime support.

The persistence property name shall be the provider-visible dirty-property identity used by mutation guards. Runtime code shall not guess it from a column name.

### Typed runtime support

Application identifier bindings shall carry target-field-specific absence, allocation, and validation support for UUID7, snowflake, or assigned identity as applicable.

Soft-delete bindings shall carry the exact active sentinel derived from the approved storage/identifier matrix.

Built-in Initializers shall consume generated support and shall not re-infer schema meaning through reflection. Missing or incompatible built-in support shall fail startup. Extension policies may leave runtime support absent and keep executable logic in their runtime Handler.

The existing Strong-ID-only accessor, catalog, and registry path may be adapted temporarily during migration, but the completed capability shall have one managed-field catalog and registry rather than parallel metadata systems.

## Runtime registry, access, and adaptation

### Application-scoped registry

The runtime shall assemble one immutable application-scoped registry during startup from:

- generated managed-field catalogs;
- admission Initializers;
- JPA persistence Enrichers;
- managed value Adapters;
- exact custom field Accessors;
- provider metadata needed to validate persistence-property footprints.

A UoW may read but shall not mutate the registry. Every used binding shall resolve completely before the application serves requests.

Unused runtime components may exist. Every qualifier or adapter referenced by a used binding shall have exactly one compatible owner.

### Default reflective access

For each binding, startup shall collect declarations matching the canonical field name from the entity class and its superclasses, require exactly one compatible declaration, and cache reflective field or method-handle access.

This access shall support direct fields, user superclass fields, private/protected visibility, and Kotlin `private set` backing fields. Reflection shall implement access only; it shall not infer managed semantics.

Field shadowing shall not use nearest-declaration-wins. Multiple matching declarations shall fail unless one exact custom Accessor replaces reflective resolution.

### Custom accessors

A custom `ManagedFieldAccessor` shall identify one entity type, field name, and policy key and shall declare a non-empty set of exact provider persistence-property names that its write may dirty.

Startup shall validate every mutation-footprint property against provider metadata. An unknown or empty footprint, duplicate custom Accessor, or Accessor/binding type mismatch shall fail.

An exact custom Accessor shall take precedence over cached reflection. It shall be an explicit project trust boundary for non-standard representations such as immutable embedded metadata replacement.

### Semantic value adaptation

Handlers shall produce semantic values. Entity fields shall store target JVM values. If the semantic value is assignable to the target type, identity assignment shall be used. If the types differ, one exact compatible `ManagedValueAdapter` qualifier shall be required.

Cap4k shall not guess conversions such as `Instant` to seconds versus milliseconds, `Instant` to `LocalDateTime` zone, `Instant` to formatted text, UUID to text, or project-specific actor/tenant identifiers.

A `ManagedFieldHandle` shall support:

- reading the current target value;
- adapting a semantic value without mutation;
- comparing the current target value to an adapted semantic value;
- assigning a semantic value through validated adaptation and the cached raw Accessor.

Null adaptation or assignment shall be allowed only for a nullable target. Context matching shall use non-mutating adaptation/comparison rather than temporary writes.

## Entity admission initialization

### Admission boundaries

Managed initialization shall execute only when a newly created entity formally enters an aggregate:

```text
Factory Supervisor accepts a new root
  -> AGGREGATE_ROOT admission

OwnedEntityList or the owned-ONE ownership boundary accepts a new child
  -> OWNED_CHILD admission
```

Repository loading, repeated observation, and stabilization shall never reinitialize a loaded entity.

### Lifecycle order

A new root shall follow:

```text
construct root
  -> initialize and validate admission-managed fields
  -> record CREATE intent
  -> invoke optional root onCreate
  -> expose root to domain behavior
```

A new owned child shall follow:

```text
construct child
  -> ownership boundary accepts child
  -> initialize and validate admission-managed fields
  -> attach child to aggregate topology
```

Owned children shall not receive aggregate lifecycle callbacks or independent Domain Event authority.

### Initializer authority

A `ManagedEntityInitializer` shall own one or more exact qualifiers and shall receive only semantic handles for those qualifiers on the one entity being processed, together with admission kind and immutable execution-context snapshot.

Initialization may assign owned handles. Validation shall be side-effect free and may only read/adapt/compare them. The API shall not expose the raw entity, aggregate topology, lifecycle state, or event queue.

Initialization shall be idempotent for the same entity instance. Reaching an admission boundary twice shall not allocate a second identifier or overwrite a preserved valid value. Idempotence shall not permit re-parenting an already admitted child.

The UoW shall invoke side-effect-free validation for every new entity before persistence. It shall fail an object graph that bypassed proper admission but shall never perform late allocation or repair.

## JPA persistence enrichment

### General contract

The audit-specific persistence hook shall become a general `JpaPersistenceEnricher` owned by exact qualifiers.

For each aggregate candidate change, the runtime shall group entity changes by identity and expose only handles:

- whose bindings belong to the invoked Enricher's qualifiers;
- whose entity participates in the current aggregate change;
- whose INSERT authority is `MANAGED_HANDLER` for CREATE;
- whose UPDATE authority is `MANAGED_HANDLER` for UPDATE.

Delete-only entries shall expose no enrichment handles. A future delete-enrichment feature shall require an explicit delete authority and provider contract.

### Stabilization order and candidate boundary

Every synchronization round shall preserve:

```text
candidate change recognition
  -> managed persistence enrichment
  -> final dirty recognition
  -> flush
  -> provider baseline advancement
```

A clean loaded aggregate shall not be an enrichment candidate. Loading, observing, or enrolling a clean object shall not cause audit mutation or SQL UPDATE.

### Mutation authority

An Enricher may mutate only the handles provided for its owned qualifiers. It shall not mutate business state, aggregate topology, event queues, lifecycle state, or another qualifier's fields.

The UoW shall snapshot provider dirty properties immediately before and after each individual Enricher call. The dirty delta for that call shall be contained in the mutation footprints of only that Enricher's supplied bindings. Validation shall not use the union of permissions from multiple Enrichers.

Unauthorized mutation shall fail before flush with aggregate, entity, field/property, policy, qualifier, lifecycle, and causal evidence where available.

### Idempotence and ordering

The enrichment context shall provide one stable `Instant` and one immutable execution-context snapshot for the outer physical UoW. Repeated stabilization rounds shall reuse them.

An Enricher shall derive deterministic values from stable context and current candidate state. It shall not read a fresh clock or produce new random values on every round.

Cap4k shall define no semantic order among separate qualifiers. Coordinated fields shall use one qualifier with distinct slots, and cross-policy facts shall come from shared execution context or one combined policy owner.

## Standard audit behavior

Cap4k shall supply audit policy definitions and a default audit-time Enricher. It shall not require or generate an audit base class.

On CREATE, the applicable created and updated audit fields shall be populated. On UPDATE, only update audit fields shall be populated. Delete-only operations shall not be treated as audit updates.

Audit actor values shall come from the explicit execution-context snapshot through one project or integration-owned Handler. A non-null binding shall require a valid actor. A nullable binding with no actor shall be assigned null rather than preserving an untrusted caller value. Any system-actor fallback shall be explicit Handler configuration.

Child-only changes may enrich the changed child's audit fields. They shall not automatically advance the root's audit fields or optimistic-lock version.

## Context-bound managed fields

`scope.tenant` shall mean admission-time context binding and validation:

- an active tenant context value is required whenever the policy is used;
- an absent field is filled from the context;
- an equal explicit value is preserved;
- a mismatching explicit value fails;
- the default Factory omits tenant input.

Tenant and similar scope policies shall consume Cap4k's explicit execution-context snapshot rather than relying on ambient `ThreadLocal` state. Framework-managed asynchronous execution shall propagate the snapshot. Outbound RPC header mapping and inbound reconstruction shall remain adapter/security responsibilities.

Managed scope fields shall not imply automatic Hibernate filters, query predicates, schema selection, database routing, authorization, or cross-service trust. Cap4k shall remain compatible with ORM multi-tenancy plugins by continuing to use normal Hibernate sessions and transaction integration.

Projects may define additional exact context dimensions such as organization, workspace, region, or legal entity. Cap4k shall not hard-code one universal scope hierarchy.

## Failure and diagnostic contract

### Source and resolution failures

Source or canonical processing shall fail with table/column and policy evidence for:

- missing, malformed, duplicated, or removed annotations;
- unresolved policy key;
- duplicate definition owner;
- invalid identifier/primary-key relationship or composite physical key;
- incompatible field type, role, lifecycle, explicit-value rule, or authority;
- invalid qualifier/slot schema or qualifier reuse across Handler kinds;
- missing required adapter qualifier;
- unsupported JPA projection.

Unknown but syntactically valid custom keys shall survive parsing and fail only if canonical resolution cannot find a definition.

### Extension loading failures

Loading shall fail for:

- no provider on a non-empty extension classpath;
- duplicate extension ID;
- unsupported SPI version;
- unknown contribution type;
- duplicate contribution ID inside an extension;
- duplicate global Artifact Addon ID;
- configured extension or contribution not discovered;
- service construction/linkage failure;
- Artifact Addon template outside its namespace;
- duplicate managed policy key across built-in or extension owners.

### Generation and startup failures

Generation shall fail rather than render fallback semantics when construction, target type, catalog identity, declaration, member naming, or JPA projection is incomplete.

Application startup shall fail before serving requests for missing, duplicate, inaccessible, or incompatible runtime bindings, including handler owners, adapters, fields, custom Accessors, mutation footprints, generated runtime support, catalog/entity identity, or slot schema.

### UoW failures

The UoW shall fail before flush when an admission-required value is absent or invalid, a context-bound value lacks context or mismatches it, an Enricher mutates outside its authority, topology/events/business state are changed through enrichment, or final dirty state violates provider participation.

Diagnostics shall identify the aggregate root type and ID when available, entity type, field/property, policy key, lifecycle, operation, qualifier/slot, adapter, and definition/provenance evidence relevant to the failure.

## Acceptance scenarios

### Application identifier visibility

Given a generated or custom Factory that constructs a UUID7 or snowflake entity, admission shall preserve a valid explicit value or allocate an absent value before root `onCreate` or owned-child attachment completes. UoW validation shall observe the final value and shall not allocate it.

Given assigned identity, absence shall fail at admission. Given database identity, normal explicit input shall fail and the value may remain absent until provider flush.

### Audit and dirty detection

Given a persistent business CREATE or UPDATE candidate, enrichment shall assign only applicable audit handles and final dirty recognition shall include the resulting JPA-writable audit columns.

Given a clean loaded aggregate, no persistence Enricher shall mutate it and no audit-only update shall occur.

Given multiple stabilization rounds caused by synchronous events, the same timestamp and execution-context snapshot shall be reused.

Given a delete-only change, update-audit fields shall not be exposed or changed.

### Context match

Given a new tenant-bound root or child and an active propagated tenant, admission shall fill absence or accept equality. Missing context or mismatch shall fail before persistence. Query filtering shall remain external.

### Field representation

Given a private or user-superclass managed field, startup shall resolve one cached binding. Given ambiguous field shadowing, startup shall fail unless one exact custom Accessor owns the binding.

Given semantic `Instant` and target epoch milliseconds, one explicit adapter shall make comparison and assignment deterministic. Omitting or duplicating that adapter shall fail startup.

### Enricher isolation

Given two separate Enricher qualifiers, changing Spring ordering metadata shall not create a guaranteed order. Given one Enricher mutating another qualifier's field or a business field, its own immediate dirty-delta check shall fail even if another Enricher would have permission for that property.

### Pipeline Extension composition

Given one extension that contributes policy definitions and artifact plans, canonical policy resolution shall consume only the declarative definitions, while artifact planning shall consume only the completed Canonical Model and preserve addon template isolation.

Given build-time extension installation without the corresponding runtime dependency, generation may succeed but application startup shall fail if a used policy lacks its runtime Handler or Adapter. Cap4k shall not add the runtime dependency implicitly.

## Migration and exclusions

The completed implementation shall have one authoritative path and shall remove active use of:

```text
@IdStrategy
@Inherited
DbIdStrategy
DbManagedRole
cap4kAddon
direct ArtifactAddonProvider discovery
JpaPersistenceAuditEnricher
GeneratedOwnIdRegistry
```

Historical specs and archived Comet evidence may retain those names as recorded history. Active documentation, fixtures, skills, examples, and the `only-engine` integration shall use only the new contract.

This capability shall not define queryable Value Object persistence, read-model generation, automatic tenant isolation, audit history, delete audit, arbitrary pipeline callbacks, a public multi-ORM Provider SPI, or root-version advancement for child-only changes.
