# Repository Ownership

## Core Rule

Assign the issue to the repository where the repair code will primarily land.

Do not assign by discovery location. Do not assign by abstract stack level. Assign by repair code location.

## Repository Mapping

### `cap4k`

Use `cap4k` when the work primarily changes:

- compiler plugin
- pipeline, planner, renderer
- Gradle DSL
- analysis / irAnalysis
- DDD core or repository contract
- generated artifact contract
- cap4k-side spec-driven framework capability

### `only-engine`

Use `only-engine` when the work primarily changes:

- `engine-spi`
- `engine-audit`
- `engine-satoken`
- starter or auto-configuration
- runtime audit behavior
- SPI bridge or runtime integration

### `only-danmuku-zero`

Use `only-danmuku-zero` when the work primarily changes:

- real-project migration cleanup
- project-specific design or modeling problems
- downstream integration or verification work
- release-consumption alignment
- temporary workaround removal after upstream fixes land

## Dogfood Escalation

If a problem is discovered in `only-danmuku-zero` but the fix belongs in `cap4k` or `only-engine`, create the main issue in the upstream repository.

Create a separate `only-danmuku-zero` issue only when the real project also has independent follow-up work, such as:

- removing a workaround
- adopting a published fix
- running downstream validation

## Split Policy

Default to one issue.

Split into multiple issues only when multiple repositories each have independent implementation work.
