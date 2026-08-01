# Cap4k Managed Field Policy And Pipeline Extension Design

Date: 2026-07-31

Status: Approved design; implementation pending

## Reader Contract

This design defines the next Cap4k contract for system-managed persistent fields. It covers the DB annotation source, canonical policy model, build-time extension surface, generated runtime metadata, entity initialization, Hibernate persistence enrichment, value adaptation, field access, and JPA projection.

Read this document as a breaking replacement for the active managed-field portions of:

- `2026-07-20-cap4k-db-custom-annotation-contract-redesign.md`;
- `2026-05-03-cap4k-special-fields-managed-write-surface-and-only-engine-audit-alignment-design.md`;
- the generic managed-field deferrals in `2026-07-26-cap4k-database-entrusted-fields-construction-design.md`;
- the direct `ArtifactAddonProvider` installation model in `2026-05-10-cap4k-artifact-addon-spi-and-only-engine-enum-translation-design.md`.

This design does not discard already implemented results from those documents. In particular, it preserves:

- database identity construction and observation;
- JPA optimistic-lock version ownership;
- soft-delete transition semantics;
- checked-in Factory and Behavior ownership with first-write then `SKIP`;
- root-oriented Hibernate Unit of Work stabilization;
- post-canonical artifact contribution and template namespace isolation.

The application execution and UoW baseline is `2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md`, implemented on current `master` by PR #149. This document narrows and generalizes that design's audit enrichment surface; it does not reopen Command transaction ownership, event frontiers, repository observation, or Hibernate-only change detection.

GitHub issue #115 is closed and remains historical evidence for owned-child creation input. It is not the backlog or semantic owner of this managed-field design.

Historical specs remain useful evidence. They are not edited to pretend that their older decisions were never made. Active source, public documentation, skills, fixtures, and implementation plans must follow this document after implementation.

## Summary

Cap4k currently has several partial concepts for infrastructure-owned fields:

- `@IdStrategy` for identifier allocation;
- `@Managed=system|scope|deleted|version` for broad field roles;
- `@Inherited` for generated declaration omission;
- `SpecialFieldWritePolicy` for Factory/write-surface governance;
- JPA `insertable` and `updatable` projection;
- generated Strong ID accessors and catalogs;
- `JpaPersistenceAuditEnricher` in the Hibernate UoW;
- `ArtifactAddonProvider` for post-canonical generated artifacts.

Those concepts do not form one coherent policy model. Most importantly, the current aggregate planner treats a generic managed `READ_ONLY` field as JPA non-insertable and non-updatable. A persistence enricher can assign such a field in memory while Hibernate is forbidden from writing it.

This design replaces the fragmented model with one policy reference:

```text
@Managed=<policy-key>
```

The DB source records the raw key. A fixed canonical policy-resolution phase expands it into strongly typed semantics. Generators and the JPA planner consume only the resolved model. Generated `ManagedFieldCatalog` metadata connects that model to runtime Initializers, Persistence Enrichers, Value Adapters, and field Accessors.

Built-in and third-party managed policies are supported now. A general multi-ORM Persistence Provider is not.

## Problem Statement

The current contract has six structural problems.

### 1. Role And Strategy Are Split

An identifier column may carry both physical primary-key metadata and `@IdStrategy`, while version and deletion use `@Managed`. The source contract has no single namespace for framework, runtime-handler, provider, and database-owned policies.

### 2. User Write Governance And JPA Participation Are Conflated

`READ_ONLY` was designed to exclude a field from ordinary create/update payloads. The current entity planner also maps generic managed `READ_ONLY` fields to:

```text
insertable = false
updatable = false
```

That is valid for some database-generated values but invalid for audit fields written by a persistence enricher.

### 3. Inheritance Is Modeled As A Database Fact

`@Inherited` currently enters `DbColumnSnapshot` and `FieldModel`, then removes the field from the default concrete entity scalar list. The default template does not select or generate a base class. The annotation therefore describes a code declaration strategy that the database cannot prove and the default generator cannot complete.

### 4. Audit Enrichment Has No Field Registry

The current UoW has the correct execution order:

```text
candidate detection
  -> audit enrichment
  -> final dirty detection
  -> provider flush
```

However, the audit SPI receives raw entities and has no declared mapping from an audit role to a persistent field. It cannot distinguish `updatedAt` from `status` without user convention or reflection by guessed name.

### 5. Strong ID Metadata Is A One-Off Runtime System

Generated Strong ID accessors and catalogs already establish a useful entity-type-to-field-binding pattern. Audit, scope, and other managed fields would otherwise repeat the same registry and access infrastructure.

### 6. Artifact Addon Is Too Narrow As An Installation Unit

`cap4kAddon` currently means all of the following:

- the Gradle dependency bucket;
- the `ServiceLoader` discovery surface;
- a provider identity;
- provider-scoped configuration;
- artifact contribution;
- template resource ownership.

Managed policy definitions need build-time discovery before canonical planning, not post-canonical artifact planning. Loading another independent service family from the same narrowly named installation surface would multiply identity, configuration, compatibility, and diagnostic rules.

## Goals

1. Replace `@IdStrategy`, broad managed roles, and `@Inherited` with exact managed policy keys.
2. Preserve physical primary-key, column, nullability, unique-constraint, and relation facts in the DB source.
3. Resolve managed policies once before generators run.
4. Support built-in and third-party managed policy definitions without opening custom pipeline stages.
5. Separate default Factory input exposure from runtime acceptance of explicit values.
6. Support user-provided identifiers when the identifier policy permits them.
7. Separate managed value authority from JPA column write projection.
8. Generalize `JpaPersistenceAuditEnricher` into managed persistence enrichment while preserving candidate-before-enrich-before-final detection.
9. Support aggregate roots and owned entities without allowing owned children to originate Domain Events.
10. Avoid requiring a Cap4k audit base class, fixed property names, or public/internal setters.
11. Provide explicit runtime ownership and startup diagnostics for handlers, adapters, and accessors.
12. Generalize generated Strong ID catalog infrastructure into a reusable managed-field catalog.
13. Generalize `cap4kAddon` into a coherent build-time Pipeline Extension installation model.
14. Keep Spring Data JPA plus Hibernate as the only persistence runtime for this iteration.

## Non-Goals

This design does not:

1. Introduce a public multi-ORM Persistence Provider SPI.
2. Replace Hibernate change detection or the current JPA UoW.
3. Implement queryable Value Object persistence, `@Embedded`, or `@ElementCollection` policy.
4. Implement read-model generation or cross-context query composition.
5. Provide complete tenant isolation, query filtering, database routing, or schema-per-tenant behavior.
6. Prevent every possible direct user mutation of a managed property.
7. Define an audit history table, temporal model, or event-sourcing log.
8. Define a Cap4k audit entity base class.
9. Require one constructor style, Factory payload style, or inheritance strategy.
10. Allow Pipeline Extensions to reorder stages, mutate arbitrary source snapshots, or write files directly.
11. Add per-extension classloader isolation before a real dependency-conflict case requires it.
12. Automatically add runtime dependencies declared by a build-time extension to application modules.
13. Restore application-facing UoW lifecycle operations.
14. Change child-only dirty behavior to advance the aggregate root version or root audit fields automatically.

## Architecture Constraints

The fixed pipeline remains:

```text
collect
  -> normalize
  -> enrich
  -> plan
  -> render
  -> export
```

Managed policy resolution is framework-owned work inside canonical normalization/enrichment. A Pipeline Extension contributes declarative policy definitions to that fixed phase; it does not contribute a new phase.

The runtime remains:

```text
outer REQUIRED Command
  -> one Hibernate persistence context
  -> one Cap4k UoW Context
  -> zero or more stabilization rounds
  -> one outer transaction completion
```

Managed persistence enrichment remains inside each stabilization round and may not reopen application mutation authority.

## Terms

### Managed Field

A persistent field whose ordinary state transition is owned by a declared policy rather than an unrestricted business write surface.

"Managed" does not mean "JPA must not write this column."

### Policy Key

The stable exact identifier referenced by `@Managed`, for example:

```text
identifier.uuid7
enrichment.audit-time.updated-at
version
database.generated-always
```

### Policy Definition

Build-time declarative semantics registered for one exact policy key.

### Resolved Managed Field Policy

The canonical field-specific result after combining the source key, project defaults, physical schema facts, type information, and its Policy Definition.

### Creation Input Policy

The rule controlling whether the default generated checked-in Factory input omits, optionally exposes, or requires a managed value. It does not prohibit a user from editing the checked-in Factory or writing another constructor.

### Explicit Value Policy

The rule controlling what happens when an entity already contains a value at its managed admission or enrichment boundary.

### Value Authority

The component that owns producing or transitioning a value for a persistence operation: caller, Cap4k framework, registered runtime handler, JPA provider, database, or none.

Value authority is not the same as the component that emits SQL.

### Handler Qualifier

The runtime ownership key used to locate exactly one Initializer or Persistence Enricher for a used managed policy.

### Handler Slot

An optional role inside one Handler qualifier. It exists only when one Handler owns multiple fields on the same entity, such as `created-at` and `updated-at`.

### Managed Field Catalog

Generated runtime metadata that maps entity types and managed policy identities to persistent field bindings.

### Semantic Value

The stable value produced by a Handler before storage conversion, such as `Instant`, `TenantId`, `ActorId`, or `UUID`.

### Target Value

The JVM property type used by the entity and JPA mapping.

### Pipeline Extension

An explicitly installed build-time package that contributes one or more allowed capabilities through a versioned descriptor. Artifact Addon and Managed Field Policy are contribution types, not independent installation mechanisms.

## DB Source Contract

### One Managed Annotation

The DB column comment surface uses:

```text
@Managed=<policy-key>
```

The source parser preserves the exact key. It does not convert the key into `DbManagedRole`, `DbIdStrategy`, JPA flags, constructor decisions, or template variables.

The key grammar is:

```text
[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*
```

Examples:

```text
@Managed=identifier.uuid7
@Managed=identifier.database-identity
@Managed=scope.tenant
@Managed=enrichment.audit-time.created-at
@Managed=database.generated-always
@Managed=version
@Managed=soft-delete
```

Policy keys are case-sensitive and use lowercase kebab-case segments. `id.*`, snake-case aliases, and short compatibility names are not supported.

### Removed Column Annotations

The following annotations are removed from the active source contract:

```text
@IdStrategy
@Inherited
```

They are not accepted as no-op aliases.

`@IdStrategy` is redundant because identifier management is selected by `@Managed=identifier.*`. Physical primary-key metadata remains the source of truth that a column is an identifier.

`@Inherited` is invalid because field declaration placement is a code rendering concern. A column remains semantically owned by its entity whether a custom project renders it directly, through a user base class, or through another representation.

The current broad values are also removed:

```text
@Managed=system
@Managed=scope
@Managed=deleted
```

They do not contain enough semantics to select a lifecycle owner, field role, explicit-value rule, or persistence participation contract.

The `version` spelling remains as the exact built-in key `@Managed=version`, but its old broad-role interpretation is removed. `@Managed=deleted` is replaced by `@Managed=soft-delete`.

### Source Snapshot

The DB source model records raw metadata conceptually as:

```kotlin
data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val managedPolicyKey: String? = null,
    // existing physical and relation metadata
)
```

Remove the following source fields after all active callers migrate:

```text
idStrategy
managedRole
inherited
```

The source validates annotation syntax and local combinations. It does not require the source module to know which Pipeline Extensions are installed.

### Identifier Rules

An `identifier.*` policy key is valid only on a physical primary-key column.

A primary-key column must resolve exactly one identifier policy through:

```text
explicit @Managed identifier policy
  -> project identifier default
  -> fail if unresolved
```

A non-primary-key column using `identifier.*` fails canonical resolution. A primary-key column using a non-identifier managed policy also fails.

