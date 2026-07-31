#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)

export INSTALL_TEMPO=true
export INSTALL_SKYWALKING=true
export KUBE_PROMETHEUS_STACK_EXTRA_VALUES="$script_dir/kube-prometheus-stack-loadtest-values.yaml"
export LOKI_EXTRA_VALUES="$script_dir/loki-loadtest-values.yaml"
export TEMPO_EXTRA_VALUES="$script_dir/tempo-loadtest-values.yaml"
export SKYWALKING_EXTRA_VALUES="$script_dir/skywalking-loadtest-values.yaml"
export OTEL_COLLECTOR_EXTRA_VALUES="$script_dir/otel-collector-loadtest-values.yaml"

exec "$script_dir/install-local-observability.sh"
