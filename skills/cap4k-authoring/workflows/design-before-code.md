# Design Before Code

1. State the business process in command/event/query language.
2. Identify aggregate roots and which command mutates each aggregate.
3. Identify external boundaries: HTTP, callback, job, cli, or integration event.
4. Identify generated artifacts, handwritten artifacts, copied snapshots, and template overrides.
5. Check `references/known-gaps.md` before promising value object, saga, domain service, or integration-event generation.
6. Present a concise design or write a spec when the task is multi-step.
7. Wait for approval before implementation when the user is still discussing direction.

Output should name the layer and tactical object for every meaningful behavior.
