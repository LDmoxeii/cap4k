# Runtime Repository Contract

## Contract

- Generated JPA repository carriers are private implementation details.
- Duplicate repository/provider registration fails deterministically; last-write-wins is forbidden.
- `JpaPredicate` supports the framework's declared comparison and composition operators.
- Sorting, offset/limit pagination, and `PageData` totals/items semantics are consistent across
  repository implementations.
- Repository reads do not enlist persistence intent unless the explicit `persist = true` (or the
  equivalent UoW intent) is requested.

## Independence

This slice may run in parallel with reliable Command/Event state work because it owns repository
carrier and registration surfaces.

## Acceptance

Tests cover private carrier visibility, duplicate registration failure, predicate composition,
stable ordering, empty/partial pages, total counts, and read-vs-persist UoW intent.
