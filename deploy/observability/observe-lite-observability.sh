#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
duration_seconds=${OBSERVABILITY_OBSERVE_SECONDS:-7200}
interval_seconds=${OBSERVABILITY_OBSERVE_INTERVAL_SECONDS:-60}
prometheus_port=${OBSERVABILITY_PROMETHEUS_PORT:-19090}
result_file=${OBSERVABILITY_OBSERVE_RESULT_FILE:-/tmp/flow-observability-stability.jsonl}
temporary_directory=$(mktemp -d)

trap 'kill ${prometheus_pid:-0} >/dev/null 2>&1 || true; rm -rf "$temporary_directory"' EXIT HUP INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

deployment_available() {
  target_namespace=$1
  deployment=$2
  available=$(kubectl -n "$target_namespace" get deployment "$deployment" -o jsonpath='{.status.availableReplicas}' 2>/dev/null || true)
  desired=$(kubectl -n "$target_namespace" get deployment "$deployment" -o jsonpath='{.status.replicas}' 2>/dev/null || true)
  [ -n "$available" ] || available=0
  [ -n "$desired" ] || desired=0
  [ "$available" = "$desired" ] && [ "$desired" != "0" ]
}

deployment_restarts() {
  target_namespace=$1
  deployment=$2
  selector=$(kubectl -n "$target_namespace" get deployment "$deployment" -o jsonpath='{range $key,$value := .spec.selector.matchLabels}{printf "%s=%s," $key $value}{end}' 2>/dev/null | sed 's/,$//')
  if [ -z "$selector" ]; then
    printf '0'
    return
  fi
  kubectl -n "$target_namespace" get pod -l "$selector" -o jsonpath='{range .items[*]}{range .status.containerStatuses[*]}{.restartCount}{"\n"}{end}{end}' 2>/dev/null \
    | awk '{ total += $1 } END { print total + 0 }'
}

business_health_request() {
  started_at=$(date +%s)
  if kubectl -n "$flow_namespace" exec deployment/"$flow_release"-flow-web -- \
    wget -q -O - "http://$flow_release-flow-server:8080/healthz" >/dev/null 2>&1; then
    ended_at=$(date +%s)
    printf 'true,%s' "$((ended_at - started_at))"
  else
    ended_at=$(date +%s)
    printf 'false,%s' "$((ended_at - started_at))"
  fi
}

prometheus_query_value() {
  query=$1
  output_file="$temporary_directory/prometheus-query.json"
  if ! curl -fsS --get "http://127.0.0.1:$prometheus_port/api/v1/query" \
    --data-urlencode "query=$query" >"$output_file" 2>/dev/null; then
    printf 'null'
    return
  fi
  jq -r 'if .status == "success" and (.data.result | length > 0) then .data.result[0].value[1] else "null" end' "$output_file"
}

metrics_top_json() {
  target_namespace=$1
  if kubectl -n "$target_namespace" top pod >/dev/null 2>&1; then
    kubectl -n "$target_namespace" top pod --no-headers 2>/dev/null \
      | awk 'BEGIN { printf "[" } { if (NR > 1) printf ","; printf "{\"pod\":\"%s\",\"cpu\":\"%s\",\"memory\":\"%s\"}", $1, $2, $3 } END { printf "]" }'
  else
    printf '[]'
  fi
}

