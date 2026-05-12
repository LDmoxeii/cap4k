# cap4k Adapter Aggregate Projection Generation Design

Date: 2026-05-12

Status: Proposed

Scope: replace the earlier runtime projection proposal for `#23` with a generation-only capability. cap4k will generate adapter-owned aggregate projection classes from DB/canonical aggregate metadata. The default template emits JPA-style projection entities. Projects that want Jimmer or another read-side technology can override the same neutral projection template.

Out of scope:

- adding `ProjectionSupervisor`, `Mediator.prj`, `ReadShape`, or a read-model runtime;
- wrapping `KSqlClient`, `EntityManager`, or other query clients behind cap4k APIs;
- generating Jimmer projection types as a built-in default;
- controlling Jimmer fetcher/DTO object graph shape;
- adding weak-reference predicate or ordering support;
- changing repository, unit-of-work, command, query, or request-supervisor semantics;
- generating query handler implementations that perform real read-side queries.

## Backlog Source

This design covers:

- `#23` generator: support advanced read/write model split with read-only weak-reference template context.

The earlier direction expanded this into runtime projection access through `Mediator.prj`. That is no longer the accepted direction. Existing cap4k runtime already lets Spring-managed query handlers be invoked through `Mediator.qry` because commands, queries, and clients all ride on `RequestParam` plus `RequestHandler`.

The new capability is intentionally smaller:

```text
DB/canonical aggregate metadata
  -> adapter aggregate projection template context
  -> generated adapter projection classes
  -> user-maintained QueryHandler uses JPA, Jimmer, SQL, or another tool directly
  -> Mediator.qry continues to call the QueryHandler
```

## Background

cap4k currently separates these concerns:

- domain aggregate entities are write-side business models;
- query contracts are generated under the application query family;
- query handlers are physically generated into adapter packages by default and implement application request contracts;
- `Mediator.qry`, `Mediator.cmd`, and `Mediator.requests` are aliases over the request supervisor;
- `Mediator.ioc` already exposes the Spring `ApplicationContext` for exceptional direct bean access.

The project does not need a new projection runtime to let users use Jimmer. A query handler can inject `KSqlClient` directly:

```kotlin
class CategoryExistsByIdQryHandler(
    private val sqlClient: KSqlClient,
) : Query<CategoryExistsByIdQry.Request, CategoryExistsByIdQry.Response> {

    override fun exec(request: CategoryExistsByIdQry.Request): CategoryExistsByIdQry.Response {
        val exists = sqlClient.exists(CategoryProjection::class) {
            where(table.id eq request.categoryId)
        }
        return CategoryExistsByIdQry.Response(exists = exists)
    }
}
```

cap4k should help by generating stable adapter projection classes and rich template context, not by abstracting Jimmer or rebuilding a query DSL.

## Problem

Projects that want a read-side model currently have to choose between several weak options:

- reuse domain aggregate entities in query handlers, coupling read queries to write-model mapping choices;
- handwrite JPA/Jimmer query model classes for every table, duplicating DB metadata already parsed by cap4k;
- override broad aggregate templates just to get read-side artifacts;
- introduce a runtime projection abstraction that duplicates JPA/Jimmer responsibilities.

The actual missing capability is generator-side:

```text
Given a parsed aggregate/table model, generate an adapter-owned projection class that query handlers can use.
```

This should stay mechanical and template-driven. The generated class is an adapter implementation artifact, not an application contract and not a domain model.

## Goals

1. Add an opt-in `aggregateProjection` generator family.
2. Generate projection artifacts only into the adapter module.
3. Fix the package root to `adapter.application.projections`.
4. Emit build-owned generated source by default.
5. Use a neutral template ID: `aggregate_projection/entity.kt.peb`.
6. Keep the built-in template JPA-flavored.
7. Provide enough template context for projects to override the same template to Jimmer.
8. Keep query execution inside user-maintained query handlers.
9. Preserve existing `Mediator.qry` and `RequestHandler` behavior.
10. Avoid object graph expansion by default: first-version built-in projection output contains scalar table fields only.

## Non-Goals

- Do not add `ReadRepository`.
- Do not add `Projection`, `ProjectionPredicate`, or `ProjectionSupervisor`.
- Do not add `Mediator.projections`, `Mediator.prj`, or `Mediator.ext`.
- Do not abstract `KSqlClient`.
- Do not generate provider-specific Jimmer artifacts in the default preset.
- Do not generate relation object graphs in the default JPA projection template.
- Do not let projection generation alter domain aggregate ownership, cascade behavior, or repository loading.
- Do not make projection generation part of the default happy path.

