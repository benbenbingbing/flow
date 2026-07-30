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
port_forward_address=${OBSERVABILITY_PORT_FORWARD_ADDRESS:-127.0.0.1}
result_file=${OBSERVABILITY_OBSERVE_RESULT_FILE:-/tmp/flow-observability-stability.jsonl}
temporary_directory=$(mktemp -d)
kubectl_request_timeout=${KUBECTL_REQUEST_TIMEOUT:-30s}
max_memory_limit_ratio=${OBSERVABILITY_MAX_MEMORY_LIMIT_RATIO:-0.90}
max_database_connection_ratio=${OBSERVABILITY_MAX_DATABASE_CONNECTION_RATIO:-0.80}
max_row_lock_waits_per_minute=${OBSERVABILITY_MAX_ROW_LOCK_WAITS_PER_MINUTE:-60}

cleanup() {
  trap - EXIT HUP INT TERM
  kill ${prometheus_pid:-0} ${flow_server_pid:-0} >/dev/null 2>&1 || true
  rm -rf "$temporary_directory"
}
trap cleanup EXIT
trap 'exit 0' HUP INT TERM

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
    return 1
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

mysql_status_json() {
  output=$(kubectl -n "$flow_namespace" exec \
    "statefulset/$flow_mysql_statefulset" -- sh -c '
      MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql \
        --batch --skip-column-names -uroot --execute="
          SHOW GLOBAL STATUS WHERE Variable_name IN (
            '\''Threads_connected'\'', '\''Threads_running'\'',
            '\''Innodb_row_lock_waits'\'', '\''Innodb_row_lock_time'\'',
            '\''Created_tmp_disk_tables'\'', '\''Innodb_log_waits'\''
          );
          SELECT '\''max_connections'\'', @@GLOBAL.max_connections;
        "
    ' 2>/dev/null || true)

  if [ -z "$output" ]; then
    printf '{"available":false,"threads_connected":null,"threads_running":null,"max_connections":null,"row_lock_waits":null,"row_lock_time_ms":null,"temporary_disk_tables":null,"log_waits":null}'
    return
  fi

  printf '%s\n' "$output" | awk '
    BEGIN {
      threads_connected = threads_running = max_connections = "null"
      row_lock_waits = row_lock_time_ms = temporary_disk_tables = log_waits = "null"
    }
    $1 == "Threads_connected" { threads_connected = $2 }
    $1 == "Threads_running" { threads_running = $2 }
    $1 == "max_connections" { max_connections = $2 }
    $1 == "Innodb_row_lock_waits" { row_lock_waits = $2 }
    $1 == "Innodb_row_lock_time" { row_lock_time_ms = $2 }
    $1 == "Created_tmp_disk_tables" { temporary_disk_tables = $2 }
    $1 == "Innodb_log_waits" { log_waits = $2 }
    END {
      available = threads_connected != "null" && threads_running != "null" \
        && max_connections != "null" && row_lock_waits != "null" \
        && row_lock_time_ms != "null" && temporary_disk_tables != "null" \
        && log_waits != "null"
      printf "{\"available\":%s,\"threads_connected\":%s,\"threads_running\":%s,\"max_connections\":%s,\"row_lock_waits\":%s,\"row_lock_time_ms\":%s,\"temporary_disk_tables\":%s,\"log_waits\":%s}", \
        available ? "true" : "false", threads_connected, threads_running, \
        max_connections, row_lock_waits, row_lock_time_ms, \
        temporary_disk_tables, log_waits
    }'
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
  statefulset_available "$namespace" prometheus-flow-monitoring-prometheus && prometheus_available=true
  statefulset_available "$namespace" flow-loki && loki_available=true
  statefulset_available "$namespace" flow-tempo && tempo_available=true
  deployment_available "$namespace" flow-otel-collector && otel_available=true
  deployment_available "$namespace" flow-monitoring-grafana && grafana_available=true

  request_total=$(prometheus_query_value 'sum(increase(http_server_requests_seconds_count{application="workflow-server"}[5m]))')
  request_errors=$(prometheus_query_value 'sum(increase(http_server_requests_seconds_count{application="workflow-server",status=~"5.."}[5m])) or vector(0)')
  request_p50=$(prometheus_query_value 'histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  request_p95=$(prometheus_query_value 'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  request_p99=$(prometheus_query_value 'histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="workflow-server"}[5m])) by (le))')
  prometheus_up=$(prometheus_query_value 'min(up{job="flow-local-flow-server"})')
  jvm_memory=$(prometheus_query_value 'sum(jvm_memory_used_bytes{application="workflow-server"})')
  otel_queue_size=$(prometheus_query_value 'max(otelcol_exporter_queue_size{job="flow-otel-collector"}) or vector(0)')
  otel_queue_capacity=$(prometheus_query_value 'max(otelcol_exporter_queue_capacity{job="flow-otel-collector"}) or vector(0)')
  otel_receiver_failed_spans=$(prometheus_query_value 'sum(otelcol_receiver_failed_spans{job="flow-otel-collector"}) or vector(0)')
  otel_receiver_refused_spans=$(prometheus_query_value 'sum(otelcol_receiver_refused_spans{job="flow-otel-collector"}) or vector(0)')
  otel_exporter_sent_spans=$(prometheus_query_value 'sum(otelcol_exporter_sent_spans{job="flow-otel-collector"}) or vector(0)')
  prometheus_rule_failures=$(prometheus_query_value 'sum(prometheus_rule_evaluation_failures_total) or vector(0)')
  prometheus_scrape_failures=$(prometheus_query_value 'sum(prometheus_target_sync_failed_total) or vector(0)')
  prometheus_exemplar_capacity=$(prometheus_query_value 'prometheus_tsdb_exemplar_max_exemplars')
  prometheus_exemplar_storage=$(prometheus_query_value 'prometheus_tsdb_exemplar_exemplars_in_storage')
  flow_memory_limit_ratio=$(prometheus_query_value 'max(container_memory_working_set_bytes{namespace="'"$flow_namespace"'",container!="",image!=""} / on(namespace,pod,container) kube_pod_container_resource_limits{namespace="'"$flow_namespace"'",resource="memory",unit="byte"})')
  observability_memory_limit_ratio=$(prometheus_query_value 'max(container_memory_working_set_bytes{namespace="'"$namespace"'",container!="",image!=""} / on(namespace,pod,container) kube_pod_container_resource_limits{namespace="'"$namespace"'",resource="memory",unit="byte"})')

  server_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-server")
  web_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-web")
  worker_restarts=$(deployment_restarts "$flow_namespace" "$flow_release-flow-schema-worker")
  mysql_restarts=$(statefulset_restarts "$flow_namespace" "$flow_mysql_statefulset")
  prometheus_restarts=$(statefulset_restarts "$namespace" prometheus-flow-monitoring-prometheus)
  loki_restarts=$(statefulset_restarts "$namespace" flow-loki)
  tempo_restarts=$(statefulset_restarts "$namespace" flow-tempo)
  otel_restarts=$(deployment_restarts "$namespace" flow-otel-collector)
  grafana_restarts=$(deployment_restarts "$namespace" flow-monitoring-grafana)

  flow_top=$(metrics_top_json "$flow_namespace")
  observability_top=$(metrics_top_json "$namespace")
  mysql_status=$(mysql_status_json)
  server_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-server" /tmp)
  web_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-web" /tmp)
  worker_disk=$(disk_sample_json "$flow_namespace" "deployment/$flow_release-flow-schema-worker" /tmp)
  prometheus_disk=$(disk_sample_json "$namespace" statefulset/prometheus-flow-monitoring-prometheus /prometheus)
  tempo_disk=$(disk_sample_json "$namespace" statefulset/flow-tempo /var/tempo)
  grafana_disk=$(disk_sample_json "$namespace" deployment/flow-monitoring-grafana /var/lib/grafana)

  printf '{"sampled_at":"%s","business":{"health_ok":%s,"health_seconds":%s,"server_available":%s,"web_available":%s,"schema_worker_available":%s,"mysql_available":%s,"server_restarts":%s,"web_restarts":%s,"schema_worker_restarts":%s,"mysql_restarts":%s},"observability":{"prometheus_available":%s,"loki_available":%s,"tempo_available":%s,"otel_collector_available":%s,"grafana_available":%s,"prometheus_restarts":%s,"loki_restarts":%s,"tempo_restarts":%s,"otel_collector_restarts":%s,"grafana_restarts":%s},"prometheus":{"flow_up":%s,"request_total_5m":%s,"request_errors_5m":%s,"request_p50_seconds":%s,"request_p95_seconds":%s,"request_p99_seconds":%s,"jvm_memory_used_bytes":%s,"exemplar_max_exemplars":%s,"exemplar_exemplars_in_storage":%s},"telemetry":{"otel_exporter_queue_size":%s,"otel_exporter_queue_capacity":%s,"otel_receiver_failed_spans":%s,"otel_receiver_refused_spans":%s,"otel_exporter_sent_spans":%s,"prometheus_rule_evaluation_failures_total":%s,"prometheus_target_sync_failed_total":%s},"database":{"max_connection_ratio":%s,"max_row_lock_waits_per_minute":%s,"mysql":%s},"resources":{"max_memory_limit_ratio":%s,"flow_memory_limit_ratio":%s,"observability_memory_limit_ratio":%s,"flow_pods":%s,"observability_pods":%s,"disk":{"server":%s,"web":%s,"schema_worker":%s,"prometheus":%s,"tempo":%s,"grafana":%s}}}\n' \
    "$sampled_at" "$health_ok" "$health_seconds" \
    "$server_available" "$web_available" "$worker_available" "$mysql_available" \
    "$server_restarts" "$web_restarts" "$worker_restarts" "$mysql_restarts" \
    "$prometheus_available" "$loki_available" "$tempo_available" "$otel_available" "$grafana_available" \
    "$prometheus_restarts" "$loki_restarts" "$tempo_restarts" "$otel_restarts" "$grafana_restarts" \
    "$prometheus_up" "$request_total" "$request_errors" "$request_p50" "$request_p95" "$request_p99" "$jvm_memory" \
    "$prometheus_exemplar_capacity" "$prometheus_exemplar_storage" \
    "$otel_queue_size" "$otel_queue_capacity" "$otel_receiver_failed_spans" "$otel_receiver_refused_spans" "$otel_exporter_sent_spans" \
    "$prometheus_rule_failures" "$prometheus_scrape_failures" \
    "$max_database_connection_ratio" "$max_row_lock_waits_per_minute" "$mysql_status" \
    "$max_memory_limit_ratio" "$flow_memory_limit_ratio" "$observability_memory_limit_ratio" \
    "$flow_top" "$observability_top" \
    "$server_disk" "$web_disk" "$worker_disk" "$prometheus_disk" "$tempo_disk" "$grafana_disk" >>"$result_file"
}

require_command curl
require_command jq
require_command kubectl

if ! awk -v ratio="$max_memory_limit_ratio" 'BEGIN { exit !(ratio > 0 && ratio < 1) }'; then
  printf 'OBSERVABILITY_MAX_MEMORY_LIMIT_RATIO must be greater than 0 and less than 1\n' >&2
  exit 1
fi
if ! awk -v ratio="$max_database_connection_ratio" 'BEGIN { exit !(ratio > 0 && ratio < 1) }'; then
  printf 'OBSERVABILITY_MAX_DATABASE_CONNECTION_RATIO must be greater than 0 and less than 1\n' >&2
  exit 1
fi
if ! awk -v rate="$max_row_lock_waits_per_minute" 'BEGIN { exit !(rate > 0) }'; then
  printf 'OBSERVABILITY_MAX_ROW_LOCK_WAITS_PER_MINUTE must be greater than 0\n' >&2
  exit 1
fi

: >"$result_file"

kubectl -n "$namespace" rollout status statefulset/prometheus-flow-monitoring-prometheus --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-server --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-web --timeout=120s
kubectl -n "$flow_namespace" wait --for=condition=Available deployment/"$flow_release"-flow-schema-worker --timeout=120s
if kubectl -n "$flow_namespace" get statefulset "$flow_mysql_statefulset" >/dev/null 2>&1; then
  kubectl -n "$flow_namespace" wait --for=jsonpath='{.status.readyReplicas}'=1 statefulset/"$flow_mysql_statefulset" --timeout=120s
fi

command kubectl --request-timeout="$kubectl_request_timeout" -n "$namespace" port-forward --address="$port_forward_address" svc/flow-monitoring-prometheus "$prometheus_port":9090 >"$temporary_directory/prometheus.log" 2>&1 &
prometheus_pid=$!
command kubectl --request-timeout="$kubectl_request_timeout" -n "$flow_namespace" port-forward --address="$port_forward_address" svc/"$flow_release"-flow-server "$flow_server_port":8080 >"$temporary_directory/flow-server.log" 2>&1 &
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
  def mysql_delta($field):
    ((.[-1].database.mysql[$field] | tonumber) - (.[0].database.mysql[$field] | tonumber));
  def elapsed_minutes:
    (((.[-1].sampled_at | fromdateiso8601) - (.[0].sampled_at | fromdateiso8601)) / 60);
  length > 0
  and all(.[]; .business.health_ok == true)
  and all(.[]; .business.server_available == true)
  and all(.[]; .business.web_available == true)
  and all(.[]; .business.schema_worker_available == true)
  and all(.[]; .business.mysql_available == true)
  and all(.[]; .observability.prometheus_available == true)
  and all(.[]; .observability.loki_available == true)
  and all(.[]; .observability.tempo_available == true)
  and all(.[]; .observability.otel_collector_available == true)
  and all(.[]; .observability.grafana_available == true)
  and all(.[]; (.prometheus.flow_up == 1 or .prometheus.flow_up == "1"))
  and all(.[]; (.prometheus.exemplar_max_exemplars | tonumber) > 0)
  and all(.[]; .database.mysql.available == true)
  and all(.[];
    ((.database.mysql.threads_connected | tonumber) / (.database.mysql.max_connections | tonumber))
      <= (.database.max_connection_ratio | tonumber))
  and (if length > 1 then
    (mysql_delta("row_lock_waits") / elapsed_minutes)
      <= (.[0].database.max_row_lock_waits_per_minute | tonumber)
    else true end)
  and (if length > 1 then mysql_delta("log_waits") == 0 else true end)
  and all(.[]; (.resources.flow_memory_limit_ratio | tonumber) <= (.resources.max_memory_limit_ratio | tonumber))
  and all(.[]; (.resources.observability_memory_limit_ratio | tonumber) <= (.resources.max_memory_limit_ratio | tonumber))
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
    memory_limit_ratio: {
      threshold: ([.[].resources.max_memory_limit_ratio | tonumber?] | min // null),
      peak_flow: ([.[].resources.flow_memory_limit_ratio | tonumber?] | max // null),
      peak_observability: ([.[].resources.observability_memory_limit_ratio | tonumber?] | max // null)
    },
    mysql: {
      unavailable_samples: map(select(.database.mysql.available != true)) | length,
      connection_ratio_threshold: ([.[].database.max_connection_ratio | tonumber?] | min // null),
      peak_connection_ratio: ([.[] | ((.database.mysql.threads_connected | tonumber?) / (.database.mysql.max_connections | tonumber?))] | max // null),
      peak_threads_running: ([.[].database.mysql.threads_running | tonumber?] | max // null),
      row_lock_waits_per_minute_threshold: ([.[].database.max_row_lock_waits_per_minute | tonumber?] | min // null),
      row_lock_wait_delta: ((.[-1].database.mysql.row_lock_waits | tonumber?) - (.[0].database.mysql.row_lock_waits | tonumber?)),
      row_lock_time_ms_delta: ((.[-1].database.mysql.row_lock_time_ms | tonumber?) - (.[0].database.mysql.row_lock_time_ms | tonumber?)),
      row_lock_waits_per_minute: (if length > 1 then
        (((.[-1].database.mysql.row_lock_waits | tonumber?) - (.[0].database.mysql.row_lock_waits | tonumber?))
          / (((.[-1].sampled_at | fromdateiso8601) - (.[0].sampled_at | fromdateiso8601)) / 60))
        else null end),
      temporary_disk_tables_delta: ((.[-1].database.mysql.temporary_disk_tables | tonumber?) - (.[0].database.mysql.temporary_disk_tables | tonumber?)),
      log_wait_delta: ((.[-1].database.mysql.log_waits | tonumber?) - (.[0].database.mysql.log_waits | tonumber?))
    },
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
