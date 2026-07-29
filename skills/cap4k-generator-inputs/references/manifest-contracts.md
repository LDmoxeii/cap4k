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
- `persistence` is optional; omission means a pure value with no persistence projection.
- The supported projection is `"persistence": {"kind": "json"}`.
- The removed `storage` field is invalid; there is no implicit JSON compatibility.
- `description` is optional.
- `fields` is optional.
- When present, each field requires `name` and `type`.
- `type` uses the closed semantic type algebra: builtin/named, `List<T>`, `Set<T>`, `Map<K, V>`, and recursive `?`.
- Field `nullable` is removed. Put nullability in `type`.
- `defaultValue` is optional and must be accepted by the semantic default compiler.
- Duplicate shared names are invalid.
- Duplicate names under the same owner are invalid.
- Use `aggregates` for ownership.
- The checked-in Value Object contains pure value semantics only. Explicit JSON persistence generates a separate build-owned `<ValueObjectName>JsonAttributeConverter`.
