# Run Focused Tests

1. Identify affected modules and behavior.
2. Classify the expected evidence shape: domain behavior, application command
   boundary, adapter/integration smoke, generation/design contract, or
   analysis/flow evidence.
3. Identify critical behavior that must be direct-focused coverage, not only a
   smoke-test side effect.
4. Choose the narrowest compile/test command that proves the change.
5. Run the command fresh.
6. Read the exit code and meaningful failures.
7. For each critical behavior, state whether coverage is direct or indirect.
8. Check whether command retreat/no-op results are inspectable enough to assert
   the reason; if not, record residual precision risk.
9. For multi-listener continuation, check independent listeners, idempotency, no
   ordering assumptions, and command-side retreat for inapplicable paths.
10. If failures are unrelated, state the evidence and residual risk.
11. Report command, result, coverage shape, residual test risk, and skipped checks.
