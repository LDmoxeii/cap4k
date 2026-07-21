# Runtime Database Schema

本页列出 framework runtime SQL resources 和 table purposes。它不是完整 runtime configuration reference。

## SQL Resources

| Resource | Tables | Purpose |
| --- | --- | --- |
| `ddd-domain-event-jpa/src/main/resources/event.sql` | `__event`, `__archived_event` | integration/domain event dispatch persistence 与 archive state。 |
| `ddd-application-request-jpa/src/main/resources/request.sql` | `__request`, `__archived_request` | application request execution、retry、result 与 archive state。 |
| `ddd-distributed-saga-jpa/src/main/resources/saga.sql` | `__saga`, `__saga_process`, `__archived_saga`, `__archived_saga_process` | Saga forward execution、process rows、compensation 与 archive。 |
| `ddd-distributed-locker-jdbc/src/main/resources/locker.sql` | `__locker` | distributed lock rows，key 为 `name`。 |
| `ddd-distributed-snowflake/src/main/resources/worker_id.sql` | `__worker_id` | Snowflake datacenter/worker id allocation。 |
| `ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql` | `__event_http_subscriber` | event/subscriber/callback_url HTTP subscriber registry。 |

## Table Purposes

| Table | Purpose |
| --- | --- |
| `__event` | active event dispatch record。 |
| `__archived_event` | archived event dispatch record。 |
| `__request` | active application request execution/retry/result record。 |
| `__archived_request` | archived application request record。 |
| `__saga` | Saga instance state。 |
| `__saga_process` | Saga process / step state。 |
| `__archived_saga` | archived Saga instance state。 |
| `__archived_saga_process` | archived Saga process / step state。 |
| `__locker` | distributed lock，key 为 `name`。 |
| `__worker_id` | Snowflake worker allocation。 |
| `__event_http_subscriber` | 带 callback URL 的 HTTP integration-event subscriber registry。 |

## Boundaries

- 这些 SQL resources 属于 runtime infrastructure modules。
- Business schema 仍由项目拥有。
- Generator source inputs 与这些 runtime tables 分离。
- 不要把本页当作 module-specific runtime configuration docs 的替代品。
