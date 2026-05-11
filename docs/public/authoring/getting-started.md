# Getting Started

[中文](getting-started.zh-CN.md)

## Who This Path Is For

- teams that want to land DDD without a heavy framework story
- teams that want a strong default path before touching advanced concepts

## Read in This Order

1. [README](../../../README.md)
2. work through the minimal workflow below on a small aggregate slice
3. read [Framework Positioning](framework-positioning.md) when you need clearer concept boundaries

## Minimal Workflow

1. identify the aggregate root and entity boundary
2. define command and query intent separately
3. let mutation converge into command handling
4. release domain events from the aggregate root when process continuation is needed
5. treat controller, job, and event handlers as coordination points, not the place where business truth lives

## Minimal Audit During AI Collaboration

AI can draft designs, implement code, and run verification, but human authors should confirm before accepting the result:

- the domain flow still follows the minimal workflow above
- generated and handwritten boundaries are clear
- the AI provided reproducible test, compile, generation, or analysis evidence
- unsupported or incomplete capabilities are explicitly marked as gaps

## Start Conservatively

- use the default happy path first
- do not start with advanced read/write modeling
- do not start with Saga, Strong ID, or Domain Service unless the problem really requires them

## Next Reading

- [Framework Positioning](framework-positioning.md)
- [Authoring Guide Overview](index.md)
- [Default Happy Path](default-happy-path.md)
