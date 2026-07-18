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
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`
- `saga`

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

- `resultFields` is allowed on `command`, `query`, `client`, and `api_payload`.
- On `command`, `fields` describes the request payload and `resultFields` describes the command outcome payload.
- `command.resultFields` uses the same field object contract, design-json parsing, canonical preservation, and template rendering path as `query`, `client`, and `api_payload` result fields.
- Empty or omitted `command.resultFields` keeps the no-result response shape.
- `resultFields` remains rejected on `domain_event`, `integration_event`, `domain_service`, and `saga`.
- `integration_event` requires `eventName`.
- `eventName` is allowed only on `domain_event` and `integration_event`.
- `persist` is allowed only on `domain_event`.
- Field `type` values must use explicit type names. Do not use the token `self`.
- In `domain_event.fields`, field name `entity` is reserved.

## Removed Or Rejected Fields

These fields are not valid design JSON input:

- `desc`
- `requestFields`
- `responseFields`
- `traits`
- `role`
- `scope`
- `entity`

## Analysis Evidence Boundary

Flow and drawing-board output can resemble design JSON, but it is not automatically valid `sources.designJson.files` input.

Manually copied drawing-board content may be registered only after it satisfies this contract. `command.resultFields` is supported when the rest of the copied content satisfies this contract.

For example, copied content that still contains `responseFields`, an unsupported tag, or an invalid artifact selection must be corrected before registration.
