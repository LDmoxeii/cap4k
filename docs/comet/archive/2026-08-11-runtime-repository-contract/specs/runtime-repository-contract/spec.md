# Runtime Repository Contract

## Purpose

The Runtime repository layer must expose one deterministic composition and query contract. Generated adapters connect tactical model artifacts to Runtime, but do not become application-owned repository APIs. Reading an aggregate may place it under Unit of Work observation, but does not by itself express an intent to persist a new aggregate or delete an existing one.

## Repository composition

- Repository contributors are keyed by the exact pair `(entityClass, predicateClass)`.
- Registering more than one contributor for the same pair must fail deterministically during repository supervisor initialization.
- The failure must identify the route and both conflicting contributor implementations.
- Registration order must not select a winner.
- Different entity/predicate pairs remain independently composable.

## Generated repository adapter boundary

- The generated JPA repository carrier remains private or internal generated infrastructure.
- Application code reaches repository behavior through `Mediator.repositories` and the Runtime supervisor contract.
- This change must not introduce a public generated Repository abstraction or a compatibility facade.

## Predicate contract

- `JpaPredicate` carries either an ID-based filter or a generated JPA `Specification`; it does not define a second expression language.
- The supported comparison surface is the one already declared by schema `Field`: equality/inequality, null checks, ordered comparisons, collection membership, and string matching, including nullable convenience forms.
- The supported composition surface is the one already declared by `Predicates`: conjunction, disjunction, negation, nullable operands, infix aliases, and nested combinations.
- Generated schema Specifications must preserve those operations when adapted through `JpaPredicate.bySpecification(...)`.
- Tests must exercise nested expressions, not only isolated operators.

## Sorting, offset, pagination, and PageData

- ID and Specification paths must share one explicit contract for `OrderInfo`, offset/limit selection, PageData items, and totalCount.
- PageData `totalCount` describes the complete filtered result before page slicing; it is not the number of items in the current page.
- Empty, partial, and out-of-range pages preserve the requested pageNum and pageSize.
- `PageData.transform` preserves pageNum, pageSize, and totalCount while transforming only items.
- Duplicate input IDs have entity-set semantics and missing IDs are omitted.
- ID filters execute as database-side predicates and use the same JPA sorting, offset/limit, PageData, and total-count path as Specification filters.
- When explicit `OrderInfo` is supplied, Runtime appends the entity ID ascending as a deterministic tie-breaker unless the ID is already part of the requested ordering.
- Without explicit `OrderInfo`, neither ID nor Specification queries promise a result order.

## Observation and persistence intent

- Entity-returning reads may register loaded managed aggregates for Unit of Work observation.
- Observation alone must not register create, delete, or equivalent persistence intent.
- `count` and `exists` do not observe aggregate instances and must not create persistence intent.
- Explicit new/create and remove/delete operations, or their equivalent Unit of Work APIs, are the only repository operations in this scope that establish the corresponding intent.
- Changes to already managed aggregates in a write Unit of Work may still be persisted through JPA dirty checking; this is not new-aggregate intent.

## Failure semantics

- Ambiguous repository composition fails at initialization rather than on first request.
- Query behavior must not silently ignore a supplied sort or page contract.
- No compatibility mode, last-write-wins fallback, or legacy generated carrier is retained.

## Non-goals

- Reliable Command/Event execution state changes.
- Integration Event transport work.
- Analyzer work.
- A generic repository extension framework or public predicate AST.

## Acceptance

- Duplicate and non-conflicting registration tests prove initialization behavior and diagnostics.
- Predicate tests cover every declared comparison/composition family and nested generated Specifications.
- JPA integration tests prove consistent sorting, offset/limit, items, and totalCount for ID and Specification paths.
- Pagination tests cover empty, partial, out-of-range, and deterministically ordered results.
- Unit tests prove `PageData.transform` metadata preservation.
- Unit of Work tests distinguish read observation from explicit persistence intent.
- Generated-source tests prove the repository carrier remains non-public.
- Focused tests, full `./gradlew check`, `git diff --check`, and Comet Native verification all pass.