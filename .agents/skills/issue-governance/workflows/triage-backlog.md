# Triage Backlog Workflow

Use this workflow when converting roadmap or backlog entries into GitHub issues.

## Steps

1. Read the source backlog or roadmap item carefully.
2. Separate:
   - upstream framework work
   - runtime work
   - downstream dogfood tracking
3. Assign each actionable item to the repository where repair code will land.
4. For work spanning multiple capability surfaces, create one Parent Issue for intent and acceptance, then create Child Issues for independently reviewable slices; record dependencies and composition evidence on the Parent.
5. If the item is only a weak observation and not yet actionable, leave it in backlog until the problem statement and acceptance criteria are clear enough.
6. If the item already has a current contract, audit decision, or Comet change, link it in the Issue body without copying its detailed contents. A Child Issue states its owned boundary, required upstream facts, downstream projections, and the Parent acceptance IDs it advances.
7. Treat roadmap and backlog files as migration input, not as long-term issue references, when those files are planned for deletion after migration. Do not use a historical roadmap paragraph as the current capability contract.

## Completion Check

- each created issue has a clear owner repository
- no duplicate upstream/downstream issue was created without reason
- each Issue has an observable macro outcome without duplicating a change specification
- issue references point to stable artifacts rather than temporary migration files