## Options Considered

### Option 1: runtime Projection API

Add `ProjectionSupervisor`, `Mediator.prj`, provider implementations, projection predicates, and generated schema helpers.

Pros:

- one cap4k-owned read access surface;
- could eventually support multiple providers behind one API.

Cons:

- large runtime surface;
- duplicates mature query tools;
- risks recreating a query DSL;
- harder to control object graph expansion than simply requiring users to use Jimmer/JPA explicitly in query handlers;
- unnecessary because `RequestHandler` binding already lets query handlers call external query clients.

Decision: reject.

### Option 2: built-in Jimmer read-model provider

Generate Jimmer projection types or wrap `KSqlClient` directly.

Pros:

- aligns with Jimmer's explicit fetcher/DTO strengths;
- convenient for projects already using Jimmer.

Cons:

- makes Jimmer a cap4k default concern;
- still leaves JPA users needing another path;
- exposes provider choice in core generator semantics;
- template override is already a simpler extension point.

Decision: reject as built-in default.

### Option 3: adapter projection generation with neutral template context

Generate adapter-owned projection classes from aggregate metadata. Default template emits JPA-style projection entities. Jimmer users override the neutral template.

Pros:

- small generator-only capability;
- reuses existing template override mechanics;
- avoids new runtime APIs;
- keeps provider-specific query code in query handlers;
- lets Jimmer users consume the same metadata without cap4k depending on Jimmer.

Cons:

- does not standardize query execution;
- users must still implement query handlers;
- Jimmer support depends on project template overrides.

Decision: choose this option.

## Chosen Design

Add a new generator family:

```kotlin
generators {
    aggregateProjection {
        enabled.set(true)
    }
}
```

It is disabled by default.

For each canonical aggregate entity/table that is eligible for projection generation, the generator emits:

```text
<adapter-module>/build/generated/cap4k/main/kotlin/
  <basePackage>/adapter/application/projections/<aggregate-package>/<EntityName>Projection.kt
```

Example:

```text
demo-adapter/build/generated/cap4k/main/kotlin/
  com/acme/demo/adapter/application/projections/category/CategoryProjection.kt
```

The package root is fixed:

```text
adapter.application.projections
```

It is not configurable in the first version. Projection artifacts are adapter-owned implementation artifacts because the built-in template uses persistence/query annotations and project overrides may use Jimmer annotations.

## Template ID And Default Output

Use a neutral template ID:

```text
aggregate_projection/entity.kt.peb
```

The template ID names the artifact, not the default technology. The built-in `ddd-default` preset emits JPA-flavored projection classes, for example:

```kotlin
package com.acme.demo.adapter.application.projections.category

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "category")
class CategoryProjection(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "name")
    val name: String = "",
)
```

The exact Kotlin constructor/property style should follow existing aggregate template conventions during implementation. The design requirement is that the default template is build-owned, mechanical, scalar-only, and adapter-owned.

## Jimmer Override Path

Jimmer is supported through template override, not through a cap4k provider:

```text
<override-dir>/aggregate_projection/entity.kt.peb
```

A project override may emit:

```kotlin
package com.acme.demo.adapter.application.projections.category

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

@Entity
@Table(name = "category")
interface CategoryProjection {
    @Id
    val id: Long
    val name: String
}
```

Query handlers remain user-maintained:

```kotlin
class CategoryExistsByIdQryHandler(
    private val sqlClient: KSqlClient,
) : Query<CategoryExistsByIdQry.Request, CategoryExistsByIdQry.Response> {

    override fun exec(request: CategoryExistsByIdQry.Request): CategoryExistsByIdQry.Response {
        val exists = sqlClient.exists(CategoryProjection::class) {
            where(table.id eq request.categoryId)
        }
        return CategoryExistsByIdQry.Response(exists = exists)
    }
}
```

cap4k does not control Jimmer fetcher shape. Object graph control remains the user's responsibility in Jimmer query code.

## Template Context

The projection render context should expose provider-neutral metadata. The built-in JPA template may use only part of it, but overrides should not need to re-parse DB metadata.

Minimum context:

- `packageName`;
- `typeName`, such as `CategoryProjection`;
- `entityName`;
- `aggregateRootName`;
- `tableName`;
- `description`;
- `imports`;
- `fields`;
- `relations`;
- `weakReferences` if weak-reference metadata is implemented in this slice.

Each field model should include:

