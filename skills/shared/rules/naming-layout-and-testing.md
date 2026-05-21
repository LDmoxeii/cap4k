# Naming Layout And Testing

- Files belong in responsibility directories; do not place code by convenience or physical proximity.
- File name plus directory should make the role inferable.
- Keep transport DTOs, external protocol details, query projections, and domain behavior in their proper layers.
- Default verification starts with domain behavior tests and application orchestration tests.
- Test helpers must stay thin and must not hide business semantics behind opaque DSLs.
- Analysis output helps review relationships and flows, but does not replace compile or tests.
