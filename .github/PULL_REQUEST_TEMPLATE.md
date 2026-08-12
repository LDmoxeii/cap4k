## Summary

-

## Target Branch

- [ ] `master`

## Change Type

- [ ] Code, build, scripts, workflow, tests, fixtures, or templates
- [ ] Documentation-only
- [ ] Repository governance or GitHub configuration

## Issue Hierarchy

- Parent: N/A - explain why this is a standalone change
- Direct issue: N/A - explain why no Child/Standalone Issue owns this PR or write `#124`
- Closing target: N/A - explain why no issue is closed or write `Closes #124`

Use `Parent: #123` and `Direct issue: #124` for a child PR. Use `Closing target: Closes #124` only when this PR completes that Direct issue. Intermediate PRs keep the same Direct issue, use a reasoned closing `N/A`, and may write `Refs #124` elsewhere.

## Acceptance IDs

- A1

Use acceptance IDs from the Parent/Child Issue or spec. If none apply, write `N/A - <reason>`.

## Capability Impact

| Surface | Result | Evidence |
| --- | --- | --- |
| Runtime | verified-no-change | Explain the contract check or changed files. |
| Generator | verified-no-change | Explain the descriptor, canonical model, planner, template, or artifact check. |
| Analyzer | verified-no-change | Explain the input, observation, output, or compatibility check. |
| AgentFacts | verified-no-change | Explain the section/schema/facts check. |
| Public Docs | verified-no-change | Link changed pages or explain why current wording remains correct. |
| Skill | verified-no-change | Link changed routes/rules or explain why current behavior remains correct. |

Allowed results: `modified`, `verified-no-change`, `not-applicable`. Every row requires concrete evidence or a reason.

## Shared Contracts

- Name the descriptors, registries, canonical models, schemas, tasks, artifacts, or governance contracts touched. Use `N/A - <reason>` only when no shared contract applies.

## Propagation Closure

- Changed contract nodes: N/A - replace with comma-separated code-derived node IDs such as `surface.generator`, or keep a reasoned N/A when the diff has no capability-contract seed.
- Closure evidence: State how direct and transitive dependency edges were checked. Include the generated capability facts or focused compatibility evidence.

## Composition Evidence

- State cross-module/cross-slice checks and the accepted `origin/master` lineage evidence. For an isolated PR, use `N/A - <reason>`.

## Sibling Slice Responsibility

- State what remains in sibling Child Issues/PRs. For a standalone change, use `N/A - standalone change because <reason>`.

## Audit Focus

- Tell reviewers which assumptions, boundaries, negative cases, and compatibility claims require independent scrutiny.

## Verification

- [ ] Full Gradle check: `./gradlew check`
- [ ] Focused tests:
- [ ] Capability contract validation: `./scripts/validate-capability-contract.ps1`
- [ ] Static validation:
- [ ] Not run because:

## Docs-Only Skip Reason

If this is documentation-only, list the changed doc/template paths that allow CI to skip the full Gradle check. Otherwise use `N/A - not documentation-only`.

-

## Related Spec Or Plan

-

## Agent Review

- [ ] Requested as non-blocking advisory review
- [ ] Not requested because:

## Release Note

-
