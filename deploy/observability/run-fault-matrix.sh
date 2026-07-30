#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/versions.env"

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
service_url=${FLOW_SERVICE_URL:-http://flow-local-flow-web.flow-hardening.svc.cluster.local}
probe_image=${PROBE_IMAGE:-curlimages/curl:8.16.0}
result_file=${RESULT_FILE:-/tmp/flow-observability-fault-matrix.txt}

probe_business() {
  kubectl -n "$flow_namespace" run flow-fault-probe \
    --rm -i --restart=Never \
    --image="$probe_image" \
    --command -- sh -c "curl -fsS -m 5 '$service_url' >/dev/null"
}

scale_if_exists() {
  workload=$1
  replicas=$2
  if kubectl -n "$namespace" get "$workload" >/dev/null 2>&1; then
    kubectl -n "$namespace" scale "$workload" --replicas="$replicas"
  fi
}

record_case() {
  name=$1
  workload=$2
  printf 'CASE %s start %s\n' "$name" "$(date -u +%FT%TZ)" | tee -a "$result_file"
  scale_if_exists "$workload" 0
  sleep 10
  kubectl -n "$flow_namespace" get deploy,pod | tee -a "$result_file"
  if probe_business; then
    printf 'CASE %s business=ok\n' "$name" | tee -a "$result_file"
  else
    printf 'CASE %s business=failed\n' "$name" | tee -a "$result_file"
    scale_if_exists "$workload" 1
    exit 1
  fi
  scale_if_exists "$workload" 1
  sleep 10
  printf 'CASE %s recovered %s\n' "$name" "$(date -u +%FT%TZ)" | tee -a "$result_file"
}

: >"$result_file"
record_case collector deployment/flow-otel-collector
record_case prometheus statefulset/prometheus-flow-monitoring-kube-prometheus-prometheus
record_case loki statefulset/flow-loki
record_case tempo statefulset/flow-tempo
record_case grafana deployment/flow-monitoring-grafana

if kubectl -n "$namespace" get deployment/flow-skywalking-oap >/dev/null 2>&1; then
  record_case skywalking deployment/flow-skywalking-oap
else
  printf 'CASE skywalking skipped: release not installed\n' | tee -a "$result_file"
fi

printf 'fault matrix completed: %s\n' "$result_file"
