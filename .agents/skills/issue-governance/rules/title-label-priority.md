# Title, Label, And Priority Rules

## Title Format

Use:

`<area>: <summary>`

Examples:

- `analysis: restore CommandHandlerToEntityMethod for top-level behavior extensions`
- `renderer: disable HTML escaping in drawing-board JSON output`
- `audit: merge custom Hibernate integrators safely`

## Required Labels

Each issue should normally include:

- one `type:*`
- one `area:*`
- one `priority:*`

Add one `source:*` label when relevant.

For a governed Parent/Child set:

- Parent issues normally use `type:feature` (or `type:investigation` while intent is being established), the owning `area:*`, and `priority:p1` or `priority:p0` when the contract blocks release.
- Child issues use the narrowest owning `area:*` and their own priority; they must link the Parent in the issue body and must not invent a second parent contract.
- Do not add `state:*` labels for hierarchy or lifecycle; use GitHub issue relationships, checklists, and comments.

## Label Groups

### `type:*`

- `type:bug`
- `type:feature`
- `type:cleanup`
- `type:investigation`
- `type:docs`

### `area:*`

Pick the closest area for the target repository.

Common `cap4k` areas:

- `area:analysis`
- `area:renderer`
- `area:generator`
- `area:dsl`
- `area:jpa`
- `area:special-fields`

Common `only-engine` areas:

- `area:audit`
- `area:spi`
- `area:autoconfig`
- `area:satoken`

Common `only-danmuku-zero` areas:

- `area:dogfood`
- `area:migration`
- `area:integration`
- `area:verification`

### `priority:*`

- `priority:p0`
- `priority:p1`
- `priority:p2`
- `priority:p3`

### `source:*`

- `source:dogfood`
- `source:migration`
- `source:user-report`

## Priority Definition

### `priority:p0`

Blocks mainline progress, release, or real-project integration, or produces invalid output.

### `priority:p1`

High-priority contract or capability gap that should enter implementation soon.

### `priority:p2`

Important issue with a workaround or limited blast radius.

### `priority:p3`

Readability, cleanup, low-risk usability, or deferred quality improvement.

## Progress Tracking

Use checklist items and issue comments for progress.

Do not use `state:*` labels.
