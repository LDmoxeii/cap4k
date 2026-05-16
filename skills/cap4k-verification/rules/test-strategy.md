# Test Strategy

- Prefer domain and application behavior tests first.
- Use adapter or integration smoke tests when the project claims runnable HTTP or external event behavior.
- Avoid brittle line-by-line snapshots of generated analysis output.
- Use analysis outputs to review relationships and flows, not to replace compile/tests.
