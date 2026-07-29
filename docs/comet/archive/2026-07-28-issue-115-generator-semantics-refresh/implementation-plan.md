# Implementation plan

## Slice 1: shared semantic model and compiler

- Add source-level semantic field declarations that retain `typeExpression` only until canonical compilation.
- Add resolved `SemanticTypeRef`, `CanonicalTypeIdentity`, `SemanticValueDefinition`, role, default-expression, envelope, and persistence-projection models.
- Keep DB/Entity `FieldModel` separate because it owns physical column and managed-field metadata.
- Add one core type-expression parser, canonical symbol catalog, conservative resolver, nested-path compiler, and Kotlin-ready default compiler.
- Preserve `PageData<Item>` as a response-only `Page` envelope compiled outside the closed general generic algebra.
- Migrate design, IR-recovered design, drawing-board, domain-event, Value Object, and result payload structures to the shared definition.

## Slice 2: source and analysis syntax migration

- Remove public field-level `nullable`; encode nullability in Kotlin-style type expressions.
- Update design JSON, Value Object manifest, IR-analysis source, code-analysis model/formatter/writer, validation scripts, and tracked fixtures.
- Reject old `nullable` and `storage` fields instead of accepting compatibility aliases.

## Slice 3: design generator migration

- Make design planners consume resolved semantic definitions.
- Move nested field-path and page-envelope compilation out of `DesignPayloadRenderModelFactory`.
- Replace design/value-object parser and resolver copies with one Kotlin type renderer that only chooses imports versus explicit FQNs.
- Preserve Command, Query, Client, API Payload, Event, Saga, handler, and checked-in artifact hand feel.

## Slice 4: optional Value Object persistence projection

- Parse optional structured `persistence`; omission means no projection and `{ "kind": "json" }` is explicit JSON.
- Keep the Value Object body checked-in and persistence-free.
- Generate `<ValueObjectName>JsonAttributeConverter` as a build-owned generated source in the same package.
- Make JPA inference consume the explicit converter FQN and fail when a non-persistent Value Object is bound to a database column.
- Include Value Object manifests in generated-source task inputs/roles and clean controlled generated roots before a full rebuild so removed projections do not leave stale converters.

## Slice 5: owned creation graph and Factory

- Compile an FQN-keyed aggregate creation graph after relations, write surfaces, IDs, and semantic types are resolved.
- Validate reachability, unique ownership, cycles, relation targets, constructor satisfiability, attachment facades, and creation-type identity collisions before rendering.
- Generate one checked-in top-level `<EntityName>Creation` per reachable owned Entity.
- Keep root `<RootName>Factory.Payload`; add recursive owned `ONE`/`MANY` fields and private Factory materialization helpers.
- Attach children only through generated forward relation facades so existing application-side and database-side ID lifecycles remain authoritative.

## Slice 6: ownership, verification, and documentation

- Make checked-in artifacts resolve to `SKIP`; build-owned artifacts remain overwrite/rebuild owned.
- Add focused API/core/source/planner/renderer tests and compile fixtures for nested semantic types, explicit JSON projection, owned `ONE`/`MANY`, recursive graphs, and child ID strategies.
- Update public reference docs and generator-input skills.
- Run focused module tests, functional compile tests, and the repository `check` before Comet Verify.

