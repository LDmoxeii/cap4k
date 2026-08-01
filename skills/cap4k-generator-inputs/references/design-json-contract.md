# Design JSON Contract

## Scope

Edit files registered through `sources.designJson.files`, commonly `design/design.json`.

## Document Shape

- The root JSON value is an array.
- Every array item is an object.
- `tag` and `name` are required nonblank strings.
- `package` is required for every tag except `domain_event`.

## Supported Tags

- `command`
- `query`
- `capability`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`

## Supported Public Fields

- `tag`
- `name`
- `package`
- `description`
- `aggregates`
- `fields`
- `resultFields`
- `eventName`
- `persist`
- `artifacts`

## Combination Rules

- `resultFields` is allowed on `command`, `query`, `capability`, and `api_payload`.
- On `command`, `fields` describes the request payload and `resultFields` describes the command outcome payload.
- `command.resultFields` uses the same field object contract, design-json parsing, canonical preservation, and template rendering path as `query`, `capability`, and `api_payload` result fields.
- Empty or omitted `command.resultFields` keeps the no-result response shape.
- Tags outside the `command`, `query`, `capability`, and `api_payload` set do not define a result payload.
- `integration_event` requires `eventName`.
- `eventName` is allowed only on `domain_event` and `integration_event`.
- `persist` is allowed only on `domain_event`.
- `domain_event.fields` is the complete generated event payload. Omitted or empty `fields` generates a marker event with no payload values.
- `domain_event.aggregates` must contain exactly one owner aggregate and records ownership and placement only. It never contributes an Aggregate instance, Entity instance, Strong ID, snapshot, or any other implicit payload field.
- Every Domain Event payload fact must be declared explicitly in `fields`. Use scalars, Strong IDs, Value Objects, enums, or purpose-built immutable snapshots.
- The resolved semantic type graph of a Domain Event field must not contain a cap4k-known Aggregate or Entity, whether directly or inside a supported nested collection, `Array<T>` element, or named value type. This rule is identical for `persist = true` and `persist = false`.
- Field `type` values use Kotlin-style type expressions and compile into the canonical semantic type tree. Supported forms are builtin/named types, `List<T>`, `Set<T>`, `Map<K, V>`, `Array<T>`, and recursive `?`. Do not use `self`.
- Do not declare a separate `nullable` property. Write `Money?` or `List<Money?>?` in `type`.
- Mutable collections, `Collection`, `Iterable`, `Sequence`, primitive array aliases such as `IntArray`/`LongArray`, tuples, and arbitrary generic types are unsupported.
- `PageData<Item>` is a query/API result-only envelope, not a general generic type.
- Explicit `defaultValue` must be accepted by the semantic default compiler; nullability alone does not synthesize a default.
- Field names have no special payload-safety behavior; validate the resolved semantic type graph instead.

## Field Set Boundary

Design JSON input uses only the supported public fields listed above. Keep ownership, request shape, result shape, event naming, persistence intent, and artifact selection on those fields. For Domain Events, keep aggregate ownership in `aggregates` and historical fact values in explicit `fields`; do not merge the two concerns.

## Analysis Evidence Boundary

Flow and drawing-board output can resemble design JSON, but it is not automatically valid `sources.designJson.files` input.

Manually copied drawing-board content may be registered only after it satisfies this contract. `command.resultFields` is supported when the rest of the copied content satisfies this contract.

Copied content must satisfy the supported tag set, field set, field object shape, and artifact selection rules before registration.
