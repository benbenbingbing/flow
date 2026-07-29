# Capacity and resilience

## Load test

Use production-like data in an isolated environment. Ramp gradually and hold
each stage long enough to observe HPA and database pools. Cover login, list
queries, process start/complete, async actions, audit writes, and S3 transfer.

Record at minimum:

- request rate and p50/p95/p99 latency by endpoint;
- HTTP errors and timeouts;
- CPU throttling, memory working set, restarts, and GC pauses;
- Hikari active/pending connections and MySQL slow queries;
- outbox/action queue depth and oldest-ready age;
- S3 latency and error rate.

The release limit is the lowest sustainable capacity of the application,
database, workflow engine, and object storage, with failure-domain headroom.

## Required fault drills

Run these after initial production-like deployment and at least quarterly:

1. Delete one server Pod during steady traffic. No request should fail and the
   replacement must become ready without running migrations or bootstrap.
2. Stop one schema-worker while publishing dynamic schemas. Each DDL request
   must reach `APPLIED` once; stale ownership must not acknowledge it.
3. Block database traffic. Readiness must fail, liveness must remain up, and
   traffic must stop reaching affected Pods. Restore traffic and verify recovery.
4. Restart object storage. Existing object integrity must survive; uploads must
   fail explicitly while unavailable.
5. Fill an async dependency with retryable failures, then restore it. Backlog
   must drain without duplicate non-idempotent side effects.
6. Drain one failure domain while respecting PDB and topology constraints.

Document timestamps, observed alerts, recovery time, data checks, and any manual
steps. A drill is incomplete if alerts did not reach the on-call route.
