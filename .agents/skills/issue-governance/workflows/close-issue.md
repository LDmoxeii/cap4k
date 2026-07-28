# Close Issue Workflow

Use this workflow before closing any issue.

## Steps

1. Read the issue checklist and latest status comments.
2. Confirm all applicable lifecycle items are complete:
   - spec, if required
   - plan, if required
   - implementation merged
   - release completed, if required
   - downstream verification completed, if required
3. Confirm there is no remaining follow-up that belongs to the same issue.
4. If work moved to another issue, link that issue before closing.
5. Add a short closing comment that states the closure basis.

## Do Not Close If

- published artifact is still pending
- downstream adoption is still pending for a downstream-tracking issue
- verification evidence is still missing

## Completion Check

- closure reason is explicit
- no required lifecycle step is still open
