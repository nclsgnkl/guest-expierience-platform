---
name: deep-review
description: >-
  Architecture, concurrency, and security deep-review checklist for complex
  or risky changes. Loaded only by the `architect` subagent — for everyday
  review use the `code-review` skill instead.
---

# Deep Review Checklist

Reference for the `architect` subagent. Load only when performing a deep
review — reserve this for complex/risky changes, not routine ones.

## 1. Architecture
- Clean separation of concerns / layer boundaries?
- Single responsibility per module/class?
- No cyclic dependencies between components?
- Domain model captures the right abstractions?

## 2. Concurrency & Threading
- Shared mutable state protected correctly?
- Async/concurrent code free of race conditions?
- Resource pool sizing (connections, workers) adequate?
- No blocking calls on a path expected to be non-blocking?

## 3. Data Layer
- Correct transaction boundaries?
- Migrations safe (backward-compatible, reversible)?
- Injection prevention (parameterized queries, escaped input)?
- ORM/raw-query decision appropriate for the complexity?

## 4. External Integrations / Messaging
- Idempotency guaranteed where retries are possible?
- Failure/retry strategy defined (at-least-once vs. exactly-once, dead-letter handling)?
- Backpressure or rate-limit handling?
- Schema/contract evolution compatible with consumers?
- Serialization/deserialization robust to malformed input?

## 5. Performance
- Handles expected load/throughput?
- N+1 query or repeated-computation prevention?
- Pagination for large result sets?
- Caching strategy where appropriate, with invalidation considered?

## 6. Security
- No secrets in logs?
- Auth/session handling intact and tested?
- Input validated at every system boundary?
- No injection vectors (SQL, command, template)?
- CORS/CSRF handling if applicable?

## 7. Resilience
- Fault tolerance patterns used where dependencies can fail (retry, timeout, circuit breaker)?
- Graceful degradation on dependency failure?
- Health checks accurate?
- Timeouts configured for every external call?

## 8. Edge Cases
- Null/undefined handling at boundaries?
- Empty collections / empty batches?
- Duplicate events / idempotency?
- Partial failures / network interruptions?
- Data migration or schema changes backward compatible?
