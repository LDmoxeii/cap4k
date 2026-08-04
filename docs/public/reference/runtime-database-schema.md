# Runtime Database Schema

本页列出 framework runtime SQL resources 和 table purposes。它不是完整 runtime configuration reference。

## SQL Resources

| Resource | Tables | Purpose |
| --- | --- | --- |
| `ddd-domain-event-jpa/src/main/resources/event.sql` | `__event`, `__archived_event` | integration/domain event dispatch persistence 与 archive state。 |
| `ddd-application-command-jpa/src/main/resources/command.sql` | `__command`, `__archived_command` | reliable Command execution、retry、result 与 archive state。 |
| `ddd-distributed-locker-jdbc/src/main/resources/locker.sql` | `__locker` | distributed lock rows，key 为 `name`。 |
| `ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql` | `__event_http_subscriber` | event/subscriber/callback_url HTTP subscriber registry。 |

## Table Purposes

| Table | Purpose |
| --- | --- |
| `__event` | active event dispatch record。 |
| `__archived_event` | archived event dispatch record。 |
| `__command` | active reliable Command execution/retry/result record。 |
| `__archived_command` | archived reliable Command record。 |
| `__locker` | distributed lock，key 为 `name`。 |
| `__event_http_subscriber` | 带 callback URL 的 HTTP integration-event subscriber registry。 |

## Boundaries

- 这些 SQL resources 属于 runtime infrastructure modules。
- Business schema 仍由项目拥有。
- Generator source inputs 与这些 runtime tables 分离。
- UUID7 是唯一内置的 application-side Strong ID 分配策略，不依赖 runtime 协调表；数据库自增或数据库分配的 identity 只是 persistence policy。
- 不要把本页当作 module-specific runtime configuration docs 的替代品。