A table with more than one physical primary-key column fails this iteration. Cap4k does not currently support a multi-column composite identifier contract. A generated single-column Strong ID represented by an embeddable JVM type is still one physical identifier column and is not a composite database key.

This design does not silently infer database identity from JDBC auto-increment metadata. The selected policy is a deliberate Cap4k construction and ownership contract.

### Project Defaults

Exact DB annotations override project defaults. Project defaults remain useful for repeated conventional columns but must also select exact policy keys.

Conceptual configuration:

```kotlin
managedFields {
    identifierDefaultPolicy.set("identifier.uuid7")
    columnPolicyDefaults.put("created_at", "enrichment.audit-time.created-at")
    columnPolicyDefaults.put("updated_at", "enrichment.audit-time.updated-at")
    columnPolicyDefaults.put("version", "version")
    columnPolicyDefaults.put("deleted", "soft-delete")
}
```

This replaces:

```text
idDefaultStrategy
managedDefaultColumns
deletedDefaultColumn
versionDefaultColumn
```

An implementation may retain dedicated deleted/version default-column DSL only if it immediately resolves those names to the exact built-in policy keys. The Canonical Model never carries a vague "managed default column" classification.

Resolution precedence is:

```text
explicit column policy
  -> matching exact project default
  -> role-specific project default, such as identifier default
  -> no managed policy
```

Fields that require a managed role, such as a primary key without an identifier policy, fail after precedence is exhausted.

### Parser Diagnostics

Required parser and canonical diagnostics include:

- missing `@Managed` value;
- invalid policy-key grammar;
- multiple `@Managed` annotations on one column;
- removed `@IdStrategy` or `@Inherited` annotation;
- unresolved policy key;
- duplicate policy definition;
- identifier policy on a non-primary-key column;
- incompatible field type for a policy definition.

The parser strips only supported annotations from cleaned comments. Unsupported annotations remain failures rather than silently disappearing.

## Pipeline Extension Contract

### Installation Unit

`ArtifactAddonProvider` is no longer the build-time installation root. The new root is a Pipeline Extension:

```kotlin
interface PipelineExtensionProvider {
    val descriptor: PipelineExtensionDescriptor
    val contributions: List<PipelineContribution>
}

data class PipelineExtensionDescriptor(
    val id: String,
    val spiVersion: Int,
    val displayName: String = id,
)

interface PipelineContribution
```

A `PipelineExtensionProvider` implementation is made discoverable from the resolved extension classpath through:

```text
META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
```

An extension descriptor, not a Maven artifact, is the installation identity. One resolved component may expose multiple providers, and a provider may be carried by a transitive component. The initial shared-classloader design does not promise one provider per direct dependency or attempt direct-component ownership attribution.

The initial allowed contribution types are:

```text
ArtifactAddonProvider
ManagedFieldPolicyProvider
```

Unknown contribution types fail loading. An extension cannot use the marker interface to create an unrecognized execution hook.

### Gradle Installation And Configuration

The build-time dependency bucket becomes:

```kotlin
dependencies {
    cap4kPipelineExtension("com.example:example-cap4k-extension:1.0.0")
}
```

The old `cap4kAddon` configuration is removed without a compatibility alias.

Provider and contribution options are repository configuration, separate from installation:

```kotlin
cap4k {
    pipelineExtensions {
        provider("example") {
            contribution("example-managed-fields") {
                option("mode", "strict")
            }
        }
    }
}
```

The exact Gradle object names may be shortened during implementation only if all three identities remain explicit:

```text
extension provider id
contribution id
contribution options
```

The canonical contribution identity is `(extensionId, contributionId)`. Contribution IDs must be unique across all contribution types inside one extension because configuration addresses `contribution(id)` without a type discriminator. Artifact Addon IDs additionally remain globally unique because the preserved `addons/<artifact-addon-id>/...` resource namespace does not contain the extension ID.

### Contribution Interfaces

Artifact contribution remains post-canonical and pre-render:

```kotlin
interface ArtifactAddonProvider : PipelineContribution {
    val id: String
    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}
```

Managed policy contribution participates only in the fixed canonical policy-resolution phase:

```kotlin
interface ManagedFieldPolicyProvider : PipelineContribution {
    val id: String

    fun definitions(
        context: ManagedFieldPolicyContributionContext,
    ): List<ManagedFieldPolicyDefinition>
}
```

The context contains immutable project configuration and contribution-scoped options. The provider returns definitions. It cannot mutate `SourceSnapshot`, mutate an existing `CanonicalModel`, plan arbitrary artifacts through this contribution, or write files.

An extension that needs both semantics and generated support may contribute both a Managed Field Policy provider and an Artifact Addon. Each contribution retains its own boundary.

### Loader And Classloader

`ArtifactAddonLoader` becomes a general `PipelineExtensionLoader`.

The initial implementation uses one task-scoped `URLClassLoader` for the resolved `cap4kPipelineExtension` classpath. It:

- uses the Cap4k API classloader as parent;
- loads `PipelineExtensionProvider` once;
- validates SPI version before contribution use;
- validates unique extension IDs and extension-scoped contribution IDs;
- validates globally unique Artifact Addon IDs;
- builds typed contribution registries;
- closes the classloader after the task run;
- remains a Gradle `@Classpath` task input.

When the configuration resolves a non-empty classpath, discovery must yield at least one provider. Repository configuration that names an extension or contribution is then validated against the descriptors actually discovered from the whole classpath.

Artifact templates continue to resolve from the owning extension code source and retain the stable path namespace:

```text
addons/<artifact-addon-id>/<path>
```

The Artifact Addon capability still enforces that namespace. Managed Policy contributions do not own templates unless the same extension also provides an Artifact Addon.

Per-extension dependency classloader isolation is deferred. It requires splitting each direct Gradle dependency with its complete transitive graph and introduces JVM type-identity risks for shared Kotlin and serialization libraries. A concrete provider-conflict case is required before adding that complexity.

### Build-Time And Runtime Dependencies

Build-time installation and application runtime dependencies remain explicit and separate:

```kotlin
dependencies {
    cap4kPipelineExtension("com.example:managed-policy-pipeline:1.0.0")
    implementation("com.example:managed-policy-runtime:1.0.0")
}
```

A single artifact may be declared in both places, or a provider may publish separate pipeline and runtime artifacts.

Pipeline Extensions do not add runtime dependencies to application modules automatically. Build-time code must not silently change the application's runtime dependency graph.

### Extension Diagnostics

Required failures include:

- no `PipelineExtensionProvider` found on a non-empty resolved extension classpath;
- duplicate extension id;
- unsupported SPI version;
- unknown contribution type;
- duplicate contribution id within an extension;
- duplicate global Artifact Addon id;
- configured extension or contribution not loaded;
- `ServiceConfigurationError`, linked to the service type and resolved classpath where possible;
- Artifact Addon template outside its namespace;
- duplicate managed policy key across built-in and extension definitions.

## Canonical Managed Field Model

### Policy Definition

The public build-time definition is conceptualized as:

```kotlin
data class ManagedFieldPolicyDefinition(
    val key: String,
    val role: ManagedFieldRole,
    val creationInput: ManagedCreationInputPolicy,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String? = null,
    val handlerSlot: String? = null,
    val semanticValueType: ManagedSemanticTypeRef = ManagedSemanticTypeRef.TargetField,
    val valueAdapterQualifier: String? = null,
    val persistence: PersistenceParticipation,
)

sealed interface ManagedSemanticTypeRef {
    data object TargetField : ManagedSemanticTypeRef
    data class FixedFqn(val value: String) : ManagedSemanticTypeRef
}
```

The field target type, column name, nullability, primary-key fact, and source provenance come from the source/canonical field, not the reusable Policy Definition.

### Roles

```kotlin
enum class ManagedFieldRole {
    IDENTIFIER,
    VERSION,
    SOFT_DELETE,
    SCOPE,
    INITIALIZATION,
    ENRICHMENT,
    DATABASE_GENERATED,
}
```

Roles support validation and diagnostics. Runtime execution is selected by lifecycles and qualifier, not by field-name convention.

### Creation Input

```kotlin
enum class ManagedCreationInputPolicy {
    OMIT,
    OPTIONAL,
    REQUIRED,
}
```

This affects only the default generated checked-in Factory input on first generation.

### Explicit Values

```kotlin
enum class ManagedExplicitValuePolicy {
    PRESERVE_IF_VALID,
    REQUIRE,
    REQUIRE_CONTEXT_MATCH,
    OVERWRITE,
    FORBID,
}
```

The enum defines ownership behavior, not a pluggable validator body. Policy Definitions are declarative and cannot execute validation code.

Generic runtime code validates presence, nullability, semantic input type, converted target type, and framework-prepared value provenance where available. An admission policy that requires semantic validation beyond those facts uses its required `handlerQualifier`; that Initializer owns both validation and value production. `FRAMEWORK` is restricted to built-in policies with a standard Cap4k owner. Extension-defined runtime policies use `MANAGED_HANDLER`.

Resolution also enforces:

```text
PRESERVE_IF_VALID, REQUIRE, REQUIRE_CONTEXT_MATCH
  -> ENTITY_ADMISSION required

OVERWRITE
  -> ENTITY_ADMISSION or PERSISTENCE_ENRICHMENT required

FORBID
  -> valid at any lifecycle; provider/database placeholders follow their planner contract
```

### Lifecycles

```kotlin
enum class ManagedFieldLifecycle {
    ENTITY_ADMISSION,
    PERSISTENCE_ENRICHMENT,
    PERSISTENCE_PROVIDER,
    DATABASE,
}
```

`lifecycles` is a non-empty set. Most policies use one phase. A policy may combine admission with provider ownership when the create and later transition boundaries differ. `soft-delete`, for example, combines `ENTITY_ADMISSION` validation of the active sentinel with `PERSISTENCE_PROVIDER` ownership of the delete transition.

Combining `ENTITY_ADMISSION` and `PERSISTENCE_ENRICHMENT` in one policy is forbidden. Those phases have different candidate and visibility semantics; define two exact policy keys when both are genuinely required.

There is no public `CONSTRUCTION` callback. A generated Factory may prepare a value before invoking a constructor, but arbitrary runtime handlers are not injected into entity constructors.

### Semantic Type Reference

Every policy has a non-null semantic type reference:

```text
TargetField
  -> semantic type is the resolved entity target field type

FixedFqn("java.time.Instant")
  -> semantic type is the named fixed JVM type
```

`TargetField` supports reusable policies whose concrete domain type varies by entity, including generated Strong IDs, tenant IDs, and actor IDs. Audit-time policies use fixed `java.time.Instant` semantics. There is no nullable or unspecified semantic-type state.

### Persistence Participation

```kotlin
enum class ManagedValueAuthority {
    CALLER,
    FRAMEWORK,
    MANAGED_HANDLER,
    PERSISTENCE_PROVIDER,
    DATABASE,
    NONE,
}

data class PersistenceParticipation(
    val insert: ManagedValueAuthority,
    val update: ManagedValueAuthority,
)
```

This identifies who performs the policy-owned value transition for each persistence operation. It is interpreted after `explicitValue`: for example, `PRESERVE_IF_VALID` may accept a caller-selected value, while `FRAMEWORK` says who supplies and validates the fallback when the field is absent. It is not a direct alias for JPA `insertable` or `updatable`.

Policy resolution enforces this operation-specific authority/lifecycle matrix:

