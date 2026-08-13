# Issue Hierarchy

## When To Use A Parent

Create a parent issue when one overall intent has two or more independently mergeable implementation slices, shared acceptance criteria, or composition risk across capabilities or repositories.

Keep a standalone issue when one reviewable change can carry the full intent and evidence.

## Parent Contract

The parent owns:

- overall problem and intended outcome
- stable links to current contracts, audit decisions, or active Comet changes when applicable
- global non-goals and invariants
- acceptance IDs and capability impact matrix
- required child inventory and dependency order
- composition and final verification evidence

A parent is a coordination and closure-audit object. Implementation PRs must not use a closing keyword against it.

## Child Contract

Each child owns one independently reviewable and mergeable slice:

- explicit parent reference
- slice boundary and non-goals
- acceptance IDs delegated from the parent
- affected capability surfaces and shared contracts
- dependencies on sibling slices
- focused verification and merge evidence

Prefer GitHub native sub-issues. If native hierarchy is unavailable, put `Parent: #<number>` in the child body and add the child link to the parent checklist.

## Pull Request Contract

A child PR uses:

- `Parent: #<parent>` without a closing keyword
- `Closes #<child>` only on the final PR for that child
- `Refs #<child>` on intermediate PRs
- explicit acceptance IDs, propagation closure, composition evidence, sibling responsibility, and audit focus

The parent and closing target must not be the same issue.

## Composition Rule

A merged child proves only its slice. The parent requires an overall audit after all required children are merged. The audit must verify the accepted commits are contained in one `origin/master` lineage and that shared contracts work together.