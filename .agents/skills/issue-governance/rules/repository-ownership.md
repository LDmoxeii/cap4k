# Repository Ownership

## Core Rule

Assign the issue to the repository where the repair code will primarily land. Do not assign by discovery location or abstract stack level.

## Repository Mapping

### `cap4k`

Use `cap4k` for compiler, pipeline, planner, renderer, Gradle DSL, analysis, runtime/DDD contracts, generated artifact contracts, and cap4k framework capabilities.

### `only-engine`

Use `only-engine` for engine SPI, audit, Sa-Token, starters, auto-configuration, runtime audit behavior, and SPI bridges.

### `only-danmuku-zero`

Use `only-danmuku-zero` for real-project migration cleanup, project-specific modeling, downstream integration, release adoption, and workaround removal.

## Dogfood Escalation

Create the main issue upstream when the repair belongs upstream. Create a downstream issue only when adoption, workaround removal, or verification is independently required.

## Split Policy

Default to the smallest independently reviewable unit, not automatically to one issue.

Use a parent with children when one intent contains multiple independently mergeable slices, even in one repository. Split across repositories when each repository has independent implementation or verification work. Do not split tightly coupled edits that cannot be reviewed, merged, or verified separately.