| Operation | Value authority | Required lifecycle and owner |
|---|---|---|
| INSERT | `CALLER` | `ENTITY_ADMISSION` Initializer validates the supplied value |
| UPDATE | `CALLER` | invalid for a managed field; caller-updatable state is ordinary domain state |
| INSERT | `FRAMEWORK` | `ENTITY_ADMISSION` plus a built-in Cap4k Initializer; extension definitions cannot claim this authority |
| UPDATE | `FRAMEWORK` | invalid; framework update transitions use a Handler or persistence provider |
| INSERT | `MANAGED_HANDLER` | either `ENTITY_ADMISSION` Initializer or `PERSISTENCE_ENRICHMENT` Enricher owns insertion, but not both |
| UPDATE | `MANAGED_HANDLER` | `PERSISTENCE_ENRICHMENT` Enricher required |
| INSERT or UPDATE | `PERSISTENCE_PROVIDER` | `PERSISTENCE_PROVIDER` required |
| INSERT or UPDATE | `DATABASE` | `DATABASE` required |
| INSERT or UPDATE | `NONE` | no value transition is permitted for that operation |

A lifecycle may remain present for validation even when the corresponding authority is another owner, as with caller-assigned identifiers validated on admission. Every non-`NONE` authority must have exactly one executable owner for that operation.

Examples:

```text
identifier.uuid7
  explicit valid value may come from CALLER
  absent-value insert transition authority FRAMEWORK
  update authority NONE
  JPA still writes the generated identifier on INSERT

enrichment.audit-time.updated-at
  insert authority MANAGED_HANDLER
  update authority MANAGED_HANDLER
  JPA writes both INSERT and UPDATE values

version
  insert authority PERSISTENCE_PROVIDER
  update authority PERSISTENCE_PROVIDER
  Hibernate owns @Version behavior

database.generated-always
  insert authority DATABASE
  update authority DATABASE
  JPA column write flags normally exclude both operations
```

Role-specific JPA planning remains necessary. In particular, database identity and version are provider mappings, not a universal `insertable=false, updatable=false` formula.

### Resolved Field Policy

The field-specific canonical result is conceptualized as:

```kotlin
data class ResolvedManagedFieldPolicy(
    val fieldName: String,
    val columnName: String,
    val fieldType: String,
    val nullable: Boolean,
    val selection: ManagedPolicySelectionProvenance,
    val definitionOwner: ManagedPolicyDefinitionOwner,
    val policyKey: String,
    val role: ManagedFieldRole,
    val creationInput: ManagedCreationInputPolicy,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String?,
    val handlerSlot: String?,
    val semanticValueType: String,
    val valueAdapterQualifier: String?,
    val persistence: PersistenceParticipation,
)

sealed interface ManagedPolicySelectionProvenance {
    data class ExplicitColumnAnnotation(val sourceLocation: String) : ManagedPolicySelectionProvenance
    data class ExactColumnDefault(val configurationPath: String) : ManagedPolicySelectionProvenance
    data class IdentifierDefault(val configurationPath: String) : ManagedPolicySelectionProvenance
}

sealed interface ManagedPolicyDefinitionOwner {
    data object BuiltIn : ManagedPolicyDefinitionOwner
    data class Extension(
        val extensionId: String,
        val contributionId: String,
    ) : ManagedPolicyDefinitionOwner
}
```

Selection provenance answers why this field chose the policy. Definition ownership answers who supplied the policy semantics. They are independent and must not be collapsed into one generic `source` string.

`FieldModel.managedRole` and `FieldModel.inherited` are removed. A canonical field may refer to its resolved managed policy directly or through an entity-level indexed policy collection, but there must be one source of truth.

### Resolution Phase

The fixed canonical flow is:

```text
DB SourceSnapshot with raw managedPolicyKey
  -> combine built-in and extension Policy Definitions
  -> validate unique exact keys
  -> apply source/default precedence
  -> attach selection provenance and definition owner
  -> validate schema role and field type
  -> create ResolvedManagedFieldPolicy
  -> expose completed CanonicalModel to generators
```

Generators never resolve raw keys independently. The renderer never takes policy or type-resolution ownership.

### Handler Qualifier And Slot

Policy keys are exact definitions; no longest-prefix matching is used.

A resolved policy that needs a runtime handler declares `handlerQualifier`. `handlerSlot` follows these rules:

```text
one field for one qualifier on one entity
  -> slot may be null

multiple fields for one qualifier on one entity
  -> every field must have a nonblank unique slot
```

The same entity/qualifier group cannot mix slotted and unslotted fields.

Lifecycle ownership is validated as follows:

```text
ENTITY_ADMISSION
  -> lifecycle set contains ENTITY_ADMISSION
  -> handlerQualifier required
  -> resolve exactly one ManagedEntityInitializer owner

PERSISTENCE_ENRICHMENT
  -> lifecycle set contains PERSISTENCE_ENRICHMENT
  -> handlerQualifier required
  -> resolve exactly one JpaPersistenceEnricher owner

lifecycles contain neither ENTITY_ADMISSION nor PERSISTENCE_ENRICHMENT
  -> handlerQualifier absent
  -> JPA projection/provider owns execution

ENTITY_ADMISSION plus PERSISTENCE_PROVIDER or DATABASE
  -> handlerQualifier resolves the admission owner
  -> provider/database authority remains operation-specific
```

`handlerSlot` must be null when `handlerQualifier` is null. The lifecycle and authority matrix is resolved before slot grouping.

One qualifier belongs to exactly one runtime Handler kind across the entire application model:

```text
qualifier -> ManagedEntityInitializer
or
qualifier -> JpaPersistenceEnricher
```

Two different policy keys cannot reuse one qualifier across admission and persistence-enrichment phases. Such a model fails canonical resolution. Slots coordinate fields only inside one Handler kind; they do not create cross-phase atomicity.

Examples:

| Policy key | Handler qualifier | Handler slot |
|---|---|---|
| `scope.tenant` | `scope.tenant` | `null` |
| `enrichment.audit-time.created-at` | `enrichment.audit-time` | `created-at` |
| `enrichment.audit-time.updated-at` | `enrichment.audit-time` | `updated-at` |
| `enrichment.audit-actor.created-by` | `enrichment.audit-actor` | `created-by` |
| `enrichment.checksum` | `enrichment.checksum` | `null` |

A user who deliberately wants one generic scope handler may define exact policies such as:

```text
scope.context.tenant
scope.context.organization
```

and resolve both to handler qualifier `scope.context` with distinct slots.

### Declaration Placement

The Canonical Model says that the entity semantically owns the persistent field. It does not say where Kotlin source must declare it.

The default entity renderer declares managed fields directly on the concrete entity. A custom template or Artifact Addon may instead use a user base class or another code shape if it still satisfies the resolved field binding.

No database annotation controls this choice. No Cap4k standard audit base class is introduced.

### Plan Evidence

`cap4kPlan` must expose enough resolved information to review:

- policy key, selection provenance, and definition owner;
- role;
- creation input policy;
- explicit value policy;
- lifecycles;
- handler qualifier and slot;
- semantic and target types;
- value adapter qualifier;
- insert and update value authority;
- final JPA provider projection where applicable.

Raw extension implementation classes are not part of plan evidence.

## Standard Policy Catalog

### Identifier Policies

Cap4k provides these exact identifier policies:

| Policy key | Handler qualifier | Default Factory input | Explicit value | Lifecycles | Insert authority | Update authority |
|---|---|---|---|---|---|---|
| `identifier.uuid7` | `identifier.uuid7` | `OMIT` | `PRESERVE_IF_VALID` | `ENTITY_ADMISSION` | `FRAMEWORK` | `NONE` |
| `identifier.snowflake` | `identifier.snowflake` | `OMIT` | `PRESERVE_IF_VALID` | `ENTITY_ADMISSION` | `FRAMEWORK` | `NONE` |
| `identifier.assigned` | `identifier.assigned` | `REQUIRED` | `REQUIRE` | `ENTITY_ADMISSION` | `CALLER` | `NONE` |
| `identifier.database-identity` | `null` | `OMIT` | `FORBID` | `DATABASE` | `DATABASE` | `NONE` |

`identifier.uuid7` validates that an explicitly supplied UUID is version 7. It does not silently preserve an arbitrary UUID under a UUID7 policy.

`identifier.snowflake` validates the declared Strong ID backing type, value range, and configured strategy output. A value outside that contract fails admission.

`identifier.assigned` is the deliberate policy for externally assigned or imported identities. It does not allocate a value.

`identifier.database-identity` forbids an ordinary create path from supplying a non-default identity. Data migration requiring explicit database identity values uses migration tooling or provider-specific SQL outside normal aggregate creation.

The standard identifier policies use `TargetField` semantic type. Their generated binding retains the typed construction support needed to allocate or validate the concrete Strong ID.

Third-party identifier policies are allowed when their Policy Definition uses current canonical and JPA capabilities. A new identifier policy does not gain permission to change Repository, Query Schema, or UoW implementation.

Cap4k registers the standard `identifier.uuid7`, `identifier.snowflake`, and `identifier.assigned` admission Initializers. Their qualifiers participate in the same unique-owner validation as project handlers. `FRAMEWORK` means the standard owner is supplied by Cap4k; `CALLER` on `identifier.assigned` means the standard owner validates rather than allocates. Policies without `ENTITY_ADMISSION` do not resolve an application Initializer.

### Provider And Database Policies

Cap4k provides these exact provider/database policies:

| Policy key | Role | Handler qualifier | Creation input | Explicit value | Lifecycles | Insert authority | Update authority |
|---|---|---|---|---|---|---|---|
| `version` | `VERSION` | `null` | `OMIT` | `FORBID` | `PERSISTENCE_PROVIDER` | `PERSISTENCE_PROVIDER` | `PERSISTENCE_PROVIDER` |
| `soft-delete` | `SOFT_DELETE` | `soft-delete` | `OMIT` | `PRESERVE_IF_VALID` | `ENTITY_ADMISSION + PERSISTENCE_PROVIDER` | `FRAMEWORK` | `PERSISTENCE_PROVIDER` |
| `database.generated-on-insert` | `DATABASE_GENERATED` | `null` | `OMIT` | `FORBID` | `DATABASE` | `DATABASE` | `NONE` |
| `database.generated-always` | `DATABASE_GENERATED` | `null` | `OMIT` | `FORBID` | `DATABASE` | `DATABASE` | `DATABASE` |

The physical type and provider mapping remain validated by the JPA planner. A policy key does not make every JDBC type valid for `@Version`, identity generation, or a generated column.

`soft-delete` retains the existing generated active-sentinel initializer and the semantic tombstone transition owned by Repository root removal plus the JPA soft-delete projection. The generated entity value participates in INSERT; the matching database default protects schema and non-ORM insert boundaries. The standard `soft-delete` admission Initializer preserves and validates only the active sentinel. It rejects a new entity already carrying a tombstone value. The field is not an audit enricher field and is not exposed as an ordinary create or update value.

Provider and database policies use `TargetField` semantic type. Cap4k registers the built-in `soft-delete` admission Initializer; the other policies in this table have no application runtime handler.

### Standard Runtime-Handler Policies

The standard vocabulary includes:

| Policy key | Qualifier | Slot | Creation input | Explicit value | Lifecycles | Insert authority | Update authority |
|---|---|---|---|---|---|---|---|
| `scope.tenant` | `scope.tenant` | `null` | `OMIT` | `REQUIRE_CONTEXT_MATCH` | `ENTITY_ADMISSION` | `MANAGED_HANDLER` | `NONE` |
| `initialization.request-context` | `initialization.request-context` | `null` | `OMIT` | `OVERWRITE` | `ENTITY_ADMISSION` | `MANAGED_HANDLER` | `NONE` |
| `enrichment.audit-time.created-at` | `enrichment.audit-time` | `created-at` | `OMIT` | `OVERWRITE` | `PERSISTENCE_ENRICHMENT` | `MANAGED_HANDLER` | `NONE` |
| `enrichment.audit-time.updated-at` | `enrichment.audit-time` | `updated-at` | `OMIT` | `OVERWRITE` | `PERSISTENCE_ENRICHMENT` | `MANAGED_HANDLER` | `MANAGED_HANDLER` |
| `enrichment.audit-actor.created-by` | `enrichment.audit-actor` | `created-by` | `OMIT` | `OVERWRITE` | `PERSISTENCE_ENRICHMENT` | `MANAGED_HANDLER` | `NONE` |
| `enrichment.audit-actor.updated-by` | `enrichment.audit-actor` | `updated-by` | `OMIT` | `OVERWRITE` | `PERSISTENCE_ENRICHMENT` | `MANAGED_HANDLER` | `MANAGED_HANDLER` |

