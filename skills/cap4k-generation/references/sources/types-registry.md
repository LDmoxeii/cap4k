# Types Registry

`types.registryFile` is configured under `types {}` rather than `sources {}`, but it is still part of the generation input contract.

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

- keys must be simple type names, not fully qualified names.
- each entry requires `fqn`.
- `converter` may be `false`, `"nested"`, or an explicit converter FQN.
- use this contract for JSON-backed or inline value carriers referenced by DB `@T`.
- value-object classes, normalization rules, and business invariants still belong to handwritten modeling and implementation.
