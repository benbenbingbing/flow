# Webhook delivery

This runbook covers production Webhook delivery failures. Webhook delivery is
at-least-once: a timeout can occur after the receiver commits successfully.
Never bypass receiver-side event ID deduplication to clear an incident.

## Initial triage

1. Record the alert start time, affected application, release revision and
   `X-Trace-Id`. Do not copy signing secrets or full event bodies into tickets.
2. Check `flow_webhook_pending`, `flow_webhook_dead`,
   `flow_webhook_oldest_pending_seconds` and delivery outcomes by application
   and event type.
3. In the administration API, inspect recent delivery status, HTTP status,
   bounded error message, attempt count and next attempt time.
4. Confirm the target hostname is still approved, DNS resolves to the expected
   public addresses, TLS is valid and the destination CIDRs remain allowed by
   NetworkPolicy.
5. Distinguish receiver failure from Flow worker saturation before changing
   concurrency.

Do not update `webhook_delivery` directly. Lease ownership, fencing tokens,
replay sequence and audit records are part of the recovery contract.

## Dead letters

A dead delivery has exhausted its attempts or received a non-retryable
response. Correct the receiver or endpoint configuration first. Then use the
administration replay operation with a specific incident reason.

Replay creates a new delivery ID and retains the original CloudEvent ID. Only
the dedicated `system:integration:delivery-replay` permission may perform this
operation. The source delivery must be `DEAD`, the endpoint and subscription
must be active, and the event must still be inside its 30-day retention window.

Replay one delivery first and confirm a `2xx` response. Increase the batch
gradually while watching pending age, success ratio, database connections and
receiver capacity. Never bulk-reset dead rows.

## Backlog

If the oldest pending delivery exceeds five minutes:

1. Check application readiness, database latency and worker executor rejection.
2. Confirm new leases are being created and `lease_until` advances during
   active HTTP requests.
3. Check target latency and timeouts by application. A single slow destination
   should be disabled temporarily if it threatens shared capacity.
4. Raise worker concurrency only when database and receiver headroom are
   measured. The supported range is 1 to 32 threads per Pod.
5. Keep processing throughput above twice the normal event arrival rate until
   the backlog clears.

Disabling Webhook stops new claims without marking pending records successful.
Re-enable it after the dependency is stable; existing records resume normally.

## Failure rate

For a success ratio below 95 percent over 15 minutes, split failures by
application, event type, HTTP status and error code. Expected classifications:

| Result | Action |
| --- | --- |
| `2xx` | Success |
| `408`, `409`, `425`, `429`, `5xx` | Automatic bounded retry |
| Other `4xx` | Dead letter |
| Timeout or disconnect | Automatic bounded retry |
| Redirect | Dead letter; redirects are never followed |
| Destination policy rejection | Dead letter and configuration review |

`Retry-After` is honored only within the 24-hour cap. Do not extend retries
indefinitely because doing so hides dead integrations.

## Lease recovery

Repeated `flow_webhook_lease_recovered_total` increments indicate Pod
termination, database stalls or requests exceeding their lease heartbeat.
Check recent rollouts and Pod termination before increasing the lease duration.

The new worker increments the fencing token when it claims an expired delivery.
An old worker returning later must update zero rows and must not record success.
This is expected behavior and must not be bypassed.

## Worker saturation

`flow_webhook_executor_rejected_total` means the bounded executor queue is full.
Rejected work is released with a short retry delay; it is not lost.

Identify slow destinations and reduce their impact before increasing queue
capacity. A larger queue increases recovery time and retained memory without
increasing downstream throughput.

## Signing-key rotation

Create or rotate the endpoint signing secret through the administration API.
The plaintext secret is returned once and is never stored. Deliver it through
the approved secret channel, update the receiver, and verify both the new and
previous secret during the 48-hour overlap.

Existing delivery rows retain the secret version captured when they were
created. New deliveries use the new version. Do not replace the Webhook AES
master key during this operation.

## Closure

Close the incident only after pending age returns to normal, dead count is
reviewed, the 15-minute success ratio is healthy, receiver deduplication is
confirmed and every manual replay has an audit record.