Cap4k can provide the built-in audit-time Handler because the outer UoW already owns one stable `Instant`.

Cap4k does not invent an Actor, Tenant, or project-specific request-context field value. A project or runtime integration supplies the `enrichment.audit-actor`, `scope.tenant`, or `initialization.request-context` Handler, normally from `ExecutionContextSnapshot`. Use of one of those policy keys without one unique runtime owner fails application startup.

The standard keys define field semantics, not a mandatory entity shape or external security framework.

Audit-time policies use `FixedFqn("java.time.Instant")`. Tenant, request-context, and audit-actor policies use `TargetField` because their concrete domain types are project-specific.

### Custom Policies

Examples of valid extension-defined policies include:

```text
identifier.business-sequence
scope.organization
initialization.region
enrichment.checksum
enrichment.source-system
```

An extension cannot redefine an exact built-in key. Policy namespaces are descriptive rather than globally reserved, but current capability validation still applies. A custom policy requiring extra tables, a custom Repository, different Query Schema generation, or another UoW is outside this SPI.

Extension-defined policies use `MANAGED_HANDLER` when their runtime component assigns a value. `FRAMEWORK` is reserved for built-in policies because it promises that a standard Cap4k runtime owner is installed whenever the policy is used.

## Construction And Admission Contract

### Constructor, Factory Input, And Command Are Different Surfaces

These concepts are not interchangeable:

```text
Entity constructor
Factory input
Command request
```

Allowing a constructor or custom Factory to accept an identifier does not require the generated default Factory or a Command request to expose it.

The generated checked-in Factory remains first-generation source with later `SKIP`. The policy supplies its initial default shape; the user may deliberately edit that Factory for import, idempotency, or migration behavior.

### Creation Input Behavior

```text
OMIT
  -> default Factory input does not expose the field

OPTIONAL
  -> default Factory input exposes an optional value

REQUIRED
  -> default Factory input exposes a required value
```

This policy does not force the Kotlin entity constructor to have the same nullability or parameter list.

### Explicit Identifier Values

Application-side generated identifiers follow:

```text
valid explicit value present
  -> preserve it

value absent
  -> allocate through the selected policy
```

"Framework-managed" therefore means "generate when absent and own later immutability," not "reject every user-provided initial value."

Once an entity is admitted into an aggregate, its identifier is immutable. Existing managed entity identifiers must continue to match Hibernate's original identity.

### Generated Factory Preparation

For an entity constructor that requires an identifier, a generated Factory may allocate the identifier before construction and pass it to the constructor:

```text
prepare managed construction value
  -> invoke constructor
  -> validate at aggregate admission
```

This is generated Factory planning, not a public constructor lifecycle callback. Runtime Initializers are not injected into entity constructors.

For a constructor that permits a missing identifier, admission may allocate it before the entity becomes part of the aggregate.

### Admission Boundaries

Managed initialization runs only at deliberate new-entity boundaries:

```text
Aggregate Factory Supervisor accepts a new root
  -> AGGREGATE_ROOT admission

OwnedEntityList or generated owned-ONE setter accepts a new child
  -> OWNED_CHILD admission
```

Repository loading, repeated observation, and later stabilization do not reinitialize existing entities.

The conceptual admission kind is:

```kotlin
enum class ManagedEntityAdmissionKind {
    AGGREGATE_ROOT,
    OWNED_CHILD,
}
```

### Ordering With Aggregate Lifecycle

The root creation order is:

```text
construct root
  -> resolve ENTITY_ADMISSION managed fields
  -> record root CREATE
  -> invoke optional root onCreate
  -> continue domain behavior
```

Owned children receive managed initialization but never invoke root lifecycle callbacks and never originate Domain Events.

### UoW Validation, Not First Allocation

The current UoW has a Strong ID safety-net assignment during stabilization. The target contract removes first allocation from UoW stabilization.

The target order is:

```text
Factory/admission
  -> initialize or validate new entity managed values

UoW candidate detection
  -> validate required managed values are already present
  -> fail when admission invariants are absent or invalid
```

An event payload or domain behavior cannot be repaired after it already observed a missing identifier. Admission is therefore the last valid initialization boundary; flush is too late.

Database identity remains unavailable until provider flush and refresh/identity observation. That limitation is intrinsic to the selected policy and must be visible in Factory result expectations.

## Generated Managed Field Catalog

### Purpose

The generator emits build-owned runtime metadata for every used managed field. Conceptually:

```kotlin
interface ManagedFieldCatalog {
    val bindings: List<ManagedFieldBinding>
}

data class ManagedFieldBinding(
    val entityType: KClass<*>,
    val fieldName: String,
    val persistencePropertyName: String,
    val columnName: String,
    val targetType: KClass<*>,
    val nullable: Boolean,
    val policyKey: String,
    val role: ManagedFieldRole,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String?,
    val handlerSlot: String?,
    val semanticValueType: KClass<*>,
    val valueAdapterQualifier: String?,
    val persistence: PersistenceParticipation,
    val runtimeSupport: ManagedFieldRuntimeSupport? = null,
)

sealed interface ManagedFieldRuntimeSupport {
    data class ApplicationIdentifier(
        val isAbsent: (Any?) -> Boolean,
        val allocateTarget: (() -> Any)?,
        val validateTarget: (Any) -> Unit,
    ) : ManagedFieldRuntimeSupport

    data class SoftDelete(
        val activeSentinel: Any,
    ) : ManagedFieldRuntimeSupport
}
```

The actual generated type may split build-time serializable descriptors from runtime `KClass` bindings and executable typed support. The semantic information must remain equivalent. `persistencePropertyName` is the exact Hibernate/JPA dirty-property identity used when the UoW validates an enricher's mutation boundary; it is not guessed from a column name at runtime.

`runtimeSupport` carries per-field facts already resolved by generation. UUID7/snowflake/assigned bindings carry typed target allocation and validation support; soft-delete bindings carry the exact active sentinel derived from the approved ID/storage matrix. Standard Initializers consume this support and never re-infer schema semantics from reflection. Startup fails when a built-in policy has missing or incompatible support. Extension-defined policies normally leave it null and keep their validation/production logic in their runtime Handler.

One generated contribution may contain the repository's bindings, following the current `GeneratedOwnIdCatalogContribution` pattern.

### Default Field Access

The default runtime resolves and caches access at application startup:

```text
entity type + canonical field name
  -> collect matching declarations from the entity class and superclasses
  -> require exactly one match
  -> validate target type
  -> establish cached reflective Field or MethodHandle access
```

This supports fields declared:

- directly on the entity;
- on a user base class;
- with private or protected visibility;
- behind a Kotlin `private set` backing field.

Reflection is not used to guess semantics. The exact entity, field, policy, qualifier, and slot come from generated metadata. Reflection only implements access.

Field lookup occurs at startup, not on every UoW round.

Field shadowing is not resolved by "nearest declaration wins." More than one declaration with the canonical name in the hierarchy is ambiguous and fails startup unless one exact custom Accessor replaces reflective resolution for that binding.

### Custom Accessor

Non-standard representations may register an exact runtime accessor:

```kotlin
interface ManagedFieldAccessor {
    val entityType: KClass<*>
    val fieldName: String
    val policyKey: String
    val mutationFootprint: Set<String>

    fun readRaw(entity: Any): Any?
    fun writeRaw(entity: Any, value: Any?)
}
```

Examples include an immutable embedded metadata object replaced through a method, a computed property without a conventional backing field, or another project-specific representation.

`mutationFootprint` contains the exact Hibernate/JPA persistence property names that may become dirty when `writeRaw` runs. The default reflective accessor uses the binding's singleton `persistencePropertyName`. A custom accessor replacing an embedded outer object declares that outer property, not only the logical nested field name. Startup validates every footprint name against provider metadata and rejects an empty or unknown footprint.

A custom Accessor is an explicit trust boundary. If one logical managed value shares an outer persistence property with business state, the mutation guard can protect only that provider-visible outer property. The project owns the correctness of the replacement implementation.

Resolution priority is:

```text
one explicit custom Accessor
  -> cached reflective Accessor
  -> startup failure
```

Generated direct accessors are an optional future optimization, not a required first implementation. Requiring an accessible setter would unnecessarily dictate user entity shape.

### Strong ID Migration

`GeneratedOwnIdAccessor`, `GeneratedOwnIdCatalog`, and `GeneratedOwnIdRegistry` are the current specialized form of this capability.

Implementation may initially adapt them behind the general registry, then remove the specialized public surface after all ID creation and UoW callers use managed bindings. There is no external compatibility requirement for retaining parallel registry families.

## Managed Value Adaptation

### Semantic And Target Types

Handlers produce semantic values. Entity fields use target JVM types. Those types may differ:

```text
Instant -> Instant
Instant -> Long epoch milliseconds
Instant -> LocalDateTime in an explicit zone
UUID -> String
ActorId -> project-specific Strong ID
```

A raw `Any?` write is an internal accessor operation, not the public Handler contract.

### Adapter Contract

Conceptually:

```kotlin
interface ManagedValueAdapter {
    val qualifier: String
    val sourceType: KClass<*>

    fun supports(targetType: KClass<*>): Boolean

    fun adapt(
        value: Any,
        targetType: KClass<*>,
    ): Any
}
```

The resolved field policy names the adapter qualifier when conversion is required.

### Resolution Rules

```text
semantic value assignable to target type
  -> identity assignment; adapter optional and normally absent

types differ
  -> exact adapter qualifier required

missing, duplicate, or incompatible adapter
  -> application startup failure
```

Cap4k does not guess ambiguous conversions:

- `Instant` to `Long` does not imply seconds or milliseconds;
- `Instant` to `LocalDateTime` does not imply a time zone;
- `Instant` to `String` does not imply a format;
- actor or tenant values do not imply one identifier projection.

Explicit reusable adapters may include:

```text
time.epoch-millis
time.epoch-seconds
time.local-date-time-utc
identifier.uuid-string
```

### Time Formatting Boundary

The UoW audit semantic remains `Instant`.

```text
database/JPA field representation
  -> ManagedValueAdapter and JPA mapping

JSON, RPC, and UI representation
  -> serializer, payload mapping, or API adapter
```

Changing external date formatting does not change persistence enrichment.

### Managed Field Handle

Handlers receive a semantic handle rather than a raw accessor:

```kotlin
interface ManagedFieldHandle {
    val fieldName: String
    val policyKey: String
    val handlerSlot: String?
    val semanticValueType: KClass<*>
    val targetType: KClass<*>
    val nullable: Boolean
    val runtimeSupport: ManagedFieldRuntimeSupport?

    fun readTarget(): Any?
    fun adaptSemantic(value: Any?): Any?
    fun matchesSemantic(value: Any?): Boolean
    fun assignSemantic(value: Any?)
}
```

`readTarget()` returns the entity's current JVM field value. It does not promise an implicit inverse conversion for representations such as epoch seconds. Policy-specific absence and explicit-value validation can inspect that target value.

`adaptSemantic()` performs validation and conversion without mutating the entity. `matchesSemantic()` compares the current target value with the adapted semantic value using target-value equality. Context-match Initializers use this non-destructive path rather than temporarily overwriting the field.

