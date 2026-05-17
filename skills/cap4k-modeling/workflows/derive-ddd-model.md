# Derive DDD Model

1. Start from agreed business vocabulary.
2. Propose aggregate roots and explain each consistency boundary.
3. Classify child entities and value objects.
4. Identify invariants and the command that enforces each invariant.
5. Identify domain events as meaningful domain facts; record whether handling must be synchronous or can be asynchronous.
6. Identify domain services only for domain decisions that cross aggregate boundaries.
7. Classify service interaction boundaries before choosing transport or generation details.
8. Produce DDL/design JSON recommendations only after the tactical model is coherent.