record_sample() {
  sampled_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
  health=$(business_health_request)
  health_ok=$(printf '%s' "$health" | cut -d, -f1)
  health_seconds=$(printf '%s' "$health" | cut -d, -f2)

  server_available=false
  web_available=false
  worker_available=false
  prometheus_available=false
  loki_available=false
  tempo_available=false
  otel_available=false
  grafana_available=false

  deployment_available "$flow_namespace" "$flow_release-flow-server" && server_available=true
  deployment_available "$flow_namespace" "$flow_release-flow-web" && web_available=true
  deployment_available "$flow_namespace" "$flow_release-flow-schema-worker" && worker_available=true
  deployment_available "$namespace" flow-prometheus && prometheus_available=true
  deployment_available "$namespace" flow-loki && loki_available=true
  deployment_available "$namespace" flow-tempo && tempo_available=true
  deployment_available "$namespace" flow-otel-collector && otel_available=true
  deployment_available "$namespace" flow-grafana && grafana_available=true

  request_total=$(prometheus_query_value 'sum(increase(http_server_requests_seconds_count{application="workflow-server"}[5m]))')
  request_errors=$(prometheus_query_value 'sum(increase(http_server_requests_seconds_count{application="workflow-server",status=~"5.."}[5m])) or vector(0)')
  request_p50=$(prometheus_query_value 'histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  request_p95=$(prometheus_query_value 'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  request_p99=$(prometheus_query_value 'histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  prometheus_up=$(prometheus_query_value 'up{job="flow-server"}')
  jvm_memory=$(prometheus_query_value 'sum(jvm_memory_used_bytes{application="workflow-server"})')

  server_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-server")
  web_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-web")
  worker_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-schema-worker")
  tempo_restarts=$(deployment_restarts "$namespace" flow-tempo)
  otel_restarts=$(deployment_restarts "$namespace" flow-otel-collector)

  flow_top=$(metrics_top_json "$flow_namespace")
  observability_top=$(metrics_top_json "$namespace")

  printf '{"sampled_at":"%s","business":{"health_ok":%s,"health_seconds":%s,"server_available":%s,"web_available":%s,"schema_worker_available":%s,"server_restarts":%s,"web_restarts":%s,"schema_worker_restarts":%s},"observability":{"prometheus_available":%s,"loki_available":%s,"tempo_available":%s,"otel_collector_available":%s,"grafana_available":%s,"tempo_restarts":%s,"otel_collector_restarts":%s},"prometheus":{"flow_up":%s,"request_total_5m":%s,"request_errors_5m":%s,"request_p50_seconds":%s,"request_p95_seconds":%s,"request_p99_seconds":%s,"jvm_memory_used_bytes":%s},"resources":{"flow_pods":%s,"observability_pods":%s}}\n' \
    "$sampled_at" "$health_ok" "$health_seconds" \
    "$server_available" "$web_available" "$worker_available" \
    "$server_restarts" "$web_restarts" "$worker_restarts" \
    "$prometheus_available" "$loki_available" "$tempo_available" "$otel_available" "$grafana_available" \
    "$tempo_restarts" "$otel_restarts" \
    "$prometheus_up" "$request_total" "$request_errors" "$request_p50" "$request_p95" "$request_p99" "$jvm_memory" \
    "$flow_top" "$observability_top" >>"$result_file"
}

require_command curl
require_command jq
require_command kubectl

: >"$result_file"

kubectl -n "$namespace" wait --for=condition=Available deployment/flow-prometheus --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-server --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-web --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-schema-worker --timeout=120s

kubectl -n "$namespace" port-forward svc/flow-prometheus "$prometheus_port":9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
sleep 5

started_at=$(date +%s)
deadline=$((started_at + duration_seconds))
printf 'observability stability observation started: duration=%ss interval=%ss result=%s\n' \
  "$duration_seconds" "$interval_seconds" "$result_file"

while :; do
  record_sample
  now=$(date +%s)
  [ "$now" -ge "$deadline" ] && break
  remaining=$((deadline - now))
  if [ "$remaining" -lt "$interval_seconds" ]; then
    sleep "$remaining"
  else
    sleep "$interval_seconds"
  fi
done

if jq -s -e '
  def stable($path): (.[0] | getpath($path)) as $first | all(.[]; getpath($path) == $first);
  length > 0
  and all(.[]; .business.health_ok == true)
  and all(.[]; .business.server_available == true)
  and all(.[]; .business.web_available == true)
  and all(.[]; .business.schema_worker_available == true)
  and all(.[]; .observability.prometheus_available == true)
  and all(.[]; (.prometheus.flow_up == 1 or .prometheus.flow_up == "1"))
  and stable(["business", "server_restarts"])
  and stable(["business", "web_restarts"])
  and stable(["business", "schema_worker_restarts"])
  and stable(["observability", "tempo_restarts"])
  and stable(["observability", "otel_collector_restarts"])
' "$result_file" >/dev/null; then
  printf 'observability stability observation passed: %s\n' "$result_file"
else
  printf 'observability stability observation failed: %s\n' "$result_file" >&2
  jq -s '{
    samples: length,
    failed_business_health: map(select(.business.health_ok != true)) | length,
    unavailable_business: map(select(.business.server_available != true or .business.web_available != true or .business.schema_worker_available != true)) | length,
    prometheus_flow_down: map(select(.prometheus.flow_up != 1 and .prometheus.flow_up != "1")) | length,
    restart_growth: {
      server: ([.[].business.server_restarts] | {first: .[0], last: .[-1]}),
      web: ([.[].business.web_restarts] | {first: .[0], last: .[-1]}),
      schema_worker: ([.[].business.schema_worker_restarts] | {first: .[0], last: .[-1]}),
      tempo: ([.[].observability.tempo_restarts] | {first: .[0], last: .[-1]}),
      otel_collector: ([.[].observability.otel_collector_restarts] | {first: .[0], last: .[-1]})
    }
  }' "$result_file" >&2
  exit 1
fi
