# cap4k Contract-First Query Contract Design

> Date: 2026-04-27
> Status: Draft for review
> Scope: ddd-core query contract, pipeline design query generation, default design templates, design input normalization guidance
> Out of scope: repository Optional/nullability cleanup, controller generation, API Result wrapper generation, mapper generation

## Background

The current cap4k query model has two different meanings for `Response`.

The base application contract is request/response oriented:

```kotlin
interface RequestParam<RESULT : Any>

interface RequestHandler<REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> {
    fun exec(request: REQUEST): RESPONSE
}

interface Query<PARAM : RequestParam<RESULT>, RESULT : Any> :
    RequestHandler<PARAM, RESULT>
```

In this model, `RESULT` / `RESPONSE` means the complete result returned by a request handler.

However, the current list/page query shortcuts use item-container semantics:

```kotlin
interface ListQuery<PARAM : RequestParam<List<ITEM>>, ITEM : Any> :
    Query<PARAM, List<ITEM>>

interface PageQuery<PARAM : RequestParam<PageData<ITEM>>, ITEM : Any> :
    Query<PARAM, PageData<ITEM>>

interface ListQueryParam<RESPONSE_ITEM : Any> :
    RequestParam<List<RESPONSE_ITEM>>

abstract class PageQueryParam<RESPONSE_ITEM : Any> :
    PageParam(),
    RequestParam<PageData<RESPONSE_ITEM>>
```

In this model, `ITEM` / `RESPONSE_ITEM` means one row, one list element, or one page item.

The generated design code currently inherits that shortcut model. For example:

```kotlin
class GetDanmukuPageQryHandler :
    PageQuery<GetDanmukuPageQry.Request, GetDanmukuPageQry.Response>
```

which produces:

```kotlin
override fun exec(request: GetDanmukuPageQry.Request): PageData<GetDanmukuPageQry.Response>
```

That makes `Response` mean "one page item", not "the complete response payload".

The same problem appears in list/tree-style generated payloads:

```kotlin
fun getCategoryTree(): List<GetCategoryTree.Response>
```

Here `Response` means one tree node, not the complete response payload.

This contradicts the contract-first style we want for the new pipeline.

## External Reference Point

The project direction is influenced by `netcorepal-cloud-framework`, which uses MediatR and FastEndpoints.

NetCorePal has two layers:

```csharp
public interface IQuery<out TResponse> : IRequest<TResponse>;

public interface IQueryHandler<in TQuery, TResponse> :
    IRequestHandler<TQuery, TResponse>
    where TQuery : IQuery<TResponse>;
```

This is request/response oriented: `TResponse` is the query result.

NetCorePal also has a paging shortcut:

```csharp
public interface IPagedQuery<TResponse> : IQuery<PagedData<TResponse>>
```

Here `TResponse` is effectively an item type, not a complete response. This shortcut is useful in hand-written code, but it mixes two meanings.

cap4k will take the contract-first side for generated design artifacts:

- A request class represents the complete request payload.
- A response class represents the complete response payload.
- List/page/tree are represented as fields inside the response payload.
- Generated design query handlers return `Response`, not `List<Response>` or `PageData<Response>`.

## Goal

Adopt a single, strict query contract for generated design code:

```text
Request -> Query Handler -> Response
```

Where:

- `Request` is the complete application request payload.
- `Response` is the complete application response payload.
- `Query<Request, Response>` is the only generated query handler contract.
- Collection and pagination shapes are response internals, not handler return wrappers.

The default generated code should no longer make `Response` mean "item".

## Non-Goals

This spec does not require controller generation.

This spec does not require API `Result<T>` / `ResponseData<T>` wrapper generation. Projects may wrap API responses in hand-written controllers or custom templates.

This spec does not require generating mapper implementations between API payload and application query result. Projects may use custom templates, slots, or hand-written mappers.

This spec does not include the ddd-core Optional-to-nullable cleanup described in `docs/design/ddd-core-nullability/analysis.md`.

This spec does not design sorting. Sorting must not be implicitly included in the first `page` trait.

This spec does not add arbitrary `superTypes` / base-class customization to design input. Trait semantics are enough for this iteration.

## Core Contract

