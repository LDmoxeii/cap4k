# Create Issue Workflow

Use this workflow when creating a new GitHub issue from a roadmap item, backlog item, or direct user request.

## Steps

1. Determine the primary repair code location.
2. Choose the repository using `rules/repository-ownership.md`.
3. Decide whether one issue is enough or whether cross-repository independent work requires multiple issues.
4. Choose title format using `rules/title-label-priority.md`.
5. Assign `type:*`, `area:*`, and `priority:*` labels. Add `source:*` when relevant.
6. Write a concise body that includes:
   - background
   - current problem
   - expected result
   - non-goals
   - acceptance criteria
   - links to related spec, plan, files, or dogfood evidence
7. Add the lifecycle checklist when the issue is expected to go through spec, plan, and implementation stages.

## Completion Check

- repository assignment matches repair code location
- labels match the issue type and area
- body is actionable
- lifecycle checklist exists when needed
