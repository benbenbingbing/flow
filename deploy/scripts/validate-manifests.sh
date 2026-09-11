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
embed_args="
  --set ingress.enabled=true
  --set-string ingress.host=admin.flow.example.com
  --set embed.enabled=true
  --set-string embed.publicHost=embed.flow.example.com
  --set-string embed.tlsSecretName=flow-embed-tls
"

# shellcheck disable=SC2086
helm lint "$repository_root/deploy/helm/flow" --strict $production_args
helm lint "$repository_root/deploy/helm/flow" \
  --strict \
  --values "$repository_root/deploy/k3s/values.yaml"
# shellcheck disable=SC2086
helm lint "$repository_root/deploy/helm/flow" \
  --strict \
  $production_args \
  $open_api_args \
  $embed_args

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
helm template flow-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  $embed_args \
  >"$temporary_directory/embed.yaml"

if helm template flow-invalid-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set ingress.enabled=true \
  --set-string ingress.host=admin.flow.example.com \
  --set embed.enabled=true \
  --set-string embed.publicHost=admin.flow.example.com \
  --set-string embed.tlsSecretName=flow-embed-tls \
  >"$temporary_directory/invalid-embed-same-host.yaml" 2>/dev/null; then
  printf 'Embed Runtime must reject an admin/embed same-host deployment\n' >&2
  exit 1
fi

if helm template flow-invalid-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set embed.enabled=true \
  --set-string embed.publicHost=embed.flow.example.com \
  >"$temporary_directory/invalid-embed-missing-tls.yaml" 2>/dev/null; then
  printf 'Embed Runtime must require an HTTPS TLS Secret\n' >&2
  exit 1
fi

if helm template flow-invalid-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  $embed_args \
  --set-string embed.hmacKeySecretKey=embed-context-key-base64 \
  >"$temporary_directory/invalid-embed-reused-crypto-key.yaml" 2>/dev/null; then
  printf 'Embed Runtime must use distinct context encryption and HMAC Secret entries\n' >&2
  exit 1
fi

if helm template flow-invalid-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  $open_api_args \
  --set embed.enabled=true \
  --set-string embed.publicHost=https://embed.flow.example.com \
  --set-string embed.tlsSecretName=flow-embed-tls \
  >"$temporary_directory/invalid-embed-http-boundary.yaml" 2>/dev/null; then
  printf 'Embed Runtime publicHost must be an HTTPS-only host, not a URL or HTTP endpoint\n' >&2
  exit 1
fi

if helm template flow-invalid-embed "$repository_root/deploy/helm/flow" \
  --namespace flow-production \
  $production_args \
  --set embed.enabled=true \
  --set-string embed.publicHost=embed.flow.example.com \
  --set-string embed.tlsSecretName=flow-embed-tls \
  >"$temporary_directory/invalid-embed-open-api-disabled.yaml" 2>/dev/null; then
  printf 'Embed Runtime must require the Open API launch boundary\n' >&2
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

extract_manifest_document() {
  manifest_file="$1"
  resource_name="$2"
  resource_kind="$3"
  awk -v target="$resource_name" -v target_kind="$resource_kind" '
    function emit_if_matched() {
      if (name_matched && kind_matched) {
        printf "%s", document
        emitted = 1
        exit
      }
    }
    /^---$/ {
      emit_if_matched()
      document = ""
      name_matched = 0
      kind_matched = 0
      next
    }
    {
      document = document $0 ORS
      if ($0 == "  name: " target) {
        name_matched = 1
      }
      if ($0 == "kind: " target_kind) {
        kind_matched = 1
      }
    }
    END {
      if (name_matched && kind_matched && !emitted) {
        printf "%s", document
      }
    }
  ' "$manifest_file"
}

assert_exact_line() {
  manifest_file="$1"
  expected_line="$2"
  if ! grep -F -x "$expected_line" "$manifest_file" >/dev/null; then
    printf 'expected line not found in %s: %s\n' "$manifest_file" "$expected_line" >&2
    exit 1
  fi
}

assert_env_value() {
  manifest_file="$1"
  variable_name="$2"
  expected_value="$3"
  if ! awk -v variable_name="$variable_name" -v expected_value="$expected_value" '
    $1 == "-" && $2 == "name:" && $3 == variable_name {
      if (getline <= 0 || $1 != "value:") {
        exit 1
      }
      value = $2
      gsub(/^"|"$/, "", value)
      if (value == expected_value) {
        found = 1
      }
    }
    END { exit(found ? 0 : 1) }
  ' "$manifest_file"; then
    printf 'expected environment value not found in %s: %s=%s\n' \
      "$manifest_file" "$variable_name" "$expected_value" >&2
    exit 1
  fi
}

extract_manifest_document \
  "$temporary_directory/embed.yaml" \
  flow-embed-flow-embed \
  Ingress \
  >"$temporary_directory/embed-ingress.yaml"
extract_manifest_document \
  "$temporary_directory/embed.yaml" \
  flow-embed-flow-web \
  Service \
  >"$temporary_directory/embed-web-resource.yaml"
