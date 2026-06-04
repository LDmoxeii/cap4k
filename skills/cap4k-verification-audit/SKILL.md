---
name: cap4k-verification-audit
description: >
  Use for cap4k verification and audit claims: static review, focused local
  evidence, full evidence, generated-versus-handwritten ownership checks,
  skipped-check disclosure, rollback targeting, and final evidence summaries.
---

# Cap4k Verification Audit

Verify structure, ownership, behavior, and evidence without overstating what
was checked.

## Always Read

1. `../shared/rules/verification-claim-policy.md`
2. `references/evidence-modes.md`
3. `workflows/run-verification-audit.md`

## Route Boundary

Use when the task asks for verification, audit, final evidence, or claim
strength. Route-level `routing.yaml` supplies forced rollback, drift gotchas,
and runtime capability map for this route.

## Common Tasks

| Task | Read | Workflow |
|---|---|---|
| Static-only skill or docs audit | `references/evidence-modes.md` | `workflows/run-verification-audit.md` |
| Focused local evidence when allowed | `references/evidence-modes.md` | `workflows/run-verification-audit.md` |
| Final verification summary | `references/evidence-modes.md` | `workflows/run-verification-audit.md` |
