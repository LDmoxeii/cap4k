# 2026-04-27 cap4k Design Nested Model Recursion Design

## Background

The current pipeline design generator supports only a shallow nested payload shape.

`design-json` and canonical models still carry request and response payloads as flat `FieldModel` lists. The generator later derives nested Kotlin classes from field names such as `address.city`.

This works for one-level nested models, but it fails for real `only-danmuku` design inputs that contain:

- multi-level nested list payloads such as `fileList[].variants[].quality`
- response trees such as `children: List<Response>`
- old plugin `Item` response contracts such as `list: List<Item>`

The zero dogfood project currently skips 7 design entries because of this gap. Those entries are not business implementation details. They are design payload structures that the generator should eventually express.

## Goal

Add stable design generator support for:

- multi-level nested request and response models
- root model recursion through a design-level `self` type
- nested type recursion through explicit nested type names
- deterministic nested type ordering and type resolution

The feature should fit the current pipeline architecture without reintroducing the old JSON runtime model.

## Non-Goals

This slice does not:

- restore the old long-term `Item` response contract as a core pipeline semantic
- add controller or payload mapping generation
- infer business handler implementations
- change aggregate, db-source, analysis-core, or analysis-compiler behavior
- redesign `FieldModel` into a recursive public API model in this iteration
- add aliases for old `payload` or `de` tags
- support cross-namespace nested type references between request and response models

Old `Item` and `Response` references may be normalized in dogfood input scripts, but they are not new design DSL primitives.

## Current Behavior

### Source and Canonical Layers

`FieldModel` is flat:

```kotlin
data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
    ...
)
```

`DesignJsonSourceProvider.parseFields()` reads each JSON field into `FieldModel` without interpreting dotted paths or `[]`.

`DefaultCanonicalAssembler` copies design entry fields into canonical command, query, client, API payload, and domain event models. It does not build nested structures.

That boundary is acceptable and should remain stable for this slice.

### Generator Layer

`DesignPayloadRenderModelFactory.buildNamespace()` currently performs nested model derivation.

It splits `FieldModel.name` by `.` and requires exactly two parts for nested fields:

```text
address.city
```

It rejects deeper paths:

```text
account.bank.code
fileList.variants.quality
```

It also requires a compatible direct root field:

```json
{ "name": "address", "type": "Address" },
{ "name": "address.city", "type": "String" }
```

The generated render model exposes:

```kotlin
DesignRenderNestedTypeModel(
    name = "Address",
    fields = listOf(...)
)
```

This is a flat list of nested types, not a recursive tree.

### Template Layer

Current templates place request nested types inside `Request` and response nested types inside `Response`.

That namespace split is useful and should remain.

Example:

```kotlin
data class Request(
    val address: Address
) : RequestParam<Response> {
    data class Address(
        val city: String
    )
}
```

## Design DSL Rules

### Flat Field Path Syntax

The design input continues to express payloads as flat fields.

Supported path syntax:

```text
name
parent.child
parent[].child
parent[].child[].leaf
```

The `[]` marker indicates that the path segment is a collection container.

These two forms are equivalent after normalization:

```json
{ "name": "fileList", "type": "List<FileItem>" }
{ "name": "fileList[].fileIndex", "type": "Int" }
```

```json
{ "name": "fileList", "type": "List<FileItem>" }
{ "name": "fileList.fileIndex", "type": "Int" }
```

The first form is preferred for design readability because it shows list ownership in the field path.

### Direct Container Declarations

Every nested container must have a direct declaration that defines its type.

Valid:

```json
{ "name": "fileList", "type": "List<FileItem>" },
{ "name": "fileList[].variants", "type": "List<VariantItem>" },
{ "name": "fileList[].variants[].quality", "type": "String" }
```

Invalid:

```json
{ "name": "fileList[].variants[].quality", "type": "String" }
```

The invalid form does not define what `fileList` or `variants` should be called in generated Kotlin.

### `self`

`self` is a reserved design type keyword.

It means the current request or response namespace root model:

- in `requestFields`, `self` renders as `Request`
- in `responseFields`, `self` renders as `Response`

The main supported use case is response tree models:

