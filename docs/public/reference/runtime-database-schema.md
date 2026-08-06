# Runtime Database Schema

本页列出 framework runtime SQL resources 和 table purposes。它不是完整 runtime configuration reference。

## SQL Resources

| Resource | Tables | Purpose |
| --- | --- | --- |
| `ddd-domain-event-jpa/src/main/resources/event.sql` | `__event` | reliable integration/domain event delivery state and safe failure facts。 |
| `ddd-application-command-jpa/src/main/resources/command.sql` | `__command` | reliable Command execution、retry state and safe failure facts。 |
| `ddd-distributed-locker-jdbc/src/main/resources/locker.sql` | `__locker` | distributed lock rows，key 为 `name`。 |
| `ddd-integration-event-http-jpa/src/main/resources/event_http_subscriber.sql` | `__event_http_subscriber` | event/subscriber/callback_url HTTP subscriber registry。 |

## Table Purposes

| Table | Purpose |
| --- | --- |
| `__event` | reliable event delivery record with safe structured failure facts。 |
| `__command` | reliable Command execution/retry record with safe structured failure facts。 |
| `__locker` | distributed lock，key 为 `name`。 |
| `__event_http_subscriber` | 带 callback URL 的 HTTP integration-event subscriber registry。 |

## Boundaries

- 这些 SQL resources 属于 runtime infrastructure modules。
- Business schema 仍由项目拥有。
- Generator source inputs 与这些 runtime tables 分离。
- Reliable Command/Event 的完成状态留在活动记录状态机，不另建结果轮询仓库或历史归档表。
- UUID7 是唯一内置的 application-side Strong ID 分配策略，不依赖 runtime 协调表；数据库自增或数据库分配的 identity 只是 persistence policy。
- 不要把本页当作 module-specific runtime configuration docs 的替代品。
