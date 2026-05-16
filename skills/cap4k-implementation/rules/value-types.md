# Value Type Rules

- Save aggregate-owned values through the aggregate root.
- Do not call `Mediator.uow.persist(valueObject)` for JSON-backed or inline aggregate-owned values.
- Use handwritten Kotlin types and converters for JSON-backed or inline values.
- Use table-backed `@VO` only when the model intentionally chooses separate persistence.
