# Types Registry

`types.registryFile` is configured under `types {}` rather than `sources {}`, but it is still part of the generation input contract. Use it for external or handwritten custom types that are not already declared by `types.enumManifest` or `types.valueObjectManifest`.

Minimal shape:

```json
{
  "OrderId": { "fqn": "com.acme.order.OrderId" },
  "Customer": { "fqn": "com.acme.customer.Customer", "converter": false },
  "External": {
    "fqn": "com.acme.external.ExternalValue",
    "converter": "com.acme.external.ExternalValueConverter"
  }
}
```

Rules:

- keys must be non-blank simple type names, not dotted names or fully qualified names.
- built-in type names cannot be overridden, for example `String`, `Long`, `List`, or `Any`.
- each entry must be an object with `fqn`.
- `fqn` must be a non-blank fully qualified class name.
- `converter` may be `false`, `"nested"`, or an explicit converter FQN.
- duplicate names after normalization are rejected, for example the same simple name after trimming whitespace.
- duplicate fields, unsupported fields, and non-object values are rejected.
- invalid converter values are rejected; only `false`, `"nested"`, or a converter FQN are allowed.
- use this contract for JSON-backed or inline value carriers referenced by DB `@T`.
- do not duplicate enum manifest or value-object manifest entries here.
- value-object classes, normalization rules, and business invariants still belong to handwritten modeling and implementation.
