# Cap4k Strong ID 1.0 Design

## Background

Strong ID is the final large framework capability track before cap4k can stabilize for 1.0.

The earlier backlog already split this concern across:

- #28: establish Strong ID as a first-class framework capability independent of wrapper;
- #29: define the aggregate-root Strong ID core model;
- #30: support aggregate-root Strong ID generation without wrapper;
- #31: expand Strong ID support to cross-aggregate reference IDs;
- #32: add persistence, serialization, and authoring guidance.

This design consolidates those issues into a single 1.0 target. It is intentionally not a narrow slice that leaves generator, runtime, JPA, JSON, or documentation as vague follow-up work.

## Problem

Current cap4k ID support is still shaped by the pre-1.0 transition period:

- generator output defaults to technical ID strategies such as `uuid7`, `snowflake-long`, `identity`, or `database-identity`;
- runtime application-side ID assignment can rely on sentinel defaults such as `UUID(0L, 0L)`;
- `@ApplicationSideId` and UoW reflection assignment make identity creation happen late in the persistence path;
- Snowflake and QueryDSL-era concerns still leak into the default story even though they are not central to Kotlin-first cap4k;
- Strong ID documentation exists as guidance, but the generator/runtime path does not yet make it a first-class default.

The result is not coherent enough for 1.0. cap4k should not tell users to model with Strong ID while generated code, persistence behavior, and examples still default to primitive IDs or technical generator strategies.

## Design Goal

Make Strong ID the default cap4k identity model for generated aggregate roots.

The default ID contract becomes:

- every generated aggregate root has a strongly typed public ID;
- every default generated ID is backed by a canonical UUIDv7 string;
- new aggregate roots receive a real ID during creation, not during UoW save;
- cross-aggregate references use strongly typed IDs instead of primitive strings;
- cross-context references use local published-language reference IDs;
- persistence, JSON, HTTP binding, analysis, authoring docs, skills, and reference examples all describe the same model.

This is a breaking 1.0 design. Backward compatibility with primitive UUID, `Long` Snowflake, database identity, or sentinel assignment is not a design constraint.

## Core Model

Strong ID is a type-safe identity wrapper whose only default backing value is a UUIDv7 string.

The preferred Kotlin shape is:

```kotlin
@JvmInline
value class ContentId(val value: String) {
    init {
        StrongIds.requireUuidV7(value, "ContentId")
    }

    companion object {
        fun new(): ContentId = ContentId(StrongIds.newUuidV7String())
        fun parse(value: String): ContentId = ContentId(value)
    }
}
```

Generated Strong ID types must enforce:

- `value` is a syntactically valid UUID;
- UUID version is 7;
- blank strings, random non-UUID strings, UUIDv4, UUIDv1, and UUID nil values are invalid;
- construction, parsing, JSON deserialization, HTTP binding, and persistence hydration all pass through the same validation rule.

If direct `@JvmInline value class` usage is not stable for JPA `@Id`, the public domain type must remain equivalent, and the implementation must provide a Hibernate/JPA adapter rather than falling back to primitive `String id` as the default generated domain model. Any persistence-only construction escape hatch must be internal to that adapter and must not expose invalid Strong IDs to domain code.

## ID Ownership Rules

Strong ID has separate definition and usage rules.

Aggregate-root ID:

- generated for each aggregate root;
- owned by that aggregate root concept;
- used as the aggregate root's public identity;
- created by the aggregate/factory creation path when a new aggregate is born.

Same-context aggregate reference ID:

- uses the referenced aggregate root's ID type directly;
- does not create a new alias type;
- keeps aggregate relationships as ID references, not JPA object references.

Cross-context reference ID:

- uses a local published-language ID type in the current bounded context;
- does not directly leak another context's ID type into the local domain model;
- may map to an external context ID in an ACL, integration adapter, or mapping table;
- does not require a local aggregate, repository, or table.

Child entity ID:

- is not part of the default Strong ID scope;
- must not be generated automatically just because a child entity has an internal identifier;
- can be revisited only if a separate modeling need appears after 1.0.

## Example Identity Model

In the content publication example:

```kotlin
class Content(
    val id: ContentId,
    val authorId: AuthorId,
    val mediaProcessingTaskId: MediaProcessingTaskId?,
)
```

`ContentId` is the ID of the `Content` aggregate root.

`MediaProcessingTaskId` is the ID of another aggregate root in the same content bounded context. It is reused directly because both aggregates share the same language.

`AuthorId` is a content-context reference identity. It represents the content-side concept of an author. It must not be replaced by an identity-context `UserId` in the content model, even if the first implementation maps author one-to-one to a user account.

## Database Annotation Contract