For non-null assignment, the runtime validates the semantic input type, performs adapter conversion if required, validates the converted target type, and invokes the cached raw accessor. A null assignment is allowed only for a nullable target, bypasses the adapter, and writes null directly. `adaptSemantic(null)` follows the same nullability rule. Failure diagnostics include entity, field, policy key, semantic type, target type, and adapter qualifier.

## Runtime Registry

### Registry Ownership

Managed field metadata is application metadata, not mutable UoW state. The runtime builds one immutable application-scoped registry from:

- generated `ManagedFieldCatalog` instances;
- resolved binding descriptors carried by those catalogs;
- entity initializers;
- persistence enrichers;
- value adapters;
- explicit custom field accessors.

The registry is assembled and validated during application startup. A UoW reads the completed registry but cannot add or replace definitions.

### Binding Resolution

For every managed field, startup resolves a complete binding:

```text
canonical field policy
  + entity field handle
  + lifecycle owners, when required
  + value adapter, when required
  + JPA projection
  = executable managed field binding
```

Resolution must fail before the application serves requests when any required component is missing or ambiguous.

Examples include:

- a policy references an unknown handler qualifier;
- two runtime handlers claim the same used qualifier;
- a conversion requires an adapter but no exact adapter is named;
- multiple accessors claim the same entity field;
- multiple catalogs claim the same entity persistence property;
- a catalog names a field that reflection cannot locate;
- a slotted qualifier has duplicate or incomplete slots.

Unused handlers and adapters may exist. Only a qualifier referenced by the resolved Canonical Model must have exactly one owner.

### Runtime Owner Resolution

`handlerQualifier` identifies the runtime semantic owner. `handlerSlot` identifies a field inside that owner's schema.

The registry applies these rules per entity type and qualifier:

```text
one field
  -> handlerSlot may be null

multiple fields
  -> every field must declare a non-null slot
  -> slots must be unique
  -> slotted and unslotted fields cannot be mixed
```

For example, a single tenant field uses:

```text
handlerQualifier = scope.tenant
handlerSlot = null
```

Audit time fields share the `enrichment.audit-time` qualifier, and audit actor fields share the `enrichment.audit-actor` qualifier. Each group uses explicit slots so its handler can distinguish creation and update fields.

### Registry API Shape

A conceptual lookup surface is:

```kotlin
interface ManagedFieldRegistry {
    fun bindings(entityType: KClass<*>): List<ManagedFieldBinding>

    fun bindings(
        entityType: KClass<*>,
        lifecycle: ManagedFieldLifecycle,
    ): List<ManagedFieldBinding>
}
```

The lifecycle lookup returns bindings whose lifecycle set contains the requested phase. The exact public visibility is an implementation decision. The important contract is that generated metadata, reflection, adapters, and handlers are resolved once rather than rediscovered during every flush.

### Runtime Component Discovery

Runtime Initializers, Persistence Enrichers, Value Adapters, and custom Accessors are application components. The Spring application context supplies them to the registry during startup.

A third-party runtime artifact may register components through normal Spring Boot auto-configuration, or the application may declare explicit beans. Standard Cap4k runtime artifacts use the same mechanism.

The build-time `PipelineExtensionProvider` instance is never copied into the application runtime. Build-time and runtime halves are connected only by stable policy keys, handler qualifiers, slots, adapter qualifiers, and generated catalog metadata.

### Context Scope

The application registry is immutable and shared. Command execution context and enrichment state are scoped to the outer physical UoW.

Handlers must not depend on ambient `ThreadLocal` state. They receive an immutable execution-context snapshot explicitly so the same semantics work when framework-managed asynchronous execution propagates context to another thread.

## Entity Admission Initialization

### Responsibility

`ManagedEntityInitializer` owns managed policies whose values must be available when a newly created entity formally enters an aggregate.

Conceptually:

```kotlin
interface ManagedEntityInitializer {
    val qualifiers: Set<String>

    fun initialize(
        admission: ManagedEntityAdmissionKind,
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    )

    fun validate(
        context: ManagedEntityInitializationContext,
        fields: ManagedEntityFieldSet,
    )
}

data class ManagedEntityInitializationContext(
    val executionContext: ExecutionContextSnapshot,
)

interface ManagedEntityFieldSet : Iterable<ManagedFieldHandle> {
    val entityType: KClass<*>
}
```

One initializer may own multiple qualifiers. Each qualifier used by the model still has exactly one initializer owner. The runtime passes only handles for that Initializer's declared qualifiers and the one entity currently being admitted or validated.

`initialize` may assign its handles and runs only at admission. `validate` is side-effect free and may only read handles. The UoW invokes `validate` for new entities before persistence so an object graph that bypassed a framework admission boundary still fails when required values are absent, context-bound values disagree, sentinels are invalid, or identifier semantics do not hold. The UoW does not invoke `initialize` as a late repair mechanism.

### Admission Is Not Construction

Cap4k does not assume that object construction is framework controlled.

```text
constructor invocation
  -> object exists

aggregate admission
  -> framework-managed invariants are initialized or validated
  -> object formally belongs to a new aggregate graph
```

This separation permits all of the following:

- the default generated Factory preallocates an identifier and passes it to a constructor;
- a custom Factory passes a caller-supplied valid identifier;
- user code constructs an object and the admission boundary fills an absent application identifier;
- an assigned-identifier policy validates a required constructor value;
- a database-identity policy leaves the identifier absent until persistence.

### Admission Kinds

The initializer receives the `ManagedEntityAdmissionKind` defined by the admission boundary above.

The minimum admission boundaries are:

- aggregate root returned by the Factory Supervisor;
- owned child accepted by `OwnedEntityList`;
- owned ONE child accepted by the generated ownership setter or equivalent ownership boundary.

Loaded entities are never admitted again. Repository materialization must not allocate identifiers, tenant values, or creation audit values.

### Initializer Authority

During `initialize`, an Initializer may read and assign only the `ManagedFieldHandle` instances supplied in its field set. During `validate`, it may only read them. It must not mutate business state, aggregate topology, lifecycle callbacks, or Domain Event queues. The API deliberately does not provide the raw entity instance.

The admission coordinator invokes Initializers one at a time with only their owned qualifier bindings. Framework topology and event guards remain active around admission where applicable. An Initializer requiring unrestricted entity access is outside this SPI and belongs in explicit domain construction code.

### Admission Order

For a new aggregate root:

```text
Factory constructs root
  -> managed root admission
  -> validate required managed values
  -> invoke aggregate onCreate lifecycle, when present
  -> expose root to caller
```

For a new owned child:

```text
construct child
  -> ownership boundary accepts child
  -> managed child admission
  -> validate required managed values
  -> child becomes part of aggregate topology
```

Owned children do not gain aggregate lifecycle callbacks and cannot publish aggregate domain events merely because admission initialized a field.

### Idempotence And Re-Admission

Admission initialization must be idempotent for the same entity instance. Reaching the same ownership boundary twice cannot allocate a second identifier or overwrite a preserved valid value.

Moving an already admitted entity between aggregate roots remains invalid aggregate topology. Idempotence is not permission to re-parent owned entities.

### Explicit Value Enforcement

The initializer enforces the resolved `explicitValue` rule before writing:

- `PRESERVE_IF_VALID`: retain a valid existing value; initialize only when absent;
- `REQUIRE`: require and validate an existing value;
- `REQUIRE_CONTEXT_MATCH`: require absence or equality with the active context, as validated by the owning Initializer;
- `OVERWRITE`: replace the existing value with the authoritative runtime value;
- `FORBID`: exclude the value from ordinary generated input and reject a non-default explicit value at the lifecycle boundary that owns validation.

The Policy Definition selects this behavior but contains no executable validator. Generic runtime code checks nullability and declared types. The resolved Initializer validates policy-specific absence, sentinels, context equality, identifier versions, ranges, and other semantic rules. Generic code must not guess whether an empty string, zero, or project-specific identifier is absent or valid.

Provider- and database-only policies have no admission Handler. Their planner/provider validation defines the permitted transient placeholder, such as a null database identity or an initial version representation. `FORBID` must not reject a framework-generated sentinel or a provider-recognized placeholder merely because the raw field is non-null.

## JPA Persistence Enrichment

### Generalized Contract

The current audit-only hook becomes a managed persistence enrichment contract:

```kotlin
interface JpaPersistenceEnricher {
    val qualifiers: Set<String>

    fun enrich(
        change: JpaAggregateChange,
        context: JpaPersistenceEnrichmentContext,
        fields: JpaManagedFieldSet,
    )
}

enum class JpaManagedOperation {
    CREATE,
    UPDATE,
}

data class JpaManagedEntityFields(
    val entity: Any,
    val operation: JpaManagedOperation,
    val handles: List<ManagedFieldHandle>,
)

interface JpaManagedFieldSet : Iterable<JpaManagedEntityFields> {
    fun forEntity(entity: Any): JpaManagedEntityFields?
}
```

The runtime passes only the fields resolved for the declared qualifiers and for entities participating in the current `JpaAggregateChange`. Handles are also filtered by operation authority: `CREATE` exposes bindings whose insert authority is `MANAGED_HANDLER`, and `UPDATE` exposes bindings whose update authority is `MANAGED_HANDLER`. `forEntity` uses entity identity, not domain equality, so a Handler can correlate each `JpaEntityChange` with its exact bound handles.

This model has insert and update participation only. `JpaManagedFieldSet` exposes enrichment handles for `CREATE` and `UPDATE` candidates, not delete-only entries. A future delete enrichment capability requires an explicit delete authority and provider contract rather than overloading update semantics.

`JpaPersistenceAuditEnricher` and `JpaPersistenceAuditContext` are renamed or replaced by this general contract. Audit is a standard implementation, not the name of the extension point.

### Stabilization Sequence

The UoW preserves the agreed two-pass change recognition:

```text
candidate change recognition
  -> managed persistence enrichment
  -> final dirty recognition
  -> flush
```

Candidate recognition answers which aggregate entries and entities already have a persistence reason to participate. Enrichment may update declared managed fields on those candidates. Final recognition computes the effective persistence change after enrichment.

This order prevents an audit timestamp from turning an otherwise clean loaded aggregate into a database update.

### Candidate Boundary

A loaded aggregate with no business, topology, lifecycle, or explicit persistence change is not an enrichment candidate.

Therefore:

```text
load aggregate
  -> inspect state
  -> make no change
  -> save/complete Command
  -> no audit mutation
  -> no SQL update
```

User code directly changing an audit or other managed field is not a special case Cap4k must repair. If that change makes the entity dirty, normal dirty detection and policy validation apply. The framework does not attempt to distinguish deliberate misuse from a business change by rewriting history.

### Enrichment Authority

An enricher may mutate only the managed bindings declared for its owned qualifiers. It must not mutate:

- aggregate topology;
- non-managed business fields;
- domain event queues;
- lifecycle state unrelated to its policy;
- another qualifier's fields.

The UoW snapshots dirty properties immediately before and after each individual enricher invocation. The snapshot covers scalar properties, to-one associations, and collection identity/content/dirty state for every provider-managed entity present before or introduced during the invocation. The delta for that call must be contained in the mutation footprints of bindings owned by that enricher's declared qualifiers. Validation is not deferred until all enrichers finish and does not use the union of every enricher's permissions; one enricher therefore cannot hide an unauthorized write inside another enricher's allowed field set.

An undeclared mutation is a framework contract violation and fails the UoW before flush.

Existing topology and event guard checks remain in force. Generalizing the enricher does not widen its authority over aggregate behavior.

### Idempotence

Persistence enrichment must be idempotent within one outer UoW. Repeated stabilization caused by synchronous event handling or an explicit additional save cannot continually advance timestamps or allocate new values.