### Keep `Query`

`Query<PARAM, RESULT>` remains the canonical query handler abstraction:

```kotlin
interface Query<PARAM : RequestParam<RESULT>, RESULT : Any> :
    RequestHandler<PARAM, RESULT>
```

Generated query handlers must use this shape:

```kotlin
class GetDanmukuPageQryHandler :
    Query<GetDanmukuPageQry.Request, GetDanmukuPageQry.Response> {

    override fun exec(request: GetDanmukuPageQry.Request): GetDanmukuPageQry.Response {
        TODO("Pending query implementation.")
    }
}
```

This makes the handler contract unambiguous:

```text
GetDanmukuPageQry.Request -> GetDanmukuPageQry.Response
```

### Remove Query Shortcuts

The following application query shortcuts should be removed from the core API:

- `ListQuery`
- `PageQuery`
- `ListQueryParam`
- `PageQueryParam`

Reason:

- They encode item-container semantics into the type system.
- They make the second generic argument mean item rather than result.
- They encourage generated code like `PageData<Response>`.
- They conflict with the contract-first rule that `Response` is the complete response payload.

This is intentionally breaking. There is no compatibility requirement for the new pipeline at this stage.

### Keep `PageParam` As Infrastructure Utility

Removing `PageQueryParam` does not automatically mean removing `PageParam`.

`PageParam` is still useful as a framework pagination utility for repository and helper APIs:

```kotlin
PageData.create(pageParam, totalCount, list)
repository.findPage(predicate, pageParam)
```

But generated API payloads and generated application query requests must not inherit `PageParam`.

`PageParam` contains behavior and mutable sorting state. That is acceptable for infrastructure-level helper usage, but too concrete for generated contract DTOs.

## Page Trait

### Add `PageRequest`

Introduce a lightweight interface for pagination-shaped request contracts:

```kotlin
interface PageRequest {
    val pageNum: Int
    val pageSize: Int
}
```

This interface is contract-oriented. It says a request carries pagination coordinates. It does not prescribe persistence behavior, repository behavior, sorting behavior, or handler return type.

### Page Trait Field Rules

The first iteration of `page` trait contains only:

- `pageNum`
- `pageSize`

Generated defaults:

```kotlin
override val pageNum: Int = 1
override val pageSize: Int = 10
```

The first iteration must not include sort.

Reason:

- Sorting is not part of basic pagination.
- Public sort APIs require field allow-lists and external-to-internal field mapping.
- Exposing arbitrary sort rules to the frontend is not a safe default.
- `OrderInfo` is a framework/internal sorting type and should not leak into API payload by default.

Sorting can be designed later as a separate trait.

### Application Query Request With Page Trait

Application query request:

```kotlin
object GetDanmukuPageQry {
    data class Request(
        override val pageNum: Int = 1,
        override val pageSize: Int = 10,
        val videoNameFuzzy: String? = null
    ) : PageRequest, RequestParam<Response>

    data class Response(
        val page: PageData<Item>
    )

    data class Item(
        val id: Long,
        val content: String
    )
}
```

The important part is:

```kotlin
RequestParam<Response>
```

not:

```kotlin
RequestParam<PageData<Response>>
```

### API Payload Request With Page Trait

Generated API payloads are not application requests. Therefore, they must not implement `RequestParam<Response>`.

API payload request:

```kotlin
object GetDanmukuPage {
    data class Request(
        override val pageNum: Int = 1,
        override val pageSize: Int = 10,
        val videoNameFuzzy: String? = null
    ) : PageRequest

    data class Response(
        val page: PageData<Item>
    )

    data class Item(
        val id: Long,
        val content: String
    )
}
```

This keeps API request payload and application query request aligned in shape, but not coupled by application runtime interfaces.

## Response Shape Rules

`Response` always means the complete response payload.

Single-object response:

```kotlin
object GetVideoInfoQry {
    data class Request(
        val videoId: Long
    ) : RequestParam<Response>

    data class Response(
        val videoId: Long,
        val title: String
    )
}
```

List response:

```kotlin
object GetRecommendVideosQry {
    class Request : RequestParam<Response>

    data class Response(
        val items: List<Item>
    )

    data class Item(
        val videoId: Long,
        val title: String
    )
}
```

