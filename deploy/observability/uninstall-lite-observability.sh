#!/usr/bin/env sh
set -eu

namespace=${OBSERVABILITY_NAMESPACE:-flow-observability}
kubectl delete namespace "$namespace" --ignore-not-found=true
