# Triage Backlog Workflow

Use this workflow when converting roadmap or backlog entries into GitHub issues.

## Steps

1. Read the source backlog or roadmap item carefully.
2. Separate:
   - upstream framework work
   - runtime work
   - downstream dogfood tracking
3. Assign each actionable item to the repository where repair code will land.
4. Avoid duplicating issues unless repositories have independent implementation work.
5. If the item is only a weak observation and not yet actionable, leave it in backlog until the problem statement and acceptance criteria are clear enough.
6. If the item already has spec or plan documents, link them in the issue body at creation time.
7. Treat roadmap and backlog files as migration input, not as long-term issue references, when those files are planned for deletion after migration.

## Completion Check

- each created issue has a clear owner repository
- no duplicate upstream/downstream issue was created without reason
- each issue has actionable acceptance criteria
- issue references point to stable artifacts rather than temporary migration files
