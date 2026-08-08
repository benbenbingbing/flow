#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
result_file=${RESULT_FILE:-/tmp/flow-observability-fault-matrix.csv}
components=${OBSERVABILITY_FAULT_COMPONENTS:-"deployment/flow-otel-collector statefulset/prometheus-flow-monitoring-prometheus statefulset/flow-loki statefulset/flow-tempo deployment/flow-monitoring-grafana deployment/flow-skywalking-skywalking-helm-oap"}
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
    resource=$(component_resource "$component")
    if kubectl -n "$namespace" get "$resource" >/dev/null 2>&1; then
      replicas=$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.spec.replicas}')
      printf '%s %s\n' "$resource" "${replicas:-1}" >>"$state_file"
    fi
  done
}

component_resource() {
  case "$1" in
    */*) printf '%s' "$1" ;;
    *) printf 'deployment/%s' "$1" ;;
  esac
}

is_optional_component() {
  case "$1" in
    *skywalking*) return 0 ;;
    *) return 1 ;;
  esac
}

restore_components() {
  if [ ! -s "$state_file" ]; then
    return
  fi
  while read -r resource replicas
  do
    [ -n "$resource" ] || continue
    kubectl -n "$namespace" scale "$resource" --replicas="$replicas" >/dev/null 2>&1 || true
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
  resource=$1
  for _ in 1 2 3 4 5 6 7 8 9 10
  do
    ready=$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)
    [ -z "$ready" ] && return
    [ "$ready" = "0" ] && return
    sleep 3
  done
  return 1
}

run_component_case() {
  component=$1
  resource=$(component_resource "$component")
  case_name=${resource#*/}
  if ! kubectl -n "$namespace" get "$resource" >/dev/null 2>&1; then
    if is_optional_component "$resource"; then
      record_result "$case_name" "component_not_installed" "business_available" "skipped" "optional_component_not_installed"
      return 0
    fi
    record_result "$case_name" "component_down" "business_available" "failed" "resource_not_found"
    return 1
  fi

  original_replicas=$(awk -v resource="$resource" '$1 == resource { print $2 }' "$state_file")
  [ -n "$original_replicas" ] || original_replicas=1

  kubectl -n "$namespace" scale "$resource" --replicas=0 >/dev/null
  if ! wait_zero_ready "$resource"; then
    record_result "$case_name" "component_down" "zero_ready" "failed" "component_still_ready"
    return 1
  fi

  run_business_assertion "$case_name" "component_down"

  kubectl -n "$namespace" scale "$resource" --replicas="$original_replicas" >/dev/null
  kubectl -n "$namespace" rollout status "$resource" --timeout=180s >/dev/null
  run_business_assertion "$case_name" "component_recovered"
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

run_tracing_endpoint_case otlp_refused "http://127.0.0.1:9/v1/traces" "500ms"
run_tracing_endpoint_case otlp_timeout "http://10.255.255.1:4318/v1/traces" "500ms"
run_tracing_endpoint_case otlp_http_error "http://flow-loki-gateway.$namespace.svc.cluster.local/v1/traces" "500ms"

restore_tracing_env
kubectl -n "$flow_namespace" rollout status deployment/"$server_deployment" --timeout=240s >/dev/null

trap - EXIT HUP INT TERM
restore_components

printf 'fault matrix completed: %s\n' "$result_file"
