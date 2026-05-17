# Framework Database Scripts

These scripts are one-time setup references for cap4k framework modules. Read this file during bootstrap or dependency setup, not during normal business modeling.

| Capability | Tables | Script |
|---|---|---|
| request records | `__request`, `__archived_request` | `references/sql/request.sql` |
| domain/integration event records | `__event`, `__archived_event` | `references/sql/event.sql` |
| HTTP integration event subscriber registry | `__event_http_subscriber` | `references/sql/event-http-subscriber.sql` |
| saga records | `__saga`, `__saga_process`, `__archived_saga`, `__archived_saga_process` | `references/sql/saga.sql` |
| distributed locker records | `__locker` | `references/sql/locker.sql` |

Use only the subset required by the selected cap4k dependencies. Translate dialect details before applying to non-MySQL databases.
