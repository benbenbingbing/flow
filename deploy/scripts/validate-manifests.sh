#!/usr/bin/env sh
set -eu

repository_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT HUP INT TERM

# Render third-party charts from an isolated repository configuration so the
# result does not depend on repositories previously configured on the runner.
export HELM_REPOSITORY_CONFIG="$temporary_directory/helm-repositories.yaml"
export HELM_REPOSITORY_CACHE="$temporary_directory/helm-repository-cache"
mkdir -p "$HELM_REPOSITORY_CACHE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo add apache-skywalking https://apache.jfrog.io/artifactory/skywalking-helm

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

helm template flow-observability-prometheus prometheus-community/kube-prometheus-stack \
  --namespace flow-observability \
  --version 87.21.0 \
  --values "$repository_root/deploy/observability/kube-prometheus-stack-values.yaml" \
  >"$temporary_directory/observability-prometheus.yaml"

helm template flow-observability-loki grafana/loki \
  --namespace flow-observability \
  --version 7.2.0 \
  --values "$repository_root/deploy/observability/loki-values.yaml" \
  >"$temporary_directory/observability-loki.yaml"

helm template flow-observability-promtail grafana/promtail \
  --namespace flow-observability \
  --version 6.17.1 \
  --values "$repository_root/deploy/observability/promtail-values.yaml" \
  >"$temporary_directory/observability-promtail.yaml"

helm template flow-observability-tempo grafana/tempo \
  --namespace flow-observability \
  --version 1.24.4 \
  --values "$repository_root/deploy/observability/tempo-values.yaml" \
  >"$temporary_directory/observability-tempo.yaml"

helm template flow-observability-otel open-telemetry/opentelemetry-collector \
  --namespace flow-observability \
  --version 0.165.0 \
  --values "$repository_root/deploy/observability/otel-collector-values.yaml" \
  >"$temporary_directory/observability-otel.yaml"

helm template flow-observability-skywalking apache-skywalking/skywalking \
  --namespace flow-observability \
  --version 4.3.0 \
  --values "$repository_root/deploy/observability/skywalking-values.yaml" \
  >"$temporary_directory/observability-skywalking.yaml"

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

container_proxy_value() {
  proxy_value="$1"
  case "$proxy_value" in
    http://127.0.0.1:*|http://localhost:*)
      printf 'http://host.docker.internal:%s' "${proxy_value##*:}"
      ;;
    https://127.0.0.1:*|https://localhost:*)
      printf 'https://host.docker.internal:%s' "${proxy_value##*:}"
      ;;
    *)
      printf '%s' "$proxy_value"
      ;;
  esac
}

source_http_proxy="${HTTP_PROXY:-${http_proxy:-}}"
source_https_proxy="${HTTPS_PROXY:-${https_proxy:-}}"
source_no_proxy="${NO_PROXY:-${no_proxy:-}}"

container_http_proxy="${VALIDATE_MANIFESTS_DOCKER_HTTP_PROXY:-$(container_proxy_value "$source_http_proxy")}"
container_https_proxy="${VALIDATE_MANIFESTS_DOCKER_HTTPS_PROXY:-$(container_proxy_value "$source_https_proxy")}"
container_no_proxy="${VALIDATE_MANIFESTS_DOCKER_NO_PROXY:-$source_no_proxy}"
kubeconform_cache_directory="${KUBECONFORM_SCHEMA_CACHE:-${TMPDIR:-/tmp}/flow-kubeconform-schema-cache}"
kubeconform_concurrency="${KUBECONFORM_CONCURRENCY:-1}"
kubeconform_retries="${KUBECONFORM_RETRIES:-3}"
kubeconform_binary="${KUBECONFORM_BIN:-}"
if [ -z "$kubeconform_binary" ] && command -v kubeconform >/dev/null 2>&1; then
  kubeconform_binary=$(command -v kubeconform)
fi

mkdir -p "$kubeconform_cache_directory"

run_kubeconform_once() {
  if [ -n "$kubeconform_binary" ]; then
    "$kubeconform_binary" \
      -cache "$kubeconform_cache_directory" \
      -n "$kubeconform_concurrency" \
      "$@"
  else
    docker run --rm --interactive \
      --env "HTTP_PROXY=$container_http_proxy" \
      --env "HTTPS_PROXY=$container_https_proxy" \
      --env "NO_PROXY=$container_no_proxy" \
      --env "http_proxy=$container_http_proxy" \
      --env "https_proxy=$container_https_proxy" \
      --env "no_proxy=$container_no_proxy" \
      --volume "$kubeconform_cache_directory:/schema-cache" \
      "$kubeconform_image" \
      -cache /schema-cache \
      -n "$kubeconform_concurrency" \
      "$@"
  fi
}

run_kubeconform_file() {
  manifest_file="$1"
  shift
  attempt=1
  while :; do
    if run_kubeconform_once "$@" <"$manifest_file"; then
      return 0
    fi
    if [ "$attempt" -ge "$kubeconform_retries" ]; then
      return 1
    fi
    attempt=$((attempt + 1))
    printf 'kubeconform validation failed for %s, retrying attempt %s/%s\n' \
      "$manifest_file" "$attempt" "$kubeconform_retries" >&2
  done
}

run_kubeconform_file "$temporary_directory/production.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary

run_kubeconform_file "$temporary_directory/local.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -ignore-missing-schemas \
  -summary

run_kubeconform_file "$temporary_directory/open-api.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary

run_kubeconform_file "$temporary_directory/webhook.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary

run_kubeconform_file "$temporary_directory/connector.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -summary

run_kubeconform_file "$temporary_directory/monitoring.yaml" \
  -kubernetes-version 1.32.0 \
  -strict \
  -ignore-missing-schemas \
  -summary

for manifest in \
  observability-prometheus.yaml \
  observability-loki.yaml \
  observability-promtail.yaml \
  observability-tempo.yaml \
  observability-otel.yaml \
  observability-skywalking.yaml
do
  run_kubeconform_file "$temporary_directory/$manifest" \
    -kubernetes-version 1.32.0 \
    -strict \
    -ignore-missing-schemas \
    -summary
done

for lite_manifest in "$repository_root"/deploy/observability/lite/*.yaml
do
  run_kubeconform_file "$lite_manifest" \
    -kubernetes-version 1.32.0 \
    -strict \
    -ignore-missing-schemas \
    -summary
done

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
