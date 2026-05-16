# Handle External Callback

1. Treat the callback as adapter input or inbound integration event.
2. Parse and validate external protocol payload in adapter-facing code.
3. Translate the external fact into an application command or process step.
4. Keep third-party status codes out of aggregate behavior unless normalized.
5. Persist internal state changes through command handlers and UoW.
6. Emit domain events only after internal domain state changes.
7. Run an adapter smoke test or focused integration test when the project claims runnable callback behavior.
