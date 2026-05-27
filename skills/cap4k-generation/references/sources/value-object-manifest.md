# Value-Object Manifest

`types.valueObjectManifest` is a types input contract, not a `sources {}` family and not a design tag.

Minimal DSL:

```kotlin
types {
    valueObjectManifest {
        files.from("design/value-objects.json")
    }
}
```

Rules:

- use it for JSON-backed value objects that should generate checked-in source.
- manifest entries do not need matching `types.registryFile` entries.
- `scope` must be `shared` or `aggregate`.
- `scope = shared` must not set `aggregate`.
- `scope = aggregate` must set `aggregate`; duplicate names are rejected within the same aggregate.
- `storage` currently supports `json`.
- each value object must declare at least one field with `name` and `type`.
- generated value-object source is `CHECKED_IN_SOURCE`; default conflict policy is `SKIP`.
- generated converters are nested directly inside the value-object class.
