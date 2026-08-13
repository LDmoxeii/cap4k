# Create Issue Workflow

1. Confirm why a durable Issue is needed. Do not manufacture one merely as a prerequisite for an immediately authorized Comet change.
2. Determine the primary repair code location and repository.
3. Decide whether the request is a standalone Issue or belongs under a Parent using `rules/issue-hierarchy.md`.
4. Choose title and labels from `rules/title-label-priority.md`.
5. Record macro-level background, current problem, expected outcome, non-goals, dependencies, capability surfaces/shared contracts, and stable references. Keep detailed behavior, scenarios, and acceptance checks in Comet Shape.
6. Add the lifecycle checklist when implementation, release, downstream verification, or cross-change coordination applies.
7. For a Child, link the Parent with native sub-issue relation or the explicit fallback backlink.
8. When execution starts, link the Comet change and implementation PR rather than copying their detailed contents into the Issue.

Completion requires actionable scope, correct ownership, applicable labels, evidence expectations, and hierarchy linkage.