Page response:

```kotlin
object GetVideoPageQry {
    data class Request(
        override val pageNum: Int = 1,
        override val pageSize: Int = 10,
        val keyword: String? = null
    ) : PageRequest, RequestParam<Response>

    data class Response(
        val page: PageData<Item>
    )

    data class Item(
        val videoId: Long,
        val title: String
    )
}
```

Tree response:

```kotlin
object GetCategoryTreeQry {
    class Request : RequestParam<Response>

    data class Response(
        val nodes: List<Node>
    )

    data class Node(
        val categoryId: Long,
        val categoryName: String,
        val children: List<Node>
    )
}
```

Generated code must not use these shapes:

```kotlin
List<GetCategoryTreeQry.Response>
PageData<GetVideoPageQry.Response>
```

## Design Input Rules

### Unified Query Tag

New design input should use one query concept:

```json
{
  "tag": "query",
  "name": "GetVideoPage",
  "traits": ["page"],
  "requestFields": [
    { "name": "keyword", "type": "String?", "nullable": true }
  ],
  "responseFields": [
    { "name": "page", "type": "PageData<Item>" },
    {
      "name": "item",
      "type": "Item",
      "children": [
        { "name": "videoId", "type": "Long" },
        { "name": "title", "type": "String" }
      ]
    }
  ]
}
```

`query_list` and `query_page` are not first-class canonical concepts after this change.

Accepted source compatibility during migration is allowed only as a parser-level alias if needed, but the canonical model must not preserve separate list/page variants.

### Nested Type Definition

The existing field-children nested model should remain the only nested type definition style.

Do not introduce a second `nestedTypes` source input format for this iteration.

Example:

```json
{
  "name": "nodes",
  "type": "List<Node>",
  "children": [
    { "name": "categoryId", "type": "Long" },
    { "name": "categoryName", "type": "String" },
    { "name": "children", "type": "List<self>" }
  ]
}
```

The renderer should convert this into:

```kotlin
data class Response(
    val nodes: List<Node>
)

data class Node(
    val categoryId: Long,
    val categoryName: String,
    val children: List<Node>
)
```

### Page Trait Source Representation

Preferred source representation:

```json
"traits": ["page"]
```

This should mean:

- request has `pageNum`
- request has `pageSize`
- request implements `PageRequest`
- application request also implements `RequestParam<Response>`
- API payload request does not implement `RequestParam<Response>`

Do not introduce arbitrary request super type strings in this iteration.

Do not include sorting in `page` trait.

## Canonical Model Changes

`QueryModel` should no longer need `QueryVariant` for generated semantics.

Current conceptual shape:

```kotlin
data class QueryModel(
    val variant: QueryVariant,
    ...
)
```

Target conceptual shape:

```kotlin
data class QueryModel(
    val packageName: String,
    val typeName: String,
    val description: String?,
    val traits: Set<RequestTrait>,
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>
)
```

`RequestTrait` can be a small enum for now:

```kotlin
enum class RequestTrait {
    PAGE
}
```

The exact internal name is flexible, but the semantics must be explicit and typed. Do not encode traits as raw inheritance strings.

Canonical query no longer answers "single/list/page query variant". It answers:

```text
What is the request shape?
What is the response shape?
What traits does the request carry?
```

## Generator Provider Changes

The default design query generator family should be simplified.

Before:

- query template
- query_list template
- query_page template
- query handler template
- query_list handler template
- query_page handler template

After:

- query template
- query handler template

The handler always implements:

```kotlin
Query<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response>
```

The request always implements:

```kotlin
RequestParam<Response>
```

If the query has `PAGE` trait, the request also implements:

```kotlin
PageRequest
```

The API payload generator should follow the same request/response field shape, but API request only implements `PageRequest` for page trait and does not implement `RequestParam<Response>`.

## Template Rules

### Application Query Template

Non-page request:

```kotlin
data class Request(
    ...
) : RequestParam<Response>
```

Page request:

```kotlin
data class Request(
    override val pageNum: Int = 1,
    override val pageSize: Int = 10,
    ...
) : PageRequest, RequestParam<Response>
```

