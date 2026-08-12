# Close Parent Issue Workflow

1. Read the parent, all required children, and latest status comments.
2. Confirm every required child is closed with valid merge/release/downstream evidence.
3. Verify accepted child commits are contained in one `origin/master` lineage.
4. Re-run or inspect the overall composition checks for shared contracts and parent acceptance IDs.
5. Record the final child inventory, master commit, checks, releases, downstream evidence, and residual risks.
6. Close the parent manually with an explicit closure-audit comment.

Do not close when any required child, acceptance item, lineage proof, or composition check is missing.