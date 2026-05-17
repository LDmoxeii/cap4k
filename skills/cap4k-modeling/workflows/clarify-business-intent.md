# Clarify Business Intent

## Inputs

- User story, requirement text, DDL draft, design draft, or verbal business goal.

## Steps

1. Identify actors, business outcome, write commands, read needs, external systems, and failure paths.
2. List decisions that are missing or ambiguous.
3. Ask the human only for decisions that cannot be safely inferred from supplied project evidence.
4. Separate domain decisions from generation decisions.
5. Produce a short modeling brief with commands, queries, aggregates, events, external contracts, and unresolved choices.

## Output

Use this format:

```markdown
## Modeling Brief

- Business outcome:
- Actors:
- Commands:
- Queries:
- Aggregates:
- Value objects:
- Domain events:
- Integration events:
- External systems:
- Human decisions still needed:
```
