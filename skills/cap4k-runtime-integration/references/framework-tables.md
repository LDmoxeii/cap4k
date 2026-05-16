# Framework Tables

Runtime families with JPA persistence may require tables for:

- request records: `__request`, `__archived_request`
- domain event records: `__event`, `__archived_event`
- integration event HTTP subscriber registry: `__event_http_subscriber`
- saga records: `__saga`, `__saga_process`, `__archived_saga`, `__archived_saga_process`
- distributed locker records: `__locker`

Local examples should include only the required subset and translate SQL to the selected local database.
