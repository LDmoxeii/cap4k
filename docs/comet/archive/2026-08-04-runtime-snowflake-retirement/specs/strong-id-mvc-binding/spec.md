# Strong ID MVC binding

## Purpose

Generated Strong ID types shall bind from Spring MVC String-based request inputs without requiring any framework-level converter registration, runtime reflection, or Strong ID type scanning.

## Generated conversion contract

- Every generated Strong ID type shall expose one JVM-static String factory with the shape `from(String): <Type>`.
- The JVM-static String factory shall delegate to the existing `parse(String)` path.
- `parse(String)` shall remain responsible for routing to the correct semantic validation for the backing and strategy.
- The generated `of(backing)` factories remain unchanged and continue to represent the direct backing-type construction path.
- The generated String factory shall exist for all supported Strong ID backings:
  - UUIDv7 stored as `String`
  - UUIDv7 stored as `UUID`

## Validation invariants

- String-based MVC conversion shall enforce the same semantic validation as every other String entry path.
- The generated MVC-facing factory shall not duplicate, weaken, or bypass `StrongIds.requireUuidV7(...)`.
- Invalid UUID variant/version, malformed UUID text, and nil UUID shall still fail.
- Conversion failure shall not silently coerce the input into another value.

## Runtime boundary

- Cap4k shall not add a generic Strong ID `Converter`, `ConverterFactory`, `Formatter`, `WebMvcConfigurer`, or starter auto-registration mechanism for this capability.
- Cap4k shall not add reflection-based Strong ID scanning, runtime registries, or any second Strong ID conversion path.
- Spring MVC support shall come solely from the generated Strong ID JVM surface that default Spring conversion can already discover.
- JSON body conversion remains owned by Jackson annotations and is a separate boundary from MVC path/query binding.

## Applicability

- Aggregate-root Strong IDs shall bind through the generated String factory.
- Reference and owned-child Strong IDs that use the same generated type shape shall share the same MVC conversion contract.
- The contract applies to Spring MVC String-based controller inputs such as `@PathVariable` and `@RequestParam`.

## Acceptance scenarios

### Aggregate-root path binding

Given a generated aggregate-root Strong ID type and a controller method parameter declared as that type, Spring MVC binds a path segment String into the Strong ID through the generated JVM-static String factory.

### Query-parameter binding across backings

Given UUIDv7 String/UUID Strong ID types, Spring MVC binds query parameters into each corresponding Strong ID type through the same generated String factory contract.

### Shared contract for non-root IDs

Given a generated-style reference or owned Strong ID type, Spring MVC binds it from request parameters without special registration, proving the contract is shared beyond aggregate-root IDs.

### Invalid textual input

Given semantically invalid textual input, MVC binding fails through the existing Strong ID validation path and does not accept or normalize the invalid value.
