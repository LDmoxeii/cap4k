# Lifecycle Policy

## Checklist Requirement

Standalone and child issues expected to move through design and implementation include applicable checklist items:

- [ ] spec written
- [ ] plan written
- [ ] implementation merged
- [ ] released if required
- [ ] downstream verified if required

Parents additionally track:

- [ ] required children identified and linked
- [ ] all required children complete
- [ ] composition audit passed on one `origin/master` lineage

## Update Rules

After each milestone, update the checklist and add a short evidence comment linking the spec, plan, PR, merge commit, release, or downstream verification. Record scope changes and new child dependencies explicitly.

A merged child does not prove parent completion. A parent never auto-closes from an implementation PR.

## Child Or Standalone Closure

Close only when all applicable lifecycle items are complete and the final implementation is merged. If several PRs implement one child, intermediate PRs reference it and only the final PR closes it.

## Parent Closure

Close only when:

- all required children are closed for valid reasons
- required releases or downstream checks are complete
- accepted child commits are contained in one `origin/master` lineage
- the overall acceptance matrix and shared contracts have composition evidence
- no remaining work belongs to the same intent

## Common Non-Closure Cases

Do not close for spec-only, plan-only, unmerged code, pending publication, pending downstream adoption, incomplete children, or missing composition evidence.