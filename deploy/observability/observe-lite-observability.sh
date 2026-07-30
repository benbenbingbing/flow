#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
flow_mysql_statefulset=${FLOW_MYSQL_STATEFULSET:-local-mysql}
duration_seconds=${OBSERVABILITY_OBSERVE_SECONDS:-7200}
interval_seconds=${OBSERVABILITY_OBSERVE_INTERVAL_SECONDS:-60}
prometheus_port=${OBSERVABILITY_PROMETHEUS_PORT:-19090}
flow_server_port=${OBSERVABILITY_FLOW_SERVER_PORT:-19091}
result_file=${OBSERVABILITY_OBSERVE_RESULT_FILE:-/tmp/flow-observability-stability.jsonl}
temporary_directory=$(mktemp -d)
kubectl_request_timeout=${KUBECTL_REQUEST_TIMEOUT:-30s}

trap 'kill ${prometheus_pid:-0} ${flow_server_pid:-0} >/dev/null 2>&1 || true; rm -rf "$temporary_directory"' EXIT HUP INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

kubectl() {
  command kubectl --request-timeout="$kubectl_request_timeout" "$@"
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

deployment_available() {
  target_namespace=$1
  deployment=$2
  attempt=1
  while [ "$attempt" -le 3 ]; do
    deployment_json=$(kubectl -n "$target_namespace" get deployment "$deployment" -o json 2>/dev/null || true)
    if [ -n "$deployment_json" ]; then
      available=$(printf '%s' "$deployment_json" | jq -r '.status.availableReplicas // 0')
      desired=$(printf '%s' "$deployment_json" | jq -r '.status.replicas // 0')
      if [ "$available" = "$desired" ] && [ "$desired" != "0" ]; then
        return 0
      fi
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  return 1
}

statefulset_available() {
  target_namespace=$1
  statefulset=$2
  if ! kubectl -n "$target_namespace" get statefulset "$statefulset" >/dev/null 2>&1; then
    return 0
  fi
  attempt=1
  while [ "$attempt" -le 3 ]; do
    statefulset_json=$(kubectl -n "$target_namespace" get statefulset "$statefulset" -o json 2>/dev/null || true)
    if [ -n "$statefulset_json" ]; then
      ready=$(printf '%s' "$statefulset_json" | jq -r '.status.readyReplicas // 0')
      desired=$(printf '%s' "$statefulset_json" | jq -r '.status.replicas // 0')
      if [ "$ready" = "$desired" ] && [ "$desired" != "0" ]; then
        return 0
      fi
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  return 1
}

deployment_restarts() {
  target_namespace=$1
  deployment=$2
  selector=$(kubectl -n "$target_namespace" get deployment "$deployment" -o json 2>/dev/null \
    | jq -r '.spec.selector.matchLabels // {} | to_entries | map("\(.key)=\(.value)") | join(",")')
  if [ -z "$selector" ]; then
    printf '0'
    return
  fi
  kubectl -n "$target_namespace" get pod -l "$selector" -o jsonpath='{range .items[*]}{range .status.containerStatuses[*]}{.restartCount}{"\n"}{end}{end}' 2>/dev/null \
    | awk '{ total += $1 } END { print total + 0 }'
}

statefulset_restarts() {
  target_namespace=$1
  statefulset=$2
  if ! kubectl -n "$target_namespace" get statefulset "$statefulset" >/dev/null 2>&1; then
    printf '0'
    return
  fi
  selector=$(kubectl -n "$target_namespace" get statefulset "$statefulset" -o json 2>/dev/null \
    | jq -r '.spec.selector.matchLabels // {} | to_entries | map("\(.key)=\(.value)") | join(",")')
  if [ -z "$selector" ]; then
    printf '0'
    return
  fi
  kubectl -n "$target_namespace" get pod -l "$selector" -o jsonpath='{range .items[*]}{range .status.containerStatuses[*]}{.restartCount}{"\n"}{end}{end}' 2>/dev/null \
    | awk '{ total += $1 } END { print total + 0 }'
}

business_health_request() {
  started_at=$(date +%s)
  if curl -fsS "http://127.0.0.1:$flow_server_port/healthz" >/dev/null 2>&1; then
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

disk_sample_json() {
  target_namespace=$1
  workload=$2
  mount_path=$3
  output=$(kubectl -n "$target_namespace" exec "$workload" -- sh -c "df -Pk '$mount_path' 2>/dev/null || df -Pk / 2>/dev/null" 2>/dev/null \
    | awk -v mount_path="$mount_path" '
        NR > 1 {
          row = $0
          filesystem = $1
          size = $2
          used = $3
          available = $4
          capacity = $5
          mounted = $6
          if (mounted == mount_path) {
            selected = 1
            selected_row = row
            selected_filesystem = filesystem
            selected_size = size
            selected_used = used
            selected_available = available
            selected_capacity = capacity
            selected_mounted = mounted
          }
          last_filesystem = filesystem
          last_size = size
          last_used = used
          last_available = available
          last_capacity = capacity
          last_mounted = mounted
        }
        END {
          if (selected == 1) {
            printf "%s,%s,%s,%s,%s", selected_filesystem, selected_size, selected_used, selected_available, selected_capacity
          } else if (last_filesystem != "") {
            printf "%s,%s,%s,%s,%s", last_filesystem, last_size, last_used, last_available, last_capacity
          }
        }')

  if [ -z "$output" ]; then
    printf '{"available":false,"mount":"%s","size_kib":null,"used_kib":null,"available_kib":null,"capacity":null}' \
      "$(json_escape "$mount_path")"
    return
  fi

  filesystem=$(printf '%s' "$output" | cut -d, -f1)
  size=$(printf '%s' "$output" | cut -d, -f2)
  used=$(printf '%s' "$output" | cut -d, -f3)
  available=$(printf '%s' "$output" | cut -d, -f4)
  capacity=$(printf '%s' "$output" | cut -d, -f5)
  printf '{"available":true,"filesystem":"%s","mount":"%s","size_kib":%s,"used_kib":%s,"available_kib":%s,"capacity":"%s"}' \
    "$(json_escape "$filesystem")" "$(json_escape "$mount_path")" \
    "$size" "$used" "$available" "$(json_escape "$capacity")"
}

record_sample() {
  sampled_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
  health=$(business_health_request)
  health_ok=$(printf '%s' "$health" | cut -d, -f1)
  health_seconds=$(printf '%s' "$health" | cut -d, -f2)

  server_available=false
  web_available=false
  worker_available=false
  mysql_available=true
  prometheus_available=false
  loki_available=false
  tempo_available=false
  otel_available=false
  grafana_available=false

  deployment_available "$flow_namespace" "$flow_release-flow-server" && server_available=true
  deployment_available "$flow_namespace" "$flow_release-flow-web" && web_available=true
  deployment_available "$flow_namespace" "$flow_release-flow-schema-worker" && worker_available=true
  statefulset_available "$flow_namespace" "$flow_mysql_statefulset" && mysql_available=true || mysql_available=false
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
  otel_queue_size=$(prometheus_query_value 'max(otelcol_exporter_queue_size{job="otel-collector"}) or vector(0)')
  otel_queue_capacity=$(prometheus_query_value 'max(otelcol_exporter_queue_capacity{job="otel-collector"}) or vector(0)')
  otel_receiver_failed_spans=$(prometheus_query_value 'sum(otelcol_receiver_failed_spans{job="otel-collector"}) or vector(0)')
  otel_receiver_refused_spans=$(prometheus_query_value 'sum(otelcol_receiver_refused_spans{job="otel-collector"}) or vector(0)')
  otel_exporter_sent_spans=$(prometheus_query_value 'sum(otelcol_exporter_sent_spans{job="otel-collector"}) or vector(0)')
  prometheus_rule_failures=$(prometheus_query_value 'sum(prometheus_rule_evaluation_failures_total) or vector(0)')
  prometheus_scrape_failures=$(prometheus_query_value 'sum(prometheus_target_sync_failed_total) or vector(0)')
  prometheus_exemplar_capacity=$(prometheus_query_value 'prometheus_tsdb_exemplar_max_exemplars')
  prometheus_exemplar_storage=$(prometheus_query_value 'prometheus_tsdb_exemplar_exemplars_in_storage')

  server_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-server")
  web_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-web")
  worker_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-schema-worker")
  mysql_restarts=$(statefulset_restarts "$flow_namespace" "$flow_mysql_statefulset")
  prometheus_restarts=$(deployment_restarts "$namespace" flow-prometheus)
  loki_restarts=$(deployment_restarts "$namespace" flow-loki)
  tempo_restarts=$(deployment_restarts "$namespace" flow-tempo)
  otel_restarts=$(deployment_restarts "$namespace" flow-otel-collector)
  grafana_restarts=$(deployment_restarts "$namespace" flow-grafana)

  flow_top=$(metrics_top_json "$flow_namespace")
  observability_top=$(metrics_top_json "$namespace")
  server_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-server" /tmp)
  web_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-web" /tmp)
  worker_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-schema-worker" /tmp)
  prometheus_disk=$(disk_sample_json "$namespace" deployment/flow-prometheus /prometheus)
  tempo_disk=$(disk_sample_json "$namespace" deployment/flow-tempo /var/tempo)
  grafana_disk=$(disk_sample_json "$namespace" deployment/flow-grafana /var/lib/grafana)

  printf '{"sampled_at":"%s","business":{"health_ok":%s,"health_seconds":%s,"server_available":%s,"web_available":%s,"schema_worker_available":%s,"mysql_available":%s,"server_restarts":%s,"web_restarts":%s,"schema_worker_restarts":%s,"mysql_restarts":%s},"observability":{"prometheus_available":%s,"loki_available":%s,"tempo_available":%s,"otel_collector_available":%s,"grafana_available":%s,"prometheus_restarts":%s,"loki_restarts":%s,"tempo_restarts":%s,"otel_collector_restarts":%s,"grafana_restarts":%s},"prometheus":{"flow_up":%s,"request_total_5m":%s,"request_errors_5m":%s,"request_p50_seconds":%s,"request_p95_seconds":%s,"request_p99_seconds":%s,"jvm_memory_used_bytes":%s,"exemplar_max_exemplars":%s,"exemplar_exemplars_in_storage":%s},"telemetry":{"otel_exporter_queue_size":%s,"otel_exporter_queue_capacity":%s,"otel_receiver_failed_spans":%s,"otel_receiver_refused_spans":%s,"otel_exporter_sent_spans":%s,"prometheus_rule_evaluation_failures_total":%s,"prometheus_target_sync_failed_total":%s},"resources":{"flow_pods":%s,"observability_pods":%s,"disk":{"server":%s,"web":%s,"schema_worker":%s,"prometheus":%s,"tempo":%s,"grafana":%s}}}\n' \
    "$sampled_at" "$health_ok" "$health_seconds" \
    "$server_available" "$web_available" "$worker_available" "$mysql_available" \
    "$server_restarts" "$web_restarts" "$worker_restarts" "$mysql_restarts" \
    "$prometheus_available" "$loki_available" "$tempo_available" "$otel_available" "$grafana_available" \
    "$prometheus_restarts" "$loki_restarts" "$tempo_restarts" "$otel_restarts" "$grafana_restarts" \
    "$prometheus_up" "$request_total" "$request_errors" "$request_p50" "$request_p95" "$request_p99" "$jvm_memory" \
    "$prometheus_exemplar_capacity" "$prometheus_exemplar_storage" \
    "$otel_queue_size" "$otel_queue_capacity" "$otel_receiver_failed_spans" "$otel_receiver_refused_spans" "$otel_exporter_sent_spans" \
    "$prometheus_rule_failures" "$prometheus_scrape_failures" \
    "$flow_top" "$observability_top" \
    "$server_disk" "$web_disk" "$worker_disk" "$prometheus_disk" "$tempo_disk" "$grafana_disk" >>"$result_file"
}

require_command curl
require_command jq
require_command kubectl

: >"$result_file"

kubectl -n "$namespace" wait --for=condition=Available deployment/flow-prometheus --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-server --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-web --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-schema-worker --timeout=120s
if kubectl -n "$flow_namespace" get statefulset "$flow_mysql_statefulset" >/dev/null 2>&1; then
  kubectl -n "$flow_namespace" wait --for=jsonpath='{.status.readyReplicas}'=1 statefulset/"$flow_mysql_statefulset" --timeout=120s
fi

kubectl -n "$namespace" port-forward svc/flow-prometheus "$prometheus_port":9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
kubectl -n "$flow_namespace" port-forward svc/"$flow_release"-flow-server "$flow_server_port":8080 >"$temporary_directory/flow-server.log" 2>&1 &
flow_server_pid=$!
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
  and all(.[]; .business.mysql_available == true)
  and all(.[]; .observability.prometheus_available == true)
  and all(.[]; (.prometheus.flow_up == 1 or .prometheus.flow_up == "1"))
  and all(.[]; (.prometheus.exemplar_max_exemplars | tonumber) > 0)
  and stable(["business", "server_restarts"])
  and stable(["business", "web_restarts"])
  and stable(["business", "schema_worker_restarts"])
  and stable(["business", "mysql_restarts"])
  and stable(["observability", "prometheus_restarts"])
  and stable(["observability", "loki_restarts"])
  and stable(["observability", "tempo_restarts"])
  and stable(["observability", "otel_collector_restarts"])
  and stable(["observability", "grafana_restarts"])
' "$result_file" >/dev/null; then
  printf 'observability stability observation passed: %s\n' "$result_file"
else
  printf 'observability stability observation failed: %s\n' "$result_file" >&2
  jq -s '{
    samples: length,
    failed_business_health: map(select(.business.health_ok != true)) | length,
    unavailable_business: map(select(.business.server_available != true or .business.web_available != true or .business.schema_worker_available != true or .business.mysql_available != true)) | length,
    prometheus_flow_down: map(select(.prometheus.flow_up != 1 and .prometheus.flow_up != "1")) | length,
    restart_growth: {
      server: ([.[].business.server_restarts] | {first: .[0], last: .[-1]}),
      web: ([.[].business.web_restarts] | {first: .[0], last: .[-1]}),
      schema_worker: ([.[].business.schema_worker_restarts] | {first: .[0], last: .[-1]}),
      mysql: ([.[].business.mysql_restarts] | {first: .[0], last: .[-1]}),
      prometheus: ([.[].observability.prometheus_restarts] | {first: .[0], last: .[-1]}),
      loki: ([.[].observability.loki_restarts] | {first: .[0], last: .[-1]}),
      tempo: ([.[].observability.tempo_restarts] | {first: .[0], last: .[-1]}),
      otel_collector: ([.[].observability.otel_collector_restarts] | {first: .[0], last: .[-1]}),
      grafana: ([.[].observability.grafana_restarts] | {first: .[0], last: .[-1]})
    }
  }' "$result_file" >&2
  exit 1
fi
