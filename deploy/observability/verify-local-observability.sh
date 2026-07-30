#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/versions.env"

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT HUP INT TERM

kubectl -n "$namespace" wait --for=condition=Available deployment/flow-otel-collector --timeout=180s
kubectl -n "$namespace" wait --for=condition=Available deployment/flow-monitoring-grafana --timeout=180s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-server --timeout=180s

kubectl -n "$namespace" port-forward svc/flow-monitoring-kube-prometheus-prometheus 19090:9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
kubectl -n "$namespace" port-forward svc/flow-loki-gateway 13100:80 >"$temporary_directory/loki.log" 2>&1 &
loki_pid=$!
kubectl -n "$namespace" port-forward svc/flow-tempo 13200:3200 >"$temporary_directory/tempo.log" 2>&1 &
tempo_pid=$!
trap 'kill $prometheus_pid $loki_pid $tempo_pid >/dev/null 2>&1 || true; rm -rf "$temporary_directory"' EXIT HUP INT TERM

sleep 5

prometheus_query() {
  curl -fsS --get "http://127.0.0.1:19090/api/v1/query" --data-urlencode "query=$1"
}

prometheus_query 'up{namespace="flow-hardening"}' | tee "$temporary_directory/prometheus-up.json" >/dev/null
prometheus_query 'sum(rate(http_server_requests_seconds_count{application="workflow-server"}[5m]))' | tee "$temporary_directory/prometheus-http.json" >/dev/null
curl -fsS --get "http://127.0.0.1:13100/loki/api/v1/query" --data-urlencode 'query={namespace="flow-hardening"}' >"$temporary_directory/loki-flow.json"
curl -fsS "http://127.0.0.1:13200/ready" >"$temporary_directory/tempo-ready.txt"

printf 'observability verification queries completed\n'
printf 'Prometheus up result: %s\n' "$temporary_directory/prometheus-up.json"
printf 'Loki result: %s\n' "$temporary_directory/loki-flow.json"
