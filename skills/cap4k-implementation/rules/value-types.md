# Value Type Rules

- Save aggregate-owned values through the aggregate root.
- Persist JSON-backed or inline aggregate-owned values through their aggregate root, not as separate UoW targets.
- Use handwritten Kotlin types and converters for JSON-backed or inline values.
- Use table-backed `@VO` only when the model intentionally chooses separate persistence.
- Keep Strong ID identity boundaries distinct from value-object modeling. Same-context aggregate references should use target aggregate ID types; external concepts mapped into local language should use local reference IDs such as `AuthorId`.
