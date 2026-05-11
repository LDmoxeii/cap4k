# Close Task With Evidence

1. Run `git status --short` and inspect all changed files.
2. Run `git diff --check`.
3. Run focused tests, compile, generation, analysis, or link scans required by the changed files.
4. Search for known forbidden claims from `references/gotchas.md`.
5. Update the governing issue with spec, plan, implementation, verification, and follow-up status when requested or when lifecycle state changed.
6. Summarize:
   - files changed
   - commands run
   - results
   - known gaps
   - whether the issue can continue, merge, or close

## AAR Gate

Answer these before marking the task done:

1. Did this task reveal a reusable gotcha?
2. Should any rule, workflow, or reference in this skill change?
3. Did any issue lifecycle state change?
4. Is there a new gap that should be recorded or linked?
