#!/usr/bin/env sh
set -eu

result_directory=${1:?result directory is required}
summary_file="$result_directory/summary.json"
canary_file="$result_directory/target-canary.jsonl"
observation_file="$result_directory/observability.jsonl"
report_file="$result_directory/analysis.json"

if [ ! -s "$summary_file" ]; then
  printf 'missing k6 summary: %s\n' "$summary_file" >&2
  exit 1
fi

summary=$(jq '
  def metric($name; $field): .metrics[$name].values[$field] // null;
  {
    http_requests: metric("http_reqs"; "count"),
    http_error_rate: metric("http_req_failed"; "rate"),
    business_error_rate: metric("flow_business_errors"; "rate"),
    catastrophic_error_rate: metric("flow_catastrophic_errors"; "rate"),
    read_p95_ms: metric("flow_read_duration"; "p(95)"),
    read_p99_ms: metric("flow_read_duration"; "p(99)"),
    write_p95_ms: metric("flow_write_duration"; "p(95)"),
    write_p99_ms: metric("flow_write_duration"; "p(99)"),
    dropped_iterations: metric("dropped_iterations"; "count"),
    created_records: metric("flow_created_records"; "count"),
    deleted_records: metric("flow_deleted_records"; "count"),
    cleanup_failures: metric("flow_cleanup_failures"; "count"),
    thresholds_ok: ([.metrics[]?.thresholds[]?.ok] | all(. == true))
  }
' "$summary_file")

if [ -s "$canary_file" ]; then
  canary=$(jq -s '{
    samples: length,
    unavailable_samples: map(select(.available != true)) | length,
    maximum_duration_ms: ([.[].duration_ms | select(type == "number")] | max // null)
  }' "$canary_file")
else
  canary='{"samples":0,"unavailable_samples":null,"maximum_duration_ms":null}'
fi

if [ -s "$observation_file" ]; then
  observation=$(jq -s '{
    samples: length,
    business_health_failures: map(select(.business.health_ok != true)) | length,
    observability_unavailable_samples: map(select(
      .observability.prometheus_available != true
      or .observability.loki_available != true
      or .observability.tempo_available != true
      or .observability.otel_collector_available != true
      or .observability.grafana_available != true
    )) | length,
    server_restart_delta: ((.[-1].business.server_restarts // 0) - (.[0].business.server_restarts // 0)),
    web_restart_delta: ((.[-1].business.web_restarts // 0) - (.[0].business.web_restarts // 0)),
    worker_restart_delta: ((.[-1].business.schema_worker_restarts // 0) - (.[0].business.schema_worker_restarts // 0)),
    mysql_restart_delta: ((.[-1].business.mysql_restarts // 0) - (.[0].business.mysql_restarts // 0)),
    prometheus_restart_delta: ((.[-1].observability.prometheus_restarts // 0) - (.[0].observability.prometheus_restarts // 0)),
    loki_restart_delta: ((.[-1].observability.loki_restarts // 0) - (.[0].observability.loki_restarts // 0)),
    tempo_restart_delta: ((.[-1].observability.tempo_restarts // 0) - (.[0].observability.tempo_restarts // 0)),
    otel_collector_restart_delta: ((.[-1].observability.otel_collector_restarts // 0) - (.[0].observability.otel_collector_restarts // 0)),
    grafana_restart_delta: ((.[-1].observability.grafana_restarts // 0) - (.[0].observability.grafana_restarts // 0)),
    peak_request_errors_5m: ([.[].prometheus.request_errors_5m | tonumber?] | max // null),
    peak_jvm_memory_bytes: ([.[].prometheus.jvm_memory_used_bytes | tonumber?] | max // null),
    max_memory_limit_ratio: ([.[].resources.max_memory_limit_ratio | tonumber?] | min // null),
    peak_flow_memory_limit_ratio: ([.[].resources.flow_memory_limit_ratio | tonumber?] | max // null),
    peak_observability_memory_limit_ratio: ([.[].resources.observability_memory_limit_ratio | tonumber?] | max // null),
    resource_metrics_available_samples: ([.[] | select(
      (.resources.max_memory_limit_ratio | tonumber?) != null
      and (.resources.flow_memory_limit_ratio | tonumber?) != null
      and (.resources.observability_memory_limit_ratio | tonumber?) != null
    )] | length),
    mysql_metrics_available_samples: ([.[] | select(.database.mysql.available == true)] | length),
    max_database_connection_ratio: ([.[].database.max_connection_ratio | tonumber?] | min // null),
    peak_database_connection_ratio: ([.[] | ((.database.mysql.threads_connected | tonumber?) / (.database.mysql.max_connections | tonumber?))] | max // null),
    peak_database_threads_running: ([.[].database.mysql.threads_running | tonumber?] | max // null),
    max_row_lock_waits_per_minute: ([.[].database.max_row_lock_waits_per_minute | tonumber?] | min // null),
    row_lock_waits_delta: ((.[-1].database.mysql.row_lock_waits | tonumber?) - (.[0].database.mysql.row_lock_waits | tonumber?)),
    row_lock_time_ms_delta: ((.[-1].database.mysql.row_lock_time_ms | tonumber?) - (.[0].database.mysql.row_lock_time_ms | tonumber?)),
    temporary_disk_tables_delta: ((.[-1].database.mysql.temporary_disk_tables | tonumber?) - (.[0].database.mysql.temporary_disk_tables | tonumber?)),
    row_lock_waits_per_minute: (if length > 1 then
      (((.[-1].database.mysql.row_lock_waits | tonumber?) - (.[0].database.mysql.row_lock_waits | tonumber?))
        / (((.[-1].sampled_at | fromdateiso8601) - (.[0].sampled_at | fromdateiso8601)) / 60))
      else null end),
    database_log_wait_delta: ((.[-1].database.mysql.log_waits | tonumber?) - (.[0].database.mysql.log_waits | tonumber?)),
    peak_otel_queue_size: ([.[].telemetry.otel_exporter_queue_size | tonumber?] | max // null),
    trace_receiver_failed_delta: ((.[-1].telemetry.otel_receiver_failed_spans // 0 | tonumber) - (.[0].telemetry.otel_receiver_failed_spans // 0 | tonumber)),
    trace_receiver_refused_delta: ((.[-1].telemetry.otel_receiver_refused_spans // 0 | tonumber) - (.[0].telemetry.otel_receiver_refused_spans // 0 | tonumber)),
    trace_exporter_sent_delta: ((.[-1].telemetry.otel_exporter_sent_spans // 0 | tonumber) - (.[0].telemetry.otel_exporter_sent_spans // 0 | tonumber)),
    prometheus_rule_failure_delta: ((.[-1].telemetry.prometheus_rule_evaluation_failures_total // 0 | tonumber) - (.[0].telemetry.prometheus_rule_evaluation_failures_total // 0 | tonumber)),
    prometheus_target_sync_failure_delta: ((.[-1].telemetry.prometheus_target_sync_failed_total // 0 | tonumber) - (.[0].telemetry.prometheus_target_sync_failed_total // 0 | tonumber))
  }' "$observation_file")
else
  observation='{"samples":0}'
fi

jq -n \
  --argjson summary "$summary" \
  --argjson canary "$canary" \
  --argjson observation "$observation" \
  '{summary:$summary,canary:$canary,observation:$observation}' \
  >"$report_file"

jq . "$report_file"

if ! jq -e '
  .summary.thresholds_ok == true
  and (.summary.dropped_iterations // 0) == 0
  and (.summary.cleanup_failures // 0) == 0
  and (.summary.created_records // 0) == (.summary.deleted_records // 0)
  and (.canary.unavailable_samples == null or .canary.unavailable_samples == 0)
  and (.observation.business_health_failures == null or .observation.business_health_failures == 0)
  and (.observation.observability_unavailable_samples == null or .observation.observability_unavailable_samples == 0)
  and (.observation.server_restart_delta == null or .observation.server_restart_delta == 0)
  and (.observation.web_restart_delta == null or .observation.web_restart_delta == 0)
  and (.observation.worker_restart_delta == null or .observation.worker_restart_delta == 0)
  and (.observation.mysql_restart_delta == null or .observation.mysql_restart_delta == 0)
  and (.observation.prometheus_restart_delta == null or .observation.prometheus_restart_delta == 0)
  and (.observation.loki_restart_delta == null or .observation.loki_restart_delta == 0)
  and (.observation.tempo_restart_delta == null or .observation.tempo_restart_delta == 0)
  and (.observation.otel_collector_restart_delta == null or .observation.otel_collector_restart_delta == 0)
  and (.observation.grafana_restart_delta == null or .observation.grafana_restart_delta == 0)
  and (.observation.peak_request_errors_5m == null or .observation.peak_request_errors_5m == 0)
  and (.observation.samples == 0 or .observation.resource_metrics_available_samples == .observation.samples)
  and (.observation.samples == 0 or .observation.peak_flow_memory_limit_ratio <= .observation.max_memory_limit_ratio)
  and (.observation.samples == 0 or .observation.peak_observability_memory_limit_ratio <= .observation.max_memory_limit_ratio)
  and (.observation.samples == 0 or .observation.mysql_metrics_available_samples == .observation.samples)
  and (.observation.samples == 0 or .observation.peak_database_connection_ratio <= .observation.max_database_connection_ratio)
  and (.observation.samples <= 1 or .observation.row_lock_waits_per_minute <= .observation.max_row_lock_waits_per_minute)
  and (.observation.samples <= 1 or .observation.database_log_wait_delta == 0)
  and (.observation.samples <= 1 or .observation.temporary_disk_tables_delta == 0)
  and (.observation.samples <= 1 or .observation.trace_receiver_failed_delta == 0)
  and (.observation.samples <= 1 or .observation.trace_receiver_refused_delta == 0)
  and (.observation.samples <= 1 or .observation.trace_exporter_sent_delta > 0)
  and (.observation.samples <= 1 or .observation.prometheus_rule_failure_delta == 0)
  and (.observation.samples <= 1 or .observation.prometheus_target_sync_failure_delta == 0)
' "$report_file" >/dev/null; then
  printf 'load-test acceptance failed: %s\n' "$report_file" >&2
  exit 1
fi
