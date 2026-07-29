#!/usr/bin/env sh
set -eu

repository_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT HUP INT TERM

server_digest="sha256:1111111111111111111111111111111111111111111111111111111111111111"
web_digest="sha256:2222222222222222222222222222222222222222222222222222222222222222"
production_args="
  --set server.image.digest=$server_digest
  --set web.image.digest=$web_digest
"
open_api_args="
  --set openApi.enabled=true
  --set openApi.keyId=current-2026-07
  --set openApi.previousPublicKeys[0].keyId=previous-2026-06
  --set openApi.previousPublicKeys[0].secretKey=open-api-previous-public-key
  --set openApi.previousPublicKeys[0].fileName=previous-public.pem
  --set openApi.trustForwardedHeaders=true
  --set openApi.trustedProxyCidrs[0]=10.42.0.0/16
"
webhook_args="
  --set openApi.webhook.enabled=true
  --set-string application.httpAllowedHosts=hooks.example.com
  --set networkPolicy.outboundHttpsCIDRs[0]=203.0.113.10/32
"
connector_args="
  --set connector.http.enabled=true
  --set connector.http.masterKeyVersion=current-2026-07
  --set connector.http.masterKeySecretKey=integration-connector-master-key
  --set connector.http.previousMasterKeysSecretKey=integration-connector-previous-master-keys
  --set networkPolicy.outboundHttpsCIDRs[0]=203.0.113.10/32
"

# shellcheck disable=SC2086
helm lint "$repository_root/deploy/helm/flow" --strict $production_args
helm lint "$repository_root/deploy/helm/flow" \
  --strict \
  --values "$repository_root/deploy/k3s/values.yaml"

# shellcheck disable=SC2086
helm template flow-production "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  >"$temporary_directory/production.yaml"

helm template flow-local "$repository_root/deploy/helm/flow" \
  --namespace flow-hardening \
  --values "$repository_root/deploy/k3s/values.yaml" \
  >"$temporary_directory/local.yaml"

# shellcheck disable=SC2086
helm template flow-open-api "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  >"$temporary_directory/open-api.yaml"

# shellcheck disable=SC2086
helm template flow-webhook "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  $webhook_args \
  >"$temporary_directory/webhook.yaml"

# shellcheck disable=SC2086
helm template flow-connector "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $connector_args \
  >"$temporary_directory/connector.yaml"

if helm template flow-connector "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  --set connector.http.enabled=true \
  --set connector.http.masterKeyVersion=current-2026-07 \
  >"$temporary_directory/invalid-connector-egress.yaml" 2>/dev/null; then
  printf 'HTTP Connector must reject an empty outbound HTTPS CIDR list\n' >&2
  exit 1
fi

if helm template flow-connector "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  --set connector.http.enabled=true \
  --set networkPolicy.outboundHttpsCIDRs[0]=203.0.113.10/32 \
  >"$temporary_directory/invalid-connector-key-version.yaml" 2>/dev/null; then
  printf 'HTTP Connector must require a master key version\n' >&2
  exit 1
fi

if helm template flow-webhook "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set openApi.webhook.enabled=true \
  --set networkPolicy.outboundHttpsCIDRs[0]=203.0.113.10/32 \
  >"$temporary_directory/invalid-webhook-hosts.yaml" 2>/dev/null; then
  printf 'Webhook must reject an empty destination host allowlist\n' >&2
  exit 1
fi

if helm template flow-open-api "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set-string openApi.publicKeySecretKey=open-api-private-key \
  >"$temporary_directory/invalid-same-key.yaml" 2>/dev/null; then
  printf 'openApi must reject identical private/public Secret keys\n' >&2
  exit 1
fi

if helm template flow-open-api "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set-string openApi.previousPublicKeys[0].secretKey=open-api-public-key \
  >"$temporary_directory/invalid-reused-key.yaml" 2>/dev/null; then
  printf 'openApi must reject reused historical Secret keys\n' >&2
  exit 1
fi

if helm template flow-open-api "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set-string openApi.previousPublicKeys[0].fileName=..data \
  >"$temporary_directory/invalid-file-name.yaml" 2>/dev/null; then
  printf 'openApi must reject reserved projected file names\n' >&2
  exit 1
fi

# shellcheck disable=SC2086
helm template flow-monitoring "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  --set monitoring.serviceMonitor.enabled=true \
  --set monitoring.prometheusRule.enabled=true \
  $production_args \
  >"$temporary_directory/monitoring.yaml"

network_policy_count=$(awk '
  /^kind: NetworkPolicy$/ { count++ }
  END { print count + 0 }
' "$temporary_directory/production.yaml")
[ "$network_policy_count" -eq 7 ]

monitoring_kind_count=$(awk '
  /^kind: (ServiceMonitor|PrometheusRule)$/ { count++ }
  END { print count + 0 }
' "$temporary_directory/monitoring.yaml")
[ "$monitoring_kind_count" -eq 2 ]

kubeconform_image="ghcr.io/yannh/kubeconform@sha256:85dbef6b4b312b99133decc9c6fc9495e9fc5f92293d4ff3b7e1b30f5611823c"
docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary \
  <"$temporary_directory/production.yaml"

docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary \
  <"$temporary_directory/local.yaml"

docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary \
  <"$temporary_directory/open-api.yaml"

docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary \
  <"$temporary_directory/webhook.yaml"

docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary \
  <"$temporary_directory/connector.yaml"

docker run --rm --interactive "$kubeconform_image" \
  -kubernetes-version 1.32.0 \
  -strict \
  -ignore-missing-schemas \
  -summary \
  <"$temporary_directory/monitoring.yaml"

CONFIG_MIGRATION_SIGNING_KEY=test-signing-key \
DB_PASSWORD=test-db-password \
DB_ROOT_PASSWORD=test-root-password \
FILE_STORAGE_S3_ACCESS_KEY=test-access \
FILE_STORAGE_S3_BUCKET=test-bucket \
FILE_STORAGE_S3_ENDPOINT=https://s3.example.test \
FILE_STORAGE_S3_SECRET_KEY=test-secret \
JWT_SECRET=test-jwt-secret \
SCHEMA_DB_PASSWORD=test-schema-password \
SCHEMA_DB_USERNAME=test-schema \
SERVER_IMAGE=example/server@"$server_digest" \
WEB_IMAGE=example/web@"$web_digest" \
WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD=TestBootstrap1234 \
  docker compose \
    --file "$repository_root/deploy/compose.prod.yml" \
    config >"$temporary_directory/compose.yaml"

printf 'production and local deployment manifests are valid\n'
