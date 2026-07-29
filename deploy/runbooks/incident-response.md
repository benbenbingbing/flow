# Incident response

## Availability

Check Deployment conditions, ready endpoints, node pressure, recent rollouts,
PDB state, and ingress health. If only one replica is unhealthy, preserve
capacity and inspect it before deletion. If all replicas fail readiness, check
database reachability and completion of all three bootstrap jobs.

Do not disable readiness probes to restore traffic.

## HTTP errors

Split 5xx by URI, exception type, and dependency latency. Correlate the response
`X-Trace-Id` with the ECS JSON `traceId` field and the system audit record.
Check recent releases before increasing resources. Redact tokens, passwords,
file bodies, and business payloads from tickets and chat.

## Database

Inspect Hikari pending/active connections, MySQL connection count, slow queries,
locks, replication lag, and storage latency. Scaling Pods can worsen connection
exhaustion. Reduce concurrency or HPA maximum before raising database limits.

Runtime Pods must never receive schema credentials as a workaround.

## Durable queues

For `workflow_queue_items{state="dead"}` or excessive oldest-ready age:

1. Identify queue (`outbox` or `flow_action`) and the oldest failed record.
2. Confirm the downstream side effect is idempotent before replay.
3. Fix dependency/configuration failures before changing queue state.
4. Replay a bounded batch and watch error rate, latency, and lease ownership.

Never bulk-reset dead items without preserving attempts and the last error.

For schema changes, inspect `workflow_schema_change`. A `RUNNING` row may be
reclaimed only after its lease expires. Fencing tokens prevent a stale worker
from acknowledging work owned by a newer worker.

## Object storage

Check bucket existence, credentials, endpoint DNS/TLS, quota, and object
versioning. Do not switch a multi-Pod deployment to local filesystem storage.
If storage is unavailable, stop workflows that require attachments rather than
accepting writes that cannot be read by another Pod.
