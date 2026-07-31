#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
temporary_directory=$(mktemp -d)
trap 'kill ${prometheus_pid:-0} ${loki_pid:-0} ${tempo_pid:-0} >/dev/null 2>&1 || true; rm -rf "$temporary_directory"' EXIT HUP INT TERM

for command_name in curl jq kubectl
do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$command_name" >&2
    exit 1
  fi
done

kubectl -n "$namespace" wait --for=condition=Available deployment/flow-prometheus --timeout=120s
kubectl -n "$namespace" wait --for=condition=Available deployment/flow-loki --timeout=120s
kubectl -n "$namespace" wait --for=condition=Available deployment/flow-tempo --timeout=120s
kubectl -n "$namespace" wait --for=condition=Available deployment/flow-otel-collector --timeout=120s
kubectl -n "$namespace" wait --for=condition=Available deployment/flow-grafana --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-server --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-web --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-schema-worker --timeout=120s

kubectl -n "$namespace" port-forward svc/flow-prometheus 19090:9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
kubectl -n "$namespace" port-forward svc/flow-loki 13100:3100 >"$temporary_directory/loki.log" 2>&1 &
loki_pid=$!
kubectl -n "$namespace" port-forward svc/flow-tempo 13200:3200 >"$temporary_directory/tempo.log" 2>&1 &
tempo_pid=$!
sleep 5

prometheus_query() {
  curl -fsS --get "http://127.0.0.1:19090/api/v1/query" --data-urlencode "query=$1"
}

assert_prometheus_vector_present() {
  query=$1
  output_file=$2
  prometheus_query "$query" >"$output_file"
  if ! jq -e '.status == "success" and (.data.result | length > 0)' "$output_file" >/dev/null; then
    printf 'prometheus query returned no data: %s\n' "$query" >&2
    jq . "$output_file" >&2
    exit 1
  fi
}

assert_prometheus_vector_present 'up{job="flow-server"} == 1' "$temporary_directory/prometheus-flow-up.json"
assert_prometheus_vector_present 'sum(jvm_memory_used_bytes{application="workflow-server"}) > 0' "$temporary_directory/prometheus-jvm.json"
assert_prometheus_vector_present 'ALERTS{alertname="FlowControlledTestAlert",alertstate="firing"}' "$temporary_directory/prometheus-alert.json"
curl -fsS --get "http://127.0.0.1:13100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="flow-hardening"}' \
  --data-urlencode 'limit=10' \
  >"$temporary_directory/loki-flow.json"
if ! jq -e '.status == "success" and (.data.result | length > 0)' "$temporary_directory/loki-flow.json" >/dev/null; then
  printf 'loki query returned no flow logs\n' >&2
  jq . "$temporary_directory/loki-flow.json" >&2
  exit 1
fi
curl -fsS "http://127.0.0.1:13200/ready" >"$temporary_directory/tempo-ready.txt"
grep -q 'ready' "$temporary_directory/tempo-ready.txt"
curl -fsS "http://127.0.0.1:13200/api/search?limit=20" >"$temporary_directory/tempo-search.json"
if ! jq -e '(.traces // [] | length) > 0' "$temporary_directory/tempo-search.json" >/dev/null; then
  printf 'tempo search returned no traces\n' >&2
  jq . "$temporary_directory/tempo-search.json" >&2
  exit 1
fi

printf 'lite observability verification completed\n'
printf 'Prometheus flow target: %s\n' "$temporary_directory/prometheus-flow-up.json"
printf 'Prometheus JVM query: %s\n' "$temporary_directory/prometheus-jvm.json"
printf 'Controlled alert query: %s\n' "$temporary_directory/prometheus-alert.json"
printf 'Loki query: %s\n' "$temporary_directory/loki-flow.json"
printf 'Tempo search query: %s\n' "$temporary_directory/tempo-search.json"
