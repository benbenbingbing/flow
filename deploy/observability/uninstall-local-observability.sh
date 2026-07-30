#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/versions.env"

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}

helm -n "$namespace" uninstall flow-otel-collector >/dev/null 2>&1 || true
helm -n "$namespace" uninstall flow-tempo >/dev/null 2>&1 || true
helm -n "$namespace" uninstall flow-promtail >/dev/null 2>&1 || true
helm -n "$namespace" uninstall flow-loki >/dev/null 2>&1 || true
helm -n "$namespace" uninstall flow-skywalking >/dev/null 2>&1 || true
helm -n "$namespace" uninstall flow-monitoring >/dev/null 2>&1 || true

kubectl delete namespace "$namespace" --ignore-not-found=true
