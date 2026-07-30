#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/versions.env"

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
install_skywalking=${INSTALL_SKYWALKING:-false}
install_tempo=${INSTALL_TEMPO:-true}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_release=${FLOW_RELEASE:-flow-local}
max_node_memory_percent=${OBSERVABILITY_MAX_NODE_MEMORY_PERCENT:-55}
max_node_cpu_percent=${OBSERVABILITY_MAX_NODE_CPU_PERCENT:-70}

helm_install() {
  release=$1
  chart=$2
  version=$3
  values_file=$4
  extra_values_file=$5
  timeout=$6

  set -- helm upgrade --install "$release" "$chart" \
    --namespace "$namespace" \
    --version "$version" \
    --values "$values_file"
  if [ -n "$extra_values_file" ]; then
    if [ ! -r "$extra_values_file" ]; then
      printf 'extra Helm values file is not readable: %s\n' "$extra_values_file" >&2
      exit 1
    fi
    set -- "$@" --values "$extra_values_file"
  fi
  "$@" --wait --timeout "$timeout"
}

check_business() {
  kubectl -n "$flow_namespace" wait \
    --for=condition=Available deployment/"$flow_release"-flow-server \
    --timeout=90s
  kubectl -n "$flow_namespace" wait \
    --for=condition=Available deployment/"$flow_release"-flow-web \
    --timeout=90s
}

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
    printf 'refusing to install observability stack: node cpu=%s%% memory=%s%%, limits cpu<=%s%% memory<=%s%%\n' \
      "$cpu_percent" "$memory_percent" "$max_node_cpu_percent" "$max_node_memory_percent" >&2
    printf 'free local k3s resources or explicitly set OBSERVABILITY_FORCE_INSTALL=true after accepting the risk.\n' >&2
    exit 1
  fi
}

check_capacity

kubectl create namespace "$namespace" --dry-run=client -o yaml | kubectl apply -f -

if ! kubectl -n "$namespace" get secret flow-grafana-admin >/dev/null 2>&1; then
  password=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)
  kubectl -n "$namespace" create secret generic flow-grafana-admin \
    --from-literal=admin-user=admin \
    --from-literal=admin-password="$password"
fi

helm_install flow-monitoring prometheus-community/kube-prometheus-stack \
  "$KUBE_PROMETHEUS_STACK_CHART_VERSION" \
  "$script_dir/kube-prometheus-stack-values.yaml" \
  "${KUBE_PROMETHEUS_STACK_EXTRA_VALUES:-}" 10m
check_business

helm_install flow-loki grafana/loki "$LOKI_CHART_VERSION" \
  "$script_dir/loki-values.yaml" "${LOKI_EXTRA_VALUES:-}" 10m
check_business

helm_install flow-promtail grafana/promtail "$PROMTAIL_CHART_VERSION" \
  "$script_dir/promtail-values.yaml" "${PROMTAIL_EXTRA_VALUES:-}" 10m
check_business

if [ "$install_tempo" = "true" ]; then
  helm_install flow-tempo grafana/tempo "$TEMPO_CHART_VERSION" \
    "$script_dir/tempo-values.yaml" "${TEMPO_EXTRA_VALUES:-}" 10m
  check_business
fi

if [ "$install_skywalking" = "true" ]; then
  helm_install flow-skywalking apache-skywalking/skywalking \
    "$SKYWALKING_CHART_VERSION" "$script_dir/skywalking-values.yaml" \
    "${SKYWALKING_EXTRA_VALUES:-}" 15m
  check_business
fi

helm_install flow-otel-collector open-telemetry/opentelemetry-collector \
  "$OTEL_COLLECTOR_CHART_VERSION" \
  "$script_dir/otel-collector-values.yaml" \
  "${OTEL_COLLECTOR_EXTRA_VALUES:-}" 10m
check_business

kubectl apply -f "$script_dir/flow-dashboard-configmap.yaml"

printf 'Grafana: kubectl -n %s port-forward svc/flow-monitoring-grafana 3000:80\n' "$namespace"
printf 'User: admin\n'
printf 'Password: kubectl -n %s get secret flow-grafana-admin -o jsonpath={.data.admin-password} | base64 -d\n' "$namespace"
