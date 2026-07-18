# Manifest Contracts

Use manifests through the `types` input surfaces.

## Enum Manifest

Register enum manifests through `types.enumManifest.files`.
Do not register them through `sources.enumManifest`.

- The root JSON value is an array.
- Each entry requires `name`, `package`, and `items`.
- `aggregates` omitted or empty means shared.
- `aggregates` identifies at most one owner.
- Each item requires integer `value`, string `name`, and string `desc`.
- Duplicate shared names are invalid.
- Duplicate names under the same owner are invalid.

## Value-Object Manifest

Register value-object manifests through `types.valueObjectManifest.files`.
Do not register them through `sources.valueObjectManifest`.

- The root JSON value is an array.
- Each entry requires `name` and `package`.
- `aggregates` omitted or empty means shared.
- `aggregates` identifies at most one owner.
- `storage` may only be `json`; omitted `storage` means `json`.
- `description` is optional.
- `fields` is optional.
- When present, each field requires `name` and `type`.
- Field `nullable` and `defaultValue` are optional.
- Duplicate shared names are invalid.
- Duplicate names under the same owner are invalid.
- Use `aggregates` for ownership.
