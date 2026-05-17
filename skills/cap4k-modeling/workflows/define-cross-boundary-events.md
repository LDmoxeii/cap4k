# Define Cross-Boundary Interaction

1. Decide whether the system consumes external capability, exposes synchronous capability, receives external fact, or publishes internal fact.
2. For external capability, define a client contract in internal business language.
3. For Open Host Service, define the Published Language and route reads to query and writes to command.
4. For external fact entry, translate state-advancing callback/message/event payloads into internal command; translate observations into query or an explicit application entry point.
5. For internal fact publication, derive outbound integration events from domain facts or application process results.
6. Keep transport details out of domain events and aggregate behavior.
