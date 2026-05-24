# Review Priorities

1. Behavioral bugs or generation output that will not compile.
2. Strong ID drift: aggregate-root IDs should be generated Strong ID types, same-context aggregate references should use target aggregate ID types, and local reference identities should not leak cross-context names.
3. Ownership bugs that overwrite handwritten project logic.
4. Layering violations that put business decisions in adapter or transport code.
5. Missing tests or verification for changed behavior.
6. Documentation drift that will mislead future agents.