```json
{ "name": "children", "type": "List<self>", "nullable": true }
```

Generated response:

```kotlin
data class Response(
    val children: List<Response>?
)
```

`self` always points to the namespace root. It does not mean the current nested type.

`self` is not introduced for domain event fields in this slice. Domain event nested type support remains limited to explicit nested type names.

### Explicit Nested Type Names

Nested type recursion uses explicit nested type names.

Example:

```json
{ "name": "variants", "type": "List<VariantItem>" },
{ "name": "variants[].quality", "type": "String" },
{ "name": "variants[].children", "type": "List<VariantItem>" }
```

Generated request:

```kotlin
data class Request(
    val variants: List<VariantItem>
) : RequestParam<Response> {
    data class VariantItem(
        val quality: String,
        val children: List<VariantItem>
    )
}
```

This keeps the DSL explicit. The user can see that `VariantItem.children` recurses into `VariantItem`, not into `Request` or `Response`.

### Type Resolution Priority

Within each namespace, type resolution priority is:

1. `self`
2. current namespace nested type names
3. Kotlin built-in types
4. explicit FQCNs
5. existing symbol registry resolution
6. project type registry resolution
7. unresolved short type failure

The important rule is that current namespace nested types win over external symbols.

If a nested type named `Item` is declared in response fields, `List<Item>` references that nested response type. This is allowed as a local model name, but `Item` itself is not a special global contract.

### Namespace Isolation

Request and response namespaces are independent.

If request and response both declare `Item`, they are separate local types:

```kotlin
data class Request(...) {
    data class Item(...)
}

data class Response(...) {
    data class Item(...)
}
```

No field in request may silently bind to response `Item`, and no field in response may silently bind to request `Item`.

## Render Model Design

The public API can keep flat `FieldModel` for this slice.

The generator should introduce an internal namespace tree model roughly equivalent to:

```kotlin
private data class PayloadPathNode(
    val name: String,
    val path: List<String>,
    val explicitField: FieldModel?,
    val children: List<PayloadPathNode>,
)
```

The tree is an implementation detail used to produce the existing render model shape:

```kotlin
DesignRenderModel(
    requestFields = ...,
    requestNestedTypes = ...,
    responseFields = ...,
    responseNestedTypes = ...,
)
```

`DesignRenderNestedTypeModel` can remain:

```kotlin
data class DesignRenderNestedTypeModel(
    val name: String,
    val fields: List<DesignRenderFieldModel>,
)
```

It does not need recursive `nestedTypes` yet because Kotlin allows sibling nested classes to reference each other by name inside the same outer class.

## Nested Type Ordering

Nested type ordering must be deterministic.

Recommended order:

1. declaration encounter order by first container field appearance
2. nested children discovered after their parent container
3. stable de-duplication by nested type name inside the namespace

Example input:

```json
{ "name": "fileList", "type": "List<FileItem>" },
{ "name": "fileList[].variants", "type": "List<VariantItem>" },
{ "name": "fileList[].variants[].quality", "type": "String" }
```

Generated order:

```kotlin
data class FileItem(...)
data class VariantItem(...)
```

Kotlin does not require `VariantItem` to be declared before `FileItem`, but deterministic output is required for stable tests and stable generated diffs.

## Compatibility and Migration Rules

### Existing One-Level Nested Inputs

Existing one-level nested inputs must keep generating equivalent Kotlin.

Example:

```json
{ "name": "address", "type": "Address" },
{ "name": "address.city", "type": "String" }
```

This remains valid.

### Old `List<Response>`

`List<Response>` is a legacy or implementation-leaking way to express root response recursion.

New canonical design input should use:

```json
{ "name": "children", "type": "List<self>" }
```

`only-danmuku-zero` normalization may convert old `List<Response>` to `List<self>` before pipeline input.

Core generator support for literal `Response` as a special recursive keyword is not required. If users write `Response` directly, it should be treated like an ordinary type name unless the implementation explicitly decides to tolerate it as a migration bridge.

### Old `List<Item>`

`Item` is not a new global pipeline semantic.

For old inputs:

```json
{ "name": "list", "type": "List<Item>" },
{ "name": "list[].messageType", "type": "Int" }
```

