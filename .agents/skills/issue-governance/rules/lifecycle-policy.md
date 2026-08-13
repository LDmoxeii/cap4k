# Lifecycle Policy

## When An Issue Is Needed

Create or keep an Issue when work must survive beyond the current authorized change: deferred or optional work, prioritization, blocked work, parent/child composition, cross-repository coordination, release tracking, downstream verification, or other durable lifecycle state.

Do not require an Issue for an immediately authorized, coherent change that can be Shaped, implemented, verified, archived, and reviewed as one unit. If the user explicitly requests an Issue, create it, but do not inflate it into a duplicate specification.

## Issue And Comet Boundary

- The Issue records the macro problem, expected outcome, non-goals, ownership, dependencies, priority, and lifecycle state.
- Comet Shape creates the detailed change target, scenarios, acceptance loop, and evidence model.
- `docs/comet/changes/<change>/specs/**` is the active target for that change.
- `docs/comet/specs/**` is the accepted authoritative contract after Archive. It may precede implementation, but it is not proof of current code support or a future-work queue.
- Undecided work discovered for later remains in an Issue. Once a spec/audit change explicitly confirms a target, Archive may publish that canonical contract before implementation, while an Issue continues to track priority and delivery status; never use a spec-only Archive to claim the code is implemented.
- When an Issue exists, one independently mergeable Child/Standalone Issue normally maps to one Comet change. A Parent normally composes multiple changes and PRs.

## Checklist Requirement

Standalone and child Issues include only applicable lifecycle items. Do not copy Comet's detailed task or acceptance list into the Issue:

- [ ] execution change or decision PR linked
- [ ] verification evidence accepted
- [ ] implementation or decision, including any Comet archive, merged
- [ ] released if required
- [ ] downstream verified if required

Parents additionally track:

- [ ] required children identified and linked
- [ ] all required children complete
- [ ] composition audit passed on one `origin/master` lineage

## Update Rules

After each milestone, update the checklist and add a short evidence comment linking the Comet change, decision record, PR, merge commit, release, or downstream verification. Record scope changes and new child dependencies explicitly without copying detailed change specs into the Issue.

A merged child does not prove parent completion. A parent never auto-closes from an implementation PR.

## Child Or Standalone Closure

Close only when all applicable lifecycle items are complete and the Issue's declared outcome is merged. A decision/spec Issue may close on an accepted spec-only Archive; an implementation Issue requires merged implementation evidence. When Comet is used, its verified archive must be included on the accepted lineage. If several PRs implement one child, intermediate PRs reference it and only the final PR closes it.

## Parent Closure

Close only when:

- all required children are closed for valid reasons
- required releases or downstream checks are complete
- accepted child commits are contained in one `origin/master` lineage
- the overall acceptance matrix and shared contracts have composition evidence
- no remaining work belongs to the same intent

## Common Non-Closure Cases

Do not close for Shape-only work, an unmerged audit decision, unmerged code, pending publication, pending downstream adoption, incomplete children, or missing composition evidence.