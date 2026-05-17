# Service Boundary Rules

- Open Host Service means external systems or frontend synchronously consume internal capability; HTTP controller, RPC endpoint, and gRPC service are implementation forms.
- Published Language is the stable request, response, status, and error language exposed by an Open Host Service.
- External capability client means internal code consumes an outside capability through an anti-corruption port.
- External fact entry means an outside fact enters the system through callback, message listener, or inbound integration event.
- Internal fact publication means an internal fact is published outward, commonly as an outbound integration event.
- Read-oriented Open Host Service routes to query.
- Write-oriented Open Host Service routes to command.
- External fact entries that advance state route to command.
- Entry implementations never write repositories or aggregates directly.
- Client handlers translate external capability protocols and must not decide aggregate state machines.
