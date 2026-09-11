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
| `open-api-private-key`, `open-api-public-key` | Current Open Integration RSA signing pair when `openApi.enabled=true` |

Do not reuse the runtime and schema database users. Rotate application secrets
through the secret manager and a controlled rolling deployment.

The Open Integration keys must be PKCS#8 private and X.509 public PEM files
from the same RSA key pair, with a minimum size of 2048 bits. Configure a
stable, versioned `openApi.keyId`. Historical verification public keys are
optional and are projected from the same external Secret:

```yaml
openApi:
  enabled: true
  issuer: https://flow.example.com
  keyId: signing-2026-07
  privateKeySecretKey: open-api-private-key
  publicKeySecretKey: open-api-public-key
  previousPublicKeys:
    - keyId: signing-2026-06
      secretKey: open-api-public-key-2026-06
      fileName: signing-2026-06.pem
```

Never place PEM data in a Helm values file. Keep at most three historical
public keys and remove each one after the maximum access-token lifetime plus
the rollout safety margin.

Open Integration ignores forwarded client-address headers by default. When it
runs behind a trusted ingress, set `openApi.trustForwardedHeaders=true` and
list only the ingress or load-balancer networks in
`openApi.trustedProxyCidrs`. The service walks `X-Forwarded-For` from the
nearest hop toward the client and ignores the header entirely when the direct
peer is not trusted.

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