The enrichment context supplies stable values for the physical UoW:

```kotlin
data class JpaPersistenceEnrichmentContext(
    val timestamp: Instant,
    val executionContext: ExecutionContextSnapshot,
)
```

`timestamp` is semantic time. Storage conversion is handled by the field adapter. An enricher must derive values deterministically from the stable context and current candidate state; it must not read another clock or generate a fresh random value on each round. This iteration does not expose a generic mutable enrichment-state bag.

### Ordering

Cap4k does not define semantic ordering among distinct handler qualifiers. Registry iteration order is not a contract.

The registry does not consume Spring `Ordered`, `@Order`, or injection-list order as managed-field semantics. Implementations may use an internal deterministic iteration order for reproducibility, but applications cannot coordinate handlers by changing Spring order metadata.

If multiple fields require coordinated calculation or ordering, they must belong to one qualifier and be distinguished by slots. That handler owns the internal order atomically.

This avoids a hidden dependency graph such as "tenant enrichment must run before audit actor enrichment." Cross-policy dependencies must instead be represented by shared execution context or one combined policy owner.

## Standard Audit Policy

### Audit Is A Default Handler, Not A Base Class

Cap4k supplies standard audit policy definitions and the default audit-time JPA persistence enricher. Audit-actor values still require one project or runtime-integration owner for the `enrichment.audit-actor` qualifier. Cap4k does not require or generate a standard audit base class.

The default entity template may declare audit fields directly. Users may place the same mapped fields in their own superclass, custom template, or entity body. The generated catalog supplies the exact logical field binding, and startup requires unambiguous field/accessor resolution across the entity hierarchy. Declaration placement does not change policy semantics.

### Audit Slots

The initial audit handler recognizes policy-owned slots equivalent to:

```text
created-at
updated-at
created-by
updated-by
```

The standard policy keys remain specific, for example:

```text
enrichment.audit-time.created-at
enrichment.audit-time.updated-at
enrichment.audit-actor.created-by
enrichment.audit-actor.updated-by
```

They may share an implementation owner while retaining exact policy keys and separate diagnostics.

### Create And Update Behavior

For a newly persisted entity, the handler fills configured creation and update audit fields according to each policy definition. For an existing entity classified as `UPDATE`, it fills only configured update fields.

`DELETE` is not an audit-update operation. Hibernate `@SQLDelete`, orphan removal, and physical delete paths do not automatically include `updated-at` or `updated-by`, and this design does not pretend otherwise. Delete auditing requires an explicit delete/audit-history policy and provider projection in a later design.

The current UoW v2 aggregate boundary remains:

- child-only changes make the aggregate a persistence candidate;
- the changed child may receive its own update audit values;
- the root's version and root audit fields do not advance automatically merely because a child changed;
- changing root version semantics requires a separate design decision and is not smuggled into managed fields.

### Actor Availability

Actor policies read from the explicit execution-context snapshot and use `OVERWRITE`. A non-null actor binding requires the Handler to produce a valid value. For a nullable binding, a missing actor is assigned as null, so a caller-prepopulated value is not trusted accidentally. Any system-actor fallback must be explicit Handler configuration rather than a generic Cap4k guess.

The generic managed-field runtime does not assume that every command has a human actor, nor that actor identity is a `String`.

## Context-Bound Managed Fields

### Tenant And Similar Scope Values

`scope.tenant` is the standard example of an admission-time context binding policy:

```text
new entity admission
  -> read active tenant from propagated execution context
  -> fill an absent tenant field or validate an explicit value
  -> preserve the value for persistence
```

The exact built-in behavior is `OMIT + REQUIRE_CONTEXT_MATCH`: the default Factory does not expose tenant input; admission fills an absent value, preserves an equal value from a custom construction path, and rejects a mismatch. An active tenant context value is required whenever `scope.tenant` is used, even when the target column is nullable.

The same mechanism can support project-defined scope dimensions such as organization, workspace, region, or legal entity. Cap4k does not hard-code a single universal hierarchy of isolation scopes.

### Propagation

Context-bound policies consume Cap4k's explicit execution-context propagation. They must work for:

- the initial synchronous Command thread;
- framework-managed asynchronous Query or Capability execution;
- outbound RPC adapters that intentionally map selected context values to request metadata;
- inbound adapters that reconstruct the execution-context snapshot.

An in-process `ThreadLocal` alone is insufficient and is not the contract. RPC propagation remains an adapter concern because header names, trust boundaries, authentication, and cross-service identity formats are transport-specific.

### Isolation Boundary

`scope.tenant` does not mean Cap4k automatically filters every query or selects a database.

```text
managed tenant field
  -> context binding and persistence validation

row filtering / discriminator SQL
  -> ORM plugin or application persistence configuration

schema or database routing
  -> ORM/data-source infrastructure

cross-service tenant trust
  -> transport and security adapters
```

Cap4k must remain compatible with Hibernate or Spring multi-tenancy plugins by avoiding assumptions that bypass their sessions, filters, or transaction integration. Full database isolation is not part of the managed-field policy feature and does not justify a public Persistence Provider SPI now.

## JPA Mapping Projection

### Projection Is Derived

The JPA generator derives persistence annotations and column participation from the resolved managed policy. It does not treat JPA flags as the source of semantic truth.

The derivation considers at least:

- managed role;
- value authority;
- lifecycles;
- mutability after entity admission;
- insert participation;
- update participation;
- target field type;
- database generation behavior.

This replaces the current shortcut where a generic managed field becomes `insertable = false, updatable = false`. A framework-managed value may need to be written on insert, on update, or both.

### Representative Projection

The initial standard policies project approximately as follows:

| Policy | Value producer | Insert | Update | Representative JPA behavior |
|---|---|---:|---:|---|
| `identifier.uuid7` | caller or initializer | yes | no | `@Id`; assigned before persist |
| `identifier.snowflake` | caller or initializer | yes | no | `@Id`; assigned before persist |
| `identifier.assigned` | caller | yes | no | `@Id`; required before persist |
| `identifier.database-identity` | database/provider | no application assignment | no | `@Id` plus identity generation |
| `version` | JPA provider | provider controlled | provider controlled | `@Version` |
| `soft-delete` | framework initializer then provider transition | yes, active sentinel | provider controlled | current soft-delete mapping |
| `scope.tenant` | initializer/context | yes | no | normal persistent column |
| `enrichment.audit-time.created-at` | enricher | yes | no | normal persistent column |
| `enrichment.audit-time.updated-at` | enricher | yes | yes | normal persistent column |
| `database.generated-on-insert` | database | database generated | no | generated-on-insert metadata |
| `database.generated-always` | database | database generated | database generated | generated/readback metadata |

The table defines intent, not a promise to express every row with the same annotation across all Hibernate versions. The JPA planner owns the exact Hibernate-compatible rendering and diagnostics.

### Value Authority Is Not Column Mutability

These are independent questions:

```text
Who may choose the semantic value?
  -> valueAuthority and explicitValue

When does the framework produce or validate it?
  -> lifecycles and handler qualifier

Does the ORM include the column in INSERT or UPDATE?
  -> persistence participation
```

For example, `updated-at` has framework value authority but participates in SQL updates. `database.generated-always` has database authority and is omitted from application writes. `identifier.assigned` has caller authority but is immutable after admission.

### User Write Surfaces

The planner derives default Factory exposure from `creationInput` and `explicitValue`, not from JPA participation.

Generated constructor invocation is a separate construction plan. It decides whether a value is passed, assigned by a generated property initializer, or observed later from the provider. `creationInput` neither requires nor forbids a constructor parameter, and it does not constrain a user-edited checked-in Factory or custom constructor.

That independence does not permit a semantically dead generated Factory input. A non-`OMIT` creation input is valid only when INSERT authority is application-visible (`CALLER`, `FRAMEWORK`, or `MANAGED_HANDLER`). It cannot pair with `OVERWRITE` or `FORBID`, and `OPTIONAL` cannot pair with `REQUIRE`. Database/provider-owned values remain `OMIT`; invalid combinations fail during policy resolution before artifact planning.

The generator may still derive an internal write-surface plan for templates, but that plan is an output of policy resolution. `SpecialFieldWritePolicy` must not remain an independent source of managed-field truth.

Generated behavior APIs must not expose arbitrary setters for immutable managed values merely because JPA needs field access. Persistence accessibility and domain API accessibility remain separate concerns.

### Provider-Specific Boundary

The initial implementation is intentionally JPA/Hibernate specific:

- Canonical managed semantics remain independent of annotation strings;
- the JPA planner projects those semantics into the only supported persistence backend;
- Hibernate-specific generated-value and dirty-detection details stay inside the JPA module;
- provider-specific diagnostics identify the unsupported policy/type combination.

This does not create a public multi-provider Persistence Provider SPI. A second real provider or a proven external provider demand is required before extracting that larger boundary.

## End-To-End Flows

### Generated UUID7 Identifier

```text
DB source: @Managed=identifier.uuid7
  -> source snapshot stores raw policy key
  -> policy resolution chooses standard uuid7 definition
  -> Canonical Model resolves creationInput=OMIT
  -> generated Factory preallocates a Strong ID when needed
  -> constructor receives the ID without exposing it as normal Factory input
  -> root admission preserves and validates the ID
  -> onCreate observes the final ID
  -> JPA inserts the ID column
  -> UoW validates presence but never performs first allocation
```

### Explicit UUID7 Identifier In A Custom Constructor

```text
user/custom Factory constructs entity with a valid ID
  -> root or owned-child admission sees an existing value
  -> PRESERVE_IF_VALID validates and keeps it
  -> no second ID is allocated
  -> persistence writes the supplied ID
```

This supports user-directed constructors without forcing every identifier through a generated Factory parameter.

### Assigned Identifier

```text
DB source: @Managed=identifier.assigned
  -> creationInput=REQUIRED for the default Factory
  -> caller/custom Factory supplies ID
  -> admission validates presence and type
  -> absence fails before aggregate lifecycle or persistence
  -> JPA inserts the ID and never updates it
```

### Database Identity Identifier

```text
DB source: @Managed=identifier.database-identity
  -> normal explicit input is FORBID
  -> entity reaches persistence without an application ID
  -> JPA/Hibernate performs identity insert
  -> provider populates the generated ID
  -> post-persist aggregate state exposes the assigned value
```

Cap4k does not emulate database identity with an application initializer.

### New Root With Audit And Tenant

```text
Factory constructs root
  -> root admission allocates/preserves application ID
  -> root admission binds and validates tenant context
  -> onCreate runs with ID and tenant already visible
  -> root is registered with the active Command UoW
  -> candidate change recognition sees CREATE
  -> audit enricher assigns stable created/updated time and actor values
  -> final recognition validates declared enrichment mutations
  -> flush inserts all application-managed columns
```

Audit fields that intentionally belong to persistence enrichment are not promised to be visible inside `onCreate`. A project that requires creation metadata during domain behavior must model that need as an admission-time policy instead of silently changing the audit policy lifecycle.

### Existing Root Update

```text
repository returns managed aggregate
  -> command changes a business field
  -> automatic Command UoW completion recognizes candidate UPDATE
  -> audit enricher assigns updated-at/updated-by using stable UoW context
  -> final dirty recognition includes business and declared audit fields
  -> flush performs update
```

Calling `save()` explicitly remains an advanced stabilization boundary where supported, but is not required for ordinary Command completion.

### Read Without Change

```text
repository returns managed aggregate
  -> command or query only reads it
  -> no candidate persistence change
  -> no persistence enricher invocation for that aggregate
  -> no audit-only dirty state
  -> no update SQL
```

Using an aggregate repository from a Query is discouraged as a write-model dependency but remains technically possible. It does not convert the Query into a write UoW and must not trigger managed persistence enrichment.

### New Owned Child

