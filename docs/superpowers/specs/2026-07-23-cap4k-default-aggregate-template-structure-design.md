# cap4k Default Aggregate Template Structure Design

Date: 2026-07-23

## Reader Contract

This spec is for implementation agents who have not seen the design discussion.

It defines the default generated aggregate template structure that later identity phases must depend on. It is a template-structure baseline, not a create-time ID implementation plan.

Give this spec to agents before assigning Phase 4 Strong-ID create-time injection work. Phase 4 must build on these structural hooks instead of changing entity structure again.

## Status

Design approved for implementation.

This spec is a prerequisite for Phase 4 in `2026-07-22-cap4k-identity-roadmap-design.md`.

## Why This Spec Exists

Phase 4 needs a stable place to attach create-time child ID assignment. The current default aggregate entity template exposes owned-many relations as raw mutable collections and models owned-one relations as a transient nullable property backed by a private collection.

If Phase 4 tries to add ID injection while also redesigning entity constructors, field visibility, owned collections, factory contracts, and schema naming, the implementation slice will become too broad and hard to review.

This spec stabilizes the generated default structure first:

- keep the current JPA annotation-based persistence model;
- keep generated entities as JPA-managed domain objects;
- keep handwritten behavior in `behavior.kt` extension functions;
- replace public raw `MutableList` owned-many relations with `OwnedEntityList`;
- route owned-one transient setters through the same owned-entity collection helper;
- reserve a future hook for create-time ID assignment without implementing it here.

## Current Evidence

Current relevant templates:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/types/value_object.kt.peb`

Current runtime evidence:

- `DefaultAggregateFactorySupervisor.create(...)` calls the registered aggregate factory and then registers the new root in UoW with `PersistIntent.CREATE`.
- `JpaApplicationSideIdSupport` already performs save-time/pre-persistence application-side ID assignment and traverses owned relations by inspecting JPA relation annotations.
- `behavior.kt.peb` emits extension functions, not member methods inside the generated entity class.

## Design Goals

- Stabilize the default generated aggregate entity shape before Phase 4.
- Keep JPA persistence working through the current annotation-based template route.
- Keep entity source readable and familiar to Java/Kotlin developers.
- Avoid exposing raw mutable owned-child collections as the public API.
- Keep owned-many and owned-one mutation behind a small cap4k-owned relation abstraction.
- Avoid inserting `UnitOfWork`, `Repository`, `Mediator`, `IdentifierGenerator`, `EntityManager`, or other application/persistence services into generated entities.
- Preserve a natural domain usage style:

```kotlin
order.lineItems.add(lineItem)
order.lineItems.remove(lineItem)
post.file = file
post.file?.id
```

## Non Goals

- Do not remove JPA annotations from generated entities in this phase.
- Do not introduce `orm.xml` or annotation-free JPA mapping in this phase.
- Do not introduce a separate persistence entity model.
- Do not implement create-time ID assignment in this phase.
- Do not move handwritten behavior from extension functions into generated entity member methods.
- Do not try to fully enforce aggregate invariants at the language level.
- Do not make `OwnedEntityList` the JPA persistent collection field itself.
- Do not restore or add generated schema relation join methods in this phase.
- Do not redesign value object, enum, schema, repository, or UoW contracts beyond what the structure baseline requires.

## Default Entity Contract

Generated aggregate entities use a light-constraint structure:

- entity classes are regular classes, not `data class`;
- entity classes do not generate `equals` or `hashCode`;
- primary constructors should be `internal constructor(...)` by default;
- scalar fields expose public getters and `internal set`;
- ID fields expose public getters and `internal set`;
- system-managed fields keep `internal set`, but generated docs/tests must treat them as infrastructure-managed;
- owned-many relation state uses a private backing collection and a public `OwnedEntityList` facade;
- owned-one relation state keeps the current nullable property shape, but its setter delegates to the owned relation helper;
- ordinary non-owned `MANY_TO_ONE`, direct reference, and non-owned `ONE_TO_ONE` fields keep the current structure unless another spec changes them.

The `internal` constructor is a soft boundary. It communicates that generated factories are the preferred aggregate-root creation path while still allowing generated code, tests, and handwritten domain behavior in the same module to construct owned child entities without excessive helper methods.

Do not replace this with protected/private constructors in this phase. Doing so would force a broader move away from extension-function behavior or introduce many generated helper methods.

## Constructor Shape

The entity template keeps constructor mapping close to the current generator behavior:

```kotlin
@Entity
@Table(name = "orders")
class Order internal constructor(
    id: OrderId,
    title: String,
    status: OrderStatus,
) {
    @Id
    @Embedded
    @AttributeOverride(...)
    var id: OrderId = id
        internal set

    @Column(name = "title")
    var title: String = title
        internal set

    @Convert(converter = OrderStatus.Converter::class)
    @Column(name = "status")
    var status: OrderStatus = status
        internal set
}
```

Implementation agents must preserve the existing JPA no-arg/open-class mechanism. This phase keeps `@Entity`, so existing Kotlin JPA/noarg/allopen configuration should remain the source of JPA hydration support unless current project evidence says otherwise.

The constructor should not include owned relation collections. Owned relation state is initialized inside the entity body.

## Owned Many Contract

Owned-many relations must no longer expose raw `MutableList<T>` as the public field.

Use this structure:

```kotlin
@OneToMany(...)
@JoinColumn(...)
private var _lineItems: MutableList<OrderLine> = mutableListOf()