extract_manifest_document \
  "$temporary_directory/embed.yaml" \
  flow-embed-flow-web \
  Deployment \
  >"$temporary_directory/embed-web-deployment.yaml"
extract_manifest_document \
  "$temporary_directory/embed.yaml" \
  flow-embed-flow-server \
  Deployment \
  >"$temporary_directory/embed-server-deployment.yaml"
extract_manifest_document \
  "$temporary_directory/production.yaml" \
  flow-production-flow-web \
  Service \
  >"$temporary_directory/default-web-resource.yaml"
extract_manifest_document \
  "$temporary_directory/production.yaml" \
  flow-production-flow-server \
  Deployment \
  >"$temporary_directory/default-server-deployment.yaml"

embed_ingress_path_count=$(awk '$1 == "-" && $2 == "path:" { count++ } END { print count + 0 }' \
  "$temporary_directory/embed-ingress.yaml")
[ "$embed_ingress_path_count" -eq 3 ]
assert_exact_line "$temporary_directory/embed-ingress.yaml" '    - host: "embed.flow.example.com"'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '      secretName: flow-embed-tls'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '    nginx.ingress.kubernetes.io/ssl-redirect: "true"'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '          - path: /embed/v1/launches/'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '          - path: /embed-assets/'
assert_exact_line "$temporary_directory/embed-ingress.yaml" '          - path: /api/embed/v1/'
embed_ingress_backend_count=$(awk '$1 == "name:" && $2 == "embed" { count++ } END { print count + 0 }' \
  "$temporary_directory/embed-ingress.yaml")
[ "$embed_ingress_backend_count" -eq 3 ]

# 独立端口必须从 Ingress Service port 一直连接到 Nginx containerPort 8081。
assert_exact_line "$temporary_directory/embed-web-resource.yaml" '    - name: embed'
assert_exact_line "$temporary_directory/embed-web-resource.yaml" '      port: 8081'
assert_exact_line "$temporary_directory/embed-web-resource.yaml" '      targetPort: embed'
assert_exact_line "$temporary_directory/embed-web-deployment.yaml" '              containerPort: 8081'
assert_env_value "$temporary_directory/embed-server-deployment.yaml" \
  WORKFLOW_EMBED_ENABLED true
assert_env_value "$temporary_directory/embed-server-deployment.yaml" \
  WORKFLOW_EMBED_PUBLIC_BASE_URL https://embed.flow.example.com
assert_exact_line "$temporary_directory/embed-server-deployment.yaml" \
  '            - name: WORKFLOW_EMBED_CONTEXT_KEY_BASE64'
assert_exact_line "$temporary_directory/embed-server-deployment.yaml" \
  '                  key: embed-context-key-base64'
assert_exact_line "$temporary_directory/embed-server-deployment.yaml" \
  '            - name: WORKFLOW_EMBED_HMAC_KEY_BASE64'
assert_exact_line "$temporary_directory/embed-server-deployment.yaml" \
  '                  key: embed-hmac-key-base64'
assert_env_value "$temporary_directory/default-server-deployment.yaml" \
  WORKFLOW_EMBED_ENABLED false
assert_env_value "$temporary_directory/default-server-deployment.yaml" \
  WORKFLOW_EMBED_PUBLIC_BASE_URL https://embed.invalid
if grep -F 'targetPort: embed' "$temporary_directory/production.yaml" >/dev/null; then
  printf 'Embed Service port must stay unpublished while embed.enabled=false\n' >&2
  exit 1
fi

embed_vhost_file="$temporary_directory/embed-nginx-vhost.conf"
awk '/^# Embed Runtime 使用独立端口/ { capture = 1 } capture { print }' \
  "$repository_root/workflow-web/nginx.conf" >"$embed_vhost_file"
assert_exact_line "$embed_vhost_file" '    listen 8081;'
assert_exact_line "$embed_vhost_file" '    if ($flow_forwarded_proto != "https") {'
assert_exact_line "$embed_vhost_file" '    location ^~ /embed-assets/ {'
assert_exact_line "$embed_vhost_file" '    location ^~ /api/embed/v1/ {'
assert_exact_line "$embed_vhost_file" '    location / {'
if grep -F 'location /api/ {' "$embed_vhost_file" >/dev/null \
  || grep -F 'try_files $uri $uri/ /index.html;' "$embed_vhost_file" >/dev/null; then
  printf 'Embed VHost must not expose ordinary API routes or an admin SPA fallback\n' >&2
  exit 1
fi
assert_exact_line "$embed_vhost_file" \
  '    location ~ "^/embed/v1/launches/lch_[A-Za-z0-9_-]{16,60}$" {'
if ! grep -F "frame-ancestors 'none'" "$repository_root/workflow-web/nginx.conf" >/dev/null; then
  printf 'Admin VHost must retain frame-ancestors none\n' >&2
  exit 1
fi

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

run_kubeconform_file "$temporary_directory/embed.yaml" \
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

printf 'production, Embed, and local deployment manifests are valid\n'
