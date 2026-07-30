#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
lite_dir="$script_dir/lite"
namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
max_node_memory_percent=${OBSERVABILITY_LITE_MAX_NODE_MEMORY_PERCENT:-80}
max_node_cpu_percent=${OBSERVABILITY_LITE_MAX_NODE_CPU_PERCENT:-80}

check_capacity() {
  if [ "${OBSERVABILITY_FORCE_INSTALL:-false}" = "true" ]; then
    return
  fi
  usage=$(kubectl top node --no-headers | awk 'NR == 1 {gsub("%", "", $3); gsub("%", "", $5); print $3, $5}')
  if [ -z "$usage" ]; then
    printf 'cannot read node metrics; set OBSERVABILITY_FORCE_INSTALL=true to override\n' >&2
    exit 1
  fi
  cpu_percent=$(printf '%s\n' "$usage" | awk '{print $1}')
  memory_percent=$(printf '%s\n' "$usage" | awk '{print $2}')
  if [ "$cpu_percent" -gt "$max_node_cpu_percent" ] \
      || [ "$memory_percent" -gt "$max_node_memory_percent" ]; then
    printf 'refusing lite observability install: node cpu=%s%% memory=%s%%, limits cpu<=%s%% memory<=%s%%\n' \
      "$cpu_percent" "$memory_percent" "$max_node_cpu_percent" "$max_node_memory_percent" >&2
    exit 1
  fi
}

check_business() {
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

apply_and_wait_deployment() {
  file=$1
  deployment=$2
  kubectl apply -f "$lite_dir/$file"
  kubectl -n "$namespace" rollout status deployment/"$deployment" --timeout=180s
  check_business
}

check_capacity
kubectl apply -f "$lite_dir/00-namespace-rbac.yaml"

if ! kubectl -n "$namespace" get secret flow-grafana-admin >/dev/null 2>&1; then
  password=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)
  kubectl -n "$namespace" create secret generic flow-grafana-admin \
    --from-literal=admin-user=admin \
    --from-literal=admin-password="$password"
fi

apply_and_wait_deployment 10-prometheus.yaml flow-prometheus
apply_and_wait_deployment 20-loki.yaml flow-loki
apply_and_wait_deployment 30-tempo.yaml flow-tempo
apply_and_wait_deployment 40-otel-collector.yaml flow-otel-collector

kubectl apply -f "$lite_dir/50-promtail.yaml"
kubectl -n "$namespace" rollout status daemonset/flow-promtail --timeout=180s
check_business

apply_and_wait_deployment 60-grafana.yaml flow-grafana

printf 'Grafana: kubectl -n %s port-forward svc/flow-grafana 3000:3000\n' "$namespace"
printf 'User: admin\n'
printf 'Password: kubectl -n %s get secret flow-grafana-admin -o jsonpath={.data.admin-password} | base64 -d\n' "$namespace"