- `name`;
- `columnName`;
- `type`;
- `renderedType`;
- `nullable`;
- `id`;
- `version`;
- `deleted`;
- `managed`;
- `exposed`;
- `insertable`;
- `updatable`;
- enum/custom type binding metadata when available.

Each relation model should include:

- source field/column;
- target table;
- target entity;
- target aggregate root;
- relation type;
- nullable;
- lazy flag if available;
- whether the relation is generated on the domain aggregate entity.

Default JPA projection generation should not render relation object fields in the first version. Relation metadata exists for template overrides such as Jimmer.

## Data Flow

Generation flow:

```text
DB source / canonical aggregate model
  -> aggregateProjection planner
  -> aggregate_projection/entity.kt.peb
  -> adapter build-owned projection source
```

Runtime query flow:

```text
Controller / Job / Subscriber
  -> Mediator.qry
  -> QueryHandler Spring bean
  -> user-selected query client (JPA, Jimmer, SQL, etc.)
  -> Query Response
```

No new runtime surface is introduced.

## Ownership And Conflict Policy

Projection artifacts are build-owned generated source:

```text
outputKind = GENERATED_SOURCE
conflictPolicy = OVERWRITE
```

Users should customize projection generation through template overrides, not by editing generated files.

Generated query handlers remain checked-in author surfaces controlled by the existing `designQueryHandler` family and normal template conflict policy. Projects may disable query handler generation and handwrite handlers.

## Hard Boundaries

- Projection classes are adapter-owned.
- Projection package root is fixed to `adapter.application.projections`.
- Projection classes are not application contracts.
- Projection classes are not domain aggregate entities.
- Projection generation does not change repository or UoW behavior.
- Default projection output is scalar-only.
- Relation and weak-reference metadata may be available to templates, but default output does not expand object graphs.
- Jimmer support is by template override only.
- Query execution belongs in query handlers.

## Error Handling

Fail fast when:

- `aggregateProjection` is enabled but `project.adapterModulePath` is missing;
- a projection output path cannot be derived;
- a field type cannot be rendered;
- duplicate projection output paths are planned;
- weak-reference metadata is enabled and references an unknown target table;
- template context contains unresolved relation target metadata required by an enabled template path.

Diagnostics should name the table, entity, projection type, and output path where possible.

## Compatibility

Existing projects are unaffected because:

- the new generator is disabled by default;
- no runtime API is added or changed;
- `Mediator.qry`, `Mediator.ioc`, repositories, and UoW keep their current behavior;
- generated projection files live under adapter build-owned generated source;
- Jimmer remains an opt-in project template override choice.

## Documentation Updates

Implementation should update public authoring docs after code lands:

- generator DSL reference: `generators.aggregateProjection`;
- generator input/output guide: adapter projection artifact ownership;
- adapter authoring guide: projections live under adapter and support query handlers;
- tactical model guide: query contracts stay in application, projection classes stay in adapter;
- template override guide: override `aggregate_projection/entity.kt.peb` for Jimmer.

The docs must explicitly say that cap4k does not provide a read-model runtime or control Jimmer fetcher shape.

## Testing Strategy

Tests should cover:

- `aggregateProjection` is disabled by default;
- enabling it requires an adapter module path;
- generated plan items use template ID `aggregate_projection/entity.kt.peb`;
- generated plan items target `adapter.application.projections`;
- generated plan items are build-owned generated source with overwrite behavior;
- default template renders scalar JPA projection fields;
- relation metadata is present in template context but not rendered by the default scalar-only template;
- project template override can replace `aggregate_projection/entity.kt.peb`;
- query and query-handler generation remain unchanged;
- no `ProjectionSupervisor`, `Mediator.prj`, or Jimmer dependency is introduced.

Functional tests should include a small aggregate with scalar fields, ID, version/deleted metadata, and one relation so both default scalar rendering and override context are exercised.

## Acceptance Criteria

- `generators.aggregateProjection.enabled` exists and defaults to false.
- When enabled, cap4k plans adapter projection artifacts from canonical aggregate metadata.
- Projection artifacts are generated under `adapter.application.projections`.
- Projection artifacts are build-owned generated source.
- Built-in template ID is `aggregate_projection/entity.kt.peb`.
- Built-in template emits JPA-flavored scalar projection classes.
- Template context is rich enough for a project to override the template to Jimmer.
- cap4k adds no projection runtime API.
- cap4k adds no Jimmer default dependency.
- Query handlers continue to use existing `Query` / `RequestHandler` / `Mediator.qry` mechanics.
