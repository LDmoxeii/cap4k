# Design Open Host Service

1. Identify who consumes the synchronous capability.
2. Define the Published Language: request fields, response fields, status meanings, and error meanings.
3. Route reads to query.
4. Route writes to command.
5. Keep controller, RPC endpoint, or gRPC service code as input/output translation only.
6. Do not access repository or aggregate state directly from the entry implementation.
