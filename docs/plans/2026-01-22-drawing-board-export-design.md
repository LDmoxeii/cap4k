# Drawing Board Export Design

**Goal:** Extend cap4k code analysis to generate a full `drawing_board.json` from compiled code, routed via arch template to `design/drawing_board.json`.

**Architecture:** The compiler plugin emits a new per-module `design-elements.json` alongside `nodes.json`/`rels.json`. A new Gradle plugin merges those elements across modules and writes a single `drawing_board.json` to the path resolved from a new template node tag (`drawing_board`) via `GenArchTask`.

## Components

1. `cap4k-plugin-code-analysis-core`
   - Add shared model for design export (e.g., `DesignElement`, `DesignField`).
2. `cap4k-plugin-code-analysis-compiler`
   - Extend IR extraction to collect request/response fields, aggregates, and domain event flags.
   - Emit `design-elements.json` under `build/cap4k-code-analysis`.
3. `cap4k-plugin-code-analysis-drawing-board` (new module)
   - Gradle plugin + task to merge module outputs and render `drawing_board.json`.
   - Uses `GenArchTask` to resolve the output file path by tag.
4. Arch templates
   - Add a new template node tag `drawing_board` pointing to `design/drawing_board.json`.

## Output Schema

`design-elements.json` is an array of:
```
{
  "tag": "cmd|qry|cli|payload|de",
  "package": "authorize",
  "name": "IssueToken",
  "desc": "",
  "aggregates": ["User"],
  "entity": "User",
  "persist": true,
  "requestFields": [
    { "name": "userId", "type": "Long", "nullable": false, "defaultValue": null }
  ],
  "responseFields": [
    { "name": "token", "type": "String", "nullable": false, "defaultValue": null }
  ]
}
```

`drawing_board.json` is the merged array of these elements.

## Extraction Rules (Compiler)

- **Tag & Type**
  - `cmd/qry/cli`: RequestParam classes (e.g., `XxxCmd`, `XxxQry`, `XxxCli`).
  - `payload`: `adapter.portal.api.payload` objects.
  - `de`: `@DomainEvent` annotated classes (or aggregate type `domain-event`).
- **Fields**
  - Read constructor parameters from nested `Request` and `Response`/`Item` data classes.
  - Preserve `nullable` and `defaultValue` from IR.
  - Emit nested paths as `field.sub` or `list[].sub`.
  - Only emit nested types at root scope (no deeper than one root object).
- **Aggregates**
  - For `cmd/qry/cli`, resolve via graph relationships:
    `*To*Handler` -> `CommandHandlerToAggregate`.
  - For `de`, set `entity` to the aggregate name (from `@Aggregate`), `persist` from annotation, and `aggregates = [entity]`.
  - For `payload`, leave `aggregates` empty unless a factory-payload mapping is found.
- **Package/Name/Desc**
  - `package` = fqcn minus base package and fixed segments (commands/queries/clients/payload).
  - `desc` empty unless a description annotation is present (best-effort).

## Merge + Rendering (Aggregator Plugin)

- Read `nodes.json`, `rels.json`, `design-elements.json` from all module `build/cap4k-code-analysis` dirs.
- Merge by key `(tag, package, name)`. First-seen wins; log a warning on field conflicts.
- Resolve output path via `GenArchTask` using template tag `drawing_board`.
- Write JSON pretty-printed to `design/drawing_board.json`.

## Error Handling

- Missing `design-elements.json` in any input dir: fail task with a clear Gradle error.
- Missing `nodes.json`/`rels.json`: same behavior (consistent with flow-export).
- Partial metadata (missing desc/aggregates/fields): allowed, still emitted.

## Testing

- Compiler: IR-based tests for nested field flattening, default values, nullability, and aggregate/event mapping.
- Aggregator: unit tests for merge behavior and output rendering.

