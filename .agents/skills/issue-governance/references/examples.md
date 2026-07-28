# Examples

## `cap4k`

### `analysis: restore CommandHandlerToEntityMethod for top-level behavior extensions`

- repository: `cap4k`
- type: `type:bug`
- area: `area:analysis`
- priority: `priority:p1`
- source: `source:dogfood`

Reason: discovered in `only-danmuku-zero`, but the repair belongs in cap4k analysis/compiler code.

### `renderer: disable HTML escaping in drawing-board JSON output`

- repository: `cap4k`
- type: `type:cleanup`
- area: `area:renderer`
- priority: `priority:p3`
- source: `source:dogfood`

Reason: renderer output quality issue, not a project-local migration bug.

## `only-engine`

### `audit: provide default operator bridge for Sa-Token runtime`

- repository: `only-engine`
- type: `type:feature`
- area: `area:satoken`
- priority: `priority:p1`

Reason: runtime bridge code lands in `engine-satoken`, not in cap4k.

## `only-danmuku-zero`

### `verification: adopt published inverse-navigation fix and remove local workaround`

- repository: `only-danmuku-zero`
- type: `type:cleanup`
- area: `area:verification`
- priority: `priority:p2`
- source: `source:dogfood`

Reason: downstream adoption and validation work remains even after upstream fix is published.
