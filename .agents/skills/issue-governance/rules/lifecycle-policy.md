# Lifecycle Policy

## Checklist Requirement

Issues that are expected to move through design and implementation should include a lifecycle checklist in the body:

- [ ] spec written
- [ ] plan written
- [ ] implementation merged
- [ ] released if required
- [ ] downstream verified if required

Remove items only when they truly do not apply.

## Update Rules

### After spec is written

Do not close the issue.

Update the issue by:

- linking the spec
- checking `spec written`
- noting any scope split or major scope change

### After plan is written

Do not close the issue.

Update the issue by:

- linking the plan
- checking `plan written`
- noting cross-repository implementation or downstream verification needs

### After implementation is merged

Do not close automatically.

Check whether release or downstream verification is still required.

### After release

If downstream consumers depend on the published artifact, keep the issue open until release is complete.

### After downstream verification

If the issue requires real-project verification, keep it open until that verification is complete.

## Closure Rule

Close only when all applicable lifecycle steps are complete:

- spec, if required
- plan, if required
- implementation merged
- release completed, if required
- downstream verification completed, if required

## Common Non-Closure Cases

Do not close when:

- only spec exists
- only plan exists
- code exists but is not merged
- merge is done but publish is still required
- upstream fix landed but downstream dogfood verification is still pending

## Stable References

Issue bodies and lifecycle updates should prefer stable references:

- spec documents
- plan documents
- commits, pull requests, releases
- downstream verification evidence

Do not treat temporary roadmap or backlog files as long-term issue references when those files are expected to be retired after issue migration.
