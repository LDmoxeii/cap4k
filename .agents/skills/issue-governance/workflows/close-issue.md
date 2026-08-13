# Close Child Or Standalone Issue Workflow

1. Confirm all applicable lifecycle items and acceptance IDs are complete.
2. Confirm the Issue's declared outcome is merged: decision/spec Issues require the accepted decision archive, while implementation Issues require merged code evidence. Confirm the Comet archive is on the accepted lineage when Comet was used and required release/downstream evidence exists.
3. Confirm no remaining work belongs to the same issue.
4. For a child, update the parent with closure evidence and remaining composition risk.
5. Add a closing comment stating the exact closure basis.

Do not close a child merely because an intermediate PR merged. Do not use child closure as parent composition evidence by itself.