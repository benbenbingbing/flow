#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
result_file=${RESULT_FILE:-/tmp/flow-observability-fault-matrix.csv}
components=${OBSERVABILITY_FAULT_COMPONENTS:-"flow-otel-collector flow-prometheus flow-loki flow-tempo flow-grafana"}
kubectl_request_timeout=${KUBECTL_REQUEST_TIMEOUT:-30s}

server_deployment="$flow_release-flow-server"
web_deployment="$flow_release-flow-web"
schema_worker_deployment="$flow_release-flow-schema-worker"
state_file=$(mktemp)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

kubectl() {
  command kubectl --request-timeout="$kubectl_request_timeout" "$@"
}

deployment_env() {
  deployment=$1
  name=$2
  kubectl -n "$flow_namespace" get deployment "$deployment" \
    -o jsonpath="{.spec.template.spec.containers[0].env[?(@.name==\"$name\")].value}"
}

record_component_replicas() {
  : >"$state_file"
  for component in $components
  do
    if kubectl -n "$namespace" get deployment "$component" >/dev/null 2>&1; then
      replicas=$(kubectl -n "$namespace" get deployment "$component" -o jsonpath='{.spec.replicas}')
      printf '%s %s\n' "$component" "${replicas:-1}" >>"$state_file"
    fi
  done
}

restore_components() {
  if [ ! -s "$state_file" ]; then
    return
  fi
  while read -r component replicas
  do
    [ -n "$component" ] || continue
    kubectl -n "$namespace" scale deployment "$component" --replicas="$replicas" >/dev/null 2>&1 || true
  done <"$state_file"
}

restore_tracing_env() {
  if [ -n "${original_otlp_endpoint:-}" ]; then
    kubectl -n "$flow_namespace" set env deployment/"$server_deployment" \
      OTEL_EXPORTER_OTLP_ENDPOINT="$original_otlp_endpoint" \
      OTEL_EXPORTER_OTLP_TIMEOUT="$original_otlp_timeout" >/dev/null 2>&1 || true
  fi
}

restore_all() {
  restore_tracing_env
  restore_components
}

wait_deployment_available() {
  target_namespace=$1
  deployment=$2
  kubectl -n "$target_namespace" wait \
    --for=condition=Available deployment/"$deployment" \
    --timeout=180s >/dev/null
}

wait_business() {
  wait_deployment_available "$flow_namespace" "$server_deployment"
  wait_deployment_available "$flow_namespace" "$web_deployment"
  wait_deployment_available "$flow_namespace" "$schema_worker_deployment"
}

probe_business_health() {
  kubectl -n "$flow_namespace" exec deployment/"$web_deployment" -- \
    wget -q -O - "http://$flow_release-flow-server:8080/healthz" >/dev/null
}

record_result() {
  case_name=$1
  fault_type=$2
  expected=$3
  status=$4
  message=$5
  printf '%s,%s,%s,%s,%s,%s\n' \
    "$(date -u +%FT%TZ)" \
    "$case_name" \
    "$fault_type" \
    "$expected" \
    "$status" \
    "$message" >>"$result_file"
}

run_business_assertion() {
  case_name=$1
  fault_type=$2
  wait_business
  if probe_business_health; then
    record_result "$case_name" "$fault_type" "business_available" "passed" "healthz_ok"
  else
    record_result "$case_name" "$fault_type" "business_available" "failed" "healthz_failed"
    return 1
  fi
}

wait_zero_ready() {
  component=$1
  for _ in 1 2 3 4 5 6 7 8 9 10
  do
    ready=$(kubectl -n "$namespace" get deployment "$component" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)
    [ -z "$ready" ] && return
    [ "$ready" = "0" ] && return
    sleep 3
  done
  return 1
}

run_component_case() {
  component=$1
  if ! kubectl -n "$namespace" get deployment "$component" >/dev/null 2>&1; then
    record_result "$component" "component_down" "business_available" "failed" "deployment_not_found"
    return 1
  fi

  original_replicas=$(awk -v component="$component" '$1 == component { print $2 }' "$state_file")
  [ -n "$original_replicas" ] || original_replicas=1

  kubectl -n "$namespace" scale deployment "$component" --replicas=0 >/dev/null
  if ! wait_zero_ready "$component"; then
    record_result "$component" "component_down" "zero_ready" "failed" "component_still_ready"
    return 1
  fi

  run_business_assertion "$component" "component_down"

  kubectl -n "$namespace" scale deployment "$component" --replicas="$original_replicas" >/dev/null
  kubectl -n "$namespace" rollout status deployment/"$component" --timeout=180s >/dev/null
  run_business_assertion "$component" "component_recovered"
}

run_tracing_endpoint_case() {
  case_name=$1
  endpoint=$2
  timeout=$3

  kubectl -n "$flow_namespace" set env deployment/"$server_deployment" \
    OTEL_EXPORTER_OTLP_ENDPOINT="$endpoint" \
    OTEL_EXPORTER_OTLP_TIMEOUT="$timeout" >/dev/null
  kubectl -n "$flow_namespace" rollout status deployment/"$server_deployment" --timeout=240s >/dev/null
  run_business_assertion "$case_name" "otlp_endpoint_fault"
}

require_command kubectl

original_otlp_endpoint=$(deployment_env "$server_deployment" OTEL_EXPORTER_OTLP_ENDPOINT)
original_otlp_timeout=$(deployment_env "$server_deployment" OTEL_EXPORTER_OTLP_TIMEOUT)
record_component_replicas
trap 'restore_all' EXIT HUP INT TERM

printf 'timestamp,case,fault_type,expected,status,message\n' >"$result_file"

wait_business
probe_business_health >/dev/null

for component in $components
do
  run_component_case "$component"
done

if kubectl -n "$namespace" get deployment/flow-skywalking-oap >/dev/null 2>&1; then
  components="$components flow-skywalking-oap"
  printf 'flow-skywalking-oap %s\n' \
    "$(kubectl -n "$namespace" get deployment flow-skywalking-oap -o jsonpath='{.spec.replicas}')" >>"$state_file"
  run_component_case flow-skywalking-oap
else
  run_business_assertion skywalking "component_not_installed"
fi

run_tracing_endpoint_case otlp_refused "http://127.0.0.1:9/v1/traces" "500ms"
run_tracing_endpoint_case otlp_timeout "http://10.255.255.1:4318/v1/traces" "500ms"
run_tracing_endpoint_case otlp_http_error "http://flow-prometheus.$namespace.svc.cluster.local:9090/v1/traces" "500ms"

restore_tracing_env
kubectl -n "$flow_namespace" rollout status deployment/"$server_deployment" --timeout=240s >/dev/null

trap - EXIT HUP INT TERM
restore_components

printf 'fault matrix completed: %s\n' "$result_file"
