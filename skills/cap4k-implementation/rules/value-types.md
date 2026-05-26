# Value Type Rules

- Save aggregate-owned values through the aggregate root.
- Persist JSON-backed or inline aggregate-owned values through their aggregate root, not as separate UoW targets.
- Use `types.valueObjectManifest` for JSON-backed values that should generate checked-in source with a nested converter.
- Use handwritten Kotlin types and converters through `types.registryFile` for external or manually maintained JSON-backed or inline values.
- Do not duplicate enum manifest or value-object manifest entries in `types.registryFile`.
- Use table-backed `@VO` only when the model intentionally chooses separate persistence.
- Keep Strong ID identity boundaries distinct from value-object modeling. Same-context aggregate references should use target aggregate ID types; external concepts mapped into local language should use local reference IDs such as `AuthorId`.
