#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
prometheus_port=${OBSERVABILITY_VERIFY_PROMETHEUS_PORT:-19090}
loki_port=${OBSERVABILITY_VERIFY_LOKI_PORT:-13100}
tempo_port=${OBSERVABILITY_VERIFY_TEMPO_PORT:-13200}
skywalking_query_port=${OBSERVABILITY_VERIFY_SKYWALKING_QUERY_PORT:-19412}
skywalking_ui_port=${OBSERVABILITY_VERIFY_SKYWALKING_UI_PORT:-18083}
temporary_directory=$(mktemp -d)

cleanup() {
  trap - EXIT HUP INT TERM
  kill ${prometheus_pid:-} ${loki_pid:-} ${tempo_pid:-} \
    ${skywalking_query_pid:-} ${skywalking_ui_pid:-} \
    >/dev/null 2>&1 || true
  rm -rf "$temporary_directory"
}
trap cleanup EXIT HUP INT TERM

for command_name in curl jq kubectl
do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$command_name" >&2
    exit 1
  fi
done

wait_for_http() {
  url=$1
  attempt=1
  while [ "$attempt" -le 30 ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  printf 'timed out waiting for endpoint: %s\n' "$url" >&2
  exit 1
}

prometheus_query() {
  curl -fsS --get "http://127.0.0.1:$prometheus_port/api/v1/query" \
    --data-urlencode "query=$1"
}

assert_prometheus_vector() {
  query=$1
  output_file=$2
  prometheus_query "$query" >"$output_file"
  if ! jq -e '.status == "success" and (.data.result | length > 0)' \
    "$output_file" >/dev/null; then
    printf 'prometheus query returned no data: %s\n' "$query" >&2
    jq . "$output_file" >&2
    exit 1
  fi
}

kubectl -n "$namespace" rollout status \
  statefulset/prometheus-flow-monitoring-prometheus --timeout=180s
kubectl -n "$namespace" rollout status statefulset/flow-loki --timeout=180s
kubectl -n "$namespace" rollout status statefulset/flow-tempo --timeout=180s
kubectl -n "$namespace" wait --for=condition=Available \
  deployment/flow-otel-collector --timeout=180s
kubectl -n "$namespace" wait --for=condition=Available \
  deployment/flow-monitoring-grafana --timeout=180s
kubectl -n "$flow_namespace" wait --for=condition=Available \
  deployment/"$flow_release"-flow-server --timeout=180s

kubectl -n "$namespace" port-forward svc/flow-monitoring-prometheus \
  "$prometheus_port":9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
kubectl -n "$namespace" port-forward svc/flow-loki-gateway \
  "$loki_port":80 >"$temporary_directory/loki.log" 2>&1 &
loki_pid=$!
kubectl -n "$namespace" port-forward svc/flow-tempo \
  "$tempo_port":3200 >"$temporary_directory/tempo.log" 2>&1 &
tempo_pid=$!

skywalking_enabled=false
skywalking_services=0
skywalking_traces=0
if kubectl -n "$namespace" get deployment \
    flow-skywalking-skywalking-helm-oap >/dev/null 2>&1; then
  skywalking_enabled=true
  kubectl -n "$namespace" rollout status \
    deployment/flow-skywalking-skywalking-helm-oap --timeout=180s
  kubectl -n "$namespace" rollout status \
    deployment/flow-skywalking-skywalking-helm-ui --timeout=180s
  kubectl -n "$namespace" port-forward \
    svc/flow-skywalking-skywalking-helm-oap \
    "$skywalking_query_port":9412 \
    >"$temporary_directory/skywalking-query.log" 2>&1 &
  skywalking_query_pid=$!
  kubectl -n "$namespace" port-forward \
    svc/flow-skywalking-skywalking-helm-ui \
    "$skywalking_ui_port":80 \
    >"$temporary_directory/skywalking-ui.log" 2>&1 &
  skywalking_ui_pid=$!
fi

wait_for_http "http://127.0.0.1:$prometheus_port/-/ready"
wait_for_http "http://127.0.0.1:$loki_port/loki/api/v1/status/buildinfo"
wait_for_http "http://127.0.0.1:$tempo_port/ready"

assert_prometheus_vector \
  'min(up{namespace="flow-hardening",job="flow-local-flow-server"}) == 1' \
  "$temporary_directory/prometheus-flow-up.json"
assert_prometheus_vector \
  'sum(increase(http_server_requests_seconds_count{application="workflow-server"}[5m])) > 0' \
  "$temporary_directory/prometheus-http.json"
assert_prometheus_vector \
  'sum(otelcol_exporter_sent_spans{job="flow-otel-collector",exporter="otlphttp/tempo"}) > 0' \
  "$temporary_directory/prometheus-tempo-export.json"

curl -fsS --get "http://127.0.0.1:$loki_port/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="flow-hardening",container="server"}' \
  --data-urlencode 'limit=200' \
  --data-urlencode 'direction=backward' \
  >"$temporary_directory/loki-flow.json"
if ! jq -e '
    .status == "success"
    and ([.data.result[].values[][1] | fromjson? | select(.traceId != null and .traceId != "")] | length) > 0
  ' "$temporary_directory/loki-flow.json" >/dev/null; then
  printf 'loki query returned no structured flow logs with trace ids\n' >&2
  exit 1
fi

curl -fsS "http://127.0.0.1:$tempo_port/api/search?limit=20" \
  >"$temporary_directory/tempo-search.json"
if ! jq -e '(.traces // [] | length) > 0' \
  "$temporary_directory/tempo-search.json" >/dev/null; then
  printf 'tempo search returned no traces\n' >&2
  jq . "$temporary_directory/tempo-search.json" >&2
  exit 1
fi

if [ "$skywalking_enabled" = "true" ]; then
  wait_for_http "http://127.0.0.1:$skywalking_query_port/zipkin/api/v2/services"
  wait_for_http "http://127.0.0.1:$skywalking_ui_port/zipkin/"
  assert_prometheus_vector \
    'sum(otelcol_exporter_sent_spans{job="flow-otel-collector",exporter="otlp/skywalking"}) > 0' \
    "$temporary_directory/prometheus-skywalking-export.json"
  curl -fsS "http://127.0.0.1:$skywalking_query_port/zipkin/api/v2/services" \
    >"$temporary_directory/skywalking-services.json"
  if ! jq -e 'index("workflow-server") != null' \
    "$temporary_directory/skywalking-services.json" >/dev/null; then
    printf 'skywalking zipkin query returned no workflow-server service\n' >&2
    jq . "$temporary_directory/skywalking-services.json" >&2
    exit 1
  fi
  curl -fsS --get \
    "http://127.0.0.1:$skywalking_query_port/zipkin/api/v2/traces" \
    --data-urlencode 'serviceName=workflow-server' \
    --data-urlencode 'limit=20' \
    >"$temporary_directory/skywalking-traces.json"
  if ! jq -e 'length > 0 and all(.[]; length > 0)' \
    "$temporary_directory/skywalking-traces.json" >/dev/null; then
    printf 'skywalking zipkin query returned no traces\n' >&2
    jq . "$temporary_directory/skywalking-traces.json" >&2
    exit 1
  fi
  skywalking_services=$(jq 'length' "$temporary_directory/skywalking-services.json")
  skywalking_traces=$(jq 'length' "$temporary_directory/skywalking-traces.json")
fi

prometheus_targets=$(jq '.data.result | length' \
  "$temporary_directory/prometheus-flow-up.json")
loki_streams=$(jq '.data.result | length' "$temporary_directory/loki-flow.json")
loki_logs_with_trace=$(jq '
  [.data.result[].values[][1] | fromjson? | select(.traceId != null and .traceId != "")] | length
  ' "$temporary_directory/loki-flow.json")
tempo_traces=$(jq '(.traces // []) | length' "$temporary_directory/tempo-search.json")

jq -n \
  --argjson prometheus_targets "$prometheus_targets" \
  --argjson loki_streams "$loki_streams" \
  --argjson loki_logs_with_trace "$loki_logs_with_trace" \
  --argjson tempo_traces "$tempo_traces" \
  --argjson skywalking_enabled "$skywalking_enabled" \
  --argjson skywalking_services "$skywalking_services" \
  --argjson skywalking_traces "$skywalking_traces" \
  '{
    status: "passed",
    prometheus_targets: $prometheus_targets,
    loki_streams: $loki_streams,
    loki_logs_with_trace: $loki_logs_with_trace,
    tempo_traces: $tempo_traces,
    skywalking: {
      enabled: $skywalking_enabled,
      services: $skywalking_services,
      traces: $skywalking_traces
    }
  }'