@get:Transient
val lineItems: OwnedEntityList<OrderLine>
    get() = OwnedEntityList.of(_lineItems, OrderLine::class, "Order.lineItems")
```

The private backing field name may be derived from the relation name with a leading underscore. For example:

- domain relation name: `lineItems`;
- backing field name: `_lineItems`.

The backing field name is not a domain API name. It must not leak into generated schema APIs, generated command/query surfaces, documentation examples, or user-facing diagnostics unless the diagnostic is specifically about generated source internals.

## OwnedEntityList Contract

Introduce a small runtime/domain support type for generated owned entity collections.

Suggested package:

```kotlin
com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList
```

Baseline API:

```kotlin
class OwnedEntityList<E : Any> internal constructor(
    private val delegate: MutableList<E>,
    private val entityType: KClass<E>,
    private val path: String,
) : List<E> by delegate {

    fun add(entity: E): Boolean {
        return delegate.add(entity)
    }

    fun remove(entity: E): Boolean {
        return delegate.remove(entity)
    }

    fun singleOrNull(): E? =
        when (delegate.size) {
            0 -> null
            1 -> delegate[0]
            else -> error("owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}")
        }

    fun replace(value: E?) {
        if (delegate.size > 1) {
            error("owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}")
        }
        delegate.clear()
        if (value != null) {
            add(value)
        }
    }

    companion object {
        fun <E : Any> of(delegate: MutableList<E>, entityType: KClass<E>, path: String): OwnedEntityList<E> =
            OwnedEntityList(delegate, entityType, path)
    }
}
```

Implementation details may adjust exact class/interface shape, but these semantics are required:

- `OwnedEntityList` is not the JPA persistent collection field.
- It delegates to an ordinary mutable backing collection that JPA can manage.
- It implements read operations through `List`.
- It does not implement full `MutableList` by default.
- It exposes only controlled mutation methods required by generated templates.
- `add(entity)` is the future Phase 4 create-time ID hook.
- `remove(entity)` removes from the backing collection and does not imply aggregate deletion outside normal JPA orphan/cascade behavior.
- `singleOrNull()` centralizes the existing owned-one "at most one" invariant.
- `replace(value)` replaces the current relation contents with zero or one entity: `null` clears the backing collection, and non-null values clear the backing collection before calling `add(value)`.
- `replace(value)` must call `add(value)` for non-null values so Phase 4 create-time ID logic automatically applies to owned-one assignments.

The baseline implementation must not perform ID assignment. Phase 4 owns that behavior.

## Owned One Contract

The current owned-one template uses a backing collection and a transient nullable property. Keep that public shape.

Replace direct setter collection manipulation with `OwnedEntityList` helper calls:

```kotlin
@OneToMany(...)
@JoinColumn(...)
private var _files: MutableList<VideoPostFile> = mutableListOf()

@get:Transient
var file: VideoPostFile?
    get() = OwnedEntityList.of(_files, VideoPostFile::class, "VideoPost.file")
        .singleOrNull()
    set(value) {
        OwnedEntityList.of(_files, VideoPostFile::class, "VideoPost.file")
            .replace(value)
    }