The preferred normalized input is:

```json
{ "name": "list", "type": "List<Item>" },
{ "name": "list[].messageType", "type": "Int" }
```

This is valid only because `list` directly declares `Item` as a local nested type and child fields define its structure.

For old tree payloads:

```json
{ "name": "children", "type": "List<Item>" }
```

There is no child field that defines local `Item`, so dogfood normalization should convert it to:

```json
{ "name": "children", "type": "List<self>" }
```

The generator should not silently guess this conversion in core.

## Error Handling

The generator should fail fast with field-level messages for invalid shapes.

Required errors:

- missing direct container declaration
- incompatible direct container type
- duplicate nested type name inside one namespace
- blank or malformed path segment
- use of `self` where no namespace root type exists
- unresolved short type after considering namespace nested types and type registry
- ambiguous external short type where no nested type shadows it

Error messages should include:

- namespace: request, response, or domain event payload
- design type name
- field path
- offending type when applicable

Example:

```text
failed to build response payload for VideoCommentPageQry: missing direct container declaration for children.items
```

## Impacted Modules

Expected code changes are limited to:

- `cap4k-plugin-pipeline-generator-design`
- `cap4k-plugin-pipeline-gradle` tests and fixtures
- `only-danmuku-zero` dogfood normalization input scripts and skipped-design baseline, when validating real project inputs

Expected code that should not change:

- `cap4k-plugin-pipeline-source-db`
- `cap4k-plugin-pipeline-generator-aggregate`
- `cap4k-plugin-analysis-core`
- `cap4k-plugin-analysis-compiler`

`cap4k-plugin-pipeline-api` may remain unchanged unless implementation discovers a strong reason to expose nested structure publicly. The default position is to keep it unchanged.

## Testing Strategy

### Unit Tests

Add generator design tests for:

- one-level nested input still works
- multi-level nested request input
- multi-level nested response input
- `self` in response root recursion
- `self` in request root recursion
- nested type self-recursion through explicit nested type name
- namespace isolation when request and response both define `Item`
- nested type name shadows type registry or external symbol
- missing direct container declaration fails clearly
- incompatible direct container type fails clearly
- duplicate nested type names fail clearly

### Functional Tests

Add or extend Gradle functional tests to generate compile-safe Kotlin for:

- `fileList[].variants[].quality`
- `children: List<self>`
- `list: List<Item>` plus `list[].messageType`

At least one functional test should compile generated Kotlin, not just inspect plan JSON.

### Dogfood Validation

After core tests pass, update `only-danmuku-zero` normalization so that the previously skipped entries can enter `codegen/design/design.json` when their only blocker is nested model support.

The expected first dogfood target is reducing or eliminating the 7 skipped design entries currently recorded in `codegen/design/skipped-design.json`.

## Risks

### Risk: accidentally restoring old `Item` semantics

The feature should not make `Item` a magic global response model. It should only allow local nested type names.

### Risk: type resolution ambiguity

Nested type names must win inside their namespace. Without this rule, local generated classes can be confused with type-registry or external imported classes.

### Risk: unstable generated output

Path tree traversal must preserve deterministic order. Otherwise repeated generation may produce noisy diffs.

### Risk: over-expanding the slice

This slice is about payload shape generation only. Handler business logic, controller adapter contracts, and old MapStruct converter contracts stay out of scope.

## Acceptance Criteria

This slice is complete when:

- current one-level nested design inputs still generate equivalent Kotlin
- multi-level nested paths generate nested Kotlin model classes
- `self` is documented and implemented as the root namespace recursion keyword
- explicit nested type names can recursively reference their own nested type
- request and response nested namespaces remain isolated
- generated imports remain deterministic and do not import local nested types
- invalid nested shapes fail with clear messages
- relevant Gradle functional tests pass and include compile-level verification
- `only-danmuku-zero` can stop skipping entries whose only blocker was multi-level nested model or root response recursion

## Deferred Work

These topics remain separate:

- controller generation
- API payload converter generation
- old `Item` contract compatibility as a public feature
- recursive public `FieldModel` API
- analysis output schema changes
- design tag aliasing for old `payload` and `de`
