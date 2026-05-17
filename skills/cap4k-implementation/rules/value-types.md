# Value Type Rules

- Save aggregate-owned values through the aggregate root.
- Persist JSON-backed or inline aggregate-owned values through their aggregate root, not as separate UoW targets.
- Use handwritten Kotlin types and converters for JSON-backed or inline values.
- Use table-backed `@VO` only when the model intentionally chooses separate persistence.