Database annotations remain the primary generation input because cap4k's generator strength is schema-driven skeleton output.

The annotation model changes from technical ID strategy hints to semantic Strong ID declarations.

Aggregate root ID:

```sql
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
```

This generates `ContentId` by default. No `@GeneratedValue=uuid7` is required.

Same-context aggregate reference:

```sql
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
```

This generates a `Content.mediaProcessingTaskId: MediaProcessingTaskId?` field if `MediaProcessingTask` is a generated aggregate root.

Physical foreign keys may be used as auxiliary evidence, but they must not be required. Many DDD projects avoid physical FK constraints for weak references, integration boundaries, sharding, or migration reasons.

Cross-context or external reference ID:

```sql
comment on column content.author_id is '@RefId=AuthorId;';
```

This generates the `AuthorId` type and allows fields, commands, events, and DTOs to use it. It must not generate an `Author` aggregate, repository, table mapping, or fake local entity.

Optional descriptive annotations may be supported later for analysis output, but should not be required for generation:

```sql
comment on column content.author_id is '@RefId=AuthorId;@ExternalContext=identity;@ExternalId=UserId;';
```

The default generator only needs `@RefId=AuthorId`.

## Generation Output

The generator must treat Strong ID as a first-class artifact family.

For aggregate-root IDs, generation must cover:

- ID type file;
- aggregate root ID field;
- factory or aggregate creation path;
- commands that create or reference the aggregate;
- domain events;
- integration events when IDs cross service boundaries;
- query DTOs and request DTOs;
- repository signatures;
- JPA mapping support;
- JSON and HTTP binding support;
- generated tests or compile samples that prove the shape compiles.

For same-context aggregate references, generation must:

- resolve `@RefAggregate=OtherAggregate` to `OtherAggregateId`;
- fail fast if the named aggregate root does not exist;
- preserve nullability from the database field and canonical model;
- avoid generating JPA object relationships between aggregate roots.

For `@RefId`, generation must:

- create a standalone reference ID type;
- allow use in fields, commands, events, DTOs, and mapping adapters;
- avoid generating aggregate, repository, factory, or table artifacts;
- include the ID type in serialization and binding support.

## ID Creation Rules

Only the aggregate root's own ID may be generated during creation of a new aggregate.

Recommended generated factory shape:

```kotlin
fun create(command: CreateContentCommand): Content {
    return Content(
        id = ContentId.new(),
        authorId = command.authorId,
        mediaProcessingTaskId = null,
    )
}
```

Reference IDs must be received, parsed, or mapped. They must not be automatically generated by the aggregate being created.

Examples:

- `ContentId.new()` is valid when creating a new `Content`;
- `AuthorId.new()` is not valid inside `Content.create(...)` because the content aggregate does not create authors;
- `MediaProcessingTaskId.new()` is valid only when creating a new `MediaProcessingTask`, not when linking existing content to an existing task.

This rule removes the need for sentinel defaults and UoW save-time ID assignment in the default path.

## Runtime Contract

The default runtime must no longer rely on `@ApplicationSideId` reflection assignment for generated aggregate-root IDs.

The default runtime contract is:

- new aggregate instances already have real Strong IDs before they are enlisted into the UoW;
- UoW persists aggregates with assigned Strong IDs;
- runtime does not treat `null`, blank string, UUID nil, or any sentinel as "needs generated ID";
- ID validation failures are domain/input failures, not persistence fallback triggers.

`@ApplicationSideId`, `IdStrategy`, and save-time assignment can be deleted or demoted during the implementation plan if no remaining supported default path requires them.

## JPA And Hibernate Contract

The generated domain model should expose Strong ID fields, not primitive ID fields.

Preferred generated shape:

```kotlin
class Content(
    @Id
    val id: ContentId,
)
```

The implementation plan must validate whether Kotlin `@JvmInline value class` can be used reliably as a Hibernate/JPA ID type across:

- persist;
- find by ID;
- repository queries;
- dirty checking around aggregate saves;
- schema generation or validation;
- JSON round trip in test fixtures.

If direct inline-class `@Id` is unstable, the fallback must still preserve Strong ID as the domain-facing generated field. Acceptable implementation choices include generated Hibernate type support, generated converter support if sufficient for ID fields, or a single-column embedded ID mapping.

Unacceptable fallback:

```kotlin
class Content(
    val id: String,
)
```

Primitive `String id` may exist only inside a private adapter if a persistence workaround requires it. It must not be the generated aggregate root's public domain field.

## JSON And HTTP Binding Contract

Strong IDs must serialize as their backing UUIDv7 string.

API shape:

```json
{
  "contentId": "018f3b73-7d1b-7c0f-9f0a-b2d4b5e2c111"
}
```