No request fields:

```kotlin
class Request : RequestParam<Response>
```

No request fields with page trait:

```kotlin
data class Request(
    override val pageNum: Int = 1,
    override val pageSize: Int = 10
) : PageRequest, RequestParam<Response>
```

Empty response:

```kotlin
data object Response
```

The empty response rule should stay consistent with current command/client/query templates that already use `data object Response` in the pipeline renderer.

### API Payload Template

Non-page request:

```kotlin
data class Request(
    ...
)
```

Page request:

```kotlin
data class Request(
    override val pageNum: Int = 1,
    override val pageSize: Int = 10,
    ...
) : PageRequest
```

No request fields:

```kotlin
class Request
```

No request fields with page trait:

```kotlin
data class Request(
    override val pageNum: Int = 1,
    override val pageSize: Int = 10
) : PageRequest
```

API payload response still means complete response payload.

## Controller And Mapper Boundary

Default cap4k pipeline generation does not generate controllers.

Therefore, this spec does not require default controller signatures such as:

```kotlin
fun page(request: GetVideoPage.Request): Result<GetVideoPage.Response>
```

Projects may use slots or custom templates to generate controllers.

Projects may wrap API responses with a project-level result type such as:

```text
only-engine/engine-common/src/main/kotlin/com/only/engine/entity/Result.kt
```

That wrapper belongs to project controller templates, not the default design query contract.

Default cap4k pipeline generation also does not generate mapper implementations between API payloads and application query responses.

The architectural rule remains:

```text
API Request -> Application Query -> Application Result -> API Response
```

But default templates only define generated payload/query structures and handler skeletons. Mapping policy stays project-owned.

## Migration Policy

This is a breaking cleanup. Compatibility is not required for existing generated output.

Allowed migration aids:

- Parser-level aliases may temporarily accept old source tags such as `query_list` and `query_page` only if implementation tests or dogfood migration need a short-lived bridge.
- If aliases are kept temporarily, they must normalize into the unified canonical query model.
- Temporary aliases must not be documented as the new stable input format.
- Tests may use old inputs only to verify normalization.

Not allowed:

- Keeping `QueryVariant` as a semantic branch in canonical generation.
- Generating `ListQuery` or `PageQuery`.
- Generating `ListQueryParam` or `PageQueryParam`.
- Generating `List<Response>` as a query handler return type.
- Generating `PageData<Response>` as a query handler return type.
- Treating `Response` as an item model in default templates.

## Affected Areas

Expected implementation impact:

- `ddd-core` query interfaces:
  - remove `ListQuery`
  - remove `PageQuery`
  - remove `ListQueryParam`
  - remove `PageQueryParam`
  - add `PageRequest`
  - keep `Query`
  - keep `PageParam`

- `ddd-core` handler discovery:
  - remove `ListQuery` / `PageQuery` from request handler lookup fallbacks
  - keep `Query` as the single query marker

- pipeline API:
  - remove `QueryVariant` from canonical generation semantics
  - if a physical enum remains temporarily during refactoring, completion criteria still require no generator/template/provider branch to depend on it
  - add query request traits, at least `PAGE`
  - ensure query model does not encode item-container semantics

- design source assembly:
  - normalize query-like entries into a single query model
  - attach `PAGE` trait from source if requested
  - ensure old list/page tags do not leak into canonical generation

- design generator providers:
  - collapse query/list/page provider behavior where possible
  - generate one query artifact shape
  - generate one query handler artifact shape

- Pebble templates:
  - delete or stop using `query_list.kt.peb`
  - delete or stop using `query_page.kt.peb`
  - delete or stop using `query_list_handler.kt.peb`
  - delete or stop using `query_page_handler.kt.peb`
  - update `query.kt.peb`
  - update `query_handler.kt.peb`
  - update `api_payload.kt.peb` for `PageRequest` trait

- legacy codegen templates:
  - update or remove old query list/page templates if they are still built/tested
  - at minimum, prevent tests from asserting removed core APIs

- tests:
  - update renderer tests
  - update pipeline functional tests
  - add compile-level fixture proving page/list/tree responses use complete `Response`
  - update code analysis tests if they reference removed marker interfaces

