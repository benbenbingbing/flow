#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
components="flow-otel-collector flow-prometheus flow-loki flow-tempo flow-grafana"
state_file=$(mktemp)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

record_replicas() {
  : >"$state_file"
  for component in $components
  do
    replicas=$(kubectl -n "$namespace" get deployment "$component" -o jsonpath='{.spec.replicas}')
    printf '%s %s\n' "$component" "${replicas:-1}" >>"$state_file"
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

wait_observability_component() {
  component=$1
  kubectl -n "$namespace" rollout status deployment/"$component" --timeout=180s
}

wait_business() {
  kubectl -n "$flow_namespace" wait \
    --for=condition=Available deployment/"$flow_release"-flow-server \
    --timeout=120s
  kubectl -n "$flow_namespace" wait \
    --for=condition=Available deployment/"$flow_release"-flow-web \
    --timeout=120s
  kubectl -n "$flow_namespace" wait \
    --for=condition=Available deployment/"$flow_release"-flow-schema-worker \
    --timeout=120s
}

check_business_request() {
  kubectl -n "$flow_namespace" exec deployment/"$flow_release"-flow-web -- \
    wget -q -O - "http://$flow_release-flow-server:8080/healthz" >/dev/null
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
  printf 'deployment/%s still has ready replicas after scale down\n' "$component" >&2
  exit 1
}

require_command kubectl
record_replicas
trap 'restore_components' EXIT HUP INT TERM

wait_business
check_business_request

printf 'component,business_available,business_request,recovered\n'
for component in $components
do
  kubectl -n "$namespace" scale deployment "$component" --replicas=0 >/dev/null
  wait_zero_ready "$component"
  wait_business >/dev/null
  check_business_request

  original_replicas=$(awk -v component="$component" '$1 == component { print $2 }' "$state_file")
  kubectl -n "$namespace" scale deployment "$component" --replicas="$original_replicas" >/dev/null
  wait_observability_component "$component" >/dev/null
  wait_business >/dev/null
  check_business_request
  printf '%s,true,true,true\n' "$component"
done

trap - EXIT HUP INT TERM
printf 'lite observability fault test completed\n'
