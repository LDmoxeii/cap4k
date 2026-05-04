# Getting Started

[中文](getting-started.zh-CN.md)

## Who This Path Is For

- teams that want to land DDD without a heavy framework story
- teams that want a strong default path before touching advanced concepts

## Read in This Order

1. `README.md`
2. `docs/public/framework-positioning.md`
3. the relevant GitHub issue
4. the relevant spec or plan under `docs/superpowers/`

## Minimal Workflow

1. identify the aggregate root and entity boundary
2. define command and query intent separately
3. let mutation converge into command handling
4. release domain events from the aggregate root when process continuation is needed
5. treat controller, job, and event handlers as orchestration surfaces rather than truth sources

## Start Conservatively

- use the default happy path first
- do not start with advanced read/write weak-reference modeling
- do not start with Saga, Strong ID, or Domain Service unless the problem really requires them

## Next Reading

- [Framework Positioning](framework-positioning.md)
