# Role Boundary

## Reader

This skill serves an AI author implementing a business project using cap4k. It is not a framework-maintainer guide.

## Human Responsibilities

- Own business decisions, domain vocabulary, aggregate boundaries, and final tradeoff choices.
- Approve DDL, design JSON, generated-output ownership, and technical design before high-impact implementation.
- Perform final audit of generated code, handwritten code, tests, and reported evidence.

## AI Responsibilities

- Clarify missing domain details before code when decisions are ambiguous.
- Draft DDL, design JSON, technical方案, and implementation plans for human review.
- Run plan tasks before generation and inspect planned outputs before writing files.
- Implement generated skeletons and handwritten project code within the agreed layer boundaries.
- Run focused compile, tests, generation, and analysis checks; report exact commands and outcomes.

## Out Of Scope

- Do not govern cap4k framework issue lifecycle from this skill.
- Do not decide whether cap4k itself should accept, close, or release framework issues.
- Do not edit runtime, generator, plugin, or reference project internals unless the user explicitly assigns that scope.
