# Deployment and rollback

## Preflight

- Confirm the target context, namespace, release name, and image digests.
- Confirm at least two schedulable failure domains for production replicas.
- Verify the runtime database user cannot run `CREATE`, `ALTER`, or `DROP`.
- Verify only migration and schema-worker egress can reach the schema endpoint.
- Verify the S3 bucket exists and a write/read/delete probe succeeds.
- Take a database backup and record its immutable identifier.
- Check current queue depth and oldest-ready age before changing workloads.

## Upgrade

The Helm pre-upgrade sequence is migration first, then bootstrap. Existing
runtime Pods continue serving until both hooks succeed. The Deployment uses
`maxUnavailable: 0`, readiness probes, graceful Spring shutdown, and a PDB.

Watch the release:

```bash
kubectl -n flow get jobs,pods --watch
kubectl -n flow rollout status deployment/flow-flow-server --timeout=10m
helm -n flow test flow
```

Abort if migrations fail. Do not bypass Flyway validation or edit an applied
migration. Add a new corrective migration.

## Rollback decision

Application-only changes may be rolled back with `helm rollback` if the old
binary is compatible with the current schema. Schema changes are forward-only:

- additive schema: roll the application back and leave schema in place;
- destructive or semantic schema change: stop and use a tested forward fix;
- corrupted data: stop writers and execute the restore runbook.

Never run Flyway `clean`, manually delete schema-history rows, or restore only
some business tables while workers are running.

## Secret rotation

Rotate one boundary at a time. For database credentials, create the new
identity/grants first, update the external Secret, roll workloads, verify old
connections drain, then revoke the old identity. JWT rotation currently
invalidates active tokens and must be communicated as a user sign-in event.