```

This preserves the current business-facing usage:

```kotlin
post.file = file
post.file?.id
```

Do not introduce `OwnedEntityRef` in this phase. A separate single-value wrapper can be reconsidered later if real usage shows the nullable property shape is insufficient.

## Ordinary Relation Contract

Ordinary non-owned relations are not part of the new owned-entity collection abstraction.

Keep current relation rendering for:

- non-owned `MANY_TO_ONE`;
- non-owned direct `ONE_TO_ONE`;
- reference-style fields that do not represent aggregate ownership.

If the current model contains an owned direct `ONE_TO_ONE` relation that is not rendered through the owned-one backing collection branch, implementation agents must not broaden behavior silently. Stop and update this spec with the concrete current-code evidence before changing that path.

## Factory Template Contract

`factory.kt` remains the official aggregate-root creation template.

Required behavior:

- factory classes remain Spring services;
- factory payloads remain the public root creation input shape;
- factories call the entity internal constructor;
- generated application-side root ID initialization may stay where current Phase 2/3 specs require it;
- factories do not create owned children in this baseline phase;
- factories do not call UoW directly;
- `DefaultAggregateFactorySupervisor` remains responsible for registering factory-created roots with `PersistIntent.CREATE`.

This phase must not move child creation into factories. Child create-time ID injection belongs to Phase 4 after the entity relation surface is stable.

## Behavior Template Contract

`behavior.kt` remains the handwritten behavior surface.

The template continues to emit extension functions for aggregate root lifecycle behavior:

```kotlin
fun Order.onCreate() {
}

fun Order.onUpdate() {
}

