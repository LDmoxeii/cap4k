# Domain Language Rules

- Ask for business vocabulary before naming commands, aggregates, events, or fields.
- Separate facts the business already knows from implementation guesses.
- Treat aggregate boundaries as business consistency boundaries, not table grouping.
- Name commands as user or process intentions, not CRUD operations.
- Name domain events as completed business facts.
- Name integration events as cross-boundary message contracts, not internal domain facts.
- Name reference identities in the current context's language. If the content context calls an external account an author, model `AuthorId` in `Content` instead of leaking a cross-context `UserId`.
- Use `@RefId=<TypeName>` when DB input needs to map an external concept into a current-context identity name.
- Record unresolved domain choices as human decisions, not agent assumptions.
