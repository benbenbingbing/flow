# Deployment and rollback

## Preflight

- Confirm the target context, namespace, release name, and image digests.
- Keep the environment-specific settings in
  `/opt/flow/values.production.yaml`; CI never uploads or rewrites secrets.
- For private GHCR packages, configure `global.imagePullSecrets` in that values
  file and create the referenced registry credential in the target namespace.
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
The production workflow publishes both images to GHCR, pins their returned
digests in Helm, and uses `--atomic --wait`; it does not run Compose.

Watch the release:

```bash
kubectl -n flow get jobs,pods --watch
kubectl -n flow rollout status deployment/flow-flow-server --timeout=10m
helm -n flow test flow
```

Abort if migrations fail. Do not bypass Flyway validation or edit an applied
migration. Add a new corrective migration.

### Expand/contract schema changes

Because migration hooks finish before the Deployment rolls, changing the
canonical read path and retiring compatibility writes defaults to four
application releases when affected writes remain available:

1. expand the schema and deploy code that reads the old canonical state while
   bridging both old and new writes;
2. after a final backfill and reconciliation, switch reads to the new canonical
   state but keep compatibility writes and the old schema; legacy public APIs
   may be removed when their clients have already exited;
3. switch to new-only reads and writes while retaining the old schema, finish
   the rollout, and wait for every step-2 Pod and in-flight transaction to exit;
4. in a later release, run the contract migration that removes the old schema
   and retired permissions.

Do not stop compatibility writes in step 2 during an ordinary rolling deploy,
and do not combine steps 3 and 4: a pre-upgrade migration runs while Pods from
the previous application release can still be serving. A three-release variant
may combine steps 2 and 3 only if affected writes are frozen for the entire
step-2 rollout, in-flight write transactions are drained before cutover, the
final backfill and reconciliation succeed, and the freeze remains until every
old Pod has exited.

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

Open Integration signing keys use a two-phase rollout so Pods never disagree
about a valid token:

1. Add the future public key to `openApi.previousPublicKeys` under its future
   `keyId`, then roll every server Pod. Keep the current signing pair unchanged.
2. Verify all ready Pods accept a test token carrying the future `kid`.
3. Replace the current private/public pair and `openApi.keyId`. Move the former
   current public key into `previousPublicKeys`, then roll every server Pod.
4. Verify tokens signed before and after the switch across every ready Pod.
5. Starting when the final Pod switches to the new signing key, retain the old
   public key for at least 35 minutes: 30 minutes for the maximum token TTL,
   one minute for verifier clock skew, and four minutes of operational margin.
   Then remove the retired key and roll the server Pods once more.

Abort the switch if any Pod has a different mounted Secret resource version or
cannot validate both key IDs. A private key is never configured as a historical
verification key.
