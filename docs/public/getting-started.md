# Getting Started

[中文](getting-started.zh-CN.md)

## Who This Path Is For

- teams that want to land DDD without a heavy framework story
- teams that want a strong default path before touching advanced concepts

## Read in This Order

1. [README](../../README.md)
2. work through the minimal workflow below on a small aggregate slice
3. read [Framework Positioning](framework-positioning.md) when you need clearer concept boundaries
4. if you are contributing inside this repository, continue with the relevant GitHub issue plus the matching [spec](../superpowers/specs/) and [plan](../superpowers/plans/)

## Minimal Workflow

1. identify the aggregate root and entity boundary
2. define command and query intent separately
3. let mutation converge into command handling
4. release domain events from the aggregate root when process continuation is needed
5. treat controller, job, and event handlers as coordination points, not the place where business truth lives

## Start Conservatively

- use the default happy path first
- do not start with advanced read/write modeling
- do not start with Saga, Strong ID, or Domain Service unless the problem really requires them

## Next Reading

- [Framework Positioning](framework-positioning.md)