```text
construct child
  -> OwnedEntityList/add ownership boundary
  -> child admission allocates/preserves its application ID
  -> context-bound admission fields are initialized
  -> child joins aggregate topology
  -> UoW recognizes child CREATE
  -> audit enricher handles the child's persistence fields
  -> final recognition flushes child persistence
```

The child does not receive aggregate `onCreate`, cannot publish aggregate domain events through an entity callback, and does not automatically advance root audit or version fields.

### Synchronous Event Causes Another Change

```text
first stabilization round
  -> recognize candidates
  -> enrich with stable UoW context
  -> flush
  -> release this round's synchronous events

synchronous handler runs in the same outer REQUIRED UoW
  -> executes another Command or changes another aggregate
  -> new changes are registered

next stabilization round
  -> recognize new candidates
  -> reuse the same timestamp and execution-context snapshot
  -> enrichment remains idempotent
  -> flush again
```

Only the outer Command coordinator ends the UoW and transaction. Managed-field policies do not introduce nested logical UoWs or `REQUIRES_NEW` semantics.

### Database-Generated Column

```text
DB source: @Managed=database.generated-always
  -> Canonical Model assigns database value authority
  -> default Factory omits the field
  -> admission does not initialize it
  -> JPA projection omits application writes
  -> provider/database generates the value
  -> Hibernate refresh/readback behavior follows the resolved JPA projection
```

The generated value may be unavailable before flush. Domain behavior that requires the value earlier must not use a database-generated policy.

## Failure And Diagnostics Contract

### Source-Time Failures

DB source diagnostics must report table and column location for:

- malformed policy keys;
- removed annotation usage;
- conflicting explicit policy declarations;
- an identifier column with no resolvable identifier policy;
- duplicate exact column defaults;
- a default referencing a syntactically invalid key.

The parser retains unknown but syntactically valid custom keys for the resolution phase rather than rejecting extension-owned policies prematurely.

### Resolution-Time Failures

Policy resolution reports:

- unknown policy key;
- duplicate policy definition owners;
- incompatible role or lifecycle combination;
- invalid `creationInput` and `explicitValue` combination;
- unresolvable fixed-FQN semantic type or missing adapter qualifier when conversion is required;
- invalid qualifier/slot schema;
- unsupported JPA projection;
- conflicting explicit column policy and model invariant.

A diagnostic should include the exact policy key, selection provenance, and definition owner. Conflicts name both extension and contribution IDs for every competing external definition.

### Generation-Time Failures

Generation fails when a resolved plan cannot be rendered safely, including:

- required Factory input without a renderable Kotlin type;
- unresolved constructor dependency;
- generated catalog collision;
- inherited declaration ambiguity;
- conflicting generated member names;
- an impossible JPA annotation combination.

Template rendering must not contain fallback policy inference. If a template receives incomplete managed-field planning, that is a generator defect.

### Startup Failures

Runtime startup fails for incomplete executable bindings:

- missing or duplicate handler owner;
- missing or duplicate adapter;
- adapter type mismatch;
- missing or incompatible built-in runtime support;
- missing entity field;
- inaccessible field without a custom accessor;
- duplicate custom accessor;
- empty or unknown custom-accessor mutation footprint;
- catalog and loaded entity type mismatch;
- invalid slot schema after all catalogs are assembled.

These failures happen at startup, not on the first production command.

### UoW Failures

The UoW fails before flush when:

- an admission-required managed value is absent;
- an explicit value violates the policy;
- a persistence enricher mutates an undeclared field;
- a handler mutates topology, events, or business state;
- final dirty state violates provider participation rules;
- a required execution-context value is absent.

Diagnostics must identify aggregate root type and ID when available, entity type, field, policy key, lifecycle phase, and owning qualifier.

## Breaking Migration

### No Compatibility Layer

Cap4k has no external user compatibility requirement for the current experimental managed-field annotations and SPIs. The implementation should remove obsolete surfaces instead of maintaining aliases that create two sources of truth.

In particular, do not support both:

- `@IdStrategy=uuid7` and `@Managed=identifier.uuid7`;
- `@Managed=system` and exact managed policy keys;
- `@Inherited` and an implicit declaration-placement flag;
- `cap4kAddon` and `cap4kPipelineExtension`;
- `ArtifactAddonProvider` as a directly discovered installation root and as a contribution;
- `JpaPersistenceAuditEnricher` and `JpaPersistenceEnricher` as parallel long-term APIs.

Historical documentation may mention the removed forms as history. Active public documentation, fixtures, skills, and examples must use only the new contract.

### DB Source Migration

Replace the current source snapshot concepts:

```text
DbColumnSnapshot.idStrategy
DbColumnSnapshot.managedRole
DbColumnSnapshot.inherited
DbIdStrategy
DbManagedRole
```

with an exact raw managed policy reference and its source origin.

Replace parser support for:

```text
@IdStrategy=...
@Managed=system|scope|deleted|version
@Inherited
```

with:

```text
@Managed=<exact-policy-key>
```

Physical `primaryKey`, auto-increment/generation facts exposed by JDBC, nullability, default expressions, and relation facts remain normal DB metadata. The implementation must not erase a physical fact merely because a managed policy also interprets it.

### Configuration Migration

Replace role- or strategy-based defaults with policy-key defaults.

Conceptually:

```kotlin
managedFields {
    identifierDefaultPolicy.set("identifier.uuid7")

    columnPolicyDefaults.put("created_at", "enrichment.audit-time.created-at")
    columnPolicyDefaults.put("updated_at", "enrichment.audit-time.updated-at")
    columnPolicyDefaults.put("tenant_id", "scope.tenant")
}
```

The exact DSL name may follow existing Gradle conventions, but values are exact policy keys. A list named `managedDefaultColumns` is insufficient because membership cannot express which policy owns each column.

Default matching follows the already defined precedence:

```text
explicit column @Managed
  -> exact configured column policy
  -> identifier/default role policy when applicable
  -> unresolved or failure when a policy is required
```

Case normalization for database identifiers follows the existing DB source identifier rules. Policy keys themselves are case-sensitive canonical lowercase values.

### Canonical Model Migration

Remove broad managed roles and inheritance flags from `FieldModel`. Add a raw-to-resolved policy path that makes the full resolved policy available to planners. Record field-selection provenance separately from built-in or extension/contribution definition ownership.

The resolved model becomes the single input for:

- generated construction and Factory planning;
- Behavior write-surface planning;
- JPA annotation and column participation planning;
- generated managed catalogs;
- runtime handler and adapter validation;
- diagnostics and plan evidence.

Any compatibility field such as `writePolicy` may exist transiently inside one implementation PR, but it must be derived from the resolved policy and removed before the migration is considered complete.

### Pipeline Extension Migration

Replace the Gradle installation bucket and root service:

```text
cap4kAddon
ServiceLoader<ArtifactAddonProvider>
```

with:

```text
cap4kPipelineExtension
ServiceLoader<PipelineExtensionProvider>
```

An extension descriptor contributes zero or more typed capabilities. The initial capability families are:

- `ArtifactAddonProvider`;
- `ManagedFieldPolicyProvider`.

Existing artifact addon behavior is retained as a contribution. Its provider ID, options, plan evidence, and template resource namespace continue to use the artifact addon contribution ID.

The initial loader uses one task-scoped child classloader for all resolved Pipeline Extensions, matching current operational behavior. It validates extension IDs and contribution IDs centrally and closes the classloader after the task.

The `only-engine` extension and every functional fixture using `cap4kAddon` must migrate in the same implementation series. Because build-time and runtime dependencies remain separate, examples must continue to declare both when both are required.

### Generated Metadata Migration

Replace the Strong-ID-only metadata path:

```text
GeneratedOwnIdAccessor
GeneratedOwnIdCatalog
GeneratedOwnIdRegistry
```

with the generic managed-field catalog and registry.

A staged internal adapter is acceptable while moving callers, but the final architecture has one runtime field-binding registry. Identifier initialization, UoW validation, audit enrichment, and scope binding must not build parallel reflective caches.

The generic catalog retains generated Strong ID allocator and validator functions through `ManagedFieldRuntimeSupport.ApplicationIdentifier`. That information belongs to the identifier policy binding, not a second global registry.

### Runtime Migration

Move first application-identifier allocation out of UoW stabilization and into entity admission. The UoW retains validation that required identifiers are present before persistence.

Replace audit-specific auto-configuration with:

- managed field catalog assembly;
- field accessor and adapter validation;
- entity initializer registration;
- JPA persistence enricher registration;
- standard audit policy handlers;
- standard identifier handlers.

Preserve the existing UoW sequence, event frontier, outer REQUIRED transaction ownership, and Hibernate dirty-detection integration.

Retain the existing `JpaAggregateChange`, `JpaEntityChange`, and operation types. Only the audit-specific Enricher and context names are generalized. Stop consuming `Ordered`/`@Order` for Enricher semantics and migrate tests that currently imply ordered audit execution.

### Documentation And Skill Migration

Update at least:

- public DB annotation documentation;
- generator configuration documentation;
- addon/SPI documentation, renamed for Pipeline Extensions;
- aggregate Factory and Strong ID authoring guidance;
- audit and UoW runtime guidance;
- repository-local authoring skills and routing references;
- official project fixtures;
- `only-engine` integration examples.

Do not edit archived Comet evidence or historical specs to rewrite their recorded state.

## Implementation Sequence

The implementation should be split into reviewable, compiling slices. Every slice must leave one authoritative path rather than introduce a new path while indefinitely retaining the old one.

### Slice 1: Pipeline Extension Installation

1. Introduce `PipelineExtensionProvider`, descriptor validation, and typed contributions.
2. Move `ArtifactAddonProvider` behind the contribution model.
3. Rename the Gradle configuration to `cap4kPipelineExtension`.
4. Preserve task-scoped classloading, provider options, plan evidence, and template isolation.
5. Migrate functional fixtures and the `only-engine` extension.
6. Remove direct addon discovery and the old configuration name.

This slice establishes the build-time discovery channel needed by custom managed policy definitions.

### Slice 2: Source And Canonical Policy Model

1. Add policy key parsing and source-origin capture.
2. Replace the old DB annotations and source snapshot enums.
3. Add standard policy definitions and extension-provided definition collection.
4. Add the fixed policy-resolution phase.
5. Add resolved policy types, plan evidence, and collision diagnostics.
6. Migrate source, normalize, assembly, and pipeline API tests.

No template should consume unresolved raw keys after this slice.

### Slice 3: JPA And Artifact Planning

1. Derive construction/write surfaces from resolved policy.
2. Derive JPA insert/update/generated behavior independently.
3. Migrate soft delete, version, identity, uuid7, and snowflake planning.
4. Remove `SpecialFieldWritePolicy` as a policy source.
5. Generate `ManagedFieldCatalog` metadata for root and owned entity fields.
6. Update rendered fixtures and golden outputs.

This slice must include a regression proving that an enrichment-managed audit field is JPA-writable while excluded from ordinary business write surfaces.

### Slice 4: Runtime Registry And Entity Admission

1. Assemble generated catalogs into one registry.
2. Implement cached reflective access and exact custom accessor override.
3. Add value adapter resolution and startup validation.
4. Implement root and owned-child admission boundaries.
5. Move uuid7 and snowflake first allocation to admission.
6. Preserve and validate the generated soft-delete active sentinel during admission.
7. Preserve explicit valid IDs and validate assigned/database identity behavior.
8. Invoke side-effect-free managed invariant validation for every new entity before persistence.
9. Remove the specialized Strong ID registry after all callers migrate.

Lifecycle ordering tests must prove identifiers are visible before root `onCreate` and that loaded entities are never reinitialized.

### Slice 5: General JPA Persistence Enrichment