- dogfood inputs:
  - normalize `only-danmuku-zero` design inputs to unified query response envelopes
  - remove `query_list` / `query_page` as stable target format

## Acceptance Criteria

Core API:

- `ddd-core` no longer contains `ListQuery`.
- `ddd-core` no longer contains `PageQuery`.
- `ddd-core` no longer contains `ListQueryParam`.
- `ddd-core` no longer contains `PageQueryParam`.
- `ddd-core` contains `PageRequest`.
- `Query<Request, Response>` remains the canonical query handler contract.
- `PageParam` still exists for repository/helper pagination use.

Generated application query code:

- Single query handlers return `XxxQry.Response`.
- List query handlers return `XxxQry.Response`.
- Page query handlers return `XxxQry.Response`.
- Tree query handlers return `XxxQry.Response`.
- No generated handler returns `List<XxxQry.Response>`.
- No generated handler returns `PageData<XxxQry.Response>`.
- Page request classes implement `PageRequest, RequestParam<Response>`.
- Non-page request classes implement only `RequestParam<Response>`.

Generated API payload code:

- API payload `Response` represents the complete API response payload.
- API payload page request classes implement `PageRequest`.
- API payload request classes do not implement `RequestParam<Response>`.
- API payload list/page/tree responses express collection shape through response fields.

Design model:

- Unified query source can express single/list/page/tree shapes.
- Page semantics are represented by request trait, not by handler return wrapper.
- Existing nested recursion support remains compatible with response envelopes.

Verification:

- `:ddd-core:test` passes.
- `:cap4k-plugin-pipeline-renderer-pebble:test` passes.
- `:cap4k-plugin-pipeline-generator-design:test` passes.
- `:cap4k-plugin-pipeline-gradle:test` passes with a sufficiently long timeout.
- A generated fixture compiles with:
  - page query response envelope
  - list query response envelope
  - recursive tree response envelope

## Risks

### Risk: Page Trait Becomes Too Broad

If `page` trait includes sorting too early, it will expose unsafe or misleading frontend sorting behavior.

Mitigation:

- First iteration includes only `pageNum/pageSize`.
- Sorting is a separate future trait.

### Risk: API And Application DTOs Look Duplicated

Keeping API payload and application query result separate can produce similar classes.

This is intentional. It preserves:

- API contract independence
- application contract independence
- explicit mapping boundary
- project-level freedom to wrap API responses with `Result<T>`

Default generation should not collapse this boundary.

### Risk: Removing Shortcuts Breaks Hand-Written Code

Deleting `PageQuery` / `ListQuery` may break hand-written code in current test projects or downstream experimental projects.

This is accepted for this iteration. The new pipeline has no compatibility requirement yet.

### Risk: Old Design Tags Keep Reintroducing Old Semantics

If old `query_list` / `query_page` tags remain canonical variants, templates may drift back to item-container semantics.

Mitigation:

- Normalize old tags at source/parser boundary if needed.
- Do not preserve variants in canonical model.
- Add tests that assert generated handlers use `Query<Request, Response>`.

## Explicitly Deferred

The following are deferred and should not be implemented as part of this spec:

- `Optional<T>` to `T?` migration in repository/aggregate APIs.
- `DomainServiceSupervisor.getService()` nullability change.
- `SagaProcessSupervisor.sendProcess()` nullability semantics.
- Arbitrary request/response `superTypes`.
- Sort trait.
- Controller generation.
- Mapper generation.
- Default `Result<T>` wrapper generation.
- API response advice/filter behavior.

## Final Decision

cap4k pipeline design generation will adopt contract-first query semantics.

The default query contract is:

```text
RequestParam<Response> + Query<Request, Response>
```

The default meaning of `Response` is:

```text
complete response payload
```

The default way to express list/page/tree is:

```text
fields inside Response
```

The default way to express pagination request shape is:

```text
PageRequest trait with pageNum/pageSize
```

The removed shortcut model is:

```text
ListQuery<Request, Item>
PageQuery<Request, Item>
ListQueryParam<Item>
PageQueryParam<Item>
```

This is a deliberate breaking change to keep generated contracts simple, explicit, and stable.
