# Cap4k Skill Route Map

Use this file when a task appears to match more than one focused skill.

| If the user asks for... | Route first to... | Then chain to... |
|---|---|---|
| "build a project from scratch" | `cap4k-modeling` | `cap4k-generation`, `cap4k-implementation`, `cap4k-verification` |
| "generate from DB/design" | `cap4k-generation` | `cap4k-generated-output-review`, `cap4k-verification` |
| "fill generated handlers" | `cap4k-implementation` | `cap4k-verification` |
| "callback/integration event flow" | `cap4k-service-integration` | `cap4k-implementation`, `cap4k-verification` |
| "is this generated output safe to edit?" | `cap4k-generated-output-review` | `cap4k-generation` if regeneration is needed |
