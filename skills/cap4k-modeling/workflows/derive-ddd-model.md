# Derive DDD Model

1. Start from agreed business vocabulary.
2. Propose aggregate roots and explain each consistency boundary.
3. Classify child entities and value concepts.
4. Identify invariants and the command that enforces each invariant.
5. Identify domain events emitted after state transitions.
6. Identify domain services only for decisions that cross aggregate boundaries.
7. Mark first-class `value_object` and `domain_service` generation as unsupported; keep implementation handwritten.
8. Produce DDL/design JSON recommendations only after the tactical model is coherent.
