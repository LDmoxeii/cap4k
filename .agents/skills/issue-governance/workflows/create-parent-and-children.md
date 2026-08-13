# Create Parent And Children Workflow

1. Create the Parent with overall intent, global acceptance IDs, capability impact matrix, invariants, composition criteria, and stable links to current contracts or audit decisions. Do not copy detailed Comet specs into it.
2. Identify the smallest independently reviewable and mergeable slices.
3. Create one child per slice with delegated acceptance IDs, boundaries, dependencies, verification, and sibling responsibilities.
4. Add each child through GitHub native sub-issues. If unavailable, add reciprocal parent/child links and a parent checklist.
5. Record dependency order without treating it as permission to merge incompatible partial contracts.
6. State that implementation PRs close children only and that parent closure requires one-lineage composition evidence.

Do not create empty coordination children or split solely by directory.