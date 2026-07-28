# Production deployment

The production topology separates four trust and lifecycle boundaries:

- `migration`: one-shot Flyway and complete Flowable schema migration with the
  schema database identity.
- `bootstrap`: one-shot, versioned catalog and administrator initialization
  with the runtime database identity.
- `schema-worker`: fenced consumers that are the only long-running workloads
  allowed to use the schema identity.
- `server`: horizontally scalable runtime Pods with DML-only database access.

The web tier is stateless. Uploaded objects must use shared S3-compatible
storage; local filesystem storage is not a supported multi-Pod configuration.

## Required secret keys

Create the Secret named by `global.existingSecret` outside Helm, preferably
through External Secrets, Sealed Secrets, or the platform secret manager:

| Key | Purpose |
| --- | --- |
| `db-username`, `db-password` | DML-only runtime identity |
| `schema-db-username`, `schema-db-password` | Migration and DDL identity |
| `jwt-secret` | JWT signing secret |
| `config-migration-signing-key` | Configuration package signing |
| `bootstrap-admin-password` | Initial administrator activation |
| `s3-access-key`, `s3-secret-key` | Shared object storage |

Do not reuse the runtime and schema database users. Rotate application secrets
through the secret manager and a controlled rolling deployment.

## Release gates

1. Pin server and web images by real `sha256:` digest. Mutable tags are rejected
   unless `global.allowMutableImages=true`.
2. Replace the example database and object-storage NetworkPolicy CIDRs with the
   target environment's stable resolved networks. Empty external CIDR lists are
   rejected; Kubernetes NetworkPolicy does not accept DNS names.
3. Confirm the database backup is restorable and object storage has versioning,
   encryption, retention, and lifecycle policy.
4. Validate the release:

   ```bash
   helm lint deploy/helm/flow --strict -f values.production.yaml
   helm template flow deploy/helm/flow -f values.production.yaml |
     kubectl apply --dry-run=server -f -
   ```

5. Install with rollback on application failure:

   ```bash
   helm upgrade --install flow deploy/helm/flow \
     --namespace flow --create-namespace \
     --values values.production.yaml \
     --rollback-on-failure --wait --timeout 15m
   helm test flow --namespace flow
   ```

6. Verify both runtime replicas, PDBs, HPA, ingress TLS, metrics scraping,
   alert delivery, and a real authenticated upload/download/delete smoke test.

Database migrations are forward-only. Helm rollback reverts workloads, not
schema. Follow [deployment.md](runbooks/deployment.md) for rollback decisions.

## Capacity model

Keep total database connections below the database limit:

```text
server.maxReplicas * server.pool.databaseMax
+ schemaWorker.replicas
+ migration/bootstrap headroom
< database max_connections * 0.8
```

Size thread pools from measured blocking time and downstream capacity. HPA
cannot compensate for database or object-storage saturation.