1. Replace audit-specific enrichment types with general managed enrichment.
2. Bind qualifiers and slots to catalog fields.
3. Implement standard time and actor audit handlers.
4. Pass a stable outer-UoW timestamp and execution-context snapshot.
5. Enforce declared-field mutation boundaries.
6. Preserve candidate-before-enrich-before-final dirty detection.
7. Remove audit-only auto-configuration surfaces.

This slice must prove that a clean loaded aggregate does not receive audit mutations or an SQL update.

### Slice 6: Context-Bound Policies And Documentation

1. Complete reusable execution-context access for admission handlers and wire a `scope.tenant` integration fixture.
2. Prove project-supplied tenant Handler registration plus strict explicit/context mismatch diagnostics.
3. Verify compatibility with existing Hibernate session and transaction integration.
4. Document that row filtering and database routing remain external persistence configuration.
5. Complete public docs, skill, template, fixture, and integration migrations.

This slice does not implement a multi-tenant ORM plugin.

## Verification Strategy

### Source Parser Tests

Cover:

- every standard policy key;
- custom syntactically valid policy keys;
- invalid capitalization, separators, empty segments, and whitespace;
- removed annotations with replacement diagnostics;
- precedence between explicit policy and exact column defaults;
- physical primary-key metadata retained alongside identifier policy;
- multiple physical primary-key columns rejected as unsupported.

### Policy Resolution Tests

Cover:

- built-in and extension-provided definitions;
- duplicate key rejection with extension identities;
- all `creationInput`, `explicitValue`, lifecycle-set, and authority combinations used by standards;
- slot cardinality and uniqueness rules;
- qualifier reuse across Handler kinds rejected;
- adapter-required semantic/target type combinations;
- unresolved and incompatible JPA policy diagnostics.

### Pipeline Extension Tests

Cover:

- one extension contributing only artifacts;
- one extension contributing only managed policies;
- one extension contributing both;
- a non-empty resolved classpath with no provider;
- a provider discovered from a transitive classpath component;
- duplicate extension IDs;
- duplicate contribution IDs;
- unsupported SPI version;
- ServiceLoader construction failure;
- preserved addon template namespace and option routing;
- classloader closure and task isolation;
- absence of accidental runtime dependency injection.

### Generator Tests

Use focused planner tests plus rendered fixtures for:

- generated and explicit uuid7/snowflake IDs;
- assigned identifiers;
- database identity;
- version and soft delete;
- soft-delete active sentinel generated, admitted, and included in INSERT;
- audit fields writable by JPA but omitted from ordinary Factory input;
- tenant fields initialized before persistence;
- fields declared in a user superclass;
- private fields with reflective access;
- exact custom accessor override;
- owned ONE and MANY child catalog entries.

### Runtime Registry Tests

Cover successful lookup and every startup failure category:

- missing/duplicate handlers;
- missing/duplicate/incompatible adapters;
- missing/incompatible identifier or soft-delete runtime support;
- missing/private/inherited fields;
- duplicate custom accessors;
- custom-accessor mutation footprints;
- ambiguous field shadowing;
- slotted and unslotted qualifier conflicts;
- deterministic diagnostic evidence.

### Entity Admission Tests

Cover:

- default generated Factory ID preparation;
- custom constructor valid explicit ID preservation;
- absent ID initialization at root admission;
- child ID initialization through `OwnedEntityList` and owned ONE boundary;
- assigned ID required failure;
- database identity explicit-value rejection;
- no second allocation on repeated admission;
- no initialization during repository load;
- each Initializer receives only handles for its declared qualifiers and no raw entity access;
- side-effect-free UoW validation rejects an invalid new entity without late initialization;
- context-match validation works through non-destructive semantic adaptation;
- root `onCreate` observes initialized ID and context fields;
- owned children do not gain aggregate callbacks or event authority.

### UoW And JPA Integration Tests

Use the current JPA starter owner fixtures to cover:

- candidate change then enrichment then final dirty detection;
- clean read produces no enrichment-only update;
- root business update writes update audit values;
- child-only update writes child audit values without root version/audit advancement;
- entity-identity field grouping exposes create/update handles only for the matching operation;
- create writes creation and update audit values;
- delete-only entries do not receive update audit handles or mutate audit columns;
- multiple stabilization rounds reuse stable context values;
- undeclared enricher mutation fails before flush;
- per-enricher delta validation prevents one Handler from borrowing another Handler's mutation footprint;
- changing Spring `Ordered` or `@Order` metadata does not change any guaranteed Enricher behavior;
- topology or event mutation remains rejected;
- database-generated and provider-owned fields remain observable correctly;
- optimistic locking and soft delete behavior remain unchanged.

Where SQL participation is material, tests should inspect generated SQL or database state rather than infer behavior only from in-memory values.

### Context Tests

Cover:

- synchronous Command context binding;
- framework-managed async context propagation;
- tenant mismatch failure;
- missing required context failure;
- multiple custom scope qualifiers without a hard-coded hierarchy;
- no claim that Cap4k filters tenant queries automatically.

### Repository-Wide Checks

At completion:

```powershell
./gradlew check
rg -n "@IdStrategy|@Inherited|DbIdStrategy|DbManagedRole|cap4kAddon|JpaPersistenceAuditEnricher|GeneratedOwnIdRegistry" `
  --glob '!docs/superpowers/**' `
  --glob '!docs/comet/archive/**'
```

The active-code search should be empty except for intentionally named migration diagnostics or historical compatibility test inputs. Such exceptions must be individually justified.

## Rejected Alternatives

### Keep `@IdStrategy` Beside `@Managed`

Rejected because allocation strategy is a managed identifier policy, not a separate annotation family. Keeping both creates precedence and composition questions immediately.

### Use Broad Values Such As `system` Or `scope`

Rejected because a role cannot determine creation input, explicit-value handling, lifecycle, semantic type, handler owner, adapter, or JPA participation.

### Treat Every Managed Field As JPA Read-Only

Rejected because audit, tenant, application identifier, and soft-delete values may be framework owned while still requiring SQL writes.

### Require A Cap4k Audit Base Class

Rejected because it dictates entity inheritance without adding policy information. The catalog and accessor model can find fields wherever the user's code declares them.

### Require Generated Direct Field Accessors

Rejected for the first implementation because cached reflection supports private and inherited fields without forcing source shape. Explicit custom accessors cover exceptional models. Generated direct access may be added later as a performance optimization.

### Initialize Identifiers Only During UoW Flush

Rejected because domain behavior and `onCreate` may need an application identifier before persistence. UoW remains the final validator, not the first allocator.

### Run Initializers During Every Repository Load

Rejected because loading is observation of persistent state, not entity creation. Reinitialization would hide corrupt data and could change identity or scope.

### Let Enrichment Create A Dirty Change By Itself

Rejected because a clean read would become an audit-only update. Candidate recognition must precede enrichment.

### Order Independent Enrichers

Rejected because cross-enricher order becomes an implicit public dependency graph. Coordinated fields belong to one qualifier/handler; shared request facts belong in execution context.

### Let Extensions Inject Arbitrary Pipeline Stages

Rejected because it reopens stage ownership and canonical consistency. Extensions contribute typed declarations consumed by fixed framework-owned phases.

### Discover Multiple Independent Service Families From `cap4kAddon`

Rejected because it duplicates provider identity, options, classloading, compatibility, and failure rules. One extension installation root owns typed contributions.

### Build A Full Persistence Provider SPI Now

Rejected because the only implemented runtime is Spring Data JPA plus Hibernate, and no second concrete provider exists to prove a stable abstraction. Managed semantic policies and JPA projection are separated internally so a later extraction remains possible.

### Make Tenant Policy Equal Full Tenant Isolation

Rejected because field binding, query filtering, connection routing, RPC trust, and authorization are separate concerns. Cap4k provides the managed context-bound field capability without pretending to solve all isolation layers.

## Fixed Decisions

The following decisions are closed for this implementation:

1. The DB annotation is `@Managed=<exact-policy-key>`.
2. Policy keys use lowercase kebab-case dot-separated segments.
3. `@IdStrategy`, broad `@Managed` roles, and `@Inherited` are removed without compatibility aliases.
4. Raw policy capture and canonical policy resolution are separate fixed phases.
5. Default Factory input policy does not prohibit a custom constructor or Factory from supplying a valid value.
6. Application identifiers are initialized no later than aggregate admission and validated again before persistence.
7. Loaded entities are never initialized as new entities.
8. Root and owned entities both receive managed field handling; owned children do not receive aggregate lifecycle/event authority.
9. Persistence enrichment runs only after candidate recognition and before final dirty detection.
10. Enrichment cannot make a clean aggregate dirty solely to update audit information.
11. Child-only changes do not automatically advance root version or root audit fields.
12. Runtime handlers own exact qualifiers; slots disambiguate multiple fields under one qualifier.
13. Separate qualifiers have no guaranteed execution order.
14. Semantic values and target JVM representations are connected only by identity assignment or an explicit adapter.
15. Audit semantic time is `Instant`; storage and external serialization formats remain separate adaptations.
16. No Cap4k audit base class is required.
17. Cached reflection is the default field access implementation; an exact custom accessor overrides it.
18. Pipeline Extensions are the installation unit; artifact addons and managed policy providers are typed contributions.
19. Pipeline stages remain fixed and extension code cannot insert arbitrary phases.
20. One task-scoped extension classloader is sufficient until a concrete conflict proves otherwise.
21. Build-time extension dependencies and application runtime dependencies remain explicit and separate.
22. Spring Data JPA plus Hibernate remains the only persistence runtime for this design.
23. `scope.tenant` defines standard context-binding semantics, but the project or runtime integration supplies the tenant value Handler.
24. Tenant managed fields provide context binding and validation, not automatic database isolation.
25. There is no public multi-ORM Persistence Provider SPI in this iteration.
26. Checked-in Factory and Behavior files retain first-generation ownership and subsequent `SKIP` behavior.
27. Managed lifecycles are a non-empty set; admission and persistence enrichment cannot coexist in one policy, while admission may coexist with provider/database ownership.
28. Semantic type is always explicit as target-field type or fixed FQN; there is no unspecified runtime type.
29. Soft delete preserves generated active-sentinel initialization and INSERT participation, then delegates the tombstone transition to the provider.
30. Enricher mutation authority is validated around each Handler call using that Handler's exact provider-property footprints.
31. Update audit policies do not apply to delete-only operations.
32. The initial extension loader discovers providers from one resolved classpath and does not promise one provider per direct artifact.
33. Admission initialization may assign managed values; UoW pre-persist validation is a separate side-effect-free operation and never performs late repair.
34. One handler qualifier belongs to exactly one runtime Handler kind across the application model.
35. Managed semantic comparison adapts without mutation; context matching never requires a temporary write.
36. Spring `Ordered`, `@Order`, and injection-list order are not managed-field semantics.
37. Policy selection provenance and policy definition ownership are recorded separately.

## Related Documents

Read these documents for preserved surrounding contracts:

- `docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md` for fixed pipeline ownership;
- `docs/superpowers/specs/2026-05-10-cap4k-artifact-addon-spi-and-only-engine-enum-translation-design.md` for the artifact contribution behavior retained inside Pipeline Extensions;
- `docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md` for the base discriminator and provider transition behavior;
- `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md` for the generated active sentinel, INSERT participation, and storage matrix preserved here;
- `docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md` for Strong ID coverage being generalized;
- `docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md` for create-time identifier visibility;
- `docs/superpowers/specs/2026-07-26-cap4k-database-entrusted-fields-construction-design.md` for database identity construction and observation;
- `docs/superpowers/specs/2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md` for Command, UoW, event, Query, and Capability execution boundaries.

The implementation plan should be created from this document against the then-current `origin/master`. It must re-read active code rather than copy old task lists mechanically.
