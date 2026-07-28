# Backup and restore

## Recovery objectives

Define and test RPO/RTO before production. A database snapshot alone is not a
complete backup: uploaded objects and configuration-package signing keys are
part of the same recovery set.

## Backup

- Use the managed MySQL service's consistent physical snapshot or supported
  point-in-time recovery mechanism.
- Enable binary logs and retain them longer than the declared RPO.
- Enable S3 versioning and lifecycle retention. Replicate to a separate fault
  domain or account where required.
- Back up secret-manager versions and record which key versions correspond to
  the database snapshot.
- Record application image digests and the Helm values revision.

Backups must be encrypted, access-audited, immutable for the retention window,
and restored regularly into an isolated account or namespace.

## Restore rehearsal

1. Prevent external traffic and stop server, schema-worker, bootstrap, and
   migration workloads in the isolated target.
2. Restore MySQL to a new instance and restore the matching object versions.
3. Restore secrets without exposing their values in logs or shell history.
4. Run the migration image against the restored database. Validation must pass.
5. Run bootstrap, start one server, and execute authenticated read/write and
   file integrity checks.
6. Scale to normal replica counts, verify queues, then measure achieved RTO and
   data loss against the declared RPO.

## Production recovery

Freeze writes before choosing a recovery point. Restore into a new database and
bucket when possible, validate there, then switch endpoints. In-place restore
increases blast radius and removes the easiest fallback.
