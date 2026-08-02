# Outcome

Implement issue #76 on top of the latest `origin/master` in an isolated worktree so generated Strong ID types bind through Spring MVC path variables and query parameters without any runtime converter registry, reflection scan, or Strong ID type discovery mechanism.

# Scope

- Update the generated Strong ID template to expose one JVM-static String factory that Spring's default conversion path can discover.
- Keep the generated factory delegating to the existing Strong ID semantic parser and validation logic rather than duplicating or relaxing parsing rules.
- Cover all four supported Strong ID backing shapes: UUIDv7 String, UUIDv7 UUID, Snowflake String, and Snowflake Long.
- Prove the contract with focused generator/template assertions, `DefaultConversionService` conversion checks, and real Spring MVC `@PathVariable` / `@RequestParam` binding tests.
- Include at least one generated-style non-root Strong ID type to prove that reference/owned IDs share the same conversion contract as aggregate-root IDs.

# Non-goals

- Do not add a framework-level `Converter`, `ConverterFactory`, `Formatter`, `WebMvcConfigurer`, or starter auto-registration path.
- Do not add reflection-based Strong ID scanning, runtime type registries, or any second conversion path outside generated type shape.
- Do not change Jackson JSON body behavior or JPA `@Embeddable` / `@EmbeddedId` mapping semantics beyond what is required to keep tests aligned with the generated template.
- Do not redesign Strong ID validation semantics, allocation semantics, or backing-strategy rules.
- Do not broaden this change into unrelated Strong ID diagnostics, docs drift, or round-trip analyzer work.

# Acceptance examples

- Given a generated UUIDv7 String-backed aggregate-root ID, `DefaultConversionService` can convert an incoming String to that ID type and a Spring MVC `@PathVariable` method receives the parsed Strong ID instance.
- Given a generated UUIDv7 UUID-backed aggregate-root ID, Spring MVC converts the incoming path segment from String to the Strong ID through the generated JVM-static String factory, and validation still rejects non-v7 or invalid UUID text.
- Given generated Snowflake String-backed and Long-backed aggregate-root IDs, query parameters bind through the same default conversion path and retain the existing canonical Snowflake validation.
- Given a generated-style reference or owned Strong ID type, query-parameter binding succeeds through the same generated JVM-static String factory without any MVC-specific converter registration.
- Given an invalid UUIDv4, nil UUID, malformed UUID, non-canonical Snowflake text, or other semantically invalid input, MVC binding fails and exposes the same Strong ID validation message chain instead of silently coercing the value.

# Constraints and invariants

- Generator output must remain Spring-agnostic except for emitting a JVM-static String factory shape that Spring already understands by default.
- The generated conversion surface must delegate to the existing `parse(String)` / `StrongIds` validation path so all boundaries share the same semantic rules.
- The generated JSON creator remains separate from MVC conversion; MVC must not depend on Jackson.
- The fix must preserve the current four-backing generated shape and compile/runtime behavior outside MVC binding.
- Tests must use generated-style Strong ID classes with non-public backing constructors so success cannot come from a public fallback constructor that the template does not generate.

# Decisions

- The generated Strong ID surface will expose `@JvmStatic fun from(value: String): <Type> = parse(value)` in the companion object for every supported backing.
- `from(String)` is the only new Spring-facing conversion surface; existing `of(backing)` and `parse(String)` semantics remain authoritative.
- MVC proof will live in focused runtime tests within an existing starter module that already carries Spring MVC test dependencies.
- The change is limited to code and verification evidence required by the audit contract and issue #76 implementation boundary.
- The user explicitly constrained this change to the isolated `fix/strong-id-mvc-binding` branch and requested no runtime converter/scanner mechanism.

# Open questions

- None. The user provided the target branch, audit contract, implementation constraints, and verification boundary up front.

# Verification expectations

- Renderer/template tests prove every generated Strong ID backing emits the JVM-static String factory and still compiles.
- Focused Strong ID runtime tests prove `DefaultConversionService.canConvert(String, StrongIdType)` and real `convert(...)` behavior for all four supported backings.
- Real Spring MVC tests prove `@PathVariable` and `@RequestParam` binding for generated-style Strong IDs, including at least one non-root ID type.
- Negative MVC tests prove invalid textual input is rejected through the same semantic validation path.
- Before closing the change, run the relevant generator, Strong ID, and MVC test tasks and record any skipped scope honestly.