It must not serialize as:

```json
{
  "contentId": {
    "value": "018f3b73-7d1b-7c0f-9f0a-b2d4b5e2c111"
  }
}
```

JSON deserialization and HTTP parameter binding must parse and validate UUIDv7 values. Invalid IDs should fail at the boundary with clear errors before they become domain objects.

## Kotlin UUIDv7 Dependency Direction

The UUIDv7 generator should move to Kotlin's native UUIDv7 support once the project upgrades to a Kotlin version that provides the required stable or explicitly accepted experimental API.

After that migration:

- `uuid-creator` should be removed from default UUIDv7 generation;
- generated IDs should use canonical string output;
- no default path should require `java.util.UUID` or `kotlin.uuid.Uuid` as a domain field type.

If the Kotlin UUID API is still experimental, the implementation plan must decide whether cap4k accepts the opt-in for 1.0 or wraps the call behind a small internal `StrongIds` utility to localize the experimental dependency.

## Generator Strategy Cleanup

The old `idDefaultStrategy` model should not remain the default authoring concept.

Default generation no longer exposes these as normal branches:

- `uuid7` as a primitive UUID strategy;
- `snowflake-long`;
- `identity`;
- `database-identity`.

`@GeneratedValue=uuid7`, `@GeneratedValue=snowflake-long`, and `@IdGenerator` comments are not part of the 1.0 default authoring path.

Legacy or non-default compatibility can be handled separately only if a concrete migration need appears. The 1.0 examples and skills must not teach those paths as normal options.

## Relationship To Snowflake And QueryDSL

Strong ID is the 1.0 identity stabilization work. Snowflake and QueryDSL cleanup should follow as slimming work, not be mixed into this design's implementation unless they directly block Strong ID.

Expected direction:

- Snowflake leaves the default story because UUIDv7 removes the normal need for centralized distributed numeric IDs;
- QueryDSL is not a core Kotlin-first cap4k capability and can be removed in a later slimming slice;
- generated output should not keep Snowflake or QueryDSL hooks alive merely for historical compatibility.

This design should unblock those deletions, not hide them inside the Strong ID PR.

## Documentation And Skill Synchronization

Implementation must update all three documentation surfaces in the same PR set:

- `docs/superpowers/analysis/`
- `docs/public/authoring/`
- `skills/`

The public authoring example must teach Strong ID through the content publication reference scenario:

- `ContentId` for the content aggregate root;
- `MediaProcessingTaskId` for same-context aggregate references;
- `AuthorId` for content-context reference identity mapped from an external identity/user context.

Docs must stop describing Strong ID as a far-future optional wrapper technique. For cap4k 1.0 it is the generated default identity model, while still not being a DDD tactical building block like aggregate, command, domain event, or value object.

## Verification Strategy

Verification must prove both generated code and runtime behavior.

Required focused checks:

- Strong ID value validation accepts UUIDv7 and rejects blank, UUID nil, UUIDv4, and non-UUID values;
- aggregate creation assigns the aggregate's own ID before UoW persistence;
- reference IDs are received or parsed, not generated by the referencing aggregate;
- generated aggregate root compiles with Strong ID fields;
- generated same-context `@RefAggregate` fields use the referenced aggregate ID type;
- generated `@RefId` fields create standalone reference ID types without fake aggregate artifacts;
- JPA persists and loads entities whose public ID field is a Strong ID;
- repository find-by-id works with Strong ID;
- JSON serializes Strong ID as a string and deserializes with validation;
- public authoring examples and skills match generated output.

Broad full-suite verification may remain constrained by existing starter fixture debt, but focused Strong ID tests must pass before the implementation can be called complete.

## Breaking-Change Note

This design intentionally breaks the old ID surface.

Expected breaking changes include:

- generated aggregate IDs no longer default to primitive `UUID`, `String`, or `Long` fields;
- `UUID(0L, 0L)` is not a supported default ID value;
- UoW save-time ID assignment is not the generated default path;
- `snowflake-long` is not a default generated ID option;
- database identity is not part of the default DDD aggregate generation path;
- generated API payloads use string-shaped Strong IDs, but generated Kotlin code uses ID types.

This is acceptable because the project is still in fast redesign before 1.0 and has no compatibility obligation to preserve the old identity model.

## Open Implementation Risks

The only major technical risk is JPA/Hibernate support for Kotlin inline Strong ID fields as `@Id`.

The implementation plan must start with a spike-style focused test that decides the JPA mapping route:

- use `@JvmInline value class` directly if stable;
- otherwise generate or register Hibernate/JPA mapping support while keeping Strong ID as the public domain field.

The spec must not be weakened to primitive `String id` just because the first JPA attempt is inconvenient.