fun Order.onDelete() {
}
```

Handwritten behavior may use:

- entity public getters;
- entity `internal set` setters when compiled in the same module;
- `OwnedEntityList.add(entity)`;
- `OwnedEntityList.remove(entity)`;
- owned-one nullable property assignment.

Handwritten behavior must not depend on:

- JPA APIs;
- UoW internals;
- repositories for mutating the same aggregate;
- identifier generation hooks that belong to Phase 4.

## Schema Template Contract

`schema.kt` remains a generated query DSL.

This phase only preserves the schema/query naming contract needed by the new entity structure. It does not restore generated relation join methods.

Schema generation must use domain names, not backing field names.

Example:

- public relation name: `lineItems`;
- private backing field: `_lineItems`;
- schema/query API must not expose `_lineItems` as the domain relation name.

If JPA Criteria queries require the backing field name internally, that name must remain inside generated implementation details. Public schema builder APIs should remain aligned with the modeled domain relation names.

Implementation agents should split relation naming in the render model when needed:

- `domainName`, for public generated schema/query/member APIs;
- `persistencePathName`, for the JPA Criteria path if the persistent backing field has a different source name.

For example, a public schema property may still be named `lineItems` while its internal Criteria path uses `root.get("_lineItems")`. Do not generate `root.get("lineItems")` for a transient facade property if the actual JPA persistent field is `_lineItems`.

This naming split is a prerequisite for a separate schema relation join recovery iteration. That later iteration should restore generated schema join methods for aggregate-owned child relations only, without exposing private backing names as public schema/query API.

Recommended follow-up slice:

- Phase 3.625: Default schema owned relation join recovery.
- Scope: generated schema join methods for cap4k aggregate-owned child relations, primarily `owned=true`, `ONE_TO_MANY`, `ONE_TO_MANY_JOIN_COLUMN` relations from `model.aggregateRelations`.
- Non-scope: arbitrary JPA joins, non-owned relation joins, weak/reference ID joins, repository save semantics, UoW owned lifecycle classification, and create-time ID assignment.
- Required naming rule: public methods and property names use the domain relation name such as `lineItems`; internal Criteria paths may use the persistence path such as `_lineItems`.
- Design note: prefer a small JPA Criteria implementation that can reuse same-path joins within one schema instance, inspired by Jimmer's controlled association join surface, rather than simply emitting duplicate `root.join(...)` calls for every accessor invocation.

## Strong ID Template Contract

`strong_id.kt` remains a value type template.

This phase does not change Strong ID generation semantics. It preserves:

- immutable value semantics;
- JSON delegating creator/value shape;
- parsing and validation;
- `new()` only when the generator model says the Strong ID can generate a value.

Do not make Strong ID constructors or `new()` methods depend on `OwnedEntityList` in this phase.

## Value Object And Enum Template Contract

Value object and enum templates remain structurally unchanged.

`value_object.kt` continues to generate immutable data classes and converters.

`enum.kt` continues to generate enum values, `valueOfOrNull`, and converters.

This phase does not externalize converters or remove JPA converter annotations.

## Runtime Semantics

This phase adds structure, not new persistence classification.

Required runtime semantics:

- adding an owned child through `OwnedEntityList.add` mutates the same backing collection JPA persists;
- assigning an owned-one nullable property mutates the same backing collection JPA persists;
- repeated calls to the public accessor may create new facade objects, but each facade must delegate to the current backing collection;
- the facade object itself has no identity significance;
- relation facade accessors must be side-effect free except for facade allocation;
- reading an `OwnedEntityList` accessor must not assign IDs, initialize lazy collections intentionally, register UoW changes, or mutate backing state;
- `OwnedEntityList` is not a UoW enrollment object and must not carry `PersistIntent`;
- no create/update audit behavior changes in this phase;
- no ID is assigned earlier than current behavior in this phase.

## Phase 4 Hook

Phase 4 may later enhance `OwnedEntityList.add(entity)` to perform create-time own-ID assignment for application-side Strong ID owned child entities.

Phase 4 may also rely on `replace(value)` calling `add(value)` for non-null values so owned-one assignment receives the same ID behavior.

This spec intentionally does not define:

- how the ID generator is located;
- how Strong ID strategy metadata is resolved;
- whether ID assignment uses generated Strong ID companions, mediator-facing identifier generators, or another policy object.

Those decisions belong to the Phase 4 spec.

## Expected Implementation Areas

Likely files/modules:

- `ddd-core` for `OwnedEntityList`;
- aggregate entity render model planning where owned relation metadata is exposed to templates;
- `aggregate/entity.kt.peb`;
- renderer tests for owned-many and owned-one output;
- compile-level functional tests for generated aggregate entities;
- schema tests if backing field names affect generated query names.

Avoid changing:

- JPA UoW persistence classification;
- repository read/write APIs;
- Phase 2 Strong ID model semantics;
- Phase 3 mediator identifier API;
- value object and enum templates except import fallout from compile fixes.

## Verification Requirements

Implementation must verify:

- generated owned-many relations no longer expose public raw `MutableList`;
- generated owned-many relations expose `OwnedEntityList<T>`;
- `OwnedEntityList.add` mutates the JPA backing collection;
- `OwnedEntityList.remove` mutates the JPA backing collection;
- generated owned-one nullable property still compiles and preserves current getter behavior;
- owned-one setter delegates through `OwnedEntityList.replace`;
- schema/query public names do not leak `_` backing field names;
- root factories still compile and still create aggregates through constructors;
- behavior extension files can still mutate scalar fields through `internal set`;
- generated aggregate compile functional tests pass for representative owned-many, owned-one, strong-id, enum, and value-object cases.

Recommended focused tests:

- renderer snapshot/assertion test for owned-many template output;
- renderer snapshot/assertion test for owned-one template output;
- unit test for `OwnedEntityList.add/remove/singleOrNull/replace`;
- compile functional test for an aggregate with both owned-many and owned-one relations;
- compile functional test proving existing factory and behavior templates remain usable.

## Rollback Triggers

Rollback or redesign if:

- Hibernate cannot reliably persist a private backing collection while exposing a transient `OwnedEntityList` facade;
- generated schema/query APIs must expose `_` backing names to work;
- `OwnedEntityList` must become a JPA persistent collection type to make the design work;
- behavior extension functions can no longer perform normal aggregate mutations without excessive generated helper methods;
- the baseline forces Phase 4 ID generation decisions into this implementation slice.

## Implementation Boundary For Agents

Agents implementing this spec must stay inside the template-structure boundary.

They may add `OwnedEntityList` and update generated aggregate entity templates/tests.

They must not:

- implement create-time ID assignment;
- change UoW intent semantics;
- remove JPA annotations;
- redesign factory payload policies;
- move behavior into generated entity member methods;
- introduce a separate persistence model.

If any of those changes appear necessary, stop and report the blocker instead of expanding the implementation.
